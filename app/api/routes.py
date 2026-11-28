from __future__ import annotations

import asyncio
import hashlib
import json
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from fastapi.responses import FileResponse
from sqlalchemy import select
from sqlalchemy.orm import selectinload
from sqlalchemy.ext.asyncio import AsyncSession

from app import __version__
from app.config import settings
from app.core import lan_auth
from app.core.artwork import extract_cover
from app.core.library import invalidate_cache, resolve_track_path, scan_library
from app.core.lyrics import get_track_lyrics, lrc_path_for
from app.core.runner import runner
from app.core.sync_export import build_export, export_target
from app.core.tagging import guess_image_mime
from app.core.updates import check_for_update
from app.core import user_settings
from app.core.sources import remember_source, sources_by_relative, sync_sources_from_tracks
from app.db import get_session
from app.models import Job, LibrarySource, Playlist, PlaylistTrack, Track
from app.platforms import ResolveError, UnsupportedURL, detect_platform, resolve
from app.schemas import (
    AuthLogin,
    AuthStatus,
    ImportRequest,
    JobOut,
    JobSummary,
    LanInfo,
    LibraryEntry,
    LibraryResponse,
    LyricsResponse,
    PlaylistAddTracks,
    PlaylistCreate,
    PlaylistDetail,
    PlaylistRename,
    PlaylistSummary,
    SettingsOut,
    SettingsUpdate,
    SyncImportRequest,
    SyncImportResponse,
    TrackOut,
)

router = APIRouter(prefix="/api")

MAX_JOBS = 100
MAX_LIBRARY_ENTRIES = 5000

# Версия HTTP-API. Desktop-оболочка сверяет её с API_REVISION в
# src-tauri/src/lib.rs и не переиспользует сервер с другим значением:
# иначе новый интерфейс открывается поверх старого бэкенда без нужных роутов.
# Меняйте вместе с константой в lib.rs.
API_REVISION = 8


@router.get("/health")
async def health(request: Request) -> dict:
    payload: dict = {
        "status": "ok",
        "version": __version__,
        "api": API_REVISION,
        "queue_running": runner.running,
    }
    # Пути к папкам нужны desktop-оболочке (она сверяет data_dir), но не
    # чужим устройствам в локальной сети.
    if lan_auth.is_local_request(request):
        payload["music_dir"] = str(settings.music_dir)
        payload["data_dir"] = str(settings.data_dir)
    return payload


@router.get("/auth/status", response_model=AuthStatus)
async def auth_status(request: Request) -> AuthStatus:
    return AuthStatus(
        authenticated=lan_auth.authenticated(request),
        local=lan_auth.is_local_request(request),
        pin_required=lan_auth.pin_required(),
        lan_enabled=lan_auth.lan_enabled(),
        url=lan_auth.lan_url(),
    )


@router.post("/auth/login", response_model=AuthStatus)
async def auth_login(
    payload: AuthLogin,
    request: Request,
    response: Response,
) -> AuthStatus:
    if lan_auth.limited(request):
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Слишком много попыток. Подождите несколько минут.",
        )
    if not lan_auth.check_pin(payload.pin):
        lan_auth.register_failure(request)
        raise HTTPException(status_code=401, detail="Неверный PIN")
    lan_auth.reset_attempts(request)
    lan_auth.issue_cookie(response)
    return AuthStatus(
        authenticated=True,
        local=lan_auth.is_local_request(request),
        pin_required=lan_auth.pin_required(),
        lan_enabled=lan_auth.lan_enabled(),
        url=lan_auth.lan_url(),
    )


@router.post("/auth/logout", response_model=AuthStatus)
async def auth_logout(request: Request, response: Response) -> AuthStatus:
    lan_auth.clear_cookie(response)
    return AuthStatus(
        authenticated=False,
        local=lan_auth.is_local_request(request),
        pin_required=lan_auth.pin_required(),
        lan_enabled=lan_auth.lan_enabled(),
        url=lan_auth.lan_url(),
    )


@router.get("/lan", response_model=LanInfo)
async def lan_info(request: Request) -> LanInfo:
    local = lan_auth.is_local_request(request)
    return LanInfo(
        enabled=lan_auth.lan_enabled(),
        host=settings.host,
        port=settings.port,
        url=lan_auth.lan_url(),
        pin=lan_auth.pin() if local else None,
        candidates=lan_auth.lan_candidates(),
    )


@router.get("/lan/qr.svg")
async def lan_qr(request: Request) -> Response:
    if not lan_auth.is_local_request(request):
        raise HTTPException(status_code=403, detail="QR доступен только с этого компьютера")
    url = lan_auth.lan_url()
    if not url:
        raise HTTPException(status_code=409, detail="Доступ по локальной сети выключен")
    try:
        svg = lan_auth.qr_svg(url)
    except Exception as exc:  # noqa: BLE001 - qrcode может быть не установлен
        raise HTTPException(status_code=503, detail=f"QR недоступен: {exc}") from exc
    return Response(
        content=svg,
        media_type="image/svg+xml",
        headers={"Cache-Control": "no-store"},
    )


@router.post("/lan/regenerate", response_model=LanInfo)
async def lan_regenerate(request: Request) -> LanInfo:
    if not lan_auth.is_local_request(request):
        raise HTTPException(status_code=403, detail="PIN можно сменить только с этого компьютера")
    try:
        lan_auth.regenerate_pin()
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc
    return await lan_info(request)


@router.get("/update")
async def update_status(refresh: bool = False) -> dict:
    return await check_for_update(force=refresh)


@router.post("/import", response_model=JobOut, status_code=status.HTTP_201_CREATED)
async def import_url(
    payload: ImportRequest,
    session: AsyncSession = Depends(get_session),
) -> Job:
    platform = detect_platform(payload.url)
    if platform is None:
        raise HTTPException(
            status_code=400,
            detail="Поддерживаются YouTube Music (youtube.com, music.youtube.com) и SoundCloud",
        )

    try:
        collection = await asyncio.to_thread(resolve, payload.url)
    except UnsupportedURL as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except ResolveError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    tracks = collection.tracks[: settings.limit_tracks]
    if not tracks:
        raise HTTPException(status_code=422, detail="В ссылке не нашлось треков")

    job = Job(
        url=payload.url,
        platform=collection.platform,
        kind=collection.kind,
        title=collection.title,
        thumbnail=collection.thumbnail,
        audio_format=payload.audio_format or settings.audio_format,
        total=len(tracks),
        status="pending",
    )
    for position, spec in enumerate(tracks):
        job.tracks.append(
            Track(
                position=position,
                title=spec.title,
                artist=spec.artist,
                album=spec.album,
                duration=spec.duration,
                source_url=spec.url,
                status="pending",
            )
        )

    session.add(job)
    await session.commit()
    await session.refresh(job, attribute_names=["tracks"])

    await runner.enqueue(job.id)
    return job


@router.get("/jobs", response_model=list[JobSummary])
async def list_jobs(
    limit: int = 50,
    session: AsyncSession = Depends(get_session),
) -> list[Job]:
    limit = max(1, min(limit, MAX_JOBS))
    result = await session.execute(
        select(Job).order_by(Job.created_at.desc()).limit(limit)
    )
    return list(result.scalars().all())


@router.get("/jobs/{job_id}", response_model=JobOut)
async def get_job(job_id: str, session: AsyncSession = Depends(get_session)) -> Job:
    job = await session.get(Job, job_id, options=[selectinload(Job.tracks)])
    if job is None:
        raise HTTPException(status_code=404, detail="Задача не найдена")
    return job


@router.post("/jobs/{job_id}/retry", response_model=JobOut)
async def retry_job(
    job_id: str,
    session: AsyncSession = Depends(get_session),
) -> Job:
    job = await session.get(Job, job_id, options=[selectinload(Job.tracks)])
    if job is None:
        raise HTTPException(status_code=404, detail="Задача не найдена")
    if job.status in {"running", "pending"}:
        raise HTTPException(status_code=409, detail="Задача уже в очереди")

    retryable = 0
    for track in job.tracks:
        if track.status == "failed":
            track.status = "pending"
            track.error = None
            retryable += 1
    if retryable == 0:
        raise HTTPException(status_code=409, detail="Нет треков для повтора")

    job.failed = 0
    job.error = None
    job.status = "pending"
    await session.commit()
    await session.refresh(job, attribute_names=["tracks"])

    await runner.enqueue(job.id)
    return job


@router.delete("/jobs/{job_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_job(job_id: str, session: AsyncSession = Depends(get_session)) -> Response:
    job = await session.get(Job, job_id, options=[selectinload(Job.tracks)])
    if job is None:
        raise HTTPException(status_code=404, detail="Задача не найдена")
    await session.delete(job)
    await session.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/library", response_model=LibraryResponse)
async def library(refresh: bool = False) -> LibraryResponse:
    tracks = await asyncio.to_thread(scan_library, refresh)
    entries = [LibraryEntry.model_validate(track) for track in tracks[:MAX_LIBRARY_ENTRIES]]
    return LibraryResponse(
        root=str(settings.music_dir),
        total=len(tracks),
        total_size=sum(track.size for track in tracks),
        entries=entries,
    )


@router.delete("/library", status_code=status.HTTP_204_NO_CONTENT)
async def delete_track(path: str) -> Response:
    """Удаляет файл трека с диска. Папки артиста и альбома остаются."""
    file = resolve_track_path(path)
    if file is None:
        raise HTTPException(status_code=404, detail="Файл не найден")

    try:
        await asyncio.to_thread(file.unlink)
        await asyncio.to_thread(lrc_path_for(file).unlink, True)
    except PermissionError as exc:
        raise HTTPException(
            status_code=409,
            detail="Файл занят: остановите воспроизведение и попробуйте снова",
        ) from exc
    except OSError as exc:
        raise HTTPException(status_code=500, detail=f"Не удалось удалить файл: {exc}") from exc

    invalidate_cache()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


# ---------- экспорт для мобильного приложения ----------


@router.get("/sync/export")
async def export_library(session: AsyncSession = Depends(get_session)) -> Response:
    """JSON-снимок библиотеки: телефон импортирует его и скачает недостающее."""
    payload = await build_export(session)
    body = json.dumps(payload, ensure_ascii=False, indent=2)
    return Response(
        content=body,
        media_type="application/json; charset=utf-8",
        headers={
            "Content-Disposition": 'attachment; filename="soundtube-library.json"',
        },
    )


@router.post("/sync/export")
async def save_library_export(session: AsyncSession = Depends(get_session)) -> dict:
    """Сохраняет тот же снимок в «Загрузки» и возвращает путь к файлу.

    Нужен для десктопной оболочки: вебвью не обязана поддерживать скачивание
    по ссылке, а файл пользователю всё равно надо как-то перенести на телефон.
    """
    payload = await build_export(session)
    target = export_target()
    target.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return {
        "path": str(target),
        "tracks": len(payload["tracks"]),
        "playlists": len(payload["playlists"]),
        "withoutSource": payload.get("withoutSource", 0),
    }


@router.post("/sync/import", response_model=SyncImportResponse)
async def import_library(
    payload: SyncImportRequest, session: AsyncSession = Depends(get_session)
) -> SyncImportResponse:
    """Восстанавливает библиотеку из файла экспорта (см. GET /sync/export).

    - Треки, уже присутствующие на диске, обновляют `library_sources`.
    - Отсутствующие треки ставятся в очередь одной задачей «Импорт библиотеки»
      (kind="sync"), чтобы её можно было раскрыть, контролировать и отменить
      целиком, как импорт плейлиста.
    - Запись о старой загрузке не отменяет скачивание: если файла на диске
      нет, трек качается заново.
    - Плейлисты восстанавливаются для треков, уже имеющихся на диске.
      После завершения загрузки недостающих треков повторный импорт того же
      файла добавит их в плейлисты.
    """
    await sync_sources_from_tracks(session)

    sources = await sources_by_relative(session)
    url_to_relative: dict[str, str] = {}
    for relative, source in sources.items():
        if source.source_url and source.source_url not in url_to_relative:
            url_to_relative[source.source_url] = relative

    sources_updated = 0
    # Треки, которых нет на диске: соберём их в одну задачу, чтобы в
    # «Загрузках» был один раскрывающийся пункт «Импорт библиотеки», как у
    # плейлиста, а не десятки отдельных карточек по одному треку.
    queued: list[tuple[str, str, str, str]] = []  # url, title, artist, platform
    seen_urls: set[str] = set()

    for track in payload.tracks:
        url = track.url.strip()
        if not url or url in seen_urls:
            continue
        seen_urls.add(url)
        relative = track.relative_path.strip() if track.relative_path else ""
        title = (track.title or "").strip()
        artist = (track.artist or "").strip()

        # Явный relative_path из экспорта — самый надёжный способ привязки.
        path = resolve_track_path(relative) if relative else None
        if path is not None:
            await remember_source(
                session,
                file_path=path,
                url=url,
                platform=track.platform or "",
                title=title,
                artist=artist,
            )
            url_to_relative[url] = relative
            sources_updated += 1
            continue

        # Если relative_path не указан или файл не найден, ищем по URL
        # среди уже известных источников.
        existing_relative = url_to_relative.get(url)
        existing_path = (
            resolve_track_path(existing_relative) if existing_relative else None
        )
        if existing_path is not None:
            await remember_source(
                session,
                file_path=existing_path,
                url=url,
                platform=track.platform or "",
                title=title,
                artist=artist,
            )
            url_to_relative[url] = existing_relative
            sources_updated += 1
            continue

        # Уже стоит в очереди на скачивание — не дублируем.
        active = await session.scalar(
            select(Track)
            .where(Track.source_url == url)
            .where(Track.status.notin_(["done", "failed", "skipped"]))
            .limit(1)
        )
        if active is not None:
            continue

        # Скачан ранее — обновим источник, только если файл действительно
        # на месте. Раньше запись о старой загрузке молча «съедала» трек:
        # файл удалён, а импорт ничего не добавлял в очередь.
        done = await session.scalar(
            select(Track)
            .where(Track.source_url == url)
            .where(Track.status == "done")
            .where(Track.file_path.is_not(None))
            .limit(1)
        )
        if done is not None and done.file_path:
            done_path = resolve_track_path(done.file_path)
            if done_path is not None:
                await remember_source(
                    session,
                    file_path=done_path,
                    url=url,
                    platform=track.platform or "",
                    title=title,
                    artist=artist,
                )
                url_to_relative[url] = done.file_path
                sources_updated += 1
                continue

        # Файла нет (нет записи на диске, старый источник мёртв) — качаем заново.
        platform = detect_platform(url) or (track.platform or "")
        if not platform:
            continue

        queued.append((url, title or url, artist, platform))

    job: Job | None = None
    if queued:
        platforms = sorted({entry[3] for entry in queued})
        job = Job(
            url="soundtube://library-import",
            platform=platforms[0] if len(platforms) == 1 else "mixed",
            kind="sync",
            title=f"Импорт библиотеки · {datetime.now().strftime('%d.%m %H:%M')}",
            audio_format=settings.audio_format,
            total=len(queued),
            status="pending",
        )
        for position, (url, title, artist, _platform) in enumerate(queued):
            job.tracks.append(
                Track(
                    position=position,
                    title=title,
                    artist=artist or None,
                    source_url=url,
                    status="pending",
                )
            )
        session.add(job)

    await session.commit()

    if job is not None:
        await session.refresh(job)
        await runner.enqueue(job.id)

    # Перечитываем sources после обновлений, чтобы плейлисты использовали
    # актуальный маппинг URL → файл.
    sources = await sources_by_relative(session)
    url_to_relative.clear()
    for relative, source in sources.items():
        if source.source_url and source.source_url not in url_to_relative:
            url_to_relative[source.source_url] = relative

    result = await session.execute(select(Playlist).options(selectinload(Playlist.items)))
    by_name = {item.name.strip().lower(): item for item in result.scalars().unique().all()}

    created = updated = added = skipped = 0
    for entry in payload.playlists:
        name = " ".join(entry.name.split())
        if not name:
            continue
        playlist = by_name.get(name.lower())
        if playlist is None:
            playlist = Playlist(name=name)
            session.add(playlist)
            await session.flush()
            by_name[name.lower()] = playlist
            created += 1
            # У нового объекта коллекция items ещё не загружена — чтение
            # спровоцировало бы ленивый SQL, потому считаем с нуля.
            existing: set[str] = set()
            position = 0
            is_new = True
        else:
            is_new = False
            existing = {item.relative_path for item in playlist.items}
            position = max((item.position for item in playlist.items), default=-1) + 1
        touched = False
        new_rows: list[PlaylistTrack] = []
        for url in dict.fromkeys(url.strip() for url in entry.tracks):
            relative = url_to_relative.get(url)
            if relative is None or relative in existing or resolve_track_path(relative) is None:
                skipped += 1
                continue
            # Строки добавляем напрямую, а не через playlist.items.append:
            # у нового плейлиста коллекция ещё не загружена, и append
            # спровоцировал бы ленивый SQL в асинхронном контексте.
            new_rows.append(
                PlaylistTrack(
                    playlist_id=playlist.id, relative_path=relative, position=position
                )
            )
            existing.add(relative)
            position += 1
            added += 1
            touched = True
        session.add_all(new_rows)
        if touched and not is_new:
            updated += 1

    await session.commit()
    return SyncImportResponse(
        playlists_created=created,
        playlists_updated=updated,
        tracks_added=added,
        tracks_skipped=skipped,
        sources_updated=sources_updated,
        download_jobs_created=1 if job is not None else 0,
        tracks_queued=len(queued),
        job_id=job.id if job is not None else None,
    )


# ---------- настройки ----------


@router.get("/settings", response_model=SettingsOut)
async def read_settings() -> SettingsOut:
    return SettingsOut(
        music_dir=str(settings.music_dir),
        data_dir=str(settings.data_dir),
        audio_format=settings.audio_format,
        is_default_music_dir=user_settings.is_default_music_dir(),
    )


@router.patch("/settings", response_model=SettingsOut)
async def update_settings(payload: SettingsUpdate) -> SettingsOut:
    """Меняет папку музыки. `music_dir: null` — сброс на значение по умолчанию."""
    try:
        if payload.music_dir is None:
            user_settings.reset_music_dir()
        else:
            user_settings.set_music_dir(payload.music_dir)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return await read_settings()


@router.get("/stream")
async def stream(path: str) -> FileResponse:
    file = resolve_track_path(path)
    if file is None:
        raise HTTPException(status_code=404, detail="Файл не найден")
    return FileResponse(file)


@router.get("/artwork")
async def artwork(path: str, request: Request) -> Response:
    file = resolve_track_path(path)
    if file is None:
        raise HTTPException(status_code=404, detail="Файл не найден")

    cover = await asyncio.to_thread(extract_cover, file)
    if not cover:
        raise HTTPException(status_code=404, detail="Обложка не найдена")

    etag = f'"{hashlib.md5(cover).hexdigest()}"'
    if request.headers.get("if-none-match") == etag:
        return Response(
            status_code=304,
            headers={"Cache-Control": "public, max-age=604800", "ETag": etag},
        )

    return Response(
        content=cover,
        media_type=guess_image_mime(cover),
        headers={"Cache-Control": "public, max-age=604800", "ETag": etag},
    )


@router.get("/lyrics", response_model=LyricsResponse)
async def lyrics(
    path: str,
    refresh: bool = False,
    session: AsyncSession = Depends(get_session),
) -> LyricsResponse:
    """Возвращает текст песни (синхронный `.lrc` или обычный) с диска либо из сети."""
    file = resolve_track_path(path)
    if file is None:
        raise HTTPException(status_code=404, detail="Файл не найден")

    source = await session.get(LibrarySource, path)
    result = await get_track_lyrics(
        file,
        path,
        refresh=refresh,
        source_url=source.source_url if source is not None else None,
    )
    return LyricsResponse.model_validate(result)


# ---------- плейлисты ----------


def _playlist_summary(playlist: Playlist) -> PlaylistSummary:
    return PlaylistSummary(
        id=playlist.id,
        name=playlist.name,
        track_count=len(playlist.items),
        cover_path=playlist.items[0].relative_path if playlist.items else None,
    )


async def _playlist_detail(playlist: Playlist) -> PlaylistDetail:
    """Сопоставляет записи плейлиста с файлами на диске."""
    index = {track.relative_path: track for track in await asyncio.to_thread(scan_library)}

    entries: list[LibraryEntry] = []
    missing = 0
    for item in playlist.items:
        track = index.get(item.relative_path)
        if track is None:
            missing += 1
            continue
        entries.append(LibraryEntry.model_validate(track))

    summary = _playlist_summary(playlist)
    return PlaylistDetail(
        id=summary.id,
        name=summary.name,
        track_count=summary.track_count,
        cover_path=summary.cover_path,
        entries=entries,
        missing=missing,
    )


async def _playlist_or_404(playlist_id: str, session: AsyncSession) -> Playlist:
    playlist = await session.get(
        Playlist, playlist_id, options=[selectinload(Playlist.items)]
    )
    if playlist is None:
        raise HTTPException(status_code=404, detail="Плейлист не найден")
    return playlist


@router.get("/playlists", response_model=list[PlaylistSummary])
async def list_playlists(
    session: AsyncSession = Depends(get_session),
) -> list[PlaylistSummary]:
    result = await session.execute(select(Playlist).order_by(Playlist.created_at))
    return [_playlist_summary(playlist) for playlist in result.scalars().unique().all()]


@router.post(
    "/playlists", response_model=PlaylistSummary, status_code=status.HTTP_201_CREATED
)
async def create_playlist(
    payload: PlaylistCreate,
    session: AsyncSession = Depends(get_session),
) -> PlaylistSummary:
    playlist = Playlist(name=payload.name)
    session.add(playlist)
    await session.commit()
    await session.refresh(playlist, attribute_names=["items"])
    return _playlist_summary(playlist)


@router.get("/playlists/{playlist_id}", response_model=PlaylistDetail)
async def get_playlist(
    playlist_id: str,
    session: AsyncSession = Depends(get_session),
) -> PlaylistDetail:
    return await _playlist_detail(await _playlist_or_404(playlist_id, session))


@router.patch("/playlists/{playlist_id}", response_model=PlaylistSummary)
async def rename_playlist(
    playlist_id: str,
    payload: PlaylistRename,
    session: AsyncSession = Depends(get_session),
) -> PlaylistSummary:
    playlist = await _playlist_or_404(playlist_id, session)
    playlist.name = payload.name
    await session.commit()
    await session.refresh(playlist, attribute_names=["items"])
    return _playlist_summary(playlist)


@router.delete("/playlists/{playlist_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_playlist(
    playlist_id: str,
    session: AsyncSession = Depends(get_session),
) -> Response:
    playlist = await _playlist_or_404(playlist_id, session)
    await session.delete(playlist)
    await session.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/playlists/{playlist_id}/tracks", response_model=PlaylistDetail)
async def add_playlist_tracks(
    playlist_id: str,
    payload: PlaylistAddTracks,
    session: AsyncSession = Depends(get_session),
) -> PlaylistDetail:
    playlist = await _playlist_or_404(playlist_id, session)

    existing = {item.relative_path for item in playlist.items}
    position = max((item.position for item in playlist.items), default=-1) + 1

    added = 0
    for path in dict.fromkeys(payload.paths):
        if path in existing or resolve_track_path(path) is None:
            continue
        playlist.items.append(PlaylistTrack(relative_path=path, position=position))
        existing.add(path)
        position += 1
        added += 1

    if added:
        await session.commit()
        await session.refresh(playlist, attribute_names=["items"])

    return await _playlist_detail(playlist)


@router.delete("/playlists/{playlist_id}/tracks", status_code=status.HTTP_204_NO_CONTENT)
async def remove_playlist_track(
    playlist_id: str,
    path: str,
    session: AsyncSession = Depends(get_session),
) -> Response:
    playlist = await _playlist_or_404(playlist_id, session)

    for item in list(playlist.items):
        if item.relative_path == path:
            playlist.items.remove(item)
            await session.commit()
            return Response(status_code=status.HTTP_204_NO_CONTENT)

    raise HTTPException(status_code=404, detail="Трека нет в этом плейлисте")


__all__ = ["router", "TrackOut"]

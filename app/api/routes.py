from __future__ import annotations

import asyncio

from fastapi import APIRouter, Depends, HTTPException, Response, status
from fastapi.responses import FileResponse
from sqlalchemy import select
from sqlalchemy.orm import selectinload
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.core.artwork import extract_cover
from app.core.library import invalidate_cache, resolve_track_path, scan_library
from app.core.runner import runner
from app.core.tagging import guess_image_mime
from app.db import get_session
from app.models import Job, Track
from app.platforms import ResolveError, UnsupportedURL, detect_platform, resolve
from app.schemas import (
    ImportRequest,
    JobOut,
    JobSummary,
    LibraryEntry,
    LibraryResponse,
    TrackOut,
)

router = APIRouter(prefix="/api")

MAX_JOBS = 100
MAX_LIBRARY_ENTRIES = 5000


@router.get("/health")
async def health() -> dict:
    return {
        "status": "ok",
        "version": "0.1.0",
        "music_dir": str(settings.music_dir),
        "queue_running": runner.running,
    }


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
    except PermissionError as exc:
        raise HTTPException(
            status_code=409,
            detail="Файл занят: остановите воспроизведение и попробуйте снова",
        ) from exc
    except OSError as exc:
        raise HTTPException(status_code=500, detail=f"Не удалось удалить файл: {exc}") from exc

    invalidate_cache()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/stream")
async def stream(path: str) -> FileResponse:
    file = resolve_track_path(path)
    if file is None:
        raise HTTPException(status_code=404, detail="Файл не найден")
    return FileResponse(file)


@router.get("/artwork")
async def artwork(path: str) -> Response:
    file = resolve_track_path(path)
    if file is None:
        raise HTTPException(status_code=404, detail="Файл не найден")

    cover = await asyncio.to_thread(extract_cover, file)
    if not cover:
        raise HTTPException(status_code=404, detail="Обложка не найдена")

    return Response(
        content=cover,
        media_type=guess_image_mime(cover),
        headers={"Cache-Control": "public, max-age=604800"},
    )


__all__ = ["router", "TrackOut"]

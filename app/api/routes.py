from __future__ import annotations

import asyncio
import os
from datetime import datetime, timezone
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import select
from sqlalchemy.orm import selectinload
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.core.runner import runner
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

AUDIO_SUFFIXES = {".mp3", ".m4a", ".flac", ".opus", ".ogg", ".aac", ".wav", ".webm"}
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
async def library(limit: int = 1000) -> LibraryResponse:
    root = settings.music_dir
    limit = max(1, min(limit, MAX_LIBRARY_ENTRIES))
    entries: list[LibraryEntry] = []
    total_size = 0

    if root.exists():
        for directory, _dirs, files in os.walk(root):
            for filename in files:
                path = Path(directory) / filename
                if path.suffix.lower() not in AUDIO_SUFFIXES:
                    continue
                try:
                    stat = path.stat()
                    relative = path.relative_to(root)
                except OSError:
                    continue

                total_size += stat.st_size
                entries.append(
                    LibraryEntry(
                        path=str(path),
                        relative_path=relative.as_posix(),
                        artist=relative.parts[0] if len(relative.parts) > 1 else "Unknown",
                        title=path.stem,
                        size=stat.st_size,
                        modified=datetime.fromtimestamp(stat.st_mtime, tz=timezone.utc),
                    )
                )

    entries.sort(key=lambda entry: entry.relative_path.lower())
    return LibraryResponse(
        root=str(root),
        total=len(entries),
        total_size=total_size,
        entries=entries[:limit],
    )


__all__ = ["router", "TrackOut"]

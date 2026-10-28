from __future__ import annotations

import asyncio
import logging

from sqlalchemy import func, select, update
from sqlalchemy.orm import selectinload

from app.config import settings
from app.core.downloader import download_track
from app.core.library import invalidate_cache
from app.db import SessionLocal
from app.models import Job, Track
from app.platforms.base import TrackSpec

logger = logging.getLogger(__name__)


class JobRunner:
    """Processes import jobs from an in-memory queue."""

    def __init__(self) -> None:
        self._queue: asyncio.Queue[str] = asyncio.Queue()
        self._workers: list[asyncio.Task] = []

    @property
    def running(self) -> bool:
        return bool(self._workers)

    async def start(self) -> None:
        if self._workers:
            return
        for index in range(max(1, settings.max_concurrent_jobs)):
            task = asyncio.create_task(self._worker(index), name=f"melodown-worker-{index}")
            self._workers.append(task)
        logger.info("Очередь запущена, воркеров: %s", len(self._workers))

    async def stop(self) -> None:
        for task in self._workers:
            task.cancel()
        if self._workers:
            await asyncio.gather(*self._workers, return_exceptions=True)
        self._workers.clear()

    async def enqueue(self, job_id: str) -> None:
        await self._queue.put(job_id)

    async def _worker(self, index: int) -> None:
        while True:
            job_id = await self._queue.get()
            try:
                await self._process(job_id)
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("Воркер %s упал на задаче %s", index, job_id)
                await self._fail_job(job_id)
            finally:
                self._queue.task_done()

    async def _process(self, job_id: str) -> None:
        async with SessionLocal() as session:
            job = await session.get(Job, job_id, options=[selectinload(Job.tracks)])
            if job is None:
                return
            if job.status == "done" and job.failed == 0:
                return

            job.status = "running"
            job.error = None
            await session.commit()

            track_ids = [track.id for track in job.tracks if track.status != "skipped"]
            platform = job.platform
            audio_format = job.audio_format

        logger.info("Задача %s: треков к обработке %s", job_id, len(track_ids))
        semaphore = asyncio.Semaphore(max(1, settings.max_concurrent_tracks))

        async def run(track_id: int) -> None:
            async with semaphore:
                await self._download_one(job_id, track_id, platform, audio_format)

        await asyncio.gather(*(run(track_id) for track_id in track_ids), return_exceptions=True)
        await self._finalize(job_id)

    async def _download_one(
        self,
        job_id: str,
        track_id: int,
        platform: str,
        audio_format: str,
    ) -> None:
        async with SessionLocal() as session:
            track = await session.get(Track, track_id)
            if track is None:
                return
            if track.status == "done" and track.file_path:
                return

            duplicate = await session.scalar(
                select(Track)
                .where(
                    Track.source_url == track.source_url,
                    Track.status == "done",
                    Track.id != track.id,
                )
                .limit(1)
            )
            if duplicate is not None:
                track.status = "skipped"
                track.file_path = duplicate.file_path
                track.error = "Уже есть в библиотеке"
                await session.commit()
                await self._refresh_counters(job_id)
                return

            spec = TrackSpec(
                title=track.title,
                url=track.source_url,
                artist=track.artist,
                album=track.album,
                duration=track.duration,
            )
            track.status = "downloading"
            track.error = None
            await session.commit()

        try:
            result = await download_track(
                spec,
                platform=platform,
                audio_format=audio_format,
            )
        except Exception as exc:
            logger.warning("Трек %s не скачался: %s", spec.url, exc)
            async with SessionLocal() as session:
                track = await session.get(Track, track_id)
                if track is not None:
                    track.status = "failed"
                    track.error = str(exc)[:1000] or exc.__class__.__name__
                    await session.commit()
            await self._refresh_counters(job_id)
            return

        async with SessionLocal() as session:
            track = await session.get(Track, track_id)
            if track is not None:
                track.status = "done"
                track.file_path = str(result.path)
                track.title = result.title
                track.artist = result.artist
                track.album = result.album
                track.duration = result.duration
                track.error = None
                await session.commit()
        await self._refresh_counters(job_id)
        invalidate_cache()

    async def _refresh_counters(self, job_id: str) -> None:
        async with SessionLocal() as session:
            rows = await session.execute(
                select(Track.status, func.count())
                .where(Track.job_id == job_id)
                .group_by(Track.status)
            )
            counts = dict(rows.all())

            job = await session.get(Job, job_id)
            if job is None:
                return
            job.completed = counts.get("done", 0) + counts.get("skipped", 0)
            job.failed = counts.get("failed", 0)
            await session.commit()

    async def _finalize(self, job_id: str) -> None:
        async with SessionLocal() as session:
            job = await session.get(Job, job_id, options=[selectinload(Job.tracks)])
            if job is None:
                return
            if job.failed == 0:
                job.status = "done"
                job.error = None
            elif job.completed > 0:
                job.status = "partial"
                job.error = f"Не скачалось треков: {job.failed}"
            else:
                job.status = "failed"
                job.error = job.error or "Не удалось скачать ни одного трека"
            await session.commit()
        logger.info("Задача %s завершена со статусом %s", job_id, job.status)

    async def _fail_job(self, job_id: str) -> None:
        async with SessionLocal() as session:
            job = await session.get(Job, job_id)
            if job is None:
                return
            job.status = "failed"
            job.error = "Внутренняя ошибка обработчика"
            await session.commit()


runner = JobRunner()


async def recover_stale_jobs() -> None:
    """After a restart, put interrupted jobs back into the queue."""
    async with SessionLocal() as session:
        await session.execute(
            update(Track).where(Track.status == "downloading").values(status="pending")
        )
        jobs = (
            await session.execute(
                select(Job).where(Job.status.in_(("running", "pending")))
            )
        ).scalars().all()
        job_ids = []
        for job in jobs:
            job.status = "pending"
            job_ids.append(job.id)
        await session.commit()

    for job_id in job_ids:
        await runner.enqueue(job_id)
    if job_ids:
        logger.info("Возобновлено задач после рестарта: %s", len(job_ids))

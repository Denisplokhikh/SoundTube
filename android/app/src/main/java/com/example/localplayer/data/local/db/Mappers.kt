package com.example.localplayer.data.local.db

import com.example.localplayer.data.local.db.dao.PlaylistEntryRow
import com.example.localplayer.data.local.db.dao.PlaylistSummaryRow
import com.example.localplayer.data.local.db.entity.PlaylistEntity
import com.example.localplayer.data.local.db.entity.TrackEntity
import com.example.localplayer.domain.model.Playlist
import com.example.localplayer.domain.model.PlaylistEntry
import com.example.localplayer.domain.model.SourcePlatform
import com.example.localplayer.domain.model.Track

fun TrackEntity.toDomain(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    durationMs = durationMs,
    sourceUrl = sourceUrl,
    sourcePlatform = SourcePlatform.fromKey(sourcePlatform),
    localFilePath = localFilePath,
    thumbnailPath = thumbnailPath,
    fileHash = fileHash,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
)

fun Track.toEntity(): TrackEntity = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    durationMs = durationMs,
    sourceUrl = sourceUrl,
    sourcePlatform = sourcePlatform.key,
    localFilePath = localFilePath,
    thumbnailPath = thumbnailPath,
    fileHash = fileHash,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
)

fun PlaylistEntity.toDomain(trackCount: Int = 0): Playlist = Playlist(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    trackCount = trackCount,
)

fun PlaylistSummaryRow.toDomain(): Playlist = playlist.toDomain(trackCount)

fun PlaylistEntryRow.toDomain(): PlaylistEntry = PlaylistEntry(
    track = track.toDomain(),
    position = position,
)

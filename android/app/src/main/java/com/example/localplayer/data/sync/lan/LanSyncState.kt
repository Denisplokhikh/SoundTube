package com.example.localplayer.data.sync.lan

/** Прогресс LAN-синхронизации, который читает экран настроек. */
sealed interface LanSyncState {
    data object Idle : LanSyncState

    /** Логин и разбор снимка библиотеки. */
    data object Connecting : LanSyncState

    /** Треки качаются с ПК: `done` из `total`, `current` — что именно качается. */
    data class Syncing(
        val done: Int,
        val total: Int,
        val current: String,
    ) : LanSyncState

    data class Completed(
        val downloaded: Int,
        val alreadyOnDisk: Int,
        val tracksImported: Int,
        val playlistsAdded: Int,
        val failed: Int = 0,
    ) : LanSyncState

    data class Failed(val message: String) : LanSyncState
}

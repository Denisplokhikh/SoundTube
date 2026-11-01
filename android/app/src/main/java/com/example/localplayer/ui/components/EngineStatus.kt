package com.example.localplayer.ui.components

import com.example.localplayer.data.downloader.EngineState

/** Состояние yt-dlp одной строкой — одинаково на «Загрузках» и в настройках. */
fun EngineState.describe(): String = when (this) {
    EngineState.NotInitialized -> "не инициализирован"
    EngineState.Initializing -> "инициализация…"
    is EngineState.Ready -> "готов" + (version?.let { " · $it" } ?: "")
    EngineState.Updating -> "обновление…"
    is EngineState.Failed -> "ошибка: $message"
}

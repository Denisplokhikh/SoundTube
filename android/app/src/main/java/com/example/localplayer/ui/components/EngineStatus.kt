package com.example.localplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.localplayer.R
import com.example.localplayer.data.downloader.EngineState

/** Состояние yt-dlp одной строкой — одинаково на «Загрузках» и в настройках. */
@Composable
fun EngineState.describe(): String = when (this) {
    EngineState.NotInitialized -> stringResource(R.string.engine_not_initialized)
    EngineState.Initializing -> stringResource(R.string.engine_initializing)
    is EngineState.Ready -> version
        ?.let { stringResource(R.string.engine_ready_version, it) }
        ?: stringResource(R.string.engine_ready)
    EngineState.Updating -> stringResource(R.string.engine_updating)
    is EngineState.Failed -> stringResource(R.string.engine_failed, message)
}

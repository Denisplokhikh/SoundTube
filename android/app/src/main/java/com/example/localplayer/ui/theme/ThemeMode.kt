package com.example.localplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/** Какую тему показывать: за системой или принудительно светлую/тёмную. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    val label: String
        get() = when (this) {
            SYSTEM -> "Система"
            LIGHT -> "Светлая"
            DARK -> "Тёмная"
        }
}

/** Итоговая тема с учётом системной настройки. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

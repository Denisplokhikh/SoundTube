package com.example.localplayer.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.example.localplayer.R

/** Какую тему показывать: за системой или принудительно светлую/тёмную. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    @StringRes
    fun labelRes(): Int = when (this) {
        SYSTEM -> R.string.theme_system
        LIGHT -> R.string.theme_light
        DARK -> R.string.theme_dark
    }
}

/** Итоговая тема с учётом системной настройки. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

package com.example.localplayer.ui.theme

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Тема на уровне Activity: нужна до того, как появятся экраны. */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val preferences: ThemePreferences,
) : ViewModel() {

    val mode: StateFlow<ThemeMode> = preferences.mode

    fun setMode(mode: ThemeMode) = preferences.setMode(mode)
}

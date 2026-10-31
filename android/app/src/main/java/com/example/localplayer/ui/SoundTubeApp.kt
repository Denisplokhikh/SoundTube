package com.example.localplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.localplayer.ui.player.MiniPlayer
import com.example.localplayer.ui.player.PlayerViewModel
import com.example.localplayer.ui.screens.downloads.DownloadSmokeScreen
import com.example.localplayer.ui.screens.library.LibraryScreen

/** Маршруты нижней навигации. */
object Routes {
    const val LIBRARY = "library"
    const val DOWNLOADS = "downloads"
}

/**
 * Экран приложения целиком: две вкладки и мини-плеер над навигацией.
 *
 * Плеер один на всё приложение (через [PlayerViewModel]), поэтому он продолжает играть
 * при переключении вкладок и сворачивании.
 *
 * @param startDestination вкладка, с которой начинаем: по умолчанию библиотека,
 *   в debug-сборке её можно задать через intent (тапы по экрану MIUI блокирует).
 */
@Composable
fun SoundTubeApp(
    modifier: Modifier = Modifier,
    startDestination: String = Routes.LIBRARY,
    navController: NavHostController = rememberNavController(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Column {
                if (player.hasTrack) {
                    MiniPlayer(
                        state = player,
                        onPlayPause = playerViewModel::playPause,
                        onNext = playerViewModel::next,
                    )
                }

                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.LIBRARY,
                        onClick = { navController.switchTab(Routes.LIBRARY) },
                        icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                        label = { Text("Библиотека") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.DOWNLOADS,
                        onClick = { navController.switchTab(Routes.DOWNLOADS) },
                        icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        label = { Text("Загрузки") },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LIBRARY) { LibraryScreen() }
            composable(Routes.DOWNLOADS) { DownloadSmokeScreen() }
        }
    }
}

/** Переключение вкладки без накопления копий экрана в back stack. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

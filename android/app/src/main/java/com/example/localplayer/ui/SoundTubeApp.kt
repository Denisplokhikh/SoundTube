package com.example.localplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.localplayer.R
import com.example.localplayer.ui.navigation.Routes
import com.example.localplayer.ui.player.MiniPlayer
import com.example.localplayer.ui.player.NowPlayingScreen
import com.example.localplayer.ui.player.PlayerViewModel
import com.example.localplayer.ui.screens.downloads.DownloadsScreen
import com.example.localplayer.ui.screens.playlists.PlaylistDetailScreen
import com.example.localplayer.ui.screens.playlists.PlaylistsScreen
import com.example.localplayer.ui.screens.settings.SettingsScreen
import com.example.localplayer.ui.screens.tracks.TracksScreen

private data class Tab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val tabs = listOf(
    Tab(Routes.TRACKS, R.string.tracks, Icons.Rounded.LibraryMusic),
    Tab(Routes.PLAYLISTS, R.string.playlists, Icons.AutoMirrored.Rounded.QueueMusic),
    Tab(Routes.DOWNLOADS, R.string.downloads, Icons.Rounded.Download),
    Tab(Routes.SETTINGS, R.string.settings, Icons.Rounded.Settings),
)

/**
 * Экран приложения целиком: четыре вкладки, мини-плеер над навигацией
 * и полноэкранный плеер поверх всего.
 *
 * Плеер один на всё приложение (через [PlayerViewModel]), поэтому он продолжает играть
 * при переключении вкладок и сворачивании.
 *
 * @param startDestination вкладка, с которой начинаем: по умолчанию треки,
 *   в debug-сборке её можно задать через intent (тапы по экрану MIUI блокирует).
 */
@Composable
fun SoundTubeApp(
    modifier: Modifier = Modifier,
    startDestination: String = Routes.TRACKS,
    navController: NavHostController = rememberNavController(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showChrome = currentRoute != Routes.NOW_PLAYING

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Статус-бар и навигацию по краям обрабатывают сами TopAppBar/NavigationBar,
        // иначе получается двойной отступ.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showChrome) {
                Column {
                    AnimatedVisibility(
                        visible = player.hasTrack,
                        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
                    ) {
                        MiniPlayer(
                            state = player,
                            onPlayPause = playerViewModel::playPause,
                            onNext = playerViewModel::next,
                            onExpand = {
                                navController.navigate(Routes.NOW_PLAYING) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = tab.isSelected(currentRoute),
                                onClick = {
                                    if (!tab.isSelected(currentRoute)) {
                                        navController.switchTab(tab.route)
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.TRACKS) {
                TracksScreen(
                    onOpenDownloads = { navController.switchTab(Routes.DOWNLOADS) },
                    playerViewModel = playerViewModel,
                )
            }

            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(
                    onOpenPlaylist = { playlistId ->
                        navController.navigate(Routes.playlistDetail(playlistId))
                    },
                )
            }

            composable(Routes.DOWNLOADS) {
                DownloadsScreen(playerViewModel = playerViewModel)
            }

            composable(Routes.SETTINGS) {
                SettingsScreen()
            }

            composable(
                route = Routes.PLAYLIST_DETAIL,
                arguments = listOf(
                    navArgument(Routes.ARG_PLAYLIST_ID) { type = NavType.StringType },
                ),
            ) {
                PlaylistDetailScreen(
                    onBack = { navController.popBackStack() },
                    playerViewModel = playerViewModel,
                )
            }

            composable(Routes.NOW_PLAYING) {
                NowPlayingScreen(
                    onBack = { navController.popBackStack() },
                    playerViewModel = playerViewModel,
                )
            }
        }
    }
}

/** Вкладка «Плейлисты» остаётся выбранной и внутри конкретного плейлиста. */
private fun Tab.isSelected(currentRoute: String?): Boolean = when (route) {
    Routes.PLAYLISTS -> currentRoute == Routes.PLAYLISTS || currentRoute == Routes.PLAYLIST_DETAIL
    else -> currentRoute == route
}

/** Переключение вкладки без накопления копий экрана в back stack. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

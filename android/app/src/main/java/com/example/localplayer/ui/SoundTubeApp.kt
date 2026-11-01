package com.example.localplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.example.localplayer.ui.theme.SoundTubeTheme

private data class Tab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

/**
 * Три вкладки нижнего бара — как три пункта навигации десктопного SoundTube.
 * Настройки живут отдельным экраном за шестерёнкой в шапке, чтобы не плодить
 * четвёртую вкладку, которой нет на ПК.
 */
private val tabs = listOf(
    Tab(Routes.TRACKS, R.string.library, Icons.Rounded.LibraryMusic),
    Tab(Routes.DOWNLOADS, R.string.downloads, Icons.Rounded.Download),
    Tab(Routes.PLAYLISTS, R.string.playlists, Icons.AutoMirrored.Rounded.QueueMusic),
)

/**
 * Экран приложения целиком: три вкладки, мини-плеер над навигацией
 * и полноэкранные маршруты поверх (плеер, плейлист, настройки).
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
    val showChrome = currentRoute == null || tabs.any { it.isSelected(currentRoute) }

    val openSettings: () -> Unit = {
        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Статус-бар и навигацию по краям обрабатывают шапки экранов и нижний бар,
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

                    SoundTubeBottomBar(
                        currentRoute = currentRoute,
                        onSelect = { route ->
                            if (route != currentRoute) navController.switchTab(route)
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            // Переходы мгновенные: по умолчанию Navigation Compose делает
            // fade + scale, из-за чего новая страница «проявляется» поверх старой.
            // Здесь экран появляется сразу, как смена кадра в терминале.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Routes.TRACKS) {
                TracksScreen(
                    onOpenDownloads = { navController.switchTab(Routes.DOWNLOADS) },
                    onOpenSettings = openSettings,
                    playerViewModel = playerViewModel,
                )
            }

            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(
                    onOpenPlaylist = { playlistId ->
                        navController.navigate(Routes.playlistDetail(playlistId))
                    },
                    onOpenSettings = openSettings,
                )
            }

            composable(Routes.DOWNLOADS) {
                DownloadsScreen(
                    onOpenSettings = openSettings,
                    playerViewModel = playerViewModel,
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
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

/**
 * Нижний бар: плоский чёрный фон, верхняя рамка 1.dp, три пункта.
 * Активный пункт — плашка #161C16 и шалфейно-зелёный текст.
 */
@Composable
private fun SoundTubeBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    val colors = SoundTubeTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .drawBehind {
                drawLine(
                    color = colors.border,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEach { tab ->
                val selected = tab.isSelected(currentRoute)
                val contentColor = if (selected) colors.primary else colors.textSecondary

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) colors.navActive else Color.Transparent)
                        .clickable { onSelect(tab.route) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        maxLines = 1,
                    )
                }
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

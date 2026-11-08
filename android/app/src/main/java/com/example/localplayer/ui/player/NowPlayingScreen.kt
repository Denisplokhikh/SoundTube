package com.example.localplayer.ui.player

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localplayer.R
import com.example.localplayer.domain.model.RepeatMode
import com.example.localplayer.ui.components.AddToPlaylistDialog
import com.example.localplayer.ui.components.EmptyState
import com.example.localplayer.ui.components.TrackArtwork
import com.example.localplayer.ui.components.formatDuration
import com.example.localplayer.ui.theme.DarkSoundTubeColors
import com.example.localplayer.ui.theme.LocalSoundTubeColors
import com.example.localplayer.ui.theme.SoundTubeTheme
import java.util.Locale

/**
 * Полноэкранный плеер в раскладке Spotify: шапка с контекстом, крупная обложка,
 * название с круглой кнопкой, перемотка, управление, строка устройства и
 * нижняя карточка текста.
 *
 * Фон — градиент из доминирующего цвета обложки (см. [rememberPlayerPalette]),
 * поэтому текст здесь всегда белый: экран остаётся тёмным в любой теме.
 * Очередь и текст песни открываются нижними шторками, а свайп вниз по шапке
 * или обложке убирает лист обратно в мини-плеер.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    contentAlpha: () -> Float = { 1f },
    artwork: (@Composable (Modifier) -> Unit)? = null,
) {
    val state by playerViewModel.state.collectAsStateWithLifecycle()
    val positionMs = playerViewModel.positionMs.collectAsStateWithLifecycle()
    val lyricsState by playerViewModel.lyricsState.collectAsStateWithLifecycle()
    val playlists by playerViewModel.playlists.collectAsStateWithLifecycle()
    val notice by playerViewModel.notice.collectAsStateWithLifecycle()
    val palette = rememberPlayerPalette(state.artworkUri)
    val context = LocalContext.current

    // Активная строка текста: пересчёт идёт по тику позиции, а перерисовку
    // вызывает только смена строки.
    var activeLine by remember { mutableStateOf(-1) }
    LaunchedEffect(lyricsState) {
        val loaded = lyricsState as? LyricsUiState.Loaded
        if (loaded == null || !loaded.lyrics.synced) {
            activeLine = -1
            return@LaunchedEffect
        }
        playerViewModel.positionMs.collect { position ->
            val lines = loaded.lyrics.lines
            var index = -1
            for (i in lines.indices) {
                if (lines[i].timeMs <= position) index = i else break
            }
            if (index != activeLine) activeLine = index
        }
    }

    // Все компоненты плеера читают цвета темы, поэтому подменяем палитру на
    // «тёмное стекло» с акцентом обложки — так шторки и пустые состояния
    // выглядят так же, как основной экран.
    val playerColors = remember(palette) {
        DarkSoundTubeColors.copy(
            background = palette.bottom,
            surface = palette.middle,
            surfaceInput = palette.middle,
            surfaceRaised = Color.White.copy(alpha = 0.10f),
            border = Color.White.copy(alpha = 0.14f),
            borderStrong = Color.White.copy(alpha = 0.22f),
            primary = palette.accent,
            onPrimary = Color(0xFF0A1410),
            statusGreen = palette.accent,
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.68f),
            playerSurface = palette.middle,
            divider = Color.White.copy(alpha = 0.12f),
            navActive = Color.White.copy(alpha = 0.10f),
            error = Color(0xFFFF9C92),
            warning = Color(0xFFFFD27A),
        )
    }
    val playerScheme = remember(palette) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF0A1410),
            background = palette.bottom,
            surface = palette.middle,
            onBackground = Color.White,
            onSurface = Color.White,
            surfaceVariant = Color.White.copy(alpha = 0.10f),
            onSurfaceVariant = Color.White.copy(alpha = 0.68f),
            outline = Color.White.copy(alpha = 0.24f),
            outlineVariant = Color.White.copy(alpha = 0.16f),
            error = Color(0xFFFF9C92),
        )
    }
    val baseTypography = MaterialTheme.typography
    val baseShapes = MaterialTheme.shapes

    // Подтверждение «добавлено в плейлист» приходит из ViewModel.
    LaunchedEffect(notice) {
        val message = notice ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        playerViewModel.consumeNotice()
    }

    MaterialTheme(colorScheme = playerScheme, typography = baseTypography, shapes = baseShapes) {
        CompositionLocalProvider(LocalSoundTubeColors provides playerColors) {
            var menuOpen by remember { mutableStateOf(false) }
            var showQueue by remember { mutableStateOf(false) }
            var showLyrics by remember { mutableStateOf(false) }
            var showAddToPlaylist by remember { mutableStateOf(false) }

            val shareTrack: () -> Unit = {
                shareCurrentTrack(context, state.title, state.artist)
            }

            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val insets = WindowInsets.systemBars.asPaddingValues()
                val usableHeight = maxHeight -
                    insets.calculateTopPadding() -
                    insets.calculateBottomPadding()
                val compact = usableHeight < 600.dp
                val coverSize = if (compact) {
                    minOf(
                        maxWidth - 32.dp,
                        (usableHeight - 260.dp).coerceAtLeast(96.dp),
                    )
                } else {
                    minOf(
                        maxWidth - 32.dp,
                        420.dp,
                        (usableHeight - 430.dp).coerceAtLeast(180.dp),
                    )
                }

                // Фон: ступени из цвета обложки + мягкая подсветка сверху.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(palette.top, palette.middle, palette.bottom),
                                startY = 0f,
                                endY = with(density) { maxHeight.toPx() },
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    palette.accent.copy(alpha = 0.10f),
                                    Color.Transparent,
                                ),
                                startY = 0f,
                                endY = with(density) { (maxHeight * 0.46f).toPx() },
                            ),
                        ),
                )

                // Весь интерактивный слой проявляется по мере раскрытия листа.
                // Фон выше рисуется всегда, чтобы выезжающий контейнер был непрозрачным.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = contentAlpha() },
                ) {
                    if (!state.hasTrack) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .systemBarsPadding(),
                        ) {
                            PlayerTopBar(
                                onCollapse = onBack,
                                menuOpen = false,
                                onMenuDismiss = {},
                                onToggleMenu = null,
                                onAddToPlaylist = null,
                                onShare = null,
                                onQueue = null,
                                onLyrics = null,
                            )
                            EmptyState(
                                icon = Icons.Rounded.MusicNote,
                                title = stringResource(R.string.nothing_playing_title),
                                message = stringResource(R.string.nothing_playing_message),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .systemBarsPadding(),
                        ) {
                            PlayerTopBar(
                                onCollapse = onBack,
                                menuOpen = menuOpen,
                                onMenuDismiss = { menuOpen = false },
                                onAddToPlaylist = {
                                    menuOpen = false
                                    showAddToPlaylist = true
                                },
                                onShare = {
                                    menuOpen = false
                                    shareTrack()
                                },
                                onQueue = {
                                    menuOpen = false
                                    showQueue = true
                                },
                                onLyrics = {
                                    menuOpen = false
                                    showLyrics = true
                                },
                                onToggleMenu = { menuOpen = !menuOpen },
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (artwork != null) {
                                    // Слот под морфящуюся обложку: размер совпадает с полноэкранным,
                                    // а картинку рисует слой ExpandablePlayerHost.
                                    artwork(Modifier.size(coverSize))
                                } else {
                                    TrackArtwork(
                                        artwork = state.artworkUri,
                                        modifier = Modifier
                                            .size(coverSize)
                                            .shadow(
                                                elevation = 24.dp,
                                                shape = RoundedCornerShape(8.dp),
                                                clip = false,
                                            ),
                                        cornerRadius = 8.dp,
                                        iconSize = 56.dp,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(if (compact) 10.dp else 18.dp))

                            PlayerMeta(
                                title = state.title.ifBlank { stringResource(R.string.untitled) },
                                artist = state.artist.ifBlank {
                                    stringResource(R.string.unknown_artist)
                                },
                                onAddToPlaylist = { showAddToPlaylist = true },
                            )

                            Spacer(modifier = Modifier.height(if (compact) 2.dp else 6.dp))

                            PlayerSeekBar(
                                positionMs = { positionMs.value },
                                durationMs = state.durationMs,
                                trackId = state.currentTrackId,
                                onSeek = playerViewModel::seekTo,
                            )

                            Spacer(modifier = Modifier.height(if (compact) 0.dp else 4.dp))

                            PlayerControls(
                                isPlaying = state.isPlaying,
                                isBuffering = state.isBuffering,
                                shuffle = state.shuffle,
                                repeatMode = state.repeatMode,
                                hasNext = state.hasNext,
                                onPlayPause = playerViewModel::playPause,
                                onPrevious = playerViewModel::previous,
                                onNext = playerViewModel::next,
                                onToggleShuffle = playerViewModel::toggleShuffle,
                                onCycleRepeat = playerViewModel::cycleRepeat,
                                compact = compact,
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            if (!compact) {
                                PlayerDeviceRow(
                                    onShare = shareTrack,
                                    onQueue = { showQueue = true },
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                PlayerLyricsCard(onClick = { showLyrics = true })
                            }
                        }
                    }
                }
            }

            if (showQueue) {
                ModalBottomSheet(
                    onDismissRequest = { showQueue = false },
                    containerColor = playerColors.surface,
                    contentColor = Color.White,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    PlayerQueueSheetContent(
                        queue = state.queue,
                        currentIndex = state.currentIndex,
                        onPick = { index ->
                            playerViewModel.seekToIndex(index)
                            showQueue = false
                        },
                    )
                }
            }

            if (showLyrics) {
                ModalBottomSheet(
                    onDismissRequest = { showLyrics = false },
                    containerColor = playerColors.surface,
                    contentColor = Color.White,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    PlayerLyricsSheetContent(
                        state = lyricsState,
                        activeLine = activeLine,
                        onSeek = playerViewModel::seekTo,
                        onRetry = playerViewModel::retryLyrics,
                    )
                }
            }

            if (showAddToPlaylist) {
                AddToPlaylistDialog(
                    trackTitle = state.title.ifBlank { stringResource(R.string.untitled) },
                    playlists = playlists,
                    onDismiss = { showAddToPlaylist = false },
                    onPick = { playlistId ->
                        showAddToPlaylist = false
                        playerViewModel.addCurrentToPlaylist(playlistId)
                    },
                    onCreate = { name ->
                        showAddToPlaylist = false
                        playerViewModel.createPlaylistAndAdd(name)
                    },
                )
            }
        }
    }
}

/** Шапка: шеврон вниз, контекст по центру и «...» с меню действий. */
@Composable
private fun PlayerTopBar(
    onCollapse: () -> Unit,
    menuOpen: Boolean,
    onMenuDismiss: () -> Unit,
    onToggleMenu: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onQueue: (() -> Unit)?,
    onLyrics: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.action_collapse),
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }

        Text(
            text = stringResource(R.string.now_playing),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (onToggleMenu != null) {
                IconButton(onClick = onToggleMenu, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = stringResource(R.string.player_more),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = onMenuDismiss,
                ) {
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_add_to_playlist)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = onAddToPlaylist,
                        )
                    }
                    if (onShare != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_share)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.IosShare,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = onShare,
                        )
                    }
                    if (onQueue != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.queue)) },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = onQueue,
                        )
                    }
                    if (onLyrics != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lyrics)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.OpenInFull,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = onLyrics,
                        )
                    }
                }
            }
        }
    }
}

/** Название, исполнитель и круглая кнопка «В плейлист» справа. */
@Composable
private fun PlayerMeta(title: String, artist: String, onAddToPlaylist: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SoundTubeTheme.colors.primary)
                .clickable(onClick = onAddToPlaylist),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.action_add_to_playlist),
                tint = Color(0xFF0A1410),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Перемотка: слева прошедшее время, справа остаток со знаком минус. */
@Composable
private fun PlayerSeekBar(
    positionMs: () -> Long,
    durationMs: Long,
    trackId: String?,
    onSeek: (Long) -> Unit,
) {
    var dragged by remember(trackId) { mutableStateOf<Float?>(null) }
    val position = positionMs()
    val progress = if (durationMs > 0L) {
        (position / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val value = dragged ?: progress
    val shownPosition = dragged?.let { (it * durationMs).toLong() } ?: position

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = { if (durationMs > 0L) dragged = it },
            onValueChangeFinished = {
                dragged?.let { onSeek((it * durationMs).toLong()) }
                dragged = null
            },
            enabled = durationMs > 0L,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = Color.White.copy(alpha = 0.5f),
                disabledActiveTrackColor = Color.White.copy(alpha = 0.5f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = shownPosition.formatDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.58f),
            )
            Text(
                text = if (durationMs > 0L) {
                    formatRemaining((durationMs - shownPosition).coerceAtLeast(0L))
                } else {
                    "--:--"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.58f),
            )
        }
    }
}

/** Строка управления: перемешать, назад, большая пауза, вперёд, повтор. */
@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    shuffle: Boolean,
    repeatMode: RepeatMode,
    hasNext: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    compact: Boolean,
) {
    val accent = SoundTubeTheme.colors.primary
    val regular = Color.White.copy(alpha = 0.92f)
    val playSize = if (compact) 52.dp else 64.dp
    val iconSize = if (compact) 20.dp else 24.dp
    val buttonSize = if (compact) 40.dp else 48.dp

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = onToggleShuffle, modifier = Modifier.size(buttonSize)) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = if (shuffle) {
                    stringResource(R.string.cd_shuffle_on)
                } else {
                    stringResource(R.string.cd_shuffle_off)
                },
                tint = if (shuffle) accent else regular,
                modifier = Modifier.size(iconSize),
            )
        }

        IconButton(onClick = onPrevious, modifier = Modifier.size(buttonSize)) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.cd_previous),
                tint = regular,
                modifier = Modifier.size(iconSize + 6.dp),
            )
        }

        Box(
            modifier = Modifier
                .size(playSize)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(playSize * 0.36f),
                    strokeWidth = 2.5.dp,
                    color = Color(0xFF0A1410),
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) {
                        stringResource(R.string.cd_pause)
                    } else {
                        stringResource(R.string.cd_play)
                    },
                    tint = Color(0xFF0A1410),
                    modifier = Modifier.size(if (isPlaying) playSize * 0.42f else playSize * 0.46f),
                )
            }
        }

        IconButton(onClick = onNext, enabled = hasNext, modifier = Modifier.size(buttonSize)) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.cd_next),
                tint = if (hasNext) regular else regular.copy(alpha = 0.35f),
                modifier = Modifier.size(iconSize + 6.dp),
            )
        }

        IconButton(onClick = onCycleRepeat, modifier = Modifier.size(buttonSize)) {
            Icon(
                imageVector = if (repeatMode == RepeatMode.ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                },
                contentDescription = when (repeatMode) {
                    RepeatMode.OFF -> stringResource(R.string.cd_repeat_off)
                    RepeatMode.ALL -> stringResource(R.string.cd_repeat_all)
                    RepeatMode.ONE -> stringResource(R.string.cd_repeat_one)
                },
                tint = if (repeatMode == RepeatMode.OFF) regular else accent,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** «Это устройство» слева и кнопки «поделиться»/«очередь» справа. */
@Composable
private fun PlayerDeviceRow(onShare: () -> Unit, onQueue: () -> Unit) {
    val accent = SoundTubeTheme.colors.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Speaker,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.player_device_this),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onShare, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Rounded.IosShare,
                contentDescription = stringResource(R.string.player_share),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onQueue, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = stringResource(R.string.queue),
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Нижняя карточка-ручка для текста песни. */
@Composable
private fun PlayerLyricsCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .width(88.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.35f)),
        )
        Text(
            text = stringResource(R.string.lyrics),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.OpenInFull,
                contentDescription = stringResource(R.string.lyrics),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** «-2:36»: остаток до конца трека. Часы появляются только у длинных записей. */
private fun formatRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "-%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "-%d:%02d", minutes, seconds)
    }
}

/** Системный «поделиться»: артист — название, без ссылок на локальный файл. */
private fun shareCurrentTrack(context: Context, title: String, artist: String) {
    val text = if (artist.isBlank()) title else "$artist — $title"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.player_share)),
    )
}

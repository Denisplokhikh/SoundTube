package com.example.localplayer.ui.screens.tracks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localplayer.domain.model.Track
import com.example.localplayer.ui.components.AddToPlaylistDialog
import com.example.localplayer.ui.components.ConfirmDeleteTrackDialog
import com.example.localplayer.ui.components.EmptyState
import com.example.localplayer.ui.components.SoundTubeButton
import com.example.localplayer.ui.components.SoundTubeIconButton
import com.example.localplayer.ui.components.SoundTubeTopBar
import com.example.localplayer.ui.components.TrackRow
import com.example.localplayer.ui.components.formatFileSize
import com.example.localplayer.ui.components.tracksWord
import com.example.localplayer.ui.player.PlayerViewModel
import com.example.localplayer.ui.theme.SoundTubeTheme
import kotlinx.coroutines.delay

/**
 * Вкладка «Библиотека»: всё, что уже лежит на диске.
 *
 * Плеер один на всё приложение, поэтому нажатие на строку сразу ставит в очередь
 * текущий (возможно, отфильтрованный) список с выбранного места.
 */
@Composable
fun TracksScreen(
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    tracksViewModel: TracksViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val colors = SoundTubeTheme.colors
    val ui by tracksViewModel.uiState.collectAsStateWithLifecycle()
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val action by tracksViewModel.action.collectAsStateWithLifecycle()
    val playlists by tracksViewModel.playlists.collectAsStateWithLifecycle()
    val message by tracksViewModel.message.collectAsStateWithLifecycle()
    var searchVisible by rememberSaveable { mutableStateOf(false) }

    val trackIds = remember(ui.tracks) { ui.tracks.map(Track::id) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        SoundTubeTopBar(
            title = "Библиотека",
            subtitle = when {
                ui.totalCount == 0 -> "Локальная библиотека"
                ui.isFiltering -> "Найдено ${ui.tracks.size} из ${ui.totalCount} · ${ui.sort.label.lowercase()}"
                ui.librarySize > 0L -> "${ui.totalCount} ${ui.totalCount.tracksWord()} · " +
                    ui.librarySize.formatFileSize()
                else -> "${ui.totalCount} ${ui.totalCount.tracksWord()}"
            },
            actions = {
                SoundTubeIconButton(
                    icon = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (searchVisible) "Закрыть поиск" else "Поиск",
                    bordered = false,
                    onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) tracksViewModel.clearQuery()
                    },
                )
                SoundTubeIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "Настройки",
                    bordered = false,
                    onClick = onOpenSettings,
                )
            },
        )

        message?.let { note ->
            LibraryNotice(
                text = note.text,
                isError = note.isError,
                onDismiss = tracksViewModel::clearMessage,
            )
        }

        if (searchVisible) {
            OutlinedTextField(                value = ui.query,
                onValueChange = tracksViewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Трек, артист, альбом") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Очистить",
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable(onClick = tracksViewModel::clearQuery),
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceInput,
                    unfocusedContainerColor = colors.surfaceInput,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    cursorColor = colors.primary,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedPlaceholderColor = colors.textSecondary,
                    unfocusedPlaceholderColor = colors.textSecondary,
                ),
            )
        }

        if (ui.tracks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SoundTubeButton(
                    text = "Играть всё",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = { playerViewModel.play(trackIds) },
                )

                Spacer(Modifier.weight(1f))

                SoundTubeButton(
                    text = ui.sort.shortLabel,
                    icon = Icons.AutoMirrored.Rounded.Sort,
                    primary = false,
                    compact = true,
                    onClick = tracksViewModel::cycleSort,
                )

                SoundTubeIconButton(
                    icon = Icons.Rounded.Shuffle,
                    contentDescription = if (player.shuffle) {
                        "Перемешивание включено"
                    } else {
                        "Перемешивание выключено"
                    },
                    tint = if (player.shuffle) colors.primary else colors.textSecondary,
                    onClick = playerViewModel::toggleShuffle,
                )
            }
        }

        when {
            ui.totalCount == 0 -> EmptyState(
                icon = Icons.Rounded.LibraryMusic,
                title = "Библиотека пуста",
                message = "Скачайте первый трек — он появится здесь и будет играть без интернета.",
                modifier = Modifier.weight(1f),
                action = {
                    SoundTubeButton(
                        text = "К загрузкам",
                        onClick = onOpenDownloads,
                    )
                },
            )

            ui.tracks.isEmpty() -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = "Ничего не найдено",
                message = "По запросу «${ui.query.trim()}» нет треков. Попробуйте другое название.",
                modifier = Modifier.weight(1f),
                action = {
                    SoundTubeButton(
                        text = "Сбросить поиск",
                        primary = false,
                        onClick = tracksViewModel::clearQuery,
                    )
                },
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(ui.tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        index = index,
                        isCurrent = track.id == player.currentTrackId,
                        isPlaying = player.isPlaying,
                        onClick = { playerViewModel.play(trackIds, index) },
                        showDivider = index < ui.tracks.lastIndex,
                        onAddToPlaylist = { tracksViewModel.askAddToPlaylist(track) },
                        onDelete = { tracksViewModel.askDelete(track) },
                    )
                }
            }
        }
    }

    when (val current = action) {
        is TrackAction.Delete -> ConfirmDeleteTrackDialog(
            trackTitle = current.track.title,
            onDismiss = tracksViewModel::dismissAction,
            onConfirm = {
                // Сначала гасим плеер, если играет именно этот файл, потом стираем его.
                playerViewModel.onTrackDeleted(current.track.id)
                tracksViewModel.confirmDelete()
            },
        )

        is TrackAction.AddToPlaylist -> AddToPlaylistDialog(
            trackTitle = current.track.title,
            playlists = playlists,
            onDismiss = tracksViewModel::dismissAction,
            onPick = tracksViewModel::addToPlaylist,
            onCreate = tracksViewModel::createPlaylistAndAdd,
        )

        null -> Unit
    }
}

/** Подтверждение действия: строка под шапкой, гаснет сама через несколько секунд. */
@Composable
private fun LibraryNotice(text: String, isError: Boolean, onDismiss: () -> Unit) {
    val colors = SoundTubeTheme.colors

    LaunchedEffect(text) {
        delay(NOTICE_TIMEOUT_MS)
        onDismiss()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isError) colors.error else colors.statusGreen,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Скрыть",
            tint = colors.textSecondary,
            modifier = Modifier
                .size(16.dp)
                .clickable(onClick = onDismiss),
        )
    }
}

private const val NOTICE_TIMEOUT_MS = 4_000L

private val TrackSort.label: String
    get() = when (this) {
        TrackSort.ARTIST -> "По артисту"
        TrackSort.TITLE -> "По названию"
        TrackSort.RECENT -> "Сначала новые"
    }

private val TrackSort.shortLabel: String
    get() = when (this) {
        TrackSort.ARTIST -> "артист"
        TrackSort.TITLE -> "название"
        TrackSort.RECENT -> "новые"
    }

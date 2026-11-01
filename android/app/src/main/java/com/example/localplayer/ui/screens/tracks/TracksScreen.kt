package com.example.localplayer.ui.screens.tracks

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localplayer.domain.model.Track
import com.example.localplayer.ui.components.EmptyState
import com.example.localplayer.ui.components.SoundTubeTopBar
import com.example.localplayer.ui.components.TrackRow
import com.example.localplayer.ui.components.tracksWord
import com.example.localplayer.ui.player.PlayerViewModel

/**
 * Вкладка «Треки»: всё, что уже лежит на диске.
 *
 * Плеер один на всё приложение, поэтому нажатие на строку сразу ставит в очередь
 * текущий (возможно, отфильтрованный) список с выбранного места.
 */
@Composable
fun TracksScreen(
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    tracksViewModel: TracksViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val ui by tracksViewModel.uiState.collectAsStateWithLifecycle()
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    var searchVisible by rememberSaveable { mutableStateOf(false) }

    val trackIds = ui.tracks.map(Track::id)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        SoundTubeTopBar(
            title = "Треки",
            subtitle = when {
                ui.totalCount == 0 -> "Локальная библиотека"
                ui.isFiltering -> "${ui.tracks.size} из ${ui.totalCount} · ${ui.sort.label.lowercase()}"
                else -> "${ui.totalCount} ${ui.totalCount.tracksWord()} · ${ui.sort.label.lowercase()}"
            },
            actions = {
                IconButton(
                    onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) tracksViewModel.clearQuery()
                    },
                ) {
                    Icon(
                        imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = if (searchVisible) "Закрыть поиск" else "Поиск",
                    )
                }
                IconButton(onClick = tracksViewModel::cycleSort) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "Сортировка: ${ui.sort.label}",
                    )
                }
            },
        )

        if (searchVisible) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = tracksViewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Название или исполнитель") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) {
                        IconButton(onClick = tracksViewModel::clearQuery) {
                            Icon(Icons.Rounded.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
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
                FilledTonalButton(
                    onClick = { playerViewModel.play(trackIds) },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Слушать всё")
                }

                Spacer(Modifier.weight(1f))

                IconButton(onClick = playerViewModel::toggleShuffle) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = if (player.shuffle) {
                            "Перемешивание включено"
                        } else {
                            "Перемешивание выключено"
                        },
                        tint = if (player.shuffle) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        when {
            ui.totalCount == 0 -> EmptyState(
                icon = Icons.Rounded.LibraryMusic,
                title = "Библиотека пуста",
                message = "Скачайте первый трек — он появится здесь и будет играть без интернета.",
                modifier = Modifier.weight(1f),
                action = {
                    Button(onClick = onOpenDownloads) {
                        Text("К загрузкам")
                    }
                },
            )

            ui.tracks.isEmpty() -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = "Ничего не найдено",
                message = "По запросу «${ui.query.trim()}» нет треков. Попробуйте другое название.",
                modifier = Modifier.weight(1f),
                action = {
                    Button(onClick = tracksViewModel::clearQuery) {
                        Text("Сбросить поиск")
                    }
                },
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(ui.tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.id == player.currentTrackId,
                        isPlaying = player.isPlaying,
                        onClick = { playerViewModel.play(trackIds, index) },
                    )
                }
            }
        }
    }
}

private val TrackSort.label: String
    get() = when (this) {
        TrackSort.ARTIST -> "По артисту"
        TrackSort.TITLE -> "По названию"
        TrackSort.RECENT -> "Сначала новые"
    }

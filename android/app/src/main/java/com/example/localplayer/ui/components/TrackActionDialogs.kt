package com.example.localplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.localplayer.domain.model.Playlist
import com.example.localplayer.ui.theme.SoundTubeTheme

/**
 * Меню «В плейлист»: список пользовательских плейлистов и создание нового.
 *
 * Системные плейлисты площадок сюда не попадают — они наполняются автоматически
 * по источнику трека. Если плейлистов ещё нет, диалог сразу предлагает создать.
 */
@Composable
fun AddToPlaylistDialog(
    trackTitle: String,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    val colors = SoundTubeTheme.colors
    var creating by rememberSaveable { mutableStateOf(playlists.isEmpty()) }
    var name by rememberSaveable { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        SoundTubeCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "В плейлист",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = trackTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SoundTubeIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Закрыть",
                    bordered = false,
                    size = 30.dp,
                    onClick = onDismiss,
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

            if (creating) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Название плейлиста") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (name.isNotBlank()) onCreate(name) },
                    ),
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoundTubeButton(
                        text = "Создать",
                        icon = Icons.Rounded.Add,
                        enabled = name.isNotBlank(),
                        onClick = { onCreate(name) },
                    )
                    if (playlists.isNotEmpty()) {
                        SoundTubeButton(
                            text = "Отмена",
                            primary = false,
                            onClick = {
                                creating = false
                                name = ""
                            },
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    playlists.forEach { playlist ->
                        PlaylistPickRow(
                            playlist = playlist,
                            onClick = { onPick(playlist.id) },
                        )
                    }
                }

                SoundTubeButton(
                    text = "Новый плейлист",
                    icon = Icons.Rounded.Add,
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { creating = true },
                )
            }
        }
    }
}

@Composable
private fun PlaylistPickRow(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    val colors = SoundTubeTheme.colors
    val shape = RoundedCornerShape(6.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (playlist.trackCount == 0) {
                    "пока пусто"
                } else {
                    "${playlist.trackCount} ${playlist.trackCount.tracksWord()}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }

        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Подтверждение удаления: файл стирается с диска, трек исчезает из библиотеки
 * и всех плейлистов. Действие необратимое, поэтому спрашиваем отдельно.
 */
@Composable
fun ConfirmDeleteTrackDialog(
    trackTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = SoundTubeTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        SoundTubeCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Удалить трек?",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
            }

            Text(
                text = "«$trackTitle» больше не будет в библиотеке и плейлистах. " +
                    "Файл будет стёрт с устройства.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundTubeButton(
                    text = "Удалить",
                    danger = true,
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                )
                SoundTubeButton(
                    text = "Отмена",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
            }
        }
    }
}

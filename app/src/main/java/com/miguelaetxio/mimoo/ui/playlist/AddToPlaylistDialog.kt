package com.miguelaetxio.mimoo.ui.playlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Shared "add to playlist" dialog (Hito 04, PASO 4): lists existing
 * playlists to add the track(s) to, plus an inline "new playlist"
 * field. Used identically from SearchScreen and LibraryScreen.
 *
 * youtubeIds en vez de un solo youtubeId (2026-07-04) -- así sirve
 * igual para añadir una pista suelta (listOf(track.youtubeId)) o un
 * álbum entero (tracks.map { it.youtubeId }), petición explícita de
 * Miguel Ángel.
 * ---
 * Diálogo compartido de "añadir a playlist" (Hito 04, PASO 4): lista
 * las playlists existentes para añadir la(s) pista(s), más un campo en
 * línea de "nueva playlist". Se usa igual desde SearchScreen y
 * LibraryScreen.
 *
 * youtubeIds instead of a single youtubeId (2026-07-04) -- this way it
 * serves both adding a single track (listOf(track.youtubeId)) and a
 * whole album (tracks.map { it.youtubeId }), explicit request from
 * Miguel Ángel.
 */
@Composable
fun AddToPlaylistDialog(
    youtubeIds: List<String>,
    onDismiss: () -> Unit,
    viewModel: AddToPlaylistDialogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var newPlaylistName by remember { mutableStateOf("") }
    val title = if (youtubeIds.size > 1) {
        "Añadir ${youtubeIds.size} pistas a lista"
    } else {
        "Añadir a lista"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (uiState.playlists.isEmpty()) {
                    Text(
                        "Todavía no tienes listas de reproducción.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                    ) {
                        items(uiState.playlists, key = { it.id }) { playlist ->
                            TextButton(
                                onClick = {
                                    viewModel.addToExistingPlaylist(
                                        playlist.id,
                                        youtubeIds,
                                    )
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    playlist.name,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nueva lista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.createPlaylistAndAdd(newPlaylistName, youtubeIds)
                    onDismiss()
                },
                enabled = newPlaylistName.isNotBlank(),
            ) {
                Text("Crear y añadir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

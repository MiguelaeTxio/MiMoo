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
 * playlists to add the track to, plus an inline "new playlist" field.
 * Used identically from SearchScreen and LibraryScreen.
 * ---
 * Diálogo compartido de "añadir a playlist" (Hito 04, PASO 4): lista
 * las playlists existentes para añadir la pista, más un campo en
 * línea de "nueva playlist". Se usa igual desde SearchScreen y
 * LibraryScreen.
 */
@Composable
fun AddToPlaylistDialog(
    youtubeId: String,
    onDismiss: () -> Unit,
    viewModel: AddToPlaylistDialogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a lista") },
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
                                        youtubeId,
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
                    viewModel.createPlaylistAndAdd(newPlaylistName, youtubeId)
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

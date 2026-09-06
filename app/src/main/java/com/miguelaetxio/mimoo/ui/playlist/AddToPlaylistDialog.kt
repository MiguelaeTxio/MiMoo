package com.miguelaetxio.mimoo.ui.playlist

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val activity = LocalContext.current as Activity
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
                // H07 PARTE 1 -- si la última operación se rechazó
                // por falta de conexión, el diálogo NO se cierra solo
                // (ver los onClick de abajo); esto es lo que informa
                // de por qué no pasó nada.
                // ---
                // H07 PART 1 -- if the last operation got rejected
                // for lack of connection, the dialog does NOT close
                // itself (see the onClick handlers below); this is
                // what explains why nothing happened.
                uiState.syncBlockedMessage?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                }

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
                                    viewModel.dismissSyncBlockedMessage()
                                    viewModel.addToExistingPlaylist(
                                        activity,
                                        playlist.id,
                                        playlist.name,
                                        youtubeIds,
                                        onSuccess = onDismiss,
                                    )
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
                    viewModel.dismissSyncBlockedMessage()
                    viewModel.createPlaylistAndAdd(
                        activity,
                        newPlaylistName,
                        youtubeIds,
                        onSuccess = onDismiss,
                    )
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

    // S051 -- petición explícita de Miguel Ángel: avisar y preguntar
    // antes de añadir una pista que ya está en la lista destino, en
    // vez de moverla al final en silencio. Diálogo aparte, por encima
    // del principal (no lo sustituye), para no perder el contexto de
    // qué se estaba añadiendo.
    uiState.duplicateConfirmation?.let { confirmation ->
        val message = if (confirmation.duplicateCount == 1 && confirmation.youtubeIds.size == 1) {
            "Este tema ya está en \"${confirmation.playlistName}\". ¿Lo añadimos de todas formas? (se moverá al final de la lista)"
        } else if (confirmation.duplicateCount == confirmation.youtubeIds.size) {
            "Todas estas pistas ya están en \"${confirmation.playlistName}\". ¿Las añadimos de todas formas? (se moverán al final de la lista)"
        } else {
            "${confirmation.duplicateCount} de estas pistas ya están en \"${confirmation.playlistName}\". ¿Las añadimos de todas formas? (las que ya estaban se moverán al final de la lista)"
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicateConfirmation,
            title = { Text("Tema ya en la lista") },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmAddDuplicates(activity, onSuccess = onDismiss) },
                ) {
                    Text("Añadir de todas formas")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDuplicateConfirmation) { Text("Cancelar") }
            },
        )
    }
}

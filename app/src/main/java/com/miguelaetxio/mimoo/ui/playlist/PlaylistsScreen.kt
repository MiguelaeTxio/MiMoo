package com.miguelaetxio.mimoo.ui.playlist

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.local.entity.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
    onOpenPlaylist: (playlistId: Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as Activity
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistPendingRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistPendingDelete by remember { mutableStateOf<Playlist?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // H07 PARTE 1 -- aviso cuando crear/borrar se rechaza por falta de conexión.
    LaunchedEffect(uiState.syncBlockedMessage) {
        uiState.syncBlockedMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSyncBlockedMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Listas de reproducción") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menú")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Nueva lista")
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Todavía no tienes listas de reproducción.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Spacer(Modifier.height(8.dp))

                // H08 PARTE 1 -- mismo patrón visual que "Filtrar
                // biblioteca" (LibraryScreen.kt). A diferencia de
                // Biblioteca, Playlists no tiene niveles de navegación
                // por capas, así que el filtro está siempre visible
                // mientras haya al menos una playlist creada. El
                // margen horizontal se aplica solo aquí (y en el
                // mensaje de "sin resultados"), no a la Column ni a
                // PlaylistRow, que ya trae su propio padding
                // horizontal de 16dp -- evita duplicar el margen.
                OutlinedTextField(
                    value = uiState.filterQuery,
                    onValueChange = viewModel::onFilterQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    label = { Text("Filtrar listas") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))

                if (uiState.filteredPlaylists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Ninguna lista coincide con \"${uiState.filterQuery}\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.filteredPlaylists, key = { it.id }) { playlist ->
                            PlaylistRow(
                                playlist = playlist,
                                onOpen = { onOpenPlaylist(playlist.id) },
                                onRename = { playlistPendingRename = playlist },
                                onDelete = { playlistPendingDelete = playlist },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        NamePlaylistDialog(
            title = "Nueva lista",
            initialName = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createPlaylist(activity, name)
                showCreateDialog = false
            },
        )
    }

    playlistPendingRename?.let { playlist ->
        NamePlaylistDialog(
            title = "Renombrar lista",
            initialName = playlist.name,
            onDismiss = { playlistPendingRename = null },
            onConfirm = { name ->
                viewModel.renamePlaylist(playlist.id, name)
                playlistPendingRename = null
            },
        )
    }

    playlistPendingDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistPendingDelete = null },
            title = { Text("Borrar lista") },
            text = {
                Text(
                    "Se eliminará la lista \"${playlist.name}\". Las pistas " +
                        "en sí no se borran, solo dejan de estar en esta " +
                        "lista. Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(activity, playlist.id)
                    playlistPendingDelete = null
                }) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistPendingDelete = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.QueueMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRename) {
            Icon(Icons.Filled.Edit, contentDescription = "Renombrar")
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Borrar lista",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun NamePlaylistDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

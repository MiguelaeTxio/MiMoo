package com.miguelaetxio.mimoo.ui.albumsearch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.remote.AlbumTrackMatch
import com.miguelaetxio.mimoo.data.remote.dto.TrackDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumSearchScreen(
    viewModel: AlbumSearchViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
    onNavigateToLibrary: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var trackPendingManualMatch by remember { mutableStateOf<AlbumTrackMatch?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buscar álbum") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menú")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.artist,
                onValueChange = viewModel::onArtistChange,
                label = { Text("Artista") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.album,
                onValueChange = viewModel::onAlbumChange,
                label = { Text("Álbum") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::searchAlbum,
                // PASO 6a: se acepta artista o album sueltos (caso real:
                // obras clasicas sin autor conocido de memoria).
                enabled = uiState.artist.isNotBlank() || uiState.album.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Buscar álbum")
            }

            if (uiState.isSearching) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            if (uiState.matches.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val matchedCount = uiState.matches.count { it.matchedTrack != null }
                Text(
                    "$matchedCount de ${uiState.matches.size} pistas emparejadas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(uiState.matches, key = { it.position }) { match ->
                        AlbumTrackMatchRow(
                            match = match,
                            downloadStatus = match.matchedTrack?.let {
                                uiState.importedStatus[it.youtubeId]
                            },
                            onCorrect = { trackPendingManualMatch = match },
                        )
                        HorizontalDivider()
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = viewModel::importAlbum,
                    enabled = matchedCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Importar álbum ($matchedCount pistas)")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    uiState.importedCount?.let { count ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Álbum importado") },
            text = {
                Text(
                    "$count pistas añadidas y descargándose. Se " +
                        "escucharán desde Biblioteca en cuanto terminen."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissImportedDialog) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissImportedDialog()
                        onNavigateToLibrary()
                    },
                ) {
                    Text("Ver en Biblioteca")
                }
            },
        )
    }

    trackPendingManualMatch?.let { match ->
        ManualMatchDialog(
            match = match,
            candidates = uiState.manualSearchCandidates,
            isSearching = uiState.isSearchingManualCandidates,
            onSearch = viewModel::searchManualCandidates,
            onPick = { candidate ->
                viewModel.applyManualMatch(match.position, candidate)
                trackPendingManualMatch = null
            },
            onDismiss = {
                viewModel.clearManualCandidates()
                trackPendingManualMatch = null
            },
        )
    }
}

@Composable
private fun AlbumTrackMatchRow(
    match: AlbumTrackMatch,
    downloadStatus: DownloadStatus?,
    onCorrect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (match.matchedTrack != null && match.isAutoMatched) {
                Icons.Filled.CheckCircle
            } else {
                Icons.Filled.WarningAmber
            },
            contentDescription = null,
            tint = if (match.matchedTrack != null && match.isAutoMatched) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("${match.position}. ${match.mbTitle}")
            Text(
                text = when {
                    match.matchedTrack == null -> "Sin emparejar"
                    match.isAutoMatched -> "Emparejado: ${match.matchedTrack.title}"
                    else -> "Revisar: ${match.matchedTrack.title}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // PASO 6b Parte 2: feedback en vivo de la descarga automatica
        // tras importar, en la misma fila -- sin esto solo se ve el
        // emparejamiento, nunca si la descarga real avanza o falla.
        if (downloadStatus != null) {
            Spacer(Modifier.width(4.dp))
            ImportedDownloadStatusIcon(downloadStatus)
        }
        IconButton(onClick = onCorrect) {
            Icon(Icons.Filled.Edit, contentDescription = "Corregir manualmente")
        }
    }
}

/**
 * Read-only status indicator for a track already imported and
 * auto-enqueued for download (PASO 6b Parte 2) — not a retry button,
 * DownloadWorker already handles its own lifecycle.
 * ---
 * Indicador de estado de solo lectura para una pista ya importada y
 * encolada automáticamente para descarga (PASO 6b Parte 2) — no es un
 * botón de reintento, DownloadWorker ya gestiona su propio ciclo de
 * vida.
 */
@Composable
private fun ImportedDownloadStatusIcon(status: DownloadStatus) {
    when (status) {
        DownloadStatus.PENDING, DownloadStatus.DOWNLOADING -> {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            }
        }
        DownloadStatus.DONE -> {
            Icon(
                Icons.Filled.CloudDownload,
                contentDescription = "Descargada",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DownloadStatus.ERROR -> {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = "Error al descargar",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ManualMatchDialog(
    match: AlbumTrackMatch,
    candidates: List<TrackDto>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onPick: (TrackDto) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember(match.position) { mutableStateOf(match.mbTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Corregir: ${match.mbTitle}") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar en YouTube") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { onSearch(query) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Buscar",
                            )
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                if (isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(candidates, key = { it.youtubeId }) { candidate ->
                        TextButton(
                            onClick = { onPick(candidate) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(candidate.title)
                                Text(
                                    candidate.channelTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

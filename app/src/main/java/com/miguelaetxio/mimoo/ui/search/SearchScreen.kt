package com.miguelaetxio.mimoo.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.ui.playlist.AddToPlaylistDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var trackPendingAddToPlaylist by remember {
        mutableStateOf<SearchResultTrack?>(null)
    }
    // Confirmación/edición de metadatos antes de descargar (petición
    // explícita de Miguel Ángel, 2026-07-04): con la búsqueda gratuita
    // por yt-dlp los metadatos son más pobres, así que se pueden
    // corregir aquí antes de que el archivo se cree en disco.
    // ---
    // Metadata confirmation/edit before downloading (explicit request
    // from Miguel Ángel, 2026-07-04): with the free yt-dlp search the
    // metadata is poorer, so it can be corrected here before the file
    // is created on disk.
    var trackPendingDownloadConfirm by remember {
        mutableStateOf<SearchResultTrack?>(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MiMoo") },
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
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar un tema") },
                trailingIcon = {
                    IconButton(onClick = viewModel::search) {
                        Icon(Icons.Filled.Search, contentDescription = "Buscar")
                    }
                },
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            if (uiState.isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.results, key = { it.youtubeId }) { track ->
                    SearchResultRow(
                        track = track,
                        onPlay = { viewModel.playTrack(track) },
                        onDownload = { trackPendingDownloadConfirm = track },
                        onToggleFavorite = { viewModel.toggleFavorite(track) },
                        onAddToPlaylist = { trackPendingAddToPlaylist = track },
                    )
                    HorizontalDivider()
                }
            }

            if (uiState.isResolvingStream) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }

    trackPendingAddToPlaylist?.let { track ->
        AddToPlaylistDialog(
            youtubeId = track.youtubeId,
            onDismiss = { trackPendingAddToPlaylist = null },
        )
    }

    trackPendingDownloadConfirm?.let { track ->
        DownloadConfirmDialog(
            track = track,
            onDismiss = { trackPendingDownloadConfirm = null },
            onConfirm = { title, artist, album ->
                viewModel.confirmDownload(track, title, artist, album)
                trackPendingDownloadConfirm = null
            },
        )
    }
}

/**
 * Diálogo de confirmación/edición de metadatos antes de descargar
 * (petición explícita de Miguel Ángel, 2026-07-04). Álbum vacío =
 * sencillo, igual que en Importar enlace/Biblioteca.
 * ---
 * Metadata confirmation/edit dialog before downloading (explicit
 * request from Miguel Ángel, 2026-07-04). Empty album = single, same
 * as Importar enlace/Biblioteca.
 */
@Composable
private fun DownloadConfirmDialog(
    track: SearchResultTrack,
    onDismiss: () -> Unit,
    onConfirm: (title: String, artist: String, album: String) -> Unit,
) {
    var title by remember(track.youtubeId) { mutableStateOf(track.title) }
    var artist by remember(track.youtubeId) {
        mutableStateOf(track.artist ?: track.channelTitle)
    }
    var album by remember(track.youtubeId) { mutableStateOf(track.album ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar descarga") },
        text = {
            Column {
                Text(
                    "Revisa los metadatos antes de descargar -- se " +
                        "usarán para el nombre del archivo y la carpeta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Álbum (vacío = Sencillos)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, artist, album) },
                enabled = title.isNotBlank() && artist.isNotBlank(),
            ) {
                Text("Descargar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun SearchResultRow(
    track: SearchResultTrack,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${track.channelTitle} · ${formatDuration(track.durationSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onAddToPlaylist) {
            Icon(
                Icons.Filled.PlaylistAdd,
                contentDescription = "Añadir a lista",
            )
        }

        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (track.isFavorite) {
                    Icons.Filled.Star
                } else {
                    Icons.Filled.StarBorder
                },
                contentDescription = if (track.isFavorite) {
                    "Quitar de favoritos"
                } else {
                    "Marcar como favorito"
                },
                tint = if (track.isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        IconButton(onClick = onPlay) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir")
        }

        DownloadButton(
            status = track.downloadStatus,
            onDownload = onDownload,
        )
    }
}

/**
 * Download action button that reflects the current DownloadStatus.
 * PENDING  -> download icon, enabled.
 * QUEUED, DOWNLOADING -> circular progress spinner, disabled (para
 *   progreso real en %, ver la pantalla "Descargas" — este botón solo
 *   necesita indicar "hay algo en marcha", no el porcentaje).
 * DONE     -> green check icon, disabled.
 * ERROR    -> red error icon, enabled (tap to retry).
 * ---
 * Boton de descarga que refleja el DownloadStatus actual.
 * PENDING      -> icono de descarga, habilitado.
 * QUEUED, DOWNLOADING -> spinner circular, deshabilitado (para
 *   progreso real en %, ver la pantalla "Descargas" — este boton solo
 *   necesita indicar "hay algo en marcha", no el porcentaje).
 * DONE         -> icono verde de exito, deshabilitado.
 * ERROR        -> icono rojo de error, habilitado (pulsar para reintentar).
 */
@Composable
private fun DownloadButton(
    status: DownloadStatus,
    onDownload: () -> Unit,
) {
    when (status) {
        DownloadStatus.PENDING -> {
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "Descargar",
                )
            }
        }
        DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        DownloadStatus.DONE -> {
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Descargado",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        DownloadStatus.ERROR -> {
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = "Error — reintentar descarga",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

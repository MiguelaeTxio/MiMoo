package com.miguelaetxio.mimoo.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("MiMoo") })
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
                        onDownload = { viewModel.requestDownload(track) },
                    )
                    HorizontalDivider()
                }
            }

            if (uiState.isResolvingStream) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    track: SearchResultTrack,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
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
 * DOWNLOADING -> circular progress spinner, disabled.
 * DONE     -> green check icon, disabled.
 * ERROR    -> red error icon, enabled (tap to retry).
 * ---
 * Boton de descarga que refleja el DownloadStatus actual.
 * PENDING      -> icono de descarga, habilitado.
 * DOWNLOADING  -> spinner circular, deshabilitado.
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
        DownloadStatus.DOWNLOADING -> {
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

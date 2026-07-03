package com.miguelaetxio.mimoo.ui.importlink

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.miguelaetxio.mimoo.data.remote.dto.ExternalLinkTrack
import com.miguelaetxio.mimoo.ui.library.displayArtistName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportLinkScreen(
    viewModel: ImportLinkViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
    onNavigateToLibrary: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importar enlace") },
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
            Text(
                "Pega un enlace de una playlist/álbum de YouTube o " +
                    "YouTube Music, o de un solo vídeo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.url,
                onValueChange = viewModel::onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Enlace de YouTube / YouTube Music") },
                leadingIcon = {
                    Icon(Icons.Filled.Link, contentDescription = null)
                },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::resolveLink,
                enabled = uiState.url.isNotBlank() && !uiState.isResolving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ver contenido del enlace")
            }

            if (uiState.isResolving) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.errorMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            if (uiState.tracks.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uiState.isPlaylist) {
                        LinkCoverThumbnail(
                            coverArtUrl = uiState.coverArtUrl,
                            fallbackThumbnailUrl = uiState.tracks.firstOrNull()?.thumbnailUrl,
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            uiState.resolvedTitle ?: "",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "${uiState.selectedYoutubeIds.size} de " +
                                "${uiState.tracks.size} seleccionadas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(uiState.tracks, key = { it.youtubeId }) { track ->
                        ExternalLinkTrackRow(
                            track = track,
                            checked = track.youtubeId in uiState.selectedYoutubeIds,
                            onToggle = { viewModel.toggleTrackSelected(track.youtubeId) },
                        )
                        HorizontalDivider()
                    }
                }

                if (uiState.isResolvingQueue) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = viewModel::playSelected,
                        enabled = uiState.selectedYoutubeIds.isNotEmpty() &&
                            !uiState.isResolvingQueue,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Reproducir")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = viewModel::importSelected,
                        enabled = uiState.selectedYoutubeIds.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Descargar (${uiState.selectedYoutubeIds.size})")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    uiState.importedCount?.let { count ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Importado") },
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
}

/**
 * Same fallback chain as LibraryScreen.AlbumCoverThumbnail: MusicBrainz
 * + Cover Art Archive first, then the first track's own YouTube
 * thumbnail, then a generic album icon.
 * ---
 * Misma cadena de fallback que LibraryScreen.AlbumCoverThumbnail:
 * primero MusicBrainz + Cover Art Archive, luego la miniatura de
 * YouTube de la primera pista, luego un icono genérico de álbum.
 */
@Composable
private fun LinkCoverThumbnail(
    coverArtUrl: String?,
    fallbackThumbnailUrl: String?,
) {
    val size = 48.dp
    val shape = RoundedCornerShape(4.dp)

    if (coverArtUrl == null && fallbackThumbnailUrl == null) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        return
    }

    SubcomposeAsyncImage(
        model = coverArtUrl ?: fallbackThumbnailUrl,
        contentDescription = "Carátula",
        modifier = Modifier.size(size).clip(shape),
        error = {
            if (coverArtUrl != null && fallbackThumbnailUrl != null) {
                SubcomposeAsyncImage(
                    model = fallbackThumbnailUrl,
                    contentDescription = "Carátula",
                    modifier = Modifier.size(size).clip(shape),
                    error = { LinkCoverThumbnail(null, null) },
                )
            } else {
                LinkCoverThumbnail(null, null)
            }
        },
    )
}

@Composable
private fun ExternalLinkTrackRow(
    track: ExternalLinkTrack,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(4.dp))
        if (track.thumbnailUrl != null) {
            SubcomposeAsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp)),
                error = {},
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = listOfNotNull(
                    displayArtistName(track.channelTitle),
                    track.durationSeconds.takeIf { it > 0 }?.let { formatDuration(it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

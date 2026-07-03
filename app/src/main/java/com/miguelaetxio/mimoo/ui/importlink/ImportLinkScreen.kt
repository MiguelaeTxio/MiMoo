package com.miguelaetxio.mimoo.ui.importlink

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
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
                    TextButton(onClick = viewModel::selectAll) { Text("Todo") }
                    TextButton(onClick = viewModel::selectNone) { Text("Ninguno") }
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

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = viewModel::importSelected,
                    enabled = uiState.selectedYoutubeIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Importar (${uiState.selectedYoutubeIds.size} pistas)")
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

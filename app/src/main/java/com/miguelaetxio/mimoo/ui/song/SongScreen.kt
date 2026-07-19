package com.miguelaetxio.mimoo.ui.song

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.ui.theme.glassChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongScreen(
    viewModel: SongViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenAlbum: (albumName: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip(interactive = false)) {
                        Text(
                            uiState.songTitle,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    // El favorito de pista suelta solo tiene sentido si
                    // ya está descargada (ver comentario de clase de
                    // SongViewModel) -- no se muestra en otro caso.
                    if (uiState.downloadStatus != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                if (uiState.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = if (uiState.isFavorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                            )
                        }
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
            when {
                uiState.isLoading -> {
                    Spacer(Modifier.height(24.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                uiState.notFound -> {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "No se encontró esta pista en YouTube.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.youtubeId != null -> {
                    Spacer(Modifier.height(8.dp))
                    Text(uiState.artistName, style = MaterialTheme.typography.bodyMedium)

                    uiState.localAlbum?.let { album ->
                        Spacer(Modifier.height(4.dp))
                        AssistChip(
                            onClick = { onOpenAlbum(album) },
                            label = { Text(album) },
                            leadingIcon = {
                                Icon(Icons.Filled.Album, contentDescription = null)
                            },
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Row {
                        Button(
                            onClick = viewModel::play,
                            enabled = !uiState.isResolvingPlayback,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Reproducir")
                        }
                        Spacer(Modifier.width(8.dp))
                        when (uiState.downloadStatus) {
                            DownloadStatus.DONE -> {
                                OutlinedButton(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Descargada")
                                }
                            }
                            DownloadStatus.PENDING, DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                                OutlinedButton(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Descargando…")
                                }
                            }
                            else -> {
                                OutlinedButton(
                                    onClick = viewModel::download,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Descargar")
                                }
                            }
                        }
                    }

                    if (uiState.isResolvingPlayback) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

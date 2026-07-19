package com.miguelaetxio.mimoo.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.remote.AlbumTrackMatch
import com.miguelaetxio.mimoo.ui.theme.glassChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    viewModel: AlbumViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenSong: (songTitle: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip(interactive = false)) {
                        Text(
                            uiState.candidate?.title ?: uiState.albumName,
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
                    if (uiState.candidate != null) {
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
                        "No se encontró este álbum en MusicBrainz.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.candidate != null -> {
                    val candidate = uiState.candidate!!
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AlbumCoverThumbnail(candidate.coverArtUrl, size = 64.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                candidate.artist ?: uiState.artistName,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            candidate.year?.let { year ->
                                Text(
                                    year,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(
                            onClick = viewModel::playAlbum,
                            enabled = !uiState.isResolvingPlayback,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Reproducir álbum")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = viewModel::downloadAlbum,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Descargar álbum")
                        }
                    }

                    if (uiState.isResolvingPlayback) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Spacer(Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        itemsIndexed(
                            uiState.matches,
                            key = { index, match -> "$index-${match.position}" },
                        ) { _, match ->
                            AlbumTrackRow(
                                match = match,
                                downloadStatus = uiState.localTracksByPosition[match.position - 1]
                                    ?.downloadStatus,
                                onPlay = { viewModel.playTrack(match) },
                                onDownload = { viewModel.downloadTrack(match) },
                                onOpenSong = { onOpenSong(match.mbTitle) },
                            )
                            HorizontalDivider()
                        }
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

/** Mismo patrón de fallback que CandidateCoverThumbnail de AlbumSearchScreen. */
@Composable
private fun AlbumCoverThumbnail(coverArtUrl: String, size: Dp) {
    val shape = RoundedCornerShape(4.dp)
    SubcomposeAsyncImage(
        model = coverArtUrl,
        contentDescription = "Carátula del álbum",
        modifier = Modifier.size(size).clip(shape),
        error = {
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
                    modifier = Modifier.size(size / 2),
                )
            }
        },
    )
}

@Composable
private fun AlbumTrackRow(
    match: AlbumTrackMatch,
    downloadStatus: DownloadStatus?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onOpenSong: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSong)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${match.position}. ${match.mbTitle}")
            if (match.matchedTrack == null && downloadStatus != DownloadStatus.DONE) {
                Text(
                    "Sin emparejar en YouTube",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        // S018 FIX -- ya no depende solo de match.matchedTrack != null:
        // una pista ya descargada (downloadStatus == DONE) debe poder
        // reproducirse aunque el reemparejamiento de ESTA sesión no
        // haya encontrado nada en YouTube (playTrack() ya prioriza el
        // archivo local sobre el emparejamiento fresco).
        if (match.matchedTrack != null || downloadStatus == DownloadStatus.DONE) {
            IconButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir")
            }
            when (downloadStatus) {
                DownloadStatus.PENDING, DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                    Box(
                        modifier = Modifier.size(40.dp),
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
                else -> {
                    if (match.matchedTrack != null) {
                        IconButton(onClick = onDownload) {
                            Icon(Icons.Filled.Download, contentDescription = "Descargar")
                        }
                    }
                }
            }
        }
    }
}

package com.miguelaetxio.mimoo.ui.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzArtistSummary
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzReleaseGroup
import com.miguelaetxio.mimoo.ui.theme.glassChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    viewModel: ArtistViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenAlbum: (albumName: String) -> Unit,
    onOpenSong: (songTitle: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    uiState.disambiguationCandidates?.let { candidates ->
        ArtistDisambiguationDialog(
            candidates = candidates,
            onChoose = viewModel::chooseDisambiguationCandidate,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip(interactive = false)) {
                        Text(
                            uiState.canonicalName ?: uiState.artistName,
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
                    if (uiState.mbid != null) {
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
                        "No se encontró ningún artista con ese nombre en MusicBrainz.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.mbid != null -> {
                    Spacer(Modifier.height(8.dp))

                    // Roadmap punto 7: conteo "Descargado" separado de
                    // "Favorito" -- independientes entre sí (S017).
                    if (uiState.isLoadingDownloadedCounts) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        Text(
                            buildString {
                                append("${uiState.completeAlbumsCount} álbumes completos, ")
                                append("${uiState.partialAlbumsCount} álbum parcial, ")
                                append("${uiState.downloadedSinglesCount} sencillos")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (uiState.albums.isNotEmpty()) {
                            item {
                                Text("Álbumes", style = MaterialTheme.typography.titleMedium)
                            }
                            items(uiState.albums, key = { "album-${it.id}" }) { album ->
                                ReleaseGroupRow(
                                    releaseGroup = album,
                                    icon = Icons.Filled.Album,
                                    onClick = { onOpenAlbum(album.title) },
                                )
                                HorizontalDivider()
                            }
                        }
                        if (uiState.singles.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(8.dp))
                                Text("Sencillos", style = MaterialTheme.typography.titleMedium)
                            }
                            items(uiState.singles, key = { "single-${it.id}" }) { single ->
                                ReleaseGroupRow(
                                    releaseGroup = single,
                                    icon = Icons.Filled.MusicNote,
                                    onClick = { onOpenSong(single.title) },
                                )
                                HorizontalDivider()
                            }
                        }
                        if (uiState.albums.isEmpty() && uiState.singles.isEmpty()) {
                            item {
                                Text(
                                    "Este artista no tiene álbumes ni sencillos en MusicBrainz.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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

@Composable
private fun ReleaseGroupRow(
    releaseGroup: MusicBrainzReleaseGroup,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(releaseGroup.title)
            releaseGroup.firstReleaseDate?.take(4)?.let { year ->
                Text(
                    year,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Diálogo de desambiguación de homónimos reales (S018, roadmap punto
 * 4): se muestra cuando MusicBrainz devuelve más de un MBID distinto
 * para el mismo nombre normalizado. País por candidato, tal como
 * cerró el diseño de S017 (2-3 candidatos con país/década/género --
 * MusicBrainzArtistSummary solo trae país hoy; década/género
 * requerirían un lookupArtist adicional por candidato, no justificado
 * solo para el diálogo de elección).
 * ---
 * Real-homonym disambiguation dialog (S018, roadmap point 4): shown
 * when MusicBrainz returns more than one distinct MBID for the same
 * normalized name. Country per candidate, as closed in the S017
 * design (2-3 candidates with country/decade/genre --
 * MusicBrainzArtistSummary only carries country today; decade/genre
 * would need an extra lookupArtist per candidate, not justified just
 * for the picker dialog).
 */
@Composable
private fun ArtistDisambiguationDialog(
    candidates: List<MusicBrainzArtistSummary>,
    onChoose: (MusicBrainzArtistSummary) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* Bloqueante: hay que elegir uno para continuar. */ },
        title = { Text("Varios artistas con este nombre") },
        text = {
            Column {
                candidates.forEach { candidate ->
                    TextButton(
                        onClick = { onChoose(candidate) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(candidate.name)
                            candidate.country?.let { country ->
                                Text(
                                    country,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

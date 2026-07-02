package com.miguelaetxio.mimoo.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack

private sealed class LibraryListItem {
    data class ArtistHeader(val artist: String) : LibraryListItem()
    data class AlbumHeader(val artist: String, val album: String) : LibraryListItem()
    data class TrackRow(val track: SearchResultTrack) : LibraryListItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var trackPendingDelete by remember {
        mutableStateOf<SearchResultTrack?>(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Biblioteca") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menú")
                    }
                },
                actions = {
                    if (uiState.isRefreshing) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    } else {
                        IconButton(onClick = viewModel::refreshLibrary) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Refrescar biblioteca",
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
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.filterQuery,
                onValueChange = viewModel::onFilterQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Filtrar biblioteca") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        viewModel.setViewMode(LibraryViewMode.HIERARCHICAL)
                    },
                ) {
                    Text(
                        "Jerárquica",
                        fontWeight = if (
                            uiState.viewMode == LibraryViewMode.HIERARCHICAL
                        ) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                    )
                }
                TextButton(
                    onClick = { viewModel.setViewMode(LibraryViewMode.FLAT) },
                ) {
                    Text(
                        "Plana",
                        fontWeight = if (
                            uiState.viewMode == LibraryViewMode.FLAT
                        ) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        viewModel.setShowFavoritesOnly(
                            !uiState.showFavoritesOnly,
                        )
                    },
                ) {
                    Icon(
                        imageVector = if (uiState.showFavoritesOnly) {
                            Icons.Filled.Star
                        } else {
                            Icons.Filled.StarBorder
                        },
                        contentDescription = if (uiState.showFavoritesOnly) {
                            "Mostrar todas"
                        } else {
                            "Mostrar solo favoritos"
                        },
                        tint = if (uiState.showFavoritesOnly) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            if (uiState.viewMode == LibraryViewMode.FLAT) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SortButton(
                        label = "Título",
                        option = LibrarySortOption.TITLE,
                        current = uiState.sortOption,
                        onSelect = viewModel::setSortOption,
                    )
                    SortButton(
                        label = "Artista",
                        option = LibrarySortOption.ARTIST,
                        current = uiState.sortOption,
                        onSelect = viewModel::setSortOption,
                    )
                    SortButton(
                        label = "Fecha",
                        option = LibrarySortOption.DATE,
                        current = uiState.sortOption,
                        onSelect = viewModel::setSortOption,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (uiState.grouped.isEmpty() && uiState.flatTracks.isEmpty()) {
                Text(
                    if (uiState.showFavoritesOnly) {
                        "Todavía no hay favoritos descargados."
                    } else {
                        "Todavía no hay pistas descargadas."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }

            val listItems: List<LibraryListItem> = if (
                uiState.viewMode == LibraryViewMode.HIERARCHICAL
            ) {
                buildList {
                    uiState.grouped.forEach { (artist, albums) ->
                        add(LibraryListItem.ArtistHeader(artist))
                        albums.forEach { (album, tracks) ->
                            add(LibraryListItem.AlbumHeader(artist, album))
                            tracks.forEach { track ->
                                add(LibraryListItem.TrackRow(track))
                            }
                        }
                    }
                }
            } else {
                uiState.flatTracks.map { LibraryListItem.TrackRow(it) }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(
                    listItems,
                    key = { item ->
                        when (item) {
                            is LibraryListItem.ArtistHeader ->
                                "artist:${item.artist}"
                            is LibraryListItem.AlbumHeader ->
                                "album:${item.artist}:${item.album}"
                            is LibraryListItem.TrackRow ->
                                "track:${item.track.youtubeId}"
                        }
                    },
                ) { item ->
                    when (item) {
                        is LibraryListItem.ArtistHeader -> ArtistHeaderRow(
                            artist = item.artist,
                            onPlayAll = { viewModel.playArtist(item.artist) },
                            onShuffle = {
                                viewModel.playArtistShuffled(item.artist)
                            },
                        )
                        is LibraryListItem.AlbumHeader -> AlbumHeaderRow(
                            artist = item.artist,
                            album = item.album,
                            tracks = uiState.grouped[item.artist]?.get(item.album)
                                ?: emptyList(),
                            onPlayAlbum = {
                                viewModel.playAlbum(item.artist, item.album)
                            },
                            onRequestCoverArt = viewModel::requestCoverArtIfMissing,
                        )
                        is LibraryListItem.TrackRow -> LibraryTrackRow(
                            track = item.track,
                            onPlay = { viewModel.playTrack(item.track) },
                            onToggleFavorite = {
                                viewModel.toggleFavorite(item.track)
                            },
                            onDelete = { trackPendingDelete = item.track },
                        )
                    }
                }
            }
        }
    }

    trackPendingDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackPendingDelete = null },
            title = { Text("Borrar descarga") },
            text = {
                Text(
                    "Se eliminará el archivo \"${track.title}\" de tu " +
                        "dispositivo. Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDownload(track)
                    trackPendingDelete = null
                }) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { trackPendingDelete = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun SortButton(
    label: String,
    option: LibrarySortOption,
    current: LibrarySortOption,
    onSelect: (LibrarySortOption) -> Unit,
) {
    TextButton(onClick = { onSelect(option) }) {
        Text(
            label,
            fontWeight = if (current == option) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            },
        )
    }
}

@Composable
private fun ArtistHeaderRow(
    artist: String,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = artist,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPlayAll) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Reproducir artista",
            )
        }
        IconButton(onClick = onShuffle) {
            Icon(Icons.Filled.Shuffle, contentDescription = "Aleatorio")
        }
    }
}

@Composable
private fun AlbumHeaderRow(
    artist: String,
    album: String,
    tracks: List<SearchResultTrack>,
    onPlayAlbum: () -> Unit,
    onRequestCoverArt: (artist: String, album: String) -> Unit,
) {
    // "Sencillos" is a synthetic grouping label for tracks with no
    // real album (see UNKNOWN_ALBUM_LABEL) — there is nothing to
    // search on MusicBrainz for it, so the lookup is skipped entirely.
    LaunchedEffect(artist, album) {
        if (album != UNKNOWN_ALBUM_LABEL) {
            onRequestCoverArt(artist, album)
        }
    }

    val coverArtUrl = tracks.firstNotNullOfOrNull { it.coverArtUrl }
    val fallbackThumbnailUrl = tracks.firstNotNullOfOrNull { it.thumbnailUrl }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumCoverThumbnail(
            coverArtUrl = coverArtUrl,
            fallbackThumbnailUrl = fallbackThumbnailUrl,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = album,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPlayAlbum) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Reproducir álbum",
            )
        }
    }
}

/**
 * Small square album thumbnail. Tries the MusicBrainz+Cover Art
 * Archive URL first; if that fails to load (404, no match, or simply
 * null because the lookup hasn't resolved yet) it falls back to the
 * YouTube thumbnail already cached on the track, and finally to a
 * generic album icon if neither is available — matching the fallback
 * chain defined in mimoo-annex-v03 PASO 6.
 * ---
 * Miniatura cuadrada pequeña de álbum. Prueba primero la URL de
 * MusicBrainz+Cover Art Archive; si falla al cargar (404, sin
 * coincidencia, o simplemente null porque la búsqueda aún no se ha
 * resuelto) hace fallback a la miniatura de YouTube ya cacheada en la
 * pista, y por último a un icono genérico de álbum si ninguna está
 * disponible — según la cadena de fallback definida en
 * mimoo-annex-v03 PASO 6.
 */
@Composable
private fun AlbumCoverThumbnail(
    coverArtUrl: String?,
    fallbackThumbnailUrl: String?,
) {
    val size = 40.dp
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
                modifier = Modifier.size(20.dp),
            )
        }
        return
    }

    SubcomposeAsyncImage(
        model = coverArtUrl ?: fallbackThumbnailUrl,
        contentDescription = "Carátula del álbum",
        modifier = Modifier.size(size).clip(shape),
        error = {
            if (coverArtUrl != null && fallbackThumbnailUrl != null) {
                // The MusicBrainz+CAA URL failed to load (no match /
                // 404) — retry with the YouTube thumbnail instead of
                // giving up.
                SubcomposeAsyncImage(
                    model = fallbackThumbnailUrl,
                    contentDescription = "Carátula del álbum",
                    modifier = Modifier.size(size).clip(shape),
                    error = { AlbumCoverThumbnail(null, null) },
                )
            } else {
                AlbumCoverThumbnail(null, null)
            }
        },
    )
}

@Composable
private fun LibraryTrackRow(
    track: SearchResultTrack,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                track.artist ?: track.channelTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Borrar descarga",
                tint = MaterialTheme.colorScheme.error,
            )
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir")
        }
    }
}

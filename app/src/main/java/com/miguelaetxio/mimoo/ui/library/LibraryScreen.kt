package com.miguelaetxio.mimoo.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
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
import com.miguelaetxio.mimoo.ui.playlist.AddToPlaylistDialog

private sealed class LibraryListItem {
    data class ArtistAlbumsHeader(val artist: String) : LibraryListItem()
    data class ArtistSinglesHeader(val artist: String) : LibraryListItem()
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
    var trackPendingEdit by remember {
        mutableStateOf<SearchResultTrack?>(null)
    }
    var trackPendingAddToPlaylist by remember {
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
                LibraryTabButton(
                    label = "Álbumes",
                    tab = LibraryTab.ALBUMS,
                    current = uiState.tab,
                    onSelect = viewModel::setTab,
                )
                LibraryTabButton(
                    label = "Sencillos",
                    tab = LibraryTab.SINGLES,
                    current = uiState.tab,
                    onSelect = viewModel::setTab,
                )
                LibraryTabButton(
                    label = "Favoritos",
                    tab = LibraryTab.FAVORITES,
                    current = uiState.tab,
                    onSelect = viewModel::setTab,
                )
            }

            Spacer(Modifier.height(8.dp))

            val isEmpty = when (uiState.tab) {
                LibraryTab.ALBUMS -> uiState.albumsByArtist.isEmpty()
                LibraryTab.SINGLES -> uiState.singlesByArtist.isEmpty()
                LibraryTab.FAVORITES -> uiState.favorites.isEmpty()
            }

            if (isEmpty) {
                Text(
                    when (uiState.tab) {
                        LibraryTab.ALBUMS -> "Todavía no hay álbumes descargados."
                        LibraryTab.SINGLES -> "Todavía no hay sencillos descargados."
                        LibraryTab.FAVORITES -> "Todavía no hay canciones favoritas."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }

            if (uiState.tab == LibraryTab.FAVORITES && uiState.favorites.isNotEmpty()) {
                FavoritesHeaderRow(
                    count = uiState.favorites.size,
                    onPlayAll = viewModel::playFavorites,
                    onShuffle = viewModel::playFavoritesShuffled,
                )
            }

            val listItems: List<LibraryListItem> = when (uiState.tab) {
                LibraryTab.ALBUMS -> buildList {
                    uiState.albumsByArtist.forEach { (artist, albums) ->
                        add(LibraryListItem.ArtistAlbumsHeader(artist))
                        albums.forEach { (album, tracks) ->
                            add(LibraryListItem.AlbumHeader(artist, album))
                            tracks.forEach { track ->
                                add(LibraryListItem.TrackRow(track))
                            }
                        }
                    }
                }
                LibraryTab.SINGLES -> buildList {
                    uiState.singlesByArtist.forEach { (artist, tracks) ->
                        add(LibraryListItem.ArtistSinglesHeader(artist))
                        tracks.forEach { track ->
                            add(LibraryListItem.TrackRow(track))
                        }
                    }
                }
                LibraryTab.FAVORITES ->
                    uiState.favorites.map { LibraryListItem.TrackRow(it) }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(
                    listItems,
                    key = { item ->
                        when (item) {
                            is LibraryListItem.ArtistAlbumsHeader ->
                                "artist-albums:${item.artist}"
                            is LibraryListItem.ArtistSinglesHeader ->
                                "artist-singles:${item.artist}"
                            is LibraryListItem.AlbumHeader ->
                                "album:${item.artist}:${item.album}"
                            is LibraryListItem.TrackRow ->
                                "track:${item.track.youtubeId}"
                        }
                    },
                ) { item ->
                    when (item) {
                        is LibraryListItem.ArtistAlbumsHeader -> ArtistHeaderRow(
                            artist = item.artist,
                            onPlayAll = { viewModel.playArtistAlbums(item.artist) },
                            onShuffle = {
                                viewModel.playArtistAlbumsShuffled(item.artist)
                            },
                        )
                        is LibraryListItem.ArtistSinglesHeader -> ArtistHeaderRow(
                            artist = item.artist,
                            onPlayAll = { viewModel.playArtistSingles(item.artist) },
                            onShuffle = {
                                viewModel.playArtistSinglesShuffled(item.artist)
                            },
                        )
                        is LibraryListItem.AlbumHeader -> AlbumHeaderRow(
                            artist = item.artist,
                            album = item.album,
                            tracks = uiState.albumsByArtist[item.artist]?.get(item.album)
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
                            onEdit = { trackPendingEdit = item.track },
                            onAddToPlaylist = {
                                trackPendingAddToPlaylist = item.track
                            },
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

    trackPendingEdit?.let { track ->
        EditMetadataDialog(
            track = track,
            onDismiss = { trackPendingEdit = null },
            onSave = { title, artist, album ->
                viewModel.editMetadata(track, title, artist, album)
                trackPendingEdit = null
            },
        )
    }

    uiState.editMetadataError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissEditMetadataError,
            title = { Text("No se pudo guardar") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissEditMetadataError) {
                    Text("Entendido")
                }
            },
        )
    }

    trackPendingAddToPlaylist?.let { track ->
        AddToPlaylistDialog(
            youtubeId = track.youtubeId,
            onDismiss = { trackPendingAddToPlaylist = null },
        )
    }
}

@Composable
private fun LibraryTabButton(
    label: String,
    tab: LibraryTab,
    current: LibraryTab,
    onSelect: (LibraryTab) -> Unit,
) {
    TextButton(onClick = { onSelect(tab) }) {
        Text(
            label,
            fontWeight = if (current == tab) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            },
        )
    }
}

@Composable
private fun FavoritesHeaderRow(
    count: Int,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$count favoritas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPlayAll) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir favoritos")
        }
        IconButton(onClick = onShuffle) {
            Icon(Icons.Filled.Shuffle, contentDescription = "Aleatorio")
        }
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
            text = displayArtistName(artist),
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
    // Todos los álbumes que llegan aquí son reales -- los sencillos
    // (sin álbum) ahora viven en su propia pestaña, nunca en
    // albumsByArtist -- así que siempre hay algo que buscar.
    LaunchedEffect(artist, album) {
        onRequestCoverArt(artist, album)
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
    onEdit: () -> Unit,
    onAddToPlaylist: () -> Unit,
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
                displayArtistName(track.artist ?: track.channelTitle),
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
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Editar metadatos",
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

/**
 * Manual metadata edit dialog (PASO 7, H03). Album is edited as free
 * text; an empty value maps to null (i.e. "Sencillos") on save,
 * mirroring how DownloadDirManager and LibraryViewModel.recompute()
 * already treat a missing album elsewhere in the app.
 * ---
 * Diálogo de edición manual de metadatos (PASO 7, H03). El álbum se
 * edita como texto libre; un valor vacío se convierte en null (es
 * decir, "Sencillos") al guardar, igual que ya trata
 * DownloadDirManager y LibraryViewModel.recompute() la ausencia de
 * álbum en el resto de la app.
 */
@Composable
private fun EditMetadataDialog(
    track: SearchResultTrack,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String) -> Unit,
) {
    var title by remember(track.youtubeId) { mutableStateOf(track.title) }
    var artist by remember(track.youtubeId) {
        mutableStateOf(track.artist ?: track.channelTitle)
    }
    var album by remember(track.youtubeId) { mutableStateOf(track.album ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar metadatos") },
        text = {
            Column {
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
                if (track.filePath != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Si cambias artista o álbum, el archivo se moverá " +
                            "a la nueva carpeta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, artist, album) },
                enabled = title.isNotBlank() && artist.isNotBlank(),
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

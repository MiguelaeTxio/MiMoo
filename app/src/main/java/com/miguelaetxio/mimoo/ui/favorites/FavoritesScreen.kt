package com.miguelaetxio.mimoo.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.favorites.FavoritePlaylistRow
import com.miguelaetxio.mimoo.data.favorites.FavoriteTrackRow
import com.miguelaetxio.mimoo.ui.theme.glassChip

/**
 * Pantalla unificada de Favoritos (sesión de diseño, 2026-08-02).
 * Centraliza el listado y la generación de popurrís que antes se
 * habría repartido entre Biblioteca/Explorador/Playlists -- decisión
 * explícita de Miguel Ángel ("centralizamos favoritos únicamente en
 * la vista de favoritos").
 * ---
 * Unified Favorites screen (design session, 2026-08-02). Centralizes
 * the listing and popurrí generation that would otherwise have been
 * spread across Biblioteca/Explorador/Playlists -- explicit decision
 * from Miguel Ángel ("we centralize favorites only in the favorites
 * view").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
    onOpenPlaylist: (playlistId: Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip(interactive = false)) {
                        Text(
                            "Favoritos",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier.padding(4.dp)
                            .glassChip(shape = androidx.compose.foundation.shape.CircleShape),
                    ) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menú")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = uiState.tab.ordinal) {
                Tab(
                    selected = uiState.tab == FavoritesTab.ARTISTS,
                    onClick = { viewModel.selectTab(FavoritesTab.ARTISTS) },
                    text = { Text("Artistas") },
                )
                Tab(
                    selected = uiState.tab == FavoritesTab.ALBUMS,
                    onClick = { viewModel.selectTab(FavoritesTab.ALBUMS) },
                    text = { Text("Álbumes") },
                )
                Tab(
                    selected = uiState.tab == FavoritesTab.TRACKS,
                    onClick = { viewModel.selectTab(FavoritesTab.TRACKS) },
                    text = { Text("Sencillos") },
                )
                Tab(
                    selected = uiState.tab == FavoritesTab.PLAYLISTS,
                    onClick = { viewModel.selectTab(FavoritesTab.PLAYLISTS) },
                    text = { Text("Listas") },
                )
            }

            if (uiState.isGeneratingPopurri) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (uiState.tab) {
                FavoritesTab.ARTISTS -> ArtistsTab(uiState, viewModel)
                FavoritesTab.ALBUMS -> AlbumsTab(uiState, viewModel)
                FavoritesTab.TRACKS -> TracksTab(uiState, viewModel)
                FavoritesTab.PLAYLISTS -> PlaylistsTab(uiState, viewModel, onOpenPlaylist)
            }
        }
    }
}

@Composable
private fun EmptyTabMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Cabecera común a Artistas/Álbumes: "Marcar/desmarcar todos" +
 * botones de reproducción según selección.
 *
 * Bug real reportado por Miguel Ángel (2026-08-03), confirmado con
 * log: al no ver reacción inmediata, pulsó reproducir tres veces
 * seguidas en 6 segundos -- las tres arrancaron `playArtistsProgressively()`
 * en paralelo, cada una resolviendo los mismos artistas por su
 * cuenta, un desperdicio real de tiempo y red que además podía
 * pisarse entre sí (varias llamadas a `playQueue()`/`playQueueShuffled()`
 * compitiendo). `isGenerating` bloquea los botones mientras ya hay
 * un popurrí en marcha.
 * ---
 * Header shared by Artists/Albums: "Select/deselect all" + playback
 * buttons based on selection.
 *
 * Real bug reported by Miguel Ángel (2026-08-03), confirmed with a
 * log: not seeing an immediate reaction, he tapped play three times
 * in 6 seconds -- all three started `playArtistsProgressively()` in
 * parallel, each independently resolving the same artists, a real
 * waste of time and network that could also race against each other
 * (several `playQueue()`/`playQueueShuffled()` calls competing).
 * `isGenerating` blocks the buttons while a popurrí is already in
 * progress.
 */
@Composable
private fun SelectionHeader(
    selectedCount: Int,
    totalCount: Int,
    isGenerating: Boolean,
    onToggleSelectAll: () -> Unit,
    onPlaySequential: () -> Unit,
    onPlayShuffled: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = totalCount > 0 && selectedCount == totalCount,
            onCheckedChange = { onToggleSelectAll() },
        )
        Text(
            if (selectedCount == 0) "Marcar todos" else "$selectedCount seleccionados",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(onClick = onPlaySequential, enabled = selectedCount > 0 && !isGenerating) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir popurrí secuencial")
        }
        IconButton(onClick = onPlayShuffled, enabled = selectedCount > 0 && !isGenerating) {
            Icon(Icons.Filled.Shuffle, contentDescription = "Reproducir popurrí aleatorio")
        }
    }
}

@Composable
private fun ArtistsTab(uiState: FavoritesUiState, viewModel: FavoritesViewModel) {
    if (uiState.artists.isEmpty()) {
        EmptyTabMessage("Todavía no tienes artistas favoritos.")
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        SelectionHeader(
            selectedCount = uiState.selectedArtists.size,
            totalCount = uiState.artists.size,
            isGenerating = uiState.isGeneratingPopurri,
            onToggleSelectAll = viewModel::toggleSelectAllArtists,
            onPlaySequential = { viewModel.playSelectedArtists(shuffle = false) },
            onPlayShuffled = { viewModel.playSelectedArtists(shuffle = true) },
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(uiState.artists, key = { it.artist }) { favorite ->
                FavoriteRow(
                    icon = Icons.Filled.Person,
                    title = favorite.artist,
                    subtitle = null,
                    checked = favorite.artist in uiState.selectedArtists,
                    isGenerating = uiState.isGeneratingPopurri,
                    onCheckedChange = { viewModel.toggleArtistSelection(favorite.artist) },
                    onPlay = { viewModel.playArtist(favorite.artist, shuffle = false) },
                    onShuffle = { viewModel.playArtist(favorite.artist, shuffle = true) },
                    onRemoveFavorite = { viewModel.removeArtistFavorite(favorite.artist) },
                )
            }
        }
    }
}

@Composable
private fun AlbumsTab(uiState: FavoritesUiState, viewModel: FavoritesViewModel) {
    if (uiState.albums.isEmpty()) {
        EmptyTabMessage("Todavía no tienes álbumes favoritos.")
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        SelectionHeader(
            selectedCount = uiState.selectedAlbums.size,
            totalCount = uiState.albums.size,
            isGenerating = uiState.isGeneratingPopurri,
            onToggleSelectAll = viewModel::toggleSelectAllAlbums,
            onPlaySequential = { viewModel.playSelectedAlbums(shuffle = false) },
            onPlayShuffled = { viewModel.playSelectedAlbums(shuffle = true) },
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(uiState.albums, key = { "${it.artist}|${it.album}" }) { favorite ->
                val key = AlbumKey(favorite.artist, favorite.album)
                FavoriteRow(
                    icon = Icons.Filled.Album,
                    title = favorite.album,
                    subtitle = favorite.artist,
                    checked = key in uiState.selectedAlbums,
                    isGenerating = uiState.isGeneratingPopurri,
                    onCheckedChange = { viewModel.toggleAlbumSelection(key) },
                    onPlay = { viewModel.playAlbum(favorite.artist, favorite.album, shuffle = false) },
                    onShuffle = { viewModel.playAlbum(favorite.artist, favorite.album, shuffle = true) },
                    onRemoveFavorite = { viewModel.removeAlbumFavorite(favorite.artist, favorite.album) },
                )
            }
        }
    }
}

@Composable
private fun TracksTab(uiState: FavoritesUiState, viewModel: FavoritesViewModel) {
    if (uiState.tracks.isEmpty()) {
        EmptyTabMessage("Todavía no tienes sencillos favoritos.")
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${uiState.tracks.size} sencillos favoritos",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(
                onClick = { viewModel.playAllFavoriteTracks(shuffle = false) },
                enabled = !uiState.isGeneratingPopurri,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir todos, secuencial")
            }
            IconButton(
                onClick = { viewModel.playAllFavoriteTracks(shuffle = true) },
                enabled = !uiState.isGeneratingPopurri,
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Reproducir todos, aleatorio")
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(uiState.tracks, key = { it.youtubeId }) { row: FavoriteTrackRow ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .glassChip()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            row.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // H18 (S032) -- SOLO play, sin aleatorio: un único
                    // sencillo no tiene nada que barajar.
                    IconButton(
                        onClick = { viewModel.playTrack(row) },
                        enabled = !uiState.isGeneratingPopurri,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir")
                    }
                    IconButton(onClick = { viewModel.removeTrackFavorite(row) }) {
                        Icon(Icons.Filled.Star, contentDescription = "Quitar de favoritos")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    uiState: FavoritesUiState,
    viewModel: FavoritesViewModel,
    onOpenPlaylist: (playlistId: Long) -> Unit,
) {
    if (uiState.playlists.isEmpty()) {
        EmptyTabMessage(
            "Todavía no tienes listas de reproducción favoritas. " +
                "Márcalas desde \"Listas\" en el menú.",
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(uiState.playlists, key = { it.playlist.id }) { row: FavoritePlaylistRow ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .glassChip()
                    .clickable { onOpenPlaylist(row.playlist.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(row.playlist.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                // H18 (S032) -- play/aleatorio de la playlist entera en su orden guardado.
                IconButton(
                    onClick = { viewModel.playPlaylist(row.playlist.id, shuffle = false) },
                    enabled = !uiState.isGeneratingPopurri,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir")
                }
                IconButton(
                    onClick = { viewModel.playPlaylist(row.playlist.id, shuffle = true) },
                    enabled = !uiState.isGeneratingPopurri,
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Reproducir aleatorio")
                }
                IconButton(onClick = { viewModel.removePlaylistFavorite(row.playlist.id) }) {
                    Icon(Icons.Filled.Star, contentDescription = "Quitar de favoritos")
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    isGenerating: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onRemoveFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .glassChip()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // H18 (S032) -- play/aleatorio de ESTE item concreto, sin
        // marcar casilla ni usar el popurrí de selección de arriba.
        IconButton(onClick = onPlay, enabled = !isGenerating) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir")
        }
        IconButton(onClick = onShuffle, enabled = !isGenerating) {
            Icon(Icons.Filled.Shuffle, contentDescription = "Reproducir aleatorio")
        }
        IconButton(onClick = onRemoveFavorite) {
            Icon(Icons.Filled.Star, contentDescription = "Quitar de favoritos")
        }
    }
}

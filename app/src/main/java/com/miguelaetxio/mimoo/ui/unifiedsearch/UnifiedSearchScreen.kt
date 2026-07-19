package com.miguelaetxio.mimoo.ui.unifiedsearch

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.miguelaetxio.mimoo.data.remote.AlbumCandidate
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzArtistSummary
import com.miguelaetxio.mimoo.data.remote.dto.SearchTypeResult
import com.miguelaetxio.mimoo.data.remote.dto.TrackDto
import com.miguelaetxio.mimoo.ui.theme.glassChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSearchScreen(
    viewModel: UnifiedSearchViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
    onOpenSong: (artistName: String, songTitle: String) -> Unit,
    onOpenAlbum: (artistName: String, albumName: String) -> Unit,
    onOpenArtist: (artistName: String) -> Unit,
    onOpenExternalLink: (url: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val subscribedChannelIds by viewModel.subscribedChannelIds.collectAsState()
    val activity = LocalContext.current as Activity
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.syncBlockedMessage) {
        uiState.syncBlockedMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSyncBlockedMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip(interactive = false)) {
                        Text("miMoo", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(4.dp).glassChip(shape = CircleShape)) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menú")
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
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar canciones, álbumes, artistas, listas, canales") },
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

            if (!uiState.isSearching && uiState.hasSearched && uiState.isEmpty) {
                Text(
                    "Sin resultados para esta búsqueda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                section(
                    title = "Canciones",
                    items = uiState.songs,
                    key = { "song-${it.youtubeId}" },
                ) { song ->
                    SongResultRow(
                        song = song,
                        onClick = { onOpenSong(song.channelTitle, song.title) },
                    )
                }
                section(
                    title = "Álbumes",
                    items = uiState.albums,
                    key = { "album-${it.mbid}" },
                ) { album ->
                    AlbumResultRow(
                        album = album,
                        onClick = { onOpenAlbum(album.artist ?: "", album.title) },
                    )
                }
                section(
                    title = "Artistas",
                    items = uiState.artists,
                    key = { "artist-${it.id}" },
                ) { artist ->
                    ArtistResultRow(
                        artist = artist,
                        onClick = { onOpenArtist(artist.name) },
                    )
                }
                section(
                    title = "Listas de reproducción",
                    items = uiState.playlists,
                    key = { "playlist-${it.id}" },
                ) { playlist ->
                    TypeResultRow(
                        result = playlist,
                        onOpen = { onOpenExternalLink(playlist.url) },
                    )
                }
                section(
                    title = "Canales",
                    items = uiState.channels,
                    key = { "channel-${it.id}" },
                ) { channel ->
                    TypeResultRow(
                        result = channel,
                        onOpen = { onOpenExternalLink(channel.url) },
                        isChannel = true,
                        isSubscribed = channel.id in subscribedChannelIds,
                        onToggleSubscription = {
                            viewModel.toggleChannelSubscription(activity, channel)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Añade una sección con cabecera a un LazyColumn -- mismo patrón que
 * ArtistScreen (bloque 3) para varias listas tipadas dentro de un
 * único scroll. No añade nada si la lista está vacía (una búsqueda
 * sin listas ni canales, por ejemplo, no deja secciones-cabecera
 * vacías colgando).
 * ---
 * Adds a headed section to a LazyColumn -- same pattern as
 * ArtistScreen (block 3) for several typed lists inside a single
 * scroll. Adds nothing if the list is empty (a search with no
 * playlists or channels, for example, doesn't leave empty section
 * headers hanging).
 */
private fun <T> androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    items: List<T>,
    key: (T) -> String,
    row: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header-$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
    items(items, key = { key(it) }) { entry ->
        row(entry)
        HorizontalDivider()
    }
}

@Composable
private fun SongResultRow(song: TrackDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResultThumbnail(song.thumbnailUrl, fallbackIcon = Icons.Filled.MusicNote)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                song.channelTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlbumResultRow(album: AlbumCandidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResultThumbnail(album.coverArtUrl, fallbackIcon = Icons.Filled.Album)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(album.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    append(album.artist ?: "Artista desconocido")
                    album.year?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArtistResultRow(artist: MusicBrainzArtistSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.bodyLarge)
            artist.country?.let { country ->
                Text(
                    country,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Miniatura con fallback genérico -- mismo patrón que CandidateCoverThumbnail (AlbumSearchScreen, ahora AlbumScreen). */
@Composable
private fun ResultThumbnail(url: String?, fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    val shape = RoundedCornerShape(4.dp)
    if (url == null) {
        FallbackThumbnail(shape, fallbackIcon)
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier.size(48.dp).clip(shape),
        error = { FallbackThumbnail(shape, fallbackIcon) },
    )
}

@Composable
private fun FallbackThumbnail(shape: RoundedCornerShape, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Fila de un resultado de playlist/canal -- MISMA UI y MISMO
 * comportamiento que SearchTypeResultRow del SearchScreen antiguo
 * (roadmap punto 2: "sin cambios de H04/H10" para listas, "sin
 * cambios" para canales). Renombrada solo por vivir en este paquete
 * nuevo.
 * ---
 * Playlist/channel result row -- SAME UI and SAME behavior as the old
 * SearchScreen's SearchTypeResultRow (roadmap point 2: "no changes
 * from H04/H10" for playlists, "no changes" for channels). Renamed
 * only because it now lives in this new package.
 */
@Composable
private fun TypeResultRow(
    result: SearchTypeResult,
    onOpen: () -> Unit,
    isChannel: Boolean = false,
    isSubscribed: Boolean = false,
    onToggleSubscription: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .glassChip()
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (result.thumbnailUrl != null) {
            SubcomposeAsyncImage(
                model = result.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp)),
                error = {},
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(result.title, style = MaterialTheme.typography.bodyLarge)
            if (result.subtitle.isNotBlank()) {
                Text(
                    result.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isChannel) {
            IconButton(onClick = onToggleSubscription) {
                Icon(
                    imageVector = if (isSubscribed) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isSubscribed) {
                        "Cancelar suscripción al canal"
                    } else {
                        "Suscribirse al canal"
                    },
                    tint = if (isSubscribed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Icon(
            Icons.Filled.OpenInNew,
            contentDescription = "Abrir",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

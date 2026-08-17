package com.miguelaetxio.mimoo.ui.explorer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.miguelaetxio.mimoo.ui.library.LetterGrid
import com.miguelaetxio.mimoo.ui.library.displayArtistName
import com.miguelaetxio.mimoo.ui.theme.glassChip
import com.miguelaetxio.mimoo.ui.unifiedsearch.SearchResultKind
import com.miguelaetxio.mimoo.util.SearchNormalizer

/**
 * Explorador (H12, S018 rediseño) -- por letra, dos bloques: "Ya
 * tienes" (local, completo) y "Explorar MusicBrainz" (paginado,
 * scroll infinito, sin repetir lo del bloque local). Ver comentario
 * de clase de ExplorerViewModel para el porqué del rediseño.
 * ---
 * Explorer (H12, S018 redesign) -- per letter, two blocks: "You
 * already have" (local, complete) and "Explore MusicBrainz"
 * (paginated, infinite scroll, not repeating the local block). See
 * ExplorerViewModel's class comment for why this was redesigned.
 */
/**
 * Campo de búsqueda embebido en el Explorador (S034, MiMoo-S34H12) --
 * incidencia real de S033: "el explorador carece de campo búsqueda
 * para buscar en musicbrainz". Dispara la misma búsqueda unificada
 * (MusicBrainz + YouTube, ver ExplorerViewModel.search()) sin
 * navegar a la pantalla "Búsqueda" aparte -- mientras hay una
 * búsqueda activa (uiState.isSearchActive), sustituye el contenido
 * de letras/artistas por los resultados; al vaciar el campo, vuelve
 * al drill normal donde estuviera.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
    onOpenArtist: (artistName: String) -> Unit,
    onOpenSong: (artistName: String, songTitle: String) -> Unit,
    onOpenAlbum: (artistName: String, albumName: String) -> Unit,
    onOpenExternalLink: (url: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val subscribedChannelIds by viewModel.subscribedChannelIds.collectAsState()
    val activity = LocalContext.current as Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val canGoBack = uiState.drill is ExplorerDrillLevel.Artists && !uiState.isSearchActive

    BackHandler(enabled = canGoBack) { viewModel.backToLetters() }
    BackHandler(enabled = uiState.isSearchActive) { viewModel.clearSearch() }

    LaunchedEffect(uiState.searchSyncBlockedMessage) {
        uiState.searchSyncBlockedMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSearchSyncBlockedMessage()
        }
    }

    val title = when (val drill = uiState.drill) {
        ExplorerDrillLevel.Letters -> "Explorador"
        is ExplorerDrillLevel.Artists -> "Artistas · ${drill.letter}"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip(interactive = false)) {
                        Text(title, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = viewModel::backToLetters) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    } else {
                        Box(modifier = Modifier.padding(4.dp).glassChip(shape = CircleShape)) {
                            IconButton(onClick = onOpenDrawer) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menú")
                            }
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
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar canciones, álbumes, artistas, listas, canales") },
                trailingIcon = {
                    if (uiState.isSearchActive) {
                        IconButton(onClick = viewModel::clearSearch) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpiar búsqueda")
                        }
                    } else {
                        IconButton(onClick = viewModel::search) {
                            Icon(Icons.Filled.Search, contentDescription = "Buscar")
                        }
                    }
                },
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            if (uiState.isSearchActive) {
                ExplorerSearchContent(
                    uiState = uiState,
                    subscribedChannelIds = subscribedChannelIds,
                    onOpenSong = onOpenSong,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                    onOpenExternalLink = onOpenExternalLink,
                    onSetFilter = viewModel::setSearchFilter,
                    onToggleChannelSubscription = { result ->
                        viewModel.toggleSearchChannelSubscription(activity, result)
                    },
                )
            } else {
                when (val drill = uiState.drill) {
                    ExplorerDrillLevel.Letters -> {
                        LetterGrid(
                            letters = uiState.letters,
                            emptyMessage = "",
                            onSelect = viewModel::selectLetter,
                        )
                    }
                    is ExplorerDrillLevel.Artists -> {
                        ExplorerArtistContent(
                            localArtists = uiState.localArtistsForLetter,
                            onlineArtists = uiState.onlineArtists,
                            isLoadingOnline = uiState.isLoadingOnline,
                            hasMoreOnline = uiState.hasMoreOnline,
                            dislikedArtistKeys = uiState.dislikedArtistKeys,
                            onArtistClick = onOpenArtist,
                            onLoadMore = viewModel::loadMoreOnline,
                            onToggleDislike = viewModel::toggleArtistDisliked,
                        )
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
}

/**
 * Contenido de resultados de la búsqueda embebida -- mismas cinco
 * secciones tipadas y mismos chips de filtro que UnifiedSearchScreen
 * (H12, S018), reutilizando aquí dentro del Explorador en vez de
 * navegar a esa pantalla aparte.
 */
@Composable
private fun ColumnScope.ExplorerSearchContent(
    uiState: ExplorerUiState,
    subscribedChannelIds: List<String>,
    onOpenSong: (artistName: String, songTitle: String) -> Unit,
    onOpenAlbum: (artistName: String, albumName: String) -> Unit,
    onOpenArtist: (artistName: String) -> Unit,
    onOpenExternalLink: (url: String) -> Unit,
    onSetFilter: (SearchResultKind) -> Unit,
    onToggleChannelSubscription: (SearchTypeResult) -> Unit,
) {
    if (uiState.isSearching) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExplorerSearchFilterChip(
            label = "Artista",
            selected = uiState.searchActiveFilter == SearchResultKind.ARTIST,
            onClick = { onSetFilter(SearchResultKind.ARTIST) },
        )
        ExplorerSearchFilterChip(
            label = "Álbum",
            selected = uiState.searchActiveFilter == SearchResultKind.ALBUM,
            onClick = { onSetFilter(SearchResultKind.ALBUM) },
        )
        ExplorerSearchFilterChip(
            label = "Sencillo",
            selected = uiState.searchActiveFilter == SearchResultKind.SONG,
            onClick = { onSetFilter(SearchResultKind.SONG) },
        )
        ExplorerSearchFilterChip(
            label = "Lista",
            selected = uiState.searchActiveFilter == SearchResultKind.PLAYLIST,
            onClick = { onSetFilter(SearchResultKind.PLAYLIST) },
        )
        ExplorerSearchFilterChip(
            label = "Canal",
            selected = uiState.searchActiveFilter == SearchResultKind.CHANNEL,
            onClick = { onSetFilter(SearchResultKind.CHANNEL) },
        )
    }
    Spacer(Modifier.height(8.dp))

    uiState.searchErrorMessage?.let { message ->
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }

    if (!uiState.isSearching && uiState.isSearchEmpty) {
        Text(
            "Sin resultados para esta búsqueda.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }

    val filter = uiState.searchActiveFilter
    LazyColumn(modifier = Modifier.weight(1f)) {
        if (filter == null || filter == SearchResultKind.SONG) {
            explorerSearchSection(
                title = "Canciones",
                items = uiState.searchSongs,
                key = { "song-${it.youtubeId}" },
            ) { song ->
                ExplorerSongResultRow(
                    song = song,
                    onClick = { onOpenSong(song.channelTitle, song.title) },
                )
            }
        }
        if (filter == null || filter == SearchResultKind.ALBUM) {
            explorerSearchSection(
                title = "Álbumes",
                items = uiState.searchAlbums,
                key = { "album-${it.mbid}" },
            ) { album ->
                ExplorerAlbumResultRow(
                    album = album,
                    onClick = { onOpenAlbum(album.artist ?: "", album.title) },
                )
            }
        }
        if (filter == null || filter == SearchResultKind.ARTIST) {
            explorerSearchSection(
                title = "Artistas",
                items = uiState.searchArtists,
                key = { "artist-${it.id}" },
            ) { artist ->
                ExplorerSearchArtistResultRow(
                    artist = artist,
                    onClick = { onOpenArtist(artist.name) },
                )
            }
        }
        if (filter == null || filter == SearchResultKind.PLAYLIST) {
            explorerSearchSection(
                title = "Listas de reproducción",
                items = uiState.searchPlaylists,
                key = { "playlist-${it.id}" },
            ) { playlist ->
                ExplorerTypeResultRow(
                    result = playlist,
                    onOpen = { onOpenExternalLink(playlist.url) },
                )
            }
        }
        if (filter == null || filter == SearchResultKind.CHANNEL) {
            explorerSearchSection(
                title = "Canales",
                items = uiState.searchChannels,
                key = { "channel-${it.id}" },
            ) { channel ->
                ExplorerTypeResultRow(
                    result = channel,
                    onOpen = { onOpenExternalLink(channel.url) },
                    isChannel = true,
                    isSubscribed = channel.id in subscribedChannelIds,
                    onToggleSubscription = { onToggleChannelSubscription(channel) },
                )
            }
        }
    }
}

@Composable
private fun ExplorerSearchFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/** Mismo patrón que UnifiedSearchScreen.section() -- cabecera + filas, sin nada si la lista está vacía. */
private fun <T> androidx.compose.foundation.lazy.LazyListScope.explorerSearchSection(
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
private fun ExplorerSongResultRow(song: TrackDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExplorerResultThumbnail(song.thumbnailUrl, fallbackIcon = Icons.Filled.MusicNote)
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
private fun ExplorerAlbumResultRow(album: AlbumCandidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExplorerResultThumbnail(album.coverArtUrl, fallbackIcon = Icons.Filled.Album)
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
private fun ExplorerSearchArtistResultRow(artist: MusicBrainzArtistSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun ExplorerResultThumbnail(url: String?, fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    val shape = RoundedCornerShape(4.dp)
    if (url == null) {
        ExplorerFallbackThumbnail(shape, fallbackIcon)
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier.size(48.dp).clip(shape),
        error = { ExplorerFallbackThumbnail(shape, fallbackIcon) },
    )
}

@Composable
private fun ExplorerFallbackThumbnail(shape: RoundedCornerShape, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier.size(48.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Mismo patrón que UnifiedSearchScreen.TypeResultRow() -- fila de playlist/canal. */
@Composable
private fun ExplorerTypeResultRow(
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
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
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
                    contentDescription = if (isSubscribed) "Cancelar suscripción al canal" else "Suscribirse al canal",
                    tint = if (isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(Icons.Filled.OpenInNew, contentDescription = "Abrir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Contenido del nivel "Artistas" -- una única LazyColumn con dos
 * secciones (local, luego MusicBrainz) para que el scroll infinito
 * funcione de forma natural sobre toda la lista, no por separado por
 * bloque. Dispara `onLoadMore()` cuando el usuario se acerca a los
 * últimos 5 elementos visibles de la lista online -- petición
 * explícita de Miguel Ángel ("si paso el dedo, que vaya mostrando
 * más"), sin botón manual de "cargar más".
 * ---
 * "Artists" level content -- a single LazyColumn with two sections
 * (local, then MusicBrainz) so infinite scroll works naturally over
 * the whole list, not separately per block. Fires `onLoadMore()` when
 * the user nears the last 5 visible items of the online list --
 * explicit request from Miguel Ángel ("scrolling should load more"),
 * no manual "load more" button.
 */
@Composable
private fun ColumnScope.ExplorerArtistContent(
    localArtists: List<String>,
    onlineArtists: List<MusicBrainzArtistSummary>,
    isLoadingOnline: Boolean,
    hasMoreOnline: Boolean,
    dislikedArtistKeys: Set<String>,
    onArtistClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onToggleDislike: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 5
        }
    }
    LaunchedEffect(shouldLoadMore, hasMoreOnline, isLoadingOnline) {
        if (shouldLoadMore && hasMoreOnline && !isLoadingOnline) {
            onLoadMore()
        }
    }

    LazyColumn(modifier = Modifier.weight(1f), state = listState) {
        if (localArtists.isNotEmpty()) {
            item(key = "header-local") {
                Text(
                    "Ya tienes",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            items(localArtists, key = { "local-$it" }) { artist ->
                ExplorerArtistRow(
                    name = displayArtistName(artist),
                    isLocal = true,
                    isDisliked = SearchNormalizer.normalizeArtistName(artist) in dislikedArtistKeys,
                    onClick = { onArtistClick(artist) },
                    onToggleDislike = { onToggleDislike(artist) },
                )
            }
        }
        item(key = "header-online") {
            Text(
                "Explorar MusicBrainz",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
        items(onlineArtists, key = { "online-${it.id}" }) { artist ->
            ExplorerArtistRow(
                name = artist.name,
                isLocal = false,
                isDisliked = SearchNormalizer.normalizeArtistName(artist.name) in dislikedArtistKeys,
                onClick = { onArtistClick(artist.name) },
                onToggleDislike = { onToggleDislike(artist.name) },
            )
        }
        if (isLoadingOnline) {
            item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
        if (!hasMoreOnline && onlineArtists.isEmpty() && localArtists.isEmpty() && !isLoadingOnline) {
            item(key = "empty") {
                Text(
                    "No se ha encontrado nada para esta letra.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ExplorerArtistRow(
    name: String,
    isLocal: Boolean,
    isDisliked: Boolean,
    onClick: () -> Unit,
    onToggleDislike: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .glassChip()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        // S018 -- marca "ya tienes descargado" pedida por Miguel Ángel
        // explícitamente para el bloque local dentro de la lista por
        // letra (independiente de la marca de descargado dentro de
        // ArtistScreen, que es por álbum).
        if (isLocal) {
            Icon(
                Icons.Filled.CloudDone,
                contentDescription = "Ya tienes contenido de este artista",
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
        }
        // H16 -- acción "no me gusta" desde el Explorador (roadmap
        // punto 5). Solo a nivel de artista -- esta pantalla no lista
        // temas individuales (drill Letras -> Artistas únicamente), a
        // diferencia del ExoPlayer, que sí ofrece la disyuntiva
        // artista/tema porque siempre hay una pista concreta sonando.
        IconButton(onClick = onToggleDislike, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.ThumbDown,
                contentDescription = if (isDisliked) "Quitar de la lista negra" else "No me gusta",
                tint = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

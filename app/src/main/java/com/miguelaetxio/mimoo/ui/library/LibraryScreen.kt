package com.miguelaetxio.mimoo.ui.library

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.miguelaetxio.mimoo.ui.theme.glassChip
import com.miguelaetxio.mimoo.ui.theme.GlassTokens
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.ui.playlist.AddToPlaylistDialog

/**
 * Comparte un enlace vía el selector nativo de Android (WhatsApp
 * incluido) -- petición explícita de Miguel Ángel (2026-07-04): poder
 * pasarle a su pareja el enlace de un disco que está escuchando, o
 * pedírselo ella a él, directamente desde Biblioteca.
 * ---
 * Shares a link via Android's native share sheet (WhatsApp included)
 * -- explicit request from Miguel Ángel (2026-07-04): being able to
 * send his partner the link of an album he's listening to, or have
 * her ask him for one, straight from Biblioteca.
 */
private fun shareLink(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/**
 * H10 (S011) -- comparte un archivo `.mimoo` real (EXTRA_STREAM), no
 * texto -- rediseñado tras la prueba real de Miguel Ángel: un texto
 * plano no se puede "tocar para abrir", un archivo sí. Permiso de
 * lectura otorgado explícitamente al chooser -- FileProvider lo exige
 * para cualquier app que reciba el Uri `content://`.
 */
private fun shareFile(context: android.content.Context, uri: android.net.Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val screenActivity = LocalContext.current as Activity

    // H10 (S011) -- en cuanto se genera un archivo .txt (álbum,
    // pista, artista o sencillos favoritos, "Compartir con réplica
    // total"), abre el selector de Compartir del sistema con ese
    // ARCHIVO (shareFile(), no shareLink() -- ver comentario de esa
    // función más abajo para el porqué del cambio).
    val generatedShareFileUri by viewModel.generatedShareFileUri.collectAsState()
    LaunchedEffect(generatedShareFileUri) {
        generatedShareFileUri?.let { uri ->
            shareFile(screenActivity, uri)
            viewModel.consumeGeneratedShareFileUri()
        }
    }
    var trackPendingDelete by remember {
        mutableStateOf<SearchResultTrack?>(null)
    }
    var trackPendingEdit by remember {
        mutableStateOf<SearchResultTrack?>(null)
    }
    // Lista de youtubeIds pendiente para AddToPlaylistDialog -- una
    // sola pista (listOf(track)) o un álbum entero (tracks.map { it })
    // según desde dónde se abra. Petición explícita de Miguel Ángel
    // (2026-07-04): poder añadir álbumes completos a una lista, no
    // solo pista a pista.
    // ---
    // Pending track list for AddToPlaylistDialog -- a single track
    // (listOf(track)) or a whole album (tracks.map { it }) depending on
    // where it's opened from. Explicit request from Miguel Ángel
    // (2026-07-04): being able to add whole albums to a playlist, not
    // just track by track.
    var tracksPendingAddToPlaylist by remember {
        mutableStateOf<List<SearchResultTrack>?>(null)
    }
    // Confirmaciones de borrado en bloque (petición de Miguel Ángel,
    // 2026-07-04): borrar un artista o un álbum entero es destructivo
    // y en cascada, así que siempre pasa por un diálogo explícito.
    // ---
    // Bulk-delete confirmations (requested by Miguel Ángel,
    // 2026-07-04): deleting a whole artist or album is destructive and
    // cascading, so it always goes through an explicit dialog.
    var artistPendingDelete by remember { mutableStateOf<String?>(null) }
    var albumPendingDelete by remember { mutableStateOf<Pair<String, String>?>(null) }
    var albumPendingEdit by remember { mutableStateOf<Pair<String, String>?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Muestra el resumen de mergeDuplicateFolders() en un Snackbar en
    // cuanto llega, y lo descarta de uiState para no repetirlo si la
    // pantalla se recompone.
    LaunchedEffect(uiState.mergeResultMessage) {
        uiState.mergeResultMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMergeResultMessage()
        }
    }

    // Aviso de la limpieza automática de arranque (carpetas vacías
    // borradas / pistas nuevas encontradas en disco) -- petición
    // explícita de Miguel Ángel (2026-07-04).
    // ---
    // Notice from the automatic startup cleanup (empty folders
    // deleted / new tracks found on disk) -- explicit request from
    // Miguel Ángel (2026-07-04).
    LaunchedEffect(uiState.startupMessage) {
        uiState.startupMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissStartupMessage()
        }
    }

    // H07 PARTE 1 -- aviso cuando una acción de añadir/borrar se
    // rechaza por falta de conexión.
    LaunchedEffect(uiState.syncBlockedMessage) {
        uiState.syncBlockedMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSyncBlockedMessage()
        }
    }

    // Nivel de profundidad actual de la pestaña activa -- 0 = Letras
    // (nivel raíz de esa pestaña, no hay nada que subir).
    // ---
    // Current drill depth of the active tab -- 0 = Letters (root level
    // of that tab, nothing to go back to).
    val canGoBack = when (uiState.tab) {
        LibraryTab.ALBUMS -> uiState.albumsDrill !is AlbumsDrillLevel.Letters &&
            uiState.albumsDrill !is AlbumsDrillLevel.ArtistsFlat
        LibraryTab.SINGLES -> uiState.singlesDrill !is SinglesDrillLevel.Letters &&
            uiState.singlesDrill !is SinglesDrillLevel.ArtistsFlat
    }

    // El botón atrás del sistema sube un nivel en vez de salir de la
    // pantalla mientras haya algo que subir.
    // ---
    // The system back button pops one level instead of leaving the
    // screen while there's something to pop.
    BackHandler(enabled = canGoBack) {
        when (uiState.tab) {
            LibraryTab.ALBUMS -> viewModel.backAlbumsDrill()
            LibraryTab.SINGLES -> viewModel.backSinglesDrill()
        }
    }

    val title = when (uiState.tab) {
        LibraryTab.ALBUMS -> when (val drill = uiState.albumsDrill) {
            is AlbumsDrillLevel.Letters -> "Artistas por letra"
            is AlbumsDrillLevel.ArtistsFlat -> "Todos los artistas"
            is AlbumsDrillLevel.Artists -> "Artistas · ${drill.letter}"
            is AlbumsDrillLevel.Albums -> displayArtistName(drill.artist)
            is AlbumsDrillLevel.Tracks -> drill.album
        }
        LibraryTab.SINGLES -> when (val drill = uiState.singlesDrill) {
            is SinglesDrillLevel.Letters -> "Artistas por letra"
            is SinglesDrillLevel.ArtistsFlat -> "Todos los artistas"
            is SinglesDrillLevel.Artists -> "Artistas · ${drill.letter}"
            is SinglesDrillLevel.Tracks -> displayArtistName(drill.artist)
        }
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
                        IconButton(onClick = {
                            when (uiState.tab) {
                                LibraryTab.ALBUMS -> viewModel.backAlbumsDrill()
                                LibraryTab.SINGLES -> viewModel.backSinglesDrill()
                            }
                        }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    } else {
                        Box(modifier = Modifier.padding(4.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
                            IconButton(onClick = onOpenDrawer) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menú")
                            }
                        }
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
                        val showAlbumsToggle = uiState.tab == LibraryTab.ALBUMS &&
                            (uiState.albumsDrill is AlbumsDrillLevel.Letters ||
                                uiState.albumsDrill is AlbumsDrillLevel.ArtistsFlat)
                        val showSinglesToggle = uiState.tab == LibraryTab.SINGLES &&
                            (uiState.singlesDrill is SinglesDrillLevel.Letters ||
                                uiState.singlesDrill is SinglesDrillLevel.ArtistsFlat)
                        if (showAlbumsToggle) {
                            Box(modifier = Modifier.padding(2.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
                                IconButton(onClick = viewModel::toggleAlbumsViewMode) {
                                    if (uiState.albumsViewMode == AlbumsViewMode.BY_LETTER) {
                                        Icon(
                                            Icons.Filled.FormatListBulleted,
                                            contentDescription = "Ver todos los artistas en una lista",
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.SortByAlpha,
                                            contentDescription = "Ver artistas agrupados por letra",
                                        )
                                    }
                                }
                            }
                        }
                        if (showSinglesToggle) {
                            Box(modifier = Modifier.padding(2.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
                                IconButton(onClick = viewModel::toggleSinglesViewMode) {
                                    if (uiState.singlesViewMode == SinglesViewMode.BY_LETTER) {
                                        Icon(
                                            Icons.Filled.FormatListBulleted,
                                            contentDescription = "Ver todos los artistas en una lista",
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.SortByAlpha,
                                            contentDescription = "Ver artistas agrupados por letra",
                                        )
                                    }
                                }
                            }
                            // H10 (S011, nivel 5) -- réplica total de
                            // todos los sencillos favoritos (pistas
                            // favoritas sin álbum asignado).
                            Box(modifier = Modifier.padding(2.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
                                IconButton(onClick = viewModel::shareFavoriteSinglesReplica) {
                                    Icon(
                                        Icons.Filled.Share,
                                        contentDescription = "Compartir sencillos favoritos",
                                    )
                                }
                            }
                        }
                        Box(modifier = Modifier.padding(4.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
                            IconButton(onClick = viewModel::mergeDuplicateFolders) {
                                Icon(
                                    Icons.Filled.CleaningServices,
                                    contentDescription = "Fusionar carpetas duplicadas",
                                )
                            }
                        }
                        Box(modifier = Modifier.padding(4.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
                            IconButton(onClick = viewModel::refreshLibrary) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Refrescar biblioteca",
                                )
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

            // El filtro de texto solo tiene sentido antes de haber
            // elegido ya un artista/álbum concreto -- una vez dentro
            // de un álbum, ya estás viendo justo lo que buscabas.
            // ---
            // The text filter only makes sense before an artist/album
            // has already been picked -- once inside an album, you're
            // already looking at exactly what you searched for.
            val showFilter = when (uiState.tab) {
                LibraryTab.ALBUMS -> uiState.albumsDrill is AlbumsDrillLevel.Letters ||
                    uiState.albumsDrill is AlbumsDrillLevel.ArtistsFlat ||
                    uiState.albumsDrill is AlbumsDrillLevel.Artists
                LibraryTab.SINGLES -> uiState.singlesDrill is SinglesDrillLevel.Letters ||
                    uiState.singlesDrill is SinglesDrillLevel.ArtistsFlat ||
                    uiState.singlesDrill is SinglesDrillLevel.Artists
            }
            if (showFilter) {
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
            }

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
            }

            Spacer(Modifier.height(8.dp))

            when (uiState.tab) {
                LibraryTab.ALBUMS -> AlbumsTabContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onDeleteArtist = { artistPendingDelete = it },
                    onDeleteAlbum = { artist, album -> albumPendingDelete = artist to album },
                    onEditAlbum = { artist, album -> albumPendingEdit = artist to album },
                    onDeleteTrack = { trackPendingDelete = it },
                    onEditTrack = { trackPendingEdit = it },
                    onAddToPlaylist = { tracksPendingAddToPlaylist = listOf(it) },
                    onAddAlbumToPlaylist = { tracksPendingAddToPlaylist = it },
                )
                LibraryTab.SINGLES -> SinglesTabContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onDeleteArtist = { artistPendingDelete = it },
                    onDeleteTrack = { trackPendingDelete = it },
                    onEditTrack = { trackPendingEdit = it },
                    onAddToPlaylist = { tracksPendingAddToPlaylist = listOf(it) },
                )
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
                    viewModel.deleteDownload(screenActivity, track)
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

    albumPendingDelete?.let { (artist, album) ->
        AlertDialog(
            onDismissRequest = { albumPendingDelete = null },
            title = { Text("Borrar álbum") },
            text = {
                Text(
                    "Se eliminará el álbum \"$album\" completo de " +
                        "${displayArtistName(artist)}, con todas sus " +
                        "pistas. Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAlbum(screenActivity, artist, album)
                    albumPendingDelete = null
                }) {
                    Text("Borrar álbum")
                }
            },
            dismissButton = {
                TextButton(onClick = { albumPendingDelete = null }) {
                    Text("Cancelar")
                }
            },
        )
    }

    albumPendingEdit?.let { (artist, album) ->
        EditAlbumDialog(
            artist = artist,
            album = album,
            onDismiss = { albumPendingEdit = null },
            onSave = { newArtist, newAlbum ->
                viewModel.editAlbumMetadata(artist, album, newArtist, newAlbum)
                albumPendingEdit = null
            },
        )
    }

    artistPendingDelete?.let { artist ->
        AlertDialog(
            onDismissRequest = { artistPendingDelete = null },
            title = { Text("Borrar artista") },
            text = {
                Text(
                    "Se eliminará ${displayArtistName(artist)} por " +
                        "completo: todos sus álbumes y sencillos. Esta " +
                        "acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteArtist(screenActivity, artist)
                    artistPendingDelete = null
                }) {
                    Text("Borrar artista")
                }
            },
            dismissButton = {
                TextButton(onClick = { artistPendingDelete = null }) {
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

    tracksPendingAddToPlaylist?.let { tracks ->
        AddToPlaylistDialog(
            youtubeIds = tracks.map { it.youtubeId },
            onDismiss = { tracksPendingAddToPlaylist = null },
        )
    }
}

@Composable
private fun ColumnScope.AlbumsTabContent(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    onDeleteArtist: (String) -> Unit,
    onDeleteAlbum: (String, String) -> Unit,
    onEditAlbum: (String, String) -> Unit,
    onDeleteTrack: (SearchResultTrack) -> Unit,
    onEditTrack: (SearchResultTrack) -> Unit,
    onAddToPlaylist: (SearchResultTrack) -> Unit,
    onAddAlbumToPlaylist: (List<SearchResultTrack>) -> Unit,
) {
    val activity = LocalContext.current as Activity
    when (val drill = uiState.albumsDrill) {
        is AlbumsDrillLevel.Letters -> {
            if (uiState.albumsByArtist.isNotEmpty()) {
                PlayAllRow(
                    label = "Biblioteca completa",
                    onPlayAll = viewModel::playAllAlbums,
                    onShuffle = viewModel::playAllAlbumsShuffled,
                )
            }
            LetterGrid(
                letters = uiState.albumLetters,
                emptyMessage = "Todavía no hay álbumes descargados.",
                onSelect = viewModel::selectAlbumsLetter,
            )
        }
        is AlbumsDrillLevel.Artists -> {
            val artists = uiState.albumsByArtist.keys
                .filter { sortLetterFor(it) == drill.letter }
                .sorted()
            if (artists.isNotEmpty()) {
                PlayAllRow(
                    label = "Letra ${drill.letter}",
                    onPlayAll = { viewModel.playLetterAlbums(drill.letter) },
                    onShuffle = { viewModel.playLetterAlbumsShuffled(drill.letter) },
                )
            }
            ArtistList(
                artists = artists,
                onArtistClick = viewModel::selectAlbumsArtist,
                onPlayAll = viewModel::playArtistAlbums,
                onShuffle = viewModel::playArtistAlbumsShuffled,
                onDelete = onDeleteArtist,
                onShare = { artist -> viewModel.shareArtistReplica(artist) },
            )
        }
        is AlbumsDrillLevel.ArtistsFlat -> {
            val artists = uiState.albumsByArtist.keys.sorted()
            if (artists.isEmpty()) {
                Text(
                    "Todavía no hay álbumes descargados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                PlayAllRow(
                    label = "Biblioteca completa",
                    onPlayAll = viewModel::playAllAlbums,
                    onShuffle = viewModel::playAllAlbumsShuffled,
                )
                ArtistList(
                    artists = artists,
                    onArtistClick = viewModel::selectAlbumsArtist,
                    onPlayAll = viewModel::playArtistAlbums,
                    onShuffle = viewModel::playArtistAlbumsShuffled,
                    onDelete = onDeleteArtist,
                    onShare = { artist -> viewModel.shareArtistReplica(artist) },
                )
            }
        }
        is AlbumsDrillLevel.Albums -> {
            val albums = uiState.albumsByArtist[drill.artist]?.keys?.toList()
                ?: emptyList()
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(albums, key = { "album:${drill.artist}:$it" }) { album ->
                    val albumTracks = uiState.albumsByArtist[drill.artist]?.get(album)
                        ?: emptyList()
                    AlbumHeaderRow(
                        artist = drill.artist,
                        album = album,
                        tracks = albumTracks,
                        isFavorite = (drill.artist to album) in uiState.favoriteAlbumKeys,
                        onClick = { viewModel.selectAlbumsAlbum(drill.artist, album) },
                        onPlayAlbum = { viewModel.playAlbum(drill.artist, album) },
                        onDelete = { onDeleteAlbum(drill.artist, album) },
                        onEditAlbum = { onEditAlbum(drill.artist, album) },
                        onAddToPlaylist = { onAddAlbumToPlaylist(albumTracks) },
                        onAddToQueue = { viewModel.addAlbumToQueue(drill.artist, album) },
                        onInsertNext = { viewModel.insertAlbumNext(drill.artist, album) },
                        onToggleFavorite = {
                            viewModel.toggleFavoriteAlbum(activity, drill.artist, album)
                        },
                        onRequestCoverArt = viewModel::requestCoverArtIfMissing,
                        onRetryCoverArt = viewModel::retryCoverArt,
                        onShareReplica = { viewModel.shareAlbumReplica(drill.artist, album) },
                    )
                }
            }
        }
        is AlbumsDrillLevel.Tracks -> {
            val tracks = uiState.albumsByArtist[drill.artist]?.get(drill.album)
                ?: emptyList()
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(tracks, key = { "track:${it.youtubeId}" }) { track ->
                    LibraryTrackRow(
                        track = track,
                        onPlay = { viewModel.playTrack(track) },
                        onToggleFavorite = { viewModel.toggleFavorite(activity, track) },
                        onDelete = { onDeleteTrack(track) },
                        onEdit = { onEditTrack(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onAddToQueue = { viewModel.addTrackToQueue(track) },
                        onInsertNext = { viewModel.insertTrackNext(track) },
                        onShareReplica = { viewModel.shareTrackReplica(track.youtubeId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SinglesTabContent(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    onDeleteArtist: (String) -> Unit,
    onDeleteTrack: (SearchResultTrack) -> Unit,
    onEditTrack: (SearchResultTrack) -> Unit,
    onAddToPlaylist: (SearchResultTrack) -> Unit,
) {
    val activity = LocalContext.current as Activity
    when (val drill = uiState.singlesDrill) {
        is SinglesDrillLevel.Letters -> {
            if (uiState.singlesByArtist.isNotEmpty()) {
                PlayAllRow(
                    label = "Todos los sencillos",
                    onPlayAll = viewModel::playAllSingles,
                    onShuffle = viewModel::playAllSinglesShuffled,
                )
            }
            LetterGrid(
                letters = uiState.singleLetters,
                emptyMessage = "Todavía no hay sencillos descargados.",
                onSelect = viewModel::selectSinglesLetter,
            )
        }
        is SinglesDrillLevel.Artists -> {
            val artists = uiState.singlesByArtist.keys
                .filter { sortLetterFor(it) == drill.letter }
                .sorted()
            if (artists.isNotEmpty()) {
                PlayAllRow(
                    label = "Letra ${drill.letter}",
                    onPlayAll = { viewModel.playLetterSingles(drill.letter) },
                    onShuffle = { viewModel.playLetterSinglesShuffled(drill.letter) },
                )
            }
            ArtistList(
                artists = artists,
                onArtistClick = viewModel::selectSinglesArtist,
                onPlayAll = viewModel::playArtistSingles,
                onShuffle = viewModel::playArtistSinglesShuffled,
                onDelete = onDeleteArtist,
                onShare = { artist -> viewModel.shareArtistReplica(artist) },
            )
        }
        is SinglesDrillLevel.ArtistsFlat -> {
            val artists = uiState.singlesByArtist.keys.sorted()
            if (artists.isEmpty()) {
                Text(
                    "Todavía no hay sencillos descargados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                PlayAllRow(
                    label = "Todos los sencillos",
                    onPlayAll = viewModel::playAllSingles,
                    onShuffle = viewModel::playAllSinglesShuffled,
                )
                ArtistList(
                    artists = artists,
                    onArtistClick = viewModel::selectSinglesArtist,
                    onPlayAll = viewModel::playArtistSingles,
                    onShuffle = viewModel::playArtistSinglesShuffled,
                    onDelete = onDeleteArtist,
                    onShare = { artist -> viewModel.shareArtistReplica(artist) },
                )
            }
        }
        is SinglesDrillLevel.Tracks -> {
            val tracks = uiState.singlesByArtist[drill.artist] ?: emptyList()
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(tracks, key = { "track:${it.youtubeId}" }) { track ->
                    LibraryTrackRow(
                        track = track,
                        onPlay = { viewModel.playTrack(track) },
                        onToggleFavorite = { viewModel.toggleFavorite(activity, track) },
                        onDelete = { onDeleteTrack(track) },
                        onEdit = { onEditTrack(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onAddToQueue = { viewModel.addTrackToQueue(track) },
                        onInsertNext = { viewModel.insertTrackNext(track) },
                        onShareReplica = { viewModel.shareTrackReplica(track.youtubeId) },
                    )
                }
            }
        }
    }
}

/**
 * Primera capa de Biblioteca pedida por Miguel Ángel (2026-07-04):
 * solo letras grandes y en negrita, en orden alfabético, únicamente
 * las que tienen al menos un artista -- nunca todas las letras del
 * abecedario.
 * ---
 * Biblioteca's first layer, requested by Miguel Ángel (2026-07-04):
 * only large, bold letters, in alphabetical order, only the ones with
 * at least one artist -- never the whole alphabet.
 */
/**
 * H12 (S018) -- visibilidad ampliada de `private` a `internal` para
 * que ExplorerScreen la reutilice tal cual (letras favoritas), sin
 * duplicar este composable ya genérico. Comportamiento sin cambios.
 * ---
 * H12 (S018) -- visibility widened from `private` to `internal` so
 * ExplorerScreen can reuse it as-is (favorite letters), without
 * duplicating this already-generic composable. Behavior unchanged.
 */
@Composable
internal fun ColumnScope.LetterGrid(
    letters: List<Char>,
    emptyMessage: String,
    onSelect: (Char) -> Unit,
) {
    if (letters.isEmpty()) {
        Text(
            emptyMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        return
    }

    LazyColumn(modifier = Modifier.weight(1f)) {
        items(letters, key = { it }) { letter ->
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .glassChip()
                    .clickable { onSelect(letter) }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.ArtistList(
    artists: List<String>,
    onArtistClick: (String) -> Unit,
    onPlayAll: (String) -> Unit,
    onShuffle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.weight(1f)) {
        items(artists, key = { "artist:$it" }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .glassChip()
                    .clickable { onArtistClick(artist) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayArtistName(artist),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onPlayAll(artist) }) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Reproducir artista",
                    )
                }
                IconButton(onClick = { onShuffle(artist) }) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Aleatorio")
                }
                // H10 (S011, nivel 2) -- réplica total de todas las
                // pistas descargadas de este artista.
                IconButton(onClick = { onShare(artist) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Compartir artista con réplica total")
                }
                IconButton(onClick = { onDelete(artist) }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Borrar artista",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTabButton(
    label: String,
    tab: LibraryTab,
    current: LibraryTab,
    onSelect: (LibraryTab) -> Unit,
) {
    val isSelected = current == tab
    TextButton(
        onClick = { onSelect(tab) },
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .glassChip()
            .let { base ->
                // S011 -- mismo criterio que la pestaña seleccionada
                // del menú lateral y la pista actual de la cola: el
                // resaltado de "pestaña activa" es una capa extra
                // encima del cristal, no un color de fondo sólido que
                // lo taparía.
                if (isSelected) {
                    base.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        RoundedCornerShape(GlassTokens.cornerRadius),
                    )
                } else {
                    base
                }
            },
    ) {
        Text(
            label,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * S011 -- "reproducir todo/aleatorio" fijos (petición explícita de
 * Miguel Ángel: "incluir botones de reproducción para reproducir todo
 * secuencial y todo aleatorio... para poder reproducir la biblioteca
 * entera, todos los sencillos, todo un artista, toda una letra").
 * Reutilizable en cualquier nivel -- biblioteca completa, una letra,
 * todos los sencillos.
 */
@Composable
private fun PlayAllRow(label: String, onPlayAll: () -> Unit, onShuffle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .glassChip(interactive = false)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.padding(2.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
            IconButton(onClick = onPlayAll) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir todo")
            }
        }
        Box(modifier = Modifier.padding(2.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
            IconButton(onClick = onShuffle) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Aleatorio")
            }
        }
    }
}

@Composable
private fun AlbumHeaderRow(
    artist: String,
    album: String,
    tracks: List<SearchResultTrack>,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onPlayAlbum: () -> Unit,
    onDelete: () -> Unit,
    onEditAlbum: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onInsertNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestCoverArt: (artist: String, album: String) -> Unit,
    onRetryCoverArt: (artist: String, album: String) -> Unit,
    onShareReplica: () -> Unit,
    showArtistSubtitle: Boolean = false,
) {
    LaunchedEffect(artist, album) {
        onRequestCoverArt(artist, album)
    }

    val coverArtUrl = tracks.firstNotNullOfOrNull { it.coverArtUrl }
    val fallbackThumbnailUrl = tracks.firstNotNullOfOrNull { it.thumbnailUrl }
    // Mismo enlace para todas las pistas de un álbum importado como
    // playlist -- basta con el de la primera. Petición explícita de
    // Miguel Ángel (2026-07-04).
    // ---
    // Same link for every track of an album imported as a playlist --
    // the first one is enough. Explicit request from Miguel Ángel
    // (2026-07-04).
    val shareableUrl = tracks.firstOrNull()?.shareableUrl
    val context = LocalContext.current
    var showOverflowMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .glassChip()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumCoverThumbnail(coverArtUrl, fallbackThumbnailUrl)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(album, style = MaterialTheme.typography.titleSmall)
            Text(
                if (showArtistSubtitle) {
                    "${displayArtistName(artist)} · ${tracks.size} pistas"
                } else {
                    "${tracks.size} pistas"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onPlayAlbum) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir álbum")
        }
        // Favorito de ÁLBUM (2026-07-05, petición explícita de Miguel
        // Ángel) -- visible directamente, mismo criterio que la
        // estrella de favorito por pista en LibraryTrackRow, concepto
        // nuevo y separado de esa.
        // ---
        // ALBUM favorite (2026-07-05, explicit request from Miguel
        // Ángel) -- directly visible, same criterion as the per-track
        // favorite star in LibraryTrackRow, a new and separate
        // concept from that one.
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (isFavorite) {
                    "Quitar álbum de favoritos"
                } else {
                    "Marcar álbum como favorito"
                },
                tint = if (isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        // Compartir/Añadir a lista agrupados en un overflow -- mismo
        // criterio de limpieza que LibraryTrackRow. Borrar se queda
        // siempre visible por ser una acción destructiva que conviene
        // no esconder detrás de un menú.
        // ---
        // Share/Add to playlist grouped into an overflow -- same
        // cleanliness criterion as LibraryTrackRow. Delete stays
        // always visible since it's a destructive action better not
        // hidden behind a menu.
        Box {
            IconButton(onClick = { showOverflowMenu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones del álbum")
            }
            DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { showOverflowMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Editar álbum") },
                    leadingIcon = {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                    },
                    onClick = {
                        showOverflowMenu = false
                        onEditAlbum()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Reproducir a continuación") },
                    leadingIcon = {
                        Icon(Icons.Filled.PlaylistPlay, contentDescription = null)
                    },
                    onClick = {
                        showOverflowMenu = false
                        onInsertNext()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Añadir a lista") },
                    leadingIcon = {
                        Icon(Icons.Filled.PlaylistAdd, contentDescription = null)
                    },
                    onClick = {
                        showOverflowMenu = false
                        onAddToPlaylist()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Añadir al final de la cola") },
                    leadingIcon = {
                        Icon(Icons.Filled.QueueMusic, contentDescription = null)
                    },
                    onClick = {
                        showOverflowMenu = false
                        onAddToQueue()
                    },
                )
                if (shareableUrl != null) {
                    DropdownMenuItem(
                        text = { Text("Compartir enlace") },
                        leadingIcon = {
                            Icon(Icons.Filled.Share, contentDescription = null)
                        },
                        onClick = {
                            showOverflowMenu = false
                            shareLink(context, shareableUrl)
                        },
                    )
                }
                // H10 (S011, nivel 3) -- "réplica total": código
                // miMoo+hash con favoritos/orden/enlaces originales,
                // que se añade a la biblioteca de quien lo abre --
                // distinto de "Compartir enlace" de arriba, que solo
                // comparte la URL de origen en YouTube.
                DropdownMenuItem(
                    text = { Text("Compartir con réplica total") },
                    leadingIcon = {
                        Icon(Icons.Filled.Share, contentDescription = null)
                    },
                    onClick = {
                        showOverflowMenu = false
                        onShareReplica()
                    },
                )
                // S011 -- fallo real: una URL de carátula rota
                // guardada antes de los fixes de hoy bloqueaba
                // cualquier reintento automático para siempre. Fuerza
                // uno manual, limpiando la fila y la caché de sesión.
                DropdownMenuItem(
                    text = { Text("Actualizar carátula") },
                    leadingIcon = {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    },
                    onClick = {
                        showOverflowMenu = false
                        onRetryCoverArt(artist, album)
                    },
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Borrar álbum",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
    HorizontalDivider()
}

/**
 * Small square album thumbnail. Tries the MusicBrainz+Cover Art
 * Archive URL first; if that fails to load it falls back to the
 * YouTube thumbnail already cached on the track, and finally to a
 * generic album icon if neither is available.
 * ---
 * Miniatura cuadrada pequeña de álbum. Prueba primero la URL de
 * MusicBrainz+Cover Art Archive; si falla hace fallback a la
 * miniatura de YouTube, y por último a un icono genérico.
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
    onAddToQueue: () -> Unit,
    onInsertNext: () -> Unit,
    onShareReplica: () -> Unit,
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
            .glassChip()
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
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
        // Acciones menos frecuentes agrupadas en un menú de overflow
        // (añadir a lista, editar, compartir) -- petición explícita de
        // Miguel Ángel de mantener la pantalla limpia; favoritos/
        // borrar/reproducir se quedan siempre visibles por ser las más
        // usadas.
        // ---
        // Less-frequent actions grouped into an overflow menu (add to
        // playlist, edit, share) -- explicit request from Miguel Ángel
        // to keep the screen clean; favorite/delete/play stay always
        // visible as the most-used ones.
        Box {
            IconButton(onClick = { showOverflowMenu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
            }
            DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { showOverflowMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Reproducir a continuación") },
                    leadingIcon = {
                        Icon(Icons.Filled.PlaylistPlay, contentDescription = null)
                    },
                    onClick = {
                        showOverflowMenu = false
                        onInsertNext()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Añadir a lista") },
                    leadingIcon = {
                        Icon(Icons.Filled.PlaylistAdd, contentDescription = null)
                    },
                    onClick = {
                        showOverflowMenu = false
                        onAddToPlaylist()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Añadir al final de la cola") },
                    leadingIcon = {
                        Icon(Icons.Filled.QueueMusic, contentDescription = null)
                    },
                    onClick = {
                        showOverflowMenu = false
                        onAddToQueue()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Editar metadatos") },
                    leadingIcon = {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                    },
                    onClick = {
                        showOverflowMenu = false
                        onEdit()
                    },
                )
                if (track.shareableUrl != null) {
                    DropdownMenuItem(
                        text = { Text("Compartir enlace") },
                        leadingIcon = {
                            Icon(Icons.Filled.Share, contentDescription = null)
                        },
                        onClick = {
                            showOverflowMenu = false
                            shareLink(context, track.shareableUrl!!)
                        },
                    )
                }
                // H10 (S011, niveles 4/6) -- réplica total de esta única pista.
                DropdownMenuItem(
                    text = { Text("Compartir con réplica total") },
                    leadingIcon = {
                        Icon(Icons.Filled.Share, contentDescription = null)
                    },
                    onClick = {
                        showOverflowMenu = false
                        onShareReplica()
                    },
                )
            }
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
 * text; an empty value maps to null (i.e. "Sencillos") on save.
 * ---
 * Diálogo de edición manual de metadatos (PASO 7, H03). El álbum se
 * edita como texto libre; un valor vacío se convierte en null.
 */
@Composable
private fun EditAlbumDialog(
    artist: String,
    album: String,
    onDismiss: () -> Unit,
    onSave: (artist: String, album: String) -> Unit,
) {
    var artistField by remember(artist, album) { mutableStateOf(artist) }
    var albumField by remember(artist, album) { mutableStateOf(album) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar álbum") },
        text = {
            Column {
                Text(
                    "Se aplicará a todas las pistas de este álbum a la vez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = artistField,
                    onValueChange = { artistField = it },
                    label = { Text("Artista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = albumField,
                    onValueChange = { albumField = it },
                    label = { Text("Álbum") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Si cambias artista o álbum, todos los archivos de este " +
                        "álbum se moverán a la nueva carpeta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(artistField, albumField) },
                enabled = artistField.isNotBlank() && albumField.isNotBlank(),
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

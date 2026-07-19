package com.miguelaetxio.mimoo.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzArtistSummary
import com.miguelaetxio.mimoo.ui.library.LetterGrid
import com.miguelaetxio.mimoo.ui.library.displayArtistName
import com.miguelaetxio.mimoo.ui.theme.glassChip

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
    onOpenArtist: (artistName: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val canGoBack = uiState.drill is ExplorerDrillLevel.Artists

    BackHandler(enabled = canGoBack) { viewModel.backToLetters() }

    val title = when (val drill = uiState.drill) {
        ExplorerDrillLevel.Letters -> "Explorador"
        is ExplorerDrillLevel.Artists -> "Artistas · ${drill.letter}"
    }

    Scaffold(
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
                        onArtistClick = onOpenArtist,
                        onLoadMore = viewModel::loadMoreOnline,
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
    onArtistClick: (String) -> Unit,
    onLoadMore: () -> Unit,
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
                    onClick = { onArtistClick(artist) },
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
                onClick = { onArtistClick(artist.name) },
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
    onClick: () -> Unit,
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
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

package com.miguelaetxio.mimoo.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.ui.library.LetterGrid
import com.miguelaetxio.mimoo.ui.library.displayArtistName
import com.miguelaetxio.mimoo.ui.theme.glassChip

/**
 * Explorador (H12, S018) -- "Biblioteca pero de MusicBrainz",
 * acordado con Miguel Ángel: mismo gesto de navegación por letras que
 * Biblioteca (reutiliza `LetterGrid` tal cual), pero la raíz son los
 * artistas FAVORITOS (no lo descargado -- MusicBrainz no se puede
 * listar entero, ver ExplorerViewModel), y tocar un artista navega
 * directamente a `ArtistScreen` (H12) en vez de reconstruir una capa
 * de álbumes/pistas propia. Sin "reproducir todo"/"aleatorio" --
 * decisión explícita (resolver stream de todo un catálogo favorito
 * sería caro y lento).
 * ---
 * Explorer (H12, S018) -- "Library but for MusicBrainz", agreed with
 * Miguel Ángel: same letter-navigation gesture as Library (reuses
 * `LetterGrid` as-is), but the root is FAVORITE artists (not
 * downloaded content -- MusicBrainz can't be listed in full, see
 * ExplorerViewModel), and tapping an artist navigates straight to
 * `ArtistScreen` (H12) instead of rebuilding its own album/track
 * layer. No "play all"/"shuffle" -- explicit decision (resolving the
 * stream of an entire favorite catalog would be slow and costly).
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
                        emptyMessage = "Todavía no tienes ningún artista favorito. " +
                            "Márcalo como favorito desde su página (búsqueda -> Artista) " +
                            "para que aparezca aquí.",
                        onSelect = viewModel::selectLetter,
                    )
                }
                is ExplorerDrillLevel.Artists -> {
                    ExplorerArtistList(
                        artists = uiState.artistsForLetter,
                        onArtistClick = onOpenArtist,
                    )
                }
            }
        }
    }
}

/**
 * Fila de artista simplificada -- a diferencia de ArtistList de
 * Biblioteca (que trae reproducir/aleatorio/compartir/borrar, todo
 * pensado para archivos locales), aquí solo hay nombre + navegación,
 * porque Explorador no gestiona nada local. Deliberadamente NO
 * reutiliza ArtistList (roadmap S018, punto 2): esos botones no
 * tendrían sentido y forzarlos como parámetros opcionales habría
 * ensuciado un composable que hoy es enteramente local.
 * ---
 * Simplified artist row -- unlike Library's ArtistList (which brings
 * play/shuffle/share/delete, all built for local files), here there's
 * only name + navigation, because Explorer manages nothing local.
 * Deliberately does NOT reuse ArtistList (S018 roadmap, point 2):
 * those buttons wouldn't make sense and forcing them as optional
 * params would have dirtied a composable that today is entirely
 * local.
 */
@Composable
private fun ColumnScope.ExplorerArtistList(
    artists: List<String>,
    onArtistClick: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.weight(1f)) {
        items(artists, key = { it }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .glassChip()
                    .clickable { onArtistClick(artist) }
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
                    text = displayArtistName(artist),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

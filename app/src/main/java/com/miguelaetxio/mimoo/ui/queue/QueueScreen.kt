package com.miguelaetxio.mimoo.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.playback.QueueItem
import androidx.compose.foundation.shape.RoundedCornerShape
import com.miguelaetxio.mimoo.ui.theme.glassChip
import com.miguelaetxio.mimoo.ui.theme.GlassTokens
import kotlin.math.roundToInt

/**
 * Pantalla de gestión de la cola de reproducción de SESIÓN -- petición
 * explícita de Miguel Ángel (2026-07-05): distinta de las Playlists
 * guardadas (PlaylistDetailScreen), esta cola vive solo en memoria
 * mientras la app está abierta ("una lista temporal... cuando cierras
 * la aplicación, esa lista desaparece"). Arrastrar y soltar para
 * reordenar (2026-07-05, sustituye a las flechas subir/bajar), quitar
 * una pista, saltar a una tocándola, o vaciarla entera.
 * ---
 * Session playback queue management screen -- explicit request from
 * Miguel Ángel (2026-07-05): unlike saved Playlists
 * (PlaylistDetailScreen), this queue lives only in memory while the
 * app is open ("a temporary list... once you close the app, that list
 * disappears"). Drag and drop to reorder (2026-07-05, replaces the
 * up/down arrows), remove a track, jump to one by tapping it, or clear
 * it entirely.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: QueueViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val queue by viewModel.queue.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val favoriteYoutubeIds by viewModel.favoriteYoutubeIds.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip(interactive = false)) {
                        Text(
                            "Cola de reproducción",
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
                    if (queue.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "Vaciar cola",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No hay nada en la cola de reproducción. Reproduce " +
                        "algo desde Biblioteca o Búsqueda para empezar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        } else {
            DraggableQueueList(
                queue = queue,
                playbackState = playbackState,
                favoriteYoutubeIds = favoriteYoutubeIds,
                modifier = Modifier.padding(padding),
                onClick = viewModel::playAtIndex,
                onTogglePlayPause = viewModel::togglePlayPause,
                onRemove = viewModel::removeFromQueue,
                onMove = viewModel::moveTo,
                onToggleFavorite = viewModel::toggleFavorite,
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Vaciar cola") },
            text = {
                Text(
                    "Se vaciará el resto de la cola. La canción que está " +
                        "sonando ahora mismo sigue -- esto no afecta a tus " +
                        "listas de reproducción guardadas."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearQueue()
                    showClearConfirm = false
                }) {
                    Text("Vaciar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

/**
 * LazyColumn con arrastrar y soltar manual (sin librería externa):
 * cada fila tiene un asa de arrastre (icono) que, al mantener pulsado
 * y arrastrar, desplaza la fila visualmente (offset en Y) y calcula
 * en qué posición de la lista quedaría según cuántas alturas de fila
 * se ha movido. Al soltar, si la posición calculada es distinta de la
 * original, se llama a onMove(from, to).
 *
 * Aproximación deliberada: usa la altura medida de la PRIMERA fila
 * (todas tienen la misma estructura, así que la altura real es
 * prácticamente idéntica) en vez de medir cada fila individualmente --
 * suficientemente preciso para esta lista sin añadir la complejidad de
 * un sistema de medición por índice.
 * ---
 * LazyColumn with manual drag-and-drop (no external library): each row
 * has a drag handle (icon) that, on long-press-and-drag, visually
 * offsets the row (Y offset) and computes what list position it would
 * land on based on how many row-heights it moved. On release, if the
 * computed position differs from the original, onMove(from, to) is
 * called.
 *
 * Deliberate approximation: uses the measured height of the FIRST row
 * (they all share the same structure, so the real height is
 * practically identical) instead of measuring each row individually --
 * accurate enough for this list without adding the complexity of a
 * per-index measurement system.
 */
@Composable
private fun DraggableQueueList(
    queue: List<QueueItem>,
    playbackState: com.miguelaetxio.mimoo.data.playback.PlaybackState,
    favoriteYoutubeIds: Set<String>,
    modifier: Modifier = Modifier,
    onClick: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onToggleFavorite: (QueueItem) -> Unit,
) {
    var rowHeightPx by remember { mutableStateOf(0f) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        // S010 -- crash real (IllegalArgumentException: Key "1" was
        // already used), mismo tipo de fallo ya visto y arreglado en
        // los chips de país de RadioBrowserScreen: el índice puro como
        // key es frágil en un LazyColumn con contenido dinámico
        // (arrastrar-para-reordenar, Radio añadiendo pistas nuevas
        // mientras la pantalla está abierta) -- la subcomposición
        // "beyond bounds" de Compose puede llegar a pedir el mismo
        // índice dos veces en el mismo pase. Clave compuesta
        // índice+uri, mismo criterio que el fix anterior.
        // ---
        // S010 -- real crash (IllegalArgumentException: Key "1" was
        // already used), same class of bug already seen and fixed in
        // RadioBrowserScreen's country chips: a bare index as key is
        // fragile in a LazyColumn with dynamic content (drag-to-reorder,
        // Radio adding new tracks while the screen is open) -- Compose's
        // "beyond bounds" subcomposition can end up requesting the same
        // index twice in the same pass. Composite index+uri key, same
        // approach as the earlier fix.
        itemsIndexed(queue, key = { index, item -> "$index-${item.uri}" }) { index, item ->
            val isBeingDragged = draggedIndex == index
            val offsetY = if (isBeingDragged) dragOffsetY.roundToInt() else 0

            QueueTrackRow(
                item = item,
                isCurrent = index == playbackState.queueIndex,
                isPlaying = playbackState.isPlaying,
                isFavorite = item.youtubeId != null && item.youtubeId in favoriteYoutubeIds,
                onClick = { if (!isBeingDragged) onClick(index) },
                onTogglePlayPause = onTogglePlayPause,
                onRemove = { onRemove(index) },
                onToggleFavorite = { onToggleFavorite(item) },
                modifier = Modifier
                    .zIndex(if (isBeingDragged) 1f else 0f)
                    .offset { IntOffset(0, offsetY) }
                    .onGloballyPositioned { coordinates ->
                        if (rowHeightPx == 0f) {
                            rowHeightPx = coordinates.size.height.toFloat()
                        }
                    },
                dragHandleModifier = Modifier.pointerInput(index, queue.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggedIndex = index
                            dragOffsetY = 0f
                        },
                        onDragEnd = {
                            val from = draggedIndex
                            if (from != null && rowHeightPx > 0f) {
                                val delta = (dragOffsetY / rowHeightPx).roundToInt()
                                val to = (from + delta).coerceIn(0, queue.lastIndex)
                                if (to != from) onMove(from, to)
                            }
                            draggedIndex = null
                            dragOffsetY = 0f
                        },
                        onDragCancel = {
                            draggedIndex = null
                            dragOffsetY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount.y
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun QueueTrackRow(
    item: QueueItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onRemove: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .glassChip()
            .let { base ->
                // S011 -- el resaltado de "pista actual" ya no puede
                // ser un color de fondo sólido (taparía el cristal) --
                // se queda como una capa extra, más clara, por encima
                // del degradado translúcido de glassChip().
                if (isCurrent) {
                    base.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        RoundedCornerShape(GlassTokens.cornerRadius),
                    )
                } else {
                    base
                }
            }
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = "Arrastrar para reordenar",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = dragHandleModifier.padding(horizontal = 8.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                if (isCurrent) {
                    "Sonando ahora"
                } else if (item.isLocal) {
                    "Local"
                } else {
                    "Streaming"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isCurrent) {
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                )
            }
        }
        // S010 -- favoritos desde la cola (petición explícita de
        // Miguel Ángel: "que aparezca en todos sitios"). Solo si la
        // pista tiene equivalente real en la biblioteca (youtubeId no
        // nulo) -- una emisora de radio, por ejemplo, nunca llega
        // aquí porque su cola es aparte.
        // ---
        // S010 -- favorites from the queue. Only if the track has a
        // real library equivalent (non-null youtubeId).
        if (item.youtubeId != null) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                    tint = if (isFavorite) MaterialTheme.colorScheme.error else LocalContentColor.current,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Quitar de la cola",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

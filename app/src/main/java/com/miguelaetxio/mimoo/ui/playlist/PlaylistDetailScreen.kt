package com.miguelaetxio.mimoo.ui.playlist

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.ui.theme.glassChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as Activity
    val snackbarHostState = remember { SnackbarHostState() }
    // S037 (H04) -- "Copiar en" dialog for the selected tracks.
    // ---
    // S037 (H04) -- diálogo "Copiar en" para las pistas seleccionadas.
    var showCopyDialog by remember { mutableStateOf(false) }

    // H07 PARTE 1 -- aviso cuando quitar una pista se rechaza por falta de conexión.
    LaunchedEffect(uiState.syncBlockedMessage) {
        uiState.syncBlockedMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSyncBlockedMessage()
        }
    }

    // H10 (S011, niveles 7/8) -- en cuanto se genera el archivo .txt
    // de esta lista, abre el selector de Compartir del sistema con
    // ese ARCHIVO (EXTRA_STREAM), no texto.
    val generatedShareFileUri by viewModel.generatedShareFileUri.collectAsState()
    LaunchedEffect(generatedShareFileUri) {
        generatedShareFileUri?.let { uri ->
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(android.content.Intent.createChooser(intent, null))
            viewModel.consumeGeneratedShareFileUri()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip(interactive = false)) {
                        Text(
                            "Lista de reproducción",
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
                    if (uiState.isResolving) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    } else {
                        IconButton(
                            onClick = viewModel::playAll,
                            enabled = uiState.tracks.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Reproducir todo",
                            )
                        }
                        // S062 -- petición explícita de Miguel Ángel:
                        // "no tenemos posibilidad de reproducir en
                        // aleatorio".
                        IconButton(
                            onClick = viewModel::playAllShuffled,
                            enabled = uiState.tracks.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = "Reproducir aleatorio",
                            )
                        }
                        IconButton(
                            onClick = viewModel::shareReplica,
                            enabled = uiState.tracks.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Compartir lista con réplica total",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Todavía no hay pistas en esta lista.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // S037 (H04) -- the list is editable as soon as it opens
            // (Miguel Ángel: "se activa al entrar en la lista"): text
            // filter, checkbox selection, and a bar with "Copiar en" /
            // "Eliminar" while something is selected.
            // ---
            // S037 (H04) -- la lista es editable nada más abrirse
            // (Miguel Ángel: "se activa al entrar en la lista"): filtro
            // de texto, selección por casilleros y una barra con
            // "Copiar en" / "Eliminar" mientras haya algo seleccionado.
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.filterQuery,
                onValueChange = viewModel::onFilterChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Filtrar por nombre de archivo o metadatos") },
            )
            if (uiState.selectedIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .glassChip(interactive = false)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${uiState.selectedIds.size} seleccionada(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showCopyDialog = true }) {
                        Text("Copiar en")
                    }
                    TextButton(onClick = { viewModel.removeSelected(activity) }) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (uiState.visibleTracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Ningún tema coincide con el filtro.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(
                    uiState.visibleTracks,
                    // S072 -- bug real reportado por Miguel Ángel:
                    // IllegalArgumentException "Key ... was already
                    // used" al desplazar una lista. Causa real: desde
                    // S051 se puede añadir el mismo tema dos veces a
                    // una lista ("¿añadir de todas formas?"), pero esta
                    // key solo usaba track.youtubeId, descartando el
                    // índice que itemsIndexed() ya da -- dos filas con
                    // el mismo tema acababan con la misma key, algo que
                    // Compose no permite. Se añade el índice a la key
                    // para que cada FILA sea única, aunque el tema se
                    // repita.
                    key = { index, track -> "${track.youtubeId}_$index" },
                ) { _, track ->
                    // S037 -- with the filter on, the row's position in
                    // the FULL list is what reordering must use.
                    // ---
                    // S037 -- con el filtro activo, reordenar debe usar
                    // la posición de la fila en la lista COMPLETA.
                    val fullIndex = uiState.tracks.indexOfFirst { it.youtubeId == track.youtubeId }
                    PlaylistDetailTrackRow(
                        track = track,
                        isSelected = track.youtubeId in uiState.selectedIds,
                        isFirst = fullIndex == 0,
                        isLast = fullIndex == uiState.tracks.lastIndex,
                        onToggleSelected = { viewModel.toggleSelection(track.youtubeId) },
                        onPlay = { viewModel.playTrack(track) },
                        onMoveUp = { viewModel.moveTrack(fullIndex, -1) },
                        onMoveDown = { viewModel.moveTrack(fullIndex, 1) },
                        onRemove = { viewModel.removeTrack(activity, track.youtubeId) },
                    )
                }
            }
            }
        }
    }

    if (showCopyDialog) {
        val selected = viewModel.selectedTrackInputs()
        AddToPlaylistDialog(
            tracks = selected,
            onDismiss = { showCopyDialog = false },
            titleOverride = if (selected.size > 1) "Copiar ${selected.size} pistas en" else "Copiar en",
        )
    }

    uiState.playError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPlayError,
            title = { Text("No se pudo reproducir") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissPlayError) {
                    Text("Entendido")
                }
            },
        )
    }

    uiState.resolveError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissResolveError,
            title = { Text("Reproducción parcial") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissResolveError) {
                    Text("Entendido")
                }
            },
        )
    }
}

@Composable
private fun PlaylistDetailTrackRow(
    track: SearchResultTrack,
    isSelected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleSelected: () -> Unit,
    onPlay: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .glassChip()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onToggleSelected() })
        Column {
            IconButton(onClick = onMoveUp, enabled = !isFirst) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Subir",
                )
            }
            IconButton(onClick = onMoveDown, enabled = !isLast) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Bajar",
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium)
            // S037 -- no channelTitle fallback: binding rule, channel
            // names never appear in any display.
            // ---
            // S037 -- sin respaldo en channelTitle: regla vinculante, el
            // nombre del canal no aparece en ninguna vista.
            track.artist?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (track.filePath == null) {
                Text(
                    "Sin descargar — se reproducirá en streaming",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onPlay) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Reproducir este tema",
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Quitar de la lista",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

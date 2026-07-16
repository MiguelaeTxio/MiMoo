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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as Activity
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = { Text("Lista de reproducción") },
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
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                itemsIndexed(
                    uiState.tracks,
                    key = { _, track -> track.youtubeId },
                ) { index, track ->
                    PlaylistDetailTrackRow(
                        track = track,
                        isFirst = index == 0,
                        isLast = index == uiState.tracks.lastIndex,
                        onMoveUp = { viewModel.moveTrack(index, -1) },
                        onMoveDown = { viewModel.moveTrack(index, 1) },
                        onRemove = { viewModel.removeTrack(activity, track.youtubeId) },
                    )
                }
            }
        }
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
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            Text(
                track.artist ?: track.channelTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (track.filePath == null) {
                Text(
                    "Sin descargar — se reproducirá en streaming",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

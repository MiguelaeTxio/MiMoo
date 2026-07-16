package com.miguelaetxio.mimoo.ui.albumsearch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.remote.AlbumCandidate
import com.miguelaetxio.mimoo.ui.theme.glassChip
import com.miguelaetxio.mimoo.data.remote.AlbumTrackMatch
import com.miguelaetxio.mimoo.data.remote.dto.TrackDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumSearchScreen(
    viewModel: AlbumSearchViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
    onNavigateToLibrary: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var trackPendingManualMatch by remember { mutableStateOf<AlbumTrackMatch?>(null) }
    // Confirmación de pistas descartadas (2026-07-04): antes, pulsar
    // "Importar álbum" descartaba en silencio toda pista "Sin
    // emparejar" -- bug real reportado por Miguel Ángel con álbumes de
    // Beethoven, donde el emparejamiento automático por duración falla
    // más a menudo (sinfonías con varios movimientos). Ahora, si hay
    // alguna pista sin emparejar, se exige esta confirmación explícita
    // listándolas por nombre antes de proceder.
    // ---
    // Skipped-tracks confirmation (2026-07-04): previously, tapping
    // "Importar álbum" silently discarded every "Sin emparejar" track
    // -- real bug reported by Miguel Ángel with Beethoven albums, where
    // automatic duration-based matching fails more often (multi-
    // movement symphonies). Now, if any track is unmatched, this
    // explicit confirmation is required, listing them by name before
    // proceeding.
    var showSkippedConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip()) {
                        Text("Buscar álbum", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menú")
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
                value = uiState.artist,
                onValueChange = viewModel::onArtistChange,
                label = { Text("Artista") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.album,
                onValueChange = viewModel::onAlbumChange,
                label = { Text("Álbum") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::searchAlbum,
                // PASO 6a: se acepta artista o album sueltos (caso real:
                // obras clasicas sin autor conocido de memoria).
                enabled = uiState.artist.isNotBlank() || uiState.album.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Buscar álbum")
            }

            if (uiState.isSearching) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            val selected = uiState.selectedCandidate
            if (selected == null) {
                // PASO 6d: lista de candidatos -- se muestra hasta que
                // el usuario elige uno, nunca se salta directo al
                // tracklist del primer resultado de MusicBrainz.
                if (uiState.candidates.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(uiState.candidates, key = { it.mbid }) { candidate ->
                            AlbumCandidateRow(
                                candidate = candidate,
                                onClick = { viewModel.selectCandidate(candidate) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                SelectedAlbumHeader(
                    candidate = selected,
                    onBack = viewModel::backToCandidates,
                )

                if (uiState.isLoadingTracks) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (uiState.matches.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val matchedCount = uiState.matches.count { it.matchedTrack != null }
                    Text(
                        "$matchedCount de ${uiState.matches.size} pistas emparejadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        // S010 -- crash real ("Key 1 was already
                        // used"): AlbumMatchRepository aplana todos
                        // los discos de una edición con
                        // .media.flatMap { it.tracks } y usa
                        // mbTrack.position tal cual -- ese campo de
                        // MusicBrainz es por disco, no global (la
                        // pista 1 del disco 1 y la pista 1 del disco 2
                        // comparten position=1). Clave compuesta
                        // índice+position, mismo criterio que las
                        // demás listas dinámicas de la app.
                        // ---
                        // S010 -- real crash ("Key 1 was already
                        // used"): AlbumMatchRepository flattens every
                        // disc of a release with .media.flatMap { it.
                        // tracks } and uses mbTrack.position as-is --
                        // that MusicBrainz field is per-disc, not
                        // global (disc 1 track 1 and disc 2 track 1
                        // share position=1). Composite index+position
                        // key, same approach as the app's other
                        // dynamic lists.
                        itemsIndexed(
                            uiState.matches,
                            key = { index, match -> "$index-${match.position}" },
                        ) { _, match ->
                            AlbumTrackMatchRow(
                                match = match,
                                downloadStatus = match.matchedTrack?.let {
                                    uiState.importedStatus[it.youtubeId]
                                },
                                onCorrect = { trackPendingManualMatch = match },
                            )
                            HorizontalDivider()
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val skippedCount = uiState.matches.size - matchedCount
                            if (skippedCount > 0) {
                                showSkippedConfirmation = true
                            } else {
                                viewModel.importAlbum()
                            }
                        },
                        enabled = matchedCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Importar álbum ($matchedCount pistas)")
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showSkippedConfirmation) {
        val skippedTitles = uiState.matches
            .filter { it.matchedTrack == null }
            .map { it.mbTitle }
        AlertDialog(
            onDismissRequest = { showSkippedConfirmation = false },
            title = { Text("${skippedTitles.size} pistas se quedarán fuera") },
            text = {
                Column {
                    Text(
                        "Estas pistas no tienen emparejamiento con " +
                            "YouTube y NO se importarán ni descargarán:",
                    )
                    Spacer(Modifier.height(8.dp))
                    skippedTitles.forEach { title ->
                        Text("• $title", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Puedes cancelar y corregirlas a mano (icono de " +
                            "editar en cada pista) antes de importar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSkippedConfirmation = false
                    viewModel.importAlbum()
                }) {
                    Text("Importar sin ellas")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSkippedConfirmation = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    uiState.importedCount?.let { count ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Álbum importado") },
            text = {
                Text(
                    "$count pistas añadidas y descargándose. Se " +
                        "escucharán desde Biblioteca en cuanto terminen."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissImportedDialog) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissImportedDialog()
                        onNavigateToLibrary()
                    },
                ) {
                    Text("Ver en Biblioteca")
                }
            },
        )
    }

    trackPendingManualMatch?.let { match ->
        ManualMatchDialog(
            match = match,
            candidates = uiState.manualSearchCandidates,
            isSearching = uiState.isSearchingManualCandidates,
            onSearch = viewModel::searchManualCandidates,
            onPick = { candidate ->
                viewModel.applyManualMatch(match, candidate)
                trackPendingManualMatch = null
            },
            onDismiss = {
                viewModel.clearManualCandidates()
                trackPendingManualMatch = null
            },
        )
    }
}

@Composable
private fun AlbumCandidateRow(
    candidate: AlbumCandidate,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CandidateCoverThumbnail(candidate.coverArtUrl, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(candidate.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = listOfNotNull(candidate.artist, candidate.year)
                    .joinToString(" · ")
                    .ifBlank { "Artista desconocido" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectedAlbumHeader(
    candidate: AlbumCandidate,
    onBack: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Cambiar álbum")
        }
        Spacer(Modifier.width(4.dp))
        CandidateCoverThumbnail(candidate.coverArtUrl, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(candidate.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = listOfNotNull(candidate.artist, candidate.year)
                    .joinToString(" · ")
                    .ifBlank { "Artista desconocido" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Small square cover thumbnail for a MusicBrainz+Cover Art Archive
 * URL, with a generic album icon fallback on 404/load failure — same
 * fallback pattern as LibraryScreen's AlbumCoverThumbnail (PASO 6,
 * H03), simplified here since there is no YouTube thumbnail to fall
 * back to before a track has even been matched.
 * ---
 * Miniatura cuadrada de carátula para una URL de MusicBrainz+Cover
 * Art Archive, con fallback a un icono genérico de álbum si falla la
 * carga (404) — mismo patrón de fallback que AlbumCoverThumbnail de
 * LibraryScreen (PASO 6, H03), simplificado aquí porque no hay
 * miniatura de YouTube a la que recurrir antes de haber emparejado
 * siquiera una pista.
 */
@Composable
private fun CandidateCoverThumbnail(coverArtUrl: String, size: androidx.compose.ui.unit.Dp) {
    val shape = RoundedCornerShape(4.dp)
    SubcomposeAsyncImage(
        model = coverArtUrl,
        contentDescription = "Carátula del álbum",
        modifier = Modifier.size(size).clip(shape),
        error = {
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
                    modifier = Modifier.size(size / 2),
                )
            }
        },
    )
}

@Composable
private fun AlbumTrackMatchRow(
    match: AlbumTrackMatch,
    downloadStatus: DownloadStatus?,
    onCorrect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when {
                match.matchError != null -> Icons.Filled.ErrorOutline
                match.matchedTrack != null && match.isAutoMatched -> Icons.Filled.CheckCircle
                else -> Icons.Filled.WarningAmber
            },
            contentDescription = null,
            tint = when {
                match.matchError != null -> MaterialTheme.colorScheme.error
                match.matchedTrack != null && match.isAutoMatched -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            },
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("${match.position}. ${match.mbTitle}")
            Text(
                text = when {
                    match.matchError != null -> "Error: ${match.matchError}"
                    match.matchedTrack == null -> "Sin emparejar"
                    match.isAutoMatched -> "Emparejado: ${match.matchedTrack.title}"
                    else -> "Revisar: ${match.matchedTrack.title}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // PASO 6b Parte 2: feedback en vivo de la descarga automatica
        // tras importar, en la misma fila -- sin esto solo se ve el
        // emparejamiento, nunca si la descarga real avanza o falla.
        if (downloadStatus != null) {
            Spacer(Modifier.width(4.dp))
            ImportedDownloadStatusIcon(downloadStatus)
        }
        IconButton(onClick = onCorrect) {
            Icon(Icons.Filled.Edit, contentDescription = "Corregir manualmente")
        }
    }
}

/**
 * Read-only status indicator for a track already imported and
 * auto-enqueued for download (PASO 6b Parte 2) — not a retry button,
 * DownloadWorker already handles its own lifecycle.
 * ---
 * Indicador de estado de solo lectura para una pista ya importada y
 * encolada automáticamente para descarga (PASO 6b Parte 2) — no es un
 * botón de reintento, DownloadWorker ya gestiona su propio ciclo de
 * vida.
 */
@Composable
private fun ImportedDownloadStatusIcon(status: DownloadStatus) {
    when (status) {
        DownloadStatus.PENDING, DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            }
        }
        DownloadStatus.DONE -> {
            Icon(
                Icons.Filled.CloudDownload,
                contentDescription = "Descargada",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DownloadStatus.ERROR -> {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = "Error al descargar",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ManualMatchDialog(
    match: AlbumTrackMatch,
    candidates: List<TrackDto>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onPick: (TrackDto) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember(match.position) { mutableStateOf(match.mbTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Corregir: ${match.mbTitle}") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar en YouTube") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { onSearch(query) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Buscar",
                            )
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                if (isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(candidates, key = { it.youtubeId }) { candidate ->
                        TextButton(
                            onClick = { onPick(candidate) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(candidate.title)
                                Text(
                                    candidate.channelTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

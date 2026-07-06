package com.miguelaetxio.mimoo.ui.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.ui.library.displayArtistName

/**
 * "Descargas" — pantalla dedicada a lo que está pasando ahora mismo
 * con las descargas: en curso (con % real, PASO 6c H05), en cola, y
 * un vistazo corto a lo recién completado. Antes de esta pantalla no
 * había ningún sitio persistente donde ver eso — ver
 * DownloadsViewModel para el detalle del bug que motivó esto.
 * ---
 * "Descargas" — dedicated screen for what's happening with downloads
 * right now: active (with real %, PASO 6c H05), queued, and a short
 * glance at what just finished. Before this screen there was no
 * persistent place to see that — see DownloadsViewModel for the bug
 * that motivated it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val isEmpty = uiState.downloading.isEmpty() &&
        uiState.queued.isEmpty() &&
        uiState.recentlyCompleted.isEmpty() &&
        uiState.failed.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Descargas") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menú")
                    }
                },
            )
        },
    ) { padding ->
        if (isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No hay ninguna descarga en curso ni reciente.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (uiState.downloading.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Descargando (${uiState.downloading.size})",
                    )
                }
                items(uiState.downloading, key = { "d_${it.youtubeId}" }) { track ->
                    DownloadingRow(track)
                }
            }

            if (uiState.queued.isNotEmpty()) {
                item {
                    SectionHeader("En cola (${uiState.queued.size})")
                }
                items(uiState.queued, key = { "q_${it.youtubeId}" }) { track ->
                    QueuedRow(track)
                }
            }

            if (uiState.failed.isNotEmpty()) {
                item {
                    // Cabecera con botón "Reintentar todas" -- petición
                    // explícita de Miguel Ángel (2026-07-06): "es un
                    // coñazo estar reintentando una por una", con 36 de
                    // 100 títulos fallados en una sola descarga.
                    // ---
                    // Header with a "Retry all" button -- explicit
                    // request from Miguel Ángel (2026-07-06): "it's a
                    // pain having to retry one by one", with 36 out of
                    // 100 titles failed in a single download.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader(
                            "Con error (${uiState.failed.size})",
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::retryAll) {
                            Text("Reintentar todas")
                        }
                    }
                }
                items(uiState.failed, key = { "e_${it.youtubeId}" }) { track ->
                    FailedRow(
                        track,
                        onRetry = { viewModel.retry(track) },
                        onDelete = { viewModel.deleteFailed(track) },
                    )
                }
            }

            if (uiState.recentlyCompleted.isNotEmpty()) {
                item {
                    SectionHeader("Completadas recientemente")
                }
                items(
                    uiState.recentlyCompleted,
                    key = { "c_${it.youtubeId}" },
                ) { track ->
                    CompletedRow(track)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Fila con barra de progreso determinada (% real, ver
 * DownloadWorker/downloader.py progress_hooks). El % se limita a 99
 * mientras yt-dlp descarga el audio en crudo -- el postproceso a Opus
 * y la copia SAF que vienen despues no reportan progreso propio, así
 * que la barra puede quedarse en 99% unos segundos justo antes de
 * pasar a Completadas.
 */
@Composable
private fun DownloadingRow(track: SearchResultTrack) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        TrackTitleLine(track)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { track.downloadProgress / 100f },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${track.downloadProgress}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Fila "en cola": sin % (todavía no hay progress_hooks que reportar),
 * barra vacía y un icono de reloj de arena para dejar claro que está
 * esperando turno, no parada por error.
 */
@Composable
private fun QueuedRow(track: SearchResultTrack) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.HourglassEmpty,
            contentDescription = "En cola",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            TrackTitleLine(track)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Fila "con error": icono rojo + botón de reintentar (mismo mecanismo
 * que el botón de retry en SearchScreen.DownloadButton para ERROR) +
 * botón de borrar definitivo -- para las que fallan siempre, sin
 * importar cuánto se espere o se reintente (petición explícita de
 * Miguel Ángel, 2026-07-06).
 */
@Composable
private fun FailedRow(
    track: SearchResultTrack,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = "Error al descargar",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        TrackTitleLine(track, modifier = Modifier.weight(1f))
        IconButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = "Reintentar")
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Borrar definitivamente",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CompletedRow(track: SearchResultTrack) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "Descargada",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        TrackTitleLine(track, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TrackTitleLine(
    track: SearchResultTrack,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(track.title, style = MaterialTheme.typography.bodyMedium)
        Text(
            listOfNotNull(
                displayArtistName(track.artist ?: track.channelTitle),
                track.album,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

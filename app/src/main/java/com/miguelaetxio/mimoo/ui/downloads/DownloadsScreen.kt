package com.miguelaetxio.mimoo.ui.downloads

import android.app.Activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.remote.dto.ExternalLinkTrack
import com.miguelaetxio.mimoo.ui.library.displayArtistName
import com.miguelaetxio.mimoo.ui.theme.glassChip

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
    val alternativeSearchState by viewModel.alternativeSearchState.collectAsState()
    val activity = LocalContext.current as Activity
    val isEmpty = uiState.downloading.isEmpty() &&
        uiState.queued.isEmpty() &&
        uiState.recentlyCompleted.isEmpty() &&
        uiState.failed.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip(interactive = false)) {
                        Text("Descargas", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(4.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menú")
                        }
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
                        TextButton(
                            onClick = viewModel::retryAll,
                            modifier = Modifier.glassChip(),
                        ) {
                            Text("Reintentar todas")
                        }
                    }
                }
                items(uiState.failed, key = { "e_${it.youtubeId}" }) { track ->
                    FailedRow(
                        track,
                        onRetry = { viewModel.retry(track) },
                        onDelete = { viewModel.deleteFailed(track) },
                        onFindAlternative = { viewModel.openAlternativeSearch(track) },
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

    // Fix real (2026-07-24, petición explícita de Miguel Ángel) --
    // diálogo "Buscar alternativa" para una pista con ERROR
    // permanente. Ver AlternativeSearchUiState/TrackAlternativeRepository.
    if (alternativeSearchState.targetTrack != null) {
        AlternativeSearchDialog(
            state = alternativeSearchState,
            onQueryChange = viewModel::updateAlternativeQuery,
            onSearch = viewModel::searchAlternatives,
            onChoose = { alternative -> viewModel.chooseAlternative(activity, alternative) },
            onDismiss = viewModel::dismissAlternativeSearch,
        )
    }
}

/** S011 -- cabeceras de sección son puramente informativas, sin ninguna acción -- cristal decorativo. */
@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp).glassChip(interactive = false)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/**
 * Fila con barra de progreso determinada (% real, ver
 * DownloadWorker/downloader.py progress_hooks). El % se limita a 99
 * mientras yt-dlp descarga el audio en crudo -- el postproceso a Opus
 * y la copia SAF que vienen despues no reportan progreso propio, así
 * que la barra puede quedarse en 99% unos segundos justo antes de
 * pasar a Completadas.
 *
 * S011 -- cristal decorativo (la fila en sí no lleva ninguna acción,
 * solo informa del progreso).
 */
@Composable
private fun DownloadingRow(track: SearchResultTrack) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .glassChip(interactive = false)
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
 *
 * S011 -- cristal decorativo, mismo criterio que DownloadingRow.
 */
@Composable
private fun QueuedRow(track: SearchResultTrack) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .glassChip(interactive = false)
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
 *
 * S011 -- la fila en sí es decorativa (no lleva acción propia), pero
 * los dos botones que contiene sí son clicables -- cada uno con su
 * propia chapita interactiva, distinta de la fila que los envuelve.
 */
@Composable
private fun FailedRow(
    track: SearchResultTrack,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onFindAlternative: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .glassChip(interactive = false)
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
        Box(modifier = Modifier.padding(2.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reintentar")
            }
        }
        // Fix real (2026-07-24, petición explícita de Miguel Ángel):
        // cuando reintentar el MISMO vídeo nunca va a funcionar (límite
        // real de yt-dlp, no del código de MiMoo -- ver
        // TrackAlternativeRepository), buscar otro vídeo distinto para
        // la misma canción es la única salida real que no rompe el LP.
        Box(modifier = Modifier.padding(2.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
            IconButton(onClick = onFindAlternative) {
                Icon(Icons.Filled.FindReplace, contentDescription = "Buscar alternativa")
            }
        }
        Box(modifier = Modifier.padding(2.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Borrar definitivamente",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** S011 -- cristal decorativo, mismo criterio que las otras filas de estado. */
@Composable
private fun CompletedRow(track: SearchResultTrack) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .glassChip(interactive = false)
            .padding(horizontal = 8.dp, vertical = 6.dp),
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

/**
 * Fix real (2026-07-24, petición explícita de Miguel Ángel, motivada
 * por "River Euphrates" de Pixies): diálogo para buscar un vídeo de
 * YouTube alternativo cuando el original lleva fallando siempre --
 * ver TrackAlternativeRepository para el porqué (límite real de
 * yt-dlp, no de MiMoo) y para el diseño de la sustitución.
 *
 * El campo de texto empieza con el título EXACTO de la pista fallida
 * (state.query) y es editable -- petición textual de Miguel Ángel:
 * "si el nombre del archivo es Canción de cuna Remaster 2007, que el
 * usuario pueda borrar Remaster 2007" antes de lanzar la búsqueda.
 * Elegir un resultado sustituye la fuente y encola la descarga de
 * inmediato (ver DownloadsViewModel.chooseAlternative()) -- el álbum,
 * artista y posición de disco de la fila original NUNCA cambian, así
 * que la pista sustituida mantiene su lugar exacto en el LP.
 * ---
 * Real fix (2026-07-24, explicit request from Miguel Ángel, prompted
 * by Pixies' "River Euphrates"): dialog to search for an alternative
 * YouTube video when the original keeps failing -- see
 * TrackAlternativeRepository for why (a real yt-dlp limitation, not
 * MiMoo's) and for the replacement's design.
 *
 * The text field starts with the failed track's EXACT title
 * (state.query) and is editable -- Miguel Ángel's literal request:
 * "if the filename is Canción de cuna Remaster 2007, let the user
 * delete Remaster 2007" before running the search. Choosing a result
 * replaces the source and enqueues the download immediately (see
 * DownloadsViewModel.chooseAlternative()) -- the original row's album,
 * artist and disc position NEVER change, so the replaced track keeps
 * its exact place on the LP.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlternativeSearchDialog(
    state: AlternativeSearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onChoose: (ExternalLinkTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    val target = state.targetTrack ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar alternativa") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "La descarga de \"${target.title}\" lleva fallando siempre. " +
                        "Edita el texto y busca otro vídeo para la misma canción -- " +
                        "se guardará en el mismo álbum y en su mismo lugar del disco.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Buscar") },
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onSearch, modifier = Modifier.glassChip()) {
                        Text("Buscar")
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (state.isSearching) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (state.errorMessage != null) {
                    Text(
                        state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (state.results.isEmpty()) {
                    Text(
                        "Sin resultados todavía -- edita el texto y pulsa \"Buscar\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    ) {
                        items(state.results, key = { it.youtubeId }) { result ->
                            AlternativeResultRow(
                                result = result,
                                onClick = { onChoose(result) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

/** Fila de un resultado de búsqueda dentro de AlternativeSearchDialog -- tocar elige ese vídeo como sustituto. */
@Composable
private fun AlternativeResultRow(
    result: ExternalLinkTrack,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .glassChip()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(result.title, style = MaterialTheme.typography.bodyMedium)
        Text(
            "${result.channelTitle} · ${formatAlternativeDuration(result.durationSeconds)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Mismo patrón que formatDuration() de ImportLinkScreen.kt (privado allí, no reutilizable desde aquí). */
private fun formatAlternativeDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}


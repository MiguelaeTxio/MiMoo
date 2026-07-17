package com.miguelaetxio.mimoo.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage

/**
 * S010 -- "reproductor expandido", rediseño completo pedido por
 * Miguel Ángel tras un primer intento fallido (carátula pequeña
 * mezclada con los controles, no era lo pedido). Orden EXACTO,
 * confirmado explícitamente:
 *   1. Controles arriba del todo (aleatorio/favorito/anterior/
 *      play-pausa/siguiente/repetir).
 *   2. Barra de progreso + tiempos, debajo de los controles.
 *   3. Carátula cuadrada GRANDE a la izquierda, metadatos (título,
 *      artista, streaming/local) a su derecha -- la fila más abajo
 *      del todo.
 * Tamaño de la carátula: lado = ancho de pantalla ÷ 2 (fórmula
 * explícita de Miguel Ángel), no un valor fijo en dp -- así escala
 * igual de bien en un móvil pequeño que en una tablet.
 *
 * onOpenQueue: tocar el bloque de metadatos abre la cola de sesión
 * (QueueScreen) -- mismo patrón ya existente, ahora sobre el bloque de
 * metadatos en vez de sobre el título suelto.
 * ---
 * S010 -- "expanded player", full redesign requested by Miguel Ángel
 * after a first failed attempt. EXACT order, explicitly confirmed:
 *   1. Controls at the very top.
 *   2. Progress bar + times, below the controls.
 *   3. Big square cover art on the left, metadata on its right -- the
 *      bottom-most row.
 * Cover art size: side = screen width ÷ 2 (Miguel Ángel's explicit
 * formula), not a fixed dp value -- scales the same on a small phone
 * and a tablet.
 *
 * S011 -- fallo real reportado por Miguel Ángel: al ser fijo y grande
 * (petición explícita suya en S010, ver arriba), en pantallas con
 * poco contenido propio -- Ajustes es el caso real que lo destapó --
 * el reproductor expandido tapaba opciones enteras de la pantalla sin
 * dejar ninguna forma de acceder a ellas mientras algo estuviera
 * sonando. *"habría que hacer el exoplayer que sea ocultable"* --
 * ahora es colapsable: un botón (flecha) alterna entre el diseño
 * expandido de arriba (sin tocar su orden ni tamaños, tal cual se
 * pidió) y una mini-barra de una sola fila (~56dp) con carátula
 * pequeña, título/artista y play/pausa. Estado en memoria del propio
 * Composable (`remember`) -- no sobrevive a matar la app, pero sí a
 * navegar entre pantallas, porque este Composable nunca sale de
 * composición al cambiar de pantalla (vive en el nivel de
 * MainActivity, fuera del NavHost).
 */
@Composable
fun PlayerBar(
    viewModel: PlayerBarViewModel = hiltViewModel(),
    onOpenQueue: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val isCurrentFavorite by viewModel.isCurrentFavorite.collectAsState()
    val coverArtUrl by viewModel.coverArtUrl.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val title = state.currentTitle ?: return
    val artSize = LocalConfiguration.current.screenWidthDp.dp / 2
    var isExpanded by remember { mutableStateOf(true) }

    Surface(tonalElevation = 4.dp) {
        if (!isExpanded) {
            PlayerBarCollapsed(
                title = title,
                artist = state.currentArtist,
                coverArtUrl = coverArtUrl,
                isPlaying = state.isPlaying,
                onTogglePlayPause = viewModel::togglePlayPause,
                onExpand = { isExpanded = true },
            )
            return@Surface
        }
        Column(modifier = Modifier.fillMaxWidth()) {

            // 1 -- Controles, arriba del todo.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (state.queueSize > 1) {
                    IconButton(onClick = viewModel::toggleShuffle) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = if (state.shuffleModeEnabled) {
                                "Desactivar orden aleatorio"
                            } else {
                                "Activar orden aleatorio"
                            },
                            tint = if (state.shuffleModeEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                }

                if (state.currentYoutubeId != null) {
                    IconButton(onClick = viewModel::toggleCurrentFavorite) {
                        Icon(
                            if (isCurrentFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isCurrentFavorite) {
                                "Quitar de favoritos"
                            } else {
                                "Añadir a favoritos"
                            },
                            tint = if (isCurrentFavorite) {
                                MaterialTheme.colorScheme.error
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                }

                // H08 -- "anterior" no depende de que haya más de una
                // pista en cola, ver PlayerManager.playPrevious().
                IconButton(onClick = viewModel::playPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior")
                }

                IconButton(onClick = viewModel::togglePlayPause) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                    )
                }

                if (state.queueSize > 1) {
                    IconButton(
                        onClick = viewModel::playNext,
                        enabled = state.repeatModeEnabled ||
                            state.queueIndex < state.queueSize - 1,
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente")
                    }
                }

                if (state.queueSize > 1) {
                    IconButton(onClick = viewModel::toggleRepeat) {
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = if (state.repeatModeEnabled) {
                                "Desactivar reproducción cíclica"
                            } else {
                                "Activar reproducción cíclica"
                            },
                            tint = if (state.repeatModeEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                }

                // S011 -- botón de descarga (petición explícita de
                // Miguel Ángel, junto con el de la notificación -- ver
                // MiMooPlaybackService para el límite real de huecos
                // de la notificación del sistema, que este reproductor
                // propio no sufre). Solo visible si la pista actual
                // tiene equivalente real en la biblioteca y todavía no
                // está descargada -- mismo criterio que el favorito de
                // arriba (state.currentYoutubeId != null).
                if (state.currentYoutubeId != null &&
                    downloadStatus != com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.DONE &&
                    downloadStatus != com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.QUEUED &&
                    downloadStatus != com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.DOWNLOADING
                ) {
                    IconButton(onClick = viewModel::downloadCurrentTrack) {
                        Icon(Icons.Filled.Download, contentDescription = "Descargar")
                    }
                }

                // S011 -- botón para colapsar el reproductor a la
                // mini-barra, ver comentario de cabecera.
                IconButton(onClick = { isExpanded = false }) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = "Contraer reproductor")
                }
            }

            // 2 -- Barra de progreso + tiempos, debajo de los controles.
            if (state.durationMs > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatPlaybackTime(positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = positionMs.coerceIn(0L, state.durationMs).toFloat(),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..state.durationMs.toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Text(
                        text = "-" + formatPlaybackTime(state.durationMs - positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // 3 -- Carátula grande (lado = ancho de pantalla / 2) a la
            // izquierda, metadatos a su derecha. Fila más abajo del todo.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable(onClick = onOpenQueue),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(artSize)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    if (coverArtUrl != null) {
                        SubcomposeAsyncImage(
                            model = coverArtUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            error = { PlayerBarArtPlaceholder() },
                        )
                    } else {
                        PlayerBarArtPlaceholder()
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!state.currentArtist.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.currentArtist!!,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // H09 -- icono + etiqueta corta en vez de una frase
                    // larga, ver historial de esta misma fila más abajo.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (state.isLocal) Icons.Filled.Download else Icons.Filled.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (state.isLocal) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (state.isLocal) "Local" else "Streaming",
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.isLocal) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                        )
                    }
                }
            }

            // S010 -- pequeña franja vacía del color de fondo debajo
            // de la carátula, para que no quede pegada al filo
            // inferior de la pantalla. Petición explícita: la mitad de
            // alta que la franja de controles de arriba.
            // ---
            // S010 -- small empty strip in the background color below
            // the cover art, so it doesn't sit flush against the
            // bottom edge of the screen. Explicit request: half as
            // tall as the controls strip above.
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** S011 -- mini-barra cuando el reproductor está contraído, ver comentario de cabecera de PlayerBar(). Una sola fila, ~56dp. */
@Composable
private fun PlayerBarCollapsed(
    title: String,
    artist: String?,
    coverArtUrl: String?,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onExpand: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            if (coverArtUrl != null) {
                SubcomposeAsyncImage(
                    model = coverArtUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    error = { PlayerBarArtPlaceholder() },
                )
            } else {
                PlayerBarArtPlaceholder()
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!artist.isNullOrBlank()) {
                Text(
                    text = artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onTogglePlayPause) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
            )
        }
        IconButton(onClick = onExpand) {
            Icon(Icons.Filled.ExpandLess, contentDescription = "Expandir reproductor")
        }
    }
}

/** S010 -- icono genérico cuando no hay carátula real (pistas transitorias de Radio sin favoritar, emisoras...). */
@Composable
private fun PlayerBarArtPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "m:ss", igual que el resto de la app (ver formatDuration en Búsqueda/Biblioteca). */
private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

package com.miguelaetxio.mimoo.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * onOpenQueue: tocar el título/artista abre la pantalla de gestión de
 * la cola de sesión (QueueScreen) -- petición explícita de Miguel
 * Ángel (2026-07-05), patrón habitual de mini-reproductor -> pantalla
 * de cola en cualquier app de música.
 * ---
 * onOpenQueue: tapping the title/artist opens the session queue
 * management screen (QueueScreen) -- explicit request from Miguel
 * Ángel (2026-07-05), the usual mini-player -> queue screen pattern in
 * any music app.
 */
@Composable
fun PlayerBar(
    viewModel: PlayerBarViewModel = hiltViewModel(),
    onOpenQueue: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val title = state.currentTitle ?: return

    Surface(tonalElevation = 4.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenQueue),
                ) {
                    Text(
                        text = title,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = if (state.isLocal) {
                            "Reproduciendo en local"
                        } else {
                            "Reproduciendo en streaming"
                        },
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.isLocal) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                    )
                }

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

                // H08 -- "anterior" ya no depende de que haya más de
                // una pista en cola. Petición explícita tras probar
                // la Radio con un único tema: debe poder reiniciarse
                // desde el principio aunque no haya pista anterior de
                // verdad (ver PlayerManager.playPrevious()).
                // ---
                // H08 -- "previous" no longer depends on the queue
                // having more than one track. Explicit request after
                // testing Radio with a single track: it should be
                // possible to restart from the beginning even with no
                // real previous track (see
                // PlayerManager.playPrevious()).
                IconButton(onClick = viewModel::playPrevious) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Anterior",
                    )
                }

                IconButton(onClick = viewModel::togglePlayPause) {
                    Icon(
                        imageVector = if (state.isPlaying) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = if (state.isPlaying) {
                            "Pausar"
                        } else {
                            "Reproducir"
                        },
                    )
                }

                if (state.queueSize > 1) {
                    IconButton(
                        onClick = viewModel::playNext,
                        enabled = state.repeatModeEnabled ||
                            state.queueIndex < state.queueSize - 1,
                    ) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "Siguiente",
                        )
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
            }

            // H08 -- tiempo transcurrido/restante + barra de progreso
            // arrastrable. Petición explícita: "deberíamos de
            // presentar el tiempo de reproducción, el total y el que
            // queda, y una barra de progreso". Solo se muestra con
            // duración conocida (duration > 0) -- un stream recién
            // arrancado puede no tener duración resuelta todavía.
            // ---
            // H08 -- elapsed/remaining time + draggable progress bar.
            // Explicit request: "we should show the playback time, the
            // total, and what's left, and a progress bar". Only shown
            // once the duration is known (duration > 0) -- a
            // just-started stream might not have its duration resolved
            // yet.
            if (state.durationMs > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 0.dp),
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
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** "m:ss", igual que el resto de la app (ver formatDuration en Búsqueda/Biblioteca). */
private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

package com.miguelaetxio.mimoo.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
    val title = state.currentTitle ?: return

    Surface(tonalElevation = 4.dp) {
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
                IconButton(
                    onClick = viewModel::playPrevious,
                    enabled = state.queueIndex > 0,
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Anterior",
                    )
                }
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
                    enabled = state.queueIndex < state.queueSize - 1,
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Siguiente",
                    )
                }
            }
        }
    }
}

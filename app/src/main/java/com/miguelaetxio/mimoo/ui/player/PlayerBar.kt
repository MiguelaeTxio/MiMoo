package com.miguelaetxio.mimoo.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PlayerBar(
    viewModel: PlayerBarViewModel = hiltViewModel(),
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
            Column(modifier = Modifier.weight(1f)) {
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
        }
    }
}

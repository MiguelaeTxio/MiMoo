package com.miguelaetxio.mimoo.ui.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.remote.dto.TrackDto

/**
 * Screen for importing a YouTube playlist.
 * ---
 * Pantalla para importar una playlist de YouTube.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportScreen(
    onBack: () -> Unit,
    viewModel: PlaylistImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var url by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importar playlist YT") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL de playlist de YouTube") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { viewModel.import(url) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Importar")
            }
            Spacer(modifier = Modifier.height(16.dp))

            when (val s = state) {
                is ImportState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ImportState.Error -> {
                    Text(
                        text = s.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                is ImportState.Success -> {
                    ImportTrackList(
                        tracks = s.tracks,
                        onSave = { selected -> viewModel.saveSelected(selected) },
                    )
                }
                is ImportState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun ImportTrackList(
    tracks: List<TrackDto>,
    onSave: (List<TrackDto>) -> Unit,
) {
    val selected = remember { mutableStateOf(tracks.toSet()) }

    Column {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(tracks) { track ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = track in selected.value,
                        onCheckedChange = { checked ->
                            selected.value = if (checked)
                                selected.value + track
                            else
                                selected.value - track
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = track.channelTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onSave(selected.value.toList()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Guardar selección (${selected.value.size})")
        }
    }
}

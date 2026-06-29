package com.miguelaetxio.mimoo.ui.track

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.Track

/**
 * Screen for creating or editing a track.
 * ---
 * Pantalla para crear o editar un track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackFormScreen(
    onBack: () -> Unit,
    viewModel: TrackViewModel = hiltViewModel(),
) {
    val editTrack by viewModel.editTrack.collectAsState()

    var youtubeInput by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf(false) }

    LaunchedEffect(editTrack) {
        editTrack?.let {
            youtubeInput = it.youtubeId
            title = it.title
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (editTrack != null) "Editar track" else "Nuevo track")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (editTrack != null) {
                        IconButton(onClick = {
                            editTrack?.let { viewModel.delete(it) }
                            onBack()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
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
                value = youtubeInput,
                onValueChange = {
                    youtubeInput = it
                    inputError = false
                },
                label = { Text("YouTube ID o URL") },
                isError = inputError,
                supportingText = {
                    if (inputError) {
                        Text("ID de 11 caracteres o URL de YouTube válida")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val youtubeId = extractYoutubeId(youtubeInput)
                    if (youtubeId == null) {
                        inputError = true
                        return@Button
                    }
                    val track = Track(
                        id = editTrack?.id ?: 0L,
                        youtubeId = youtubeId,
                        title = title.ifBlank { youtubeId },
                        artistId = editTrack?.artistId ?: -1L,
                        albumId = editTrack?.albumId,
                        durationSeconds = editTrack?.durationSeconds,
                        downloadStatus = editTrack?.downloadStatus
                            ?: DownloadStatus.PENDING,
                        filePath = editTrack?.filePath,
                    )
                    viewModel.save(track)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar")
            }
        }
    }
}

/**
 * Extracts an 11-char YouTube video ID from a raw ID or full URL.
 * Returns null if neither format matches.
 * ---
 * Extrae un video ID de 11 caracteres de un ID crudo o URL completa.
 * Devuelve null si ningún formato coincide.
 */
private fun extractYoutubeId(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.length == 11 &&
        trimmed.matches(Regex("[A-Za-z0-9_-]+"))
    ) return trimmed
    val urlRegex = Regex("""(?:v=|youtu\.be/)([A-Za-z0-9_-]{11})""")
    return urlRegex.find(trimmed)?.groupValues?.get(1)
}

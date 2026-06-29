package com.miguelaetxio.mimoo.ui.artist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistFormScreen(
    onBack: () -> Unit,
    vm: ArtistViewModel = hiltViewModel(),
) {
    val artist by vm.current.collectAsState()

    var name    by remember(artist) { mutableStateOf(artist?.name ?: "") }
    var bio     by remember(artist) { mutableStateOf(artist?.bio ?: "") }
    var genres  by remember(artist) { mutableStateOf(artist?.genres ?: "") }
    var coverUrl by remember(artist) { mutableStateOf(artist?.coverUrl ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (artist == null) "Nuevo artista" else "Editar artista") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nombre *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = genres, onValueChange = { genres = it },
                label = { Text("Géneros (separados por coma)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = coverUrl, onValueChange = { coverUrl = it },
                label = { Text("URL de imagen") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = bio, onValueChange = { bio = it },
                label = { Text("Biografía") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.save(name, bio, genres, coverUrl); onBack() },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar") }
        }
    }
}

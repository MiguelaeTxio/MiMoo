package com.miguelaetxio.mimoo.ui.album

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
fun AlbumFormScreen(
    onBack: () -> Unit,
    vm: AlbumViewModel = hiltViewModel(),
) {
    val album by vm.current.collectAsState()

    var title    by remember(album) { mutableStateOf(album?.title ?: "") }
    var year     by remember(album) { mutableStateOf(album?.year?.toString() ?: "") }
    var genres   by remember(album) { mutableStateOf(album?.genres ?: "") }
    var coverUrl by remember(album) { mutableStateOf(album?.coverUrl ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (album == null) "Nuevo álbum" else "Editar álbum") },
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
                value = title, onValueChange = { title = it },
                label = { Text("Título *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = year, onValueChange = { year = it.filter { c -> c.isDigit() } },
                label = { Text("Año") },
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
                label = { Text("URL de portada") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.save(title, year, genres, coverUrl); onBack() },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar") }
            album?.let { a ->
                OutlinedButton(
                    onClick = { vm.delete(a); onBack() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Eliminar álbum") }
            }
        }
    }
}

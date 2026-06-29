package com.miguelaetxio.mimoo.ui.artist

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onViewAlbums: (Long) -> Unit,
    vm: ArtistViewModel = hiltViewModel(),
) {
    val artist by vm.current.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(artist?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    artist?.let { a ->
                        IconButton(onClick = { onEdit(a.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                    }
                },
            )
        },
    ) { padding ->
        artist?.let { a ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                a.genres?.let { Text("Géneros: $it", style = MaterialTheme.typography.bodyMedium) }
                a.bio?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onViewAlbums(a.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ver álbumes") }
                OutlinedButton(
                    onClick = { vm.delete(a); onBack() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Eliminar artista") }
            }
        }
    }
}

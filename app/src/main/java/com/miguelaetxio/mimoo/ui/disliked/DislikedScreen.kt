package com.miguelaetxio.mimoo.ui.disliked

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.local.entity.DislikedArtist
import com.miguelaetxio.mimoo.data.local.entity.DislikedTrack
import com.miguelaetxio.mimoo.ui.theme.glassChip

/**
 * Pantalla de gestión (CRUD) de la Lista Negra -- H16 (2026-08-05).
 * Alcance cerrado con Miguel Ángel en S029: SOLO ver/borrar lo ya
 * añadido desde el ExoPlayer o el Explorador, sin alta manual --
 * ver `ANNEX_H16.md`, "Puntos de diseño -- CERRADOS", punto 4. Entrada
 * propia en el drawer, fuera de Ajustes -- punto 5 de "Decisiones ya
 * cerradas con Miguel Ángel en S029".
 * ---
 * Blacklist management (CRUD) screen -- H16 (2026-08-05). Scope closed
 * with Miguel Ángel in S029: view/delete ONLY what was already added
 * from the ExoPlayer or the Explorer, no manual addition -- see
 * `ANNEX_H16.md`, "Puntos de diseño -- CERRADOS", point 4. Own entry
 * in the drawer, outside Ajustes -- point 5 of "Decisiones ya cerradas
 * con Miguel Ángel en S029".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DislikedScreen(
    viewModel: DislikedViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip(interactive = false)) {
                        Text(
                            "Lista negra",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier.padding(4.dp)
                            .glassChip(shape = androidx.compose.foundation.shape.CircleShape),
                    ) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menú")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = uiState.tab.ordinal) {
                Tab(
                    selected = uiState.tab == DislikedTab.ARTISTS,
                    onClick = { viewModel.selectTab(DislikedTab.ARTISTS) },
                    text = { Text("Artistas") },
                )
                Tab(
                    selected = uiState.tab == DislikedTab.TRACKS,
                    onClick = { viewModel.selectTab(DislikedTab.TRACKS) },
                    text = { Text("Temas") },
                )
            }

            when (uiState.tab) {
                DislikedTab.ARTISTS -> ArtistsTab(uiState.artists, onRemove = viewModel::removeArtist)
                DislikedTab.TRACKS -> TracksTab(uiState.tracks, onRemove = viewModel::removeTrack)
            }
        }
    }
}

@Composable
private fun EmptyTabMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ArtistsTab(artists: List<DislikedArtist>, onRemove: (String) -> Unit) {
    if (artists.isEmpty()) {
        EmptyTabMessage(
            "Todavía no has marcado ningún artista como \"no me gusta\". " +
                "Hazlo desde el reproductor o desde el Explorador.",
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(artists, key = { it.artist }) { disliked ->
            DislikedRow(
                icon = Icons.Filled.Person,
                title = disliked.artist,
                subtitle = null,
                onRemove = { onRemove(disliked.artist) },
            )
        }
    }
}

@Composable
private fun TracksTab(tracks: List<DislikedTrack>, onRemove: (String, String) -> Unit) {
    if (tracks.isEmpty()) {
        EmptyTabMessage(
            "Todavía no has marcado ningún tema como \"no me gusta\". " +
                "Hazlo desde el reproductor o desde el Explorador.",
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(tracks, key = { "${it.artist}|${it.title}" }) { disliked ->
            DislikedRow(
                icon = Icons.Filled.MusicNote,
                title = disliked.title,
                subtitle = disliked.artist,
                onRemove = { onRemove(disliked.artist, disliked.title) },
            )
        }
    }
}

@Composable
private fun DislikedRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .glassChip()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Quitar de la lista negra")
        }
    }
}

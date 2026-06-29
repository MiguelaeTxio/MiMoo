package com.miguelaetxio.mimoo.ui.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.local.entity.Artist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistListScreen(
    onArtistClick: (Long) -> Unit,
    onAddArtist: () -> Unit,
    vm: ArtistViewModel = hiltViewModel(),
) {
    val artists by vm.artists.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Artistas") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddArtist) {
                Icon(Icons.Default.Add, contentDescription = "Añadir artista")
            }
        },
    ) { padding ->
        if (artists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Sin artistas. Pulsa + para añadir.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(contentPadding = padding) {
                items(artists, key = { it.id }) { artist ->
                    ArtistItem(artist = artist, onClick = { onArtistClick(artist.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ArtistItem(artist: Artist, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(artist.name) },
        supportingContent = artist.genres?.let { { Text(it) } },
        leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
    )
}

package com.miguelaetxio.mimoo.ui.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.local.entity.Album

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumListScreen(
    onBack: () -> Unit,
    onAddAlbum: (Long) -> Unit,
    onAlbumEdit: (Long, Long) -> Unit,
    vm: AlbumViewModel = hiltViewModel(),
) {
    val albums by vm.albums.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Álbumes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddAlbum(vm.albums.value.firstOrNull()?.artistId ?: -1L) }) {
                Icon(Icons.Default.Add, contentDescription = "Añadir álbum")
            }
        },
    ) { padding ->
        if (albums.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Sin álbumes. Pulsa + para añadir.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(contentPadding = padding) {
                items(albums, key = { it.id }) { album ->
                    AlbumItem(
                        album = album,
                        onClick = { onAlbumEdit(album.artistId, album.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AlbumItem(album: Album, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(album.title) },
        supportingContent = album.year?.let { { Text(it.toString()) } },
        leadingContent = { Icon(Icons.Default.Album, contentDescription = null) },
    )
}

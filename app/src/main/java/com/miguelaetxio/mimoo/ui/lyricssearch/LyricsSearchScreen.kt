package com.miguelaetxio.mimoo.ui.lyricssearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.ui.theme.glassChip

/**
 * Pantalla de búsqueda y lectura de letras (H17, S031, bloque 3) --
 * ver "Puntos de diseño -- CERRADOS EN S031", punto 5 de
 * DOCS/ANNEX_H17.md: búsqueda libre contra lrclib.net, cualquier
 * canción exista o no en la biblioteca, con distinción visual (chip)
 * para lo que ya está descargado en MiMoo. Entrada propia en el
 * drawer -- mismo patrón que Lista Negra (H16)/miMooutCast (H15).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSearchScreen(
    viewModel: LyricsSearchViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val expandedResultId by viewModel.expandedResultId.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip(interactive = false)) {
                        Text(
                            "Letras",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier.padding(4.dp).glassChip(shape = CircleShape),
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text("Artista, título o ambos") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { viewModel.search() },
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.glassChip(shape = CircleShape)) {
                    IconButton(onClick = viewModel::search, enabled = uiState.query.isNotBlank()) {
                        Icon(Icons.Filled.Search, contentDescription = "Buscar")
                    }
                }
            }

            when {
                uiState.loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.hasSearched && uiState.results.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No se han encontrado letras para \"${uiState.query}\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                !uiState.hasSearched -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Busca cualquier canción para leer su letra.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.results, key = { it.result.id }) { item ->
                            LyricsSearchRow(
                                item = item,
                                expanded = expandedResultId == item.result.id,
                                lyricsText = if (expandedResultId == item.result.id) {
                                    viewModel.readableLyrics(item.result)
                                } else {
                                    null
                                },
                                onClick = { viewModel.toggleExpanded(item.result.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsSearchRow(
    item: LyricsSearchResultItem,
    expanded: Boolean,
    lyricsText: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .glassChip()
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.result.trackName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.result.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // H17 (S031, punto 5) -- distinción visual "ya en tu
            // biblioteca", sin bloquear ni filtrar el resto de
            // resultados.
            if (item.inLibrary) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Ya en tu biblioteca",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = lyricsText ?: "No hay letra disponible para este tema.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (lyricsText != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

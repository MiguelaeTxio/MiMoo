package com.miguelaetxio.mimoo.ui.radiobrowser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.miguelaetxio.mimoo.data.remote.dto.RadioStation

/**
 * Pantalla "Radio Online" (H09 PASO 3, S010) -- emisoras de radio de
 * todo el mundo por género/país/búsqueda de texto, vía
 * Radio-Browser.info. Solo streaming, ningún botón de descarga en
 * ningún punto de esta pantalla -- decisión explícita de Miguel Ángel,
 * confirmada de nuevo en S010.
 * ---
 * "Radio Online" screen (H09 STEP 3, S010) -- radio stations from all
 * over the world by genre/country/free-text search, via
 * Radio-Browser.info. Streaming only, no download button anywhere on
 * this screen -- explicit decision from Miguel Ángel, confirmed again
 * in S010.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioBrowserScreen(
    viewModel: RadioBrowserViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Radio Online") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menú")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleShowFavoritesOnly) {
                        Icon(
                            if (uiState.showFavoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (uiState.showFavoritesOnly) {
                                "Mostrando solo favoritas"
                            } else {
                                "Mostrar solo favoritas"
                            },
                            tint = if (uiState.showFavoritesOnly) {
                                MaterialTheme.colorScheme.error
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            if (!uiState.showFavoritesOnly) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar emisora por nombre") },
                trailingIcon = {
                    IconButton(onClick = viewModel::search) {
                        Icon(Icons.Filled.Search, contentDescription = "Buscar")
                    }
                },
                singleLine = true,
            )

            Spacer(Modifier.height(12.dp))

            if (uiState.genres.isNotEmpty()) {
                Text(
                    "Género",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.genres, key = { it.label }) { genre ->
                        RadioFilterChip(
                            label = genre.label,
                            selected = uiState.selectedGenre == genre,
                            onClick = { viewModel.onGenreSelect(genre) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (uiState.decades.isNotEmpty()) {
                Text(
                    "Década",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.decades, key = { it.label }) { decade ->
                        RadioFilterChip(
                            label = decade.label,
                            selected = uiState.selectedDecade == decade,
                            onClick = { viewModel.onDecadeSelect(decade) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (uiState.countries.isNotEmpty()) {
                Text(
                    "País",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        RadioFilterChip(
                            label = "Todos",
                            selected = uiState.selectedCountryCode == null,
                            onClick = { viewModel.onCountrySelect(null) },
                        )
                    }
                    itemsIndexed(
                        uiState.countries,
                        key = { index, country -> "$index-${country.isoCode ?: country.name}" },
                    ) { _, country ->
                        RadioFilterChip(
                            label = country.name,
                            selected = uiState.selectedCountryCode == country.isoCode,
                            onClick = { viewModel.onCountrySelect(country.isoCode) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            } else if (!uiState.isLoadingFilters) {
                // S010 -- antes desaparecía sin ningún aviso si
                // getCountries() fallaba (RadioBrowserRepository es
                // defensivo, nunca lanza, así que un fallo de red se
                // veía exactamente igual que "no hay países", y la
                // fila entera se esfumaba sin que Miguel Ángel supiera
                // por qué). Ahora, si tras terminar de cargar la lista
                // sigue vacía, se avisa explícitamente con opción de
                // reintentar en vez de desaparecer.
                // ---
                // S010 -- used to vanish with no warning at all if
                // getCountries() failed (RadioBrowserRepository is
                // defensive, never throws, so a network failure looked
                // exactly like "there are no countries", and the whole
                // row disappeared without any indication why). Now, if
                // the list is still empty after loading finished, it's
                // explicitly flagged with a retry option instead of
                // vanishing.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "No se pudo cargar la lista de países.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::retryLoadCountries) {
                        Text("Reintentar")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            }

            if (uiState.isSearching || uiState.isLoadingFilters) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (!uiState.isSearching && uiState.stations.isEmpty()) {
                Text(
                    "Sin resultados para este filtro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.stations, key = { it.stationUuid }) { station ->
                    RadioStationRow(
                        station = station,
                        isFavorite = station.stationUuid in uiState.favoriteUuids,
                        onPlay = { viewModel.playStation(station) },
                        onToggleFavorite = { viewModel.toggleFavorite(station) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadioFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

/**
 * Fila de una emisora -- favicon, nombre, país + etiquetas. Tocar
 * cualquier punto de la fila la reproduce directamente en streaming
 * (PlayerManager.play(), reutilizando toda la infraestructura de
 * ExoPlayer/notificación/barra de progreso ya existente). Sin ningún
 * botón de descarga -- a diferencia de SearchResultRow, esta fila no
 * tiene equivalente al DownloadButton.
 * ---
 * Station row -- favicon, name, country + tags. Tapping anywhere on
 * the row plays it directly in streaming (PlayerManager.play(),
 * reusing all the existing ExoPlayer/notification/progress bar
 * infrastructure). No download button anywhere -- unlike
 * SearchResultRow, this row has no DownloadButton equivalent.
 */
@Composable
private fun RadioStationRow(
    station: RadioStation,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!station.favicon.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = station.favicon,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                error = {
                    Icon(
                        Icons.Filled.Radio,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        } else {
            Icon(
                Icons.Filled.Radio,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                station.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!station.country.isNullOrBlank()) {
                    Icon(
                        Icons.Filled.Public,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    listOfNotNull(
                        station.country?.ifBlank { null },
                        station.tags?.ifBlank { null },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavorite) "Quitar de favoritas" else "Añadir a favoritas",
                tint = if (isFavorite) MaterialTheme.colorScheme.error else LocalContentColor.current,
            )
        }

        IconButton(onClick = onPlay) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir")
        }
    }
}

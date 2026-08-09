package com.miguelaetxio.mimoo.ui.mimooutcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.remote.MimooutcastDecade
import com.miguelaetxio.mimoo.data.remote.MimooutcastGenre
import com.miguelaetxio.mimoo.data.remote.MimooutcastOrigin
import com.miguelaetxio.mimoo.ui.theme.GlassTokens
import com.miguelaetxio.mimoo.ui.theme.glassChip

/**
 * H15 (miMooutCast) -- pantalla nueva para elegir el ancla de la
 * Radio A MANO: tres secciones, Géneros (con un segundo nivel de
 * subgéneros donde MusicBrainz los tenga catalogados -- petición de
 * Miguel Ángel, 2026-08-06), Décadas y Origen. Una sola chapita
 * elegida arranca la sesión de inmediato -- "muy intuitivo" -- sin
 * pantalla de confirmación intermedia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MimooutcastScreen(
    viewModel: MimooutcastViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip(interactive = false)) {
                        Text(
                            "miMooutCast",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier.padding(4.dp).glassChip(shape = CircleShape),
                    ) {
                        if (uiState.expandedGenre != null) {
                            IconButton(onClick = viewModel::collapseGenre) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Volver a géneros")
                            }
                        } else {
                            IconButton(onClick = onOpenDrawer) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menú")
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // H15 (miMooutCast), S032 -- botón TRANSVERSAL, encima de
            // las pestañas para que afecte a las tres por igual (no es
            // parte de ninguna pestaña concreta). Orden de Miguel
            // Ángel, ver el kdoc de `MimooutcastUiState.requireKnownInSpain`.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .glassChip()
                    .clickable(onClick = viewModel::toggleRequireKnownInSpain)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Conocido en España", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Compara cada candidato contra los éxitos en España. Actívalo en " +
                            "décadas/géneros amplios; desactívalo en géneros de nicho para " +
                            "tener candidatos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = uiState.requireKnownInSpain, onCheckedChange = { viewModel.toggleRequireKnownInSpain() })
            }

            TabRow(selectedTabIndex = uiState.tab.ordinal) {
                Tab(
                    selected = uiState.tab == MimooutcastTab.GENEROS,
                    onClick = { viewModel.selectTab(MimooutcastTab.GENEROS) },
                    text = { Text("Géneros") },
                )
                Tab(
                    selected = uiState.tab == MimooutcastTab.DECADAS,
                    onClick = { viewModel.selectTab(MimooutcastTab.DECADAS) },
                    text = { Text("Décadas") },
                )
                Tab(
                    selected = uiState.tab == MimooutcastTab.ORIGENES,
                    onClick = { viewModel.selectTab(MimooutcastTab.ORIGENES) },
                    text = { Text("Origen") },
                )
            }

            Text(
                text = uiState.expandedGenre?.let { "Subgéneros de \"${it.label}\" -- o elige el género entero." }
                    ?: "Elige una chapita para arrancar la Radio anclada ahí -- sin tener nada sonando antes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            // H15 (miMooutCast), S032 -- filtro de búsqueda de géneros
            // pedido por Miguel Ángel: *"hay que poner un filtro para
            // buscar géneros."* Solo en la pestaña Géneros -- Décadas y
            // Origen tienen listas cortas y fijas, no lo necesitan.
            if (uiState.tab == MimooutcastTab.GENEROS) {
                OutlinedTextField(
                    value = uiState.genreSearchQuery,
                    onValueChange = viewModel::updateGenreSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Buscar género...") },
                    singleLine = true,
                )
            }

            if (uiState.noResultsFor != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .glassChip(),
                ) {
                    Text(
                        "No se ha encontrado ningún tema para \"${uiState.noResultsFor}\". " +
                            "Prueba otra chapita.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                val expanded = uiState.expandedGenre
                val query = uiState.genreSearchQuery.trim()
                when {
                    uiState.tab == MimooutcastTab.GENEROS && expanded != null -> SubgenreGrid(
                        root = expanded,
                        subgenres = viewModel.subgenresOf(expanded)
                            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) },
                        loadingLabel = uiState.loadingLabel,
                        onPickRoot = { viewModel.startWithGenre(expanded.mbGenre, expanded.label) },
                        onPickSub = { sub -> viewModel.startWithGenre(sub.mbGenre, sub.label) },
                    )
                    uiState.tab == MimooutcastTab.GENEROS -> GenreGrid(
                        genres = viewModel.genres
                            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) },
                        loadingLabel = uiState.loadingLabel,
                        onPick = viewModel::tapGenre,
                    )
                    uiState.tab == MimooutcastTab.DECADAS -> DecadeGrid(
                        decades = viewModel.decades,
                        loadingLabel = uiState.loadingLabel,
                        onPick = { d -> viewModel.startWithDecade(d.decadeBegin, d.label) },
                    )
                    else -> OriginGrid(
                        origins = viewModel.origins,
                        loadingLabel = uiState.loadingLabel,
                        onPick = { o -> viewModel.startWithOrigin(o.group, o.label) },
                    )
                }
                // Petición explícita de Miguel Ángel (2026-08-06): capa
                // opaca, no un overlay translúcido -- que no se mezcle
                // con las chapitas de debajo mientras se resuelve.
                if (uiState.loadingLabel != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Buscando \"${uiState.loadingLabel}\"...")
                            // H15 (miMooutCast), S032 -- botón "dejar de
                            // buscar" pedido por Miguel Ángel: *"cuando ya
                            // veo que no encuentra absolutamente nada y voy
                            // a escuchar otra cosa, te salta lo que estaba
                            // buscando."*
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = viewModel::cancelSearch) {
                                Text("Dejar de buscar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreGrid(
    genres: List<MimooutcastGenre>,
    loadingLabel: String?,
    onPick: (MimooutcastGenre) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        genres.forEach { genre ->
            AnchorChip(
                label = genre.label,
                enabled = loadingLabel == null,
                onClick = { onPick(genre) },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** H15 -- segundo nivel de un género raíz: sus subgéneros directos + una chapita para el género entero. */
@Composable
private fun SubgenreGrid(
    root: MimooutcastGenre,
    subgenres: List<MimooutcastGenre>,
    loadingLabel: String?,
    onPickRoot: () -> Unit,
    onPickSub: (MimooutcastGenre) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnchorChip(
            label = "Todo ${root.label}",
            enabled = loadingLabel == null,
            onClick = onPickRoot,
        )
        subgenres.forEach { sub ->
            AnchorChip(
                label = sub.label,
                enabled = loadingLabel == null,
                onClick = { onPickSub(sub) },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DecadeGrid(
    decades: List<MimooutcastDecade>,
    loadingLabel: String?,
    onPick: (MimooutcastDecade) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        decades.forEach { decade ->
            AnchorChip(
                label = decade.label,
                enabled = loadingLabel == null,
                onClick = { onPick(decade) },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OriginGrid(
    origins: List<MimooutcastOrigin>,
    loadingLabel: String?,
    onPick: (MimooutcastOrigin) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        origins.forEach { origin ->
            AnchorChip(
                label = origin.label,
                enabled = loadingLabel == null,
                onClick = { onPick(origin) },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AnchorChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .glassChip(shape = RoundedCornerShape(GlassTokens.cornerRadius))
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

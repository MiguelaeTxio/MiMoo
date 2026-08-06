package com.miguelaetxio.mimoo.ui.mimooutcast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
 * Radio A MANO: dos secciones, Géneros y Décadas (S029 cerró una
 * tercera, Origen, pendiente de una entrega siguiente -- ver
 * `DOCS/ANNEX_H15.md`). Una sola chapita elegida arranca la sesión
 * de inmediato -- "muy intuitivo", petición explícita de Miguel
 * Ángel -- sin pantalla de confirmación intermedia.
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
                "Elige una chapita para arrancar la Radio anclada ahí -- sin tener nada sonando antes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

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
                when (uiState.tab) {
                    MimooutcastTab.GENEROS -> GenreGrid(
                        genres = viewModel.genres,
                        loadingLabel = uiState.loadingLabel,
                        onPick = { g -> viewModel.startWithGenre(g.mbGenre, g.label) },
                    )
                    MimooutcastTab.DECADAS -> DecadeGrid(
                        decades = viewModel.decades,
                        loadingLabel = uiState.loadingLabel,
                        onPick = { d -> viewModel.startWithDecade(d.decadeBegin, d.label) },
                    )
                    MimooutcastTab.ORIGENES -> OriginGrid(
                        origins = viewModel.origins,
                        loadingLabel = uiState.loadingLabel,
                        onPick = { o -> viewModel.startWithOrigin(o.group, o.label) },
                    )
                }
                if (uiState.loadingLabel != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Buscando \"${uiState.loadingLabel}\"...")
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

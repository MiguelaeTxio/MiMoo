package com.miguelaetxio.mimoo.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.stats.TopLabelRow
import com.miguelaetxio.mimoo.ui.library.displayArtistName
import com.miguelaetxio.mimoo.ui.theme.glassChip
import java.text.DateFormat
import java.util.Date

private fun formatDuration(totalMs: Long): String {
    val minutes = totalMs / 60_000L
    return when {
        totalMs <= 0L -> "0 min"
        minutes < 1L -> "<1 min"
        minutes < 60L -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip(interactive = false)) {
                        Text("Estadísticas", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(4.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menú")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard("Descargas y archivos") {
                    StatRow(
                        "Descargadas por MiMoo",
                        "${state.downloadedTracks} · ${formatDuration(state.downloadedSeconds * 1000L)}",
                    )
                    StatRow(
                        "Archivos que ya estaban en la carpeta",
                        "${state.localFileTracks} · ${formatDuration(state.localFileSeconds * 1000L)}",
                    )
                    if (state.activeDownloads > 0) StatRow("En cola o descargando", "${state.activeDownloads}")
                    if (state.failedDownloads > 0) StatRow("Con error", "${state.failedDownloads}")
                }
            }
            item {
                SectionCard("Escuchas") {
                    val first = state.firstPlayedAt
                    Text(
                        if (first == null) {
                            "Aún no hay escuchas registradas. Se cuentan desde esta versión, " +
                                "cuando una pista suena al menos 30 segundos."
                        } else {
                            "Contadas desde el " +
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(first)) + "."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    KindRow(state, "LOCAL", "Reproducidas desde archivo local")
                    KindRow(state, "STREAMING", "Reproducidas en streaming")
                    KindRow(state, "RADIO", "Radio automática")
                    KindRow(state, "MIMOOUTCAST", "miMooutCast")
                    KindRow(state, "STATION", "Emisoras de radio online")
                }
            }
            if (state.topTracks.isNotEmpty()) {
                item {
                    SectionCard("Canciones más escuchadas") {
                        state.topTracks.forEachIndexed { i, row ->
                            val name = if (row.artist.isNullOrBlank()) row.title
                            else "${row.title} — ${displayArtistName(row.artist)}"
                            StatRow("${i + 1}. $name", "${row.plays}×")
                        }
                    }
                }
            }
            if (state.topArtists.isNotEmpty()) {
                item { RankingCard("Artistas más escuchados", state.topArtists, artist = true) }
            }
            if (state.topLists.isNotEmpty()) {
                item { RankingCard("Listas más escuchadas", state.topLists, prefix = "Lista: ") }
            }
            if (state.topStations.isNotEmpty()) {
                item { RankingCard("Emisoras online", state.topStations, byTime = true) }
            }
            if (state.topMimooutcastArtists.isNotEmpty()) {
                item { RankingCard("miMooutCast: artistas más sonados", state.topMimooutcastArtists, artist = true) }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun KindRow(state: StatsUiState, kind: String, label: String) {
    val t = state.totalsByKind[kind]
    StatRow(label, if (t == null) "0" else "${t.plays} · ${formatDuration(t.totalMs)}")
}

@Composable
private fun RankingCard(
    title: String,
    rows: List<TopLabelRow>,
    prefix: String = "",
    artist: Boolean = false,
    byTime: Boolean = false,
) {
    SectionCard(title) {
        rows.forEachIndexed { i, row ->
            val name = row.label.removePrefix(prefix).let { if (artist) displayArtistName(it) else it }
            val value = if (byTime) formatDuration(row.totalMs) else "${row.plays}×"
            StatRow("${i + 1}. $name", value)
        }
    }
}

package com.miguelaetxio.mimoo.ui.channels

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.miguelaetxio.mimoo.ui.theme.glassChip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.miguelaetxio.mimoo.ui.common.SortControl

/**
 * Pantalla "Canales" (H11 PASO 3, S011) -- canales de YouTube a los
 * que Miguel Ángel está suscrito, con su contenido ya descargado.
 * Suscribirse vive en la búsqueda (SearchScreen, modo Canales, H08
 * PARTE 1) -- esta pantalla es solo para ver/reproducir/darse de baja
 * de lo ya suscrito, no para buscar canales nuevos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    viewModel: ChannelsViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as Activity
    val snackbarHostState = remember { SnackbarHostState() }

    // H07 PARTE 1 (S015) -- aviso cuando dar de baja se rechaza por falta de conexión.
    LaunchedEffect(uiState.syncBlockedMessage) {
        uiState.syncBlockedMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSyncBlockedMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip(interactive = false)) {
                        Text("Canales", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
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
        if (uiState.channels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Podcasts,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Todavía no estás suscrito a ningún canal.\n" +
                            "Busca uno en \"Buscar\" → modo Canales.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SortControl(
                criterion = uiState.sortCriterion,
                direction = uiState.sortDirection,
                onCriterionChange = viewModel::setSortCriterion,
                onToggleDirection = viewModel::toggleSortDirection,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(uiState.channels, key = { it.subscription.channelId }) { channel ->
                    ChannelSection(
                        channel = channel,
                        onPlayAll = { viewModel.playChannelTracks(channel) },
                        onUnsubscribe = { viewModel.unsubscribe(activity, channel.subscription) },
                        onPlayTrack = { viewModel.playTrack(it) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ChannelSection(
    channel: ChannelWithTracks,
    onPlayAll: () -> Unit,
    onUnsubscribe: () -> Unit,
    onPlayTrack: (com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassChip(interactive = false)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (channel.subscription.thumbnailUrl != null) {
                SubcomposeAsyncImage(
                    model = channel.subscription.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)),
                    error = {},
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.subscription.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${channel.tracks.size} descargado(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(modifier = Modifier.padding(2.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
                IconButton(onClick = onPlayAll, enabled = channel.tracks.isNotEmpty()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir todo el canal")
                }
            }
            Box(modifier = Modifier.padding(2.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
                IconButton(onClick = onUnsubscribe) {
                    Icon(
                        Icons.Filled.RemoveCircleOutline,
                        contentDescription = "Darse de baja del canal",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        channel.tracks.forEach { track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 44.dp, end = 8.dp, top = 3.dp, bottom = 3.dp)
                    .glassChip()
                    .clickable { onPlayTrack(track) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (channel.tracks.isEmpty()) {
            Text(
                "Sin contenido descargado todavía.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 52.dp, top = 4.dp),
            )
        }
    }
}

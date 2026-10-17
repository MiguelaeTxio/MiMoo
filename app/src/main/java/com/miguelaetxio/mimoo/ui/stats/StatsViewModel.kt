package com.miguelaetxio.mimoo.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.dao.PlayEventDao
import com.miguelaetxio.mimoo.data.local.dao.SearchResultTrackDao
import com.miguelaetxio.mimoo.data.stats.PlayKindTotal
import com.miguelaetxio.mimoo.data.stats.StatsAggregator
import com.miguelaetxio.mimoo.data.stats.StatsRepository
import com.miguelaetxio.mimoo.data.stats.StatsSyncManager
import com.miguelaetxio.mimoo.data.stats.TopLabelRow
import com.miguelaetxio.mimoo.data.stats.TopTrackRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val TOP_LIMIT = 10

data class StatsUiState(
    val loaded: Boolean = false,
    val downloadedTracks: Int = 0,
    val downloadedSeconds: Long = 0L,
    val localFileTracks: Int = 0,
    val localFileSeconds: Long = 0L,
    val activeDownloads: Int = 0,
    val failedDownloads: Int = 0,
    val totalsByKind: Map<String, PlayKindTotal> = emptyMap(),
    val topTracks: List<TopTrackRow> = emptyList(),
    val topArtists: List<TopLabelRow> = emptyList(),
    val topLists: List<TopLabelRow> = emptyList(),
    val topStations: List<TopLabelRow> = emptyList(),
    val topMimooutcastArtists: List<TopLabelRow> = emptyList(),
    val firstPlayedAt: Long? = null,
)

/**
 * Pantalla de Estadísticas: descargas (datos que ya existían) y
 * escuchas. Las escuchas son los totales de Drive (copia local de la
 * última sincronización) más las escuchas de este dispositivo que
 * todavía no se han subido.
 * ---
 * Statistics screen: downloads (data that already existed) and
 * listens. Listens are Drive's totals (local copy of the last sync)
 * plus this device's listens not yet uploaded.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    playEventDao: PlayEventDao,
    trackDao: SearchResultTrackDao,
    statsRepository: StatsRepository,
    statsSyncManager: StatsSyncManager,
) : ViewModel() {

    init {
        statsSyncManager.requestSync(immediate = true)
    }

    val uiState: StateFlow<StatsUiState> = combine(
        statsRepository.cache,
        playEventDao.observeAll(),
        trackDao.observeDownloadSummary(),
    ) { cache, pending, downloads ->
        val block = StatsAggregator.apply(cache, pending)
        val done = downloads.filter { it.status == "DONE" }
        val mimoo = done.filter { it.isLocalFile == 0 }
        val local = done.filter { it.isLocalFile == 1 }
        StatsUiState(
            loaded = true,
            downloadedTracks = mimoo.sumOf { it.tracks },
            downloadedSeconds = mimoo.sumOf { it.seconds },
            localFileTracks = local.sumOf { it.tracks },
            localFileSeconds = local.sumOf { it.seconds },
            activeDownloads = downloads
                .filter { it.status == "QUEUED" || it.status == "DOWNLOADING" }
                .sumOf { it.tracks },
            failedDownloads = downloads
                .filter { it.status == "ERROR" }
                .sumOf { it.tracks },
            totalsByKind = StatsAggregator.totals(block),
            topTracks = StatsAggregator.topTracks(block, TOP_LIMIT),
            topArtists = StatsAggregator.topByPlays(block.artists, TOP_LIMIT),
            topLists = StatsAggregator.topByPlays(block.lists, TOP_LIMIT),
            topStations = StatsAggregator.topByTime(block.stations, TOP_LIMIT),
            topMimooutcastArtists = StatsAggregator.topByPlays(block.mimooutcastArtists, TOP_LIMIT),
            firstPlayedAt = block.firstPlayedAt,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}

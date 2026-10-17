package com.miguelaetxio.mimoo.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.dao.DownloadSummaryRow
import com.miguelaetxio.mimoo.data.local.dao.PlayEventDao
import com.miguelaetxio.mimoo.data.local.dao.PlayKindTotal
import com.miguelaetxio.mimoo.data.local.dao.SearchResultTrackDao
import com.miguelaetxio.mimoo.data.local.dao.TopLabelRow
import com.miguelaetxio.mimoo.data.local.dao.TopTrackRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

private data class PlayPart(
    val totals: List<PlayKindTotal>,
    val tracks: List<TopTrackRow>,
    val artists: List<TopLabelRow>,
    val lists: List<TopLabelRow>,
    val firstPlayedAt: Long?,
)

private data class OtherPart(
    val stations: List<TopLabelRow>,
    val mimooutcastArtists: List<TopLabelRow>,
    val downloads: List<DownloadSummaryRow>,
)

/**
 * Pantalla de Estadísticas: descargas (datos que ya existían) y
 * escuchas (PlayEvent, contadas desde que existe esta pantalla).
 * ---
 * Statistics screen: downloads (data that already existed) and
 * listens (PlayEvent, counted since this screen exists).
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    playEventDao: PlayEventDao,
    trackDao: SearchResultTrackDao,
) : ViewModel() {

    private val playPart = combine(
        playEventDao.observeTotalsByKind(),
        playEventDao.observeTopTracks(TOP_LIMIT),
        playEventDao.observeTopArtists(TOP_LIMIT),
        playEventDao.observeTopLists(TOP_LIMIT),
        playEventDao.observeFirstPlayedAt(),
    ) { totals, tracks, artists, lists, first ->
        PlayPart(totals, tracks, artists, lists, first)
    }

    private val otherPart = combine(
        playEventDao.observeTopStations(TOP_LIMIT),
        playEventDao.observeTopMimooutcastArtists(TOP_LIMIT),
        trackDao.observeDownloadSummary(),
    ) { stations, mimooutcastArtists, downloads ->
        OtherPart(stations, mimooutcastArtists, downloads)
    }

    val uiState: StateFlow<StatsUiState> = combine(playPart, otherPart) { play, other ->
        val done = other.downloads.filter { it.status == "DONE" }
        val mimoo = done.filter { it.isLocalFile == 0 }
        val local = done.filter { it.isLocalFile == 1 }
        StatsUiState(
            loaded = true,
            downloadedTracks = mimoo.sumOf { it.tracks },
            downloadedSeconds = mimoo.sumOf { it.seconds },
            localFileTracks = local.sumOf { it.tracks },
            localFileSeconds = local.sumOf { it.seconds },
            activeDownloads = other.downloads
                .filter { it.status == "QUEUED" || it.status == "DOWNLOADING" }
                .sumOf { it.tracks },
            failedDownloads = other.downloads
                .filter { it.status == "ERROR" }
                .sumOf { it.tracks },
            totalsByKind = play.totals.associateBy { it.kind },
            topTracks = play.tracks,
            topArtists = play.artists,
            topLists = play.lists,
            topStations = other.stations,
            topMimooutcastArtists = other.mimooutcastArtists,
            firstPlayedAt = play.firstPlayedAt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}

package com.miguelaetxio.mimoo.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.StreamResolver
import com.miguelaetxio.mimoo.data.remote.ExternalLinkResolver
import com.miguelaetxio.mimoo.ui.library.UNKNOWN_ARTIST_CREDIT
import com.miguelaetxio.mimoo.util.YoutubeTitleCleaner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResultTrack> = emptyList(),
    val isSearching: Boolean = false,
    val isResolvingStream: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * ViewModel for the search screen. Handles YouTube search, stream
 * playback, and audio download requests.
 *
 * search() usa ExternalLinkResolver.searchYoutube() (yt-dlp,
 * "ytsearchN:query") en vez de YouTubeApiService.search() (search.list,
 * 100 unidades/llamada) -- petición explícita de Miguel Ángel
 * (2026-07-04): "quiero poder buscar de forma gratuita, no gastar
 * cuota". Coste de cuota CERO, sin importar cuántas veces se use.
 * ---
 * ViewModel de la pantalla de busqueda. Gestiona la busqueda en
 * YouTube, la reproduccion en streaming y las solicitudes de descarga.
 *
 * search() uses ExternalLinkResolver.searchYoutube() (yt-dlp,
 * "ytsearchN:query") instead of YouTubeApiService.search() (search.list,
 * 100 units/call) -- explicit request from Miguel Ángel (2026-07-04):
 * "I want to be able to search for free, not spend quota". ZERO quota
 * cost, no matter how many times it's used.
 *
 * Download state flow:
 *   search() inserts tracks into Room ->
 *   _currentYoutubeIds emits the new ID set ->
 *   resultFlow collects Room's Flow filtered to the current search ->
 *   DownloadWorker updates Room ->
 *   Room emits -> UI recomposes automatically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val externalLinkResolver: ExternalLinkResolver,
    private val searchResultTrackRepository: SearchResultTrackRepository,
    private val streamResolver: StreamResolver,
    private val playerManager: PlayerManager,
    private val downloadQueueManager: DownloadQueueManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _currentYoutubeIds = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch {
            _currentYoutubeIds
                .flatMapLatest { ids ->
                    if (ids.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        searchResultTrackRepository.getAll().map { all ->
                            val idSet = ids.toSet()
                            all.filter { it.youtubeId in idSet }
                                .sortedBy { ids.indexOf(it.youtubeId) }
                        }
                    }
                }
                .collect { tracks ->
                    _uiState.value = _uiState.value.copy(results = tracks)
                }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                errorMessage = null,
            )
            try {
                val result = externalLinkResolver.searchYoutube(query)
                val tracks = result.tracks.map { dto ->
                    SearchResultTrack(
                        youtubeId = dto.youtubeId,
                        title = YoutubeTitleCleaner.clean(dto.title),
                        channelTitle = dto.channelTitle,
                        durationSeconds = dto.durationSeconds,
                        thumbnailUrl = dto.thumbnailUrl,
                        artist = dto.channelTitle.ifBlank { UNKNOWN_ARTIST_CREDIT },
                    )
                }
                searchResultTrackRepository.cacheSearchResults(tracks)
                _currentYoutubeIds.value = tracks.map { it.youtubeId }
                _uiState.value = _uiState.value.copy(isSearching = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    errorMessage = e.message ?: "Error al buscar",
                )
            }
        }
    }

    /**
     * Plays a track. If it was already downloaded (downloadStatus ==
     * DONE and filePath is set), it plays directly from the local
     * SAF file, skipping StreamResolver entirely. Otherwise, falls
     * back to resolving a live streaming URL as before.
     * ---
     * Reproduce una pista. Si ya fue descargada (downloadStatus ==
     * DONE y filePath está definido), reproduce directamente desde
     * el archivo SAF local, sin pasar por StreamResolver. En caso
     * contrario, resuelve una URL de streaming en vivo como antes.
     */
    fun playTrack(track: SearchResultTrack) {
        val localFilePath = track.filePath
        if (track.downloadStatus == DownloadStatus.DONE && localFilePath != null) {
            playerManager.play(localFilePath, track.title, isLocal = true)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isResolvingStream = true,
                errorMessage = null,
            )
            try {
                val streamUrl =
                    streamResolver.resolveAudioStreamUrl(track.youtubeUrl)
                playerManager.play(streamUrl, track.title, isLocal = false)
                _uiState.value = _uiState.value.copy(isResolvingStream = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isResolvingStream = false,
                    errorMessage = e.message ?: "Error al resolver el stream",
                )
            }
        }
    }

    /**
     * Enqueues a WorkManager download job for the given track.
     * DownloadWorker resolves the SAF destination internally via
     * StorageManager + DownloadDirManager.
     * ---
     * Encola un trabajo de descarga WorkManager para la pista dada.
     * DownloadWorker resuelve el destino SAF internamente via
     * StorageManager + DownloadDirManager.
     */
    fun requestDownload(track: SearchResultTrack) {
        viewModelScope.launch {
            downloadQueueManager.enqueue(
                youtubeId = track.youtubeId,
                title = track.title,
                artist = track.artist ?: track.channelTitle,
                album = track.album,
            )
        }
    }

    /**
     * Toggles the favorite flag for a track (PASO 4, H03). Favoriting
     * is independent of download state — a search result can be
     * favorited before it has ever been downloaded.
     * ---
     * Alterna el marcador de favorito de una pista (PASO 4, H03).
     * Marcar como favorito es independiente del estado de descarga —
     * un resultado de búsqueda puede marcarse antes de descargarse.
     */
    fun toggleFavorite(track: SearchResultTrack) {
        viewModelScope.launch {
            searchResultTrackRepository.updateFavorite(
                track.youtubeId,
                !track.isFavorite,
            )
        }
    }
}

package com.miguelaetxio.mimoo.ui.search

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.BuildConfig
import com.miguelaetxio.mimoo.data.download.DownloadDirManager
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.StreamResolver
import com.miguelaetxio.mimoo.data.remote.YouTubeRepository
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
 * ---
 * ViewModel de la pantalla de busqueda. Gestiona la busqueda en
 * YouTube, la reproduccion en streaming y las solicitudes de descarga.
 *
 * Download state flow:
 *   search() inserts tracks into Room ->
 *   _currentYoutubeIds emits the new ID set ->
 *   resultFlow collects Room's Flow<List<SearchResultTrack>> filtered
 *   to the current search ->
 *   DownloadWorker updates Room ->
 *   Room emits -> resultFlow re-emits -> UI recomposes automatically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youTubeRepository: YouTubeRepository,
    private val searchResultTrackRepository: SearchResultTrackRepository,
    private val streamResolver: StreamResolver,
    private val playerManager: PlayerManager,
    private val downloadQueueManager: DownloadQueueManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // Holds the youtubeIds of the most recent search so resultFlow
    // can filter Room's full table to only the current results.
    // Contiene los youtubeIds de la ultima busqueda para que resultFlow
    // filtre la tabla completa de Room a solo los resultados actuales.
    private val _currentYoutubeIds = MutableStateFlow<List<String>>(emptyList())

    init {
        // Observe Room for the current search results. When Room emits
        // (e.g. DownloadWorker updates downloadStatus), uiState.results
        // updates automatically without a new network call.
        // Observa Room para los resultados de la busqueda actual. Cuando
        // Room emite (p.ej. DownloadWorker actualiza downloadStatus),
        // uiState.results se actualiza automaticamente sin llamada de red.
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
                val dtos = youTubeRepository.search(
                    query = query,
                    apiKey = BuildConfig.YOUTUBE_API_KEY,
                )
                val tracks = dtos.map { dto ->
                    SearchResultTrack(
                        youtubeId = dto.youtubeId,
                        title = dto.title,
                        channelTitle = dto.channelTitle,
                        durationSeconds = dto.durationSeconds,
                        thumbnailUrl = dto.thumbnailUrl,
                    )
                }
                searchResultTrackRepository.cacheSearchResults(tracks)
                // Emit the ordered ID list; resultFlow will pick it up.
                // Emitir la lista ordenada de IDs; resultFlow la recoger.
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

    fun playTrack(track: SearchResultTrack) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isResolvingStream = true,
                errorMessage = null,
            )
            try {
                val streamUrl =
                    streamResolver.resolveAudioStreamUrl(track.youtubeUrl)
                playerManager.play(streamUrl, track.title)
                _uiState.value = _uiState.value.copy(
                    isResolvingStream = false,
                )
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
     * Uses DownloadDirManager.getTrackDir() to resolve the output
     * directory, then appends the sanitized title as filename.
     * externalStorageDirectory is the legacy public root
     * (/sdcard), compatible with minSdk 26 and the existing
     * WRITE_EXTERNAL_STORAGE permission declared in the Manifest.
     * ---
     * Encola un trabajo de descarga WorkManager para la pista dada.
     * Usa DownloadDirManager.getTrackDir() para resolver el directorio
     * de salida y añade el titulo sanitizado como nombre de archivo.
     * externalStorageDirectory es la raiz publica legacy (/sdcard),
     * compatible con minSdk 26 y el permiso WRITE_EXTERNAL_STORAGE
     * declarado en el Manifest.
     */
    fun requestDownload(track: SearchResultTrack) {
        @Suppress("DEPRECATION")
        val storageRoot = Environment.getExternalStorageDirectory()
        val trackDir = DownloadDirManager.getTrackDir(
            externalStorageRoot = storageRoot,
            artist = track.channelTitle,
            album = null,
        )
        val safeTitle = DownloadDirManager.sanitize(track.title)
        val outputPath = "${trackDir.absolutePath}/$safeTitle"
        downloadQueueManager.enqueue(track.youtubeId, outputPath)
    }
}

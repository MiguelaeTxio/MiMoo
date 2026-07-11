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
import com.miguelaetxio.mimoo.data.remote.SearchResultType
import com.miguelaetxio.mimoo.data.remote.dto.SearchTypeResult
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

/**
 * H08 PARTE 1 (S009) -- qué busca la pantalla de Búsqueda: vídeos
 * sueltos (comportamiento original, H01), o listas/canales creados
 * por otros usuarios en YouTube (selector nuevo dentro de la propia
 * pantalla, decisión explícita de Miguel Ángel: "un selector estaría
 * bien... así no complicamos mucho la sidebar").
 * ---
 * H08 PARTE 1 (S009) -- what the Search screen searches for: single
 * videos (original behavior, H01), or playlists/channels created by
 * other users on YouTube (new selector inside the screen itself,
 * Miguel Ángel's explicit decision: "a selector would be nice... that
 * way we don't complicate the sidebar much").
 */
enum class SearchMode { VIDEOS, PLAYLISTS, CHANNELS }

data class SearchUiState(
    val query: String = "",
    val mode: SearchMode = SearchMode.VIDEOS,
    val results: List<SearchResultTrack> = emptyList(),
    val typeResults: List<SearchTypeResult> = emptyList(),
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

    /**
     * Cambiar de modo no repite la búsqueda automáticamente (igual
     * que search() ya requiere pulsar el botón) -- solo limpia los
     * resultados del modo anterior para que no se vea una lista de
     * un tipo distinto al seleccionado.
     * ---
     * Switching modes doesn't auto-repeat the search (same as
     * search() already requiring the button) -- it only clears the
     * previous mode's results so a stale, wrong-type list isn't left
     * showing.
     */
    fun onModeChange(mode: SearchMode) {
        _uiState.value = _uiState.value.copy(
            mode = mode,
            results = emptyList(),
            typeResults = emptyList(),
            errorMessage = null,
        )
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return

        when (_uiState.value.mode) {
            SearchMode.VIDEOS -> searchVideos(query)
            SearchMode.PLAYLISTS -> searchByType(query, SearchResultType.PLAYLIST)
            SearchMode.CHANNELS -> searchByType(query, SearchResultType.CHANNEL)
        }
    }

    private fun searchVideos(query: String) {
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
     * H08 PARTE 1 (S009). No trata una lista vacía como error --
     * la búsqueda filtrada por tipo es una zona menos estable de
     * yt-dlp, ver ExternalLinkResolver.searchByType().
     * ---
     * H08 PARTE 1 (S009). Doesn't treat an empty list as an error --
     * type-filtered search is a less stable area of yt-dlp, see
     * ExternalLinkResolver.searchByType().
     */
    private fun searchByType(query: String, type: SearchResultType) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                errorMessage = null,
                typeResults = emptyList(),
            )
            try {
                val results = externalLinkResolver.searchByType(query, type)
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    typeResults = results,
                )
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

        val remoteUrl = track.youtubeUrl ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isResolvingStream = true,
                errorMessage = null,
            )
            try {
                val streamUrl = streamResolver.resolveAudioStreamUrl(remoteUrl)
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
     * Confirma la descarga de una pista, guardando primero cualquier
     * corrección manual de título/artista/álbum -- petición explícita
     * de Miguel Ángel (2026-07-04): con la búsqueda gratuita por
     * yt-dlp los metadatos son más pobres que los de la API oficial,
     * así que antes de descargar (y de que el archivo se cree en
     * disco con ese nombre/carpeta) se puede corregir a mano, en vez
     * de tener que editar y mover el archivo después desde Biblioteca.
     * Álbum vacío = sencillo (igual que en Importar enlace).
     * ---
     * Confirms downloading a track, first saving any manual title/
     * artist/album correction -- explicit request from Miguel Ángel
     * (2026-07-04): with the free yt-dlp search the metadata is
     * poorer than the official API's, so before downloading (and
     * before the file gets created on disk with that name/folder) it
     * can be corrected by hand, instead of having to edit and move the
     * file afterwards from Biblioteca. Empty album = single (same as
     * Importar enlace).
     */
    fun confirmDownload(track: SearchResultTrack, title: String, artist: String, albumRaw: String) {
        val trimmedTitle = title.trim().ifBlank { track.title }
        val trimmedArtist = artist.trim()
            .ifBlank { track.artist ?: track.channelTitle }
        val album = albumRaw.trim().ifBlank { null }

        viewModelScope.launch {
            val updated = track.copy(
                title = trimmedTitle,
                artist = trimmedArtist,
                album = album,
            )
            searchResultTrackRepository.update(updated)
            downloadQueueManager.enqueue(
                youtubeId = updated.youtubeId,
                title = updated.title,
                artist = updated.artist ?: updated.channelTitle,
                album = updated.album,
            )
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

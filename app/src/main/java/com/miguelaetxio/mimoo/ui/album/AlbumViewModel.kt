package com.miguelaetxio.mimoo.ui.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.FavoriteAlbumRepository
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import com.miguelaetxio.mimoo.data.playback.StreamResolver
import com.miguelaetxio.mimoo.data.remote.AlbumCandidate
import com.miguelaetxio.mimoo.data.remote.AlbumMatchRepository
import com.miguelaetxio.mimoo.data.remote.AlbumTrackMatch
import com.miguelaetxio.mimoo.data.remote.dto.TrackDto
import com.miguelaetxio.mimoo.util.SearchNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumUiState(
    val artistName: String = "",
    val albumName: String = "",
    val isLoading: Boolean = true,
    val candidate: AlbumCandidate? = null,
    val matches: List<AlbumTrackMatch> = emptyList(),
    val notFound: Boolean = false,
    val errorMessage: String? = null,
    val isFavorite: Boolean = false,
    // youtubeId -> estado de descarga en vivo, mismo patrón que
    // AlbumSearchViewModel (PASO 6b Parte 2).
    val downloadStatusByYoutubeId: Map<String, DownloadStatus> = emptyMap(),
    val isResolvingPlayback: Boolean = false,
)

/**
 * ViewModel de AlbumScreen (H12, S018): a diferencia de
 * AlbumSearchViewModel (H05), que exige al usuario elegir un release
 * de una lista de candidatos, aquí artista+álbum ya llegan exactos
 * por navegación (desde ArtistScreen o desde la búsqueda unificada),
 * así que la resolución del release es automática -- se reutiliza
 * AlbumMatchRepository entero (searchAlbumCandidates + matchAlbumTracks),
 * sin repetir su lógica.
 * ---
 * AlbumScreen's ViewModel (H12, S018): unlike AlbumSearchViewModel
 * (H05), which requires the user to pick a release from a candidate
 * list, here artist+album already arrive exact via navigation (from
 * ArtistScreen or unified search), so release resolution is automatic
 * -- AlbumMatchRepository is reused wholesale (searchAlbumCandidates +
 * matchAlbumTracks), without repeating its logic.
 */
@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val albumMatchRepository: AlbumMatchRepository,
    private val favoriteAlbumRepository: FavoriteAlbumRepository,
    private val searchResultTrackRepository: SearchResultTrackRepository,
    private val downloadQueueManager: DownloadQueueManager,
    private val streamResolver: StreamResolver,
    private val playerManager: PlayerManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val artistName: String =
        checkNotNull(savedStateHandle.get<String>("artistName")) {
            "AlbumViewModel requires an artistName nav argument"
        }
    private val albumName: String =
        checkNotNull(savedStateHandle.get<String>("albumName")) {
            "AlbumViewModel requires an albumName nav argument"
        }

    private val _uiState = MutableStateFlow(
        AlbumUiState(artistName = artistName, albumName = albumName),
    )
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    init {
        resolve()
    }

    private fun resolve() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val candidates = albumMatchRepository.searchAlbumCandidates(
                    artist = artistName,
                    album = albumName,
                )
                val candidate = pickBestCandidate(candidates)
                if (candidate == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, notFound = true)
                    return@launch
                }
                _uiState.value = _uiState.value.copy(candidate = candidate)

                val matches = albumMatchRepository.matchAlbumTracks(
                    mbid = candidate.mbid,
                    artist = candidate.artist ?: artistName,
                    album = candidate.title,
                )
                _uiState.value = _uiState.value.copy(isLoading = false, matches = matches)
                refreshFavorite()
                refreshDownloadStatus()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Error al resolver el álbum",
                )
            }
        }
    }

    /**
     * De entre los candidatos que coinciden por título normalizado con
     * `albumName`, prefiere el que además coincide de artista (evita
     * confundir álbumes homónimos de artistas distintos); si ninguno
     * coincide de artista, se queda con el primer candidato de título
     * coincidente; si ninguno coincide ni de título, cae al primer
     * resultado general de MusicBrainz (mismo comportamiento tolerante
     * que ya tenía AlbumSearchViewModel al no forzar coincidencia
     * exacta).
     * ---
     * Among candidates matching `albumName` by normalized title,
     * prefers the one that also matches by artist (avoids confusing
     * homonymous albums from different artists); if none matches by
     * artist, keeps the first title-matching candidate; if none
     * matches by title either, falls back to MusicBrainz's first
     * general result (same tolerant behavior AlbumSearchViewModel
     * already had by not forcing an exact match).
     */
    private fun pickBestCandidate(candidates: List<AlbumCandidate>): AlbumCandidate? {
        val normalizedAlbum = SearchNormalizer.normalize(albumName)
        val normalizedArtist = SearchNormalizer.normalizeArtistName(artistName)
        val titleMatches = candidates.filter {
            SearchNormalizer.normalize(it.title) == normalizedAlbum
        }
        val exact = titleMatches.firstOrNull { candidate ->
            candidate.artist?.let {
                SearchNormalizer.normalizeArtistName(it) == normalizedArtist
            } == true
        }
        return exact ?: titleMatches.firstOrNull() ?: candidates.firstOrNull()
    }

    private suspend fun refreshFavorite() {
        val candidate = _uiState.value.candidate ?: return
        val favorites = favoriteAlbumRepository.getAll()
        // getAll() es un Flow -- una sola lectura puntual con
        // kotlinx.coroutines.flow.first(), mismo criterio que el resto
        // de comprobaciones de favorito puntuales de la app (no hace
        // falta observar en vivo: toggleFavorite() ya actualiza
        // isFavorite localmente sin esperar a Room).
        val isFavorite = favorites.first().any {
            SearchNormalizer.normalizeArtistName(it.artist) ==
                SearchNormalizer.normalizeArtistName(candidate.artist ?: artistName) &&
                SearchNormalizer.normalize(it.album) == SearchNormalizer.normalize(candidate.title)
        }
        _uiState.value = _uiState.value.copy(isFavorite = isFavorite)
    }

    fun toggleFavorite() {
        val candidate = _uiState.value.candidate ?: return
        viewModelScope.launch {
            favoriteAlbumRepository.toggle(candidate.artist ?: artistName, candidate.title)
            _uiState.value = _uiState.value.copy(isFavorite = !_uiState.value.isFavorite)
        }
    }

    private suspend fun refreshDownloadStatus() {
        val youtubeIds = _uiState.value.matches.mapNotNull { it.matchedTrack?.youtubeId }
        if (youtubeIds.isEmpty()) return
        val statusById = youtubeIds.mapNotNull { id ->
            searchResultTrackRepository.getById(id)?.let { id to it.downloadStatus }
        }.toMap()
        _uiState.value = _uiState.value.copy(downloadStatusByYoutubeId = statusById)
    }

    /**
     * Reproduce una pista del álbum -- streaming si no está
     * descargada, archivo local si ya lo está, mismo criterio que
     * SearchViewModel.playTrack().
     * ---
     * Plays one album track -- streaming if not downloaded, local file
     * if it already is, same criterion as SearchViewModel.playTrack().
     */
    fun playTrack(match: AlbumTrackMatch) {
        val track = match.matchedTrack ?: return
        val candidate = _uiState.value.candidate
        val artist = candidate?.artist ?: artistName

        viewModelScope.launch {
            val local = searchResultTrackRepository.getById(track.youtubeId)
            if (local?.downloadStatus == DownloadStatus.DONE && local.filePath != null) {
                playerManager.play(
                    local.filePath,
                    track.title,
                    isLocal = true,
                    artist = artist,
                    youtubeId = track.youtubeId,
                    channelTitle = track.channelTitle,
                    artworkUri = local.coverArtUrl ?: track.thumbnailUrl,
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(isResolvingPlayback = true, errorMessage = null)
            try {
                val streamUrl = streamResolver.resolveAudioStreamUrl("https://youtu.be/${track.youtubeId}")
                playerManager.play(
                    streamUrl,
                    track.title,
                    isLocal = false,
                    artist = artist,
                    youtubeId = track.youtubeId,
                    channelTitle = track.channelTitle,
                    artworkUri = track.thumbnailUrl,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Error al resolver el stream",
                )
            } finally {
                _uiState.value = _uiState.value.copy(isResolvingPlayback = false)
            }
        }
    }

    /**
     * "Reproducir álbum" -- resuelve el stream de cada pista emparejada
     * (secuencial, misma limitación de coste-cero de yt-dlp que el
     * resto de la app) y las inserta de golpe con
     * PlayerManager.playQueue(), que ya decide dónde insertarlas en la
     * cola de sesión sin sustituirla (ver comentario de clase de
     * PlayerManager). Pistas ya descargadas reproducen desde el
     * archivo local, sin resolver stream para ellas.
     * ---
     * "Play album" -- resolves the stream of each matched track
     * (sequential, same zero-cost yt-dlp limitation as the rest of the
     * app) and inserts them all at once via PlayerManager.playQueue(),
     * which already decides where to insert them in the session queue
     * without replacing it (see PlayerManager's class comment). Already
     * downloaded tracks play from the local file, without resolving a
     * stream for them.
     */
    fun playAlbum() {
        val candidate = _uiState.value.candidate
        val artist = candidate?.artist ?: artistName
        val matchedTracks = _uiState.value.matches.mapNotNull { it.matchedTrack }
        if (matchedTracks.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResolvingPlayback = true, errorMessage = null)
            try {
                val items = matchedTracks.map { track ->
                    val local = searchResultTrackRepository.getById(track.youtubeId)
                    if (local?.downloadStatus == DownloadStatus.DONE && local.filePath != null) {
                        QueueItem(
                            local.filePath,
                            track.title,
                            isLocal = true,
                            artist = artist,
                            youtubeId = track.youtubeId,
                            channelTitle = track.channelTitle,
                        )
                    } else {
                        val streamUrl = streamResolver
                            .resolveAudioStreamUrl("https://youtu.be/${track.youtubeId}")
                        QueueItem(
                            streamUrl,
                            track.title,
                            isLocal = false,
                            artist = artist,
                            youtubeId = track.youtubeId,
                            channelTitle = track.channelTitle,
                        )
                    }
                }
                playerManager.playQueue(items)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Error al reproducir el álbum",
                )
            } finally {
                _uiState.value = _uiState.value.copy(isResolvingPlayback = false)
            }
        }
    }

    /**
     * Descarga una pista suelta del álbum -- primero la registra en
     * search_result_tracks (DownloadWorker necesita esa fila), luego
     * la encola, mismo patrón que AlbumSearchViewModel.importAlbum()
     * pero para una sola pista.
     * ---
     * Downloads a single album track -- first registers it in
     * search_result_tracks (DownloadWorker needs that row), then
     * enqueues it, same pattern as AlbumSearchViewModel.importAlbum()
     * but for a single track.
     */
    fun downloadTrack(match: AlbumTrackMatch) {
        val track = match.matchedTrack ?: return
        val candidate = _uiState.value.candidate
        val artist = candidate?.artist ?: artistName
        val album = candidate?.title ?: albumName

        viewModelScope.launch {
            cacheAndEnqueue(track, artist, album, match.position - 1)
            refreshDownloadStatus()
        }
    }

    /** "Descargar álbum" -- todas las pistas emparejadas, mismo mecanismo que AlbumSearchViewModel.importAlbum(). */
    fun downloadAlbum() {
        val candidate = _uiState.value.candidate
        val artist = candidate?.artist ?: artistName
        val album = candidate?.title ?: albumName
        val matches = _uiState.value.matches

        viewModelScope.launch {
            matches.forEachIndexed { index, match ->
                match.matchedTrack?.let { track ->
                    cacheAndEnqueue(track, artist, album, index)
                }
            }
            refreshDownloadStatus()
        }
    }

    private suspend fun cacheAndEnqueue(
        track: TrackDto,
        artist: String,
        album: String,
        trackPosition: Int,
    ) {
        searchResultTrackRepository.cacheSearchResults(
            listOf(
                SearchResultTrack(
                    youtubeId = track.youtubeId,
                    title = track.title,
                    channelTitle = track.channelTitle,
                    durationSeconds = track.durationSeconds,
                    thumbnailUrl = track.thumbnailUrl,
                    artist = artist,
                    album = album,
                    trackPosition = trackPosition,
                ),
            ),
        )
        downloadQueueManager.enqueue(
            youtubeId = track.youtubeId,
            title = track.title,
            artist = artist,
            album = album,
            trackPosition = trackPosition,
        )
    }
}

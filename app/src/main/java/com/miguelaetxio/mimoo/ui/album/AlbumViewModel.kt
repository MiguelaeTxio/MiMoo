package com.miguelaetxio.mimoo.ui.album

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.backup.MutationOutcome
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
import dagger.hilt.android.qualifiers.ApplicationContext
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
    // S018 FIX -- ver comentario de clase: reemplaza a
    // downloadStatusByYoutubeId (comparaba por el youtubeId recién
    // emparejado, que no coincide con el de la pista ya descargada).
    // Clave = trackPosition 0-indexado (match.position - 1).
    val localTracksByPosition: Map<Int, SearchResultTrack> = emptyMap(),
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
 *
 * FIX S018 (bug real reportado por Miguel Ángel en dispositivo,
 * álbum "Moon Safari" de Air: marcaba "descargar" en todas las
 * pistas pese a que ArtistScreen ya decía "1 álbum completo"):
 * matchAlbumTracks() vuelve a buscar en YouTube CADA VEZ que se abre
 * la pantalla, y el vídeo que empareja ahora puede no ser el mismo
 * (youtubeId distinto) que el que se descargó en su día -- comparar
 * "¿está descargada esta pista?" por ese youtubeId recién resuelto
 * era comparar la pista equivocada. Se cruza ahora por
 * trackPosition (0-indexado, igual que ya hace
 * cacheAndEnqueue()/LibraryViewModel para ordenar el disco), NUNCA
 * por youtubeId -- mismo principio de "cruce por nombre/posición
 * estable, no por dato que puede cambiar" que ya se aplicó en
 * ArtistViewModel para el resumen agregado.
 * ---
 * AlbumScreen's ViewModel (H12, S018): unlike AlbumSearchViewModel
 * (H05), which requires the user to pick a release from a candidate
 * list, here artist+album already arrive exact via navigation (from
 * ArtistScreen or unified search), so release resolution is automatic
 * -- AlbumMatchRepository is reused wholesale (searchAlbumCandidates +
 * matchAlbumTracks), without repeating its logic.
 *
 * S018 FIX (real bug reported by Miguel Ángel on-device, Air's "Moon
 * Safari" album: marked "download" on every track even though
 * ArtistScreen already said "1 complete album"): matchAlbumTracks()
 * searches YouTube again EVERY TIME the screen opens, and the video
 * it matches now may not be the same one (different youtubeId) that
 * was downloaded back when -- comparing "is this track downloaded?"
 * by that freshly-resolved youtubeId was comparing the wrong track.
 * Now cross-referenced by trackPosition (0-indexed, same as
 * cacheAndEnqueue()/LibraryViewModel already use to order the disc),
 * NEVER by youtubeId -- same "cross-reference by stable name/position,
 * never by data that can change" principle already applied in
 * ArtistViewModel for the aggregate summary.
 */
@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val albumMatchRepository: AlbumMatchRepository,
    private val favoriteAlbumRepository: FavoriteAlbumRepository,
    private val searchResultTrackRepository: SearchResultTrackRepository,
    private val downloadQueueManager: DownloadQueueManager,
    private val streamResolver: StreamResolver,
    private val playerManager: PlayerManager,
    private val autoSyncPusher: AutoSyncPusher,
    @ApplicationContext private val appContext: Context,
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
                refreshLocalTracks()
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

    /** Bug real (2026-08-02, ver comentario de ArtistViewModel.toggleFavorite()): pasa ahora por AutoSyncPusher. */
    fun toggleFavorite() {
        val candidate = _uiState.value.candidate ?: return
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(appContext) {
                favoriteAlbumRepository.toggle(candidate.artist ?: artistName, candidate.title)
            }
            if (outcome is MutationOutcome.Success) {
                _uiState.value = _uiState.value.copy(isFavorite = !_uiState.value.isFavorite)
            }
        }
    }

    /**
     * S018 FIX -- ver comentario de clase. Trae TODAS las pistas
     * locales de este artista+álbum (cruce por nombre normalizado,
     * mismo criterio que refreshFavorite()/ArtistViewModel) y las
     * indexa por trackPosition (0-indexado). Pistas locales sin
     * trackPosition (import legacy, no debería darse en álbumes
     * importados por esta pantalla) se descartan silenciosamente --
     * no hay forma fiable de emparejarlas a una posición concreta.
     * ---
     * S018 FIX -- see class comment. Fetches ALL local tracks for this
     * artist+album (cross-referenced by normalized name, same
     * criterion as refreshFavorite()/ArtistViewModel) and indexes them
     * by trackPosition (0-indexed). Local tracks without a
     * trackPosition (legacy import, shouldn't happen for albums
     * imported through this screen) are silently dropped -- there's no
     * reliable way to match them to a specific position.
     */
    private suspend fun refreshLocalTracks() {
        val candidate = _uiState.value.candidate ?: return
        val normalizedArtist = SearchNormalizer.normalizeArtistName(candidate.artist ?: artistName)
        val normalizedAlbum = SearchNormalizer.normalize(candidate.title)

        val localTracks = searchResultTrackRepository.getAllOnce()
            .filter { track ->
                val trackAlbum = track.album ?: return@filter false
                SearchNormalizer.normalizeArtistName(track.artist ?: track.channelTitle) == normalizedArtist &&
                    SearchNormalizer.normalize(trackAlbum) == normalizedAlbum
            }

        val byPosition = localTracks
            .mapNotNull { track -> track.trackPosition?.let { it to track } }
            .toMap()
        _uiState.value = _uiState.value.copy(localTracksByPosition = byPosition)
    }

    /**
     * Reproduce una pista del álbum -- SIEMPRE mira primero si hay
     * fila local para esta posición (S018 FIX, cruce por posición, no
     * por el youtubeId recién emparejado). Si la hay y está
     * descargada, reproduce ese archivo local con SUS propios datos
     * (youtubeId/carátula), sin tocar la red. Si no, streaming con la
     * pista recién emparejada online.
     * ---
     * Plays one album track -- ALWAYS checks first whether there's a
     * local row for this position (S018 FIX, cross-referenced by
     * position, not by the freshly-matched youtubeId). If there is and
     * it's downloaded, plays that local file with ITS OWN data
     * (youtubeId/artwork), without touching the network. Otherwise,
     * streams the freshly-matched online track.
     */
    fun playTrack(match: AlbumTrackMatch) {
        val candidate = _uiState.value.candidate
        val artist = candidate?.artist ?: artistName
        val local = _uiState.value.localTracksByPosition[match.position - 1]

        if (local?.downloadStatus == DownloadStatus.DONE && local.filePath != null) {
            playerManager.play(
                local.filePath,
                local.title,
                isLocal = true,
                artist = artist,
                youtubeId = local.youtubeId,
                channelTitle = local.channelTitle,
                artworkUri = local.coverArtUrl,
            )
            return
        }

        val track = match.matchedTrack ?: return
        viewModelScope.launch {
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
     * "Reproducir álbum" -- mismo criterio local-por-posición que
     * playTrack() para cada pista, streaming solo para las que de
     * verdad no están descargadas, insertadas de golpe con
     * PlayerManager.playQueue() (inserción, nunca sustituye la cola de
     * sesión).
     * ---
     * "Play album" -- same local-by-position criterion as playTrack()
     * for each track, streaming only for the ones genuinely not
     * downloaded, inserted all at once via PlayerManager.playQueue()
     * (insertion, never replaces the session queue).
     */
    fun playAlbum() {
        val candidate = _uiState.value.candidate
        val artist = candidate?.artist ?: artistName
        val matches = _uiState.value.matches
        val localByPosition = _uiState.value.localTracksByPosition
        if (matches.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResolvingPlayback = true, errorMessage = null)
            try {
                val items = matches.mapNotNull { match ->
                    val local = localByPosition[match.position - 1]
                    if (local?.downloadStatus == DownloadStatus.DONE && local.filePath != null) {
                        QueueItem(
                            local.filePath,
                            local.title,
                            isLocal = true,
                            artist = artist,
                            youtubeId = local.youtubeId,
                            channelTitle = local.channelTitle,
                        )
                    } else {
                        val track = match.matchedTrack ?: return@mapNotNull null
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
     * Descarga una pista suelta del álbum -- si ya hay fila local para
     * esta posición (S018 FIX), no hace NADA (ya está, evita duplicar
     * descarga bajo un youtubeId distinto). Si no, la registra y
     * encola, mismo patrón que AlbumSearchViewModel.importAlbum() pero
     * para una sola pista.
     * ---
     * Downloads a single album track -- if there's already a local row
     * for this position (S018 FIX), does NOTHING (already there, avoids
     * duplicating the download under a different youtubeId). If not,
     * registers and enqueues it, same pattern as
     * AlbumSearchViewModel.importAlbum() but for a single track.
     */
    fun downloadTrack(match: AlbumTrackMatch) {
        if (_uiState.value.localTracksByPosition[match.position - 1]?.downloadStatus == DownloadStatus.DONE) {
            return
        }
        val track = match.matchedTrack ?: return
        val candidate = _uiState.value.candidate
        val artist = candidate?.artist ?: artistName
        val album = candidate?.title ?: albumName

        viewModelScope.launch {
            cacheAndEnqueue(track, artist, album, match.position - 1)
            refreshLocalTracks()
        }
    }

    /**
     * "Descargar álbum" -- solo encola las pistas que de verdad faltan
     * (S018 FIX): las que ya tienen fila local DONE para su posición
     * se saltan, para no duplicar descargas.
     * ---
     * "Download album" -- only enqueues the tracks that are genuinely
     * missing (S018 FIX): the ones that already have a local DONE row
     * for their position are skipped, to avoid duplicate downloads.
     */
    fun downloadAlbum() {
        val candidate = _uiState.value.candidate
        val artist = candidate?.artist ?: artistName
        val album = candidate?.title ?: albumName
        val matches = _uiState.value.matches
        val localByPosition = _uiState.value.localTracksByPosition

        viewModelScope.launch {
            for (match in matches) {
                if (localByPosition[match.position - 1]?.downloadStatus == DownloadStatus.DONE) continue
                match.matchedTrack?.let { track ->
                    cacheAndEnqueue(track, artist, album, match.position - 1)
                }
            }
            refreshLocalTracks()
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

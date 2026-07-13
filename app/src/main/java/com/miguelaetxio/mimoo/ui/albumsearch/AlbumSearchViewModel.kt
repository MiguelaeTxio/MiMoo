package com.miguelaetxio.mimoo.ui.albumsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.remote.AlbumCandidate
import com.miguelaetxio.mimoo.data.remote.AlbumMatchRepository
import com.miguelaetxio.mimoo.data.remote.AlbumTrackMatch
import com.miguelaetxio.mimoo.data.remote.ExternalLinkResolver
import com.miguelaetxio.mimoo.data.remote.dto.TrackDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumSearchUiState(
    val artist: String = "",
    val album: String = "",
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    // PASO 6d: lista de releases candidatos (hasta 20) devueltos por
    // MusicBrainz -- el usuario elige uno antes de pedir tracklist ni
    // tocar YouTube. matches queda vacio hasta que hay seleccion.
    val candidates: List<AlbumCandidate> = emptyList(),
    val selectedCandidate: AlbumCandidate? = null,
    val isLoadingTracks: Boolean = false,
    val matches: List<AlbumTrackMatch> = emptyList(),
    val manualSearchCandidates: List<TrackDto> = emptyList(),
    val isSearchingManualCandidates: Boolean = false,
    val importedCount: Int? = null,
    // PASO 6b Parte 2: downloadStatus en vivo por youtubeId de las
    // pistas ya importadas, para pintarlo en la misma lista de
    // matches sin esperar a entrar en Biblioteca.
    val importedStatus: Map<String, DownloadStatus> = emptyMap(),
)

/**
 * ViewModel for the album search screen (Hito 05): search a full
 * album on MusicBrainz, review the automatic YouTube duration match
 * per track, correct it manually where needed, and import the result
 * into search_result_tracks.
 * ---
 * ViewModel de la pantalla de búsqueda de álbum (Hito 05): buscar un
 * álbum completo en MusicBrainz, revisar el emparejamiento automático
 * por duración con YouTube por pista, corregirlo manualmente donde
 * haga falta, e importar el resultado a search_result_tracks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlbumSearchViewModel @Inject constructor(
    private val albumMatchRepository: AlbumMatchRepository,
    private val externalLinkResolver: ExternalLinkResolver,
    private val searchResultTrackRepository: SearchResultTrackRepository,
    private val downloadQueueManager: DownloadQueueManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumSearchUiState())
    val uiState: StateFlow<AlbumSearchUiState> = _uiState.asStateFlow()

    // PASO 6b Parte 2: mismo patron que SearchViewModel._currentYoutubeIds
    // -- se re-emite el conjunto de ids importados y se observa Room en
    // vivo para reflejar downloadStatus sin polling.
    private val _importedYoutubeIds = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch {
            _importedYoutubeIds
                .flatMapLatest { ids ->
                    if (ids.isEmpty()) {
                        flowOf(emptyMap())
                    } else {
                        searchResultTrackRepository.getAll().map { all ->
                            val idSet = ids.toSet()
                            all.filter { it.youtubeId in idSet }
                                .associate { it.youtubeId to it.downloadStatus }
                        }
                    }
                }
                .collect { statusByYoutubeId ->
                    _uiState.value = _uiState.value.copy(importedStatus = statusByYoutubeId)
                }
        }
    }

    fun onArtistChange(artist: String) {
        _uiState.value = _uiState.value.copy(artist = artist)
    }

    fun onAlbumChange(album: String) {
        _uiState.value = _uiState.value.copy(album = album)
    }

    fun searchAlbum() {
        // PASO 6a: ya no se exigen ambos campos -- artista solo, album
        // solo, o ambos son validos (caso real: obras clasicas donde
        // solo se conoce el titulo, ej. "Novena Sinfonia" sin autor).
        val artist = _uiState.value.artist.trim().ifBlank { null }
        val album = _uiState.value.album.trim().ifBlank { null }
        if (artist == null && album == null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                errorMessage = null,
                candidates = emptyList(),
                selectedCandidate = null,
                matches = emptyList(),
                importedCount = null,
            )
            try {
                // PASO 6d: primero se lista, no se empareja de golpe --
                // peticion explicita de Miguel Angel tras ver que un
                // solo termino ("Beethoven", "Sinfonia") coincidia con
                // demasiados releases distintos para asumir el primero
                // sin mostrarselos.
                val candidates = albumMatchRepository.searchAlbumCandidates(
                    artist = artist,
                    album = album,
                )
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    candidates = candidates,
                    errorMessage = if (candidates.isEmpty()) {
                        "No se encontraron álbumes para esa búsqueda."
                    } else {
                        null
                    },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    errorMessage = e.message ?: "Error al buscar el álbum",
                )
            }
        }
    }

    /**
     * User picked one candidate from the list (PASO 6d) — now, and
     * only now, its tracklist is fetched and matched against YouTube.
     * artist used for the YouTube per-track queries comes from the
     * candidate's own artist-credit, not the raw search field: more
     * reliable when the user searched by album title only (e.g.
     * "Novena Sinfonía" without typing "Beethoven").
     * ---
     * El usuario eligió un candidato de la lista (PASO 6d) — solo
     * ahora se pide su tracklist y se empareja con YouTube. El artista
     * usado en las búsquedas de YouTube por pista viene del propio
     * artist-credit del candidato, no del campo de búsqueda tal cual:
     * más fiable cuando se buscó solo por título de álbum (p. ej.
     * "Novena Sinfonía" sin escribir "Beethoven").
     */
    fun selectCandidate(candidate: AlbumCandidate) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedCandidate = candidate,
                isLoadingTracks = true,
                errorMessage = null,
                matches = emptyList(),
                importedCount = null,
            )
            try {
                val matches = albumMatchRepository.matchAlbumTracks(
                    mbid = candidate.mbid,
                    artist = candidate.artist,
                    album = candidate.title,
                )
                _uiState.value = _uiState.value.copy(
                    isLoadingTracks = false,
                    matches = matches,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingTracks = false,
                    errorMessage = e.message ?: "Error al obtener el tracklist",
                )
            }
        }
    }

    /** Returns from the tracklist view to the candidate list (PASO 6d). */
    fun backToCandidates() {
        _uiState.value = _uiState.value.copy(
            selectedCandidate = null,
            matches = emptyList(),
            errorMessage = null,
        )
    }

    /**
     * Searches YouTube candidates for manual correction of one track
     * (PASO 4). Free-text query rather than "$artist $mbTitle" — the
     * automatic match already tried that combination, so a manual
     * correction is presumably needed because it didn't work well.
     * ---
     * Busca candidatos de YouTube para corrección manual de una pista
     * (PASO 4). Texto libre en vez de "$artist $mbTitle" — el
     * emparejamiento automático ya probó esa combinación, así que una
     * corrección manual presumiblemente hace falta porque no
     * funcionó bien.
     */
    fun searchManualCandidates(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearchingManualCandidates = true,
                manualSearchCandidates = emptyList(),
            )
            val candidates = try {
                externalLinkResolver.searchYoutube(trimmed).tracks.map { entry ->
                    TrackDto(
                        youtubeId = entry.youtubeId,
                        title = entry.title,
                        durationSeconds = entry.durationSeconds,
                        thumbnailUrl = entry.thumbnailUrl,
                        channelTitle = entry.channelTitle,
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(
                isSearchingManualCandidates = false,
                manualSearchCandidates = candidates,
            )
        }
    }

    fun clearManualCandidates() {
        _uiState.value = _uiState.value.copy(manualSearchCandidates = emptyList())
    }

    /**
     * Applies a manually chosen candidate to one track (PASO 4).
     *
     * S010 -- identifica la pista objetivo por IGUALDAD DE REFERENCIA
     * (el propio objeto AlbumTrackMatch, no su position) -- position
     * es un campo de MusicBrainz por disco, no global (ver
     * AlbumMatchRepository: .media.flatMap { it.tracks }), así que en
     * una edición de varios CDs corregir "pista 1" a mano corregía A
     * LA VEZ la pista 1 del disco 1 Y la pista 1 del disco 2. El
     * objeto que llega aquí es siempre la instancia exacta que ya
     * estaba en uiState.matches (viene de ahí mismo, sin copiar), así
     * que compararlo por referencia identifica sin ambigüedad la fila
     * correcta.
     * ---
     * S010 -- identifies the target track by REFERENCE EQUALITY (the
     * AlbumTrackMatch object itself, not its position) -- position is
     * a per-disc MusicBrainz field, not global, so on a multi-disc
     * release manually correcting "track 1" corrected disc 1 track 1
     * AND disc 2 track 1 at once. The object arriving here is always
     * the exact instance already in uiState.matches, so comparing by
     * reference unambiguously identifies the right row.
     */
    fun applyManualMatch(target: AlbumTrackMatch, candidate: TrackDto) {
        val updated = _uiState.value.matches.map { match ->
            if (match === target) {
                match.copy(matchedTrack = candidate, isAutoMatched = false)
            } else {
                match
            }
        }
        _uiState.value = _uiState.value.copy(
            matches = updated,
            manualSearchCandidates = emptyList(),
        )
    }

    /**
     * Imports every matched track into search_result_tracks
     * (PASO 5). artist/album are fixed to the selected candidate's own
     * values (PASO 6d), not to matchedTrack.channelTitle, so the album
     * groups correctly in Biblioteca regardless of which channel
     * actually uploaded each video. Tracks left unmatched
     * (matchedTrack == null) are simply excluded rather than blocking
     * the rest of the import.
     * ---
     * Importa cada pista emparejada a search_result_tracks (PASO 5).
     * artist/album se fijan a los valores del candidato elegido (PASO
     * 6d), no a matchedTrack.channelTitle, para que el álbum se agrupe
     * bien en Biblioteca sin importar qué canal subió cada vídeo. Las
     * pistas sin emparejar (matchedTrack == null) simplemente se
     * excluyen en vez de bloquear el resto de la importación.
     */
    fun importAlbum() {
        val selected = _uiState.value.selectedCandidate
        // Fallback a los campos de texto solo por robustez -- en el
        // flujo normal siempre hay selectedCandidate, porque matches
        // no se puebla hasta selectCandidate().
        val artist = selected?.artist ?: _uiState.value.artist.trim()
        val album = selected?.title ?: _uiState.value.album.trim()
        val tracks = _uiState.value.matches.mapIndexedNotNull { index, match ->
            match.matchedTrack?.let { candidate ->
                SearchResultTrack(
                    youtubeId = candidate.youtubeId,
                    title = match.mbTitle,
                    channelTitle = candidate.channelTitle,
                    durationSeconds = candidate.durationSeconds,
                    thumbnailUrl = candidate.thumbnailUrl,
                    artist = artist,
                    album = album,
                    // Posición real dentro de matches -- orden que ya
                    // viene de MusicBrainz (release track order), no
                    // se recalcula tras descartar pistas sin
                    // emparejar, así que el hueco de una pista sin
                    // match no desplaza la posición de las demás.
                    trackPosition = index,
                )
            }
        }
        if (tracks.isEmpty()) return

        viewModelScope.launch {
            searchResultTrackRepository.cacheSearchResults(tracks)
            // PASO 6b Parte 1: autodescarga al importar (peticion
            // explicita de producto -- la cuota diaria de YouTube Data
            // API hace que no se quiera depender de rebuscar mas
            // tarde). Mismo mecanismo que
            // SearchViewModel.requestDownload(), alcance limitado a
            // album importado -- no se generaliza a SearchScreen sin
            // confirmar con Miguel Angel.
            tracks.forEach { track ->
                downloadQueueManager.enqueue(
                    youtubeId = track.youtubeId,
                    title = track.title,
                    artist = track.artist ?: track.channelTitle,
                    album = track.album,
                    trackPosition = track.trackPosition,
                )
            }
            // PASO 6b Parte 2: dispara la observacion en vivo de Room
            // para estas pistas -- el init{} las recoge y actualiza
            // importedStatus segun avance DownloadWorker.
            _importedYoutubeIds.value = tracks.map { it.youtubeId }
            _uiState.value = _uiState.value.copy(importedCount = tracks.size)
        }
    }

    /** Dismisses the "album imported" confirmation dialog. */
    fun dismissImportedDialog() {
        _uiState.value = _uiState.value.copy(importedCount = null)
    }
}

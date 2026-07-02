package com.miguelaetxio.mimoo.ui.albumsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.BuildConfig
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.remote.AlbumMatchRepository
import com.miguelaetxio.mimoo.data.remote.AlbumTrackMatch
import com.miguelaetxio.mimoo.data.remote.YouTubeRepository
import com.miguelaetxio.mimoo.data.remote.dto.TrackDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumSearchUiState(
    val artist: String = "",
    val album: String = "",
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val matches: List<AlbumTrackMatch> = emptyList(),
    val manualSearchCandidates: List<TrackDto> = emptyList(),
    val isSearchingManualCandidates: Boolean = false,
    val importedCount: Int? = null,
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
@HiltViewModel
class AlbumSearchViewModel @Inject constructor(
    private val albumMatchRepository: AlbumMatchRepository,
    private val youTubeRepository: YouTubeRepository,
    private val searchResultTrackRepository: SearchResultTrackRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumSearchUiState())
    val uiState: StateFlow<AlbumSearchUiState> = _uiState.asStateFlow()

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
                matches = emptyList(),
                importedCount = null,
            )
            try {
                val matches = albumMatchRepository.matchAlbum(
                    artist = artist,
                    album = album,
                    youtubeApiKey = BuildConfig.YOUTUBE_API_KEY,
                )
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    matches = matches,
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
                youTubeRepository.search(trimmed, BuildConfig.YOUTUBE_API_KEY)
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

    /** Applies a manually chosen candidate to one track (PASO 4). */
    fun applyManualMatch(position: Int, candidate: TrackDto) {
        val updated = _uiState.value.matches.map { match ->
            if (match.position == position) {
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
     * (PASO 5). artist/album are fixed to the searched values, not
     * to matchedTrack.channelTitle, so the album groups correctly in
     * Biblioteca regardless of which channel actually uploaded each
     * video. Tracks left unmatched (matchedTrack == null) are simply
     * excluded rather than blocking the rest of the import.
     * ---
     * Importa cada pista emparejada a search_result_tracks (PASO 5).
     * artist/album se fijan a los valores buscados, no a
     * matchedTrack.channelTitle, para que el álbum se agrupe bien en
     * Biblioteca sin importar qué canal subió cada vídeo. Las pistas
     * sin emparejar (matchedTrack == null) simplemente se excluyen en
     * vez de bloquear el resto de la importación.
     */
    fun importAlbum() {
        val artist = _uiState.value.artist.trim()
        val album = _uiState.value.album.trim()
        val tracks = _uiState.value.matches.mapNotNull { match ->
            match.matchedTrack?.let { candidate ->
                SearchResultTrack(
                    youtubeId = candidate.youtubeId,
                    title = match.mbTitle,
                    channelTitle = candidate.channelTitle,
                    durationSeconds = candidate.durationSeconds,
                    thumbnailUrl = candidate.thumbnailUrl,
                    artist = artist,
                    album = album,
                )
            }
        }
        if (tracks.isEmpty()) return

        viewModelScope.launch {
            searchResultTrackRepository.cacheSearchResults(tracks)
            _uiState.value = _uiState.value.copy(importedCount = tracks.size)
        }
    }

    /** Dismisses the "album imported" confirmation dialog. */
    fun dismissImportedDialog() {
        _uiState.value = _uiState.value.copy(importedCount = null)
    }
}

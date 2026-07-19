package com.miguelaetxio.mimoo.ui.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.repository.FavoriteArtistRepository
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.remote.ArtistDirectoryRepository
import com.miguelaetxio.mimoo.data.remote.ArtistResolution
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzArtistSummary
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzReleaseGroup
import com.miguelaetxio.mimoo.util.SearchNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistUiState(
    val artistName: String = "",
    val isLoading: Boolean = true,
    val canonicalName: String? = null,
    val mbid: String? = null,
    // No nulo mientras se espera que el usuario elija en el diálogo de
    // homónimos reales (S018, ver DOCS/ANNEX_H12.md punto 4).
    val disambiguationCandidates: List<MusicBrainzArtistSummary>? = null,
    val disambiguationKey: String? = null,
    val notFound: Boolean = false,
    val errorMessage: String? = null,
    val albums: List<MusicBrainzReleaseGroup> = emptyList(),
    val singles: List<MusicBrainzReleaseGroup> = emptyList(),
    val isFavorite: Boolean = false,
    val isLoadingDownloadedCounts: Boolean = false,
    val completeAlbumsCount: Int = 0,
    val partialAlbumsCount: Int = 0,
    val downloadedSinglesCount: Int = 0,
)

/**
 * ViewModel de ArtistScreen (H12, S018): resuelve el nombre de
 * artista recibido por navegación a un MBID de MusicBrainz (con
 * desambiguación de homónimos reales cuando hace falta), lista sus
 * álbumes y sencillos, y calcula favorito/descargado.
 * ---
 * ArtistScreen's ViewModel (H12, S018): resolves the artist name
 * received via navigation to a MusicBrainz MBID (with real-homonym
 * disambiguation when needed), lists their albums and singles, and
 * computes favorite/downloaded state.
 */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val artistDirectoryRepository: ArtistDirectoryRepository,
    private val favoriteArtistRepository: FavoriteArtistRepository,
    private val searchResultTrackRepository: SearchResultTrackRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val artistName: String =
        checkNotNull(savedStateHandle.get<String>("artistName")) {
            "ArtistViewModel requires an artistName nav argument"
        }

    private val _uiState = MutableStateFlow(ArtistUiState(artistName = artistName))
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    init {
        resolve()
    }

    private fun resolve() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                when (val resolution = artistDirectoryRepository.resolveArtist(artistName)) {
                    is ArtistResolution.Resolved -> onResolved(resolution.mbid, resolution.canonicalName)
                    is ArtistResolution.NeedsDisambiguation -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            disambiguationCandidates = resolution.candidates,
                            disambiguationKey = resolution.normalizedNameKey,
                        )
                    }
                    ArtistResolution.NotFound -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, notFound = true)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Error al resolver el artista",
                )
            }
        }
    }

    /** El usuario eligió un candidato en el diálogo de homónimos (S018, punto 4). */
    fun chooseDisambiguationCandidate(candidate: MusicBrainzArtistSummary) {
        val key = _uiState.value.disambiguationKey ?: return
        viewModelScope.launch {
            artistDirectoryRepository.saveDisambiguationChoice(key, candidate.id)
            _uiState.value = _uiState.value.copy(
                disambiguationCandidates = null,
                disambiguationKey = null,
            )
            onResolved(candidate.id, candidate.name)
        }
    }

    private suspend fun onResolved(mbid: String, canonicalName: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            mbid = mbid,
            canonicalName = canonicalName,
        )
        loadCatalog(mbid)
        loadFavoriteAndDownloaded(mbid)
    }

    private suspend fun loadCatalog(mbid: String) {
        try {
            val albums = artistDirectoryRepository.getAlbums(mbid)
            val singles = artistDirectoryRepository.getSingles(mbid)
            _uiState.value = _uiState.value.copy(albums = albums, singles = singles)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = e.message ?: "Error al cargar el catálogo del artista",
            )
        }
    }

    /**
     * Sección "Descargado" de la página de artista (roadmap punto 7,
     * S018): álbumes completos/parciales y sencillos sueltos
     * descargados. El cruce con lo local es por NOMBRE, mismo criterio
     * que el resto de la app (LibraryViewModel, FavoriteAlbum) --
     * nunca por MBID. "Completo" solo se marca cuando
     * ArtistDirectoryRepository.getTrackCount() (solo MusicBrainz)
     * devuelve un número conocido y coincide con lo descargado; si no
     * se puede resolver, el álbum queda como "parcial" en vez de
     * asumir "completo" por defecto.
     * ---
     * Artist page's "Downloaded" section (roadmap point 7, S018):
     * complete/partial albums and downloaded loose singles. The cross-
     * reference with local data is by NAME, same criterion as the rest
     * of the app (LibraryViewModel, FavoriteAlbum) -- never by MBID.
     * "Complete" is only marked when
     * ArtistDirectoryRepository.getTrackCount() (MusicBrainz only)
     * returns a known number and it matches what's downloaded; if it
     * can't be resolved, the album stays "partial" instead of
     * defaulting to "complete".
     */
    private suspend fun loadFavoriteAndDownloaded(mbid: String) {
        val isFavorite = favoriteArtistRepository.isFavorite(artistName)
        _uiState.value = _uiState.value.copy(isFavorite = isFavorite, isLoadingDownloadedCounts = true)

        val normalizedArtist = SearchNormalizer.normalize(artistName)
        val localTracks = searchResultTrackRepository.getAllOnce()
            .filter { track ->
                val trackArtist = track.artist ?: track.channelTitle
                SearchNormalizer.normalize(trackArtist) == normalizedArtist
            }

        val localTracksByAlbum: Map<String, Int> = localTracks
            .mapNotNull { it.album }
            .groupingBy { SearchNormalizer.normalize(it) }
            .eachCount()

        var completeCount = 0
        var partialCount = 0
        val currentAlbums = _uiState.value.albums
        for (albumGroup in currentAlbums) {
            val normalizedTitle = SearchNormalizer.normalize(albumGroup.title)
            val localCount = localTracksByAlbum[normalizedTitle] ?: continue
            val totalCount = artistDirectoryRepository.getTrackCount(albumGroup.id)
            if (totalCount != null && localCount >= totalCount) {
                completeCount++
            } else {
                partialCount++
            }
        }

        val downloadedSinglesCount = localTracks.count { it.album == null }

        _uiState.value = _uiState.value.copy(
            isLoadingDownloadedCounts = false,
            completeAlbumsCount = completeCount,
            partialAlbumsCount = partialCount,
            downloadedSinglesCount = downloadedSinglesCount,
        )
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            favoriteArtistRepository.toggle(artistName)
            _uiState.value = _uiState.value.copy(isFavorite = !_uiState.value.isFavorite)
        }
    }
}

package com.miguelaetxio.mimoo.ui.artist

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.local.repository.FavoriteArtistRepository
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.remote.ArtistDirectoryRepository
import com.miguelaetxio.mimoo.data.remote.ArtistResolution
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzArtistSummary
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzReleaseGroup
import com.miguelaetxio.mimoo.util.SearchNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado de descarga de UN álbum concreto -- distinto del resumen
 * agregado (completeAlbumsCount/partialAlbumsCount): esto es lo que
 * pinta la marca en cada fila de la lista (petición explícita de
 * Miguel Ángel, S018: "si entro en la página, le doy a Beastie
 * Boys... me muestra de los discos que tengo, si tengo alguno
 * descargado, me lo muestra").
 * ---
 * Download status of ONE specific album -- distinct from the
 * aggregate summary (completeAlbumsCount/partialAlbumsCount): this is
 * what paints the badge on each list row (explicit request from
 * Miguel Ángel, S018: "if I open the page, tap Beastie Boys... it
 * shows me which of the albums I have, if I have any downloaded, it
 * shows me").
 */
enum class AlbumDownloadStatus { NONE, PARTIAL, COMPLETE }

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
    // S018 -- por-fila, ver AlbumDownloadStatus. Claves = release-group
    // id de MusicBrainz. Un álbum ausente del mapa equivale a NONE.
    val albumDownloadStatusById: Map<String, AlbumDownloadStatus> = emptyMap(),
    val downloadedSingleIds: Set<String> = emptySet(),
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
    private val autoSyncPusher: AutoSyncPusher,
    @ApplicationContext private val appContext: Context,
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
        val albumStatusById = mutableMapOf<String, AlbumDownloadStatus>()
        val currentAlbums = _uiState.value.albums
        for (albumGroup in currentAlbums) {
            val normalizedTitle = SearchNormalizer.normalize(albumGroup.title)
            val localCount = localTracksByAlbum[normalizedTitle]
            if (localCount == null) {
                albumStatusById[albumGroup.id] = AlbumDownloadStatus.NONE
                continue
            }
            val totalCount = artistDirectoryRepository.getTrackCount(albumGroup.id)
            if (totalCount != null && localCount >= totalCount) {
                completeCount++
                albumStatusById[albumGroup.id] = AlbumDownloadStatus.COMPLETE
            } else {
                partialCount++
                albumStatusById[albumGroup.id] = AlbumDownloadStatus.PARTIAL
            }
        }

        val downloadedSinglesLocal = localTracks.filter { it.album == null }
        val downloadedSingleTitles = downloadedSinglesLocal
            .map { SearchNormalizer.normalize(it.title) }
            .toSet()
        val downloadedSingleIds = _uiState.value.singles
            .filter { SearchNormalizer.normalize(it.title) in downloadedSingleTitles }
            .map { it.id }
            .toSet()
        val downloadedSinglesCount = downloadedSinglesLocal.count()

        _uiState.value = _uiState.value.copy(
            isLoadingDownloadedCounts = false,
            completeAlbumsCount = completeCount,
            partialAlbumsCount = partialCount,
            downloadedSinglesCount = downloadedSinglesCount,
            albumDownloadStatusById = albumStatusById,
            downloadedSingleIds = downloadedSingleIds,
        )
    }

    /**
     * Bug real reportado por Miguel Ángel (2026-08-02): un artista
     * marcado como favorito en el móvil no aparecía en la tablet tras
     * sincronizar, ni siquiera después de arreglar lo que viajaba en
     * el bundle -- porque esta mutación NUNCA pasaba por
     * AutoSyncPusher, el punto único obligatorio por el que debe
     * pasar cualquier añadido/borrado de favorito (regla de negocio
     * H07, ver AutoSyncPusher). Se corrige aquí -- mismo patrón que
     * LibraryViewModel.toggleFavoriteAlbum(). Sin conexión, la
     * mutación NO se aplica en absoluto (se descarta en silencio el
     * toggle de UI también, revirtiendo el optimista si hiciera
     * falta -- aquí no hace falta porque se aplica solo si hubo
     * conexión).
     * ---
     * Real bug reported by Miguel Ángel (2026-08-02): an artist marked
     * favorite on the phone didn't show up on the tablet after
     * syncing, even after fixing what traveled in the bundle --
     * because this mutation NEVER went through AutoSyncPusher, the
     * mandatory single point any favorite addition/removal must pass
     * through (H07 business rule, see AutoSyncPusher). Fixed here --
     * same pattern as LibraryViewModel.toggleFavoriteAlbum(). Without
     * connection, the mutation is NOT applied at all (the UI toggle
     * is skipped too -- only applied if there was a connection).
     */
    fun toggleFavorite() {
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(appContext) {
                favoriteArtistRepository.toggle(artistName)
            }
            if (outcome is com.miguelaetxio.mimoo.data.backup.MutationOutcome.Success) {
                _uiState.value = _uiState.value.copy(isFavorite = !_uiState.value.isFavorite)
            }
        }
    }
}

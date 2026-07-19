package com.miguelaetxio.mimoo.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.remote.ArtistDirectoryRepository
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzArtistSummary
import com.miguelaetxio.mimoo.ui.library.sortLetterFor
import com.miguelaetxio.mimoo.util.SearchNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * REDISEÑO S018 (feedback en dispositivo de Miguel Ángel, 2026-07-19):
 * la primera versión de Explorador (solo FavoriteArtist) estaba mal
 * planteada -- "el explorador es el total... debe aparecer todo".
 * Explorador ya NO tiene nivel de "artistas favoritos" propio; cada
 * letra muestra DOS bloques: lo que ya tienes descargado (local,
 * completo, sin paginar) y una muestra paginada de MusicBrainz
 * (scroll infinito), sin repetir lo que ya salió en el bloque local.
 * ---
 * S018 REDESIGN (Miguel Ángel's on-device feedback, 2026-07-19): the
 * first Explorer version (favorite-artists-only) was the wrong shape
 * -- "Explorer is the total... everything should show up". Explorer
 * no longer has its own "favorite artists" level; each letter shows
 * TWO blocks: what you already have downloaded (local, complete, not
 * paginated) and a paginated MusicBrainz sample (infinite scroll),
 * without repeating what already showed up in the local block.
 */
sealed class ExplorerDrillLevel {
    object Letters : ExplorerDrillLevel()
    data class Artists(val letter: Char) : ExplorerDrillLevel()
}

data class ExplorerUiState(
    val drill: ExplorerDrillLevel = ExplorerDrillLevel.Letters,
    // Fijas A-Z -- a diferencia de Biblioteca, que solo muestra letras
    // con contenido local, MusicBrainz "tiene de todo" así que las 26
    // letras son siempre explorables.
    val letters: List<Char> = ('A'..'Z').toList(),
    val localArtistsForLetter: List<String> = emptyList(),
    val onlineArtists: List<MusicBrainzArtistSummary> = emptyList(),
    val isLoadingOnline: Boolean = false,
    val hasMoreOnline: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * ViewModel de Explorador (H12, S018 rediseño): "Biblioteca pero de
 * MusicBrainz", ahora de verdad -- letra -> lo que ya tienes de esa
 * letra (local) + muestra paginada de MusicBrainz de esa letra
 * (online, scroll infinito). Sigue sin reutilizar LibraryViewModel
 * (SAF, descarga, edición, fusión de carpetas no aplican aquí); lo
 * único compartido con Biblioteca son `sortLetterFor()` y
 * `LetterGrid` (composable), como ya se acordó.
 * ---
 * Explorer's ViewModel (H12, S018 redesign): "Library but for
 * MusicBrainz", for real this time -- letter -> what you already have
 * for that letter (local) + a paginated MusicBrainz sample for that
 * letter (online, infinite scroll). Still doesn't reuse
 * LibraryViewModel (SAF, download, editing, folder merging don't
 * apply here); the only things shared with Biblioteca are
 * `sortLetterFor()` and the `LetterGrid` composable, as already
 * agreed.
 */
@HiltViewModel
class ExplorerViewModel @Inject constructor(
    private val artistDirectoryRepository: ArtistDirectoryRepository,
    private val searchResultTrackRepository: SearchResultTrackRepository,
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 20
    }

    /** Todos los artistas con algo DESCARGADO (DownloadStatus.DONE) -- mismo filtro que Biblioteca. */
    private var allLocalArtists: List<String> = emptyList()
    private var onlineOffset = 0

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            searchResultTrackRepository.getByStatus(DownloadStatus.DONE).collect { tracks ->
                allLocalArtists = tracks
                    .map { it.artist ?: it.channelTitle }
                    .distinct()
                    .sorted()
                val currentDrill = _uiState.value.drill
                if (currentDrill is ExplorerDrillLevel.Artists) {
                    _uiState.value = _uiState.value.copy(
                        localArtistsForLetter = localArtistsFor(currentDrill.letter),
                    )
                }
            }
        }
    }

    private fun localArtistsFor(letter: Char): List<String> =
        allLocalArtists.filter { sortLetterFor(it) == letter }

    fun selectLetter(letter: Char) {
        onlineOffset = 0
        _uiState.value = _uiState.value.copy(
            drill = ExplorerDrillLevel.Artists(letter),
            localArtistsForLetter = localArtistsFor(letter),
            onlineArtists = emptyList(),
            hasMoreOnline = true,
            errorMessage = null,
        )
        loadMoreOnline()
    }

    fun backToLetters() {
        _uiState.value = _uiState.value.copy(
            drill = ExplorerDrillLevel.Letters,
            localArtistsForLetter = emptyList(),
            onlineArtists = emptyList(),
        )
    }

    /**
     * Pide la siguiente página de MusicBrainz para la letra activa --
     * llamado tanto al entrar en la letra como al acercarse al final
     * del scroll (ver ExplorerScreen). Descarta duplicados con el
     * bloque local (mismo criterio de nombre normalizado que el resto
     * de H12) para no mostrar dos veces el mismo artista.
     * ---
     * Requests the next MusicBrainz page for the active letter --
     * called both on entering the letter and when nearing the end of
     * the scroll (see ExplorerScreen). Discards duplicates against the
     * local block (same normalized-name criterion as the rest of H12)
     * to avoid showing the same artist twice.
     */
    fun loadMoreOnline() {
        val drill = _uiState.value.drill
        if (drill !is ExplorerDrillLevel.Artists) return
        if (_uiState.value.isLoadingOnline || !_uiState.value.hasMoreOnline) return

        val letter = drill.letter
        val requestedOffset = onlineOffset
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingOnline = true, errorMessage = null)
            try {
                val page = artistDirectoryRepository.browseArtistsByLetter(
                    letter = letter,
                    offset = requestedOffset,
                )
                // La letra pudo haber cambiado mientras esta llamada
                // estaba en vuelo (usuario tocó otra letra rápido) --
                // si ya no coincide, se descarta el resultado.
                if ((_uiState.value.drill as? ExplorerDrillLevel.Artists)?.letter != letter) return@launch

                val existingKeys = (_uiState.value.localArtistsForLetter + _uiState.value.onlineArtists.map { it.name })
                    .map { SearchNormalizer.normalizeArtistName(it) }
                    .toSet()
                val newArtists = page.filter {
                    SearchNormalizer.normalizeArtistName(it.name) !in existingKeys
                }

                onlineOffset = requestedOffset + PAGE_SIZE
                _uiState.value = _uiState.value.copy(
                    isLoadingOnline = false,
                    onlineArtists = _uiState.value.onlineArtists + newArtists,
                    hasMoreOnline = page.size == PAGE_SIZE,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingOnline = false,
                    errorMessage = e.message ?: "Error al cargar más artistas",
                )
            }
        }
    }
}

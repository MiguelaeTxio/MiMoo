package com.miguelaetxio.mimoo.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.repository.FavoriteArtistRepository
import com.miguelaetxio.mimoo.ui.library.sortLetterFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Igual que AlbumsDrillLevel/SinglesDrillLevel de Biblioteca, pero de
 * un solo nivel -- Explorador no tiene capa de álbum/pista propia
 * (roadmap S018, punto 2 acordado con Miguel Ángel): tocar un artista
 * navega directamente a ArtistScreen (H12), que ya resuelve álbumes/
 * sencillos reales de MusicBrainz. No se reconstruye esa profundidad
 * aquí.
 * ---
 * Same idea as Biblioteca's AlbumsDrillLevel/SinglesDrillLevel, but a
 * single level -- Explorer has no album/track layer of its own (S018
 * roadmap, point 2 agreed with Miguel Ángel): tapping an artist
 * navigates directly to ArtistScreen (H12), which already resolves
 * real MusicBrainz albums/singles. That depth isn't rebuilt here.
 */
sealed class ExplorerDrillLevel {
    object Letters : ExplorerDrillLevel()
    data class Artists(val letter: Char) : ExplorerDrillLevel()
}

data class ExplorerUiState(
    val drill: ExplorerDrillLevel = ExplorerDrillLevel.Letters,
    val letters: List<Char> = emptyList(),
    val artistsForLetter: List<String> = emptyList(),
)

/**
 * ViewModel de Explorador (H12, S018): "Biblioteca pero de
 * MusicBrainz" -- letras -> artistas FAVORITOS (`FavoriteArtist`) de
 * esa letra -> ArtistScreen. Deliberadamente NO reutiliza
 * LibraryViewModel ni ninguna de sus operaciones locales (descarga,
 * SAF, edición de metadatos, borrado, fusión de carpetas) -- ninguna
 * tiene sentido sobre un directorio que solo lee de MusicBrainz, sin
 * archivo real detrás. Lo único reutilizado de Biblioteca es
 * `sortLetterFor()` (función pura) y el composable `LetterGrid`
 * (ver ExplorerScreen), tal como se acordó con Miguel Ángel para no
 * duplicar código sin forzar una abstracción compartida que mezclaría
 * conceptos locales con conceptos online.
 * ---
 * Explorer's ViewModel (H12, S018): "Library but for MusicBrainz" --
 * letters -> FAVORITE artists (`FavoriteArtist`) for that letter ->
 * ArtistScreen. Deliberately does NOT reuse LibraryViewModel or any
 * of its local-only operations (download, SAF, metadata editing,
 * deletion, folder merging) -- none of them make sense over a
 * directory that only reads from MusicBrainz, with no real file
 * behind it. The only things reused from Biblioteca are
 * `sortLetterFor()` (a pure function) and the `LetterGrid` composable
 * (see ExplorerScreen), as agreed with Miguel Ángel to avoid
 * duplicating code without forcing a shared abstraction that would
 * mix local concepts with online ones.
 */
@HiltViewModel
class ExplorerViewModel @Inject constructor(
    private val favoriteArtistRepository: FavoriteArtistRepository,
) : ViewModel() {

    /** Todos los artistas favoritos, orden natural -- mismo criterio que Biblioteca (toSortedMap()). */
    private var allArtists: List<String> = emptyList()

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            favoriteArtistRepository.getAll().collect { favorites ->
                allArtists = favorites.map { it.artist }.sorted()
                val letters = allArtists.map { sortLetterFor(it) }.distinct().sorted()
                _uiState.value = _uiState.value.copy(
                    letters = letters,
                    artistsForLetter = artistsFor(_uiState.value.drill),
                )
            }
        }
    }

    private fun artistsFor(drill: ExplorerDrillLevel): List<String> =
        when (drill) {
            is ExplorerDrillLevel.Artists -> allArtists.filter { sortLetterFor(it) == drill.letter }
            ExplorerDrillLevel.Letters -> emptyList()
        }

    fun selectLetter(letter: Char) {
        val drill = ExplorerDrillLevel.Artists(letter)
        _uiState.value = _uiState.value.copy(drill = drill, artistsForLetter = artistsFor(drill))
    }

    fun backToLetters() {
        _uiState.value = _uiState.value.copy(
            drill = ExplorerDrillLevel.Letters,
            artistsForLetter = emptyList(),
        )
    }
}

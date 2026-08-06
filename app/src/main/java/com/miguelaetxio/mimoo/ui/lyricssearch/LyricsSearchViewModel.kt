package com.miguelaetxio.mimoo.ui.lyricssearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.remote.LyricsRepository
import com.miguelaetxio.mimoo.data.remote.dto.LrcLibLyricsResult
import com.miguelaetxio.mimoo.util.LrcParser
import com.miguelaetxio.mimoo.util.SearchNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Un resultado de búsqueda de lrclib.net, con la distinción visual
 * "ya en tu biblioteca" resuelta (H17, S031, punto 5 del anexo:
 * "ambas, con distinción visual"). `inLibrary` se calcula cruzando
 * `artistName`+`trackName` normalizados (`SearchNormalizer`, mismo
 * mecanismo que `DislikedTrackRepository.normalizedKeysSnapshot()`)
 * contra la biblioteca local -- nunca al revés (la biblioteca no se
 * filtra por lrclib.net, es lrclib.net el que se anota).
 */
data class LyricsSearchResultItem(
    val result: LrcLibLyricsResult,
    val inLibrary: Boolean,
)

data class LyricsSearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<LyricsSearchResultItem> = emptyList(),
    val hasSearched: Boolean = false,
)

/**
 * ViewModel de la pantalla de búsqueda de letras del drawer (H17,
 * S031, bloque 3). Búsqueda libre contra `lrclib.net`
 * (`LyricsRepository.searchLyrics()`) -- deliberadamente por botón,
 * no en cada pulsación de tecla, mismo criterio que el resto de
 * búsquedas contra APIs externas de la app (evita machacar un
 * servicio público con una petición por letra tecleada).
 */
@HiltViewModel
class LyricsSearchViewModel @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val searchResultTrackRepository: SearchResultTrackRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LyricsSearchUiState())
    val uiState: StateFlow<LyricsSearchUiState> = _uiState.asStateFlow()

    /** Expandido = letra visible bajo la fila; identificado por posición en `results` (id de lrclib.net no es estable como key de UI aquí porque puede repetirse tras una nueva búsqueda). */
    private val _expandedResultId = MutableStateFlow<Long?>(null)
    val expandedResultId: StateFlow<Long?> = _expandedResultId.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun toggleExpanded(id: Long) {
        _expandedResultId.value = if (_expandedResultId.value == id) null else id
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        _expandedResultId.value = null
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val rawResults = lyricsRepository.searchLyrics(query)
            // Mismo mecanismo de claves normalizadas que
            // DislikedTrackRepository.normalizedKeysSnapshot() -- ver
            // su kdoc. Se calcula una sola vez por búsqueda, no por
            // resultado, para no recorrer la biblioteca N veces.
            val libraryKeys = searchResultTrackRepository.getAllOnce()
                .filter { !it.artist.isNullOrBlank() }
                .map { libraryKey(it.artist!!, it.title) }
                .toSet()
            val items = rawResults.map { result ->
                LyricsSearchResultItem(
                    result = result,
                    inLibrary = libraryKey(result.artistName, result.trackName) in libraryKeys,
                )
            }
            _uiState.value = _uiState.value.copy(loading = false, results = items, hasSearched = true)
        }
    }

    /** Texto legible de la letra de un resultado -- prioriza `syncedLyrics` desnudado de timestamps sobre `plainLyrics` solo si esta última no existe (leer, no cantar, así que da igual cuál se muestre si ambas existen; `plainLyrics` es la forma "natural" de leer). */
    fun readableLyrics(result: LrcLibLyricsResult): String? {
        result.plainLyrics?.takeIf { it.isNotBlank() }?.let { return it }
        result.syncedLyrics?.takeIf { it.isNotBlank() }?.let { synced ->
            val lines = LrcParser.parse(synced)
            if (lines.isNotEmpty()) return lines.joinToString("\n") { it.text }
        }
        return null
    }

    companion object {
        private fun libraryKey(artist: String, title: String): String =
            SearchNormalizer.normalizeArtistName(artist) + "|" + SearchNormalizer.songTitleKey(title, artist)
    }
}

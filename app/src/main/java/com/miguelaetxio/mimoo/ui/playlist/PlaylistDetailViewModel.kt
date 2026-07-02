package com.miguelaetxio.mimoo.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.PlaylistRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val tracks: List<SearchResultTrack> = emptyList(),
)

/**
 * ViewModel for a single playlist's detail screen (Hito 04): ordered
 * track list, remove, reorder (up/down), play all.
 *
 * playlistId comes from navigation arguments rather than the
 * constructor directly — SavedStateHandle is the standard Hilt+Nav
 * Compose pattern for that, but is intentionally left for the actual
 * NavGraph wiring step rather than guessed here; this constructor
 * signature may need adjusting once Screen.PlaylistDetail's argument
 * name is fixed in NavGraph.kt.
 * ---
 * ViewModel de la pantalla de detalle de una playlist (Hito 04):
 * lista de pistas en orden, quitar, reordenar (subir/bajar),
 * reproducir todo.
 *
 * playlistId llega de los argumentos de navegación en vez de ir
 * directo al constructor — SavedStateHandle es el patrón estándar de
 * Hilt+Nav Compose para eso, pero se deja intencionadamente para el
 * propio paso de conexión de NavGraph en vez de adivinarlo aquí; esta
 * firma de constructor puede necesitar ajuste en cuanto se fije el
 * nombre del argumento de Screen.PlaylistDetail en NavGraph.kt.
 */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val playerManager: PlayerManager,
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    private val playlistId: Long =
        checkNotNull(savedStateHandle.get<Long>("playlistId")) {
            "PlaylistDetailViewModel requires a playlistId nav argument"
        }

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getTracksForPlaylist(playlistId).collect { tracks ->
                _uiState.value = _uiState.value.copy(tracks = tracks)
            }
        }
    }

    fun removeTrack(youtubeId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, youtubeId)
        }
    }

    /**
     * Swaps a track with its neighbor and persists both positions.
     * Minimum-viable reordering per mimoo-annex-v04 PASO 3 — drag and
     * drop is explicitly out of scope for this hito.
     * ---
     * Intercambia una pista con su vecina y persiste ambas
     * posiciones. Reordenación mínima viable según mimoo-annex-v04
     * PASO 3 — el drag and drop queda explícitamente fuera de alcance
     * de este hito.
     */
    fun moveTrack(fromIndex: Int, direction: Int) {
        val tracks = _uiState.value.tracks
        val toIndex = fromIndex + direction
        if (toIndex !in tracks.indices || fromIndex !in tracks.indices) return

        val a = tracks[fromIndex]
        val b = tracks[toIndex]
        viewModelScope.launch {
            repository.updatePosition(playlistId, a.youtubeId, toIndex)
            repository.updatePosition(playlistId, b.youtubeId, fromIndex)
        }
    }

    /**
     * Plays the whole playlist in saved order. Unlike Biblioteca's
     * playAlbum/playArtist (only ever downloaded tracks), a playlist
     * can contain tracks with filePath == null (never downloaded,
     * streaming-only search results) — those are skipped here rather
     * than resolved to a stream URL, since StreamResolver's actual
     * invocation flow lives in SearchViewModel and has not been read
     * yet for this hito (see mimoo-annex-v04 PASO 5, still pending);
     * wiring it in is left for that step rather than guessed now.
     * ---
     * Reproduce la playlist completa en el orden guardado. A
     * diferencia de playAlbum/playArtist de Biblioteca (solo pistas
     * descargadas), una playlist puede contener pistas con
     * filePath == null (nunca descargadas, resultados de búsqueda
     * solo en streaming) — esas se omiten aquí en vez de resolverse a
     * una URL de stream, ya que el flujo real de invocación de
     * StreamResolver vive en SearchViewModel y todavía no se ha leído
     * para este hito (ver mimoo-annex-v04 PASO 5, aún pendiente);
     * conectarlo se deja para ese paso en vez de adivinarlo ahora.
     */
    fun playAll() {
        val items = _uiState.value.tracks.mapNotNull { track ->
            track.filePath?.let { path ->
                QueueItem(uri = path, title = track.title, isLocal = true)
            }
        }
        if (items.isNotEmpty()) {
            playerManager.playQueue(items)
        }
    }
}

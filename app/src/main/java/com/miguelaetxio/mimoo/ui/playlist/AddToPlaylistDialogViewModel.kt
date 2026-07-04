package com.miguelaetxio.mimoo.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.entity.Playlist
import com.miguelaetxio.mimoo.data.local.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddToPlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
)

/**
 * Backs the shared "add to playlist" dialog used from both
 * SearchScreen and LibraryScreen (Hito 04, PASO 4). Deliberately
 * separate from SearchViewModel/LibraryViewModel — the dialog is a
 * self-contained unit that only needs the playlist list and two
 * actions, so it doesn't need either screen's ViewModel to know
 * anything about playlists at all.
 * ---
 * Sostiene el diálogo compartido de "añadir a playlist" usado tanto
 * desde SearchScreen como desde LibraryScreen (Hito 04, PASO 4).
 * Deliberadamente separado de SearchViewModel/LibraryViewModel — el
 * diálogo es una unidad autocontenida que solo necesita la lista de
 * playlists y dos acciones, así que ninguno de los dos ViewModels de
 * pantalla necesita saber nada sobre playlists.
 */
@HiltViewModel
class AddToPlaylistDialogViewModel @Inject constructor(
    private val repository: PlaylistRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddToPlaylistUiState())
    val uiState: StateFlow<AddToPlaylistUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllPlaylists().collect { playlists ->
                _uiState.value = _uiState.value.copy(playlists = playlists)
            }
        }
    }

    /**
     * youtubeIds en vez de un solo youtubeId -- petición explícita de
     * Miguel Ángel (2026-07-04): poder añadir un álbum entero (varias
     * pistas de golpe) a una lista, no solo una pista suelta. Los
     * llamantes existentes (una sola pista) pasan listOf(youtubeId).
     * ---
     * youtubeIds instead of a single youtubeId -- explicit request
     * from Miguel Ángel (2026-07-04): being able to add a whole album
     * (several tracks at once) to a playlist, not just a single track.
     * Existing callers (one track) pass listOf(youtubeId).
     */
    fun addToExistingPlaylist(playlistId: Long, youtubeIds: List<String>) {
        viewModelScope.launch {
            repository.addTracksToPlaylist(playlistId, youtubeIds)
        }
    }

    fun createPlaylistAndAdd(name: String, youtubeIds: List<String>) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val playlistId = repository.createPlaylist(trimmed)
            repository.addTracksToPlaylist(playlistId, youtubeIds)
        }
    }
}

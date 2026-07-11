package com.miguelaetxio.mimoo.ui.playlist

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.backup.MutationOutcome
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
    // H07 PARTE 1 -- aviso cuando añadir se rechaza por falta de
    // conexión. El diálogo YA NO se cierra solo al pulsar -- espera a
    // que la operación termine, para poder mostrar esto en vez de
    // cerrarse en silencio sin haber añadido nada.
    val syncBlockedMessage: String? = null,
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
    private val autoSyncPusher: AutoSyncPusher,
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
     *
     * `onSuccess` -- el diálogo lo usa para cerrarse solo si la
     * operación realmente se aplicó (H07 PARTE 1); si no hay
     * conexión, el diálogo se queda abierto mostrando el aviso.
     * ---
     * youtubeIds instead of a single youtubeId -- explicit request
     * from Miguel Ángel (2026-07-04): being able to add a whole album
     * (several tracks at once) to a playlist, not just a single track.
     * Existing callers (one track) pass listOf(youtubeId).
     *
     * `onSuccess` -- the dialog uses this to close itself only if the
     * operation actually applied (H07 PART 1); if there's no
     * connection, the dialog stays open showing the notice.
     */
    fun addToExistingPlaylist(
        activity: Activity,
        playlistId: Long,
        youtubeIds: List<String>,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(activity) {
                repository.addTracksToPlaylist(playlistId, youtubeIds)
            }
            if (outcome is MutationOutcome.Success) {
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(
                    syncBlockedMessage = "Sin conexión: no se puede añadir ahora mismo."
                )
            }
        }
    }

    fun createPlaylistAndAdd(
        activity: Activity,
        name: String,
        youtubeIds: List<String>,
        onSuccess: () -> Unit,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(activity) {
                val playlistId = repository.createPlaylist(trimmed)
                repository.addTracksToPlaylist(playlistId, youtubeIds)
            }
            if (outcome is MutationOutcome.Success) {
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(
                    syncBlockedMessage = "Sin conexión: no se puede crear la lista ahora mismo."
                )
            }
        }
    }

    /** Descarta el aviso de mutación bloqueada por falta de conexión (H07 PARTE 1). */
    fun dismissSyncBlockedMessage() {
        _uiState.value = _uiState.value.copy(syncBlockedMessage = null)
    }
}

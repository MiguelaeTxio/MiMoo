package com.miguelaetxio.mimoo.ui.playlist

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.local.entity.Playlist
import com.miguelaetxio.mimoo.data.local.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList(),
    // H08 PARTE 1 -- filtro de texto sobre el nombre de la playlist.
    // playlists conserva SIEMPRE la lista completa; filteredPlaylists
    // es la vista derivada que consume PlaylistsScreen.kt, mismo
    // patrón que Biblioteca (LibraryViewModel no sustituye su lista
    // base al filtrar).
    val filterQuery: String = "",
    val filteredPlaylists: List<Playlist> = emptyList(),
    // H07 PARTE 1 -- aviso cuando una acción de crear/borrar se
    // rechaza por falta de conexión (regla de negocio de Miguel
    // Ángel, S008).
    val syncBlockedMessage: String? = null,
)

/**
 * ViewModel for the playlist listing screen (Hito 04): create,
 * rename, delete. Track membership/order and playback live in
 * PlaylistDetailViewModel, one level down.
 * ---
 * ViewModel de la pantalla de listado de playlists (Hito 04): crear,
 * renombrar, borrar. La pertenencia/orden de pistas y la reproducción
 * viven en PlaylistDetailViewModel, un nivel más abajo.
 */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val autoSyncPusher: AutoSyncPusher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllPlaylists().collect { playlists ->
                _uiState.value = _uiState.value.copy(playlists = playlists)
                recompute()
            }
        }
    }

    fun createPlaylist(activity: Activity, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(activity) {
                repository.createPlaylist(trimmed)
            }
            if (outcome is com.miguelaetxio.mimoo.data.backup.MutationOutcome.NoConnection) {
                _uiState.value = _uiState.value.copy(
                    syncBlockedMessage = "Sin conexión: no se puede crear la lista ahora mismo."
                )
            }
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.renamePlaylist(playlistId, trimmed) }
    }

    fun deletePlaylist(activity: Activity, playlistId: Long) {
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(activity) {
                repository.deletePlaylist(playlistId)
            }
            if (outcome is com.miguelaetxio.mimoo.data.backup.MutationOutcome.NoConnection) {
                _uiState.value = _uiState.value.copy(
                    syncBlockedMessage = "Sin conexión: no se puede borrar la lista ahora mismo."
                )
            }
        }
    }

    /** Descarta el aviso de mutación bloqueada por falta de conexión (H07 PARTE 1). */
    fun dismissSyncBlockedMessage() {
        _uiState.value = _uiState.value.copy(syncBlockedMessage = null)
    }

    /**
     * H08 PARTE 1 -- filtro de texto de la pantalla de Playlists.
     * Mismo criterio que LibraryViewModel.recompute(): trim + lowercase
     * + contains sobre el nombre, sin normalizar acentos (el filtro de
     * Biblioteca tampoco lo hace -- verificado leyendo el código real
     * antes de asumirlo, §4.1).
     */
    fun onFilterQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(filterQuery = query)
        recompute()
    }

    private fun recompute() {
        val query = _uiState.value.filterQuery.trim().lowercase()
        val playlists = _uiState.value.playlists
        val filtered = if (query.isEmpty()) {
            playlists
        } else {
            playlists.filter { it.name.lowercase().contains(query) }
        }
        _uiState.value = _uiState.value.copy(filteredPlaylists = filtered)
    }
}

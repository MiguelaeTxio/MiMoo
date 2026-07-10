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
            }
        }
    }

    fun createPlaylist(activity: Activity, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.createPlaylist(trimmed)
            // H07 PARTE 1, PASO 1.2.
            autoSyncPusher.pushIfAuthorized(activity)
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.renamePlaylist(playlistId, trimmed) }
    }

    fun deletePlaylist(activity: Activity, playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            // H07 PARTE 1, PASO 1.2.
            autoSyncPusher.pushIfAuthorized(activity)
        }
    }
}

package com.miguelaetxio.mimoo.ui.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.entity.Playlist
import com.miguelaetxio.mimoo.data.local.entity.PlaylistTrack
import com.miguelaetxio.mimoo.data.local.entity.Track
import com.miguelaetxio.mimoo.data.local.repository.PlaylistRepository
import com.miguelaetxio.mimoo.data.local.repository.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for playlist list, detail and form screens.
 * ---
 * ViewModel para las pantallas de lista, detalle y formulario de playlists.
 */
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val trackRepository: TrackRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val playlistId: Long =
        savedStateHandle.get<Long>("playlistId") ?: -1L

    val playlists: StateFlow<List<Playlist>> = playlistRepository
        .getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editPlaylist = MutableStateFlow<Playlist?>(null)
    val editPlaylist: StateFlow<Playlist?> = _editPlaylist

    val playlistTracks: StateFlow<List<Track>> = if (playlistId > 0) {
        playlistRepository.getTracksForPlaylist(playlistId)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList(),
            )
    } else {
        MutableStateFlow(emptyList())
    }

    /**
     * Loads a playlist for editing by its id.
     * ---
     * Carga una playlist para editar por su id.
     */
    fun loadPlaylist(id: Long) {
        viewModelScope.launch {
            _editPlaylist.value = playlistRepository.getById(id)
        }
    }

    /**
     * Saves (insert or update) a playlist.
     * ---
     * Guarda (inserta o actualiza) una playlist.
     */
    fun save(playlist: Playlist) {
        viewModelScope.launch { playlistRepository.save(playlist) }
    }

    /**
     * Deletes a playlist.
     * ---
     * Elimina una playlist.
     */
    fun delete(playlist: Playlist) {
        viewModelScope.launch { playlistRepository.delete(playlist) }
    }

    /**
     * Adds a track to the playlist at the next position.
     * ---
     * Añade un track a la playlist en la siguiente posición.
     */
    fun addTrack(pId: Long, trackId: Long) {
        viewModelScope.launch {
            val position = playlistTracks.value.size
            playlistRepository.addTrack(
                PlaylistTrack(playlistId = pId, trackId = trackId, position = position)
            )
        }
    }

    /**
     * Removes a track from the playlist.
     * ---
     * Elimina un track de la playlist.
     */
    fun removeTrack(pId: Long, trackId: Long) {
        viewModelScope.launch {
            playlistRepository.removeTrack(pId, trackId)
        }
    }
}

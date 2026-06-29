package com.miguelaetxio.mimoo.ui.track

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.entity.Track
import com.miguelaetxio.mimoo.data.local.repository.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for track list and form screens.
 * ---
 * ViewModel para las pantallas de lista y formulario de tracks.
 */
@HiltViewModel
class TrackViewModel @Inject constructor(
    private val repository: TrackRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val artistId: Long =
        savedStateHandle.get<Long>("artistId") ?: -1L

    val tracks: StateFlow<List<Track>> = repository
        .getTracksByArtist(artistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editTrack = MutableStateFlow<Track?>(null)
    val editTrack: StateFlow<Track?> = _editTrack

    /**
     * Loads a track for editing by its id.
     * ---
     * Carga un track para editar por su id.
     */
    fun loadTrack(trackId: Long) {
        viewModelScope.launch {
            _editTrack.value = repository.getById(trackId)
        }
    }

    /**
     * Saves (insert or update) a track.
     * ---
     * Guarda (inserta o actualiza) un track.
     */
    fun save(track: Track) {
        viewModelScope.launch { repository.save(track) }
    }

    /**
     * Deletes a track.
     * ---
     * Elimina un track.
     */
    fun delete(track: Track) {
        viewModelScope.launch { repository.delete(track) }
    }
}

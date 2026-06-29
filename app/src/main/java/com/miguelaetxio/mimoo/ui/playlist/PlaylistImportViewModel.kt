package com.miguelaetxio.mimoo.ui.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.BuildConfig
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.Track
import com.miguelaetxio.mimoo.data.local.repository.TrackRepository
import com.miguelaetxio.mimoo.data.remote.YouTubeRepository
import com.miguelaetxio.mimoo.data.remote.dto.TrackDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * States for the playlist import flow.
 * ---
 * Estados del flujo de importación de playlist.
 */
sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    data class Success(val tracks: List<TrackDto>) : ImportState()
    data class Error(val message: String) : ImportState()
}

/**
 * ViewModel for importing a YouTube playlist.
 * ---
 * ViewModel para importar una playlist de YouTube.
 */
@HiltViewModel
class PlaylistImportViewModel @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val trackRepository: TrackRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val artistId: Long =
        savedStateHandle.get<Long>("artistId") ?: -1L

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state

    /**
     * Extracts playlist id from URL and fetches tracks.
     * ---
     * Extrae el id de playlist de la URL y obtiene los tracks.
     */
    fun import(url: String) {
        val playlistId = extractPlaylistId(url)
        if (playlistId == null) {
            _state.value = ImportState.Error("URL de playlist inválida")
            return
        }
        _state.value = ImportState.Loading
        viewModelScope.launch {
            try {
                val tracks = youTubeRepository.fetchPlaylistItems(
                    playlistId = playlistId,
                    apiKey = BuildConfig.YOUTUBE_API_KEY,
                )
                _state.value = ImportState.Success(tracks)
            } catch (e: Exception) {
                _state.value = ImportState.Error(
                    e.message ?: "Error al importar la playlist"
                )
            }
        }
    }

    /**
     * Saves selected tracks to Room.
     * ---
     * Guarda los tracks seleccionados en Room.
     */
    fun saveSelected(selected: List<TrackDto>) {
        viewModelScope.launch {
            selected.forEach { dto ->
                trackRepository.save(
                    Track(
                        id = 0L,
                        youtubeId = dto.youtubeId,
                        title = dto.title,
                        artistId = artistId,
                        albumId = null,
                        durationSeconds = dto.durationSeconds,
                        downloadStatus = DownloadStatus.PENDING,
                        localPath = null,
                        thumbnailUrl = dto.thumbnailUrl,
                    )
                )
            }
            _state.value = ImportState.Idle
        }
    }

    private fun extractPlaylistId(url: String): String? {
        val regex = Regex("""list=([A-Za-z0-9_-]+)""")
        return regex.find(url)?.groupValues?.get(1)
    }
}

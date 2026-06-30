package com.miguelaetxio.mimoo.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.BuildConfig
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.StreamResolver
import com.miguelaetxio.mimoo.data.remote.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResultTrack> = emptyList(),
    val isSearching: Boolean = false,
    val isResolvingStream: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val searchResultTrackRepository: SearchResultTrackRepository,
    private val streamResolver: StreamResolver,
    private val playerManager: PlayerManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                errorMessage = null,
            )
            try {
                val dtos = youTubeRepository.search(
                    query = query,
                    apiKey = BuildConfig.YOUTUBE_API_KEY,
                )
                val tracks = dtos.map { dto ->
                    SearchResultTrack(
                        youtubeId = dto.youtubeId,
                        title = dto.title,
                        channelTitle = dto.channelTitle,
                        durationSeconds = dto.durationSeconds,
                        thumbnailUrl = dto.thumbnailUrl,
                    )
                }
                searchResultTrackRepository.cacheSearchResults(tracks)
                _uiState.value = _uiState.value.copy(
                    results = tracks,
                    isSearching = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    errorMessage = e.message ?: "Error al buscar",
                )
            }
        }
    }

    fun playTrack(track: SearchResultTrack) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isResolvingStream = true,
                errorMessage = null,
            )
            try {
                val streamUrl =
                    streamResolver.resolveAudioStreamUrl(track.youtubeUrl)
                playerManager.play(streamUrl, track.title)
                _uiState.value = _uiState.value.copy(isResolvingStream = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isResolvingStream = false,
                    errorMessage = e.message ?: "Error al resolver el stream",
                )
            }
        }
    }
}

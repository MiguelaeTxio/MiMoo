package com.miguelaetxio.mimoo.ui.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.entity.Album
import com.miguelaetxio.mimoo.data.local.repository.AlbumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val repo: AlbumRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val artistId: Long = savedStateHandle.get<Long>("artistId") ?: -1L
    private val albumId: Long  = savedStateHandle.get<Long>("albumId") ?: -1L

    val albums: StateFlow<List<Album>> = repo.getByArtist(artistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _current = MutableStateFlow<Album?>(null)
    val current: StateFlow<Album?> = _current.asStateFlow()

    init {
        if (albumId > 0L) {
            viewModelScope.launch { _current.value = repo.getById(albumId) }
        }
    }

    fun save(title: String, year: String, genres: String, coverUrl: String) {
        viewModelScope.launch {
            val album = _current.value?.copy(
                title = title,
                year = year.toIntOrNull(),
                genres = genres.ifBlank { null },
                coverUrl = coverUrl.ifBlank { null },
                updatedAt = System.currentTimeMillis(),
            ) ?: Album(
                artistId = artistId,
                title = title,
                year = year.toIntOrNull(),
                genres = genres.ifBlank { null },
                coverUrl = coverUrl.ifBlank { null },
            )
            repo.save(album)
        }
    }

    fun delete(album: Album) {
        viewModelScope.launch { repo.delete(album) }
    }
}

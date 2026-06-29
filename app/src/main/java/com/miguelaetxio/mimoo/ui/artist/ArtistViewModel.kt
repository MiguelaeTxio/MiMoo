package com.miguelaetxio.mimoo.ui.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.entity.Artist
import com.miguelaetxio.mimoo.data.local.repository.ArtistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val repo: ArtistRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val artists: StateFlow<List<Artist>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val artistId: Long = savedStateHandle.get<Long>("artistId") ?: -1L

    private val _current = MutableStateFlow<Artist?>(null)
    val current: StateFlow<Artist?> = _current.asStateFlow()

    init {
        if (artistId > 0L) {
            viewModelScope.launch {
                _current.value = repo.getById(artistId)
            }
        }
    }

    fun save(name: String, bio: String, genres: String, coverUrl: String) {
        viewModelScope.launch {
            val artist = _current.value?.copy(
                name = name, bio = bio.ifBlank { null },
                genres = genres.ifBlank { null }, coverUrl = coverUrl.ifBlank { null },
                updatedAt = System.currentTimeMillis(),
            ) ?: Artist(name = name, bio = bio.ifBlank { null },
                genres = genres.ifBlank { null }, coverUrl = coverUrl.ifBlank { null })
            repo.save(artist)
        }
    }

    fun delete(artist: Artist) {
        viewModelScope.launch { repo.delete(artist) }
    }
}

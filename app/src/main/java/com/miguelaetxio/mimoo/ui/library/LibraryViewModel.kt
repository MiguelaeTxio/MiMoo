package com.miguelaetxio.mimoo.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryViewMode { HIERARCHICAL, FLAT }
enum class LibrarySortOption { TITLE, ARTIST, DATE }

/**
 * Fallback label for albums with no name — matches the "Sencillos"
 * convention already used by DownloadDirManager for the on-disk
 * folder, so the UI and the storage structure agree.
 * ---
 * Etiqueta de respaldo para álbumes sin nombre — coincide con la
 * convención "Sencillos" que ya usa DownloadDirManager para la
 * carpeta en disco, para que la UI y el almacenamiento concuerden.
 */
private const val UNKNOWN_ALBUM_LABEL = "Sencillos"

data class LibraryUiState(
    val viewMode: LibraryViewMode = LibraryViewMode.HIERARCHICAL,
    val sortOption: LibrarySortOption = LibrarySortOption.ARTIST,
    val filterQuery: String = "",
    val flatTracks: List<SearchResultTrack> = emptyList(),
    val grouped: Map<String, Map<String, List<SearchResultTrack>>> = emptyMap(),
)

/**
 * ViewModel for the Biblioteca (local library) screen. Reads only
 * tracks with downloadStatus == DONE — this screen is about what has
 * actually been downloaded, not search results.
 * ---
 * ViewModel de la pantalla de Biblioteca (biblioteca local). Lee
 * solo pistas con downloadStatus == DONE — esta pantalla trata sobre
 * lo que realmente se ha descargado, no resultados de búsqueda.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: SearchResultTrackRepository,
    private val playerManager: PlayerManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var allDownloaded: List<SearchResultTrack> = emptyList()

    init {
        viewModelScope.launch {
            repository.getByStatus(DownloadStatus.DONE).collect { tracks ->
                allDownloaded = tracks
                recompute()
            }
        }
    }

    fun setViewMode(mode: LibraryViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
        recompute()
    }

    fun setSortOption(option: LibrarySortOption) {
        _uiState.value = _uiState.value.copy(sortOption = option)
        recompute()
    }

    fun onFilterQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(filterQuery = query)
        recompute()
    }

    private fun recompute() {
        val query = _uiState.value.filterQuery.trim().lowercase()
        val filtered = if (query.isEmpty()) {
            allDownloaded
        } else {
            allDownloaded.filter { track ->
                track.title.lowercase().contains(query) ||
                    (track.artist ?: track.channelTitle).lowercase()
                        .contains(query) ||
                    (track.album ?: "").lowercase().contains(query)
            }
        }

        val sorted = when (_uiState.value.sortOption) {
            LibrarySortOption.TITLE -> filtered.sortedBy { it.title }
            LibrarySortOption.ARTIST -> filtered.sortedBy {
                it.artist ?: it.channelTitle
            }
            LibrarySortOption.DATE -> filtered.sortedByDescending {
                it.lastSearchedAt
            }
        }

        val grouped = filtered
            .groupBy { it.artist ?: it.channelTitle }
            .toSortedMap()
            .mapValues { (_, tracks) ->
                tracks
                    .groupBy { it.album ?: UNKNOWN_ALBUM_LABEL }
                    .toSortedMap()
                    .mapValues { (_, albumTracks) ->
                        albumTracks.sortedBy { it.title }
                    }
            }

        _uiState.value = _uiState.value.copy(
            flatTracks = sorted,
            grouped = grouped,
        )
    }

    /** Plays a single track from the library (always local). */
    fun playTrack(track: SearchResultTrack) {
        val filePath = track.filePath ?: return
        playerManager.play(filePath, track.title, isLocal = true)
    }

    /** Plays every track of one album, in title order, as a queue. */
    fun playAlbum(artist: String, album: String) {
        val tracks = _uiState.value.grouped[artist]?.get(album) ?: return
        playerManager.playQueue(tracks.toQueueItems())
    }

    /**
     * Plays every track of one artist across all their albums, album
     * order then title order within each album, as a queue.
     * ---
     * Reproduce todas las pistas de un artista en todos sus álbumes,
     * en orden de álbum y luego de título dentro de cada álbum, como
     * cola.
     */
    fun playArtist(artist: String) {
        val albums = _uiState.value.grouped[artist] ?: return
        val tracks = albums.values.flatten()
        playerManager.playQueue(tracks.toQueueItems())
    }

    /** Plays every track of one artist in random order. */
    fun playArtistShuffled(artist: String) {
        val albums = _uiState.value.grouped[artist] ?: return
        val tracks = albums.values.flatten().shuffled()
        playerManager.playQueue(tracks.toQueueItems())
    }

    private fun List<SearchResultTrack>.toQueueItems(): List<QueueItem> =
        mapNotNull { track ->
            track.filePath?.let { path ->
                QueueItem(uri = path, title = track.title, isLocal = true)
            }
        }
}

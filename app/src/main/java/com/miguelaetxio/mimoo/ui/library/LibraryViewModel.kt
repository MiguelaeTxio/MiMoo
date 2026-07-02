package com.miguelaetxio.mimoo.ui.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.library.LibraryReconciler
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import com.miguelaetxio.mimoo.data.remote.CoverArtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections
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
const val UNKNOWN_ALBUM_LABEL = "Sencillos"

data class LibraryUiState(
    val viewMode: LibraryViewMode = LibraryViewMode.HIERARCHICAL,
    val sortOption: LibrarySortOption = LibrarySortOption.ARTIST,
    val filterQuery: String = "",
    val showFavoritesOnly: Boolean = false,
    val flatTracks: List<SearchResultTrack> = emptyList(),
    val grouped: Map<String, Map<String, List<SearchResultTrack>>> = emptyMap(),
    val isRefreshing: Boolean = false,
)

/**
 * ViewModel for the Biblioteca (local library) screen. Reads only
 * tracks with downloadStatus == DONE — this screen is about what has
 * actually been downloaded, not search results.
 *
 * Does NOT auto-reconcile the SAF folder on creation — that only
 * happens once when the storage folder is first chosen (in
 * MainActivity) or on demand via refreshLibrary(), called from an
 * explicit refresh button. A full SAF tree walk on every screen open
 * would not scale to a large library.
 * ---
 * ViewModel de la pantalla de Biblioteca (biblioteca local). Lee
 * solo pistas con downloadStatus == DONE — esta pantalla trata sobre
 * lo que realmente se ha descargado, no resultados de búsqueda.
 *
 * NO reconcilia la carpeta SAF automáticamente al crearse — eso solo
 * ocurre una vez al elegir la carpeta por primera vez (en
 * MainActivity) o bajo demanda vía refreshLibrary(), llamado desde un
 * botón de refresco explícito. Un recorrido completo del árbol SAF en
 * cada apertura de pantalla no escalaría con una biblioteca grande.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: SearchResultTrackRepository,
    private val playerManager: PlayerManager,
    private val storageManager: StorageManager,
    private val libraryReconciler: LibraryReconciler,
    private val coverArtRepository: CoverArtRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var allDownloaded: List<SearchResultTrack> = emptyList()

    // Tracks which artist+album pairs already have a cover art lookup
    // in flight or resolved this process run, so LibraryScreen can
    // call requestCoverArtIfMissing() on every recomposition without
    // spamming MusicBrainz — only the first call per album per app
    // run actually triggers a network request.
    private val coverArtRequested = Collections.synchronizedSet(mutableSetOf<String>())

    init {
        viewModelScope.launch {
            repository.getByStatus(DownloadStatus.DONE).collect { tracks ->
                allDownloaded = tracks
                recompute()
            }
        }
    }

    /**
     * Manually triggers a SAF↔Room reconciliation, called only from
     * the refresh button in LibraryScreen.
     * ---
     * Dispara manualmente una reconciliación SAF↔Room, llamado solo
     * desde el botón de refresco de LibraryScreen.
     */
    fun refreshLibrary() {
        val rootUri = storageManager.getRootUri() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            libraryReconciler.rescan(rootUri)
            _uiState.value = _uiState.value.copy(isRefreshing = false)
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

    /** Toggles the "show favorites only" filter (PASO 4, H03). */
    fun setShowFavoritesOnly(showFavoritesOnly: Boolean) {
        _uiState.value = _uiState.value.copy(
            showFavoritesOnly = showFavoritesOnly,
        )
        recompute()
    }

    /** Toggles the favorite flag for a track from the library. */
    fun toggleFavorite(track: SearchResultTrack) {
        viewModelScope.launch {
            repository.updateFavorite(track.youtubeId, !track.isFavorite)
        }
    }

    /**
     * Resolves and persists the cover art for one artist+album if it
     * hasn't been requested yet this process run (PASO 6, H03).
     * Called from LibraryScreen once per rendered AlbumHeaderRow — the
     * `coverArtRequested` guard makes repeated calls across
     * recompositions a no-op after the first. Synthetic "Sencillos"
     * groupings (no real album, see UNKNOWN_ALBUM_LABEL) are never
     * passed here — there is nothing meaningful to search for.
     * ---
     * Resuelve y persiste la carátula de un artista+álbum si no se ha
     * pedido ya en esta ejecución del proceso (PASO 6, H03). Se llama
     * desde LibraryScreen una vez por cada AlbumHeaderRow renderizado
     * — el guardián `coverArtRequested` hace que las llamadas
     * repetidas entre recomposiciones no hagan nada tras la primera.
     * Las agrupaciones sintéticas "Sencillos" (sin álbum real, ver
     * UNKNOWN_ALBUM_LABEL) nunca se pasan aquí — no hay nada
     * significativo que buscar.
     */
    fun requestCoverArtIfMissing(artist: String, album: String) {
        val key = "$artist|$album"
        if (!coverArtRequested.add(key)) return

        val alreadyHasCover = _uiState.value.grouped[artist]?.get(album)
            ?.any { it.coverArtUrl != null } == true
        if (alreadyHasCover) return

        viewModelScope.launch {
            val url = coverArtRepository.resolveCoverArtUrl(artist, album)
            if (url != null) {
                repository.updateCoverArtForAlbum(artist, album, url)
            }
        }
    }

    /**
     * Deletes a download (PASO 5, H03): removes the physical .opus
     * file via SAF, then either deletes the row entirely (synthetic
     * rows from LibraryReconciler, which have no real youtubeId to
     * fall back to) or resets it to PENDING with a null filePath
     * (real, search-originated rows, which can be re-downloaded
     * later from SearchScreen).
     * ---
     * Elimina una descarga (PASO 5, H03): borra el archivo .opus
     * físico vía SAF, y después o bien elimina la fila entera (filas
     * sintéticas de LibraryReconciler, que no tienen un youtubeId
     * real al que volver) o la resetea a PENDING con filePath null
     * (filas reales originadas de una búsqueda, que pueden volver a
     * descargarse más adelante desde SearchScreen).
     */
    fun deleteDownload(track: SearchResultTrack) {
        viewModelScope.launch {
            track.filePath?.let { path ->
                DocumentFile.fromSingleUri(context, Uri.parse(path))?.delete()
            }
            if (track.youtubeId.startsWith(LibraryReconciler.LOCAL_ID_PREFIX)) {
                repository.delete(track)
            } else {
                repository.clearDownload(track.youtubeId)
            }
        }
    }

    private fun recompute() {
        val query = _uiState.value.filterQuery.trim().lowercase()
        val favoritesOnly = _uiState.value.showFavoritesOnly

        val base = if (favoritesOnly) {
            allDownloaded.filter { it.isFavorite }
        } else {
            allDownloaded
        }

        val filtered = if (query.isEmpty()) {
            base
        } else {
            base.filter { track ->
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

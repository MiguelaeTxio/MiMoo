package com.miguelaetxio.mimoo.ui.favorites

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.favorites.FavoritePlaylistRow
import com.miguelaetxio.mimoo.data.favorites.FavoritesRepository
import com.miguelaetxio.mimoo.data.favorites.FavoriteTrackRow
import com.miguelaetxio.mimoo.data.favorites.PopurriRepository
import com.miguelaetxio.mimoo.data.local.entity.FavoriteAlbum
import com.miguelaetxio.mimoo.data.local.entity.FavoriteArtist
import com.miguelaetxio.mimoo.data.local.entity.FavoriteTrack
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FavoritesTab { ARTISTS, ALBUMS, TRACKS, PLAYLISTS }

/** Clave de selección de álbum -- FavoriteAlbum no es Parcelable/no hace falta, basta el par (artist, album). */
data class AlbumKey(val artist: String, val album: String)

data class FavoritesUiState(
    val tab: FavoritesTab = FavoritesTab.ARTISTS,
    val artists: List<FavoriteArtist> = emptyList(),
    val albums: List<FavoriteAlbum> = emptyList(),
    val tracks: List<FavoriteTrackRow> = emptyList(),
    val playlists: List<FavoritePlaylistRow> = emptyList(),
    val selectedArtists: Set<String> = emptySet(),
    val selectedAlbums: Set<AlbumKey> = emptySet(),
    val isGeneratingPopurri: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * ViewModel de la pantalla unificada de Favoritos (sesión de diseño,
 * 2026-08-02): cuatro pestañas (Artistas/Álbumes/Sencillos/Listas),
 * selección múltiple con casillas para Artistas/Álbumes (generan
 * popurrí a partir de la selección), reproducción directa de TODOS
 * los sencillos favoritos sin selección, y apertura de las listas de
 * reproducción propias marcadas como favoritas.
 * ---
 * ViewModel for the unified Favorites screen (design session,
 * 2026-08-02): four tabs (Artists/Albums/Singles/Playlists), checkbox
 * multi-select for Artists/Albums (generates a popurrí from the
 * selection), direct playback of ALL favorite singles with no
 * selection needed, and opening the user's own playlists marked
 * favorite.
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val popurriRepository: PopurriRepository,
    private val playerManager: PlayerManager,
    private val autoSyncPusher: AutoSyncPusher,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            favoritesRepository.getFavoriteArtists().collect { artists ->
                _uiState.value = _uiState.value.copy(
                    artists = artists,
                    // Al desaparecer un favorito de la lista (se desmarcó desde ArtistScreen), su selección deja de tener sentido.
                    selectedArtists = _uiState.value.selectedArtists
                        .filter { name -> artists.any { it.artist == name } }
                        .toSet(),
                )
            }
        }
        viewModelScope.launch {
            favoritesRepository.getFavoriteAlbums().collect { albums ->
                _uiState.value = _uiState.value.copy(
                    albums = albums,
                    selectedAlbums = _uiState.value.selectedAlbums
                        .filter { key -> albums.any { it.artist == key.artist && it.album == key.album } }
                        .toSet(),
                )
            }
        }
        viewModelScope.launch {
            favoritesRepository.getFavoriteTracks().collect { tracks ->
                _uiState.value = _uiState.value.copy(tracks = tracks)
            }
        }
        viewModelScope.launch {
            favoritesRepository.getFavoritePlaylists().collect { playlists ->
                _uiState.value = _uiState.value.copy(playlists = playlists)
            }
        }
    }

    fun selectTab(tab: FavoritesTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    // --- Selección (Artistas) ---

    fun toggleArtistSelection(artist: String) {
        val current = _uiState.value.selectedArtists
        _uiState.value = _uiState.value.copy(
            selectedArtists = if (artist in current) current - artist else current + artist,
        )
    }

    fun toggleSelectAllArtists() {
        val state = _uiState.value
        val allNames = state.artists.map { it.artist }.toSet()
        _uiState.value = state.copy(
            selectedArtists = if (state.selectedArtists.size == allNames.size) emptySet() else allNames,
        )
    }

    // --- Selección (Álbumes) ---

    fun toggleAlbumSelection(key: AlbumKey) {
        val current = _uiState.value.selectedAlbums
        _uiState.value = _uiState.value.copy(
            selectedAlbums = if (key in current) current - key else current + key,
        )
    }

    fun toggleSelectAllAlbums() {
        val state = _uiState.value
        val allKeys = state.albums.map { AlbumKey(it.artist, it.album) }.toSet()
        _uiState.value = state.copy(
            selectedAlbums = if (state.selectedAlbums.size == allKeys.size) emptySet() else allKeys,
        )
    }

    // --- Marcar/desmarcar favorito ---
    // Bug real (2026-08-02, ver comentario de
    // ArtistViewModel.toggleFavorite()): estas cuatro mutaciones
    // tampoco pasaban por AutoSyncPusher.

    fun removeArtistFavorite(artist: String) {
        viewModelScope.launch {
            autoSyncPusher.executeIfConnected(appContext) { favoritesRepository.toggleArtist(artist) }
        }
    }

    fun removeAlbumFavorite(artist: String, album: String) {
        viewModelScope.launch {
            autoSyncPusher.executeIfConnected(appContext) { favoritesRepository.toggleAlbum(artist, album) }
        }
    }

    fun removeTrackFavorite(row: FavoriteTrackRow) {
        viewModelScope.launch {
            autoSyncPusher.executeIfConnected(appContext) {
                favoritesRepository.toggleTrack(
                    FavoriteTrack(
                        youtubeId = row.youtubeId,
                        title = row.title,
                        artist = row.artist,
                        thumbnailUrl = row.thumbnailUrl,
                        durationSeconds = 0,
                    ),
                )
            }
        }
    }

    fun removePlaylistFavorite(playlistId: Long) {
        viewModelScope.launch {
            autoSyncPusher.executeIfConnected(appContext) { favoritesRepository.togglePlaylist(playlistId) }
        }
    }

    // --- Generación y reproducción de popurrís ---

    fun playSelectedArtists(shuffle: Boolean) {
        val selected = _uiState.value.artists.filter { it.artist in _uiState.value.selectedArtists }
        if (selected.isEmpty()) return
        generateAndPlay(shuffle) { popurriRepository.buildFromArtists(selected) }
    }

    fun playSelectedAlbums(shuffle: Boolean) {
        val keys = _uiState.value.selectedAlbums
        val selected = _uiState.value.albums.filter { AlbumKey(it.artist, it.album) in keys }
        if (selected.isEmpty()) return
        generateAndPlay(shuffle) { popurriRepository.buildFromAlbums(selected) }
    }

    fun playAllFavoriteTracks(shuffle: Boolean) {
        generateAndPlay(shuffle) {
            popurriRepository.buildFromFavoriteTracks(
                favoriteTracks = pendingStreamingFavorites(),
                favoriteLocalTracks = pendingLocalFavorites(),
            )
        }
    }

    /** Los sencillos en streaming (sin fila local) de entre los favoritos unificados actuales. */
    private suspend fun pendingStreamingFavorites(): List<FavoriteTrack> =
        _uiState.value.tracks
            .filter { !it.isDownloaded }
            .map { FavoriteTrack(it.youtubeId, it.title, it.artist, it.thumbnailUrl, durationSeconds = 0) }

    private suspend fun pendingLocalFavorites() =
        favoritesRepository.getFavoriteLocalTracks().first()

    private fun generateAndPlay(shuffle: Boolean, builder: suspend () -> List<QueueItem>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingPopurri = true, errorMessage = null)
            try {
                val items = builder()
                if (items.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingPopurri = false,
                        errorMessage = "No se ha podido resolver ninguna pista para este popurrí.",
                    )
                    return@launch
                }
                if (shuffle) {
                    playerManager.playQueueShuffled(items)
                } else {
                    playerManager.playQueue(items)
                }
                _uiState.value = _uiState.value.copy(isGeneratingPopurri = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingPopurri = false,
                    errorMessage = e.message ?: "Error al generar el popurrí",
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

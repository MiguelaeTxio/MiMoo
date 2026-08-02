package com.miguelaetxio.mimoo.ui.playlist

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.local.entity.Playlist
import com.miguelaetxio.mimoo.data.local.repository.FavoritePlaylistRepository
import com.miguelaetxio.mimoo.data.local.repository.PlaylistRepository
import com.miguelaetxio.mimoo.util.SearchNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList(),
    // H08 PARTE 1 -- filteredPlaylists is a derived view consumed by
    // PlaylistsScreen.kt; playlists ALWAYS keeps the full list, same
    // pattern as Biblioteca (LibraryViewModel never replaces its base
    // list when filtering).
    // ---
    // H08 PARTE 1 -- filteredPlaylists es la vista derivada que
    // consume PlaylistsScreen.kt; playlists conserva SIEMPRE la lista
    // completa, mismo patrón que Biblioteca (LibraryViewModel no
    // sustituye su lista base al filtrar).
    val filterQuery: String = "",
    val filteredPlaylists: List<Playlist> = emptyList(),
    // H07 PARTE 1 -- aviso cuando una acción de crear/borrar se
    // rechaza por falta de conexión (regla de negocio de Miguel
    // Ángel, S008).
    val syncBlockedMessage: String? = null,
    // Sesión de diseño de Favoritos (2026-08-02): playlists propias marcadas como favoritas.
    val favoritePlaylistIds: Set<Long> = emptySet(),
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
    private val favoritePlaylistRepository: FavoritePlaylistRepository,
    private val autoSyncPusher: AutoSyncPusher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllPlaylists().collect { playlists ->
                _uiState.value = _uiState.value.copy(playlists = playlists)
                recompute()
            }
        }
        viewModelScope.launch {
            favoritePlaylistRepository.getAll().collect { favorites ->
                _uiState.value = _uiState.value.copy(
                    favoritePlaylistIds = favorites.map { it.playlistId }.toSet(),
                )
            }
        }
    }

    /** Sesión de diseño de Favoritos (2026-08-02): marcar/desmarcar una playlist propia como favorita. */
    fun toggleFavoritePlaylist(playlistId: Long) {
        viewModelScope.launch { favoritePlaylistRepository.toggle(playlistId) }
    }

    fun createPlaylist(activity: Activity, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(activity) {
                repository.createPlaylist(trimmed)
            }
            if (outcome is com.miguelaetxio.mimoo.data.backup.MutationOutcome.NoConnection) {
                _uiState.value = _uiState.value.copy(
                    syncBlockedMessage = "Sin conexión: no se puede crear la lista ahora mismo."
                )
            }
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.renamePlaylist(playlistId, trimmed) }
    }

    fun deletePlaylist(activity: Activity, playlistId: Long) {
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(activity) {
                repository.deletePlaylist(playlistId)
            }
            if (outcome is com.miguelaetxio.mimoo.data.backup.MutationOutcome.NoConnection) {
                _uiState.value = _uiState.value.copy(
                    syncBlockedMessage = "Sin conexión: no se puede borrar la lista ahora mismo."
                )
            }
        }
    }

    /** Descarta el aviso de mutación bloqueada por falta de conexión (H07 PARTE 1). */
    fun dismissSyncBlockedMessage() {
        _uiState.value = _uiState.value.copy(syncBlockedMessage = null)
    }

    /**
     * H08 PARTE 1 -- text filter for the Playlists screen. Matches
     * accent- and case-insensitively via `SearchNormalizer` (fixes a
     * real gap found in the equivalent Biblioteca filter, which only
     * lowercased -- corrected there too, not just avoided here).
     * ---
     * H08 PARTE 1 -- filtro de texto de la pantalla de Playlists.
     * Coincide sin distinguir acentos ni mayúsculas vía
     * `SearchNormalizer` (corrige un hueco real encontrado en el
     * filtro equivalente de Biblioteca, que solo hacía lowercase --
     * corregido también allí, no solo evitado aquí).
     */
    fun onFilterQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(filterQuery = query)
        recompute()
    }

    private fun recompute() {
        val query = SearchNormalizer.normalize(_uiState.value.filterQuery)
        val playlists = _uiState.value.playlists
        val filtered = if (query.isEmpty()) {
            playlists
        } else {
            playlists.filter { SearchNormalizer.normalize(it.name).contains(query) }
        }
        _uiState.value = _uiState.value.copy(filteredPlaylists = filtered)
    }
}

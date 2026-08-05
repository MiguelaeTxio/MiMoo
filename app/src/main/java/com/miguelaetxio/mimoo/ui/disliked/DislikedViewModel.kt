package com.miguelaetxio.mimoo.ui.disliked

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.local.entity.DislikedArtist
import com.miguelaetxio.mimoo.data.local.entity.DislikedTrack
import com.miguelaetxio.mimoo.data.local.repository.DislikedArtistRepository
import com.miguelaetxio.mimoo.data.local.repository.DislikedTrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DislikedTab { ARTISTS, TRACKS }

data class DislikedUiState(
    val tab: DislikedTab = DislikedTab.ARTISTS,
    val artists: List<DislikedArtist> = emptyList(),
    val tracks: List<DislikedTrack> = emptyList(),
)

/**
 * ViewModel de la pantalla de gestión (CRUD) de la Lista Negra -- H16.
 * Alcance del CRUD, cerrado con Miguel Ángel en S029: SOLO ver/borrar
 * lo ya añadido desde el ExoPlayer o el Explorador, sin alta manual
 * escribiendo un nombre -- ver `ANNEX_H16.md`, "Puntos de diseño --
 * CERRADOS", punto 4. Mismo patrón de mutación que
 * FavoritesViewModel: nunca se llama al repositorio directamente,
 * siempre a través de AutoSyncPusher.executeIfConnected() (regla de
 * negocio H07 PARTE 1, "sin conectividad no hay mutación").
 * ---
 * ViewModel for the Blacklist management (CRUD) screen -- H16. CRUD
 * scope, closed with Miguel Ángel in S029: view/delete ONLY what was
 * already added from the ExoPlayer or the Explorer, no manual
 * addition by typing a name -- see `ANNEX_H16.md`, "Puntos de diseño
 * -- CERRADOS", point 4. Same mutation pattern as FavoritesViewModel:
 * the repository is never called directly, always through
 * AutoSyncPusher.executeIfConnected() (H07 PART 1 business rule, "no
 * connectivity means no mutation").
 */
@HiltViewModel
class DislikedViewModel @Inject constructor(
    private val dislikedArtistRepository: DislikedArtistRepository,
    private val dislikedTrackRepository: DislikedTrackRepository,
    private val autoSyncPusher: AutoSyncPusher,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DislikedUiState())
    val uiState: StateFlow<DislikedUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dislikedArtistRepository.getAll().collect { artists ->
                _uiState.value = _uiState.value.copy(artists = artists)
            }
        }
        viewModelScope.launch {
            dislikedTrackRepository.getAll().collect { tracks ->
                _uiState.value = _uiState.value.copy(tracks = tracks)
            }
        }
    }

    fun selectTab(tab: DislikedTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    fun removeArtist(artist: String) {
        viewModelScope.launch {
            autoSyncPusher.executeIfConnected(appContext) { dislikedArtistRepository.remove(artist) }
        }
    }

    fun removeTrack(artist: String, title: String) {
        viewModelScope.launch {
            autoSyncPusher.executeIfConnected(appContext) { dislikedTrackRepository.remove(artist, title) }
        }
    }
}

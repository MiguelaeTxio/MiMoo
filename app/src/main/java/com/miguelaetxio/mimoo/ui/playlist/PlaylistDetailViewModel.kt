package com.miguelaetxio.mimoo.ui.playlist

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.backup.MutationOutcome
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.PlaylistRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import com.miguelaetxio.mimoo.data.playback.StreamResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val tracks: List<SearchResultTrack> = emptyList(),
    val isResolving: Boolean = false,
    val resolveError: String? = null,
    // H07 PARTE 1 -- aviso cuando quitar una pista se rechaza por falta de conexión.
    val syncBlockedMessage: String? = null,
)

/**
 * ViewModel for a single playlist's detail screen (Hito 04): ordered
 * track list, remove, reorder (up/down), play all.
 *
 * playlistId comes from navigation arguments rather than the
 * constructor directly — SavedStateHandle is the standard Hilt+Nav
 * Compose pattern for that, but is intentionally left for the actual
 * NavGraph wiring step rather than guessed here; this constructor
 * signature may need adjusting once Screen.PlaylistDetail's argument
 * name is fixed in NavGraph.kt.
 * ---
 * ViewModel de la pantalla de detalle de una playlist (Hito 04):
 * lista de pistas en orden, quitar, reordenar (subir/bajar),
 * reproducir todo.
 *
 * playlistId llega de los argumentos de navegación en vez de ir
 * directo al constructor — SavedStateHandle es el patrón estándar de
 * Hilt+Nav Compose para eso, pero se deja intencionadamente para el
 * propio paso de conexión de NavGraph en vez de adivinarlo aquí; esta
 * firma de constructor puede necesitar ajuste en cuanto se fije el
 * nombre del argumento de Screen.PlaylistDetail en NavGraph.kt.
 */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val playerManager: PlayerManager,
    private val streamResolver: StreamResolver,
    private val autoSyncPusher: AutoSyncPusher,
    private val shareCodeRepository: com.miguelaetxio.mimoo.data.share.ShareCodeRepository,
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    private val playlistId: Long =
        checkNotNull(savedStateHandle.get<Long>("playlistId")) {
            "PlaylistDetailViewModel requires a playlistId nav argument"
        }

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    /** H10 (S011, niveles 7/8) -- Uri del archivo .txt de esta lista, mismo patrón que Ajustes/Biblioteca. */
    private val _generatedShareFileUri = MutableStateFlow<android.net.Uri?>(null)
    val generatedShareFileUri: StateFlow<android.net.Uri?> = _generatedShareFileUri.asStateFlow()

    fun shareReplica() {
        viewModelScope.launch {
            _generatedShareFileUri.value = shareCodeRepository.buildPlaylistShareFile(playlistId)
        }
    }

    fun consumeGeneratedShareFileUri() {
        _generatedShareFileUri.value = null
    }

    init {
        viewModelScope.launch {
            repository.getTracksForPlaylist(playlistId).collect { tracks ->
                _uiState.value = _uiState.value.copy(tracks = tracks)
            }
        }
    }

    fun removeTrack(activity: Activity, youtubeId: String) {
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(activity) {
                repository.removeTrackFromPlaylist(playlistId, youtubeId)
            }
            if (outcome is MutationOutcome.NoConnection) {
                _uiState.value = _uiState.value.copy(
                    syncBlockedMessage = "Sin conexión: no se puede quitar la pista ahora mismo."
                )
            }
        }
    }

    /** Descarta el aviso de mutación bloqueada por falta de conexión (H07 PARTE 1). */
    fun dismissSyncBlockedMessage() {
        _uiState.value = _uiState.value.copy(syncBlockedMessage = null)
    }

    /**
     * Swaps a track with its neighbor and persists both positions.
     * Minimum-viable reordering per mimoo-annex-v04 PASO 3 — drag and
     * drop is explicitly out of scope for this hito.
     * ---
     * Intercambia una pista con su vecina y persiste ambas
     * posiciones. Reordenación mínima viable según mimoo-annex-v04
     * PASO 3 — el drag and drop queda explícitamente fuera de alcance
     * de este hito.
     */
    fun moveTrack(fromIndex: Int, direction: Int) {
        val tracks = _uiState.value.tracks
        val toIndex = fromIndex + direction
        if (toIndex !in tracks.indices || fromIndex !in tracks.indices) return

        val a = tracks[fromIndex]
        val b = tracks[toIndex]
        viewModelScope.launch {
            repository.updatePosition(playlistId, a.youtubeId, toIndex)
            repository.updatePosition(playlistId, b.youtubeId, fromIndex)
        }
    }

    /**
     * Plays the whole playlist in saved order. Downloaded tracks
     * (filePath != null) play locally; tracks that were never
     * downloaded resolve a live stream URL via StreamResolver first
     * — same call SearchViewModel.playTrack uses
     * (streamResolver.resolveAudioStreamUrl(track.youtubeUrl)).
     * Resolution runs sequentially and a track whose resolution fails
     * is skipped rather than aborting the whole queue, since one dead
     * link should not block playback of the rest of the playlist.
     * ---
     * Reproduce la playlist completa en el orden guardado. Las pistas
     * descargadas (filePath != null) reproducen en local; las que
     * nunca se descargaron resuelven primero una URL de streaming en
     * vivo vía StreamResolver — la misma llamada que usa
     * SearchViewModel.playTrack
     * (streamResolver.resolveAudioStreamUrl(track.youtubeUrl)). La
     * resolución se ejecuta de forma secuencial y una pista cuya
     * resolución falla se omite en vez de abortar toda la cola, ya
     * que un enlace muerto no debería bloquear la reproducción del
     * resto de la lista.
     */
    fun playAll() {
        val tracks = _uiState.value.tracks
        if (tracks.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isResolving = true,
                resolveError = null,
            )
            var resolutionFailures = 0
            val items = tracks.mapNotNull { track ->
                val localPath = track.filePath
                val remoteUrl = track.youtubeUrl
                if (localPath != null) {
                    QueueItem(
                        uri = localPath,
                        title = track.title,
                        isLocal = true,
                        artist = track.artist ?: track.channelTitle,
                        youtubeId = track.youtubeId,
                        channelTitle = track.channelTitle,
                        artworkUri = track.coverArtUrl ?: track.thumbnailUrl,
                    )
                } else if (remoteUrl == null) {
                    // Pista sintética (local:) sin filePath -- caso
                    // extremo que no debería darse nunca en la
                    // práctica (las sintéticas siempre vienen de un
                    // archivo real en disco), pero sin URL real de
                    // YouTube que resolver no hay nada que reproducir.
                    // ---
                    // Synthetic (local:) track with no filePath --
                    // edge case that shouldn't happen in practice
                    // (synthetic tracks always come from a real disk
                    // file), but with no real YouTube URL to resolve
                    // there's nothing to play.
                    resolutionFailures++
                    null
                } else {
                    try {
                        val streamUrl = streamResolver.resolveAudioStreamUrl(remoteUrl)
                        QueueItem(
                            uri = streamUrl,
                            title = track.title,
                            isLocal = false,
                            artist = track.artist ?: track.channelTitle,
                            youtubeId = track.youtubeId,
                            channelTitle = track.channelTitle,
                            artworkUri = track.coverArtUrl ?: track.thumbnailUrl,
                        )
                    } catch (e: Exception) {
                        resolutionFailures++
                        null
                    }
                }
            }
            _uiState.value = _uiState.value.copy(
                isResolving = false,
                resolveError = if (resolutionFailures > 0) {
                    "No se pudieron resolver $resolutionFailures pista(s); " +
                        "se reproduce el resto."
                } else {
                    null
                },
            )
            if (items.isNotEmpty()) {
                playerManager.playQueue(items)
            }
        }
    }

    /** Dismisses a previously shown resolve-error banner. */
    fun dismissResolveError() {
        _uiState.value = _uiState.value.copy(resolveError = null)
    }
}

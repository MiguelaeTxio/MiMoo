package com.miguelaetxio.mimoo.ui.queue

import androidx.lifecycle.ViewModel
import com.miguelaetxio.mimoo.data.playback.PlaybackState
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Backs QueueScreen -- la cola de reproducción de SESIÓN, en memoria,
 * distinta de las Playlists guardadas (PlaylistDetailScreen/Room).
 * Petición explícita de Miguel Ángel (2026-07-05). Envoltorio fino
 * sobre PlayerManager, mismo patrón que PlayerBarViewModel.
 * ---
 * Sostiene QueueScreen -- la cola de reproducción de SESIÓN, en
 * memoria, distinta de las Playlists guardadas
 * (PlaylistDetailScreen/Room). Explicit request from Miguel Ángel
 * (2026-07-05). Thin wrapper over PlayerManager, same pattern as
 * PlayerBarViewModel.
 */
@HiltViewModel
class QueueViewModel @Inject constructor(
    private val playerManager: PlayerManager,
) : ViewModel() {

    val queue: StateFlow<List<QueueItem>> = playerManager.queue
    val playbackState: StateFlow<PlaybackState> = playerManager.state

    fun playAtIndex(index: Int) = playerManager.playAtIndex(index)

    fun removeFromQueue(index: Int) = playerManager.removeFromQueue(index)

    /**
     * Mueve una pista de `fromIndex` a `toIndex` -- usado por el
     * drag-and-drop de QueueScreen (petición explícita de Miguel
     * Ángel, 2026-07-05: "una vista de la lista donde se pueda hacer
     * drag and drop de los temas para reorganizarlos").
     * ---
     * Moves a track from `fromIndex` to `toIndex` -- used by
     * QueueScreen's drag-and-drop (explicit request from Miguel Ángel,
     * 2026-07-05: "a list view where you can drag and drop tracks to
     * reorganize them").
     */
    fun moveTo(fromIndex: Int, toIndex: Int) = playerManager.moveInQueue(fromIndex, toIndex)

    fun togglePlayPause() {
        if (playbackState.value.isPlaying) {
            playerManager.pause()
        } else {
            playerManager.resume()
        }
    }

    fun clearQueue() = playerManager.clearQueue()
}

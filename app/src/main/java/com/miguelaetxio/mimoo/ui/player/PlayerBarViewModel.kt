package com.miguelaetxio.mimoo.ui.player

import androidx.lifecycle.ViewModel
import com.miguelaetxio.mimoo.data.playback.PlaybackState
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerBarViewModel @Inject constructor(
    private val playerManager: PlayerManager,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = playerManager.state

    fun togglePlayPause() {
        if (state.value.isPlaying) playerManager.pause() else playerManager.resume()
    }

    fun playNext() = playerManager.playNext()

    fun playPrevious() = playerManager.playPrevious()

    /** H07 PARTE 3. */
    fun toggleRepeat() = playerManager.toggleRepeatMode()

    /** H07 PARTE 3. */
    fun toggleShuffle() = playerManager.toggleShuffleMode()
}

package com.miguelaetxio.mimoo.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.playback.PlaybackState
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerBarViewModel @Inject constructor(
    private val playerManager: PlayerManager,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = playerManager.state

    /**
     * H08 -- ExoPlayer no dispara ningún evento continuo de "la
     * posición ha cambiado" mientras suena (solo eventos puntuales:
     * cambio de pista, play/pausa...). Para la barra de progreso hace
     * falta sondear currentPositionMs() a intervalos cortos -- patrón
     * estándar en apps de reproducción con ExoPlayer/Media3, no hay
     * alternativa "reactiva" razonable para esto. 500ms es
     * suficientemente fino para que la barra se vea fluida sin
     * sondear en exceso.
     * ---
     * H08 -- ExoPlayer fires no continuous "position changed" event
     * while playing (only discrete events: track change, play/pause...).
     * The progress bar needs to poll currentPositionMs() at short
     * intervals -- standard pattern in ExoPlayer/Media3 playback apps,
     * there's no reasonable "reactive" alternative for this. 500ms is
     * fine-grained enough for the bar to look smooth without
     * over-polling.
     */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                _positionMs.value = playerManager.currentPositionMs()
                delay(500)
            }
        }
    }

    fun togglePlayPause() {
        if (state.value.isPlaying) playerManager.pause() else playerManager.resume()
    }

    fun playNext() = playerManager.playNext()

    fun playPrevious() = playerManager.playPrevious()

    /** H07 PARTE 3. */
    fun toggleRepeat() = playerManager.toggleRepeatMode()

    /** H07 PARTE 3. */
    fun toggleShuffle() = playerManager.toggleShuffleMode()

    /** H08 -- arrastrar la barra de progreso. */
    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
        _positionMs.value = positionMs
    }
}

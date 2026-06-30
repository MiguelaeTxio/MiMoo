package com.miguelaetxio.mimoo.data.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTitle: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * Wraps a single ExoPlayer instance for audio-only streaming playback
 * of URLs resolved by StreamResolver.
 * ---
 * Encapsula una única instancia de ExoPlayer para reproducción en
 * streaming de solo audio de URLs resueltas por StreamResolver.
 */
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    init {
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }
        })
    }

    fun play(streamUrl: String, title: String) {
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
        player.play()
        _state.value = _state.value.copy(currentTitle = title)
    }

    fun pause() = player.pause()

    fun resume() = player.play()

    fun currentPositionMs(): Long = player.currentPosition

    fun durationMs(): Long = player.duration.coerceAtLeast(0L)

    fun release() = player.release()
}

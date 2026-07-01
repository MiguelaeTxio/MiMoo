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
    val isLocal: Boolean = false,
)

/**
 * Wraps a single ExoPlayer instance for audio-only playback, either
 * from a local SAF file (downloaded track) or a streaming URL
 * resolved by StreamResolver.
 * ---
 * Encapsula una única instancia de ExoPlayer para reproducción de
 * solo audio, ya sea desde un archivo SAF local (pista descargada)
 * o una URL de streaming resuelta por StreamResolver.
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

    /**
     * Plays the given URI. isLocal indicates whether the URI points
     * to a local SAF file (content://) or a remote streaming URL,
     * for UI purposes only — playback itself works identically for
     * both, since ExoPlayer's DefaultDataSource resolves the scheme
     * automatically.
     * ---
     * Reproduce la URI dada. isLocal indica si la URI apunta a un
     * archivo SAF local (content://) o a una URL de streaming
     * remota, solo con fines de UI — la reproducción en sí funciona
     * igual para ambos casos, ya que DefaultDataSource de ExoPlayer
     * resuelve el esquema automáticamente.
     */
    fun play(streamUrl: String, title: String, isLocal: Boolean = false) {
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
        player.play()
        _state.value = _state.value.copy(
            currentTitle = title,
            isLocal = isLocal,
        )
    }

    fun pause() = player.pause()

    fun resume() = player.play()

    fun currentPositionMs(): Long = player.currentPosition

    fun durationMs(): Long = player.duration.coerceAtLeast(0L)

    fun release() = player.release()
}

package com.miguelaetxio.mimoo.data.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single entry in the playback queue.
 * ---
 * Una entrada de la cola de reproducción.
 */
data class QueueItem(
    val uri: String,
    val title: String,
    val isLocal: Boolean,
)

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTitle: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLocal: Boolean = false,
    val queueIndex: Int = -1,
    val queueSize: Int = 0,
)

/**
 * Wraps a single ExoPlayer instance for audio-only playback, either
 * ad-hoc (single track, e.g. from SearchScreen) or as a sequential
 * queue (e.g. a whole album or artist from the Biblioteca screen).
 * Auto-advances to the next queue item when a track finishes.
 * ---
 * Encapsula una única instancia de ExoPlayer para reproducción de
 * solo audio, ya sea puntual (una pista, p.ej. desde SearchScreen) o
 * como cola secuencial (p.ej. un álbum o artista completo desde la
 * Biblioteca). Avanza automáticamente al siguiente elemento de la
 * cola cuando termina una pista.
 */
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    /**
     * Público (antes privado) para que MiMooPlaybackService pueda
     * envolver esta MISMA instancia en un MediaSession -- al ser
     * PlayerManager un @Singleton de Hilt, el Service y todos los
     * ViewModels comparten exactamente el mismo ExoPlayer, así que
     * nada más en la app necesita cambiar: se sigue llamando a
     * PlayerManager.play()/playQueue() exactamente igual que antes.
     * ---
     * Public (was private) so MiMooPlaybackService can wrap this SAME
     * instance in a MediaSession -- since PlayerManager is a Hilt
     * @Singleton, the Service and every ViewModel share the exact same
     * ExoPlayer, so nothing else in the app needs to change: it's
     * still PlayerManager.play()/playQueue() exactly as before.
     */
    val player: ExoPlayer = ExoPlayer.Builder(appContext).build()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    private var queue: List<QueueItem> = emptyList()
    private var currentIndex: Int = -1

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                // Arranca (o promociona a primer plano) el servicio de
                // reproducción en cuanto empieza a sonar algo -- bug
                // real reportado por Miguel Ángel (2026-07-04): sin
                // esto, el ExoPlayer vivía solo en un singleton sin
                // ningún servicio en primer plano, y el sistema podía
                // (y lo hizo) matar el proceso entero al cerrar otra
                // app y reclamar memoria, sin dejar ningún rastro en
                // crash_log.txt. ContextCompat.startForegroundService
                // es idempotente -- llamarlo con el servicio ya
                // arrancado no hace nada malo.
                // ---
                // Starts (or promotes to foreground) the playback
                // service as soon as something starts playing -- real
                // bug reported by Miguel Ángel (2026-07-04): without
                // this, the ExoPlayer lived alone in a singleton with
                // no foreground service at all, and the system could
                // (and did) kill the entire process when another app
                // closed and memory was reclaimed, leaving no trace in
                // crash_log.txt. ContextCompat.startForegroundService
                // is idempotent -- calling it with the service already
                // running does nothing harmful.
                if (isPlaying) {
                    ContextCompat.startForegroundService(
                        appContext,
                        Intent(appContext, MiMooPlaybackService::class.java),
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    playNext()
                }
            }
        })
    }

    /**
     * Plays a single ad-hoc track, replacing any existing queue with
     * a one-item queue containing just this track. Used by
     * SearchScreen's individual play button.
     * ---
     * Reproduce una pista puntual, sustituyendo cualquier cola
     * existente por una cola de un solo elemento con esta pista.
     * Usado por el botón de reproducción individual de SearchScreen.
     */
    fun play(streamUrl: String, title: String, isLocal: Boolean = false) {
        playQueue(
            listOf(QueueItem(streamUrl, title, isLocal)),
            startIndex = 0,
        )
    }

    /**
     * Replaces the queue and starts playback at startIndex. Used by
     * the Biblioteca screen for album/artist/shuffle playback.
     * ---
     * Sustituye la cola y comienza la reproducción en startIndex.
     * Usado por la pantalla de Biblioteca para reproducción de
     * álbum/artista/aleatorio.
     */
    fun playQueue(items: List<QueueItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        queue = items
        currentIndex = startIndex.coerceIn(0, items.lastIndex)
        playCurrent()
    }

    private fun playCurrent() {
        val item = queue.getOrNull(currentIndex) ?: return
        player.setMediaItem(MediaItem.fromUri(item.uri))
        player.prepare()
        player.play()
        _state.value = _state.value.copy(
            currentTitle = item.title,
            isLocal = item.isLocal,
            queueIndex = currentIndex,
            queueSize = queue.size,
        )
    }

    /**
     * Advances to the next queue item, if any. Called automatically
     * when a track ends, and available for manual skip.
     * ---
     * Avanza al siguiente elemento de la cola, si lo hay. Se llama
     * automáticamente al terminar una pista, y también está
     * disponible para avance manual.
     */
    fun playNext() {
        if (currentIndex < queue.lastIndex) {
            currentIndex++
            playCurrent()
        }
    }

    /** Goes back to the previous queue item, if any. */
    fun playPrevious() {
        if (currentIndex > 0) {
            currentIndex--
            playCurrent()
        }
    }

    fun pause() = player.pause()

    fun resume() = player.play()

    fun currentPositionMs(): Long = player.currentPosition

    fun durationMs(): Long = player.duration.coerceAtLeast(0L)

    fun release() = player.release()
}

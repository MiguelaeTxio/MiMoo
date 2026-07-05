package com.miguelaetxio.mimoo.data.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.miguelaetxio.mimoo.data.download.StorageManager
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
 * Wraps a single ExoPlayer instance for audio-only playback, y
 * gestiona la COLA DE REPRODUCCIÓN DE SESIÓN -- petición explícita de
 * Miguel Ángel (2026-07-05): "hay lo que es una lista de reproducción
 * actual... una lista temporal de lo que se está oyendo... una vez
 * que cierras la app, la lista desaparece". Esta cola vive SOLO en
 * memoria (el propio `_queue` de este @Singleton de Hilt) -- nunca se
 * persiste en Room, así que desaparece sola al matar el proceso,
 * exactamente como se pidió. No confundir con las Playlists guardadas
 * (PlaylistRepository/Room), que son un concepto totalmente distinto
 * y persistente.
 *
 * Semántica clave, tal como la describió Miguel Ángel:
 *   - "Reproducir" un álbum/pista/lista NUNCA sustituye la cola
 *     entera -- se INSERTA justo después de la pista actual y se
 *     salta a reproducirla ya. Lo que quedaba en cola después de la
 *     pista que sonaba antes se conserva y sigue sonando después
 *     (playQueue()).
 *   - "Añadir a la cola" añade al FINAL sin interrumpir lo que suena
 *     (addToQueue()).
 *   - La pantalla de gestión de la cola (QueueScreen) puede reordenar,
 *     quitar pistas, saltar a una concreta, o vaciarla entera.
 * ---
 * Envuelve una única instancia de ExoPlayer para reproducción de solo
 * audio, y gestiona la COLA DE REPRODUCCIÓN DE SESIÓN -- explicit
 * request from Miguel Ángel (2026-07-05): "there's a thing called the
 * current playlist... a temporary list of what's playing... once you
 * close the app, the list disappears". This queue lives ONLY in
 * memory (this Hilt @Singleton's own `_queue`) -- it's never persisted
 * to Room, so it disappears on its own when the process dies, exactly
 * as requested. Not to be confused with saved Playlists
 * (PlaylistRepository/Room), which are a completely different,
 * persistent concept.
 *
 * Key semantics, as described by Miguel Ángel:
 *   - "Playing" an album/track/playlist NEVER replaces the whole
 *     queue -- it gets INSERTED right after the current track and
 *     jumped to immediately. Whatever was queued after the
 *     previously-playing track is preserved and keeps playing
 *     afterwards (playQueue()).
 *   - "Add to queue" appends at the END without interrupting what's
 *     currently playing (addToQueue()).
 *   - The queue management screen (QueueScreen) can reorder, remove
 *     tracks, jump to a specific one, or clear it entirely.
 */
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val storageManager: StorageManager,
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

    /**
     * La cola de sesión en sí, expuesta para QueueScreen -- ver el
     * comentario de clase. Solo en memoria, nunca en Room.
     * ---
     * The session queue itself, exposed for QueueScreen -- see the
     * class comment. In memory only, never in Room.
     */
    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue

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
     * Reproduce una pista puntual -- misma semántica de inserción que
     * playQueue() (ver comentario de clase), con una lista de un solo
     * elemento. Usado por el botón de reproducción individual de
     * SearchScreen/Biblioteca.
     * ---
     * Plays a single ad-hoc track -- same insertion semantics as
     * playQueue() (see class comment), with a one-item list. Used by
     * the individual play button in SearchScreen/Biblioteca.
     */
    fun play(streamUrl: String, title: String, isLocal: Boolean = false) {
        playQueue(listOf(QueueItem(streamUrl, title, isLocal)), startIndex = 0)
    }

    /**
     * Inserta `items` justo después de la pista actual en la cola de
     * sesión y salta a reproducirlos de inmediato -- NUNCA sustituye
     * la cola entera. Lo que quedaba en cola después de la pista que
     * sonaba antes se conserva intacto justo después de estos nuevos
     * items, y seguirá sonando en cuanto termine este bloque nuevo.
     * `startIndex` elige por cuál de los `items` insertados empezar
     * (0 = el primero, comportamiento por defecto de todos los
     * llamantes actuales).
     *
     * Petición explícita de Miguel Ángel (2026-07-05): "si voy ahora a
     * otro álbum y le digo reproducir ahora, me lo mete justo en la
     * lista de reproducción. Cuando ese álbum acabe, seguirá con lo
     * que quedaba del álbum anterior".
     * ---
     * Inserts `items` right after the current track in the session
     * queue and jumps to playing them immediately -- NEVER replaces
     * the whole queue. Whatever was queued after the previously-
     * playing track is kept intact right after these new items, and
     * will keep playing once this new block finishes. `startIndex`
     * picks which of the inserted `items` to start from (0 = the
     * first one, the default behavior of every current caller).
     *
     * Explicit request from Miguel Ángel (2026-07-05): "if I go to
     * another album now and hit play now, it gets inserted right into
     * the playback queue. When that album ends, it'll continue with
     * what was left of the previous album".
     */
    fun playQueue(items: List<QueueItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        val insertAt = (currentIndex + 1).coerceIn(0, _queue.value.size)
        val newQueue = _queue.value.toMutableList().apply {
            addAll(insertAt, items)
        }
        _queue.value = newQueue
        currentIndex = insertAt + startIndex.coerceIn(0, items.lastIndex)
        playCurrent()
    }

    /**
     * "Reproducir a continuación" -- inserta `items` justo después de
     * la pista actual, SIN interrumpir lo que suena ahora mismo:
     * sonarán en cuanto termine la pista actual, antes que lo que ya
     * hubiera después en la cola. Distinto de playQueue() (interrumpe
     * y salta ya) y de addToQueue() (va al final). Petición explícita
     * de Miguel Ángel (2026-07-05): "reproducir ahora, reproducir a
     * continuación, añadir al final" como tres acciones separadas.
     * ---
     * "Play next" -- inserts `items` right after the current track,
     * WITHOUT interrupting whatever is playing right now: they'll
     * play as soon as the current track ends, ahead of whatever was
     * already queued after it. Distinct from playQueue() (interrupts
     * and jumps immediately) and addToQueue() (goes to the end).
     * Explicit request from Miguel Ángel (2026-07-05): "play now, play
     * next, add to the end" as three separate actions.
     */
    fun insertNext(items: List<QueueItem>) {
        if (items.isEmpty()) return
        val insertAt = (currentIndex + 1).coerceIn(0, _queue.value.size)
        _queue.value = _queue.value.toMutableList().apply {
            addAll(insertAt, items)
        }
        _state.value = _state.value.copy(queueSize = _queue.value.size)
    }

    /**
     * Añade `items` al FINAL de la cola de sesión sin interrumpir lo
     * que suena ahora mismo -- "añadir a la lista de reproducción
     * actual" pedido por Miguel Ángel (2026-07-05), distinto de
     * playQueue()/"reproducir ahora".
     * ---
     * Appends `items` to the END of the session queue without
     * interrupting whatever is currently playing -- "add to the
     * current playback queue" requested by Miguel Ángel (2026-07-05),
     * distinct from playQueue()/"play now".
     */
    fun addToQueue(items: List<QueueItem>) {
        if (items.isEmpty()) return
        _queue.value = _queue.value + items
        _state.value = _state.value.copy(queueSize = _queue.value.size)
    }

    private fun playCurrent() {
        val item = _queue.value.getOrNull(currentIndex) ?: return
        // MediaMetadata real (antes se reproducía con MediaItem.fromUri()
        // a secas, sin título) -- DefaultMediaNotificationProvider usa
        // esto para el título de la notificación con controles reales.
        // Diagnóstico de por qué la notificación no muestra controles
        // (2026-07-05, ver NotificationDebugLogger): puede que Media3
        // necesite metadatos para considerar la sesión "lista".
        // ---
        // Real MediaMetadata (previously played with a bare
        // MediaItem.fromUri(), no title at all) -- DefaultMediaNotificationProvider
        // uses this for the title of the real-controls notification.
        // Diagnosing why the notification shows no controls (2026-07-05,
        // see NotificationDebugLogger): Media3 may need metadata to
        // consider the session "ready".
        val mediaItem = MediaItem.Builder()
            .setUri(item.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setDisplayTitle(item.title)
                    .build()
            )
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        _state.value = _state.value.copy(
            currentTitle = item.title,
            isLocal = item.isLocal,
            queueIndex = currentIndex,
            queueSize = _queue.value.size,
        )
        NotificationDebugLogger.log(
            appContext, storageManager,
            "playCurrent() -- index=$currentIndex title=\"${item.title}\" " +
                "commands=${player.availableCommands}",
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
        if (currentIndex < _queue.value.lastIndex) {
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

    /**
     * Salta directamente a una posición concreta de la cola --
     * gestión manual desde QueueScreen (tocar una pista de la lista).
     * ---
     * Jumps directly to a specific queue position -- manual management
     * from QueueScreen (tapping a track in the list).
     */
    fun playAtIndex(index: Int) {
        if (index in _queue.value.indices) {
            currentIndex = index
            playCurrent()
        }
    }

    /**
     * Quita una pista de la cola por posición -- gestión manual desde
     * QueueScreen. Si se quita la que estaba sonando, sigue con la
     * que ocupa ahora su misma posición (la que era la siguiente); si
     * la cola queda vacía, para la reproducción.
     * ---
     * Removes a track from the queue by position -- manual management
     * from QueueScreen. If the one playing gets removed, continues
     * with whatever now occupies that same position (what was next);
     * if the queue ends up empty, stops playback.
     */
    fun removeFromQueue(index: Int) {
        val current = _queue.value
        if (index !in current.indices) return

        val newQueue = current.toMutableList().apply { removeAt(index) }
        _queue.value = newQueue

        when {
            index < currentIndex -> {
                currentIndex--
                _state.value = _state.value.copy(
                    queueIndex = currentIndex,
                    queueSize = newQueue.size,
                )
            }
            index == currentIndex -> {
                if (newQueue.isEmpty()) {
                    currentIndex = -1
                    player.stop()
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        currentTitle = null,
                        queueIndex = -1,
                        queueSize = 0,
                    )
                } else {
                    if (currentIndex > newQueue.lastIndex) {
                        currentIndex = newQueue.lastIndex
                    }
                    playCurrent()
                }
            }
            else -> {
                _state.value = _state.value.copy(queueSize = newQueue.size)
            }
        }
    }

    /**
     * Mueve una pista de `fromIndex` a `toIndex` dentro de la cola --
     * reordenar manualmente desde QueueScreen.
     * ---
     * Moves a track from `fromIndex` to `toIndex` within the queue --
     * manual reordering from QueueScreen.
     */
    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        val current = _queue.value
        if (fromIndex !in current.indices || toIndex !in current.indices) return

        val newQueue = current.toMutableList()
        val moved = newQueue.removeAt(fromIndex)
        newQueue.add(toIndex, moved)
        _queue.value = newQueue

        currentIndex = when (currentIndex) {
            fromIndex -> toIndex
            in (fromIndex + 1)..toIndex -> currentIndex - 1
            in toIndex until fromIndex -> currentIndex + 1
            else -> currentIndex
        }
        _state.value = _state.value.copy(queueIndex = currentIndex)
    }

    /**
     * Vacía la cola entera y para la reproducción -- acción explícita
     * de gestión desde QueueScreen.
     * ---
     * Empties the whole queue and stops playback -- explicit
     * management action from QueueScreen.
     */
    fun clearQueue() {
        _queue.value = emptyList()
        currentIndex = -1
        player.stop()
        _state.value = _state.value.copy(
            isPlaying = false,
            currentTitle = null,
            queueIndex = -1,
            queueSize = 0,
        )
    }

    fun pause() = player.pause()

    fun resume() = player.play()

    fun currentPositionMs(): Long = player.currentPosition

    fun durationMs(): Long = player.duration.coerceAtLeast(0L)

    fun release() = player.release()
}

package com.miguelaetxio.mimoo.data.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
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
    /** H07 PARTE 3 -- true si la cola vuelve al principio al terminar. */
    val repeatModeEnabled: Boolean = false,
    /** H07 PARTE 3 -- true si el orden de reproducción es aleatorio. */
    val shuffleModeEnabled: Boolean = false,
)

/**
 * Wraps a single ExoPlayer instance for audio-only playback, y
 * gestiona la COLA DE REPRODUCCIÓN DE SESIÓN -- petición explícita de
 * Miguel Ángel (2026-07-05): "hay lo que es una lista de reproducción
 * actual... una lista temporal de lo que se está oyendo... una vez
 * que cierras la app, la lista desaparece". Esta cola vive SOLO en
 * memoria -- nunca se persiste en Room, así que desaparece sola al
 * matar el proceso, exactamente como se pidió. No confundir con las
 * Playlists guardadas (PlaylistRepository/Room), que son un concepto
 * totalmente distinto y persistente.
 *
 * LA COLA VIVE DENTRO DEL PROPIO EXOPLAYER (2026-07-05, corrige un bug
 * real): antes, `_queue` era una lista aparte mantenida a mano, y el
 * player solo veía UNA pista suelta cada vez (player.setMediaItem()).
 * Eso rompía los botones nativos siguiente/anterior de la notificación
 * del sistema -- esos botones actúan sobre el ExoPlayer de verdad via
 * la MediaSession, no sobre nuestra lógica interna, y un player con
 * una sola pista cargada nunca tiene un "siguiente" real que ofrecer
 * (por eso el botón "anterior" sí aparecía -- ExoPlayer permite
 * reiniciar la pista actual sin necesitar más de un item -- pero
 * "siguiente" nunca funcionaba). Ahora `queueItems` (nuestros datos:
 * título, uri, isLocal) se mantiene en paralelo, en el mismo orden,
 * 1:1 con la playlist real del player (player.addMediaItems()/
 * removeMediaItem()/moveMediaItem()), así que
 * seekToNextMediaItem()/seekToPreviousMediaItem() del propio ExoPlayer
 * -- lo que la notificación llama de verdad -- funcionan con datos
 * reales. `currentIndex` ya no se guarda a mano: siempre se lee de
 * player.currentMediaItemIndex, la única fuente de verdad.
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
 * Wraps a single ExoPlayer instance for audio-only playback, and
 * manages the SESSION PLAYBACK QUEUE -- explicit request from Miguel
 * Ángel (2026-07-05): "there's a thing called the current playlist...
 * a temporary list of what's playing... once you close the app, the
 * list disappears". This queue lives ONLY in memory -- it's never
 * persisted to Room, so it disappears on its own when the process
 * dies, exactly as requested. Not to be confused with saved Playlists
 * (PlaylistRepository/Room), which are a completely different,
 * persistent concept.
 *
 * THE QUEUE NOW LIVES INSIDE EXOPLAYER ITSELF (2026-07-05, fixes a
 * real bug): previously, `_queue` was a separate hand-maintained list,
 * and the player only ever saw ONE loose track at a time
 * (player.setMediaItem()). That broke the system notification's native
 * next/previous buttons -- those buttons act on the real ExoPlayer via
 * the MediaSession, not on our internal logic, and a player with only
 * one item loaded never has a real "next" to offer (which is why
 * "previous" did show up -- ExoPlayer allows restarting the current
 * item without needing more than one item -- but "next" never worked).
 * Now `queueItems` (our own data: title, uri, isLocal) is kept in
 * parallel, in the same order, 1:1 with the player's real playlist
 * (player.addMediaItems()/removeMediaItem()/moveMediaItem()), so the
 * ExoPlayer's own seekToNextMediaItem()/seekToPreviousMediaItem() --
 * what the notification actually calls -- work with real data.
 * `currentIndex` is no longer hand-tracked: it's always read from
 * player.currentMediaItemIndex, the single source of truth.
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
     * comentario de clase. Solo en memoria, nunca en Room. Siempre en
     * el mismo orden que la playlist real de `player`.
     * ---
     * The session queue itself, exposed for QueueScreen -- see the
     * class comment. In memory only, never in Room. Always in the same
     * order as `player`'s real playlist.
     */
    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue

    /**
     * Copia 1:1 de `_queue.value` como lista mutable, para poder
     * insertar/quitar/mover en el mismo índice que
     * player.addMediaItems()/removeMediaItem()/moveMediaItem() sin
     * tener que reconstruir MediaItems desde `_queue` en cada
     * operación. SIEMPRE se muta en el mismo bloque que la playlist
     * real del player -- nunca por separado.
     * ---
     * 1:1 copy of `_queue.value` as a mutable list, so we can
     * insert/remove/move at the same index as
     * player.addMediaItems()/removeMediaItem()/moveMediaItem() without
     * rebuilding MediaItems from `_queue` on every operation. ALWAYS
     * mutated in the same block as the player's real playlist -- never
     * separately.
     */
    private val queueItems: MutableList<QueueItem> = mutableListOf()

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

            // onMediaItemTransition/onTimelineChanged cubren TODOS los
            // casos en que cambia la pista actual o el tamaño de la
            // playlist -- avance automático al terminar una pista
            // (ExoPlayer lo hace solo con una playlist real, ya no
            // hace falta el hack manual de "STATE_ENDED -> playNext()"
            // de antes), seekToNextMediaItem()/seekToPreviousMediaItem()
            // llamados desde la notificación del sistema, o cualquier
            // cambio disparado desde esta misma clase.
            // ---
            // onMediaItemTransition/onTimelineChanged cover ALL cases
            // where the current track or the playlist size changes --
            // automatic advance when a track ends (ExoPlayer does this
            // on its own with a real playlist, no longer needs the old
            // manual "STATE_ENDED -> playNext()" hack),
            // seekToNextMediaItem()/seekToPreviousMediaItem() called
            // from the system notification, or any change triggered
            // from this same class.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncStateFromPlayer()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                syncStateFromPlayer()
            }

            // H07 PARTE 3 -- si repeat/shuffle cambian por cualquier
            // vía (nuestros propios toggleRepeatMode()/
            // toggleShuffleMode(), o en el futuro un control nativo de
            // la notificación), el estado expuesto se sincroniza
            // igual que con la cola -- una única fuente de verdad
            // (player.repeatMode/player.shuffleModeEnabled), nunca un
            // booleano mantenido a mano por separado.
            // ---
            // H07 PART 3 -- if repeat/shuffle change through any path
            // (our own toggleRepeatMode()/toggleShuffleMode(), or in
            // the future a native notification control), the exposed
            // state is synced the same way as the queue -- a single
            // source of truth (player.repeatMode/
            // player.shuffleModeEnabled), never a hand-kept separate
            // boolean.
            override fun onRepeatModeChanged(repeatMode: Int) {
                syncStateFromPlayer()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                syncStateFromPlayer()
            }
        })
    }

    private fun syncStateFromPlayer() {
        val index = player.currentMediaItemIndex
        val item = queueItems.getOrNull(index)
        _queue.value = queueItems.toList()
        _state.value = _state.value.copy(
            currentTitle = item?.title,
            isLocal = item?.isLocal ?: false,
            queueIndex = if (queueItems.isEmpty()) -1 else index,
            queueSize = queueItems.size,
            repeatModeEnabled = player.repeatMode == Player.REPEAT_MODE_ALL,
            shuffleModeEnabled = player.shuffleModeEnabled,
        )
    }

    private fun toMediaItem(item: QueueItem): MediaItem =
        MediaItem.Builder()
            .setUri(item.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setDisplayTitle(item.title)
                    .build()
            )
            .build()

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
        val insertAt = if (queueItems.isEmpty()) {
            0
        } else {
            (player.currentMediaItemIndex + 1).coerceIn(0, queueItems.size)
        }
        queueItems.addAll(insertAt, items)
        player.addMediaItems(insertAt, items.map { toMediaItem(it) })
        player.prepare()
        player.seekTo(insertAt + startIndex.coerceIn(0, items.lastIndex), 0)
        player.play()
        syncStateFromPlayer()
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
        val insertAt = if (queueItems.isEmpty()) {
            0
        } else {
            (player.currentMediaItemIndex + 1).coerceIn(0, queueItems.size)
        }
        queueItems.addAll(insertAt, items)
        player.addMediaItems(insertAt, items.map { toMediaItem(it) })
        syncStateFromPlayer()
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
        queueItems.addAll(items)
        player.addMediaItems(items.map { toMediaItem(it) })
        syncStateFromPlayer()
    }

    /**
     * Avanza a la siguiente pista de la cola, si la hay -- ahora
     * delega en el seekToNextMediaItem() real de ExoPlayer (2026-07-05,
     * ver comentario de clase) en vez de mantener el índice a mano.
     * ---
     * Advances to the next queue item, if any -- now delegates to
     * ExoPlayer's real seekToNextMediaItem() (2026-07-05, see class
     * comment) instead of hand-tracking the index.
     */
    fun playNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    /** Goes back to the previous queue item, if any. */
    fun playPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
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
        if (index in queueItems.indices) {
            player.seekTo(index, 0)
            player.play()
        }
    }

    /**
     * Quita una pista de la cola por posición -- gestión manual desde
     * QueueScreen. Si se quita la que estaba sonando, ExoPlayer sigue
     * solo con la que ocupa ahora su misma posición (la que era la
     * siguiente); si la cola queda vacía, para la reproducción.
     * ---
     * Removes a track from the queue by position -- manual management
     * from QueueScreen. If the one playing gets removed, ExoPlayer
     * continues on its own with whatever now occupies that same
     * position (what was next); if the queue ends up empty, stops
     * playback.
     */
    fun removeFromQueue(index: Int) {
        if (index !in queueItems.indices) return
        queueItems.removeAt(index)
        player.removeMediaItem(index)
        if (queueItems.isEmpty()) {
            player.stop()
        }
        syncStateFromPlayer()
    }

    /**
     * Mueve una pista de `fromIndex` a `toIndex` dentro de la cola --
     * reordenar manualmente desde QueueScreen.
     * ---
     * Moves a track from `fromIndex` to `toIndex` within the queue --
     * manual reordering from QueueScreen.
     */
    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in queueItems.indices || toIndex !in queueItems.indices) return
        val moved = queueItems.removeAt(fromIndex)
        queueItems.add(toIndex, moved)
        player.moveMediaItem(fromIndex, toIndex)
        syncStateFromPlayer()
    }

    /**
     * Vacía la cola entera y para la reproducción -- acción explícita
     * de gestión desde QueueScreen.
     * ---
     * Empties the whole queue and stops playback -- explicit
     * management action from QueueScreen.
     */
    fun clearQueue() {
        queueItems.clear()
        player.clearMediaItems()
        player.stop()
        syncStateFromPlayer()
    }

    fun pause() = player.pause()

    fun resume() = player.play()

    /**
     * Cíclico: al llegar al final de la cola, vuelve a empezar por la
     * primera pista -- Player.REPEAT_MODE_ALL cubre exactamente esto
     * de forma nativa (verificado en línea, S008): repite la playlist
     * entera, no solo la pista actual (eso sería REPEAT_MODE_ONE, que
     * no es lo que pidió Miguel Ángel). Caso descrito explícitamente
     * por Miguel Ángel: cola construida añadiendo pistas sueltas
     * (p.ej. 200 canciones) -- al llegar a la última, vuelve a la
     * primera y repite la cola completa en el mismo orden.
     * ---
     * Cyclic: on reaching the end of the queue, starts again from the
     * first track -- Player.REPEAT_MODE_ALL covers exactly this
     * natively (verified online, S008): repeats the whole playlist,
     * not just the current track (that would be REPEAT_MODE_ONE,
     * which isn't what Miguel Ángel asked for). Case explicitly
     * described by Miguel Ángel: a queue built by adding loose tracks
     * (e.g. 200 songs) -- on reaching the last one, goes back to the
     * first and repeats the whole queue in the same order.
     */
    fun toggleRepeatMode() {
        player.repeatMode = if (player.repeatMode == Player.REPEAT_MODE_ALL) {
            Player.REPEAT_MODE_OFF
        } else {
            Player.REPEAT_MODE_ALL
        }
    }

    /**
     * Aleatorio: orden aleatorio dentro de la cola actual. Por sí
     * solo (shuffleModeEnabled=true, repeatMode=OFF), ExoPlayer SÍ se
     * para al agotar la cola -- igual que en orden normal, solo
     * cambia el orden, no si para al final. El "no para nunca" que
     * describe Miguel Ángel es la combinación de shuffle + cíclico
     * activados a la vez (ver toggleRepeatMode()), no un modo
     * separado -- verificado en línea (S008) el comportamiento real
     * de ExoPlayer/Media3 antes de asumirlo.
     * ---
     * Shuffle: random order within the current queue. On its own
     * (shuffleModeEnabled=true, repeatMode=OFF), ExoPlayer DOES stop
     * once the queue is exhausted -- same as normal order, only the
     * order changes, not whether it stops at the end. The "never
     * stops" Miguel Ángel describes is the combination of shuffle +
     * cyclic both enabled at once (see toggleRepeatMode()), not a
     * separate mode -- verified online (S008) ExoPlayer/Media3's real
     * behavior before assuming it.
     */
    fun toggleShuffleMode() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun currentPositionMs(): Long = player.currentPosition

    fun durationMs(): Long = player.duration.coerceAtLeast(0L)

    fun release() = player.release()
}

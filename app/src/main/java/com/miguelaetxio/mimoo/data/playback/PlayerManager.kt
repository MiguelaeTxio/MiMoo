package com.miguelaetxio.mimoo.data.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.remote.ExternalLinkResolver
import com.miguelaetxio.mimoo.data.remote.RadioAnchor
import com.miguelaetxio.mimoo.data.remote.RadioDebugLogger
import com.miguelaetxio.mimoo.data.remote.RadioRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single entry in the playback queue.
 *
 * artist (H08 PARTE 2, S009): opcional porque QueueItem se usaba
 * antes solo para reproducción, sin necesitar saber el artista de
 * cada pista -- ahora hace falta para poder buscar "algo relacionado
 * con X" al terminar la cola (ver PlayerManager). Los llamantes que sí
 * conocen el artista (Biblioteca, Importar enlace, Búsqueda,
 * Playlists) lo pasan; ningún llamante existente se rompe al quedar
 * con el valor por defecto null.
 * isFromRadio: true solo en la pista que la propia Radio añadió --
 * distingue "esto lo elegiste tú" de "esto lo sugirió la Radio", por
 * si la UI quiere mostrarlo alguna vez (no usado todavía en pantalla).
 * youtubeId (S010, favoritos desde el reproductor): opcional por el
 * mismo motivo que artist -- permite marcar/quitar de favoritos la
 * pista que está sonando o en cola reutilizando
 * SearchResultTrackRepository.updateFavorite() (H03), sin duplicar el
 * concepto de favorito. Queda null para pistas sin equivalente real en
 * la biblioteca (nada distinto cambia para ellas: simplemente no se
 * ofrece el botón de favorito). Los llamantes que sí conocen el
 * youtubeId (Biblioteca, Importar enlace, Búsqueda, Playlists, y la
 * propia Radio de H08 vía ExternalLinkTrack) lo pasan.
 * ---
 * A single entry in the playback queue.
 *
 * artist (H08 PARTE 2, S009): optional because QueueItem was only
 * used for playback before, with no need to know each track's artist
 * -- now needed to be able to search "something related to X" when
 * the queue ends (see PlayerManager). Callers that do know the artist
 * (Biblioteca, Importar enlace, Búsqueda, Playlists) pass it; no
 * existing caller breaks by falling back to the default null value.
 * isFromRadio: true only on the track Radio itself added -- tells
 * apart "you chose this" from "Radio suggested this", in case the UI
 * ever wants to show it (not used on screen yet).
 * youtubeId (S010, favorites from the player): optional for the same
 * reason as artist -- lets the currently playing/queued track be
 * favorited/unfavorited by reusing
 * SearchResultTrackRepository.updateFavorite() (H03), without
 * duplicating the favorite concept. Stays null for tracks with no real
 * library equivalent (nothing changes for them: the favorite button is
 * simply not offered). Callers that do know the youtubeId pass it.
 */
data class QueueItem(
    val uri: String,
    val title: String,
    val isLocal: Boolean,
    val artist: String? = null,
    val isFromRadio: Boolean = false,
    val youtubeId: String? = null,
    /**
     * S010 -- distinto de `artist`. `artist` es el "artista
     * estructurado" de H05 (AlbumMatchRepository): un emparejamiento
     * heurístico contra releases de MusicBrainz por título, útil para
     * organizar la Biblioteca pero con falsos positivos reales en
     * títulos ambiguos/poco conocidos (reportado por Miguel Ángel,
     * S010: "EL PISTOLERO -pistones" emparejó con un release
     * atribuido a "Kris", sin relación real). `channelTitle` es el
     * nombre del canal de YouTube de ESE vídeo concreto (ya limpio de
     * sufijos "- Topic"/"VEVO"/"Oficial", ver link_resolver.py) --
     * mucho más fiable como ancla para la Radio (H08), que necesita
     * precisión, no solo una etiqueta aproximada para mostrar.
     * ---
     * S010 -- different from `artist`. `artist` is H05's "structured
     * artist" (AlbumMatchRepository): a heuristic match against
     * MusicBrainz releases by title, useful for organizing the
     * Library but with real false positives on ambiguous/obscure
     * titles. `channelTitle` is that specific video's YouTube channel
     * name (already cleaned of "- Topic"/"VEVO"/"Oficial" suffixes) --
     * much more reliable as Radio's anchor, which needs precision, not
     * just an approximate display label.
     */
    val channelTitle: String? = null,
)

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTitle: String? = null,
    val currentYoutubeId: String? = null,
    /** S010 -- necesarios junto a currentYoutubeId para poder crear la fila de favorito de una pista transitoria de Radio (SearchResultTrackRepository.setFavoriteEnsuringRow()). */
    val currentArtist: String? = null,
    val currentChannelTitle: String? = null,
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
    // H08 PARTE 2 (S009) -- dependencias de red que PlayerManager no
    // necesitaba hasta ahora (era pura infraestructura de
    // reproducción). Se aceptan aquí, en vez de crear un coordinador
    // aparte, porque PlayerManager es el único sitio que sabe de
    // verdad cuándo la cola termina de verdad sin cíclico
    // (onPlaybackStateChanged) -- un ViewModel no sirve, se destruye
    // al salir de pantalla y el enganche dejaría de disparar.
    // ---
    // H08 PARTE 2 (S009) -- network dependencies PlayerManager didn't
    // need until now (it was pure playback infrastructure). Accepted
    // here, instead of a separate coordinator, because PlayerManager
    // is the only place that truly knows when the queue really ends
    // without cyclic (onPlaybackStateChanged) -- a ViewModel won't do,
    // it gets destroyed on leaving the screen and the hook would stop
    // firing.
    private val radioRepository: RadioRepository,
    private val externalLinkResolver: ExternalLinkResolver,
    private val streamResolver: StreamResolver,
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

    /**
     * H08 PARTE 2 (S009) -- CoroutineScope propio, no viewModelScope
     * (PlayerManager no es un ViewModel), para la búsqueda de "artista
     * relacionado" + resolución de stream al terminar la cola.
     * SupervisorJob: un fallo en una continuación de Radio no debe
     * cancelar la capacidad de disparar la siguiente. Cancelado en
     * release().
     * ---
     * H08 PARTE 2 (S009) -- own CoroutineScope, not viewModelScope
     * (PlayerManager isn't a ViewModel), for the "related artist"
     * search + stream resolution when the queue ends. SupervisorJob:
     * a failure in one Radio continuation must not cancel the ability
     * to fire the next one. Cancelled in release().
     */
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                // H08 PARTE 2 (S009, corrección tras prueba real --
                // el autoplay seguía sin funcionar). Se repone en dos
                // casos, no solo uno:
                //   - la pista que empieza a sonar es ya de Radio
                //     (reposición continua, igual que antes).
                //   - la pista que empieza a sonar es la ÚLTIMA de la
                //     cola (aunque sea la única, aunque no sea de
                //     Radio todavía) -- esto es lo que faltaba: en vez
                //     de esperar a STATE_ENDED para reaccionar
                //     (reactivo, y ExoPlayer no reanuda solo desde ahí
                //     de forma fiable), se empieza a rellenar la Radio
                //     EN CUANTO arranca la última pista (proactivo),
                //     mientras todavía está sonando. Así, cuando esa
                //     pista termina, ExoPlayer ya tiene la siguiente
                //     en su propia lista y avanza solo -- el mismo
                //     mecanismo de avance automático que ya funciona
                //     siempre para cualquier cola normal, sin
                //     necesitar resucitar el player desde
                //     STATE_ENDED.
                // ---
                // H08 PARTE 2 (S009, fix after real-device test --
                // autoplay still wasn't working). Tops up in two
                // cases now, not just one:
                //   - the track that starts playing is already a
                //     Radio one (same continuous top-up as before).
                //   - the track that starts playing is the LAST one
                //     in the queue (even if it's the only one, even if
                //     it isn't Radio yet) -- this was the missing
                //     piece: instead of waiting for STATE_ENDED to
                //     react (reactive, and ExoPlayer doesn't reliably
                //     resume from there on its own), Radio starts
                //     filling in AS SOON AS the last track starts
                //     (proactive), while it's still playing. So by the
                //     time that track finishes, ExoPlayer already has
                //     the next one in its own list and advances on its
                //     own -- the same auto-advance mechanism that
                //     already works for any normal queue, no need to
                //     resurrect the player from STATE_ENDED.
                val currentIndex = player.currentMediaItemIndex
                val currentItem = queueItems.getOrNull(currentIndex)
                val isLastItem = queueItems.isNotEmpty() && currentIndex == queueItems.lastIndex
                if (isLastItem && currentItem?.isFromRadio != true) {
                    // H08 (S009, corrección tras corte a los 3 temas)
                    // -- se fija el "ancla": el artista que de verdad
                    // arrancó la Radio. Si la cadena de "relacionados"
                    // llega a un callejón sin salida más adelante
                    // (artista sin géneros en MusicBrainz), se
                    // reintenta desde aquí en vez de rendirse del
                    // todo -- ver topUpRadioQueueIfNeeded().
                    // ---
                    // H08 (S009, fix after cutting off at 3 tracks) --
                    // sets the "anchor": the artist that actually
                    // started Radio. If the "related" chain hits a
                    // dead end later on (an artist with no genres in
                    // MusicBrainz), it retries from here instead of
                    // giving up entirely -- see
                    // topUpRadioQueueIfNeeded().
                    // S010 -- se prefiere channelTitle sobre artist
                    // como ancla: artist es el emparejamiento
                    // heurístico de H05 (AlbumMatchRepository), con
                    // falsos positivos reales en títulos ambiguos
                    // (ver QueueItem.channelTitle); channelTitle es
                    // el nombre de canal real de ese vídeo concreto,
                    // más fiable para esto en concreto aunque para
                    // mostrar en pantalla siga prefiriéndose artist
                    // (ver el resto de la app: artist ?: channelTitle).
                    // ---
                    // S010 -- channelTitle is preferred over artist as
                    // the anchor: artist is H05's heuristic match, with
                    // real false positives on ambiguous titles;
                    // channelTitle is that specific video's real
                    // channel name, more reliable for this specific
                    // purpose even though display elsewhere still
                    // prefers artist.
                    radioAnchorArtist = currentItem?.channelTitle?.takeIf { it.isNotBlank() }
                        ?: currentItem?.artist?.takeIf { it.isNotBlank() }
                    // S010 -- respaldo para cuando el nombre de canal
                    // no sea un artista real en absoluto (p.ej.
                    // "OldGuitar8", un canal de recopilaciones, sin
                    // ningún resultado en MusicBrainz -- reportado por
                    // Miguel Ángel, S010 continuación). Solo se guarda
                    // si es distinto del principal, para no repetir el
                    // mismo intento fallido dos veces -- ver
                    // fetchOneRadioTrack().
                    // ---
                    // S010 -- fallback for when the channel name isn't
                    // a real artist at all (e.g. "OldGuitar8", a
                    // compilation channel, no results in MusicBrainz).
                    // Only stored if different from the primary one, to
                    // avoid repeating the same failed attempt twice.
                    radioAnchorArtistFallback = currentItem?.artist
                        ?.takeIf { it.isNotBlank() && !it.equals(radioAnchorArtist, ignoreCase = true) }
                    radioAnchorTrackTitle = currentItem?.title
                    // S010 -- nueva sesión de Radio: invalida el
                    // género+país cacheado y la lista de ya-usados de
                    // la sesión anterior, se recalculan de cero desde
                    // este nuevo artista en el próximo topUp.
                    // ---
                    // S010 -- new Radio session: invalidates the
                    // cached genre+country and the previous session's
                    // used-artists list, recalculated from scratch
                    // from this new artist on the next top-up.
                    radioAnchor = null
                    radioUsedArtists.clear()
                }
                if (currentItem?.isFromRadio == true || isLastItem) {
                    topUpRadioQueueIfNeeded()
                }
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

            /**
             * H08 PARTE 2 (S009) -- disparo de Radio: cuando la cola
             * termina de verdad (Player.STATE_ENDED, que ExoPlayer solo
             * alcanza al terminar la última pista SIN que haya
             * cíclico activado -- con REPEAT_MODE_ALL nunca se llega
             * a este estado, vuelve a la primera pista en su lugar).
             * Decisión explícita de Miguel Ángel: se dispara al acabar
             * la última canción, sin cíclico, sin ningún control
             * aparte que activar/desactivar.
             * ---
             * H08 PARTE 2 (S009) -- Radio trigger: when the queue
             * truly ends (Player.STATE_ENDED, which ExoPlayer only
             * reaches after the last track finishes WITHOUT cyclic
             * enabled -- with REPEAT_MODE_ALL this state is never
             * reached, it goes back to the first track instead).
             * Explicit decision from Miguel Ángel: fires when the last
             * song ends, no cyclic, no separate control to turn it on
             * or off.
             */
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED &&
                    player.repeatMode == Player.REPEAT_MODE_OFF
                ) {
                    topUpRadioQueueIfNeeded()
                }
            }

            /**
             * S010 -- reportado por Miguel Ángel: tras rellenar la cola
             * de Radio con éxito (10 pistas, confirmado en
             * radio_relacionados_debug.txt), al terminar el primer
             * tema el indicador de "sonando ahora" saltaba a la
             * segunda pista pero no llegaba a sonar nada -- y ni
             * siquiera el botón "Siguiente" conseguía arrancarla.
             *
             * Causa real: este listener nunca implementaba
             * `onPlayerError()`. Cuando una URL de stream falla (una
             * de las pistas de Radio venía de una búsqueda de YouTube
             * dudosa -- "Charli xcx - Wink Wink" en vez del DJ "Wink"
             * buscado, posible causa del fallo de stream aunque no
             * confirmada), ExoPlayer dispara `onPlayerError` y pasa a
             * `Player.STATE_IDLE` -- un estado terminal del que
             * `player.play()` solo no saca al player, exactamente el
             * mismo problema ya documentado para `STATE_ENDED`
             * (`prepare()` obligatorio para reanudar, ver
             * `topUpRadioQueueIfNeeded()` más abajo). Como nadie
             * escuchaba el error, el player se quedaba callado sin
             * ningún aviso, y `playNext()` (que solo llama a
             * `seekToNextMediaItem()`, sin `prepare()`/`play()`)
             * tampoco lo resucitaba.
             *
             * Recuperación automática: se registra el error (con el
             * título de la pista que falló) y, si hay una pista
             * siguiente en la cola, se salta a ella y se fuerza
             * `prepare()` + `play()` -- mismo patrón exacto que el
             * fix de autoplay de S009. Así una URL rota no deja la
             * Radio muda entera, sigue con la siguiente pista sola.
             * ---
             * S010 -- reported by Miguel Ángel: after successfully
             * filling the Radio queue (10 tracks, confirmed in
             * radio_relacionados_debug.txt), when the first track
             * ended the "now playing" indicator jumped to the second
             * track but nothing actually played -- and not even the
             * "Next" button could get it going.
             *
             * Real cause: this listener never implemented
             * `onPlayerError()`. When a stream URL fails, ExoPlayer
             * fires `onPlayerError` and moves to `Player.STATE_IDLE` --
             * a terminal state that `player.play()` alone can't
             * recover from, the exact same gotcha already documented
             * for `STATE_ENDED`. Since nothing listened for the error,
             * the player just went silent with no warning, and
             * `playNext()` (which only calls `seekToNextMediaItem()`,
             * no `prepare()`/`play()`) couldn't resurrect it either.
             *
             * Automatic recovery: logs the error (with the title of
             * the track that failed) and, if there's a next track in
             * the queue, skips to it and forces `prepare()` + `play()`
             * -- the exact same pattern as the S009 autoplay fix. This
             * way one broken URL doesn't leave all of Radio silent, it
             * just continues with the next track on its own.
             */
            override fun onPlayerError(error: PlaybackException) {
                val failedItem = queueItems.getOrNull(player.currentMediaItemIndex)
                NotificationDebugLogger.log(
                    appContext, storageManager,
                    "onPlayerError() -- pista='${failedItem?.title}' " +
                        "isFromRadio=${failedItem?.isFromRadio} -- " +
                        "${error.errorCodeName}: ${error.message}",
                )
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })
    }

    /**
     * H08 PARTE 2 (S009, corrección tras prueba real de Miguel Ángel).
     * Dos fallos del primer diseño (una sola pista añadida al llegar
     * a STATE_ENDED):
     *
     * 1. **No reanudaba sola.** `player.play()` no basta para salir
     *    de `Player.STATE_ENDED` -- Media3 documenta que hay que
     *    volver a llamar a `prepare()` para reanudar desde ese
     *    estado terminal. Sin esto, la pista se añadía a la cola
     *    pero se quedaba esperando a que Miguel Ángel pulsara
     *    reproducir a mano.
     * 2. **Una sola pista no es "radio".** Petición explícita tras
     *    probarlo: mantener siempre hasta `RADIO_QUEUE_SIZE` (10)
     *    pistas de Radio por delante en la cola, reponiendo una cada
     *    vez que la que suena termina -- no esperar a quedarse sin
     *    nada para buscar la siguiente.
     *
     * `topUpRadioQueueIfNeeded()` se llama desde dos sitios del
     * listener de ExoPlayer:
     *   - `onPlaybackStateChanged` en STATE_ENDED -- arranque inicial,
     *     cuando la cola de verdad se queda sin nada.
     *   - `onMediaItemTransition`, pero SOLO si la pista a la que se
     *     acaba de saltar es ella misma de Radio (`isFromRadio`) --
     *     así nunca se dispara mientras todavía queda contenido propio
     *     del usuario en cola, sin necesitar ningún flag de modo
     *     aparte: "estamos reproduciendo algo que puso la Radio" ya
     *     es la señal de que estamos en territorio de Radio.
     *
     * `isRadioTopUpRunning` evita relanzar la corrutina de reposición
     * si ya hay una en marcha (p.ej. el propio seekTo() del primer
     * disparo provoca un onMediaItemTransition que llamaría otra vez).
     * ---
     * H08 PARTE 2 (S009, fix after Miguel Ángel's real-device test).
     * Two failures of the first design (a single track added on
     * reaching STATE_ENDED):
     *
     * 1. **Didn't resume on its own.** `player.play()` alone isn't
     *    enough to leave `Player.STATE_ENDED` -- Media3 documents
     *    that `prepare()` must be called again to resume from that
     *    terminal state. Without this, the track got added to the
     *    queue but sat waiting for Miguel Ángel to tap play by hand.
     * 2. **A single track isn't "radio".** Explicit request after
     *    testing it: always keep up to `RADIO_QUEUE_SIZE` (10) Radio
     *    tracks queued ahead, replenishing one every time the
     *    currently-playing one finishes -- don't wait to run
     *    completely dry before looking for the next one.
     *
     * `topUpRadioQueueIfNeeded()` is called from two places in
     * ExoPlayer's listener:
     *   - `onPlaybackStateChanged` on STATE_ENDED -- initial kickoff,
     *     when the queue truly has nothing left.
     *   - `onMediaItemTransition`, but ONLY if the track just jumped
     *     to is itself a Radio one (`isFromRadio`) -- this way it
     *     never fires while the user's own content is still queued,
     *     with no separate mode flag needed: "we're playing something
     *     Radio added" is itself the signal that we're in Radio
     *     territory.
     *
     * `isRadioTopUpRunning` avoids relaunching the top-up coroutine if
     * one is already running (e.g. the first trigger's own seekTo()
     * causes an onMediaItemTransition that would call it again).
     */
    private var isRadioTopUpRunning = false

    /**
     * H08 (S009) -- el artista que arrancó la Radio (el último tema
     * "propio" del usuario, no de Radio, antes de que empezara a
     * reponer).
     *
     * `radioAnchor` (S010, rediseño de sesión-ancla) -- género+país
     * calculados UNA SOLA VEZ a partir de `radioAnchorArtist`, ver
     * RadioRepository.resolveAnchor(). Se cachea aquí para no volver a
     * resolverlo en cada salto de la cadena -- justo el bug que
     * arregla este rediseño (antes se recalculaba el género del
     * artista recién añadido en cada salto, y "derivaba" con el
     * tiempo: Jeff Mills, techno, acababa en Led Zeppelin, rock,
     * varios saltos después).
     *
     * `radioUsedArtists` -- nombres ya sugeridos en esta sesión, para
     * no repetir siempre el mismo puñado de candidatos del mismo
     * género+país fijo.
     *
     * Los tres se resetean juntos cuando arranca una sesión de Radio
     * genuinamente nueva (ver onMediaItemTransition más arriba).
     * ---
     * H08 (S009) -- the artist that started Radio.
     *
     * `radioAnchor` (S010, anchor-session redesign) -- genre+country
     * computed ONCE from `radioAnchorArtist`. Cached here so it's not
     * re-resolved on every hop of the chain.
     *
     * `radioUsedArtists` -- names already suggested this session.
     *
     * All three get reset together when a genuinely new Radio session
     * starts.
     */
    private var radioAnchorArtist: String? = null
    private var radioAnchorArtistFallback: String? = null
    private var radioAnchorTrackTitle: String? = null
    private var radioAnchor: RadioAnchor? = null
    private val radioUsedArtists = mutableSetOf<String>()

    private fun topUpRadioQueueIfNeeded() {
        if (player.repeatMode != Player.REPEAT_MODE_OFF) return
        if (isRadioTopUpRunning) return
        if (currentRadioBacklog() >= RADIO_QUEUE_SIZE) return

        isRadioTopUpRunning = true
        managerScope.launch {
            try {
                while (true) {
                    val (shouldContinue, backlogNow) = withContext(Dispatchers.Main) {
                        val backlog = currentRadioBacklog()
                        val keepGoing = player.repeatMode == Player.REPEAT_MODE_OFF &&
                            backlog < RADIO_QUEUE_SIZE
                        keepGoing to backlog
                    }
                    if (!shouldContinue) {
                        RadioDebugLogger.log(
                            appContext, storageManager,
                            "topUpRadioQueueIfNeeded() -- parado: repeatMode cambió o backlog ya " +
                                "llegó a $RADIO_QUEUE_SIZE (backlog actual: $backlogNow)",
                        )
                        break
                    }
                    val anchorArtistName = radioAnchorArtist
                    if (anchorArtistName == null) {
                        RadioDebugLogger.log(
                            appContext, storageManager,
                            "topUpRadioQueueIfNeeded() -- parado: no hay artista ancla (la última " +
                                "pista propia del usuario no tiene 'artist'), no hay sesión de Radio que anclar",
                        )
                        break
                    }

                    // S010 (rediseño de ancla de sesión) -- ya NO se
                    // encadena desde el artista recién añadido en cada
                    // vuelta (eso era la causa real de la deriva de
                    // género, ver RadioRepository). fetchOneRadioTrack()
                    // resuelve el ancla (género+país) UNA SOLA VEZ, la
                    // cachea en radioAnchor, y la reutiliza en todas las
                    // vueltas siguientes de esta misma sesión.
                    // ---
                    // S010 (anchor-session redesign) -- no longer
                    // chains from the just-added artist each round.
                    // fetchOneRadioTrack() resolves the anchor ONCE,
                    // caches it, and reuses it every following round of
                    // this same session.
                    val newItem = fetchOneRadioTrack(anchorArtistName)
                    if (newItem == null) {
                        val backlogFinal = withContext(Dispatchers.Main) { currentRadioBacklog() }
                        RadioDebugLogger.log(
                            appContext, storageManager,
                            "topUpRadioQueueIfNeeded() -- parado del todo: sin más candidatos para " +
                                "el ancla de '$anchorArtistName' -- backlog final: $backlogFinal",
                        )
                        break
                    }
                    newItem.artist?.let { radioUsedArtists.add(it) }

                    withContext(Dispatchers.Main) {
                        // S010 -- reportado por Miguel Ángel con log real
                        // (notification_debug.txt: onPlayerError para
                        // "Radio Futura - Escuela de Calor",
                        // ERROR_CODE_IO_BAD_HTTP_STATUS): si el error de
                        // reproducción llega ANTES de que la Radio haya
                        // añadido nada a la cola, onPlayerError() no
                        // podía recuperarse solo (hasNextMediaItem() era
                        // false, no había nada a lo que saltar todavía).
                        // Cuando topUpRadioQueueIfNeeded() por fin
                        // añadía las pistas nuevas, esta comprobación
                        // solo miraba STATE_ENDED -- el player seguía en
                        // estado de ERROR, no en ENDED, así que nunca se
                        // le decía que arrancara con las pistas recién
                        // insertadas: se quedaban ahí sin sonar. Ahora
                        // también se reanuda si hay un error pendiente,
                        // no solo si terminó de forma normal.
                        // ---
                        // S010 -- reported by Miguel Ángel with a real
                        // log: if the playback error arrives BEFORE
                        // Radio has added anything to the queue,
                        // onPlayerError() couldn't recover on its own
                        // (hasNextMediaItem() was false, nothing to jump
                        // to yet). When topUpRadioQueueIfNeeded()
                        // finally added the new tracks, this check only
                        // looked at STATE_ENDED -- the player was still
                        // in an ERROR state, not ENDED, so it was never
                        // told to start with the newly inserted tracks:
                        // they just sat there unplayed. Now it also
                        // resumes if there's a pending error, not just
                        // on a normal end.
                        val needsResume = player.playbackState == Player.STATE_ENDED ||
                            player.playerError != null
                        val insertIndex = queueItems.size
                        queueItems.add(newItem)
                        player.addMediaItems(listOf(toMediaItem(newItem)))
                        if (needsResume) {
                            // Fix del fallo de autoplay -- ver docstring.
                            player.prepare()
                            player.seekTo(insertIndex, 0)
                            player.play()
                        }
                        syncStateFromPlayer()
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isRadioTopUpRunning = false
                }
            }
        }
    }

    /** Pistas de Radio (isFromRadio) que quedan por sonar, sin contar la actual. */
    private fun currentRadioBacklog(): Int {
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex < 0) return 0
        return queueItems.drop(currentIndex + 1).count { it.isFromRadio }
    }

    /**
     * S010 -- tres intentos en cadena para fijar el ancla de una
     * sesión de Radio, cada uno solo si el anterior no encontró NADA
     * en MusicBrainz:
     *   1. Nombre de canal de YouTube (más fiable en general, pero
     *      inútil si el canal es una resubida ajena -- p.ej. "Radio
     *      Futura - Escuela de Calor" subida por un canal random
     *      llamado "OldGuitar8", nada que ver con la banda real).
     *   2. Artista estructurado de H05 (emparejamiento heurístico
     *      contra MusicBrainz por título -- puede no existir para esa
     *      pista en absoluto).
     *   3. NUEVO -- parseado del propio título del vídeo, patrón
     *      "Artista - Canción" (extremadamente común en YouTube,
     *      incluso en resubidas de canales random como el caso de
     *      arriba). Última red de seguridad antes de rendirse del
     *      todo.
     * ---
     * S010 -- three chained attempts to fix a Radio session's anchor,
     * each only if the previous one found NOTHING in MusicBrainz:
     *   1. YouTube channel name.
     *   2. H05's structured artist.
     *   3. NEW -- parsed from the video title itself, "Artist - Song"
     *      pattern (extremely common on YouTube, even on reuploads
     *      from random channels).
     */
    private suspend fun resolveAnchorWithFallbacks(anchorArtistName: String): RadioAnchor? {
        radioRepository.resolveAnchor(anchorArtistName)?.let { return it }

        radioAnchorArtistFallback?.let { fallback ->
            RadioDebugLogger.log(
                appContext, storageManager,
                "fetchOneRadioTrack() -- ancla '$anchorArtistName' sin resultado, " +
                    "reintentando con el artista estructurado '$fallback'",
            )
            radioRepository.resolveAnchor(fallback)?.let { return it }
        }

        val titleGuess = parseArtistFromTitle(radioAnchorTrackTitle)
            ?.takeIf { !it.equals(anchorArtistName, ignoreCase = true) &&
                !it.equals(radioAnchorArtistFallback, ignoreCase = true) }
        if (titleGuess != null) {
            RadioDebugLogger.log(
                appContext, storageManager,
                "fetchOneRadioTrack() -- ancla '$anchorArtistName' y artista estructurado sin " +
                    "resultado, último intento con el título parseado ('${radioAnchorTrackTitle}' -> '$titleGuess')",
            )
            radioRepository.resolveAnchor(titleGuess)?.let { return it }
        }

        RadioDebugLogger.log(
            appContext, storageManager,
            "fetchOneRadioTrack() -- ancla '$anchorArtistName' sin resultado en NINGUNO de los " +
                "intentos (canal, H05, título) -- sin más respaldos que probar",
        )
        return null
    }

    /**
     * "Artista - Canción" -- patrón de nombrado extremadamente común
     * en YouTube. Solo el primer " - " cuenta (un título como "AC/DC
     * - Back In Black - Live" debe dar "AC/DC", no cortar por el
     * segundo guion). Se descarta si el resultado es sospechosamente
     * corto (1-2 caracteres) o si no hay separador en absoluto.
     * ---
     * "Artist - Song" -- extremely common YouTube naming pattern. Only
     * the first " - " counts. Discarded if the result is suspiciously
     * short or if there's no separator at all.
     */
    private fun parseArtistFromTitle(title: String?): String? {
        if (title.isNullOrBlank()) return null
        val separatorIndex = title.indexOf(" - ")
        if (separatorIndex <= 0) return null
        val candidate = title.substring(0, separatorIndex).trim()
        return candidate.takeIf { it.length > 2 }
    }

    /**
     * Un único ciclo búsqueda-de-relacionado + búsqueda-gratuita-en-
     * YouTube + resolución-de-stream (ver RadioRepository y
     * ExternalLinkResolver.searchYoutube()). Nunca lanza -- cualquier
     * fallo en cualquiera de los tres pasos se trata como "no hay
     * pista", dejando que topUpRadioQueueIfNeeded() pare de intentar
     * en vez de reventar.
     * ---
     * A single suggest-related + free-YouTube-search + stream-
     * resolution cycle (see RadioRepository and
     * ExternalLinkResolver.searchYoutube()). Never throws -- any
     * failure in any of the three steps is treated as "no track",
     * letting topUpRadioQueueIfNeeded() stop trying instead of
     * blowing up.
     */
    private suspend fun fetchOneRadioTrack(anchorArtistName: String): QueueItem? =
        try {
            val anchor = radioAnchor ?: resolveAnchorWithFallbacks(anchorArtistName)?.also {
                radioAnchor = it
            }
            if (anchor == null) {
                // Sin log aquí -- RadioRepository.resolveAnchor() ya
                // registra el motivo exacto en
                // radio_relacionados_debug.txt antes de devolver null.
                null
            } else {
                val relatedArtist = radioRepository.suggestRelatedArtist(
                    anchor,
                    excludeArtists = radioUsedArtists + anchorArtistName,
                )
                if (relatedArtist == null) {
                    // Sin log aquí -- suggestRelatedArtist() ya
                    // registra el motivo exacto.
                    null
                } else {
                    // H08 -- limit=6 en vez de 1, y se descartan
                    // candidatos que huelen a compilación (detectado en
                    // pruebas reales: buscar solo el nombre del artista
                    // devuelve muchas veces un "Greatest Hits Full Album"
                    // de 1-2 horas como primer resultado -- si se cogiera
                    // tal cual, "ocuparía" un hueco del backlog de Radio
                    // durante horas, rompiendo el propio modelo de
                    // "mantener 10 temas". RADIO_MAX_TRACK_SECONDS (15 min)
                    // es generoso a propósito -- hay canciones sueltas
                    // legítimas largas (rock progresivo, etc.), no se
                    // quiere descartarlas por error.
                    // ---
                    // H08 -- limit=6 instead of 1, and candidates that
                    // smell like a compilation are discarded (found in
                    // real testing: searching just the artist's name often
                    // returns a 1-2 hour "Greatest Hits Full Album" as the
                    // first result -- if taken as-is, it would "occupy" a
                    // slot in Radio's backlog for hours, breaking the very
                    // "keep 10 tracks" model. RADIO_MAX_TRACK_SECONDS
                    // (15 min) is deliberately generous -- there are
                    // legitimate long standalone songs (prog rock etc.),
                    // don't want to wrongly discard those.
                    val searchResult = externalLinkResolver.searchYoutube(relatedArtist, limit = 6)
                    val track = searchResult.tracks.firstOrNull { candidate ->
                        candidate.durationSeconds in 1..RADIO_MAX_TRACK_SECONDS &&
                            COMPILATION_TITLE_HINTS.none { hint ->
                                candidate.title.contains(hint, ignoreCase = true)
                            }
                    }
                    if (track == null) {
                        RadioDebugLogger.log(
                            appContext, storageManager,
                            "fetchOneRadioTrack(ancla='$anchorArtistName') -> relacionado='$relatedArtist' pero " +
                                "0 de ${searchResult.tracks.size} resultados de YouTube pasaron el filtro " +
                                "de duración/compilación -- eslabón roto",
                        )
                        null
                    } else {
                        val streamUrl = try {
                            streamResolver.resolveAudioStreamUrl("https://youtu.be/${track.youtubeId}")
                        } catch (e: Exception) {
                            RadioDebugLogger.log(
                                appContext, storageManager,
                                "fetchOneRadioTrack(ancla='$anchorArtistName') -> relacionado='$relatedArtist', " +
                                    "vídeo='${track.title}' -- resolveAudioStreamUrl() falló: " +
                                    "${e::class.java.simpleName}: ${e.message}",
                            )
                            throw e
                        }
                        RadioDebugLogger.log(
                            appContext, storageManager,
                            "fetchOneRadioTrack(ancla='$anchorArtistName') -> relacionado='$relatedArtist', " +
                                "añadido: '${track.title}'",
                        )
                        QueueItem(
                            uri = streamUrl,
                            title = track.title,
                            isLocal = false,
                            artist = relatedArtist,
                            isFromRadio = true,
                            youtubeId = track.youtubeId,
                            channelTitle = track.channelTitle,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            RadioDebugLogger.log(
                appContext, storageManager,
                "fetchOneRadioTrack(ancla='$anchorArtistName') -- EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}",
            )
            null
        }

    private fun syncStateFromPlayer() {
        val index = player.currentMediaItemIndex
        val item = queueItems.getOrNull(index)
        _queue.value = queueItems.toList()
        _state.value = _state.value.copy(
            currentTitle = item?.title,
            currentYoutubeId = item?.youtubeId,
            currentArtist = item?.artist,
            currentChannelTitle = item?.channelTitle,
            isLocal = item?.isLocal ?: false,
            queueIndex = if (queueItems.isEmpty()) -1 else index,
            queueSize = queueItems.size,
            repeatModeEnabled = player.repeatMode == Player.REPEAT_MODE_ALL,
            shuffleModeEnabled = player.shuffleModeEnabled,
            // H08 -- durationMs ya existía en PlaybackState pero nunca
            // se rellenaba aquí; positionMs se deja tal cual (0L) --
            // cambia continuamente mientras suena, así que se consulta
            // por sondeo desde la UI (currentPositionMs()), no por
            // este StateFlow que solo se actualiza en eventos puntuales.
            // ---
            // H08 -- durationMs already existed in PlaybackState but
            // was never populated here; positionMs is left as-is (0L)
            // -- it changes continuously while playing, so it's
            // polled from the UI (currentPositionMs()) instead of
            // through this StateFlow, which only updates on discrete
            // events.
            durationMs = player.duration.coerceAtLeast(0L),
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
    fun play(
        streamUrl: String,
        title: String,
        isLocal: Boolean = false,
        artist: String? = null,
        youtubeId: String? = null,
        channelTitle: String? = null,
    ) {
        playQueue(
            listOf(QueueItem(streamUrl, title, isLocal, artist, youtubeId = youtubeId, channelTitle = channelTitle)),
            startIndex = 0,
        )
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
     * Avanza a la siguiente pista de la cola, si la hay -- delega en
     * el seekToNextMediaItem() real de ExoPlayer (2026-07-05, ver
     * comentario de clase).
     *
     * `prepare()` + `play()` añadidos en S010 -- defensa adicional
     * ante cualquier estado terminal del player (STATE_ENDED,
     * STATE_IDLE tras un onPlayerError sin gestionar como el
     * reportado por Miguel Ángel esta sesión): sin esto, pulsar
     * "Siguiente" cambiaba la pista marcada como actual pero no
     * arrancaba nada. `prepare()` es seguro de llamar aunque el
     * player ya esté preparado -- no reinicia nada si no hace falta.
     * ---
     * Advances to the next queue item, if any -- delegates to
     * ExoPlayer's real seekToNextMediaItem() (2026-07-05, see class
     * comment).
     *
     * `prepare()` + `play()` added in S010 -- extra defense against
     * any terminal player state (STATE_ENDED, STATE_IDLE after an
     * unhandled onPlayerError like the one reported by Miguel Ángel
     * this session): without this, pressing "Next" changed which
     * track was marked current but nothing actually started.
     * `prepare()` is safe to call even if the player is already
     * prepared -- it's a no-op in that case.
     */
    fun playNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }
    }

    /**
     * H08 -- si no hay una pista anterior de verdad en la cola (p.ej.
     * un único tema suelto, o la primera de la cola sin cíclico),
     * reinicia la actual desde el principio en vez de no hacer nada.
     * Petición explícita de Miguel Ángel tras probar la Radio con un
     * solo tema: "no aparece el control de ir atrás... pero debería
     * aparecer para poder escuchar el tema desde el principio".
     * ---
     * H08 -- if there's no real previous track in the queue (e.g. a
     * single lone track, or the first one with cyclic off), restarts
     * the current one from the beginning instead of doing nothing.
     * Explicit request from Miguel Ángel after testing Radio with a
     * single track: "the back control doesn't show up... but it
     * should, to be able to listen to the track from the start".
     */
    fun playPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0)
        }
        // S010 -- mismo refuerzo defensivo que playNext(): si el
        // player estaba en un estado terminal (STATE_ENDED,
        // STATE_IDLE tras un error sin gestionar), un seek solo no
        // basta para que vuelva a sonar.
        // ---
        // S010 -- same defensive reinforcement as playNext(): if the
        // player was in a terminal state, a seek alone isn't enough
        // to make it play again.
        player.prepare()
        player.play()
    }

    /**
     * H08 -- seek manual, para la barra de progreso arrastrable de
     * PlayerBar. Sin comprobación de límites: ExoPlayer ya recorta
     * solo a [0, duración] si se pasa un valor fuera de rango.
     * ---
     * H08 -- manual seek, for PlayerBar's draggable progress bar. No
     * bounds checking: ExoPlayer already clamps to [0, duration] on
     * out-of-range values.
     */
    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
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

    fun release() {
        managerScope.cancel()
        player.release()
    }

    private companion object {
        /**
         * H08 PARTE 2 (S009) -- cuántas pistas de Radio se mantienen
         * siempre por delante en la cola. Petición explícita de
         * Miguel Ángel tras probar la primera versión (una sola
         * pista): "iría añadiendo temas hasta 10 más... manteniendo
         * 10 más siempre".
         * ---
         * H08 PARTE 2 (S009) -- how many Radio tracks are always kept
         * queued ahead. Explicit request from Miguel Ángel after
         * testing the first version (a single track): "I'd keep
         * adding tracks up to 10 more... always keeping 10 more".
         */
        const val RADIO_QUEUE_SIZE = 10

        /**
         * H08 -- por encima de esto, un resultado de búsqueda se
         * descarta como candidato de Radio por sospecha de ser una
         * compilación, no una canción suelta. 15 min es generoso a
         * propósito, ver docstring de fetchOneRadioTrack().
         * ---
         * H08 -- above this, a search result is discarded as a Radio
         * candidate on suspicion of being a compilation, not a single
         * song. 15 min is deliberately generous, see
         * fetchOneRadioTrack()'s docstring.
         */
        const val RADIO_MAX_TRACK_SECONDS = 15 * 60

        /**
         * H08 -- palabras en el título que delatan una compilación
         * (álbum completo, mejores éxitos, playlist ajena) en vez de
         * una canción suelta. Detectado en pruebas reales (S009):
         * "Elvis Presley Greatest Hits Playlist Full Album", "The
         * Beatles - Greatest Hits Full Album", "Led Zeppelin -
         * Mothership (Full Album)". Complementa el filtro de
         * duración, no lo sustituye -- una compilación corta (p.ej.
         * un "Top 10" de 14 minutos) podría colarse solo por
         * duración.
         * ---
         * H08 -- title words that give away a compilation (full
         * album, greatest hits, someone else's playlist) instead of a
         * single song. Found in real testing (S009): "Elvis Presley
         * Greatest Hits Playlist Full Album", "The Beatles - Greatest
         * Hits Full Album", "Led Zeppelin - Mothership (Full Album)".
         * Complements the duration filter, doesn't replace it -- a
         * short compilation (e.g. a 14-minute "Top 10") could slip
         * through on duration alone.
         */
        val COMPILATION_TITLE_HINTS = listOf(
            "full album",
            "greatest hits",
            "playlist",
            "compilation",
            "best songs of",
            "best of",
        )
    }
}

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
    /**
     * S011 -- fallo real reportado por Miguel Ángel ("carátula canción
     * tocándose, de fondo" en la notificación): `toMediaItem()` nunca
     * ponía artista ni carátula en el `MediaMetadata`, así que
     * `DefaultMediaNotificationProvider` (que fabrica la notificación
     * DIRECTAMENTE a partir de esos metadatos) nunca tenía nada que
     * mostrar. `coverArtUrl` real de MusicBrainz si ya está resuelto,
     * si no la miniatura de YouTube como respaldo -- así la
     * notificación muestra algo desde el primer segundo en vez de
     * esperar a que Biblioteca resuelva la carátula real. Los
     * llamantes que conocen la pista pasan
     * `track.coverArtUrl ?: track.thumbnailUrl`.
     * ---
     * S011 -- real bug reported by Miguel Ángel ("song artwork playing
     * in the background" of the notification): `toMediaItem()` never
     * set artist or artwork on `MediaMetadata`, so
     * `DefaultMediaNotificationProvider` (which builds the
     * notification DIRECTLY from that metadata) never had anything to
     * show. Real MusicBrainz `coverArtUrl` if already resolved,
     * otherwise the YouTube thumbnail as a fallback -- so the
     * notification shows something from the first second instead of
     * waiting for the Library to resolve the real cover art.
     */
    val artworkUri: String? = null,
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
    private val knownHitsRepository: com.miguelaetxio.mimoo.data.remote.KnownHitsRepository,
    // S013/S014 -- fuente de "disco" del cupo 80/10/10 (10%, ver
    // ANNEX_H08.md sección "S013" punto 8): lista los artistas ya
    // descargados para poder ofrecer alguno como parte de la Radio.
    private val searchResultTrackRepository: com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository,
    // S016 -- el cupo 80/10/10 deja de ser fijo: exploración y disco
    // ahora se leen en vivo de Ajustes (ver ANNEX_H08.md punto 6,
    // RESUMPTION_POINT.md "Siguiente sesión (H08, cupo configurable)").
    // Se lee el StateFlow.value directamente en dueForExploreQuota()/
    // dueForDiscoQuota() -- ambas son funciones síncronas llamadas
    // desde una corrutina ya en marcha (fetchRoundCandidate()), no
    // necesitan collect().
    private val uiPreferencesManager: com.miguelaetxio.mimoo.data.access.UiPreferencesManager,
    // S016, segundo bloque -- historial persistente de artistas de
    // Radio ENTRE sesiones (petición explícita de Miguel Ángel: "que
    // las listas no sean siempre igual"). Preferencia suave, ver
    // RadioSessionHistoryManager.
    private val radioSessionHistoryManager: com.miguelaetxio.mimoo.data.access.RadioSessionHistoryManager,
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
                    radioTracksAccepted = 0
                    radioExploreTracksUsed = 0
                    radioDiscoTracksUsed = 0
                    radioDiscoExhausted = false
                    radioLibraryArtistProfileCache.clear()
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

    /**
     * S011 -- cupo de "exploración" de la sesión de Radio (petición
     * explícita de Miguel Ángel): "una de cada diez canciones es la
     * que nos dé que no esté [en el diccionario de éxitos]... las
     * otras nueve deben cumplir con estar". `radioTracksAccepted`
     * cuenta TODAS las pistas que Radio ha añadido de verdad esta
     * sesión (conocidas + exploración); `radioExploreTracksUsed`
     * cuenta cuántas de ellas fueron "exploración" (no encontradas en
     * el diccionario). Se reinician junto con `radioAnchor` al
     * arrancar una sesión nueva.
     */
    private var radioTracksAccepted = 0
    private var radioExploreTracksUsed = 0
    private val radioUsedArtists = mutableSetOf<String>()

    /**
     * S013/S014 -- cupo diccionario/exploración/disco (80/10/10 por
     * defecto, ver ANNEX_H08.md sección "S013" puntos 6-8);
     * configurable desde S016 vía Ajustes, ver
     * `UiPreferencesManager.radioExplorePercent`/`radioDiscoPercent`.
     * `radioDiscoTracksUsed` cuenta las pistas de disco aceptadas esta
     * sesión. `radioDiscoExhausted` (punto 7.1): true cuando el cupo
     * de disco ya no tiene NINGÚN candidato español -- se retira el
     * resto de la sesión y el reparto pasa a diccionario+exploración
     * en la proporción configurada. Se resetea junto con el resto del
     * estado de Radio.
     */
    private var radioDiscoTracksUsed = 0
    private var radioDiscoExhausted = false

    /**
     * S013/S014, punto 8.2 -- caché en memoria de género/país/década
     * por artista de biblioteca local, dentro de UNA sesión de Radio
     * (para no repetir la consulta a MusicBrainz si el mismo artista
     * de disco vuelve a salir candidato). Se resetea junto con
     * radioAnchor.
     */
    private val radioLibraryArtistProfileCache =
        mutableMapOf<String, com.miguelaetxio.mimoo.data.remote.RadioRepository.ArtistProfile?>()

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
     * S010 -- CUATRO intentos en cadena para fijar el ancla de una
     * sesión de Radio, cada uno solo si el anterior no encontró NADA
     * en MusicBrainz:
     *   1. Nombre de canal de YouTube (más fiable en general, pero
     *      inútil si el canal es una resubida ajena -- p.ej. "Radio
     *      Futura - Escuela de Calor" subida por un canal random
     *      llamado "OldGuitar8", nada que ver con la banda real).
     *   2. Artista estructurado de H05 (emparejamiento heurístico
     *      contra MusicBrainz por título -- puede no existir para esa
     *      pista en absoluto).
     *   3. Parseado del propio título del vídeo, patrón
     *      "Artista - Canción" (extremadamente común en YouTube,
     *      incluso en resubidas de canales random como el caso de
     *      arriba).
     *   4. FIX REAL S016 (reemplaza el antiguo fallback fijo a género
     *      "classical" de S010 -- orden explícita y repetida de Miguel
     *      Ángel: NUNCA MÁS cae a género fijo, en ningún punto del
     *      flujo) -- si ninguno de los tres anteriores encontró NADA
     *      (caso real: "Def Con Dos Armas pal pueblo", subida por un
     *      canal random sin relación, sin artista de H05, sin guion en
     *      el título que parsear), el ancla se deriva de disco: un
     *      artista al azar de la biblioteca local ya descargada, cuyo
     *      perfil (género/país/década) se resuelve vía MusicBrainz
     *      igual que en pickDiscoCandidate(). Si tampoco eso da nada,
     *      la función devuelve `null` y la Radio no arranca esta vez.
     * ---
     * S010 -- FOUR chained attempts to fix a Radio session's anchor,
     * each only if the previous one found NOTHING in MusicBrainz:
     *   1. YouTube channel name.
     *   2. H05's structured artist.
     *   3. Parsed from the video title itself, "Artist - Song" pattern.
     *   4. FIX REAL S016 (reemplaza el fallback "classical" de S010,
     *      orden explícita y repetida de Miguel Ángel: NUNCA MÁS cae a
     *      género fijo) -- si ninguno de los tres anteriores encontró
     *      NADA en MusicBrainz, el ancla se deriva de disco: un
     *      artista al azar de la biblioteca local ya descargada, cuyo
     *      perfil (género/país/década) se resuelve vía MusicBrainz
     *      igual que en pickDiscoCandidate(). Si tampoco eso da nada
     *      (sin descargas, o ninguna resuelve perfil), la función
     *      devuelve `null` y la Radio simplemente no arranca esta vez
     *      -- nunca rellenar con un género arbitrario sin relación.
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
                "intentos (canal, H05, título) -- derivando ancla de disco (biblioteca local), NUNCA clásica",
        )
        return resolveAnchorFromDisco(excludeArtistName = anchorArtistName)
    }

    /**
     * S016 -- último recurso de anclaje de sesión, reemplaza el
     * antiguo fallback fijo a "classical". Recorre artistas ya
     * descargados (misma fuente que el cupo de disco) en orden
     * aleatorio y resuelve el primero cuyo perfil MusicBrainz tenga al
     * menos un género -- ese perfil (género/país/década) se convierte
     * en el ancla de la sesión completa. `null` si no hay biblioteca o
     * ninguna resuelve perfil -- la Radio no arranca esta vez, en vez
     * de arrancar con un género arbitrario sin relación con el usuario.
     */
    private suspend fun resolveAnchorFromDisco(excludeArtistName: String): RadioAnchor? {
        val candidates = searchResultTrackRepository.getAllOnce()
            .mapNotNull { it.artist }
            .distinct()
            .filter { !it.equals(excludeArtistName, ignoreCase = true) }
            .shuffled()
        for (artistName in candidates) {
            val profile = radioRepository.lookupArtistProfile(artistName) ?: continue
            val genre = profile.genres.firstOrNull() ?: continue
            val isSpanish = profile.country == "ES" || knownHitsRepository.isKnownSpanishArtist(artistName)
            RadioDebugLogger.log(
                appContext, storageManager,
                "resolveAnchorFromDisco() -- ancla derivada de disco: '$artistName' " +
                    "(género='$genre', país=${profile.country}, década=${profile.decadeBegin}, es=$isSpanish)",
            )
            return RadioAnchor(
                genre = genre,
                country = profile.country,
                decadeBegin = profile.decadeBegin,
                isSpanishOrigin = isSpanish,
            )
        }
        RadioDebugLogger.log(
            appContext, storageManager,
            "resolveAnchorFromDisco() -- ni la biblioteca local tiene nada resoluble -- " +
                "la Radio no arranca esta vez, eslabón roto de verdad",
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
    /**
     * H12 (S018) -- visibilidad ampliada de `private` a `internal`
     * para que PlayerBarViewModel la reutilice en el menú de tres
     * puntos ("Ver artista"), sin duplicar esta lógica de parseo.
     * Comportamiento sin cambios respecto a su uso original en Radio.
     * ---
     * H12 (S018) -- visibility widened from `private` to `internal` so
     * PlayerBarViewModel can reuse it for the three-dot menu ("View
     * artist"), without duplicating this parsing logic. Behavior
     * unchanged from its original use in Radio.
     */
    internal fun parseArtistFromTitle(title: String?): String? {
        if (title.isNullOrBlank()) return null
        val separatorIndex = title.indexOf(" - ")
        if (separatorIndex <= 0) return null
        val candidate = title.substring(0, separatorIndex).trim()
        return candidate.takeIf { it.length > 2 }
    }

    /**
     * S013/S014 -- punto de entrada de un ciclo de reposición de
     * Radio. Resuelve el ancla (una sola vez por sesión) y delega en
     * fetchRoundCandidate() el reparto de cupo 80/10/10. Nunca lanza.
     */
    private suspend fun fetchOneRadioTrack(anchorArtistName: String): QueueItem? =
        try {
            val anchor = radioAnchor ?: resolveAnchorWithFallbacks(anchorArtistName)?.also {
                radioAnchor = it
            }
            if (anchor == null) {
                null
            } else {
                fetchRoundCandidate(anchor, anchorArtistName)
            }
        } catch (e: Exception) {
            RadioDebugLogger.log(
                appContext, storageManager,
                "fetchOneRadioTrack(ancla='$anchorArtistName') -- EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}",
            )
            null
        }

    /**
     * S013/S014 -- un ciclo completo de selección de cupo (ver
     * ANNEX_H08.md sección "S013" puntos 6-8): 10% disco (si toca y no
     * está agotado para el origen de esta sesión) -> 10% exploración
     * vía MusicBrainz (si toca) -> 80% diccionario (por defecto) ->
     * cascada de fallback final (punto 7) si los tres fallan en la
     * misma vuelta. Nunca lanza.
     */
    private suspend fun fetchRoundCandidate(anchor: RadioAnchor, anchorArtistName: String): QueueItem? {
        val excludeNames = radioUsedArtists + anchorArtistName
        // S016, segundo bloque -- preferencia suave entre sesiones
        // (ver RadioSessionHistoryManager). Se calcula UNA vez por
        // vuelta, no por cupo -- es la misma lista para los tres.
        val avoidNames = radioSessionHistoryManager.recentlyUsedLower()

        if (dueForDiscoQuota()) {
            val discoItem = pickDiscoCandidate(anchor, excludeNames, avoidNames)
            if (discoItem != null) {
                radioDiscoTracksUsed++
                radioTracksAccepted++
                registerUsedArtist(discoItem.artist)
                RadioDebugLogger.log(
                    appContext, storageManager,
                    "fetchRoundCandidate(ancla='$anchorArtistName') -> cupo=disco: '${discoItem.artist}' ('${discoItem.title}')",
                )
                return discoItem
            } else if (anchor.isSpanishOrigin && !radioDiscoExhausted) {
                radioDiscoExhausted = true
                RadioDebugLogger.log(
                    appContext, storageManager,
                    "fetchRoundCandidate(ancla='$anchorArtistName') -- cupo de disco sin candidatos " +
                        "españoles, retirado para el resto de la sesión (reparto pasa a diccionario+exploración)",
                )
            }
        }

        if (dueForExploreQuota()) {
            val exploreArtist = radioRepository.suggestRelatedArtist(anchor, excludeNames, avoidNames)
            if (exploreArtist != null) {
                val item = resolveYoutubeCandidate(anchorArtistName, exploreArtist, songTitle = null)
                if (item != null) {
                    radioExploreTracksUsed++
                    radioTracksAccepted++
                    registerUsedArtist(exploreArtist)
                    RadioDebugLogger.log(
                        appContext, storageManager,
                        "fetchRoundCandidate(ancla='$anchorArtistName') -> cupo=exploración: '$exploreArtist'",
                    )
                    return item
                }
            }
        }

        val dictHit = pickDictCandidate(anchor, excludeNames, avoidNames)
        if (dictHit != null) {
            val item = resolveYoutubeCandidate(anchorArtistName, dictHit.artist, dictHit.song)
            if (item != null) {
                radioTracksAccepted++
                registerUsedArtist(dictHit.artist)
                RadioDebugLogger.log(
                    appContext, storageManager,
                    "fetchRoundCandidate(ancla='$anchorArtistName') -> cupo=diccionario: " +
                        "'${dictHit.artist}' - '${dictHit.song}' (género='${dictHit.genre}', " +
                        "ancla=género:'${anchor.genre}'/década:${anchor.decadeBegin})",
                )
                return item
            }
        } else {
            RadioDebugLogger.log(
                appContext, storageManager,
                "fetchRoundCandidate(ancla='$anchorArtistName') -- cupo=diccionario sin candidatos " +
                    "para género='${anchor.genre}' década=${anchor.decadeBegin} (cascada género/década " +
                    "agotada del todo esta sesión) -- pasando a resolveFinalFallback()",
            )
        }

        return resolveFinalFallback(anchor, anchorArtistName, excludeNames)
    }

    /**
     * S016 -- generalización del cupo, antes fijo al 10% (`used * 10 <
     * accepted + 1`, válido solo para p=10). La nueva fórmula
     * `used * 100 < (accepted + 1) * percent` es la misma desigualdad
     * reescrita para cualquier `percent` sin división (evita el
     * redondeo de enteros que un `/` habría introducido con reparto no
     * exacto, p.ej. 20/30/50) -- verificado con p=10: `used*100 <
     * (accepted+1)*10` es literalmente `used*10 < accepted+1`
     * multiplicado por 10 en ambos lados, mismo resultado exacto.
     * `percent == 0` se trata aparte: nunca toca ese cupo si Miguel
     * Ángel lo ha puesto a cero en Ajustes.
     */
    private fun dueForQuota(usedTracks: Int, percent: Int): Boolean =
        percent > 0 && usedTracks * 100 < (radioTracksAccepted + 1) * percent

    private fun dueForExploreQuota(): Boolean =
        dueForQuota(radioExploreTracksUsed, uiPreferencesManager.radioExplorePercent.value)

    private fun dueForDiscoQuota(): Boolean =
        !radioDiscoExhausted && dueForQuota(radioDiscoTracksUsed, uiPreferencesManager.radioDiscoPercent.value)

    /** S016, segundo bloque -- registra en memoria de sesión Y en el historial persistente entre sesiones. */
    private fun registerUsedArtist(artist: String?) {
        artist?.let {
            radioUsedArtists.add(it)
            radioSessionHistoryManager.registerUsed(it)
        }
    }

    /**
     * S013 punto 7 -- se llega aquí solo cuando disco (si activo),
     * exploración y diccionario han fallado los tres en la misma
     * vuelta. En modo español: se permite UNA vez un tema conocido
     * pero extranjero (punto 7.2) antes del último recurso.
     *
     * **Fix real S016, segundo bloque** -- orden explícito y repetido
     * de Miguel Ángel: NUNCA MÁS cae a género fijo "classical". El
     * último peldaño, cuando ni siquiera el extranjero conocido de la
     * misma década tiene nada, pasa a ser `pickDiscoCandidate()` --
     * la MISMA biblioteca local del cupo de disco (S013 punto 8, con
     * su propia relajación interna género->década->origen puro, pero
     * origen SIEMPRE respetado). Se llama directamente, sin mirar
     * `radioDiscoExhausted` (esa marca solo apaga el cupo REGULAR del
     * 10%, no este último recurso) -- si la biblioteca de verdad no
     * tiene nada que ofrecer, la función devuelve `null` y
     * `topUpRadioQueueIfNeeded()` para la Radio con su log habitual de
     * "sin más candidatos", que es el único desenlace aceptable
     * cuando NADA (diccionario, exploración, disco) tiene ya nada que
     * ofrecer -- nunca rellenar con música sin relación alguna.
     */
    private suspend fun resolveFinalFallback(
        anchor: RadioAnchor,
        anchorArtistName: String,
        excludeNames: Set<String>,
    ): QueueItem? {
        RadioDebugLogger.log(
            appContext, storageManager,
            "resolveFinalFallback(ancla='$anchorArtistName') -- diccionario y exploración agotados " +
                "esta vuelta, último recurso: disco (biblioteca local), NUNCA clásica",
        )
        val discoItem = pickDiscoCandidate(anchor, excludeNames, avoidNames)
        if (discoItem != null) {
            radioTracksAccepted++
            registerUsedArtist(discoItem.artist)
            radioDiscoTracksUsed++
            return discoItem
        }

        RadioDebugLogger.log(
            appContext, storageManager,
            "resolveFinalFallback(ancla='$anchorArtistName') -- ni siquiera disco tiene candidatos -- " +
                "eslabón roto de verdad, la Radio se para en vez de rellenar con música sin relación",
        )
        return null
    }

    /**
     * S013, cupo del 80% -- elige un candidato del diccionario de
     * éxitos ampliado para género+década+origen del ancla.
     * **S020 -- el origen lo fija el ancla y no se relaja jamás.**
     * Desaparece `allowForeignFallback` (antiguo punto 7.2, que
     * permitía una vez un conocido extranjero en sesión española):
     * contradice la regla de Miguel Ángel "si es española de origen se
     * fija en España". Esta función solo traduce el ancla a `Origin`;
     * género y década los gestiona por completo
     * `KnownHitsRepository.randomHit()`.
     *
     * **Historial S016 en dos pasos, mismo bloque de trabajo:**
     * 1. Fix de década (reportado por Miguel Ángel con
     *    `radio_relacionados_debug.txt`): esta función tenía un
     *    fallback que, al agotarse la década del ancla, caía
     *    silenciosamente a CUALQUIER década sin marca en el log.
     *    Eliminado en un primer commit.
     * 2. Corrección de Miguel Ángel sobre el punto anterior: nunca
     *    pidió que el diccionario dejara de filtrar por género -- el
     *    diccionario, hasta este bloque, JAMÁS había tenido dato de
     *    género por entrada (`KnownHitsRepository.RawHit` no lo
     *    tenía). Añadido `genre` a cada entrada del JSON y
     *    reescrita `randomHit()` con la cascada género+década
     *    simétrica que pidió explícitamente: género+década exacta ->
     *    se agota género, se mantiene década -> se agota también eso,
     *    se mantiene género sin década -> `null`. El origen nunca se
     *    relaja en ninguno de esos tres pasos.
     */
    private fun pickDictCandidate(
        anchor: RadioAnchor,
        excludeArtists: Set<String>,
        avoidArtists: Set<String>,
    ): com.miguelaetxio.mimoo.data.remote.KnownHitsRepository.KnownHit? {
        val origin = if (anchor.isSpanishOrigin) {
            com.miguelaetxio.mimoo.data.remote.KnownHitsRepository.Origin.ES
        } else {
            com.miguelaetxio.mimoo.data.remote.KnownHitsRepository.Origin.INTL
        }
        return knownHitsRepository.randomHit(anchor.genre, anchor.decadeBegin, origin, excludeArtists, avoidArtists)
    }

    /**
     * S013, punto 8 -- 10% de la biblioteca local. Lista artistas ya
     * descargados (misma fuente que Biblioteca), en orden aleatorio,
     * excluyendo los ya usados esta sesión; para cada uno resuelve
     * género/país/década vía MusicBrainz bajo demanda (cacheado por
     * artista dentro de la sesión, ver radioLibraryArtistProfileCache).
     *
     * **Cascada (S020, orden explícita de Miguel Ángel: "el género no
     * debe abandonarse"), idéntica a la del diccionario:**
     *   1. género + década exacta.
     *   2. se agota la década -> se mantiene el GÉNERO, cualquier
     *      década.
     *   3. nada -- `null`. Nunca cae a clásica ni al
     *      extranjero-conocido desde aquí, eso lo decide
     *      fetchRoundCandidate()/resolveFinalFallback() cuando este
     *      cupo devuelve null.
     *
     * El origen SIEMPRE fijo, nunca se relaja aquí.
     *
     * **Historial de los dos peldaños que se han caído.** El diseño
     * original tenía un último escalón ("cualquier pista que cumpla
     * origen") que ignoraba género Y década -- eliminado en S016 por
     * corrección de Miguel Ángel, porque metía Pink Floyd en una
     * sesión de flamenco rock español. La cascada simétrica que lo
     * sustituyó conservaba un escalón que mantenía la década y soltaba
     * el género; eliminado en S020 por la misma razón medida sobre log
     * real, ver `DOCS/ANNEX_H08.md` sección "S020".
     */
    private suspend fun pickDiscoCandidate(
        anchor: RadioAnchor,
        excludeArtists: Set<String>,
        avoidArtists: Set<String>,
    ): QueueItem? {
        val excludeLower = excludeArtists.map { it.lowercase() }.toSet()
        val avoidLower = avoidArtists.map { it.lowercase() }.toSet()
        val downloadedTracks = searchResultTrackRepository.getAllOnce()
            .filter {
                it.downloadStatus == com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.DONE &&
                    it.filePath != null && !it.artist.isNullOrBlank()
            }
        if (downloadedTracks.isEmpty()) return null

        val candidateArtists = downloadedTracks
            .mapNotNull { it.artist }
            .distinct()
            .filter { it.lowercase() !in excludeLower }
            .shuffled()
        if (candidateArtists.isEmpty()) return null

        data class ProfiledArtist(
            val artist: String,
            val profile: com.miguelaetxio.mimoo.data.remote.RadioRepository.ArtistProfile,
        )

        val originMatches = candidateArtists.mapNotNull { artistName ->
            val profile = radioLibraryArtistProfileCache.getOrPut(artistName) {
                radioRepository.lookupArtistProfile(artistName)
            } ?: return@mapNotNull null
            // S020 -- separación dura en los dos sentidos. País
            // desconocido en MusicBrainz cuenta como NO español: con
            // ancla española queda fuera, con ancla extranjera entra.
            val originOk = if (anchor.isSpanishOrigin) {
                profile.country == "ES"
            } else {
                profile.country != "ES"
            }
            if (!originOk) null else ProfiledArtist(artistName, profile)
        }
        if (originMatches.isEmpty()) return null

        fun genreOk(p: ProfiledArtist) = p.profile.genres.any { it.equals(anchor.genre, ignoreCase = true) }
        fun decadeOk(p: ProfiledArtist) = anchor.decadeBegin == null || p.profile.decadeBegin == anchor.decadeBegin

        /** S016, segundo bloque -- entre los que cumplen `condition`, prefiere los no evitados; si eso vacía la lista, ignora la preferencia. */
        fun pickPreferred(condition: (ProfiledArtist) -> Boolean): String? {
            val matching = originMatches.filter(condition)
            val preferred = matching.filter { it.artist.lowercase() !in avoidLower }
            return preferred.ifEmpty { matching }.firstOrNull()?.artist
        }

        // S020, orden explícita de Miguel Ángel: "el género no debe
        // abandonarse". Desaparece el peldaño intermedio que mantenía
        // la década y soltaba el género (`pickPreferred { decadeOk(it) }`)
        // -- mismo cambio y misma razón que en
        // `KnownHitsRepository.randomHit()`, para que los dos cupos
        // degraden igual. Si no hay nada del género del ancla en la
        // biblioteca local, este cupo devuelve null y resuelve la
        // vuelta siguiente: nunca sirve un género que no sea el del
        // ancla.
        // ---
        // S020 -- the genre is never abandoned. The intermediate rung
        // that kept the decade and dropped the genre is gone, matching
        // KnownHitsRepository.randomHit() so both quotas degrade the
        // same way.
        val chosenArtist = pickPreferred { genreOk(it) && decadeOk(it) }
            ?: pickPreferred { genreOk(it) }
            ?: return null

        val track = downloadedTracks
            .filter { it.artist.equals(chosenArtist, ignoreCase = true) }
            .randomOrNull() ?: return null
        return QueueItem(
            uri = track.filePath!!,
            title = track.title,
            isLocal = true,
            artist = track.artist ?: track.channelTitle,
            isFromRadio = true,
            youtubeId = track.youtubeId,
            channelTitle = track.channelTitle,
            artworkUri = track.coverArtUrl ?: track.thumbnailUrl,
        )
    }

    /**
     * Búsqueda gratuita en YouTube + filtro de duración/compilación +
     * resolución de stream -- mismo mecanismo que ya existía antes de
     * S013, ahora reutilizado por los cupos de diccionario,
     * exploración y el fallback final. `songTitle` no nulo
     * (diccionario) hace la búsqueda "artista + canción concreta" --
     * S013 punto 2 -- en vez de solo "artista" (exploración/fallback).
     * Nunca lanza -- cualquier fallo se trata como "no hay pista".
     */
    private suspend fun resolveYoutubeCandidate(
        anchorArtistName: String,
        artist: String,
        songTitle: String?,
    ): QueueItem? = try {
        val query = if (songTitle != null) "$artist $songTitle" else artist
        val searchResult = externalLinkResolver.searchYoutube(query, limit = 6)
        val track = searchResult.tracks.firstOrNull { candidate ->
            candidate.durationSeconds in 1..RADIO_MAX_TRACK_SECONDS &&
                COMPILATION_TITLE_HINTS.none { hint ->
                    candidate.title.contains(hint, ignoreCase = true)
                }
        }
        if (track == null) {
            RadioDebugLogger.log(
                appContext, storageManager,
                "resolveYoutubeCandidate(ancla='$anchorArtistName', query='$query') -- 0 de " +
                    "${searchResult.tracks.size} resultados pasaron el filtro de duración/compilación",
            )
            null
        } else {
            val streamUrl = try {
                streamResolver.resolveAudioStreamUrl("https://youtu.be/${track.youtubeId}")
            } catch (e: Exception) {
                RadioDebugLogger.log(
                    appContext, storageManager,
                    "resolveYoutubeCandidate(ancla='$anchorArtistName', query='$query') -- " +
                        "resolveAudioStreamUrl() falló: ${e::class.java.simpleName}: ${e.message}",
                )
                return null
            }
            RadioDebugLogger.log(
                appContext, storageManager,
                "resolveYoutubeCandidate(ancla='$anchorArtistName', query='$query') -> añadido: '${track.title}'",
            )
            QueueItem(
                uri = streamUrl,
                title = track.title,
                isLocal = false,
                artist = artist,
                isFromRadio = true,
                youtubeId = track.youtubeId,
                channelTitle = track.channelTitle,
                artworkUri = track.thumbnailUrl,
            )
        }
    } catch (e: Exception) {
        RadioDebugLogger.log(
            appContext, storageManager,
            "resolveYoutubeCandidate(ancla='$anchorArtistName', artist='$artist') -- EXCEPCIÓN: " +
                "${e::class.java.simpleName}: ${e.message}",
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
                    .apply {
                        if (!item.artist.isNullOrBlank()) setArtist(item.artist)
                        if (!item.artworkUri.isNullOrBlank()) {
                            setArtworkUri(android.net.Uri.parse(item.artworkUri))
                        }
                    }
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
        artworkUri: String? = null,
    ) {
        playQueue(
            listOf(
                QueueItem(
                    streamUrl,
                    title,
                    isLocal,
                    artist,
                    youtubeId = youtubeId,
                    channelTitle = channelTitle,
                    artworkUri = artworkUri,
                )
            ),
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
     * "Reproducir en aleatorio" desde Biblioteca -- SUSTITUYE al
     * patrón anterior (`playQueue(items.shuffled())`, una mezcla
     * manual de la lista de Kotlin, ajena por completo al modo
     * aleatorio real de ExoPlayer). Aquí los `items` se insertan en
     * su orden ORIGINAL y se activa `player.shuffleModeEnabled` --
     * así es el propio ExoPlayer quien decide el orden real (y lo
     * recalcula al saltar pistas, no una mezcla fija de un solo
     * uso), y `PlaybackState.shuffleModeEnabled` -- y por tanto la
     * chapita del reproductor -- queda sincronizado de verdad.
     *
     * Fallo real reportado por Miguel Ángel (S019): "escoger el modo
     * aleatorio desde la biblioteca implica no saber qué modo de
     * reproducción se está ejecutando" -- con el mezclado manual, el
     * reproductor mostraba "aleatorio desactivado" mientras en
     * realidad se escuchaba una lista mezclada, sin ninguna forma de
     * saberlo desde la UI.
     * ---
     * "Play shuffled" from Library -- REPLACES the previous pattern
     * (`playQueue(items.shuffled())`, a manual Kotlin-list shuffle
     * completely unrelated to ExoPlayer's real shuffle mode). Here
     * `items` are inserted in their ORIGINAL order and
     * `player.shuffleModeEnabled` is turned on -- so ExoPlayer itself
     * decides the real play order (and recalculates it when skipping
     * tracks, not a fixed one-off shuffle), and
     * `PlaybackState.shuffleModeEnabled` -- and therefore the
     * player's chip -- stays genuinely in sync.
     *
     * Real bug reported by Miguel Ángel (S019): "choosing shuffle mode
     * from the Library means not knowing which playback mode is
     * running" -- with the manual shuffle, the player showed "shuffle
     * off" while a shuffled list was actually playing, with no way to
     * tell from the UI.
     */
    fun playQueueShuffled(items: List<QueueItem>) {
        playQueue(items)
        if (!player.shuffleModeEnabled) {
            player.shuffleModeEnabled = true
        }
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
    /**
     * S010 -- petición explícita de Miguel Ángel: al vaciar la cola
     * también hay que borrar la sesión de Radio (ancla de género+país,
     * artista de respaldo, título, y los ya-usados), no solo dejarla
     * viva a la espera de que el próximo topUp reutilice un ancla
     * vieja que ya no tiene ningún sentido con nada de lo que suene a
     * partir de ahora. Sin este reseteo, vaciar la cola no bastaba
     * para "empezar otra Radio" de verdad -- la siguiente vez que la
     * Radio topara con el final de la cola, seguiría anclada al
     * artista de la sesión anterior.
     * ---
     * S010 -- explicit request from Miguel Ángel: clearing the queue
     * must also clear the Radio session (genre+country anchor,
     * fallback artist, title, and already-used artists), not just
     * leave it alive waiting for the next top-up to reuse a stale
     * anchor that no longer makes sense with anything played from now
     * on. Without this reset, clearing the queue wasn't really enough
     * to "start another Radio" -- the next time Radio hit the end of
     * the queue, it would still be anchored to the previous session's
     * artist.
     */
    fun clearQueue() {
        queueItems.clear()
        player.clearMediaItems()
        player.stop()
        radioAnchorArtist = null
        radioAnchorArtistFallback = null
        radioAnchorTrackTitle = null
        radioAnchor = null
        radioUsedArtists.clear()
        radioTracksAccepted = 0
        radioExploreTracksUsed = 0
        radioDiscoTracksUsed = 0
        radioDiscoExhausted = false
        radioLibraryArtistProfileCache.clear()
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

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
import com.miguelaetxio.mimoo.util.SearchNormalizer
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
     * sufijos "- Topic"/"VEVO"/"Oficial", ver link_resolver.py).
     *
     * S025 -- ESTE CAMPO NO TIENE NADA QUE VER CON EL ANCLA DE LA
     * RADIO (H08). S010 lo declaró aquí "más fiable como ancla"; era
     * falso y costó varias sesiones de radios descarriladas. El canal
     * describe a QUIEN SUBIÓ el vídeo, no QUÉ ES el vídeo: solo
     * acierta por casualidad, cuando el canal resulta ser el artista.
     * Sigue siendo válido para mostrar en pantalla, para H11 (Canales)
     * y para comprobar si un vídeo candidato es de verdad del artista
     * pedido (`matchesArtist`), que es una verificación y no una
     * fuente de identidad.
     * ---
     * S010 -- different from `artist`. `artist` is H05's "structured
     * artist" (AlbumMatchRepository): a heuristic match against
     * MusicBrainz releases by title, useful for organizing the
     * Library but with real false positives on ambiguous/obscure
     * titles. `channelTitle` is that specific video's YouTube channel
     * name (already cleaned of "- Topic"/"VEVO"/"Oficial" suffixes).
     *
     * S025 -- this field has nothing to do with Radio's anchor (H08).
     * It describes WHO UPLOADED the video, not WHAT it is. Still valid
     * for display, for H11 (Channels) and for verifying a candidate
     * video really belongs to the requested artist.
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
    // Se lee el StateFlow.value directamente en basePercent() --
    // función síncrona llamada
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
                    // S025 -- EL NOMBRE DEL CANAL NO ENTRA EN EL ANCLA.
                    // NUNCA, POR NINGUNA VÍA.
                    //
                    // Orden de Miguel Ángel, sin matices: *"el canal no
                    // tiene absolutamente nada que ver con el ancla.
                    // ¿Qué vamos a anclar, con el nombre del canal?
                    // Esto va de canciones, títulos de canciones y
                    // nombres de grupos y artistas. No va de nombres de
                    // canales."*
                    //
                    // S024 retiró el canal de la CASCADA de intentos
                    // (ver resolveAnchorWithFallbacks), pero lo dejó
                    // vivo aquí, que es donde de verdad nacía: era la
                    // SEMILLA de la identidad del ancla. Consecuencias
                    // reales, medidas en el log de S024:
                    //
                    //   1. El log entero decía ancla='OlvidadasCanciones'
                    //      -- el nombre de un canal de recopilaciones --
                    //      cuando el tema era de Pistones.
                    //   2. Peor que cosmético: ese nombre es el que
                    //      alimenta `anchorExclusion` en
                    //      fetchRoundCandidate(). Se estaba excluyendo
                    //      de la radio a un artista que no existe,
                    //      mientras el ancla real quedaba libre para
                    //      volver a salir sorteada en su propia radio.
                    //
                    // La identidad se siembra ahora solo de lo que
                    // describe el CONTENIDO del vídeo: el artista
                    // estructurado de H05 y, si no lo hay, el artista
                    // partido del propio título ("Artista - Canción").
                    // Si no hay ninguno de los dos, no hay ancla y la
                    // Radio no arranca sobre este tema -- mismo
                    // principio ya establecido más abajo: antes no
                    // arrancar que anclar en ruido.
                    // ---
                    // S025 -- the channel name is never part of the
                    // anchor, by any route. Identity is seeded only from
                    // what describes the video's CONTENT: H05's
                    // structured artist, else the artist parsed from the
                    // title. Neither means no anchor, and Radio simply
                    // doesn't start on this track.
                    radioAnchorArtist = currentItem?.artist?.takeIf { it.isNotBlank() }
                        ?: parseArtistFromTitle(currentItem?.title)
                    // S025 -- el artista estructurado de H05 es el
                    // primer peldaño de la cascada y se guarda SIEMPRE.
                    // Antes se guardaba solo si difería del nombre del
                    // canal; con el canal fuera, esa condición dejaría
                    // el peldaño vacío justo cuando el dato es bueno
                    // (radioAnchorArtist ya sería ese mismo artista).
                    // ---
                    // S025 -- H05's structured artist is the cascade's
                    // first step and is now always stored.
                    radioAnchorArtistFallback = currentItem?.artist?.takeIf { it.isNotBlank() }
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
                    radioRecentArtists.clear()
                    radioTracksAccepted = 0
                    radioPortionUsed.clear()
                    radioPortionExhausted.clear()
                    radioUsedSongs.clear()
        radioUsedTitles.clear()
                    radioUsedTitles.clear()
                    radioKnownSongsExhausted = false
                    radioDiscoArtistsExhausted = false
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
     * S020 -- las TRES porciones de la Radio, tal como las cerró
     * Miguel Ángel (ver `DOCS/ANNEX_H08.md`, "Las TRES porciones y el
     * reparto dinámico"):
     *
     * - `KNOWN`  -- 1º temas conocidos, 2º artistas conocidos con
     *               temas no catalogados.
     * - `DISCO`  -- 1º artistas de la biblioteca local, 2º más temas
     *               de esos mismos artistas.
     * - `UNKNOWN`-- artistas sin éxito catalogado, vía MusicBrainz.
     */
    private enum class RadioPortion { KNOWN, DISCO, UNKNOWN }

    /**
     * `radioTracksAccepted` cuenta TODAS las pistas que Radio ha
     * añadido de verdad esta sesión; `radioPortionUsed`, cuántas ha
     * puesto cada porción. Se reinician junto con `radioAnchor`.
     *
     * `radioPortionExhausted` -- porciones muertas. Su porcentaje se
     * reparte a partes iguales entre las vivas, ver
     * `effectiveQuotaPercent()`.
     *
     * `radioUsedSongs` -- **exclusión DURA**, claves `artista|canción`
     * ya servidas (ver `KnownHitsRepository.songKey`). S020, orden
     * textual: *"si hay que repetir artista se repite. Mientras, no se
     * repite canción hasta que no quede más remedio."*
     *
     * `radioUsedArtists` -- **preferencia SUAVE** desde S020. Hasta
     * entonces excluía artistas de forma dura, y eso era lo que
     * forzaba las degradaciones de género que había que eliminar.
     */
    private var radioTracksAccepted = 0
    private val radioPortionUsed = mutableMapOf<RadioPortion, Int>()
    private val radioPortionExhausted = mutableSetOf<RadioPortion>()
    private val radioUsedArtists = mutableSetOf<String>()
    private val radioUsedSongs = mutableSetOf<String>()

    /**
     * S025 -- TÍTULOS ya sonados, sin mirar el artista.
     *
     * Red de seguridad última y deliberadamente tonta. La clave
     * `artista|canción` de `radioUsedSongs` falla en cuanto el artista
     * llega escrito de dos formas distintas -- 'Wings' y
     * 'Paul McCartney & Wings' son el mismo 'Band on the Run' --, y
     * entonces el tema vuelve a sonar.
     *
     * Miguel Ángel, sobre esto: *"lo único es que el título no se
     * repita. No se puede repetir el puto título."* Así que aquí no se
     * mira nada más: título normalizado, y si ya sonó, fuera. Que dos
     * canciones distintas compartan título exacto y una se pierda es un
     * precio ridículo comparado con volver a oír la misma doce veces.
     */
    private val radioUsedTitles = mutableSetOf<String>()

    private fun titleKey(title: String?): String =
        SearchNormalizer.tight(SearchNormalizer.songTitleKey(title.orEmpty()))

    /**
     * Peldaño interno agotado dentro de una porción que sigue viva:
     * `radioKnownSongsExhausted` (Conocidos ya no tiene temas
     * catalogados y sirve artistas conocidos con temas libres) y
     * `radioDiscoArtistsExhausted` (Disco ya usó todos sus artistas y
     * pasa a sacar más temas de ellos). Agotar un peldaño NO agota la
     * porción -- para eso deben fallar los dos.
     */
    private var radioKnownSongsExhausted = false
    private var radioDiscoArtistsExhausted = false

    /**
     * Últimos artistas servidos, en orden y con tope
     * `RADIO_ARTIST_WINDOW`. S022: es la ventana que impide que un
     * artista suene dos veces demasiado cerca. `radioUsedArtists` no
     * sirve para esto -- es un `Set` sin orden, así que sabe *si* un
     * artista sonó, pero no *hace cuánto*, y desde S020 además es solo
     * preferencia suave.
     */
    private val radioRecentArtists = ArrayDeque<String>()

    /**
     * ¿Ha sonado este artista dentro de las últimas
     * `RADIO_ARTIST_WINDOW` canciones? Criterio de Miguel Ángel
     * (S022): a veinte canciones de distancia repetir no molesta; dos
     * veces en diez, sí.
     */
    private fun isArtistTooRecent(artist: String?): Boolean {
        val name = artist?.lowercase() ?: return false
        return radioRecentArtists.any { it == name }
    }

    /**
     * S013/S014, punto 8.2 -- caché en memoria de género/país/década
     * por artista de biblioteca local, dentro de UNA sesión de Radio
     * (para no repetir la consulta a MusicBrainz si el mismo artista
     * de disco vuelve a salir candidato). Se resetea junto con
     * radioAnchor.
     */
    private val radioLibraryArtistProfileCache =
        mutableMapOf<String, com.miguelaetxio.mimoo.data.remote.RadioRepository.ArtistProfile?>()

    /**
     * S023 -- ¿falló alguna consulta de perfil por red durante la
     * última pasada de `pickDiscoCandidate()`?
     *
     * No basta con mirar `radioRepository.lastFailureWasTransient` al
     * final: ese indicador se reinicia con cada éxito, así que si la
     * última consulta salió bien tapa los diez 503 anteriores. Aquí se
     * recuerda si hubo ALGUNO, que es lo que decide si la porción
     * puede darse por agotada.
     */
    private var radioDiscoLookupFailedTransiently = false

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
                    // S020 -- el registro real (porción, totales,
                    // artista suave y tema duro) lo hace
                    // acceptRadioItem() dentro de la vuelta. Aquí solo
                    // queda la red de seguridad del artista, por si la
                    // pista llegó por un camino que no pasa por allí.
                    newItem.artist?.let { radioUsedArtists.add(it) }
                    radioUsedSongs.add(knownHitsRepository.songKey(newItem.artist, newItem.title))

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
     * S025 -- TRES intentos en cadena para fijar el ancla de una
     * sesión de Radio, todos sobre fuentes que describen el CONTENIDO
     * del vídeo. Cada uno se prueba solo si el anterior no encontró
     * NADA en MusicBrainz (un fallo de RED no baja de peldaño: ver
     * `lastFailureWasTransient` más abajo):
     *   1. Artista estructurado de H05 (dato real de la pista;
     *      emparejamiento heurístico contra MusicBrainz por título,
     *      puede no existir para esa pista en absoluto).
     *   2. Parseado del propio título del vídeo, patrón
     *      "Artista - Canción" -- extremadamente común en YouTube,
     *      y funciona incluso en resubidas de canales ajenos.
     *   3. Búsqueda por palabras del título (S023, idea de Miguel
     *      Ángel tras el caso "Led Zeppelin Immigrant song", que no
     *      lleva " - " y por tanto el peldaño 2 no sabía leer).
     *
     * Si los tres fallan, la función devuelve `null` y la Radio no
     * arranca sobre este tema. NUNCA se rellena con un género fijo
     * (fuera desde S016) ni con un artista sorteado de la biblioteca
     * local (fuera desde S024): las dos veces que se intentó, el
     * resultado fue una radio sin relación con lo que sonaba.
     *
     * HISTÓRICO -- el nombre del canal de YouTube fue peldaño 1 en
     * S010, peldaño 3 en S023, se retiró de esta cascada en S024 y
     * dejó de ser semilla de la identidad del ancla en S025. No
     * vuelve: describe a QUIEN SUBIÓ el vídeo, no QUÉ ES.
     * ---
     * S025 -- THREE chained attempts to fix a Radio session's anchor,
     * all on sources that describe the video's CONTENT: H05's
     * structured artist, the artist parsed from the title, and a
     * word-by-word title lookup. If all three fail, Radio doesn't
     * start on this track -- never a fixed genre, never a random
     * artist from the local library. The YouTube channel name was
     * removed from this cascade in S024 and from anchor identity
     * altogether in S025.
     */
    private suspend fun resolveAnchorWithFallbacks(anchorArtistName: String): RadioAnchor? {
        // Histórico. S010 dio un respaldo para canales que NO son un
        // artista ("OldGuitar8", sin resultados en MusicBrainz): solo
        // saltaba cuando el canal FALLABA. S023 descubrió el agujero
        // con "Radio Futura - Divina" subido por un canal llamado
        // 'Kurt Cobain': un artista real, que resuelve perfectamente, y
        // por eso nunca se llegaba al título -- donde estaba el dato
        // bueno. Aquella sesión devolvió The Strokes, R.E.M. y The
        // Smiths. S023 bajó el canal a último peldaño.
        //
        // S024 -- EL CANAL NO ES FUENTE DE ANCLA. NUNCA.
        //
        // Orden de Miguel Ángel, sin matices: *"el canal no puede ser
        // objeto de ancla nunca. ¿Qué vamos a anclar por canal? Los
        // nombres de los canales, ¿qué tienen que ver? De hecho, lo
        // que estamos es contaminando las anclas si metemos los
        // nombres de los canales."*
        //
        // S023 ya lo había bajado a último peldaño tras el caso "Radio
        // Futura - Divina" subido por un canal llamado 'Kurt Cobain',
        // que ancló la sesión en grunge estadounidense. Bajarlo no
        // bastó: en S024 volvió a colarse en cuanto el peldaño bueno
        // falló por un timeout de red, y la radio sirvió Lou Reed.
        //
        // La razón de fondo es de fuente, no de orden: el canal
        // describe a QUIEN SUBIÓ el vídeo, no QUÉ ES. Un dato que solo
        // acierta por casualidad -- cuando el canal resulta ser el
        // artista -- no es un dato, es ruido con suerte. Se retira.
        //
        // Quedan las tres fuentes que describen el contenido: el
        // artista estructurado (H05, dato real de la pista), el
        // artista partido del título, y la búsqueda por palabras del
        // título de más abajo.
        val attempts = buildList {
            add("artista estructurado" to radioAnchorArtistFallback)
            add("título del tema" to parseArtistFromTitle(radioAnchorTrackTitle))
        }

        val tried = mutableSetOf<String>()
        for ((source, candidate) in attempts) {
            val name = candidate?.takeIf { it.isNotBlank() } ?: continue
            if (!tried.add(name.lowercase())) continue
            radioRepository.resolveAnchor(name, radioAnchorTrackTitle)?.let {
                RadioDebugLogger.log(
                    appContext, storageManager,
                    "resolveAnchorWithFallbacks() -- ancla fijada desde $source: '$name' " +
                        "(título='$radioAnchorTrackTitle')",
                )
                // S023 -- adoptar el nombre que de verdad resolvió. Sin
                // esto, el resto de la sesión seguía arrastrando el del
                // canal: el log entero decía ancla='Kurt Cobain' cuando
                // el ancla era Radio Futura, lo que costó tiempo de
                // diagnóstico. Y no era solo cosmético -- la exclusión
                // del artista del ancla estaba excluyendo al canal, así
                // que el artista real podía volver a salir sorteado en
                // su propia radio.
                radioAnchorArtist = name
                return it
            }
            // S024 -- UN FALLO DE RED NO AUTORIZA A BAJAR DE PELDAÑO.
            //
            // Verificado en log real, y es el caso que reporta Miguel
            // Ángel al ver aparecer a Lou Reed:
            //
            //   resolveAnchor('Radio Futura') -- EXCEPCIÓN: SocketTimeout
            //   resolveAnchor('Kurt Cobain') -- ...
            //   ancla fijada: género='grunge', país=US, década=null
            //
            // El peldaño bueno -- el título, 'Radio Futura' -- no falló
            // por no resolver: falló porque MusicBrainz no contestó a
            // tiempo. Y la cascada lo trató igual que un "este artista
            // no existe" y se fue al canal, que se llamaba 'Kurt
            // Cobain'. De ahí en cadena: ancla grunge/US/sin década ->
            // el diccionario no sirve nada -> KNOWN agotada al segundo
            // -> su 80% se reparte a DISCO -> Lou Reed, que en una
            // radio de grunge estadounidense encaja perfectamente.
            //
            // Es la lección de S022 escrita en `fetch_json()` de las
            // herramientas -- *"un 503 no es 'este artista no tiene
            // géneros', es 'ahora no'"* -- que aquí no se estaba
            // aplicando pese a existir ya el indicador.
            //
            // Sin ancla no se arranca: la siguiente vuelta reintenta, y
            // el orden por fiabilidad de fuente se respeta de verdad.
            if (radioRepository.lastFailureWasTransient) {
                RadioDebugLogger.log(
                    appContext, storageManager,
                    "resolveAnchorWithFallbacks() -- '$name' ($source) falló por RED, no por no " +
                        "resolver. No se baja al siguiente peldaño: se reintentará. " +
                        "Anclar en una fuente peor por un timeout es como se coló 'Kurt Cobain' " +
                        "en una radio de Radio Futura.",
                )
                return null
            }
        }

        // S023 -- BÚSQUEDA POR PALABRAS, idea de Miguel Ángel tras ver
        // fallar el caso "Led Zeppelin Immigrant song".
        //
        // Ese título no lleva " - ", así que `parseArtistFromTitle()`
        // devolvió null y los tres intentos de arriba se quedaron sin
        // nada. El artista y la canción estaban los dos delante, en el
        // propio título, y la sesión acabó anclándose en un artista
        // sorteado al azar de la biblioteca local.
        //
        // La lección: no es que el dato no estuviera, es que no
        // sabíamos leerlo. Si el título no viene partido, se parte por
        // palabras y se pregunta -- del prefijo más largo al más corto,
        // porque buscar primero lo corto encontraría 'Led' y perdería
        // 'Led Zeppelin'.
        radioRepository.identifyFromTitleWords(radioAnchorTrackTitle)?.let { identified ->
            radioRepository.resolveAnchor(identified.artist, identified.song)?.let {
                RadioDebugLogger.log(
                    appContext, storageManager,
                    "resolveAnchorWithFallbacks() -- ancla fijada partiendo el título por palabras: " +
                        "'${identified.artist}' (canción='${identified.song}', título='$radioAnchorTrackTitle')",
                )
                radioAnchorArtist = identified.artist
                return it
            }
        }

        // S022 -- un 503 o un timeout NO significan "este artista no
        // existe". Miguel Ángel puso un tema de Alaska y Dinarama, el
        // `resolveAnchor` se comió un HTTP 503, se probó el nombre del
        // canal ('Chapuzasmix') que lógicamente tampoco existe, y la
        // sesión acabó anclada en 'Quentin Gas & Los Zíngaros'
        // (flamenco/2010) porque era lo que salió de la biblioteca
        // local. Antes no arrancar Radio que anclarla en un artista
        // arbitrario: sin ancla, la siguiente ronda lo reintenta.
        if (radioRepository.lastFailureWasTransient) {
            RadioDebugLogger.log(
                appContext, storageManager,
                "fetchOneRadioTrack() -- NO se deriva ancla de disco: el fallo fue de red " +
                    "(MusicBrainz no responde), no que el artista no exista. Se reintentará.",
            )
            return null
        }
        // S024 -- aquí ya NO se deriva ancla de la biblioteca local.
        //
        // Miguel Ángel puso la 9ª de Beethoven. La cascada entera falló
        // (el nombre venía como 'Beethoven, Ludwig van', y 'American
        // Bach Soloists' existe en MusicBrainz pero sin géneros
        // propios), se sorteó la biblioteca, salió The Offspring, y la
        // Radio de una sinfonía sirvió INXS, The Smiths, Depeche Mode,
        // Def Leppard y The Cure.
        //
        // `resolveAnchorFromDisco()` recorría los artistas descargados
        // en orden ALEATORIO (`.shuffled()`) y anclaba en el primero
        // que resolviera perfil. Desde el asiento del usuario eso es
        // exactamente un artista arbitrario -- justo lo que el propio
        // código decía querer evitar dos guardas más arriba ("antes no
        // arrancar Radio que anclarla en un artista arbitrario").
        // La guarda existía, pero solo saltaba si el fallo había sido
        // de red; cuando el ancla de verdad no resolvía, se sorteaba.
        //
        // Sin ancla no hay Radio: la siguiente ronda lo reintenta.
        RadioDebugLogger.log(
            appContext, storageManager,
            "fetchOneRadioTrack() -- ancla '$anchorArtistName' sin resultado en NINGUNO de los " +
                "intentos (H05, título, palabras del título) -- NO se sortea la biblioteca: " +
                "la Radio no arranca sobre este tema",
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
                // S025 -- aprovechando que estamos con red por otra
                // búsqueda, se vacía un poco el cajón de lo que quedó
                // pendiente por no tenerla. Orden de Miguel Ángel:
                // *"llegar y decir: vale, tengo que buscar este, tengo
                // red, voy a ver en la cola de los que fallamos porque
                // no teníamos red."* No influye en lo que suena; solo
                // engorda el diccionario de la tarjeta.
                radioRepository.reconcilePending()
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
        // S020 -- el artista ya no se excluye de forma dura. El ancla
        // sí se sigue evitando (no tiene sentido que la Radio te
        // devuelva el tema que la arrancó), y el resto es preferencia
        // suave: primero los no usados esta sesión, luego los no
        // usados en sesiones recientes.
        val avoidNames = radioUsedArtists.map { it.lowercase() }.toSet() +
            radioSessionHistoryManager.recentlyUsedLower()
        // S024 -- ya no hay lista negra de artistas: la ventana de
        // `RADIO_ARTIST_WINDOW` descarta por vuelta, no para siempre.
        // Solo se excluye el ancla, que no tiene sentido devolver.
        val anchorExclusion = setOf(anchorArtistName.lowercase())

        for (portion in portionsDueThisRound()) {
            val item = when (portion) {
                RadioPortion.DISCO -> fetchFromDisco(anchor, anchorArtistName, anchorExclusion, avoidNames)
                RadioPortion.UNKNOWN -> fetchFromUnknown(anchor, anchorArtistName, anchorExclusion, avoidNames)
                RadioPortion.KNOWN -> fetchFromKnown(anchor, anchorArtistName, avoidNames)
            }
            if (item != null) {
                // ─── REGLA DURA 1, S022 ───────────────────────────────
                // *"El mismo tema no se debe repetir nunca. Repetir el
                // mismo tema es un rollazo (...) y sobre todo cuando
                // por desgracia acaba de ponerla, pum, y lo repite otra
                // vez."* Sin excepción y venga de la porción que venga.
                if (knownHitsRepository.songKey(item.artist, item.title) in radioUsedSongs ||
                    titleKey(item.title) in radioUsedTitles
                ) {
                    RadioDebugLogger.log(
                        appContext, storageManager,
                        "fetchRoundCandidate(ancla='$anchorArtistName') -- descartado por TEMA " +
                            "ya sonado esta sesión: '${item.artist}' ('${item.title}')",
                    )
                    continue
                }

                // ─── REGLA DURA 2, S022 ───────────────────────────────
                // *"No se deben repetir el mismo artista en cuatro o
                // cinco canciones (...) si ponemos un artista, 20
                // canciones, ponemos otra vez, no pasa nada, pero dos
                // veces cada diez, ya estamos incurriendo en
                // repetición."*
                //
                // S024 -- se RETIRA el veto permanente. Reincidir
                // dentro de la ventana descarta el candidato de esta
                // vuelta y nada más: el artista sigue disponible en
                // cuanto salga de las últimas diez canciones. Es lo que
                // dijo Miguel Ángel al precisar la regla: *"de cada
                // diez canciones no se puede repetir el artista;
                // cuando pasen las diez, se puede volver a poner una
                // del mismo artista"*.
                //
                // Vetar al artista para toda la sesión convertía una
                // ventana deslizante en una lista negra que solo
                // crecía, y en un pool pequeño la dejaba seca en pocas
                // vueltas. Es una de las causas de que la sesión
                // acabara viviendo del fallback que repite.
                if (isArtistTooRecent(item.artist)) {
                    RadioDebugLogger.log(
                        appContext, storageManager,
                        "fetchRoundCandidate(ancla='$anchorArtistName') -- '${item.artist}' repetiría " +
                            "dentro de las últimas $RADIO_ARTIST_WINDOW canciones: descartado esta " +
                            "vuelta, vuelve a estar disponible al salir de la ventana",
                    )
                    continue
                }

                acceptRadioItem(portion, item)
                return item
            }
        }

        return resolveFinalFallback(anchor, anchorArtistName, avoidNames)
    }

    /**
     * S020 -- orden de porciones a intentar en esta vuelta. Primero la
     * que tenga cupo pendiente según su porcentaje EFECTIVO; si
     * ninguna lo tiene (reparto ya cumplido), se intentan todas de
     * todos modos en orden fijo, para que la vuelta nunca se quede sin
     * candidato por un redondeo del cupo.
     */
    private fun portionsDueThisRound(): List<RadioPortion> {
        val alive = RadioPortion.values().filter { it !in radioPortionExhausted }
        val due = alive.filter { dueForPortion(it) }
        return due + alive.filterNot { it in due }
    }

    /**
     * S020 -- porcentaje EFECTIVO de una porción. Las porciones
     * agotadas reparten su base a partes iguales entre las vivas:
     *
     *     efectivo(i) = base(i) + (suma de bases agotadas) / (nº vivas)
     *
     * Ejemplo textual de Miguel Ángel desde 80/10/10: si Conocidos
     * (80) se agota, "un 40 por cien para cada una de las otras dos"
     * -> Disco 50, Desconocidos 50. Encadenado, cuando cae la segunda
     * la superviviente se queda con el 100%.
     *
     * Hasta S020 el porcentaje liberado simplemente se perdía:
     * `radioDiscoExhausted` apagaba el cupo de disco y nadie recogía
     * ese 10%.
     */
    private fun basePercent(portion: RadioPortion): Int {
        val disco = uiPreferencesManager.radioDiscoPercent.value
        val unknown = uiPreferencesManager.radioExplorePercent.value
        return when (portion) {
            RadioPortion.DISCO -> disco
            RadioPortion.UNKNOWN -> unknown
            RadioPortion.KNOWN -> (100 - disco - unknown).coerceAtLeast(0)
        }
    }

    private fun effectiveQuotaPercent(portion: RadioPortion): Int {
        if (portion in radioPortionExhausted) return 0
        val alive = RadioPortion.values().filter { it !in radioPortionExhausted }
        if (alive.isEmpty()) return 0
        val freed = RadioPortion.values()
            .filter { it in radioPortionExhausted }
            .sumOf { basePercent(it) }
        return basePercent(portion) + freed / alive.size
    }

    /**
     * Misma desigualdad sin división que S016 (`used * 100 <
     * (accepted + 1) * percent`), ahora contra el porcentaje efectivo
     * en vez del de Ajustes.
     */
    private fun dueForPortion(portion: RadioPortion): Boolean {
        val percent = effectiveQuotaPercent(portion)
        val used = radioPortionUsed[portion] ?: 0
        return percent > 0 && used * 100 < (radioTracksAccepted + 1) * percent
    }

    /** Marca una porción como agotada y registra el reparto resultante. */
    private fun exhaustPortion(portion: RadioPortion, anchorArtistName: String, reason: String) {
        // S024 -- DESCONOCIDOS NO SE AGOTA JAMÁS.
        //
        // Es diseño de Miguel Ángel, recogido literal en
        // `DOCS/ANNEX_H08.md`: *"Desconocidos: en la práctica no se
        // agota. Es prácticamente imposible agotar el último baremo
        // aunque no repitamos temas."* Y en la escalera de
        // degradación: *"si se agotan los artistas pq no debemos
        // repetir temas, se siguen poniendo de artistas
        // desconocidos"*. Es el peldaño FINAL, el que sostiene la
        // Radio cuando todo lo demás se ha acabado.
        //
        // El código lo marcaba agotado permanentemente en cuanto una
        // tanda de búsquedas fallaba, y eso es lo que empujaba la
        // sesión entera a `resolveFinalFallback()`, que repite a
        // propósito. De ahí "Cadillac Solitario" siete veces en un
        // solo log.
        //
        // Disco y Conocidos SÍ pueden agotarse: son conjuntos finitos
        // -- la biblioteca local y el diccionario. MusicBrainz no lo
        // es. Que una vuelta no dé resultado no significa que no vaya
        // a darlo la siguiente.
        if (portion == RadioPortion.UNKNOWN) {
            RadioDebugLogger.log(
                appContext, storageManager,
                "fetchFromUnknown(ancla='$anchorArtistName') -- sin resultado esta vuelta " +
                    "($reason). La porción DESCONOCIDOS sigue viva: no se agota nunca.",
            )
            return
        }
        if (!radioPortionExhausted.add(portion)) return
        val reparto = RadioPortion.values()
            .filter { it !in radioPortionExhausted }
            .joinToString(", ") { "$it=${effectiveQuotaPercent(it)}%" }
        RadioDebugLogger.log(
            appContext, storageManager,
            "exhaustPortion(ancla='$anchorArtistName') -- porción $portion AGOTADA ($reason). " +
                "Su porcentaje se reparte -> ${reparto.ifBlank { "no queda ninguna viva" }}",
        )
    }

    /** Contabiliza una pista aceptada: porción, totales, artista (suave) y tema (duro). */
    private fun acceptRadioItem(portion: RadioPortion, item: QueueItem) {
        radioTracksAccepted++
        radioPortionUsed[portion] = (radioPortionUsed[portion] ?: 0) + 1
        registerUsedArtist(item.artist)
        radioUsedSongs.add(knownHitsRepository.songKey(item.artist, item.title))
        titleKey(item.title).takeIf { it.isNotBlank() }?.let { radioUsedTitles.add(it) }
        // S022 -- alimenta la ventana deslizante de artistas recientes.
        item.artist?.lowercase()?.let { name ->
            radioRecentArtists.addLast(name)
            while (radioRecentArtists.size > RADIO_ARTIST_WINDOW) {
                radioRecentArtists.removeFirst()
            }
        }
    }

    /**
     * S024 -- en repertorio clásico el origen NO separa: se consulta
     * el diccionario entero con `Origin.ANY`, sin distinguir español
     * de extranjero. Ver `RadioAnchor.isClassical`. `ANY` ya existía
     * en el enum desde S020 pero ningún camino de la Radio lo usaba;
     * este es el primero.
     */
    private fun anchorOrigin(anchor: RadioAnchor) =
        when {
            anchor.isClassical ->
                com.miguelaetxio.mimoo.data.remote.KnownHitsRepository.Origin.ANY
            anchor.isSpanishOrigin ->
                com.miguelaetxio.mimoo.data.remote.KnownHitsRepository.Origin.ES
            else ->
                com.miguelaetxio.mimoo.data.remote.KnownHitsRepository.Origin.INTL
        }

    /**
     * Porción CONOCIDOS, dos peldaños (S020):
     *   1. tema catalogado que no haya sonado.
     *   2. artista con algún éxito que cumpla el ancla, con un tema
     *      NO catalogado buscado en YouTube.
     * La porción solo se agota si fallan LOS DOS.
     */
    private suspend fun fetchFromKnown(
        anchor: RadioAnchor,
        anchorArtistName: String,
        avoidNames: Set<String>,
    ): QueueItem? {
        // S022 -- con MusicBrainz caído, el diccionario es lo único que
        // sostiene la Radio, así que se suelta el género y se conservan
        // origen y década (decisión de Miguel Ángel). Con el servicio
        // sano, el género se respeta como siempre.
        val degraded = radioRepository.isServiceDegraded
        if (degraded) {
            RadioDebugLogger.log(
                appContext, storageManager,
                "fetchFromKnown(ancla='$anchorArtistName') -- MODO DEGRADADO (MusicBrainz no " +
                    "responde): se suelta el género, se mantienen origen y década",
            )
        }
        if (!radioKnownSongsExhausted) {
            val hit = knownHitsRepository.randomHit(
                anchor.genre, anchor.decadeBegin, anchorOrigin(anchor), radioUsedSongs, avoidNames,
                country = anchor.country, classical = anchor.isClassical,
                relaxGenre = degraded,
                anchorGenres = anchor.genres,
            )
            if (hit != null) {
                val item = resolveYoutubeCandidate(anchorArtistName, hit.artist, hit.song)
                if (item != null) {
                    RadioDebugLogger.log(
                        appContext, storageManager,
                        "fetchFromKnown(ancla='$anchorArtistName') -> tema conocido: '${hit.artist}' - " +
                            "'${hit.song}' (género='${hit.genre}', país='${hit.country ?: "-"}', " +
                            "ancla=género:'${anchor.genre}'/país:${anchor.country ?: "-"}/" +
                            "década:${anchor.decadeBegin})",
                    )
                    return item
                }
            } else {
                radioKnownSongsExhausted = true
                RadioDebugLogger.log(
                    appContext, storageManager,
                    "fetchFromKnown(ancla='$anchorArtistName') -- agotados los TEMAS catalogados del ancla; " +
                        "la porción sigue viva con artistas conocidos y temas no catalogados",
                )
            }
        }

        val artists = knownHitsRepository.knownArtists(
            anchor.genre, anchor.decadeBegin, anchorOrigin(anchor), avoidNames,
            country = anchor.country, classical = anchor.isClassical,
            relaxGenre = degraded,
            anchorGenres = anchor.genres,
        )
        for (artist in artists) {
            val item = resolveYoutubeCandidate(anchorArtistName, artist, songTitle = null) ?: continue
            if (knownHitsRepository.songKey(item.artist, item.title) in radioUsedSongs) continue
            if (titleKey(item.title) in radioUsedTitles) continue
            RadioDebugLogger.log(
                appContext, storageManager,
                "fetchFromKnown(ancla='$anchorArtistName') -> artista conocido, tema no catalogado: " +
                    "'$artist' ('${item.title}')",
            )
            return item
        }

        // S022 -- si MusicBrainz está caído, NO se agota la porción:
        // agotar es irreversible, y lo que ha pasado es que no se ha
        // podido preguntar, no que no quede música. Queda viva para la
        // ronda siguiente, que es la mitad de la propuesta de Miguel
        // Ángel: "esperar a que esos timeouts dejen de serlo".
        if (radioRepository.isServiceDegraded) {
            RadioDebugLogger.log(
                appContext, storageManager,
                "fetchFromKnown(ancla='$anchorArtistName') -- sin candidatos, pero MusicBrainz " +
                    "está caído: la porción NO se agota, se reintentará en la siguiente ronda",
            )
            return null
        }
        exhaustPortion(RadioPortion.KNOWN, anchorArtistName, "ni temas catalogados ni artistas conocidos libres")
        return null
    }

    /**
     * Porción DISCO, dos peldaños (S020): primero artistas de la
     * biblioteca local todavía no usados, después más temas de esos
     * mismos artistas. No degrada JAMÁS fuera del ancla -- *"si se
     * pone rock de los setenta y no hay rock de los setenta en el
     * disco, no se pone disco"*.
     */
    private suspend fun fetchFromDisco(
        anchor: RadioAnchor,
        anchorArtistName: String,
        anchorExclusion: Set<String>,
        avoidNames: Set<String>,
    ): QueueItem? {
        val item = pickDiscoCandidate(anchor, anchorExclusion, avoidNames)
        if (item != null) {
            // S022, orden explícita de Miguel Ángel tras las doce
            // Fangorias seguidas: *"agotamos el disco y pasamos la
            // cuota de disco a las otras dos (...) cuando ocurre eso,
            // no podemos llegar y seguir poniendo el mismo artista
            // solamente"*.
            //
            // Hasta aquí, quedarse sin artistas NUEVOS solo encendía
            // `radioDiscoArtistsExhausted` y la porción "seguía viva
            // sacando más temas de los ya usados" -- que es
            // literalmente cómo una sesión anclada en Fangoria acabó
            // sirviendo doce temas de Fangoria del tirón. Sin artistas
            // nuevos la porción está agotada, punto: cede su cuota a
            // diccionario y exploración, que es donde queda música que
            // el usuario no tiene.
            if (item.artist?.lowercase() in radioUsedArtists.map { it.lowercase() }) {
                exhaustPortion(
                    RadioPortion.DISCO, anchorArtistName,
                    "agotados los ARTISTAS nuevos de la biblioteca local para el ancla",
                )
                return null
            }
            RadioDebugLogger.log(
                appContext, storageManager,
                "fetchFromDisco(ancla='$anchorArtistName') -> disco: '${item.artist}' ('${item.title}')",
            )
            return item
        }
        // S023 -- la porción DISCO no puede agotarse por no haber
        // podido preguntar. La guarda equivalente existía en
        // fetchFromKnown() desde S022 y aquí faltaba.
        //
        // Visto en log real: diez consultas seguidas de
        // lookupArtistProfile() devolvieron HTTP 503 -- ZAZ, Future
        // Sound of London, Héroes del Silencio, Air, Iron Maiden,
        // Fatboy Slim, Chumbawamba, Pistones, Pixies, Transglobal
        // Underground -- y de ahí se concluyó "la biblioteca local no
        // tiene nada de género='pop' década=2000". Falso: la
        // biblioteca puede estar llena, lo que pasó es que MusicBrainz
        // no contestó. `lookupArtistProfile()` devuelve null tanto
        // cuando el artista no existe como cuando falla la red, y
        // pickDiscoCandidate() los descartaba igual.
        //
        // Agotar es IRREVERSIBLE para el resto de la vuelta y reparte
        // la cuota a las otras porciones, así que el precio de
        // equivocarse aquí es alto y el de esperar una ronda es nulo.
        if (radioDiscoLookupFailedTransiently || radioRepository.isServiceDegraded) {
            RadioDebugLogger.log(
                appContext, storageManager,
                "fetchFromDisco(ancla='$anchorArtistName') -- sin candidatos, pero MusicBrainz " +
                    "no está respondiendo: la porción NO se agota, se reintentará en la siguiente ronda",
            )
            return null
        }
        exhaustPortion(
            RadioPortion.DISCO, anchorArtistName,
            "la biblioteca local no tiene nada de género='${anchor.genre}' década=${anchor.decadeBegin}",
        )
        return null
    }

    /** Porción DESCONOCIDOS -- artistas sin éxito catalogado, vía MusicBrainz. */
    private suspend fun fetchFromUnknown(
        anchor: RadioAnchor,
        anchorArtistName: String,
        anchorExclusion: Set<String>,
        avoidNames: Set<String>,
    ): QueueItem? {
        // S024 -- antes esto pedía UN artista, intentaba UNA resolución
        // en YouTube, y si esa fallaba daba la porción entera por
        // agotada. Verificado en log real sobre la 9ª de Beethoven:
        //
        //   suggestRelatedArtist(...) -> 'Richard Strauss' (10 candidatos)
        //   resolveYoutubeCandidate(query='Richard Strauss') -- 0 de 6 pasaron
        //   exhaustPortion(...) -- porción UNKNOWN AGOTADA
        //
        // Diez candidatos encontrados, uno probado, nueve tirados. Y el
        // motivo que se escribía era falso: MusicBrainz SÍ devolvía
        // artistas nuevos; lo que fallaba era la resolución en YouTube.
        //
        // No es un problema de clásica -- le pasa igual a cualquier
        // ancla cuyo primer candidato no resuelva.
        val triedNames = mutableSetOf<String>()
        var suggestedAny = false
        repeat(UNKNOWN_CANDIDATE_ATTEMPTS) {
            val artist = radioRepository.suggestRelatedArtist(
                anchor,
                anchorExclusion + triedNames,
                avoidNames,
            ) ?: return@repeat
            suggestedAny = true
            triedNames += artist
            val item = resolveYoutubeCandidate(anchorArtistName, artist, songTitle = null)
            if (item != null &&
                knownHitsRepository.songKey(item.artist, item.title) !in radioUsedSongs &&
                titleKey(item.title) !in radioUsedTitles
            ) {
                RadioDebugLogger.log(
                    appContext, storageManager,
                    "fetchFromUnknown(ancla='$anchorArtistName') -> desconocido: '$artist'" +
                        (if (triedNames.size > 1) " (tras ${triedNames.size - 1} que no resolvieron)" else ""),
                )
                return item
            }
        }

        // S024 -- los TEMAS no catalogados NO se sirven desde aqui.
        //
        // Se anadieron a esta porcion en esta misma sesion, por
        // impulso, y produjeron el desastre que reporto Miguel Angel:
        // se buscaba en YouTube el nombre del artista a secas y se
        // aceptaba lo que viniera. Con 'Los Locos' vino
        // "Los locos, Presentacion. Carnaval de Cadiz 2026" en una
        // radio anclada en la movida madrilena.
        //
        // El diseno documentado los coloca en CONOCIDOS, no aqui
        // (DOCS/ANNEX_H08.md, tabla de porciones):
        //
        //   Conocidos     1o temas conocidos; 2o artistas conocidos
        //                 con temas NO catalogados
        //   Desconocidos  artistas sin exito catalogado, via MusicBrainz
        //
        // `fetchFromKnown()` ya implementa ese peldano 2. Aqui sobraba.
        // El motivo distingue ahora los dos casos, que exigen arreglos
        // distintos: sin sugerencias es MusicBrainz; con sugerencias que
        // no resuelven es el filtro de YouTube.
        // S025 -- UN FALLO DE RED NO ES UN CUPO AGOTADO.
        //
        // Observación de Miguel Ángel: *"tampoco es cuestión de que se
        // dependa solo de diccionario. MusicBrainz existe."* Tenía
        // razón, y esta es la razón técnica de que pareciera lo
        // contrario. En su log de S025 hay trece llamadas seguidas
        // caídas en dos minutos y medio:
        //
        //   21:46:16  lookupArtistProfile('Jesus and Mary Chain') -- SocketTimeoutException
        //   21:46:27  lookupArtistProfile('Yes')                  -- SocketTimeoutException
        //   ...
        //   21:48:52  lookupArtistProfile('Pink Floyd')           -- HTTP 503
        //
        // `findCandidates()` captura la excepción y devuelve lista
        // vacía, que aquí era indistinguible de "MusicBrainz no tiene
        // más artistas". Se agotaba la porción, y agotada lo está para
        // el RESTO DE LA SESIÓN: una avería de treinta segundos dejaba
        // la Radio dependiendo solo del diccionario durante horas.
        //
        // `RadioRepository` ya sabía distinguirlo -- expone
        // `lastFailureWasTransient`, que usa la cascada del ancla desde
        // S022 para no derivar un ancla falsa por un timeout. Aquí
        // simplemente no se consultaba. Si el último fallo fue de red,
        // la porción se queda VIVA y vuelve a intentarlo en la
        // siguiente vuelta, cuando MusicBrainz haya vuelto.
        // ---
        // S025 -- a network failure is not an exhausted quota. An empty
        // candidate list from a timeout used to be indistinguishable
        // from "no more artists", which killed the portion for the rest
        // of the session over a transient outage. The repository
        // already exposed `lastFailureWasTransient`; this simply asks.
        if (!suggestedAny &&
            (radioRepository.lastFailureWasTransient || radioRepository.isServiceDegraded)
        ) {
            RadioDebugLogger.log(
                appContext, storageManager,
                "fetchFromUnknown(ancla='$anchorArtistName') -- sin candidatos por FALLO DE RED, " +
                    "no por agotamiento: la porción sigue viva y se reintenta en la próxima vuelta",
            )
            return null
        }

        exhaustPortion(
            RadioPortion.UNKNOWN, anchorArtistName,
            if (!suggestedAny) {
                "MusicBrainz no devuelve artistas nuevos para el ancla"
            } else {
                "los $UNKNOWN_CANDIDATE_ATTEMPTS candidatos probados no resolvieron en YouTube " +
                    "(${triedNames.joinToString()})"
            },
        )
        return null
    }

    /** S016, segundo bloque -- registra en memoria de sesión Y en el historial persistente entre sesiones. */
    private fun registerUsedArtist(artist: String?) {
        artist?.let {
            radioUsedArtists.add(it)
            radioSessionHistoryManager.registerUsed(it)
        }
    }

    /**
     * S020 -- DESENLACE TERMINAL. Se llega aquí solo cuando las TRES
     * porciones han fallado en la misma vuelta. Orden textual de
     * Miguel Ángel: *"cuando se agota ya es lo que venga respetando
     * género y década y origen"* -- es el único punto en el que deja
     * de importar la procedencia del tema, pero el ancla se sigue
     * respetando entera.
     *
     * Dos últimos intentos, en orden:
     *   1. Repetir artista conocido admitiendo REPETIR TEMA -- es el
     *      "hasta que no quede más remedio" de la regla de
     *      repetición.
     *   2. Biblioteca local sin exigir artista nuevo.
     *
     * Si tampoco eso da nada, la Radio se para con su log habitual en
     * vez de rellenar con música sin relación. Miguel Ángel avisó de
     * que llegar aquí es *"prácticamente imposible"*.
     */
    private suspend fun resolveFinalFallback(
        anchor: RadioAnchor,
        anchorArtistName: String,
        avoidNames: Set<String>,
    ): QueueItem? {
        RadioDebugLogger.log(
            appContext, storageManager,
            "resolveFinalFallback(ancla='$anchorArtistName') -- las TRES porciones agotadas esta vuelta; " +
                "lo que venga respetando género='${anchor.genre}', década=${anchor.decadeBegin}, " +
                "origen_es=${anchor.isSpanishOrigin}",
        )

        // S024 -- primero se intenta SIN repetir nada. Antes se pasaba
        // `excludeSongKeys = emptySet()` directamente, o sea que se
        // repetía aunque hubiera material sin estrenar.
        val fresh = knownHitsRepository.randomHit(
            anchor.genre, anchor.decadeBegin, anchorOrigin(anchor),
            excludeSongKeys = radioUsedSongs, avoidArtists = avoidNames,
            anchorGenres = anchor.genres,
            country = anchor.country, classical = anchor.isClassical,
        )
        // S025 -- NO SE REPITE. PUNTO.
        //
        // Aquí había un segundo intento con `excludeSongKeys =
        // emptySet()` que repetía a propósito cuando no quedaba nada
        // sin estrenar, ordenando por antigüedad. Era la última pieza
        // que incumplía la regla que Miguel Ángel lleva repitiendo toda
        // la semana: *"no se pueden repetir los temas, es lo más
        // básico"*. Tenía razón, y la excusa de "es que si no, la Radio
        // se para" no vale: que se pare.
        val hit = fresh
        if (hit != null) {
            val item = resolveYoutubeCandidate(anchorArtistName, hit.artist, hit.song)
            if (item != null) {
                RadioDebugLogger.log(
                    appContext, storageManager,
                    "resolveFinalFallback(ancla='$anchorArtistName') -> " +
                        (if (fresh != null) "tema sin estrenar: " else "repitiendo el más antiguo: ") +
                        "'${hit.artist}' - '${hit.song}'",
                )
                acceptRadioItem(RadioPortion.KNOWN, item)
                return item
            }
        }

        val discoItem = pickDiscoCandidate(anchor, emptySet(), avoidNames)
        if (discoItem != null) {
            acceptRadioItem(RadioPortion.DISCO, discoItem)
            return discoItem
        }

        RadioDebugLogger.log(
            appContext, storageManager,
            "resolveFinalFallback(ancla='$anchorArtistName') -- ni siquiera repitiendo hay candidatos -- " +
                "la Radio se para en vez de rellenar con música sin relación",
        )
        return null
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

        // S020 -- solo se excluye de forma dura el propio artista
        // ancla; los ya usados esta sesión son preferencia suave
        // (`avoidLower`), porque repetir artista es siempre preferible
        // a salirse del ancla.
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

        radioDiscoLookupFailedTransiently = false
        val originMatches = candidateArtists.mapNotNull { artistName ->
            val profile = radioLibraryArtistProfileCache.getOrPut(artistName) {
                radioRepository.lookupArtistProfile(artistName)
            } ?: run {
                // S023 -- distinguir "este artista no encaja" de "no se
                // ha podido preguntar". lookupArtistProfile() devuelve
                // null en los dos casos, y tratarlos igual fue lo que
                // hizo concluir que la biblioteca no tenía nada de pop
                // de los 2000 tras diez 503 seguidos.
                if (radioRepository.lastFailureWasTransient) {
                    radioDiscoLookupFailedTransiently = true
                }
                return@mapNotNull null
            }
            // S020 -- separación dura en los dos sentidos. País
            // desconocido en MusicBrainz cuenta como NO español: con
            // ancla española queda fuera, con ancla extranjera entra.
            //
            // S024 -- salvo en clásica, donde el país no filtra en
            // absoluto: cualquier intérprete de la biblioteca vale,
            // sea de donde sea. Ver `RadioAnchor.isClassical`.
            //
            // S025 -- el origen es el PAÍS del ancla, no un booleano.
            // Misma orden y mismo cambio que en
            // RadioRepository.buildGenreQuery(): si el ancla es Led
            // Zeppelin (GB), esta porción tiene que traer británicos, y
            // no "todo lo que no sea español". Cuando MusicBrainz no da
            // país del ancla se conserva el comportamiento antiguo, que
            // es lo único honesto sin el dato.
            // ---
            // S025 -- origin is the anchor's actual country, not a
            // boolean; same change as in buildGenreQuery(). Falls back
            // to the old behaviour when the anchor has no country.
            val originOk = when {
                anchor.isClassical -> true
                anchor.country != null -> profile.country == anchor.country
                anchor.isSpanishOrigin -> profile.country == "ES"
                else -> profile.country != "ES"
            }
            if (!originOk) null else ProfiledArtist(artistName, profile)
        }
        if (originMatches.isEmpty()) return null

        // S022 -- INTERSECCIÓN, no igualdad contra el género principal.
        // Antes se comparaba cada género del candidato solo contra
        // `anchor.genre`, es decir contra la única etiqueta que había
        // sobrevivido de las siete que MusicBrainz da del ancla. Ahora
        // se cruzan los dos conjuntos completos: si Dead Can Dance
        // tiene `post-punk` entre los suyos y Joy Division también,
        // encajan -- y Pet Shop Boys, que no comparte ninguno, no.
        fun genreOk(p: ProfiledArtist) = anchor.sharesGenreWith(p.profile.genres)
        fun decadeOk(p: ProfiledArtist) = anchor.decadeBegin == null || p.profile.decadeBegin == anchor.decadeBegin

        /** S016, segundo bloque -- entre los que cumplen `condition`, prefiere los no evitados; si eso vacía la lista, ignora la preferencia. */
        fun pickPreferred(condition: (ProfiledArtist) -> Boolean): String? {
            val matching = originMatches.filter(condition)
            val preferred = matching.filter { it.artist.lowercase() !in avoidLower }
            return preferred.ifEmpty { matching }.firstOrNull()?.artist
        }

        // S020, orden explícita de Miguel Ángel: "el género no debe
        // abandonarse". Desapareció el peldaño intermedio que mantenía
        // la década y soltaba el género (`pickPreferred { decadeOk(it) }`).
        //
        // S021 -- desaparece también el simétrico, que mantenía el
        // género y soltaba la década (`pickPreferred { genreOk(it) }`).
        // Era la cuarta y última fuga de década del motor, y la que
        // hacía que una sesión anclada en los 70 sirviese cualquier
        // cosa del disco con tal de que el género cuadrase. Queda una
        // vuelta ÚNICA: género Y década del ancla, o esta porción
        // devuelve null y se declara agotada -- que es exactamente lo
        // que Miguel Ángel especificó: *"si se pone rock de los setenta
        // y no hay rock de los setenta en el disco, no se pone disco"*.
        // Nótese que `decadeOk()` ya es `true` cuando el ancla no trae
        // década, así que el caso sin ancla de década sigue cubierto.
        // ---
        // S021 -- neither the genre nor the decade is ever abandoned.
        // Single pass: the anchor's genre AND decade, or this quota
        // reports itself exhausted.
        val chosenArtist = pickPreferred { genreOk(it) && decadeOk(it) }
            ?: return null

        // S020 -- dentro del artista elegido, primero los temas que no
        // han sonado esta sesión; solo si todos han sonado se repite
        // uno ("hasta que no quede más remedio"). Este es el segundo
        // peldaño de la porción de disco: más temas de artistas ya
        // usados.
        val artistTracks = downloadedTracks.filter { it.artist.equals(chosenArtist, ignoreCase = true) }
        val unheard = artistTracks.filter {
            knownHitsRepository.songKey(it.artist, it.title) !in radioUsedSongs &&
                titleKey(it.title) !in radioUsedTitles
        }
        val track = unheard.randomOrNull() ?: artistTracks.randomOrNull() ?: return null
        return QueueItem(
            uri = track.filePath!!,
            title = track.title,
            isLocal = true,
            // S025 -- ya no cae al nombre del canal. Este candidato
            // sale de la biblioteca local y se ha filtrado justo arriba
            // por `it.artist.equals(chosenArtist)`, así que `artist`
            // nunca es nulo aquí; el respaldo era código muerto y a la
            // vez una vía más para que un nombre de canal acabara
            // figurando como artista dentro de una sesión de Radio (y
            // entrando en radioUsedArtists / la ventana de repetición).
            // ---
            // S025 -- no longer falls back to the channel name.
            artist = track.artist,
            isFromRadio = true,
            youtubeId = track.youtubeId,
            channelTitle = track.channelTitle,
            artworkUri = track.coverArtUrl ?: track.thumbnailUrl,
        )
    }

    /**
     * S020 -- comprueba que el vídeo resuelto es DE VERDAD del artista
     * pedido, mirando su título y su canal. Sin esto, el peldaño de
     * artistas desconocidos encolaba a ciegas lo primero que devolviera
     * YouTube: en el log real de Miguel Ángel entraron
     * *"Art & Language -- Conceptual Art, Mirrors and Selfies |
     * TateShots"* (un vídeo de la Tate, buscando un colectivo de arte
     * conceptual que MusicBrainz etiqueta como 'art rock') y, buscando
     * 'Гражданская оборона', un vídeo de NOTICIAS sobre la guerra.
     *
     * Importa más ahora que antes: con el reparto dinámico de S020,
     * esa porción hereda porcentaje cuando otra se agota, así que un
     * candidato basura se repite más.
     *
     * Se compara normalizado (`SearchNormalizer.normalizeArtistName`,
     * que ya quita puntuación y el "The " inicial), y basta con que el
     * nombre aparezca en el título O en el canal -- ser más estricto
     * descartaría vídeos legítimos subidos por sellos o recopilatorios
     * de canal.
     */
    private fun matchesArtist(artist: String, title: String, channelTitle: String?): Boolean {
        val needle = com.miguelaetxio.mimoo.util.SearchNormalizer.normalizeArtistName(artist)
        if (needle.isBlank()) return true
        val haystack = com.miguelaetxio.mimoo.util.SearchNormalizer.normalizeArtistName(title) + " " +
            com.miguelaetxio.mimoo.util.SearchNormalizer.normalizeArtistName(channelTitle.orEmpty())
        return haystack.contains(needle)
    }

    /**
     * ¿El ancla de esta sesión es repertorio clásico?
     *
     * Se decide una sola vez, al fijar el ancla en
     * `RadioRepository.resolveAnchor()`, contra `genre_tree.json` y no
     * contra una lista escrita a mano. Aquí solo se lee.
     */
    private fun isClassicalAnchor(): Boolean = radioAnchor?.isClassical == true

    /**
     * ¿El título delata que esto NO es una canción suelta?
     *
     * S024, idea de Miguel Ángel: *"full album o greatest hits podemos
     * descartarlos por nombre igual que interview, chap, capítulo,
     * álbum completo, grandes éxitos, película completa, entrevista"*.
     * El nombre es evidencia directa; la duración era solo un indicio,
     * y uno malo -- dejaba fuera la 9ª de Beethoven entera.
     *
     * **Se compara por PALABRA COMPLETA, no por subcadena.** El filtro
     * anterior hacía `title.contains(hint)` a pelo, y con esa lista
     * ampliada eso habría descartado a Tracy Chap-man por 'chap' y
     * cualquier cosa de Cap-itol Records por 'cap'. Con límite de
     * palabra, 'capitulo' no casa dentro de 'Chapman'.
     *
     * **Se pliegan los acentos** con el mismo normalizador que usa
     * todo lo demás, para que 'álbum completo' case con un título
     * escrito 'Album Completo' o 'ALBUM COMPLETO'.
     *
     * Deliberadamente FUERA de la lista: 'mix' a secas. "Original
     * Mix", "Club Mix" y "Extended Mix" son temas sueltos legítimos en
     * electrónica; solo se descarta 'megamix', que sí delata.
     */
    private fun looksLikeNonSong(title: String): Boolean {
        val normalized = com.miguelaetxio.mimoo.util.SearchNormalizer.normalize(title)
        if (normalized.isBlank()) return false
        // S024 -- en repertorio clásico solo se aplica la lista de "esto
        // no es música". Verificado en log real: buscando 'Richard
        // Strauss' pasaron 0 de 6 resultados PESE a haber subido el tope
        // a 45 minutos, o sea que no los tumbaba la duración sino los
        // avisos de compilación. Y es que en clásica "Best of", "Complete
        // Works" o "Full Concert" no delatan una chapuza: son la forma
        // habitual en que se publica el repertorio. Con el tope de 45
        // minutos acotando la duración, dejarlos entrar es preferible a
        // quedarse sin radio.
        val hints = if (isClassicalAnchor()) {
            NOT_MUSIC_TITLE_HINTS
        } else {
            NOT_MUSIC_TITLE_HINTS + COMPILATION_TITLE_HINTS
        }
        return hints.any { hint ->
            Regex("(^|\\s)" + Regex.escape(hint) + "($|\\s)").containsMatchIn(normalized)
        }
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
        // S024 -- el tope de 15 minutos deja fuera el repertorio
        // clásico entero. Miguel Ángel puso la 9ª de Beethoven, que
        // dura 18:34: el ancla NO pasaba su propio filtro, y por eso
        // una radio anclada en ella se quedaba seca en tres temas
        // ("0 de 6 resultados pasaron el filtro" sobre François
        // Couperin). Quince minutos delatan una compilación en pop;
        // en un movimiento sinfónico, una obertura o un concierto no
        // delatan nada.
        val maxSeconds = if (isClassicalAnchor()) {
            RADIO_MAX_CLASSICAL_SECONDS
        } else {
            RADIO_MAX_TRACK_SECONDS
        }
        val track = searchResult.tracks.firstOrNull { candidate ->
            candidate.durationSeconds in 1..maxSeconds &&
                !looksLikeNonSong(candidate.title) &&
                matchesArtist(artist, candidate.title, candidate.channelTitle)
        }
        if (track == null) {
            RadioDebugLogger.log(
                appContext, storageManager,
                "resolveYoutubeCandidate(ancla='$anchorArtistName', query='$query') -- 0 de " +
                    "${searchResult.tracks.size} resultados pasaron el filtro de duración " +
                    "(tope ${maxSeconds / 60} min)/compilación",
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
        radioRecentArtists.clear()
        radioTracksAccepted = 0
        radioPortionUsed.clear()
        radioPortionExhausted.clear()
        radioUsedSongs.clear()
        radioUsedTitles.clear()
        radioKnownSongsExhausted = false
        radioDiscoArtistsExhausted = false
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
         * S022 -- ventana, en canciones, dentro de la cual un mismo
         * artista no puede volver a sonar. Criterio literal de Miguel
         * Ángel: *"si ponemos un artista, 20 canciones, ponemos otra
         * vez, no pasa nada, pero cada diez o dos veces cada diez, si
         * ya estamos incurriendo en repetición, se hace repetitivo"*.
         * Reincidir dentro de la ventana no solo descarta el
         * candidato: agota a ese artista para el resto de la sesión.
         */
        private const val RADIO_ARTIST_WINDOW = 10

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
         * S024 -- tope propio para repertorio clásico.
         *
         * Los 15 minutos de [RADIO_MAX_TRACK_SECONDS] se pusieron para
         * cazar "Full Album" y "Greatest Hits" en música popular,
         * donde esa duración delata una compilación. En clásica no
         * delata nada: la 9ª de Beethoven que puso Miguel Ángel dura
         * 18:34, o sea que el propio tema del ancla no pasaba el
         * filtro, y su radio se quedaba seca en tres temas.
         *
         * Cuarenta y cinco minutos cubren movimientos, sinfonías
         * completas y conciertos, y siguen dejando fuera la ópera
         * íntegra y las recopilaciones de tres horas. Los avisos de
         * compilación del título siguen aplicando igual.
         */
        const val RADIO_MAX_CLASSICAL_SECONDS = 45 * 60

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
        /**
         * Recopilaciones y discos enteros. En repertorio clásico NO se
         * aplican -- ver `looksLikeNonSong()`.
         */
        val COMPILATION_TITLE_HINTS = listOf(
            // ingles
            "full album",
            "greatest hits",
            "playlist",
            "compilation",
            "best songs of",
            "best of",
            "all songs",
            "complete works",
            "full concert",
            "megamix",
            "top 10",
            "top 20",
            "top 50",
            "top 100",
            // castellano
            "album completo",
            "disco completo",
            "grandes exitos",
            "sus mejores",
            "lo mejor de",
            "los mejores",
            "recopilacion",
            "recopilatorio",
            "concierto completo",
            "exitos",
        )

        /**
         * Contenido que directamente NO es música. Se aplica siempre,
         * también en clásica: una entrevista o un capítulo de podcast no
         * pintan nada en ninguna radio.
         */
        val NOT_MUSIC_TITLE_HINTS = listOf(
            "interview",
            "entrevista",
            "chapter",
            "capitulo",
            "episode",
            "episodio",
            "podcast",
            "documentary",
            "documental",
            "audiobook",
            "audiolibro",
            "full movie",
            "pelicula completa",
        )

        /**
         * Candidatos distintos que se prueban en la porción de
         * exploración antes de darla por agotada (S024).
         *
         * Antes era uno implícito: se pedía un artista, se intentaba
         * resolverlo en YouTube, y si fallaba se cerraba la porción
         * entera aunque MusicBrainz hubiera devuelto diez candidatos.
         * Cuatro acota el coste -- son cuatro búsquedas de YouTube en el
         * peor caso, solo cuando los anteriores fallan.
         */
        const val UNKNOWN_CANDIDATE_ATTEMPTS = 4
    }
}

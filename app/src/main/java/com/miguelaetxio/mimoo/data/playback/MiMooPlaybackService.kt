package com.miguelaetxio.mimoo.data.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.miguelaetxio.mimoo.MainActivity
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MediaSessionService en primer plano para la reproducción de audio
 * -- bug real reportado por Miguel Ángel (2026-07-04): cerró otra app
 * (ajedrez) mientras MiMoo reproducía en segundo plano, y el proceso
 * entero de MiMoo murió sin dejar ningún rastro en crash_log.txt.
 * Causa raíz confirmada: PlayerManager envolvía un ExoPlayer suelto en
 * un singleton, sin absolutamente ningún servicio en primer plano
 * protegiéndolo -- Android puede (y pudo) matar ese proceso libremente
 * bajo presión de memoria, y al no ser una excepción capturada por la
 * app, no hay stacktrace que escribir.
 *
 * Envuelve el MISMO ExoPlayer de PlayerManager (también @Singleton de
 * Hilt) en una MediaSession -- no un ExoPlayer nuevo ni duplicado.
 * PlayerManager arranca este servicio (ContextCompat.
 * startForegroundService) en cuanto empieza a sonar algo
 * (onIsPlayingChanged); a partir de ahí, MediaSessionService gestiona
 * solo la notificación del sistema y la promoción a primer plano
 * mientras isPlaying sea true.
 *
 * Ningún ViewModel ni pantalla necesita cambiar nada: se sigue
 * llamando a PlayerManager.play()/playQueue()/pause() exactamente
 * igual que antes -- este servicio es puramente infraestructura de
 * ciclo de vida, no un punto de entrada nuevo a la reproducción.
 * ---
 * Foreground MediaSessionService for audio playback -- real bug
 * reported by Miguel Ángel (2026-07-04): closed another app (chess)
 * while MiMoo was playing in the background, and MiMoo's entire
 * process died leaving no trace in crash_log.txt. Confirmed root
 * cause: PlayerManager wrapped a bare ExoPlayer in a singleton, with
 * absolutely no foreground service protecting it -- Android can (and
 * did) freely kill that process under memory pressure, and since it
 * isn't an exception the app caught, there's no stacktrace to write.
 *
 * Wraps the SAME ExoPlayer from PlayerManager (also a Hilt
 * @Singleton) in a MediaSession -- not a new or duplicate ExoPlayer.
 * PlayerManager starts this service (ContextCompat.
 * startForegroundService) as soon as something starts playing
 * (onIsPlayingChanged); from there, MediaSessionService only manages
 * the system notification and the promotion to foreground while
 * isPlaying is true.
 *
 * No ViewModel or screen needs to change anything: it's still
 * PlayerManager.play()/playQueue()/pause() exactly as before -- this
 * service is purely lifecycle infrastructure, not a new entry point
 * into playback.
 */
@UnstableApi
@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class MiMooPlaybackService : MediaSessionService() {

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var storageManager: StorageManager

    @Inject
    lateinit var searchResultTrackRepository: SearchResultTrackRepository

    @Inject
    lateinit var downloadQueueManager: com.miguelaetxio.mimoo.data.download.DownloadQueueManager

    /**
     * S011 -- ámbito propio del servicio para observar la pista actual
     * y mantener el botón de favoritos de la notificación
     * sincronizado. Cancelado en onDestroy() -- nunca viewModelScope
     * (esto es un Service, no un ViewModel).
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaSession: MediaSession? = null

    /**
     * S011 -- botón de favoritos en la notificación (petición
     * explícita de Miguel Ángel: "Notificación: añadir favoritos").
     * API oficial y actual de Media3 confirmada contra la
     * documentación de Android Developers
     * (developer.android.com/media/implement/surfaces/mobile,
     * sección "Customize command buttons", verificado 2026-07-15,
     * directriz §4.5): `CommandButton` con un icono predefinido
     * (`ICON_HEART_FILLED`/`ICON_HEART_UNFILLED`, sin necesidad de
     * ningún drawable propio) + `SessionCommand` personalizado,
     * autorizado en `onConnect()` y gestionado en `onCustomCommand()`.
     * El icono se reconstruye (`updateFavoriteCommandButton()`) tanto
     * al conectar como cada vez que cambia la pista actual o se
     * alterna el favorito, para que refleje siempre el estado real de
     * la pista que suena.
     */
    private val favoriteSessionCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)

    private fun buildFavoriteCommandButton(isFavorite: Boolean, enabled: Boolean): CommandButton =
        CommandButton.Builder(
            if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName(if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos")
            .setSessionCommand(favoriteSessionCommand)
            .setEnabled(enabled)
            .build()

    /**
     * S011 -- botón de descarga en la notificación (petición explícita
     * de Miguel Ángel: "añadir un botón de descarga en las
     * notificaciones"). Ningún icono predefinido de Media3
     * (`CommandButton.ICON_*`) cubre "descargar" -- confirmado
     * revisando la lista real en la documentación oficial antes de
     * escribir esto, para no arriesgarme a citar una constante que no
     * existiera. Camino oficial para ese caso: `ICON_UNDEFINED` +
     * `setCustomIconResId()` con un drawable propio
     * (`R.drawable.ic_download`, glifo estándar de "descargar" --
     * flecha hacia una bandeja), documentado explícitamente en
     * developer.android.com/media/media3/session/control-playback,
     * sección "Icon".
     *
     * Habilitado solo cuando la pista actual todavía no está
     * descargada (`DownloadStatus.PENDING`/`ERROR`) -- deshabilitado
     * si ya está `DONE`, en cola o descargándose, o si no hay pista
     * real (Radio Online, H09).
     */
    private val downloadSessionCommand = SessionCommand(ACTION_DOWNLOAD_TRACK, Bundle.EMPTY)

    private fun buildDownloadCommandButton(enabled: Boolean): CommandButton =
        CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(com.miguelaetxio.mimoo.R.drawable.ic_download)
            .setDisplayName("Descargar")
            .setSessionCommand(downloadSessionCommand)
            .setEnabled(enabled)
            .build()

    /**
     * Reconstruye y publica AMBOS botones (favorito + descarga) con el
     * estado real de la pista actual -- `setCustomLayout()` sustituye
     * la lista entera cada vez, así que hay que pasar los dos juntos
     * o el que no se pase desaparecería de la notificación.
     */
    private fun updateCommandButtons(isFavorite: Boolean, favoriteEnabled: Boolean, downloadEnabled: Boolean) {
        mediaSession?.setCustomLayout(
            listOf(
                buildFavoriteCommandButton(isFavorite, favoriteEnabled),
                buildDownloadCommandButton(downloadEnabled),
            )
        )
    }

    /** Mismos campos que ya usa toggleFavoriteForCurrentTrack() -- ver ese comentario. */
    private suspend fun downloadCurrentTrack() {
        val current = playerManager.state.value
        val youtubeId = current.currentYoutubeId ?: return
        val title = current.currentTitle ?: return
        val track = searchResultTrackRepository.getById(youtubeId)
        downloadQueueManager.enqueue(
            youtubeId = youtubeId,
            title = title,
            artist = track?.artist ?: current.currentChannelTitle ?: title,
            album = track?.album,
            trackPosition = track?.trackPosition,
        )
    }

    /** Misma operación que PlayerBarViewModel.toggleCurrentFavorite() -- ver ese comentario para el porqué de cada campo. */
    private suspend fun toggleFavoriteForCurrentTrack() {
        val current = playerManager.state.value
        val youtubeId = current.currentYoutubeId ?: return
        val title = current.currentTitle ?: return
        val isFavoriteNow = searchResultTrackRepository.getById(youtubeId)?.isFavorite == true
        searchResultTrackRepository.setFavoriteEnsuringRow(
            youtubeId = youtubeId,
            isFavorite = !isFavoriteNow,
            title = title,
            channelTitle = current.currentChannelTitle ?: title,
            artist = current.currentArtist,
        )
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(favoriteSessionCommand)
                        .add(downloadSessionCommand)
                        .build()
                )
                .build()
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            super.onPostConnect(session, controller)
            val current = playerManager.state.value
            val youtubeId = current.currentYoutubeId
            if (youtubeId == null) {
                updateCommandButtons(isFavorite = false, favoriteEnabled = false, downloadEnabled = false)
            } else {
                serviceScope.launch {
                    val track = searchResultTrackRepository.getById(youtubeId)
                    updateCommandButtons(
                        isFavorite = track?.isFavorite == true,
                        favoriteEnabled = true,
                        downloadEnabled = track?.downloadStatus != com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.DONE &&
                            track?.downloadStatus != com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.QUEUED &&
                            track?.downloadStatus != com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.DOWNLOADING,
                    )
                }
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_FAVORITE) {
                serviceScope.launch {
                    toggleFavoriteForCurrentTrack()
                    refreshCommandButtons()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == ACTION_DOWNLOAD_TRACK) {
                serviceScope.launch {
                    downloadCurrentTrack()
                    refreshCommandButtons()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    /** Vuelve a leer el estado real de la pista actual y publica ambos botones -- usado tras cada acción para reflejar el resultado. */
    private suspend fun refreshCommandButtons() {
        val youtubeId = playerManager.state.value.currentYoutubeId
        if (youtubeId == null) {
            updateCommandButtons(isFavorite = false, favoriteEnabled = false, downloadEnabled = false)
            return
        }
        val track = searchResultTrackRepository.getById(youtubeId)
        updateCommandButtons(
            isFavorite = track?.isFavorite == true,
            favoriteEnabled = true,
            downloadEnabled = track?.downloadStatus != com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.DONE &&
                track?.downloadStatus != com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.QUEUED &&
                track?.downloadStatus != com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.DOWNLOADING,
        )
    }

    /**
     * MediaController conectado a NUESTRA PROPIA sesión -- confirmado
     * con notification_debug.txt (2026-07-05): onGetSession() no se
     * llamaba NUNCA, ni una sola vez en todo el log, por mucho que
     * sonara música durante 16 minutos. Sin ningún MediaController
     * conectándose (ni el sistema ni la propia app lo hacían, ya que
     * PlayerManager habla directo con el ExoPlayer), la maquinaria
     * interna de Media3 que fabrica la notificación real con controles
     * nunca se activaba -- de ahí el placeholder estático para
     * siempre. No se usa para controlar la reproducción (eso lo
     * sigue haciendo PlayerManager exactamente igual); su única
     * función es forzar la conexión que dispara onGetSession().
     * ---
     * MediaController connected to OUR OWN session -- confirmed with
     * notification_debug.txt (2026-07-05): onGetSession() was NEVER
     * called, not once in the whole log, no matter that music played
     * for 16 minutes. With no MediaController ever connecting (neither
     * the system nor the app itself, since PlayerManager talks
     * directly to the ExoPlayer), Media3's internal machinery that
     * builds the real notification with controls never engaged --
     * hence the permanent static placeholder. Not used to control
     * playback (PlayerManager still does that exactly as before); its
     * only job is to force the connection that triggers
     * onGetSession().
     */
    private var debugController: MediaController? = null

    override fun onCreate() {
        super.onCreate()

        // H09 -- PendingIntent que abre MainActivity al tocar el
        // cuerpo de la notificación (no los botones de control, que
        // ya funcionaban). Petición explícita de Miguel Ángel: podía
        // controlar la reproducción desde la notificación, pero
        // tocarla no abría la app. FLAG_ACTIVITY_SINGLE_TOP +
        // FLAG_ACTIVITY_CLEAR_TOP: si MainActivity ya está en la pila
        // de tareas, la trae al frente en vez de crear una instancia
        // nueva encima. FLAG_IMMUTABLE obligatorio desde Android 12
        // (API 31) para cualquier PendingIntent que la propia app no
        // vaya a mutar después -- este no se muta nunca.
        // ---
        // H09 -- PendingIntent that opens MainActivity when the body
        // of the notification is tapped (not the control buttons,
        // which already worked). Explicit request from Miguel Ángel:
        // he could control playback from the notification, but
        // tapping it didn't open the app. FLAG_ACTIVITY_SINGLE_TOP +
        // FLAG_ACTIVITY_CLEAR_TOP: if MainActivity is already on the
        // task stack, brings it to front instead of creating a new
        // instance on top. FLAG_IMMUTABLE mandatory since Android 12
        // (API 31) for any PendingIntent the app itself won't mutate
        // later -- this one never is.
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, playerManager.player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(sessionCallback)
            .build()
        NotificationDebugLogger.log(
            this, storageManager,
            "onCreate() -- MediaSession creada, player=${playerManager.player}",
        )

        // S011 -- mantiene el botón de favoritos sincronizado durante
        // toda la vida de la sesión, no solo al conectar
        // (onPostConnect): si la pista cambia sola (siguiente de la
        // cola, Radio) sin que ningún controller se reconecte, el
        // botón debe reflejar igualmente el favorito real de la
        // pista nueva.
        serviceScope.launch {
            playerManager.state.map { it.currentYoutubeId }
                .distinctUntilChanged()
                .flatMapLatest { youtubeId ->
                    if (youtubeId == null) flowOf(null) else searchResultTrackRepository.getByIdFlow(youtubeId)
                }
                .collect { track ->
                    updateCommandButtons(
                        isFavorite = track?.isFavorite == true,
                        favoriteEnabled = playerManager.state.value.currentYoutubeId != null,
                        downloadEnabled = playerManager.state.value.currentYoutubeId != null &&
                            track?.downloadStatus != com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.DONE &&
                            track?.downloadStatus != com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.QUEUED &&
                            track?.downloadStatus != com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.DOWNLOADING,
                    )
                }
        }

        // Registrado explícitamente (aunque MediaSessionService ya usa
        // este mismo provider por defecto si no se llama a este método)
        // para dejar constancia expresa de qué ID/canal se usan --
        // deben coincidir EXACTAMENTE con los que usa
        // startForegroundImmediately() más abajo, o la notificación
        // manual de arranque y la real de Media3 con controles
        // acabarían siendo dos notificaciones distintas en vez de una
        // sola que se sustituye a sí misma. Bug real reportado por
        // Miguel Ángel (2026-07-05): "tendría que aparecer en la
        // pantalla de notificaciones para poder manejarlo desde ahí" --
        // antes se veía la notificación de arranque (sin controles) y
        // nunca la sustituía la real, porque usaban ID/canal distintos.
        // ---
        // Explicitly registered (even though MediaSessionService
        // already uses this same provider by default if this method
        // isn't called) to leave explicit record of which ID/channel
        // are used -- they must match EXACTLY what
        // startForegroundImmediately() uses below, or the manual
        // startup notification and Media3's real one with controls
        // would end up being two separate notifications instead of one
        // replacing itself. Real bug reported by Miguel Ángel
        // (2026-07-05): "it should show up in the notification screen
        // so it can be controlled from there" -- previously the
        // startup notification (no controls) showed up and was never
        // replaced by the real one, because they used different
        // ID/channel.
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build())

        // Llamada MANUAL e inmediata a startForeground() -- bug real
        // reportado por Miguel Ángel (2026-07-04, crash_log.txt):
        // ForegroundServiceDidNotStartInTimeException. El mecanismo
        // automático de MediaSessionService reacciona a eventos de
        // cambio de estado del reproductor, pero en nuestro flujo la
        // transición a "reproduciendo" (que dispara
        // ContextCompat.startForegroundService() desde
        // PlayerManager.onIsPlayingChanged) ya ha ocurrido ANTES de
        // que este servicio termine de arrancar y cree la sesión --
        // para cuando Media3 podría reaccionar, no hay ningún evento
        // nuevo que lo dispare, y el sistema mata el proceso a los 5
        // segundos por no haber llamado a startForeground() a tiempo.
        // Llamarlo aquí, de forma síncrona en onCreate(), garantiza
        // que siempre se cumple el plazo; Media3 sigue actualizando
        // esta misma notificación con controles/metadatos reales en
        // cuanto procesa la sesión.
        // ---
        // MANUAL, immediate startForeground() call -- real bug
        // reported by Miguel Ángel (2026-07-04, crash_log.txt):
        // ForegroundServiceDidNotStartInTimeException.
        // MediaSessionService's automatic mechanism reacts to player
        // state-change events, but in our flow the transition to
        // "playing" (which triggers ContextCompat.
        // startForegroundService() from PlayerManager.
        // onIsPlayingChanged) has already happened BEFORE this service
        // finishes starting and creates the session -- by the time
        // Media3 could react, there's no new event left to trigger it,
        // and the system kills the process after 5 seconds for not
        // calling startForeground() in time. Calling it here,
        // synchronously in onCreate(), guarantees the deadline is
        // always met; Media3 keeps updating this same notification
        // with real controls/metadata once it processes the session.
        startForegroundImmediately(sessionActivityPendingIntent)

        // El fix real (2026-07-05, ver comentario de debugController
        // más arriba): conectamos un MediaController a nuestra propia
        // sesión para forzar la llamada a onGetSession() que nunca
        // llegaba. SessionToken(this, ComponentName(...)) apunta a
        // este mismo servicio.
        // ---
        // The real fix (2026-07-05, see debugController's comment
        // above): we connect a MediaController to our own session to
        // force the onGetSession() call that never arrived.
        // SessionToken(this, ComponentName(...)) points at this same
        // service.
        val token = SessionToken(this, ComponentName(this, MiMooPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener(
            {
                try {
                    debugController = controllerFuture.get()
                    NotificationDebugLogger.log(
                        this, storageManager,
                        "MediaController conectado correctamente a la propia sesión",
                    )
                } catch (e: Exception) {
                    NotificationDebugLogger.log(
                        this, storageManager,
                        "MediaController -- fallo al conectar: ${e.message}",
                    )
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun startForegroundImmediately(sessionActivityPendingIntent: PendingIntent) {
        // ID y canal IDÉNTICOS a los que usa DefaultMediaNotificationProvider
        // por defecto (androidx.media3.session.DefaultMediaNotificationProvider.
        // DEFAULT_NOTIFICATION_ID = 1001, DEFAULT_CHANNEL_ID =
        // "default_channel_id", confirmado contra el código fuente de
        // Media3) -- así, en cuanto Media3 publique su notificación
        // real con controles de play/pausa/siguiente/anterior, lo hace
        // sobre este MISMO id+canal y sustituye a esta notificación
        // provisional en vez de crear una segunda notificación
        // separada sin controles que se quedaba fija para siempre
        // (bug real reportado por Miguel Ángel, 2026-07-05).
        // ---
        // ID and channel IDENTICAL to what DefaultMediaNotificationProvider
        // uses by default (androidx.media3.session.
        // DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID =
        // 1001, DEFAULT_CHANNEL_ID = "default_channel_id", confirmed
        // against Media3's source code) -- this way, as soon as Media3
        // posts its real notification with play/pause/next/previous
        // controls, it does so on this SAME id+channel and replaces
        // this placeholder notification instead of creating a second,
        // separate, controls-less notification that stayed stuck
        // forever (real bug reported by Miguel Ángel, 2026-07-05).
        val channelId = DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID
        val notificationId = DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(channelId) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "Reproducción de MiMoo",
                        NotificationManager.IMPORTANCE_LOW,
                    )
                )
            }
        }

        // Icono de sistema como placeholder -- este proyecto todavía
        // no tiene un icono de app propio en res/ (ver
        // AndroidManifest.xml, sin android:icon). Cuando exista uno,
        // sustituir por el drawable real de MiMoo. Media3 sustituirá
        // esta notificación entera (icono incluido) en cuanto publique
        // la suya real, así que este placeholder solo se ve una
        // fracción de segundo.
        // ---
        // System icon as a placeholder -- this project doesn't have
        // its own app icon in res/ yet (see AndroidManifest.xml, no
        // android:icon). Once one exists, swap in MiMoo's real
        // drawable. Media3 will replace this whole notification (icon
        // included) as soon as it posts its real one, so this
        // placeholder is only visible for a split second.
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("miMoo")
            .setContentText("Reproduciendo música")
            .setOngoing(true)
            .setContentIntent(sessionActivityPendingIntent)
            .build()

        ServiceCompat.startForeground(
            this,
            notificationId,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        NotificationDebugLogger.log(
            this, storageManager,
            "onGetSession() -- llamado por package=${controllerInfo.packageName}",
        )
        return mediaSession
    }

    /**
     * Si la app se cierra desde recientes mientras NO hay nada
     * sonando, el servicio se detiene solo -- si sí hay algo sonando,
     * se deja vivo (comportamiento estándar recomendado por Media3
     * para que la música siga tras cerrar la app desde recientes).
     * ---
     * If the app is closed from recents while NOTHING is playing, the
     * service stops itself -- if something IS playing, it's left
     * alive (standard behavior recommended by Media3 so music keeps
     * playing after closing the app from recents).
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = mediaSession
        if (session == null || !session.player.playWhenReady || session.player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Libera SOLO la MediaSession, nunca `player` -- ese ExoPlayer es
     * el singleton compartido de PlayerManager (@Singleton de Hilt,
     * vive tanto como el proceso), mientras que este servicio puede
     * crearse y destruirse varias veces a lo largo de la vida de la
     * app (p.ej. cada vez que onTaskRemoved() hace stopSelf() sin
     * nada sonando, y vuelve a arrancar en la siguiente reproducción).
     * Liberar aquí el player dejaría a PlayerManager con un ExoPlayer
     * ya liberado, y la siguiente llamada a play()/playQueue() desde
     * cualquier pantalla lanzaría una excepción. MediaSession.release()
     * es independiente de player.release() precisamente para este
     * caso -- un player de vida más larga que la propia sesión.
     * ---
     * Releases ONLY the MediaSession, never `player` -- that ExoPlayer
     * is PlayerManager's shared singleton (Hilt @Singleton, lives as
     * long as the process), while this service can be created and
     * destroyed several times over the app's lifetime (e.g. every
     * time onTaskRemoved() calls stopSelf() with nothing playing, and
     * starts again on the next playback). Releasing the player here
     * would leave PlayerManager holding an already-released ExoPlayer,
     * and the next play()/playQueue() call from any screen would
     * throw. MediaSession.release() is independent of
     * player.release() for exactly this case -- a player that outlives
     * the session itself.
     */
    override fun onDestroy() {
        NotificationDebugLogger.log(this, storageManager, "onDestroy()")
        serviceScope.cancel()
        debugController?.release()
        debugController = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}

private const val ACTION_TOGGLE_FAVORITE = "com.miguelaetxio.mimoo.TOGGLE_FAVORITE"
private const val ACTION_DOWNLOAD_TRACK = "com.miguelaetxio.mimoo.DOWNLOAD_TRACK"

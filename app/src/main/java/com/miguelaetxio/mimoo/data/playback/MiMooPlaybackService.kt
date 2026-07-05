package com.miguelaetxio.mimoo.data.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.miguelaetxio.mimoo.data.download.StorageManager
import dagger.hilt.android.AndroidEntryPoint
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
@AndroidEntryPoint
class MiMooPlaybackService : MediaSessionService() {

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var storageManager: StorageManager

    private var mediaSession: MediaSession? = null

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
        mediaSession = MediaSession.Builder(this, playerManager.player).build()
        NotificationDebugLogger.log(
            this, storageManager,
            "onCreate() -- MediaSession creada, player=${playerManager.player}",
        )

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
        startForegroundImmediately()

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

    private fun startForegroundImmediately() {
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
        debugController?.release()
        debugController = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}

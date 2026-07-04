package com.miguelaetxio.mimoo.data.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val NOTIFICATION_CHANNEL_ID = "mimoo_playback"
private const val NOTIFICATION_ID = 1

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

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, playerManager.player).build()

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
    }

    private fun startForegroundImmediately() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Reproducción de MiMoo",
                        NotificationManager.IMPORTANCE_LOW,
                    )
                )
            }
        }

        // Icono de sistema como placeholder -- este proyecto todavía
        // no tiene un icono de app propio en res/ (ver
        // AndroidManifest.xml, sin android:icon). Cuando exista uno,
        // sustituir por el drawable real de MiMoo.
        // ---
        // System icon as a placeholder -- this project doesn't have
        // its own app icon in res/ yet (see AndroidManifest.xml, no
        // android:icon). Once one exists, swap in MiMoo's real
        // drawable.
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("MiMoo")
            .setContentText("Reproduciendo música")
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

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
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}

package com.miguelaetxio.mimoo.data.channels

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.ChannelSubscriptionRepository
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.remote.ExternalLinkResolver
import com.miguelaetxio.mimoo.data.remote.dto.ExternalLinkTrack
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val TAG = "MiMoo-ChannelCheck"

/**
 * Worker periódico (H11 PASO 4, S011) que comprueba si los canales
 * suscritos tienen contenido nuevo y lo encola para descarga
 * automática -- "es como un guardado de podcast", petición explícita
 * de Miguel Ángel.
 *
 * Reutiliza `ExternalLinkResolver.resolveLink()` (H08 PARTE 1) tal
 * cual -- `resolve_youtube_link()` (Python) ya trata una URL de canal
 * exactamente igual que una playlist (`extract_flat`, misma forma de
 * `entries`), así que no hace falta ninguna función Python nueva.
 *
 * **Primera comprobación de un canal (`lastCheckedAt == null`): NO se
 * descarga nada.** Decisión tomada al construir esto (asunción 4 de
 * `DOCS/ANNEX_H11.md`, a confirmar con Miguel Ángel): suscribirse a
 * un canal con años de contenido y descargarlo todo de golpe sería
 * probablemente indeseado. En la primera comprobación, los vídeos que
 * ya existen en el canal se guardan como fila `PENDING` (nunca se
 * encolan) -- sirven de "línea base" para que la SIGUIENTE
 * comprobación sepa qué es genuinamente nuevo. A partir de la segunda
 * comprobación, todo lo que no exista ya como fila en
 * `search_result_tracks` se encola de verdad
 * (`DownloadQueueManager.enqueue()`).
 *
 * Un canal que falla (URL rota, sin conexión, etc.) no bloquea la
 * comprobación del resto -- se registra y se sigue con el siguiente.
 * ---
 * Periodic worker (H11 STEP 4, S011) that checks subscribed channels
 * for new content and auto-queues it for download -- "like a saved
 * podcast", explicit request from Miguel Ángel.
 *
 * Reuses `ExternalLinkResolver.resolveLink()` (H08 PART 1) as-is --
 * `resolve_youtube_link()` (Python) already treats a channel URL
 * exactly like a playlist, so no new Python function is needed.
 *
 * **A channel's first check (`lastCheckedAt == null`): nothing gets
 * downloaded.** Its current videos are stored as `PENDING` rows
 * (never queued) as a baseline for the NEXT check to know what's
 * genuinely new. From the second check onward, anything not already
 * a row in `search_result_tracks` gets really queued.
 */
@HiltWorker
class ChannelCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val channelSubscriptionRepository: ChannelSubscriptionRepository,
    private val searchResultTrackRepository: SearchResultTrackRepository,
    private val externalLinkResolver: ExternalLinkResolver,
    private val downloadQueueManager: DownloadQueueManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val subscriptions = channelSubscriptionRepository.getAllOnce()
        if (subscriptions.isEmpty()) return Result.success()

        // Una sola lectura para todos los canales de esta pasada --
        // el dataset es personal, no hace falta una consulta por
        // canal (mismo criterio de "no resolver un problema que
        // todavía no existe" que el resto del proyecto).
        val existingIds = searchResultTrackRepository.getAllOnce().map { it.youtubeId }.toSet()

        subscriptions.forEach { subscription ->
            try {
                val channelUrl = "https://www.youtube.com/channel/${subscription.channelId}/videos"
                val result = externalLinkResolver.resolveLink(channelUrl)
                val newTracks = result.tracks.filter { it.youtubeId !in existingIds }

                if (subscription.lastCheckedAt == null) {
                    if (newTracks.isNotEmpty()) {
                        searchResultTrackRepository.cacheSearchResults(
                            newTracks.map { it.toBaselineEntity(subscription.title) }
                        )
                    }
                    Log.d(
                        TAG,
                        "doWork() -- '${subscription.title}': primera comprobación, " +
                            "${newTracks.size} vídeo(s) guardados como línea base, sin descargar",
                    )
                } else {
                    newTracks.forEach { track ->
                        downloadQueueManager.enqueue(
                            youtubeId = track.youtubeId,
                            title = track.title,
                            artist = subscription.title,
                        )
                    }
                    if (newTracks.isNotEmpty()) {
                        Log.d(
                            TAG,
                            "doWork() -- '${subscription.title}': ${newTracks.size} " +
                                "vídeo(s) nuevo(s) encolados",
                        )
                    }
                }

                channelSubscriptionRepository.updateLastCheckedAt(
                    subscription.channelId,
                    System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                Log.w(TAG, "doWork() -- '${subscription.title}' falló, sigo con el resto", e)
            }
        }

        return Result.success()
    }
}

/** Fila PENDING de línea base -- nunca se encola, solo marca "ya existía cuando se comprobó por primera vez". */
private fun ExternalLinkTrack.toBaselineEntity(channelTitle: String): SearchResultTrack =
    SearchResultTrack(
        youtubeId = youtubeId,
        title = title,
        channelTitle = channelTitle,
        durationSeconds = durationSeconds,
        thumbnailUrl = thumbnailUrl,
    )

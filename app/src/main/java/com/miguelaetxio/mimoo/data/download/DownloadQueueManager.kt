package com.miguelaetxio.mimoo.data.download

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton that enqueues and cancels audio download jobs via
 * WorkManager. Each download is a OneTimeWorkRequest tagged with
 * the youtubeId so it can be cancelled individually.
 * ---
 * Singleton que encola y cancela trabajos de descarga de audio via
 * WorkManager. Cada descarga es un OneTimeWorkRequest etiquetado con
 * el youtubeId para poder cancelarla individualmente.
 */
@Singleton
class DownloadQueueManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SearchResultTrackRepository,
) {

    private val workManager = WorkManager.getInstance(context)

    /**
     * Enqueues a download job for the given YouTube video.
     * DownloadWorker resolves the output path internally via SAF.
     * Marca la pista como QUEUED en Room antes de encolar el
     * OneTimeWorkRequest — único punto de entrada real de toda
     * descarga pedida por el usuario (SearchScreen, AlbumSearchScreen,
     * ImportLinkScreen), así que es el sitio correcto para que la
     * pantalla "Descargas" pueda verla de inmediato, sin esperar a
     * que WorkManager arranque el Worker.
     * ---
     * Encola un trabajo de descarga para el video de YouTube indicado.
     * DownloadWorker resuelve la ruta de salida internamente via SAF.
     * Marca la pista como QUEUED en Room antes de encolar el
     * OneTimeWorkRequest — único punto de entrada real de toda
     * descarga pedida por el usuario (SearchScreen, AlbumSearchScreen,
     * ImportLinkScreen), así que es el sitio correcto para que la
     * pantalla "Descargas" pueda verla de inmediato, sin esperar a
     * que WorkManager arranque el Worker.
     *
     * @param youtubeId  11-char YouTube video ID.
     * @param title      Track title (used as filename by DownloadWorker).
     * @param artist     Artist/channel name (used as dir by DownloadWorker).
     * @param album      Album name (used as sub-dir by DownloadWorker), or
     *                   null for a sencillo — DownloadDirManager falls
     *                   back to "Sencillos" in that case, never before.
     */
    suspend fun enqueue(
        youtubeId: String,
        title: String,
        artist: String,
        album: String? = null,
    ) {
        repository.markQueued(youtubeId)

        val inputData = Data.Builder()
            .putString(DownloadWorker.KEY_YOUTUBE_ID, youtubeId)
            .putString(DownloadWorker.KEY_TITLE, title)
            .putString(DownloadWorker.KEY_ARTIST, artist)
            .putString(DownloadWorker.KEY_ALBUM, album)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .addTag(youtubeId)
            .build()

        workManager.enqueue(request)
    }

    /**
     * Cancels any in-progress or pending download for the given video.
     * ---
     * Cancela cualquier descarga en curso o pendiente para el video dado.
     *
     * @param youtubeId  Tag used when the work request was enqueued.
     */
    fun cancelDownload(youtubeId: String) {
        workManager.cancelAllWorkByTag(youtubeId)
    }

    /**
     * Reencola cualquier pista QUEUED/DOWNLOADING en Room que no tenga
     * un WorkRequest real vivo en WorkManager (ENQUEUED o RUNNING).
     * Bug real reportado por Miguel Ángel (2026-07-03, verificación de
     * Moon Safari): sin esto, una fila que se queda huérfana -- el
     * proceso murió a mitad de descarga, o el sistema canceló el
     * WorkRequest -- se queda visible en "Descargas" para siempre, sin
     * avanzar ni pasar nunca a "Con error", y sin ningún botón de
     * reintentar (esa fila no está en la sección ERROR). No hace falta
     * tocar trackPosition/album al reencolar: enqueue() usa los
     * valores ya guardados en la fila, así que el orden de disco
     * (fix S003) se conserva igual que en un reintento manual.
     *
     * Llamar una sola vez al arrancar la app (ver MiMooApp.onCreate()).
     * ---
     * Re-enqueues any QUEUED/DOWNLOADING track in Room that has no
     * live WorkRequest (ENQUEUED or RUNNING) in WorkManager. Real bug
     * reported by Miguel Ángel (2026-07-03, Moon Safari verification):
     * without this, an orphaned row -- process died mid-download, or
     * the system cancelled the WorkRequest -- stays visible in
     * "Descargas" forever, never progressing and never moving to "Con
     * error" either (that row isn't in the ERROR section, so there's
     * no retry button for it). No need to touch trackPosition/album on
     * re-enqueue: enqueue() reads the values already stored in the
     * row, so disk order (S003 fix) is preserved just like on a manual
     * retry.
     *
     * Call once at app startup (see MiMooApp.onCreate()).
     */
    suspend fun reconcileOrphanedDownloads() {
        val active = repository.getActiveDownloadsOnce()
        if (active.isEmpty()) return

        for (track in active) {
            val hasLiveWork = withContext(Dispatchers.IO) {
                workManager.getWorkInfosByTag(track.youtubeId).get()
            }.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }

            if (!hasLiveWork) {
                enqueue(
                    youtubeId = track.youtubeId,
                    title = track.title,
                    artist = track.artist ?: track.channelTitle,
                    album = track.album,
                )
            }
        }
    }
}


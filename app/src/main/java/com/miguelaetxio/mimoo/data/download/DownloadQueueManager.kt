package com.miguelaetxio.mimoo.data.download

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
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
) {

    private val workManager = WorkManager.getInstance(context)

    /**
     * Enqueues a download job for the given YouTube video.
     * DownloadWorker resolves the output path internally via SAF.
     * ---
     * Encola un trabajo de descarga para el video de YouTube indicado.
     * DownloadWorker resuelve la ruta de salida internamente via SAF.
     *
     * @param youtubeId  11-char YouTube video ID.
     * @param title      Track title (used as filename by DownloadWorker).
     * @param artist     Artist/channel name (used as dir by DownloadWorker).
     */
    fun enqueue(youtubeId: String, title: String, artist: String) {
        val inputData = Data.Builder()
            .putString(DownloadWorker.KEY_YOUTUBE_ID, youtubeId)
            .putString(DownloadWorker.KEY_TITLE, title)
            .putString(DownloadWorker.KEY_ARTIST, artist)
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
}


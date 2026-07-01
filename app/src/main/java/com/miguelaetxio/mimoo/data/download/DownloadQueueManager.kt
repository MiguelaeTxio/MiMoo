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
 * Singleton que encola y cancela trabajos de descarga de audio vía
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
     * ---
     * Encola un trabajo de descarga para el vídeo de YouTube indicado.
     *
     * @param youtubeId   11-char YouTube video ID.
     * @param outputPath  Absolute path where the .opus file will be written.
     */
    fun enqueue(youtubeId: String, outputPath: String) {
        val inputData = Data.Builder()
            .putString(DownloadWorker.KEY_YOUTUBE_ID, youtubeId)
            .putString(DownloadWorker.KEY_OUTPUT_PATH, outputPath)
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
     * Cancela cualquier descarga en curso o pendiente para el vídeo dado.
     *
     * @param youtubeId  Tag used when the work request was enqueued.
     */
    fun cancelDownload(youtubeId: String) {
        workManager.cancelAllWorkByTag(youtubeId)
    }
}

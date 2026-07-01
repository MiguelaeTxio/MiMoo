package com.miguelaetxio.mimoo.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that downloads a YouTube track as an Opus file
 * using yt-dlp (embedded via Chaquopy). Runs in background, survives
 * app close. Injected by Hilt via HiltWorkerFactory.
 * ---
 * Worker de WorkManager que descarga una pista de YouTube como archivo
 * Opus usando yt-dlp (embebido vía Chaquopy). Se ejecuta en background,
 * sobrevive al cierre de la app. Inyectado por Hilt vía HiltWorkerFactory.
 *
 * Input Data keys:
 *   KEY_YOUTUBE_ID  — 11-char YouTube video ID
 *   KEY_OUTPUT_PATH — absolute path for the output .opus file
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: SearchResultTrackRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_YOUTUBE_ID = "youtube_id"
        const val KEY_OUTPUT_PATH = "output_path"
    }

    override suspend fun doWork(): Result {
        val youtubeId = inputData.getString(KEY_YOUTUBE_ID)
            ?: return Result.failure()
        val outputPath = inputData.getString(KEY_OUTPUT_PATH)
            ?: return Result.failure()

        val youtubeUrl = "https://youtu.be/$youtubeId"

        // Mark as DOWNLOADING so the UI can show a spinner.
        // Marcamos como DOWNLOADING para que la UI muestre un spinner.
        repository.updateDownloadStatus(youtubeId, DownloadStatus.DOWNLOADING)

        return try {
            runYtDlp(youtubeUrl, outputPath)
            // Download succeeded: persist file path and mark as DONE.
            // Descarga correcta: persistir ruta del archivo y marcar DONE.
            repository.updateDownloadResult(
                youtubeId,
                filePath = outputPath,
                status = DownloadStatus.DONE,
            )
            Result.success()
        } catch (e: Exception) {
            repository.updateDownloadStatus(youtubeId, DownloadStatus.ERROR)
            Result.failure()
        }
    }

    /**
     * Invokes yt-dlp via Chaquopy to download and convert to Opus.
     * Uses the same Python instance already started in MiMooApp.onCreate().
     * ---
     * Invoca yt-dlp vía Chaquopy para descargar y convertir a Opus.
     * Reutiliza la misma instancia Python arrancada en MiMooApp.onCreate().
     *
     * yt-dlp options used:
     *   format     : bestaudio  — best audio-only stream
     *   outtmpl    : outputPath — destination file template
     *   postprocessors: FFmpegExtractAudio to opus
     *   quiet      : True       — suppress yt-dlp console output
     */
    private fun runYtDlp(youtubeUrl: String, outputPath: String) {
        val py = com.chaquo.python.Python.getInstance()
        val downloader = py.getModule("downloader")
        val result = downloader.callAttr(
            "download_audio",
            youtubeUrl,
            outputPath,
        )
        // downloader.download_audio raises on error; if it returns
        // a non-None falsy value we treat that as failure too.
        // download_audio lanza excepción en caso de error; si devuelve
        // un valor falsy no nulo también lo tratamos como fallo.
        if (result != null && result.toJava(Boolean::class.java) == false) {
            throw RuntimeException("yt-dlp returned failure for $youtubeUrl")
        }
    }
}

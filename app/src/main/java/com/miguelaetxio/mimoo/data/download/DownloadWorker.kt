package com.miguelaetxio.mimoo.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * WorkManager worker that downloads a YouTube track as an Opus file.
 *
 * Two-step pattern (avoids any storage permission on all API levels):
 *   1. yt-dlp downloads to a temp file in app cache dir (no permission).
 *   2. Kotlin copies the temp file to the SAF DocumentFile destination
 *      via ContentResolver.openOutputStream(), then deletes the temp.
 *
 * ---
 * Worker de WorkManager que descarga una pista de YouTube como Opus.
 *
 * Patron de dos pasos (evita cualquier permiso de almacenamiento):
 *   1. yt-dlp descarga a un archivo temporal en el cache dir de la app.
 *   2. Kotlin copia el temporal al DocumentFile SAF de destino via
 *      ContentResolver.openOutputStream() y borra el temporal.
 *
 * Input Data keys:
 *   KEY_YOUTUBE_ID  — 11-char YouTube video ID
 *   KEY_TITLE       — sanitized track title (used as filename)
 *   KEY_ARTIST      — channel title (used as artist dir name)
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: SearchResultTrackRepository,
    private val storageManager: StorageManager,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_YOUTUBE_ID = "youtube_id"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
    }

    override suspend fun doWork(): Result {
        val youtubeId = inputData.getString(KEY_YOUTUBE_ID)
            ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE)
            ?: return Result.failure()
        val artist = inputData.getString(KEY_ARTIST)
            ?: return Result.failure()

        val youtubeUrl = "https://youtu.be/$youtubeId"

        val rootUri = storageManager.getRootUri()
            ?: return Result.failure()

        val trackDir = DownloadDirManager.getOrCreateTrackDir(
            context = applicationContext,
            rootUri = rootUri,
            artist = artist,
            album = null,
        ) ?: return Result.failure()

        val safeTitle = DownloadDirManager.sanitize(title)
        val outputFileName = "$safeTitle.opus"

        // Temp file in app cache: no permission needed.
        // Archivo temporal en cache de la app: sin permiso necesario.
        val tempFile = File(applicationContext.cacheDir, "$youtubeId.opus")

        repository.updateDownloadStatus(youtubeId, DownloadStatus.DOWNLOADING)

        return try {
            // Step 1 — download to temp via yt-dlp + Chaquopy.
            // Paso 1 — descargar al temporal via yt-dlp + Chaquopy.
            runYtDlp(youtubeUrl, tempFile.absolutePath.removeSuffix(".opus"))

            // Locate the actual output file: yt-dlp may append .opus itself.
            // Localizar el archivo real: yt-dlp puede añadir .opus el mismo.
            val actualTemp = if (tempFile.exists()) tempFile
                             else File(applicationContext.cacheDir, "$youtubeId.opus.opus")

            // Step 2 — copy to SAF destination via ContentResolver.
            // Paso 2 — copiar al destino SAF via ContentResolver.
            val outputDoc = trackDir.createFile("audio/opus", outputFileName)
                ?: throw RuntimeException("No se pudo crear el archivo SAF: $outputFileName")

            applicationContext.contentResolver
                .openOutputStream(outputDoc.uri)
                ?.use { out -> actualTemp.inputStream().use { it.copyTo(out) } }
                ?: throw RuntimeException("No se pudo abrir el OutputStream SAF")

            actualTemp.delete()

            repository.updateDownloadResult(
                youtubeId,
                filePath = outputDoc.uri.toString(),
                status = DownloadStatus.DONE,
            )
            Result.success()
        } catch (e: Exception) {
            tempFile.delete()
            File(applicationContext.cacheDir, "$youtubeId.opus.opus").delete()
            // Write stacktrace to app private external dir (no permission needed).
            // Escribe stacktrace en directorio externo privado de la app.
            try {
                val debugDir = applicationContext.getExternalFilesDir(null)
                    ?: applicationContext.filesDir
                File(debugDir, "debug_error.txt").writeText(
                    buildString {
                        appendLine("youtubeId : $youtubeId")
                        appendLine("title     : $title")
                        appendLine("artist    : $artist")
                        appendLine("exception : ${e::class.java.name}")
                        appendLine("message   : ${e.message}")
                        appendLine("--- stacktrace ---")
                        appendLine(e.stackTraceToString())
                    }
                )
            } catch (_: Exception) { }
            repository.updateDownloadStatus(youtubeId, DownloadStatus.ERROR)
            Result.failure()
        }
    }

    /**
     * Invokes downloader.py via Chaquopy. outputBasePath must be a
     * writable filesystem path WITHOUT the .opus extension (yt-dlp
     * appends the container extension automatically).
     * ---
     * Invoca downloader.py via Chaquopy. outputBasePath debe ser una
     * ruta de sistema de archivos SIN la extension .opus (yt-dlp
     * añade la extension del contenedor automaticamente).
     */
    private fun runYtDlp(youtubeUrl: String, outputBasePath: String) {
        val py = com.chaquo.python.Python.getInstance()
        val downloader = py.getModule("downloader")
        downloader.callAttr("download_audio", youtubeUrl, outputBasePath)
    }
}


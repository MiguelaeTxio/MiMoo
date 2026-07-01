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
 * Two-step pattern (no storage permission needed on any API level):
 *   1. yt-dlp downloads to a temp file in app cache dir using the
 *      bundled ffmpeg binary from nativeLibraryDir.
 *   2. Kotlin copies the temp file to the SAF DocumentFile destination
 *      via ContentResolver.openOutputStream(), then deletes the temp.
 *
 * ---
 * Worker de WorkManager que descarga una pista de YouTube como Opus.
 *
 * Patron de dos pasos (sin permiso de almacenamiento en ninguna API):
 *   1. yt-dlp descarga al cache dir de la app usando el binario ffmpeg
 *      incluido en el APK (nativeLibraryDir/libffmpeg_bin.so).
 *   2. Kotlin copia el temporal al DocumentFile SAF de destino via
 *      ContentResolver.openOutputStream() y borra el temporal.
 *
 * Input Data keys:
 *   KEY_YOUTUBE_ID  — 11-char YouTube video ID
 *   KEY_TITLE       — track title (used as filename)
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

        // Temp file in app cache dir: no permission needed.
        // Archivo temporal en cache dir de la app: sin permiso necesario.
        val tempBase = File(applicationContext.cacheDir, youtubeId)

        // ffmpeg binary is extracted by Android to nativeLibraryDir
        // under the name libffmpeg_bin.so with execute permission.
        // Android extrae el binario ffmpeg a nativeLibraryDir con el
        // nombre libffmpeg_bin.so y permisos de ejecucion.
        val ffmpegPath = File(
            applicationContext.applicationInfo.nativeLibraryDir,
            "libffmpeg_bin.so",
        ).absolutePath

        repository.updateDownloadStatus(youtubeId, DownloadStatus.DOWNLOADING)

        return try {
            // Step 1 — download to temp via yt-dlp + Chaquopy + ffmpeg.
            // Paso 1 — descargar al temporal via yt-dlp + Chaquopy + ffmpeg.
            runYtDlp(youtubeUrl, tempBase.absolutePath, ffmpegPath)

            // yt-dlp appends .opus to the output template.
            // yt-dlp añade .opus a la plantilla de salida.
            val actualTemp = File("${tempBase.absolutePath}.opus")

            if (!actualTemp.exists()) {
                throw RuntimeException(
                    "El archivo temporal no existe tras la descarga: ${actualTemp.absolutePath}"
                )
            }

            // Step 2 — copy to SAF destination via ContentResolver.
            // Paso 2 — copiar al destino SAF via ContentResolver.
            val outputDoc = trackDir.createFile("audio/opus", outputFileName)
                ?: throw RuntimeException(
                    "No se pudo crear el archivo SAF: $outputFileName"
                )

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
            // Clean up temp files.
            // Limpiar archivos temporales.
            File("${tempBase.absolutePath}.opus").delete()
            tempBase.delete()

            // Write stacktrace via SAF to the root dir where we have
            // write permission (chosen by the user via OpenDocumentTree).
            // Falls back to app filesDir if SAF root is unavailable.
            // ---
            // Escribe el stacktrace via SAF en la carpeta raiz donde
            // tenemos permiso de escritura (elegida por el usuario).
            // Recurre a filesDir si el Uri SAF no esta disponible.
            try {
                val rootUri = storageManager.getRootUri()
                if (rootUri != null) {
                    val rootDoc = androidx.documentfile.provider.DocumentFile
                        .fromTreeUri(applicationContext, rootUri)
                    val debugFile = rootDoc
                        ?.findFile("debug_error.txt")
                        ?: rootDoc?.createFile("text/plain", "debug_error.txt")
                    debugFile?.let { doc ->
                        applicationContext.contentResolver
                            .openOutputStream(doc.uri, "wt")
                            ?.use { out ->
                                out.write(
                                    buildString {
                                        appendLine("youtubeId   : $youtubeId")
                                        appendLine("title       : $title")
                                        appendLine("artist      : $artist")
                                        appendLine("ffmpegPath  : $ffmpegPath")
                                        appendLine("ffmpegExists: ${File(ffmpegPath).exists()}")
                                        appendLine("exception   : ${e::class.java.name}")
                                        appendLine("message     : ${e.message}")
                                        appendLine("--- stacktrace ---")
                                        appendLine(e.stackTraceToString())
                                    }.toByteArray()
                                )
                            }
                    }
                } else {
                    // No SAF root yet: write to app internal filesDir.
                    // Sin Uri SAF: escribir en filesDir interno de la app.
                    File(applicationContext.filesDir, "debug_error.txt").writeText(
                        buildString {
                            appendLine("youtubeId   : $youtubeId")
                            appendLine("ffmpegPath  : $ffmpegPath")
                            appendLine("ffmpegExists: ${File(ffmpegPath).exists()}")
                            appendLine("exception   : ${e::class.java.name}")
                            appendLine("message     : ${e.message}")
                            appendLine("--- stacktrace ---")
                            appendLine(e.stackTraceToString())
                        }
                    )
                }
            } catch (_: Exception) { }

            repository.updateDownloadStatus(youtubeId, DownloadStatus.ERROR)
            Result.failure()
        }
    }

    /**
     * Invokes downloader.py via Chaquopy passing the ffmpeg binary path.
     * outputBasePath must be WITHOUT the .opus extension.
     * ---
     * Invoca downloader.py via Chaquopy pasando la ruta del binario ffmpeg.
     * outputBasePath debe ser SIN la extension .opus.
     */
    private fun runYtDlp(
        youtubeUrl: String,
        outputBasePath: String,
        ffmpegPath: String,
    ) {
        val py = com.chaquo.python.Python.getInstance()
        val downloader = py.getModule("downloader")
        downloader.callAttr(
            "download_audio",
            youtubeUrl,
            outputBasePath,
            ffmpegPath,
        )
    }
}

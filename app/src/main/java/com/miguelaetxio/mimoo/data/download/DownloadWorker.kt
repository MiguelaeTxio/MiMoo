package com.miguelaetxio.mimoo.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Callback interface passed as a Chaquopy proxy into downloader.py's
 * download_audio(progress_listener=...) — yt-dlp's progress_hooks call
 * onProgress() synchronously from the same (blocking) thread running
 * runYtDlp(), many times per second while the raw download is active.
 * ---
 * Interfaz de callback pasada como proxy de Chaquopy a
 * download_audio(progress_listener=...) de downloader.py — los
 * progress_hooks de yt-dlp llaman a onProgress() de forma sincrona
 * desde el mismo hilo (bloqueante) que ejecuta runYtDlp(), muchas
 * veces por segundo mientras la descarga en crudo esta activa.
 */
fun interface DownloadProgressListener {
    fun onProgress(percent: Int)
}

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
        const val KEY_ALBUM = "album"

        /**
         * Número máximo de intentos antes de rendirse y marcar ERROR
         * sin dejar rastro en disco (petición de Miguel Ángel,
         * 2026-07-04). runAttemptCount empieza en 0, así que se
         * reintenta mientras sea menor que MAX_DOWNLOAD_ATTEMPTS - 1.
         * ---
         * Maximum number of attempts before giving up and marking
         * ERROR with no trace left on disk (requested by Miguel Ángel,
         * 2026-07-04). runAttemptCount starts at 0, so it retries
         * while it's less than MAX_DOWNLOAD_ATTEMPTS - 1.
         */
        const val MAX_DOWNLOAD_ATTEMPTS = 3
    }

    override suspend fun doWork(): Result {
        val youtubeId = inputData.getString(KEY_YOUTUBE_ID)
            ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE)
            ?: return Result.failure()
        val artist = inputData.getString(KEY_ARTIST)
            ?: return Result.failure()
        // Ausente == sencillo (comportamiento normal) -- a diferencia
        // de youtubeId/title/artist, la ausencia de album NO es un
        // fallo, así que no aborta con Result.failure().
        val album = inputData.getString(KEY_ALBUM)

        val youtubeUrl = "https://youtu.be/$youtubeId"

        val rootUri = storageManager.getRootUri()
            ?: return Result.failure()

        // Álbum real, no siempre null -- antes de este fix, TODO lo
        // descargado (incluso álbumes completos con su campo album
        // correcto en Room) acababa físicamente en
        // {artista}/Sencillos/, sin importar lo que mostrara
        // Biblioteca. Reportado por Miguel Ángel (2026-07-03): "las
        // carpetas son un galimatías".
        val trackDir = DownloadDirManager.getOrCreateTrackDir(
            context = applicationContext,
            rootUri = rootUri,
            artist = artist,
            album = album,
        ) ?: return Result.failure()

        val safeTitle = DownloadDirManager.sanitize(title)
        val outputFileName = "$safeTitle.opus"

        // Idempotencia real (2026-07-04, petición de Miguel Ángel tras
        // el fix de reconcileOrphanedDownloads()): esta pista puede
        // reencolarse por una fila QUEUED/DOWNLOADING huérfana cuyo
        // archivo YA se descargó con éxito la vez anterior -- el
        // proceso murió justo después de la copia SAF pero antes de
        // updateDownloadResult(DONE). Sin esta comprobación,
        // trackDir.createFile() de más abajo crearía un duplicado
        // físico byte a byte (con sufijo " (1)" añadido por el
        // proveedor SAF) cada vez que esa fila huérfana se reencola --
        // exactamente el escenario de "zombis" que preocupa a Miguel
        // Ángel a escala de una tarjeta de 256 GB. Si el archivo ya
        // existe, no se descarga nada: se marca DONE con ese archivo
        // tal cual y se termina aquí.
        // ---
        // Real idempotency (2026-07-04, requested by Miguel Ángel
        // after the reconcileOrphanedDownloads() fix): this track can
        // get re-enqueued by an orphaned QUEUED/DOWNLOADING row whose
        // file was ALREADY downloaded successfully last time -- the
        // process died right after the SAF copy but before
        // updateDownloadResult(DONE). Without this check,
        // trackDir.createFile() further down would create a byte-for-
        // byte physical duplicate (with a " (1)" suffix added by the
        // SAF provider) every time that orphaned row gets re-enqueued
        // -- exactly the "zombie" scenario Miguel Ángel is worried
        // about at the scale of a 256 GB card. If the file already
        // exists, nothing is downloaded: it's marked DONE with that
        // existing file as-is and we return here.
        trackDir.findFile(outputFileName)?.let { existing ->
            repository.updateDownloadResult(
                youtubeId,
                filePath = existing.uri.toString(),
                status = DownloadStatus.DONE,
            )
            return Result.success()
        }

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

        // yt-dlp looks for an executable named "ffmpeg" in ffmpeg_location.
        // The native lib is named libffmpeg_bin.so, so we create a symlink
        // named "ffmpeg" in cacheDir pointing to the real binary.
        // ---
        // yt-dlp busca un ejecutable llamado "ffmpeg" en ffmpeg_location.
        // La lib nativa se llama libffmpeg_bin.so, por lo que creamos un
        // symlink llamado "ffmpeg" en cacheDir apuntando al binario real.
        val ffmpegDir = prepareFfmpeg(ffmpegPath)
            ?: run {
                repository.updateDownloadStatus(youtubeId, DownloadStatus.ERROR)
                return Result.failure()
            }

        repository.updateDownloadStatus(youtubeId, DownloadStatus.DOWNLOADING)

        // runBlocking es aceptable aqui: onProgress() se llama de forma
        // sincrona desde dentro de la llamada bloqueante a Chaquopy
        // (runYtDlp, mas abajo), que ya bloquea este mismo hilo — no
        // añade una espera nueva, solo una escritura corta a Room en
        // el mismo hilo que ya estaba ocupado.
        // ---
        // runBlocking is fine here: onProgress() is called synchronously
        // from inside the blocking Chaquopy call (runYtDlp, below),
        // which already blocks this same thread — it doesn't add a new
        // wait, just a short Room write on the thread that was already
        // busy.
        val progressListener = DownloadProgressListener { percent ->
            runBlocking { repository.updateDownloadProgress(youtubeId, percent) }
        }

        // Hoisted fuera del try para que el catch pueda borrarlo si se
        // llegó a crear pero la copia falló después -- sin esto, un
        // fallo a mitad de copyTo() dejaba un archivo SAF vacío o a
        // medias huérfano en disco para siempre (vector real de
        // "espurios" reportado por Miguel Ángel, 2026-07-04).
        // ---
        // Hoisted out of the try so the catch block can delete it if
        // it was created but the copy failed afterwards -- without
        // this, a failure partway through copyTo() left an empty or
        // partial SAF file orphaned on disk forever (real "espurios"
        // vector reported by Miguel Ángel, 2026-07-04).
        var outputDoc: androidx.documentfile.provider.DocumentFile? = null

        return try {
            // Step 1 — download to temp via yt-dlp + Chaquopy + ffmpeg.
            // Paso 1 — descargar al temporal via yt-dlp + Chaquopy + ffmpeg.
            runYtDlp(youtubeUrl, tempBase.absolutePath, ffmpegDir, progressListener)

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
            outputDoc = trackDir.createFile("audio/opus", outputFileName)
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

            // Borra el archivo SAF de destino si se llegó a crear pero
            // la copia falló después -- ver comentario de outputDoc
            // más arriba. Nunca deja un archivo vacío/a medias en la
            // carpeta del álbum.
            // ---
            // Deletes the SAF destination file if it was created but
            // the copy failed afterwards -- see the outputDoc comment
            // above. Never leaves an empty/partial file in the album
            // folder.
            outputDoc?.delete()

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
                                        appendLine("ffmpegDir   : $ffmpegDir")
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

            // Reintento automático hasta MAX_DOWNLOAD_ATTEMPTS veces
            // (petición explícita de Miguel Ángel, 2026-07-04: "si en
            // tres veces no hemos conseguido descargar una canción, no
            // dejar rastros en disco... y avisado el usuario"). Con
            // Result.retry(), WorkManager reprograma este mismo
            // WorkRequest con backoff, incrementando runAttemptCount;
            // solo al agotar los intentos se marca ERROR de verdad (ya
            // sin ningún archivo parcial en disco, por el
            // outputDoc?.delete() de más arriba).
            // ---
            // Automatic retry up to MAX_DOWNLOAD_ATTEMPTS times
            // (explicit request from Miguel Ángel, 2026-07-04: "if
            // three times we haven't managed to download a song, leave
            // no trace on disk... and the user is notified").  With
            // Result.retry(), WorkManager reschedules this same
            // WorkRequest with backoff, incrementing runAttemptCount;
            // only once attempts are exhausted is it marked ERROR for
            // real (with no partial file left on disk anymore, thanks
            // to the outputDoc?.delete() above).
            if (runAttemptCount < MAX_DOWNLOAD_ATTEMPTS - 1) {
                repository.updateDownloadStatus(youtubeId, DownloadStatus.QUEUED)
                Result.retry()
            } else {
                repository.updateDownloadStatus(youtubeId, DownloadStatus.ERROR)
                Result.failure()
            }
        }
    }

    /**
     * Creates a symlink named "ffmpeg" in cacheDir pointing to the
     * real binary (libffmpeg_bin.so). yt-dlp requires the executable
     * to be named exactly "ffmpeg" in the ffmpeg_location directory.
     * Returns the directory path on success, null on failure.
     * ---
     * Crea un symlink llamado "ffmpeg" en cacheDir apuntando al
     * binario real (libffmpeg_bin.so). yt-dlp requiere que el
     * ejecutable se llame exactamente "ffmpeg" en ffmpeg_location.
     * Devuelve la ruta del directorio en exito, null en fallo.
     */
    private fun prepareFfmpeg(ffmpegSoPath: String): String? {
        return try {
            val ffmpegLink = File(applicationContext.cacheDir, "ffmpeg")
            if (!ffmpegLink.exists()) {
                Runtime.getRuntime().exec(
                    arrayOf("ln", "-sf", ffmpegSoPath, ffmpegLink.absolutePath)
                ).waitFor()
            }
            // Ensure execute permission on the symlink target.
            // Asegurar permiso de ejecucion en el destino del symlink.
            Runtime.getRuntime().exec(
                arrayOf("chmod", "755", ffmpegSoPath)
            ).waitFor()
            applicationContext.cacheDir.absolutePath
        } catch (e: Exception) {
            null
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
        progressListener: DownloadProgressListener,
    ) {
        val py = com.chaquo.python.Python.getInstance()
        val downloader = py.getModule("downloader")
        downloader.callAttr(
            "download_audio",
            youtubeUrl,
            outputBasePath,
            ffmpegPath,
            progressListener,
        )
    }
}


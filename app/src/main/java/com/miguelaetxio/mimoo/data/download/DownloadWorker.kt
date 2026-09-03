package com.miguelaetxio.mimoo.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
    private val autoSyncPusher: AutoSyncPusher,
    private val cookiesManager: CookiesManager,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_YOUTUBE_ID = "youtube_id"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ALBUM = "album"

        /**
         * Posición dentro del álbum (0-indexed), opcional -- si está
         * presente, se antepone al nombre del archivo como "NN - " para
         * que el orden real de disco sobreviva a una reconciliación
         * futura (ver LibraryReconciler). Petición explícita de Miguel
         * Ángel (2026-07-05): "cuando hacemos la reconciliación, el
         * orden de las canciones cambia... en discos conceptuales como
         * The Wall, eso rompe el concepto de álbum por completo". Sin
         * esto en el propio nombre de archivo, no había forma de que
         * un rescan recuperara el orden -- el archivo en disco no
         * llevaba ninguna pista de su posición real.
         * ---
         * Position within the album (0-indexed), optional -- if
         * present, it's prepended to the filename as "NN - " so the
         * real disc order survives a future reconciliation (see
         * LibraryReconciler). Explicit request from Miguel Ángel
         * (2026-07-05): "when we reconcile, the song order changes...
         * on concept albums like The Wall, that completely breaks the
         * album concept". Without this in the filename itself, there
         * was no way for a rescan to recover the order -- the file on
         * disk carried no trace of its real position.
         */
        const val KEY_TRACK_POSITION = "track_position"

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

    /**
     * S049 -- causa real del bug reportado por Miguel Ángel: mientras
     * hay descargas en marcha, la Biblioteca aparece vacía (el fix
     * anterior, distinctUntilChanged() en LibraryViewModel, no lo
     * arregló -- se queda, es correcto en sí mismo, pero no era la
     * causa de fondo). `CoroutineWorker.doWork()` corre por defecto en
     * `Dispatchers.Default` -- un pool de hilos limitado, pensado para
     * trabajo de CPU, NUNCA para E/S bloqueante. Todo este método hace
     * trabajo bloqueante real y prolongado ahí dentro: `runYtDlp()`
     * (Chaquopy/Python, red), `copyTo()` (E/S de archivo). Con varias
     * descargas en marcha (una importación de álbum encola varias a la
     * vez), ese pool compartido y limitado se satura de hilos
     * bloqueados en E/S -- y `LibraryViewModel.recompute()`, que
     * también corre en `Dispatchers.Default`, se queda sin turno de
     * CPU mientras dura la descarga: no es que se cancele, es que
     * nunca llega a ejecutarse.
     *
     * Se envuelve TODO el cuerpo en `withContext(Dispatchers.IO)`
     * -- el dispatcher correcto para E/S bloqueante, con un pool mucho
     * más amplio (pensado para justo este caso) y separado del que usa
     * la UI para cálculos de CPU. Kotlin prohíbe el retorno no local
     * (`return` a secas) dentro del lambda suspendido de
     * `withContext` -- los ocho `return Result.xxx` que ya había en
     * el cuerpo pasan a `return@withContext Result.xxx`, retorno
     * LOCAL del lambda (que es lo que `withContext()` devuelve, y por
     * tanto lo que devuelve `doWork()`), sin tocar ni una línea más de
     * la lógica.
     */
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
        val youtubeId = inputData.getString(KEY_YOUTUBE_ID)
            ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE)
            ?: return@withContext Result.failure()
        val artist = inputData.getString(KEY_ARTIST)
            ?: return@withContext Result.failure()
        // Ausente == sencillo (comportamiento normal) -- a diferencia
        // de youtubeId/title/artist, la ausencia de album NO es un
        // fallo, así que no aborta con Result.failure().
        val album = inputData.getString(KEY_ALBUM)
        // Ausente == no se conoce la posición (comportamiento normal
        // para sencillos o pistas de la pantalla de Búsqueda) -- ver
        // comentario de KEY_TRACK_POSITION más arriba.
        // ---
        // Absent == position unknown (normal for singles or tracks
        // from the Search screen) -- see KEY_TRACK_POSITION's comment
        // above.
        val trackPosition = inputData.getInt(KEY_TRACK_POSITION, -1)
            .takeIf { it >= 0 }

        val youtubeUrl = "https://youtu.be/$youtubeId"

        val rootUri = storageManager.getRootUri()
            ?: return@withContext Result.failure()

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
        ) ?: return@withContext Result.failure()

        val safeTitle = DownloadDirManager.sanitize(title)
        // "NN - Título.opus" cuando se conoce la posición -- ver
        // comentario de KEY_TRACK_POSITION. +1 porque trackPosition es
        // 0-indexed en Room pero el número de pista real de un disco
        // siempre empieza en 1.
        // ---
        // "NN - Title.opus" when the position is known -- see
        // KEY_TRACK_POSITION's comment. +1 because trackPosition is
        // 0-indexed in Room but a real disc's track number always
        // starts at 1.
        val outputFileName = if (trackPosition != null) {
            "%02d - %s.opus".format(trackPosition + 1, safeTitle)
        } else {
            "$safeTitle.opus"
        }

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
            // H07 PARTE 1 -- si no hay conexión justo en este
            // instante (poco probable, el archivo ya estaba
            // descargado de una vez anterior), no se compromete en
            // Room -- se deja el trabajo para que WorkManager lo
            // reintente más tarde, en vez de dejar un estado "solo
            // local, sin subir" (regla de negocio de Miguel Ángel,
            // S008).
            // ---
            // H07 PART 1 -- if there's no connection right at this
            // instant (unlikely, the file was already downloaded from
            // a previous run), it doesn't get committed to Room -- the
            // work is left for WorkManager to retry later, instead of
            // leaving a "local-only, not uploaded" state (Miguel
            // Ángel's business rule, S008).
            val outcome = autoSyncPusher.executeIfConnected(applicationContext) {
                repository.updateDownloadResult(
                    youtubeId,
                    filePath = existing.uri.toString(),
                    status = DownloadStatus.DONE,
                )
            }
            return@withContext if (outcome is com.miguelaetxio.mimoo.data.backup.MutationOutcome.Success) {
                Result.success()
            } else {
                Result.retry()
            }
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
                return@withContext Result.failure()
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

        return@withContext try {
            // Step 1 — download to temp via yt-dlp + Chaquopy + ffmpeg.
            // Paso 1 — descargar al temporal via yt-dlp + Chaquopy + ffmpeg.
            runYtDlp(
                youtubeUrl,
                tempBase.absolutePath,
                ffmpegDir,
                progressListener,
                youtubeId,
                cookiesManager.cookiesFilePathOrNull(),
            )

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

            // H07 PARTE 1 -- fix real del bug reportado por Miguel
            // Ángel (S008, segunda vuelta): antes, esto solo escribía
            // en Room, sin avisar nunca a Drive -- una pista
            // descargada aquí era invisible para cualquier otro
            // dispositivo hasta que alguna otra acción (favorito,
            // playlist) empujara un cambio por casualidad. Ahora
            // empuja como parte de la misma operación, igual que
            // cualquier otra mutación de la app.
            // ---
            // H07 PART 1 -- real fix for the bug reported by Miguel
            // Ángel (S008, second round): before, this only wrote to
            // Room, never telling Drive about it -- a track downloaded
            // here was invisible to every other device until some
            // other action (favorite, playlist) happened to push a
            // change. Now it pushes as part of the same operation,
            // same as any other mutation in the app.
            val outcome = autoSyncPusher.executeIfConnected(applicationContext) {
                repository.updateDownloadResult(
                    youtubeId,
                    filePath = outputDoc.uri.toString(),
                    status = DownloadStatus.DONE,
                )
            }
            if (outcome is com.miguelaetxio.mimoo.data.backup.MutationOutcome.Success) {
                Result.success()
            } else {
                Result.retry()
            }
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
                val cookiesPathForDebug = cookiesManager.cookiesFilePathOrNull()
                val ytDlpVersionForDebug = try {
                    com.chaquo.python.Python.getInstance()
                        .getModule("downloader")
                        .callAttr("get_ytdlp_version")
                        .toString()
                } catch (_: Exception) {
                    "desconocida"
                }
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
                                        // Fix real (2026-07-24) -- diagnóstico
                                        // del bug de cookies: sin esto no hay
                                        // forma de saber si downloader.py
                                        // llegó a recibir un cookiefile real o
                                        // si CookiesManager no tenía nada que
                                        // dar en el momento exacto del fallo.
                                        appendLine("cookiesPath : $cookiesPathForDebug")
                                        appendLine(
                                            "cookiesExist: " +
                                                "${cookiesPathForDebug?.let { File(it).exists() }}"
                                        )
                                        appendLine("cookiesDiag : ${cookiesManager.diagnosticsSummary()}")
                                        appendLine("ytDlpVersion: $ytDlpVersionForDebug")
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
                            appendLine("cookiesPath : $cookiesPathForDebug")
                            appendLine(
                                "cookiesExist: " +
                                    "${cookiesPathForDebug?.let { File(it).exists() }}"
                            )
                            appendLine("cookiesDiag : ${cookiesManager.diagnosticsSummary()}")
                            appendLine("ytDlpVersion: $ytDlpVersionForDebug")
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
     *
     * youtubeId se pasa para que downloader.py embeba el metadato
     * MIMOO_YOUTUBE_ID en el .opus resultante (H07 PARTE 0) -- así el
     * link sobrevive a una reinstalación aunque se pierda la fila de
     * Room. Ver downloader.py::download_audio() y
     * LibraryReconciler.kt (lado de lectura).
     * ---
     * Invoca downloader.py via Chaquopy pasando la ruta del binario ffmpeg.
     * outputBasePath debe ser SIN la extension .opus.
     *
     * youtubeId is passed so downloader.py embeds the
     * MIMOO_YOUTUBE_ID metadata tag in the resulting .opus (H07
     * PART 0) -- so the link survives a reinstall even if the Room
     * row is lost. See downloader.py::download_audio() and
     * LibraryReconciler.kt (read side).
     */
    private fun runYtDlp(
        youtubeUrl: String,
        outputBasePath: String,
        ffmpegPath: String,
        progressListener: DownloadProgressListener,
        youtubeId: String,
        cookiesPath: String?,
    ) {
        val py = com.chaquo.python.Python.getInstance()
        val downloader = py.getModule("downloader")
        downloader.callAttr(
            "download_audio",
            youtubeUrl,
            outputBasePath,
            ffmpegPath,
            progressListener,
            youtubeId,
            cookiesPath,
        )
    }
}


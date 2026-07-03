package com.miguelaetxio.mimoo

import android.app.Application
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.download.StorageManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class for MiMoo. Bootstraps Hilt, Chaquopy Python
 * runtime, and WorkManager with HiltWorkerFactory so that
 * @HiltWorker classes receive their injected dependencies.
 * ---
 * Clase Application de MiMoo. Arranca Hilt, el runtime Python de
 * Chaquopy y WorkManager con HiltWorkerFactory para que las clases
 * @HiltWorker reciban sus dependencias inyectadas.
 *
 * WorkManager initialisation pattern:
 * 1. The default WorkManagerInitializer ContentProvider is removed
 *    from the Manifest (tools:node="remove") to prevent auto-init
 *    before Hilt is ready.
 * 2. MiMooApp implements Configuration.Provider and returns a
 *    Configuration that uses HiltWorkerFactory.
 * WorkManager then picks up this configuration lazily on first use.
 */
@HiltAndroidApp
class MiMooApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var storageManager: StorageManager

    @Inject
    lateinit var downloadQueueManager: DownloadQueueManager

    // Vive tanto como el proceso -- solo se usa para el disparo puntual
    // de reconcileOrphanedDownloads() al arrancar, no para trabajo
    // continuo (eso sigue siendo responsabilidad de WorkManager).
    // ---
    // Lives as long as the process -- only used for the one-shot
    // reconcileOrphanedDownloads() trigger at startup, not for ongoing
    // work (that remains WorkManager's responsibility).
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        installCrashLogger()

        // Reencola al arrancar cualquier descarga QUEUED/DOWNLOADING
        // que se quedó huérfana (proceso muerto o WorkRequest
        // cancelado por el sistema antes de llegar a DONE/ERROR) --
        // bug real reportado por Miguel Ángel el 2026-07-03
        // reimportando Moon Safari: 4 de 10 pistas se quedaban "En
        // cola" para siempre sin avanzar y sin reintento manual
        // posible. Ver DownloadQueueManager.reconcileOrphanedDownloads().
        // ---
        // Re-enqueues at startup any QUEUED/DOWNLOADING download left
        // orphaned (process died or WorkRequest cancelled by the
        // system before reaching DONE/ERROR) -- real bug reported by
        // Miguel Ángel on 2026-07-03 while re-importing Moon Safari: 4
        // of 10 tracks stayed "En cola" forever, never progressing and
        // with no manual retry possible. See DownloadQueueManager.
        // reconcileOrphanedDownloads().
        appScope.launch {
            downloadQueueManager.reconcileOrphanedDownloads()
        }
    }

    /**
     * DIAGNÓSTICO TEMPORAL — instalado para capturar el crash real al
     * entrar en Biblioteca reportado por Miguel Ángel (2026-07-03),
     * sin acceso a adb/logcat ni a "Crashes & ANRs" del sistema.
     * Mismo mecanismo que DownloadWorker.debug_error.txt: escribe el
     * stacktrace completo a crash_log.txt en la raíz de la carpeta SAF
     * elegida, y reenvía la excepción al handler por defecto del
     * sistema para no alterar el comportamiento normal de cierre tras
     * un crash. Candidato a retirar una vez diagnosticado el bug —
     * no es una herramienta de producción permanente.
     * ---
     * TEMPORARY DIAGNOSTIC — installed to capture the real crash on
     * entering Biblioteca reported by Miguel Ángel (2026-07-03),
     * without adb/logcat access or a working system "Crashes & ANRs"
     * screen. Same mechanism as DownloadWorker.debug_error.txt: writes
     * the full stacktrace to crash_log.txt at the root of the chosen
     * SAF folder, then forwards the exception to the system's default
     * handler so normal crash-close behaviour is unaffected. Candidate
     * for removal once the bug is diagnosed — not a permanent
     * production tool.
     */
    private fun installCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val rootUri = storageManager.getRootUri()
                if (rootUri != null) {
                    val rootDoc = DocumentFile.fromTreeUri(this, rootUri)
                    val logFile = rootDoc?.findFile("crash_log.txt")
                        ?: rootDoc?.createFile("text/plain", "crash_log.txt")
                    logFile?.let { doc ->
                        contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                            out.write(
                                buildString {
                                    appendLine("timestamp   : ${System.currentTimeMillis()}")
                                    appendLine("thread      : ${thread.name}")
                                    appendLine("exception   : ${throwable::class.java.name}")
                                    appendLine("message     : ${throwable.message}")
                                    appendLine("--- stacktrace ---")
                                    appendLine(throwable.stackTraceToString())
                                }.toByteArray()
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Si ni siquiera esto funciona, no hay nada más que
                // hacer aquí — no debe impedir el cierre normal.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

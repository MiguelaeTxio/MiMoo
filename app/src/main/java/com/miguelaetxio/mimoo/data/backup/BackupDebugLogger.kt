package com.miguelaetxio.mimoo.data.backup

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.miguelaetxio.mimoo.data.download.StorageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log de diagnóstico de H06 (exportar/importar repositorio vía
 * Drive) a archivo -- MISMO patrón exacto que
 * `NotificationDebugLogger` (H03/H05, ver ese archivo): escribe en
 * "backup_debug.txt" en la raíz SAF elegida (mismo sitio donde vive
 * la música, fácil de encontrar con cualquier gestor de archivos),
 * con fallback al almacenamiento interno de la app si todavía no hay
 * raíz elegida. Cola de las últimas ~300 líneas para no crecer sin
 * límite.
 *
 * Motivo (S006): las llamadas `Log.d`/`Log.e` de Logcat que ya
 * existían en `DriveAuthorizationHelper`/`SettingsViewModel`/
 * `BackupDriveRepository`/`BackupImportRepository` exigen `adb` o una
 * app lectora de logcat -- un obstáculo real para diagnosticar en
 * campo sin ordenador a mano. Este logger escribe el mismo tipo de
 * información a un archivo de texto plano que Miguel Ángel puede leer
 * directamente. Complementa Logcat, no lo sustituye -- las llamadas
 * `Log.d`/`Log.e` existentes se mantienen tal cual.
 * ---
 * H06 (export/import repository via Drive) diagnostic log to file --
 * the EXACT same pattern as `NotificationDebugLogger` (H03/H05, see
 * that file): writes to "backup_debug.txt" at the chosen SAF root
 * (same place where the music lives, easy to find with any file
 * manager), falling back to the app's internal storage if no root has
 * been chosen yet. Keeps only the last ~300 lines so it doesn't grow
 * without bound.
 *
 * Why (S006): the existing Logcat `Log.d`/`Log.e` calls in
 * `DriveAuthorizationHelper`/`SettingsViewModel`/
 * `BackupDriveRepository`/`BackupImportRepository` require `adb` or a
 * logcat-reading app -- a real obstacle for diagnosing in the field
 * without a computer at hand. This logger writes the same kind of
 * information to a plain text file Miguel Ángel can read directly.
 * Complements Logcat, doesn't replace it -- the existing `Log.d`/
 * `Log.e` calls stay as they are.
 */
object BackupDebugLogger {
    private const val FILE_NAME = "backup_debug.txt"
    private const val MAX_LINES = 300
    private const val LOGCAT_TAG = "MiMoo-Backup-File"

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun log(context: Context, storageManager: StorageManager, line: String) {
        Log.d(LOGCAT_TAG, line)
        val stamped = "${timeFormat.format(Date())}  $line\n"
        try {
            val rootUri = storageManager.getRootUri()
            if (rootUri != null) {
                val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
                val file = rootDoc?.findFile(FILE_NAME)
                    ?: rootDoc?.createFile("text/plain", FILE_NAME)
                file?.let { doc ->
                    val existing = try {
                        context.contentResolver.openInputStream(doc.uri)
                            ?.bufferedReader()?.use { it.readText() }
                            ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                        val combined = (existing + stamped)
                            .lines()
                            .takeLast(MAX_LINES)
                            .joinToString("\n")
                        out.write(combined.toByteArray())
                    }
                }
            } else {
                File(context.filesDir, FILE_NAME).appendText(stamped)
            }
        } catch (e: Exception) {
            // Un fallo escribiendo el log de diagnóstico nunca debe
            // afectar al flujo real de exportación/importación.
            // ---
            // A failure writing the diagnostic log must never affect
            // the actual export/import flow.
        }
    }

    /** Variante para excepciones -- añade el stacktrace completo tras el mensaje. */
    fun logError(context: Context, storageManager: StorageManager, message: String, throwable: Throwable) {
        log(context, storageManager, "$message -- ${throwable::class.java.name}: ${throwable.message}\n${throwable.stackTraceToString()}")
    }
}

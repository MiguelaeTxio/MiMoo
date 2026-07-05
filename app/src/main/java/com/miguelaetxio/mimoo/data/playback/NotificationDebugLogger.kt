package com.miguelaetxio.mimoo.data.playback

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.miguelaetxio.mimoo.data.download.StorageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log de diagnóstico para el problema de la notificación de
 * reproducción sin controles reales (2026-07-05, ver captura de
 * Miguel Ángel: notificación con solo un icono genérico de play, sin
 * pausa/siguiente/anterior). Escribe en "notification_debug.txt" en
 * la raíz SAF (mismo patrón que debug_error.txt de DownloadWorker),
 * para confirmar con datos reales si MediaSessionService.onGetSession()
 * llega a invocarse siquiera, y qué eventos ve el Player.Listener --
 * en vez de seguir adivinando la causa a ciegas.
 * ---
 * Diagnostic log for the playback notification-with-no-real-controls
 * problem (2026-07-05, see Miguel Ángel's screenshot: notification with
 * just a generic play icon, no pause/next/previous). Writes to
 * "notification_debug.txt" at the SAF root (same pattern as
 * DownloadWorker's debug_error.txt), to confirm with real data whether
 * MediaSessionService.onGetSession() even gets invoked at all, and
 * what events the Player.Listener sees -- instead of continuing to
 * guess the cause blindly.
 */
object NotificationDebugLogger {
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun log(context: Context, storageManager: StorageManager, line: String) {
        val stamped = "${timeFormat.format(Date())}  $line\n"
        try {
            val rootUri = storageManager.getRootUri()
            if (rootUri != null) {
                val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
                val file = rootDoc?.findFile("notification_debug.txt")
                    ?: rootDoc?.createFile("text/plain", "notification_debug.txt")
                file?.let { doc ->
                    val existing = try {
                        context.contentResolver.openInputStream(doc.uri)
                            ?.bufferedReader()?.use { it.readText() }
                            ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                        // Cola de las últimas ~200 líneas para no crecer sin
                        // límite -- esto es un log de diagnóstico temporal,
                        // no un histórico permanente.
                        // ---
                        // Keep only the last ~200 lines so it doesn't grow
                        // without bound -- this is a temporary diagnostic
                        // log, not a permanent history.
                        val combined = (existing + stamped)
                            .lines()
                            .takeLast(200)
                            .joinToString("\n")
                        out.write(combined.toByteArray())
                    }
                }
            } else {
                File(context.filesDir, "notification_debug.txt").appendText(stamped)
            }
        } catch (e: Exception) {
            // Un fallo escribiendo el log de diagnóstico nunca debe
            // afectar a la reproducción real.
            // ---
            // A failure writing the diagnostic log must never affect
            // actual playback.
        }
    }
}

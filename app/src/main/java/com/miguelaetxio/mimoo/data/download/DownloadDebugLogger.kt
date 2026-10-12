package com.miguelaetxio.mimoo.data.download

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic log for real concurrent downloads, written to
 * "download_debug.txt" in the app's internal storage (never the SAF
 * root: with thousands of queued workers a SAF read-rewrite per line
 * would be far too slow). Picked up by `DebugLogCollector` and uploaded
 * with "Subir logs a Drive". Added in S037 to find out, with real data,
 * how many transfers are running at once.
 * ---
 * Log de diagnóstico de descargas reales simultáneas, escrito en
 * "download_debug.txt" en el almacenamiento interno de la app (nunca en
 * la raíz SAF: con miles de workers en cola, leer y reescribir por SAF
 * en cada línea sería demasiado lento). Lo recoge `DebugLogCollector` y
 * se sube con "Subir logs a Drive". Añadido en S037 para saber, con
 * datos reales, cuántas transferencias corren a la vez.
 */
object DownloadDebugLogger {
    const val FILE_NAME = "download_debug.txt"
    private const val LOGCAT_TAG = "MiMoo-Download-File"
    private const val MAX_BYTES = 200_000L
    private const val KEEP_LINES = 400
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val lock = Any()

    fun log(context: Context, line: String) {
        Log.d(LOGCAT_TAG, line)
        try {
            synchronized(lock) {
                val file = File(context.filesDir, FILE_NAME)
                if (file.exists() && file.length() > MAX_BYTES) {
                    val tail = file.readLines().takeLast(KEEP_LINES).joinToString("\n", postfix = "\n")
                    file.writeText(tail)
                }
                file.appendText("${timeFormat.format(Date())}  $line\n")
            }
        } catch (e: Exception) {
            // A failure writing this log must never affect a download.
            // ---
            // Un fallo escribiendo este log nunca debe afectar a una descarga.
        }
    }
}

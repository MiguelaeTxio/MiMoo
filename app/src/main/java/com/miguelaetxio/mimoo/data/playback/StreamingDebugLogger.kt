package com.miguelaetxio.mimoo.data.playback

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log de reproducción en streaming (`filesDir/streaming_debug.txt`):
 * resolución de URLs con yt-dlp, errores del reproductor y reproducción
 * de listas importadas. Un fallo al escribir nunca afecta a la app.
 * ---
 * Streaming playback log (`filesDir/streaming_debug.txt`): yt-dlp URL
 * resolution, player errors and playback of imported lists. A write
 * failure never affects the app.
 */
object StreamingDebugLogger {
    const val FILE_NAME = "streaming_debug.txt"
    private val lock = Any()
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun log(context: Context, line: String) {
        Log.d("MiMoo-Streaming", line)
        try {
            synchronized(lock) {
                val file = File(context.filesDir, FILE_NAME)
                if (file.exists() && file.length() > 200_000L) {
                    file.writeText(file.readLines().takeLast(500).joinToString("\n", postfix = "\n"))
                }
                file.appendText("${time.format(Date())}  $line\n")
            }
        } catch (_: Exception) {
        }
    }
}

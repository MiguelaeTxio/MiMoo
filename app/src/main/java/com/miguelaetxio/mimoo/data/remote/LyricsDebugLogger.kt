package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.miguelaetxio.mimoo.data.download.StorageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log de diagnóstico de H17 (Karaoke & Lyrics, S031) a archivo --
 * mismo patrón exacto que `RadioDebugLogger` (H08) y
 * `RadioBrowserDebugLogger` (H09). Archivo separado
 * ("letras_debug.txt") a propósito, mismo criterio que el resto de
 * loggers del proyecto: no mezclar el diagnóstico de una fuente/hito
 * con el de otro aunque compartan capa de red.
 *
 * Motivo: igual que `RadioRepository`, `LyricsRepository` es
 * defensivo a propósito (`catch (e: Exception)` en `getLyrics()`/
 * `searchLyrics()`, cualquier fallo se trata como "sin letra"/lista
 * vacía) -- correcto para no romper la UI, pero deja a Miguel Ángel
 * sin ninguna pista de si un tema sin letra en la app de verdad no la
 * tiene en lrclib.net, o si el fallo fue de red, de coincidencia
 * artista/título, o de caché.
 * ---
 * H17 (Karaoke & Lyrics, S031) diagnostic log to file -- same exact
 * pattern as `RadioDebugLogger` (H08) and `RadioBrowserDebugLogger`
 * (H09). Separate file, same reasoning as the rest of the project's
 * loggers.
 */
object LyricsDebugLogger {
    private const val FILE_NAME = "letras_debug.txt"
    // Mismo límite que RadioDebugLogger (S027) -- manejable para abrir
    // y compartir sin ambigüedad.
    private const val MAX_LINES = 400
    private const val LOGCAT_TAG = "MiMoo-Letras"

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
            // afectar al flujo real de letras/karaoke.
            // ---
            // A failure writing the diagnostic log must never affect
            // the actual lyrics/karaoke flow.
        }
    }
}

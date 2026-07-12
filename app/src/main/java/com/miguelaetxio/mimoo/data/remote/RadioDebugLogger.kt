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
 * Log de diagnóstico de H08 PARTE 2 ("Radio" -- artistas relacionados
 * vía MusicBrainz) a archivo -- mismo patrón exacto que
 * `RadioBrowserDebugLogger` (H09) y `BackupDebugLogger` (H06). Archivo
 * separado ("radio_relacionados_debug.txt") a propósito, para no
 * mezclar el diagnóstico de "Radio" (H08, artistas relacionados) con
 * el de "Radio Online" (H09, emisoras de Radio-Browser.info) --
 * conceptos y código completamente distintos que solo comparten
 * nombre coloquial.
 *
 * Motivo (S010): tanto RadioRepository.suggestRelatedArtist() como
 * PlayerManager.fetchOneRadioTrack()/topUpRadioQueueIfNeeded() son
 * defensivos a propósito (nunca lanzan, cualquier fallo se trata como
 * "no hay pista/sugerencia") -- correcto para no romper la
 * reproducción, pero deja a Miguel Ángel sin ninguna pista de qué
 * paso concreto falló cuando la Radio se corta antes de llegar a las
 * 10 pistas esperadas ("no sé qué es lo que está fallando", S010).
 * ---
 * H08 PART 2 ("Radio" -- related artists via MusicBrainz) diagnostic
 * log to file -- same exact pattern as `RadioBrowserDebugLogger` (H09)
 * and `BackupDebugLogger` (H06). Separate file on purpose, to avoid
 * mixing "Radio" (H08) diagnostics with "Radio Online" (H09)
 * diagnostics -- completely different features that only share a
 * colloquial name.
 */
object RadioDebugLogger {
    private const val FILE_NAME = "radio_relacionados_debug.txt"
    private const val MAX_LINES = 300
    private const val LOGCAT_TAG = "MiMoo-Radio-Relacionados"

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
            // afectar al flujo real de la Radio.
            // ---
            // A failure writing the diagnostic log must never affect
            // the actual Radio flow.
        }
    }
}

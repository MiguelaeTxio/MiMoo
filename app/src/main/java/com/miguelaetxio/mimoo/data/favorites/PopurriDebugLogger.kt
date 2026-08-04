package com.miguelaetxio.mimoo.data.favorites

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.miguelaetxio.mimoo.data.download.StorageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log de diagnóstico de los popurrís de Favoritos (sesión de diseño
 * de Favoritos, 2026-08-02/03) a archivo -- mismo patrón exacto que
 * `RadioDebugLogger` (H08). Archivo separado a propósito, mismo
 * criterio que ya justificó separar "Radio" (H08) de "Radio Online"
 * (H09): son sistemas de resolución completamente distintos
 * (`PopurriRepository` no comparte código con `RadioRepository`/
 * `PlayerManager.fetchOneRadioTrack()`) que solo comparten la
 * intuición de "generar una cola automáticamente".
 *
 * Motivo (2026-08-03): Miguel Ángel reportó un fallo real
 * ("timeout" visible en pantalla, superpuesto sobre la lista) al
 * generar un popurrí de 7 artistas favoritos, y no había NINGÚN
 * registro al que recurrir -- `PopurriRepository` nunca escribió en
 * ningún archivo de depuración. Mismo punto ciego que ya se corrigió
 * una vez para Radio (S010): "no sé qué es lo que está fallando".
 * ---
 * Diagnostic log for Favorites popurrís (Favorites design session,
 * 2026-08-02/03) to file -- same exact pattern as `RadioDebugLogger`
 * (H08). Separate file on purpose, same reasoning that already
 * justified separating "Radio" (H08) from "Radio Online" (H09):
 * completely different resolution systems (`PopurriRepository`
 * shares no code with `RadioRepository`/
 * `PlayerManager.fetchOneRadioTrack()`) that only share the intuition
 * of "auto-generate a queue".
 *
 * Reason (2026-08-03): Miguel Ángel reported a real failure ("timeout"
 * visible on screen, overlapping the list) generating a popurrí from
 * 7 favorite artists, and there was NO log to check --
 * `PopurriRepository` never wrote to any debug file. Same blind spot
 * already fixed once for Radio (S010): "I don't know what's failing".
 */
object PopurriDebugLogger {
    private const val FILE_NAME = "popurri_favoritos_debug.txt"
    private const val MAX_LINES = 400
    private const val LOGCAT_TAG = "MiMoo-Popurri-Favoritos"

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
            // afectar al flujo real del popurrí.
        }
    }
}

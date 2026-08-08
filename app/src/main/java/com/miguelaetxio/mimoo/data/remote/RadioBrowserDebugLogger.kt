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
 * Log de diagnóstico de H09 (Radio Online / Radio-Browser.info) a
 * archivo -- MISMO patrón exacto que `BackupDebugLogger` (H06, ver ese
 * archivo): escribe en "radio_debug.txt" en la raíz SAF elegida, con
 * fallback al almacenamiento interno de la app.
 *
 * Motivo (S010): RadioBrowserRepository es defensivo a propósito
 * (nunca lanza, cualquier fallo de red se trata como "sin
 * resultados") -- correcto para no romper la pantalla, pero eso
 * también esconde el motivo real de un fallo. Reportado por Miguel
 * Ángel (S010): la fila "País" desaparecía por completo de Radio
 * Online sin ningún aviso -- exactamente el tipo de fallo silencioso
 * que este logger existe para hacer visible sin necesitar `adb`.
 * ---
 * H09 (Radio Online / Radio-Browser.info) diagnostic log to file --
 * the EXACT same pattern as `BackupDebugLogger` (H06).
 *
 * Why (S010): RadioBrowserRepository is deliberately defensive (never
 * throws, any network failure is treated as "no results") -- correct
 * to avoid breaking the screen, but it also hides the real reason for
 * a failure. Reported by Miguel Ángel (S010): the "País" row vanished
 * entirely from Radio Online with no warning at all -- exactly the
 * kind of silent failure this logger exists to surface without
 * needing `adb`.
 */
object RadioBrowserDebugLogger {
    private const val FILE_NAME = "radio_debug.txt"
    private const val MAX_LINES = 300
    private const val LOGCAT_TAG = "MiMoo-Radio-File"

    // H15, S032 -- bug real de diagnóstico reportado por Miguel Ángel:
    // sin fecha en el timestamp, y con el archivo recortado a las
    // últimas MAX_LINES líneas sin importar el día, entradas de
    // sesiones de DÍAS DISTINTOS podían convivir en el mismo archivo
    // sin ninguna forma de distinguirlas -- llevó a diagnosticar como
    // "bug todavía vivo" lo que en realidad era una prueba de un día
    // anterior al arreglo. Aplicado a los siete logs de depuración de
    // la app por igual, no solo a este.
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun logError(
        context: Context,
        storageManager: StorageManager,
        message: String,
        throwable: Throwable,
    ) {
        val line = "$message -- ${throwable::class.java.name}: " +
            "${throwable.message}\n${throwable.stackTraceToString()}"
        Log.e(LOGCAT_TAG, line)
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
            // afectar al flujo real de Radio Online.
            // ---
            // A failure writing the diagnostic log must never affect
            // the actual Radio Online flow.
        }
    }
}

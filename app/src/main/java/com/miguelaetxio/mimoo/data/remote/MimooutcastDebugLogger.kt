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
 * Log de diagnóstico de H15 ("miMooutCast" -- Radio anclada a mano
 * por género/origen/década) a archivo -- mismo patrón exacto que
 * `RadioDebugLogger` (H08), `RadioBrowserDebugLogger` (H09),
 * `BackupDebugLogger` (H06) y `PopurriDebugLogger` (Favoritos).
 * Archivo separado a propósito ("mimooutcast_debug.txt"), para no
 * mezclar el diagnóstico de esta pantalla con el de la Radio
 * automática (`radio_relacionados_debug.txt`) -- aunque
 * `fetchSimpleManualCandidate()` reutilice piezas del motor de H08
 * (dictionario/MusicBrainz/biblioteca local), el punto de entrada, el
 * ancla y el criterio SIN CUPOS son completamente distintos.
 */
object MimooutcastDebugLogger {
    private const val FILE_NAME = "mimooutcast_debug.txt"
    // Mismo límite que RadioDebugLogger (S027): suficiente para varias
    // sesiones de miMooutCast sin volverse inmanejable.
    private const val MAX_LINES = 400
    private const val LOGCAT_TAG = "MiMoo-miMooutCast"

    // H15, S032 -- bug real de diagnóstico reportado por Miguel Ángel:
    // sin fecha en el timestamp, y con el archivo recortado a las
    // últimas MAX_LINES líneas sin importar el día, entradas de
    // sesiones de DÍAS DISTINTOS podían convivir en el mismo archivo
    // sin ninguna forma de distinguirlas -- llevó a diagnosticar como
    // "bug todavía vivo" lo que en realidad era una prueba de un día
    // anterior al arreglo. Aplicado a los siete logs de depuración de
    // la app por igual, no solo a este.
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

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
            // afectar al flujo real de miMooutCast.
        }
    }
}

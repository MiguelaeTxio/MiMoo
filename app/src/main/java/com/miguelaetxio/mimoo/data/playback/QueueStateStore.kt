package com.miguelaetxio.mimoo.data.playback

import android.content.Context
import com.google.gson.Gson
import java.io.File

/**
 * Cola de reproducción guardada en disco para sobrevivir a que el
 * sistema mate el proceso (Xiaomi lo hace aunque la app esté "sin
 * restricciones"): al volver, la cola, la pista y la posición siguen
 * ahí en pausa. Solo guarda lo que ya está en la cola; no inventa nada.
 * ---
 * Playback queue persisted to disk so it survives the system killing
 * the process: when the user comes back, queue, track and position
 * are still there, paused.
 */
data class SavedQueue(
    val items: List<QueueItem>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

class QueueStateStore(context: Context) {
    private val file = File(context.filesDir, "queue_state.json")
    private val gson = Gson()

    fun save(queue: SavedQueue) {
        try {
            val tmp = File(file.parentFile, "queue_state.json.tmp")
            tmp.writeText(gson.toJson(queue))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (_: Exception) {
            // Un fallo guardando la cola nunca debe afectar a la reproducción.
        }
    }

    fun clear() {
        try {
            file.delete()
        } catch (_: Exception) {
        }
    }

    fun load(): SavedQueue? = try {
        if (file.exists()) gson.fromJson(file.readText(), SavedQueue::class.java) else null
    } catch (_: Exception) {
        null
    }
}

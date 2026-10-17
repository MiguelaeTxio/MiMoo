package com.miguelaetxio.mimoo.data.stats

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copia local de los totales de Drive, solo para poder enseñar la
 * pantalla sin conexión y para recuperar los totales si una subida de
 * una versión antigua de la app los hubiera borrado de Drive. No es
 * una fuente de verdad: la verdad está en Drive.
 * ---
 * Local copy of Drive's totals, only to show the screen offline and to
 * recover the totals if an upload from an old app version had wiped
 * them from Drive. Not a source of truth: Drive is.
 */
@Singleton
class StatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val gson = Gson()
    private val file = File(context.filesDir, "stats_cache.json")
    private val _cache = MutableStateFlow(load())
    val cache: StateFlow<StatsBlock> = _cache.asStateFlow()

    private fun load(): StatsBlock = try {
        if (file.exists()) gson.fromJson(file.readText(), StatsBlock::class.java) ?: StatsBlock()
        else StatsBlock()
    } catch (e: Exception) {
        StatsBlock()
    }

    fun setCache(block: StatsBlock) {
        _cache.value = block
        try {
            file.writeText(gson.toJson(block))
        } catch (e: Exception) {
            // La copia local es prescindible.
        }
    }

    /**
     * Qué totales debe llevar una subida: los que ya hay en Drive; si
     * Drive no tiene ninguno pero esta copia local sí, esos.
     * ---
     * Which totals an upload must carry: the ones already in Drive; if
     * Drive has none but this local copy does, those.
     */
    fun carry(remote: StatsBlock?): StatsBlock? =
        remote ?: _cache.value.takeIf { !it.isEmpty() }
}

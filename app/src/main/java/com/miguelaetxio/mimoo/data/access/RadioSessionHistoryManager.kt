package com.miguelaetxio.mimoo.data.access

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S016 -- historial persistente de artistas usados por Radio (H08),
 * ENTRE sesiones (no solo dentro de una), a petición explícita de
 * Miguel Ángel: "procurar que en la siguiente sesión no volvamos a
 * escuchar lo mismo... hay que tener una semilla aleatoria ahí para
 * que las listas no sean siempre igual". Antes de esto, `PlayerManager`
 * solo llevaba `radioUsedArtists` en memoria, reseteado en cada sesión
 * nueva -- dos sesiones de Radio distintas con el mismo ancla podían
 * sonar casi idénticas.
 *
 * Es una preferencia SUAVE, no una exclusión absoluta:
 * `registerUsed()` se llama desde `PlayerManager.registerUsedArtist()`
 * cada vez que Radio acepta una pista de verdad, y
 * `recentlyUsedLower()` se usa en las tres cascadas
 * (diccionario/exploración/disco) para intentar evitar repetir --
 * pero si evitarlos deja el pool sin candidatos, el llamante ignora
 * esta lista y sigue con la cascada normal. Nunca bloquea que Radio
 * encuentre algo por evitar repetición.
 * ---
 * S016 -- persistent history of artists used by Radio (H08), ACROSS
 * sessions. A soft preference, never a hard exclusion -- see doc
 * above.
 */
@Singleton
class RadioSessionHistoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val PREFS_NAME = "mimoo_radio_history_prefs"
        const val KEY_RECENT_ARTISTS = "recent_artists"

        /** Tope del historial -- lista rotatoria, los más antiguos se van cayendo. */
        const val MAX_HISTORY = 400
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<String>>() {}.type

    private fun readList(): MutableList<String> {
        val json = prefs.getString(KEY_RECENT_ARTISTS, null) ?: return mutableListOf()
        return try {
            @Suppress("UNCHECKED_CAST")
            (gson.fromJson(json, listType) as? MutableList<String>) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    /** Nombres (en minúscula) usados recientemente por Radio, en cualquier sesión pasada. */
    fun recentlyUsedLower(): Set<String> = readList().map { it.lowercase() }.toSet()

    /**
     * Registra un artista como "usado" -- si ya estaba, lo mueve al
     * final (más reciente); recorta el historial a `MAX_HISTORY` por
     * el extremo más antiguo.
     */
    fun registerUsed(artist: String) {
        val trimmed = artist.trim()
        if (trimmed.isBlank()) return
        val list = readList()
        list.removeAll { it.equals(trimmed, ignoreCase = true) }
        list.add(trimmed)
        while (list.size > MAX_HISTORY) list.removeAt(0)
        prefs.edit { putString(KEY_RECENT_ARTISTS, gson.toJson(list)) }
    }
}

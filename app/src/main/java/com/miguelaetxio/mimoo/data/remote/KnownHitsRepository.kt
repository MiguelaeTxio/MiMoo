package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S011 -- diccionario de artistas conocidos por década, compilado UNA
 * SOLA VEZ (petición explícita de Miguel Ángel: "solo vamos a tener
 * que obtener las listas una única vez... sacamos un diccionario, ya
 * está") a partir de Wikipedia (números uno de LOS40/España por
 * década) y Billboard Hot 100 (histórico conocido), combinados en un
 * único archivo (`assets/known_hit_artists.json`) empaquetado con la
 * app -- sin scraping en tiempo de ejecución, sin llamada de red
 * alguna para esto.
 *
 * Nivel de artista, no de canción concreta -- encaja con el diseño ya
 * existente de RadioRepository, que sugiere ARTISTAS relacionados
 * (nunca canciones sueltas) vía género+país+década de MusicBrainz.
 * Comparar títulos exactos de canción contra los títulos, a menudo
 * ruidosos, que da la búsqueda de YouTube habría sido mucho más
 * frágil que comparar solo el nombre del artista.
 */
@Singleton
class KnownHitsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val gson = Gson()

    /** `lazy` -- se lee y parsea el asset una sola vez, la primera vez que se necesita. */
    private val byDecade: Map<Int, Set<String>> by lazy {
        try {
            val json = context.assets.open("known_hit_artists.json")
                .bufferedReader()
                .use { it.readText() }
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            val raw: Map<String, List<String>> = gson.fromJson(json, type)
            raw.mapKeys { it.key.toInt() }
                .mapValues { entry -> entry.value.map { it.lowercase() }.toSet() }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * `decadeBegin == null` -- sin ancla de década fijada (S011, ver
     * `RadioRepository.parseDecadeBegin()` -- puede no haber
     * `life-span.begin` en MusicBrainz para el artista que arrancó la
     * sesión) -- se comprueba contra TODAS las décadas conocidas, en
     * vez de rechazar todo por no poder acotar.
     */
    fun isKnownHitArtist(artist: String, decadeBegin: Int?): Boolean {
        val artistLower = artist.trim().lowercase()
        if (artistLower.isBlank()) return false
        val sets = if (decadeBegin != null) {
            listOfNotNull(byDecade[decadeBegin])
        } else {
            byDecade.values
        }
        return sets.any { artistLower in it }
    }
}

package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S013/S014 -- rediseño completo del diccionario de éxitos conocidos
 * (ver `DOCS/ANNEX_H08.md`, sección "S013"). Pasa de una lista plana
 * `{década: [artistas]}` (S011) a `{década: {es: [...], intl: [...]}}`,
 * cada entrada ahora `{artist, song}` en vez de solo el nombre del
 * artista -- petición explícita de Miguel Ángel: la búsqueda en
 * YouTube pasa de "solo artista" a "artista + canción concreta" (caso
 * guía: Yes en los 80 -> "Owner of a Lonely Heart", nunca
 * "Roundabout", que sería la correcta para los 70). `es`/`intl`
 * separan origen -- ESTRICTAMENTE "de España", no "de habla
 * hispana" (Alejandro Fernández, Chayanne, Ricky Martin, Shakira...
 * van en `intl` pese a cantar en español, porque no son grupos
 * españoles -- ver ANNEX_H08.md S013 punto 1: "el idioma en que
 * canten es irrelevante").
 *
 * Sigue compilado UNA SOLA VEZ (conocimiento propio + verificación
 * puntual, sin scraping en tiempo real, mismo criterio que S011) --
 * reutiliza los ~210 artistas que ya existían en la versión anterior
 * del diccionario, asignándoles ahora una canción concreta por
 * década. Deliberadamente no exhaustivo.
 * ---
 * S013/S014 -- complete redesign of the known-hits dictionary. See
 * `DOCS/ANNEX_H08.md`, "S013" section, for the full design.
 */
@Singleton
class KnownHitsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Un éxito conocido concreto -- artista + canción real de esa época. */
    data class KnownHit(val artist: String, val song: String)

    private data class RawHit(val artist: String = "", val song: String = "")
    private data class RawDecade(val es: List<RawHit> = emptyList(), val intl: List<RawHit> = emptyList())

    /** `lazy` -- se lee y parsea el asset una sola vez, la primera vez que se necesita. */
    private val byDecade: Map<Int, RawDecade> by lazy {
        try {
            val json = context.assets.open("known_hit_artists.json")
                .bufferedReader()
                .use { it.readText() }
            val type = object : TypeToken<Map<String, RawDecade>>() {}.type
            val raw: Map<String, RawDecade> = Gson().fromJson(json, type)
            raw.mapKeys { it.key.toInt() }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Pool de un origen para una década concreta o, si `decadeBegin`
     * es null, de TODAS las décadas conocidas (mismo criterio que la
     * versión S011 de esta clase para "sin ancla de década fijada" --
     * puede no haber `life-span.begin` en MusicBrainz para el artista
     * que arrancó la sesión).
     */
    private fun pool(decadeBegin: Int?, requireEs: Boolean): List<KnownHit> {
        val decades = if (decadeBegin != null) listOfNotNull(byDecade[decadeBegin]) else byDecade.values.toList()
        return decades.flatMap { d ->
            val raw = if (requireEs) d.es else d.es + d.intl
            raw.map { KnownHit(it.artist, it.song) }
        }
    }

    /** Comprueba si `artist` es un "éxito conocido" para la década+origen dados (ignora may/min). */
    fun isKnownHitArtist(artist: String, decadeBegin: Int?, requireEs: Boolean): Boolean =
        lookupHit(artist, decadeBegin, requireEs) != null

    /** Devuelve el par artista+canción exacto si `artist` está en el diccionario para esa década/origen. */
    fun lookupHit(artist: String, decadeBegin: Int?, requireEs: Boolean): KnownHit? {
        val artistLower = artist.trim().lowercase()
        if (artistLower.isBlank()) return null
        return pool(decadeBegin, requireEs).firstOrNull { it.artist.lowercase() == artistLower }
    }

    /**
     * S013, cupo del 80% -- elige un candidato al azar del diccionario
     * para la década+origen dados, excluyendo los artistas ya usados
     * en la sesión. `decadeBegin == null` amplía la búsqueda a
     * cualquier década conocida, igual que `lookupHit()`.
     */
    fun randomHit(decadeBegin: Int?, requireEs: Boolean, excludeArtists: Set<String>): KnownHit? {
        val excludeLower = excludeArtists.map { it.lowercase() }.toSet()
        return pool(decadeBegin, requireEs)
            .filter { it.artist.lowercase() !in excludeLower }
            .randomOrNull()
    }

    /**
     * S013, punto 4 -- primer filtro (barato, sin red) para saber si
     * un candidato encontrado por otra vía (MusicBrainz, biblioteca
     * local) "es de aquí": se comprueba contra la sublista `es` del
     * diccionario en CUALQUIER década, antes de caer al campo
     * `country` de MusicBrainz como respaldo (ver RadioRepository).
     */
    fun isKnownSpanishArtist(artist: String): Boolean {
        val artistLower = artist.trim().lowercase()
        if (artistLower.isBlank()) return false
        return byDecade.values.any { d -> d.es.any { it.artist.lowercase() == artistLower } }
    }
}

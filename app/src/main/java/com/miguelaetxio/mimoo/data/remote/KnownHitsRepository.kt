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
 * década. Ampliado en S016 (orden explícita de Miguel Ángel, "quince
 * es una mierda de diccionario"): listas `es` de cada década
 * engordadas con más artistas reales verificables, y corregida la
 * clasificación de Quevedo (canario, España -- estaba mal metido en
 * `intl` de los 2020). Deliberadamente no exhaustivo -- sigue
 * pendiente ampliar más en próximas sesiones, ver ANNEX_H08.md S016.
 *
 * **Género (S016, corrección de Miguel Ángel):** cada entrada tiene
 * ahora también un `genre` (un único género principal por canción,
 * mismo estilo de etiqueta que `RadioAnchor.genre`/MusicBrainz --
 * "pop", "rock", "pop rock", "flamenco", "rumba", "copla",
 * "reggaeton", "hip hop", etc.). El diccionario NUNCA había filtrado
 * por género -- error real, no decisión de Miguel Ángel, corregido en
 * el mismo bloque que amplió las listas `es`. Ver `randomHit()` para
 * la cascada género+década.
 * ---
 * S013/S014 -- complete redesign of the known-hits dictionary. See
 * `DOCS/ANNEX_H08.md`, "S013" section, for the full design.
 */
@Singleton
class KnownHitsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Un éxito conocido concreto -- artista + canción real de esa época, con su género principal. */
    data class KnownHit(val artist: String, val song: String, val genre: String)

    private data class RawHit(val artist: String = "", val song: String = "", val genre: String = "")
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
     * S020, orden explícita de Miguel Ángel: **el origen separa
     * España y extranjero en los DOS sentidos.** Ancla española ->
     * solo artistas españoles; ancla no española -> solo artistas no
     * españoles. No hay mezcla.
     *
     * `ANY` no lo usa hoy ningún camino de la Radio -- se conserva
     * porque `lookupHit()`/`isKnownHitArtist()` son consultas de
     * "¿está este artista en el diccionario?" donde a veces interesa
     * mirar el diccionario entero sin condicionar por origen.
     */
    enum class Origin { ES, INTL, ANY }

    /**
     * Pool de un origen para una década concreta o, si `decadeBegin`
     * es null, de TODAS las décadas conocidas (mismo criterio que la
     * versión S011 de esta clase para "sin ancla de década fijada" --
     * puede no haber `life-span.begin` en MusicBrainz para el artista
     * que arrancó la sesión).
     *
     * **Historial S020.** Antes recibía un `requireEs: Boolean` y
     * servía `d.es + d.intl` cuando era `false`, lo que metía el
     * bloque español entero en cualquier sesión anclada en un artista
     * extranjero. Miguel Ángel cerró la regla definitiva en S020:
     * separación dura en los dos sentidos.
     */
    private fun pool(decadeBegin: Int?, origin: Origin): List<KnownHit> {
        val decades = if (decadeBegin != null) listOfNotNull(byDecade[decadeBegin]) else byDecade.values.toList()
        return decades.flatMap { d ->
            val raw = when (origin) {
                Origin.ES -> d.es
                Origin.INTL -> d.intl
                Origin.ANY -> d.es + d.intl
            }
            raw.map { KnownHit(it.artist, it.song, it.genre) }
        }
    }

    /** Comprueba si `artist` es un "éxito conocido" para la década+origen dados (ignora may/min). */
    fun isKnownHitArtist(artist: String, decadeBegin: Int?, origin: Origin): Boolean =
        lookupHit(artist, decadeBegin, origin) != null

    /** Devuelve el par artista+canción exacto si `artist` está en el diccionario para esa década/origen. */
    fun lookupHit(artist: String, decadeBegin: Int?, origin: Origin): KnownHit? {
        val artistLower = artist.trim().lowercase()
        if (artistLower.isBlank()) return null
        return pool(decadeBegin, origin).firstOrNull { it.artist.lowercase() == artistLower }
    }

    /**
     * Cupo de CONOCIDOS, peldaño 1 -- un tema catalogado del género +
     * década + origen del ancla que no haya sonado todavía.
     *
     * **S020, cambio estructural.** La unidad de no-repetición pasa a
     * ser la CANCIÓN, no el artista. Orden textual de Miguel Ángel:
     * *"si hay que repetir artista se repite. Mientras, no se repite
     * canción hasta que no quede más remedio."* Antes se excluían
     * artistas enteros de forma dura, y eso era justo lo que forzaba
     * las degradaciones de género que había que eliminar.
     *
     * - `excludeSongKeys`: exclusión DURA, claves `artista|canción`
     *   ya servidas esta sesión (ver `songKey()`).
     * - `avoidArtists`: preferencia SUAVE -- se prefiere no repetir
     *   artista, pero repetirlo es siempre mejor que salirse del
     *   género. Si evitarlos deja el peldaño sin candidatos, se
     *   ignora la preferencia para ESE peldaño.
     *
     * Cascada, sin abandonar jamás el género (S020):
     *   1. género + década exacta.
     *   2. género, cualquier década.
     *   3. `null` -- el peldaño 1 de Conocidos está agotado; que lo
     *      resuelva `knownArtists()` (peldaño 2).
     */
    fun randomHit(
        genre: String?,
        decadeBegin: Int?,
        origin: Origin,
        excludeSongKeys: Set<String>,
        avoidArtists: Set<String> = emptySet(),
    ): KnownHit? {
        val avoidLower = avoidArtists.map { it.lowercase() }.toSet()
        fun pick(candidates: List<KnownHit>): KnownHit? {
            val allowed = candidates.filter { songKey(it.artist, it.song) !in excludeSongKeys }
            val preferred = allowed.filter { it.artist.lowercase() !in avoidLower }
            return preferred.ifEmpty { allowed }.randomOrNull()
        }

        if (genre != null && decadeBegin != null) {
            pick(pool(decadeBegin, origin).filter { it.genre.equals(genre, ignoreCase = true) })
                ?.let { return it }
        }
        if (genre != null) {
            pick(pool(null, origin).filter { it.genre.equals(genre, ignoreCase = true) })
                ?.let { return it }
        }
        return null
    }

    /**
     * Cupo de CONOCIDOS, peldaño 2 (S020) -- *"podemos seguir poniendo
     * temas de artistas conocidos aunque no se conozcan los temas"*.
     *
     * Devuelve los ARTISTAS del diccionario que cumplen género +
     * década + origen del ancla, sin mirar qué canciones suyas están
     * catalogadas: el llamante buscará en YouTube cualquier tema de
     * ellos. Ordenados con los menos repetidos primero (`avoidArtists`
     * al final), nunca vacío por preferencia: si todos están en
     * `avoidArtists` se devuelven igualmente.
     *
     * Misma cascada de dos peldaños que `randomHit()`: género+década
     * exacta y, si no hay nadie, género con cualquier década. El
     * género no se abandona.
     */
    fun knownArtists(
        genre: String?,
        decadeBegin: Int?,
        origin: Origin,
        avoidArtists: Set<String> = emptySet(),
    ): List<String> {
        val avoidLower = avoidArtists.map { it.lowercase() }.toSet()
        fun artistsOf(candidates: List<KnownHit>): List<String> =
            candidates.map { it.artist }.distinct()

        val exact = if (genre != null && decadeBegin != null) {
            artistsOf(pool(decadeBegin, origin).filter { it.genre.equals(genre, ignoreCase = true) })
        } else {
            emptyList()
        }
        val anyDecade = if (exact.isEmpty() && genre != null) {
            artistsOf(pool(null, origin).filter { it.genre.equals(genre, ignoreCase = true) })
        } else {
            emptyList()
        }
        val all = exact.ifEmpty { anyDecade }
        val (repeated, fresh) = all.partition { it.lowercase() in avoidLower }
        return fresh.shuffled() + repeated.shuffled()
    }

    /**
     * Clave de no-repetición de un tema. Normaliza a minúsculas y
     * recorta, para que "Bon Jovi - Livin' on a Prayer" y
     * "bon jovi - livin' on a prayer" cuenten como el mismo tema.
     */
    fun songKey(artist: String?, song: String?): String =
        (artist.orEmpty().trim().lowercase() + "|" + song.orEmpty().trim().lowercase())


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

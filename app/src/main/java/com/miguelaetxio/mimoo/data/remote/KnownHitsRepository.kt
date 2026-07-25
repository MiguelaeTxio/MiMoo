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
     * S013, cupo del 80% -- elige un candidato al azar del diccionario
     * para género+década+origen dados, excluyendo los artistas ya
     * usados en la sesión.
     *
     * **Cascada género/década (S020, orden explícita de Miguel Ángel:
     * "el género no debe abandonarse"):**
     *   1. género + década exacta.
     *   2. se agota la década -> se mantiene el GÉNERO, cualquier
     *      década.
     *   3. nada -- `null`. Que resuelva el cupo de disco o la vuelta
     *      siguiente; este cupo nunca sirve un género que no sea el
     *      del ancla.
     *
     * El origen NUNCA se relaja aquí dentro: lo fija el ancla de la
     * sesión y no se toca (S020, ver `Origin`).
     *
     * **Historial -- por qué desapareció un peldaño.** Hasta S020 la
     * cascada era simétrica (S016): entre el paso 1 y el actual paso 2
     * había un peldaño que mantenía la década y soltaba el género
     * entero. Ese peldaño es la causa directa, medida sobre
     * `radio_relacionados_debug.txt` real (~30h de uso), de que una
     * sesión anclada en `rock`/1980 sirviera copla (Rocío Jurado,
     * Isabel Pantoja) y pop a mansalva: saltaba enseguida, porque el
     * diccionario tiene solo 12 géneros y 158 de sus 289 entradas son
     * `pop`, así que cualquier caída acababa en pop. Ver
     * `DOCS/ANNEX_H08.md`, sección "S020", causa 2.
     * ---
     * S020 -- the genre is never abandoned (explicit instruction).
     * Cascade: genre+decade -> genre, any decade -> null. The old
     * S016 rung that kept the decade and dropped the genre is gone:
     * measured on a real ~30h log, it was the direct cause of copla
     * and pop flooding a `rock`/1980 session.
     *
     * `genre == null` o `decadeBegin == null` saltan directamente el
     * paso que dependería de ese dato ausente (mismo criterio que
     * antes de S016 para década desconocida).
     *
     * `avoidArtists` (S016 -- "que las listas no sean siempre igual"
     * entre sesiones, ver `RadioSessionHistoryManager`): preferencia
     * SUAVE en cada paso -- si evitarlos deja el paso sin candidatos,
     * se ignora `avoidArtists` para ESE paso y se elige igualmente de
     * él, nunca se salta un paso entero por esto.
     */
    fun randomHit(
        genre: String?,
        decadeBegin: Int?,
        origin: Origin,
        excludeArtists: Set<String>,
        avoidArtists: Set<String> = emptySet(),
    ): KnownHit? {
        val excludeLower = excludeArtists.map { it.lowercase() }.toSet()
        val avoidLower = avoidArtists.map { it.lowercase() }.toSet()
        fun pick(candidates: List<KnownHit>): KnownHit? {
            val allowed = candidates.filter { it.artist.lowercase() !in excludeLower }
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

package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.miguelaetxio.mimoo.util.SearchNormalizer
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
    private val genreTree: GenreTree,
) {
    /**
     * Un éxito conocido concreto -- artista + canción real de esa época.
     *
     * `genre` es la etiqueta única escrita a mano de siempre. `genres`
     * (S023) es el CONJUNTO real que MusicBrainz atribuye al artista,
     * y lo tienen 616 de las 777 entradas. Se conservan los dos:
     * `genre` sigue haciendo falta como término único de búsqueda en
     * MusicBrainz, y romperlo no aportaba nada.
     */
    data class KnownHit(
        val artist: String,
        val song: String,
        val genre: String,
        val genres: List<String> = emptyList(),
    ) {
        /**
         * Conjunto con el que cruzar contra el ancla. Las 161 entradas
         * sin enriquecer caen a su género único, que aquí es
         * simplemente un conjunto de uno.
         */
        val genreSet: Set<String>
            get() = genres.ifEmpty { listOf(genre) }
                .map { it.lowercase().trim() }
                .filter { it.isNotBlank() }
                .toSet()
    }

    private data class RawHit(
        val artist: String = "",
        val song: String = "",
        val genre: String = "",
        val genres: List<String> = emptyList(),
    )
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
     * Década en la que el diccionario sitúa un tema concreto (S023).
     *
     * **Por qué existe.** Hasta S023 la década del ancla salía de
     * `life-span.begin` del ARTISTA en MusicBrainz. Para un grupo eso
     * es el año de formación; para un solista es su fecha de
     * NACIMIENTO, y entonces el ancla siempre miente: P!nk nació en
     * 1979, así que su radio se anclaba en los 70 y devolvía Cat
     * Stevens y Lynyrd Skynyrd. Verificado sobre log real.
     *
     * Pero el problema es más profundo que los solistas, y lo cerró
     * Miguel Ángel: **la década la marca el TEMA, no el artista.** Yes
     * se formó en 1968, y entre "Roundabout" (1971) y "Owner of a
     * Lonely Heart" (1983) hay doce años y dos grupos distintos.
     * Anclar por el artista no acierta con ninguno de los dos.
     *
     * Se busca por artista Y tema. Si el artista está pero con otro
     * tema, no vale: sería volver a fechar por artista.
     */
    fun decadeOfTrack(artist: String, song: String?): Int? {
        if (song.isNullOrBlank()) return null
        val wantedArtist = SearchNormalizer.normalizeArtistName(artist)
        val wantedSong = SearchNormalizer.normalize(song)
        if (wantedArtist.isBlank() || wantedSong.isBlank()) return null

        return byDecade.entries.firstOrNull { (_, decade) ->
            (decade.es + decade.intl).any { hit ->
                SearchNormalizer.normalizeArtistName(hit.artist) == wantedArtist &&
                    SearchNormalizer.normalize(hit.song) == wantedSong
            }
        }?.key
    }

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
            raw.map { KnownHit(it.artist, it.song, it.genre, it.genres) }
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
     * Vuelta ÚNICA (S021), sin abandonar jamás el género NI la década:
     *   1. género + década del ancla.
     *   2. `null` -- el peldaño 1 de Conocidos está agotado; que lo
     *      resuelva `knownArtists()` (peldaño 2).
     *
     * **Historial S021.** Aquí vivía un peldaño intermedio "género,
     * cualquier década" (`pool(null, origin)`) que contradecía de
     * frente la especificación que Miguel Ángel cerró en S020:
     * *"siempre se respeta género y década, siempre"*. Era el
     * mecanismo REAL que servía Måneskin o Blur en una sesión anclada
     * en los 80. En S020 ese síntoma se atribuyó a entradas mal
     * fechadas del diccionario, pero la auditoría de S021 comprobó que
     * esas entradas estaban bien fechadas: la década la soltaba el
     * código, no el dato. Eliminado.
     *
     * `pool()` ya resuelve por sí solo el caso "el ancla no trae
     * década" sirviendo todas las décadas, así que no hace falta
     * ningún peldaño extra: si el ancla tiene década se respeta, y si
     * no la tiene no hay nada que respetar.
     */
    /**
     * ¿Encaja esta entrada del diccionario con el género del ancla?
     *
     * **S023 -- sustituye por completo a `GENRE_FAMILIES`.** Aquello
     * eran sacos de géneros escritos a mano por el modelo: opinión, no
     * dato. En uno convivían `new wave` y `post-punk`, y por esa arista
     * Tears for Fears entraba en una radio de Dead Can Dance. Ahora la
     * pertenencia se decide contra la taxonomía real de MusicBrainz
     * (ver [GenreTree]).
     *
     * **La regla, cerrada por Miguel Ángel**, con su analogía: oso
     * hormiguero y oso polar comparten ancestro -- mamífero -- y no son
     * parientes. Compartir un antepasado lejano no significa nada.
     *
     *   1. **Intersección directa** sobre un género CONCRETO. Que los
     *      dos digan `rock` no vale: es la carpeta raíz, y por ahí
     *      entraban Creed y Café Tacvba en una radio de P!nk. Que los
     *      dos digan `electropop` o `dance-pop`, sí.
     *   2. **Descenso desde el ancla.** Un género de la entrada cuelga
     *      de un género del ancla. Solo hacia ABAJO, y solo desde
     *      carpetas contenidas: bajar desde `rock` (129 descendientes)
     *      admitiría medio catálogo.
     *   3. **Hermanos**, último peldaño, y con el mismo tope: si el
     *      padre común es una raíz, ser hermanos no significa nada.
     *
     * Nunca se sube al padre y nunca se recorren aristas de influencia.
     * Con eso, las dos decisiones que quedaron abiertas en S022 se
     * resuelven sin criterio del modelo: Tears for Fears (`new wave`) y
     * New Order (`electronic`) entraban por influencia o por la raíz, y
     * quedan fuera. Joy Division sigue entrando porque `post-punk` está
     * literalmente en el conjunto del ancla.
     *
     * `hitGenres` es el conjunto de la entrada -- las 616 entradas
     * enriquecidas en S023 lo tienen. Las 161 que no, caen al `genre`
     * único de siempre, que aquí es un conjunto de uno.
     */
    private fun matchesGenre(hitGenres: Set<String>, anchorGenres: Set<String>): Boolean {
        val hits = hitGenres.map { it.lowercase().trim() }.filter { it.isNotBlank() }.toSet()
        val anchors = anchorGenres.map { it.lowercase().trim() }.filter { it.isNotBlank() }.toSet()
        if (hits.isEmpty() || anchors.isEmpty()) return false

        // 1 -- intersección directa, pero solo cuenta si lo que
        // comparten es una carpeta CONCRETA. Compartir `rock` (129
        // descendientes) no es parentesco: por ahí entraban Creed y
        // Café Tacvba en una radio de P!nk.
        if (hits.any { it in anchors && genreTree.isSpecific(it) }) return true

        // 2 -- descenso desde el ancla, nunca ascenso.
        val descendable = anchors.filter { genreTree.isSpecificEnoughToDescend(it) }
        if (descendable.any { anchor -> hits.any { genreTree.isDescendantOf(it, anchor) } }) {
            return true
        }

        // 3 -- hermanos, último peldaño.
        return anchors.any { anchor -> hits.any { genreTree.shareImmediateParent(it, anchor) } }
    }

    fun randomHit(
        genre: String?,
        decadeBegin: Int?,
        origin: Origin,
        excludeSongKeys: Set<String>,
        avoidArtists: Set<String> = emptySet(),
        relaxGenre: Boolean = false,
        anchorGenres: Set<String> = emptySet(),
    ): KnownHit? {
        val avoidLower = avoidArtists.map { it.lowercase() }.toSet()
        fun pick(candidates: List<KnownHit>): KnownHit? {
            val allowed = candidates.filter { songKey(it.artist, it.song) !in excludeSongKeys }
            val preferred = allowed.filter { it.artist.lowercase() !in avoidLower }
            return preferred.ifEmpty { allowed }.randomOrNull()
        }

        // S022 -- MODO DEGRADADO. Con MusicBrainz caído, el
        // diccionario es lo único que sostiene la Radio, y filtrar
        // además por género lo deja seco: una sesión anclada en
        // 'electropop'/ES/1980 se quedó sin candidatos en 0,7 segundos
        // y acabó sirviendo doce temas del mismo artista. Decisión de
        // Miguel Ángel ("habrá que soltarlo"): en degradado se
        // conservan origen y década -- que es lo que se percibe -- y
        // se suelta el género. Que suene Mecano es infinitamente mejor
        // que no sonar nada o repetir.
        if (relaxGenre) return pick(pool(decadeBegin, origin))
        if (genre == null) return null
        val anchorSet = anchorGenres.ifEmpty { setOf(genre) }
        return pick(pool(decadeBegin, origin).filter { matchesGenre(it.genreSet, anchorSet) })
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
     * Misma vuelta ÚNICA que `randomHit()` (S021): género + década del
     * ancla, sin peldaño de rescate que suelte la década. Ni el género
     * ni la década se abandonan nunca.
     */
    fun knownArtists(
        genre: String?,
        decadeBegin: Int?,
        origin: Origin,
        avoidArtists: Set<String> = emptySet(),
        relaxGenre: Boolean = false,
        anchorGenres: Set<String> = emptySet(),
    ): List<String> {
        val avoidLower = avoidArtists.map { it.lowercase() }.toSet()
        fun artistsOf(candidates: List<KnownHit>): List<String> =
            candidates.map { it.artist }.distinct()

        // S022 -- ver el comentario de `randomHit()`: en modo degradado
        // se sueltan los géneros y se conservan origen y década.
        val all = if (relaxGenre) {
            artistsOf(pool(decadeBegin, origin))
        } else {
            if (genre == null) return emptyList()
            artistsOf(
                pool(decadeBegin, origin).filter {
                    matchesGenre(it.genreSet, anchorGenres.ifEmpty { setOf(genre) })
                },
            )
        }
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

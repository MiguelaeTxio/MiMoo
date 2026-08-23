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
        /**
         * S025 -- país ISO del artista (`ES`, `GB`, `US`, `PR`...).
         * Nulo solo en el repertorio clásico, donde el país no filtra.
         */
        val country: String? = null,
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
        val country: String? = null,
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
     * S025 -- repertorio clásico, en asset aparte y SIN década ni país.
     *
     * Orden de Miguel Ángel: *"en clásica es clásica. No tenemos ni
     * origen ni década, solo género"*. Por eso no cabe en
     * `known_hit_artists.json`, que está organizado justamente por esas
     * dos dimensiones: es una lista plana.
     *
     * Antes de esto el diccionario no tenía NI UNA entrada clásica, y
     * `randomHit()` además corta en seco cuando la década es nula. Las
     * dos cosas juntas dejaban la Radio de clásica completamente muda:
     * verificado con Beethoven y la sonata 14 -- `backlog final: 0`.
     * ---
     * S025 -- classical repertoire, in its own asset and with neither
     * decade nor country, since those are exactly the two dimensions
     * the main dictionary is organized by. Before this there was not a
     * single classical entry, which left classical Radio silent.
     */
    private val classicalHits: List<KnownHit> by lazy {
        try {
            val json = context.assets.open("known_hit_classical.json")
                .bufferedReader()
                .use { it.readText() }
            val type = object : TypeToken<List<RawHit>>() {}.type
            val raw: List<RawHit> = Gson().fromJson(json, type)
            raw.map { KnownHit(it.artist, it.song, it.genre, it.genres, null) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * S026 -- el origen separa los CUATRO GRUPOS
     * (Iberoamericana/Anglosajona/Europea/Mundial, ver `OriginGroup`)
     * en TODOS los sentidos, sustituyendo el binario España/extranjero
     * de S020. Ancla de un grupo -> solo artistas del MISMO grupo.
     * Dentro del grupo, el país exacto ya no manda nada -- decisión
     * explícita de Miguel Ángel: *"prefiero que Led Zeppelin me traiga
     * a Van Halen o AC/DC antes que rebuscar en GB."*
     *
     * `originGroup == null` no filtra por origen en absoluto --
     * se conserva para `lookupHit()`/`isKnownHitArtist()`, consultas
     * de "¿está este artista en el diccionario?" que no alimentan la
     * cadena de la Radio, y para clásica (`RadioAnchor.isClassical`).
     */

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
     * Pool de un grupo de origen para una década concreta o, si
     * `decadeBegin` es null, de TODAS las décadas conocidas.
     *
     * **S024 -- ese "todas" ya no lo usa la Radio.** Era un resto de
     * S011 y en el log de S023 se vio lo que hacía: la copla de Carlos
     * Cano no se pudo fechar (MusicBrainz no tiene fecha para esa
     * actuación), el ancla quedó con `década=null`, y en vez de
     * restringir se abrió el diccionario entero -- la radio de una
     * copla de 1999 sirvió David Bisbal, La Oreja de Van Gogh, Aitana
     * y Dvicio. `randomHit()` y `knownArtists()` cortan ahora antes de
     * llegar aquí.
     *
     * El "todas las décadas" se conserva porque lo siguen necesitando
     * `lookupHit()`/`isKnownHitArtist()`.
     *
     * **S026 -- ya no hay bloque `es`/`intl` que elegir**: se combinan
     * siempre los dos (`d.es + d.intl`) y el filtro real es
     * `OriginGroup.of(it.country) == originGroup`, calculado del país
     * real de cada entrada -- el bloque `es`/`intl` de los propios
     * datos ya no se corresponde con los cuatro grupos (Brasil y
     * Portugal, por ejemplo, viven en `intl` pero pertenecen a
     * Iberoamericana).
     */
    private fun pool(decadeBegin: Int?, originGroup: OriginGroup?): List<KnownHit> {
        val decades = if (decadeBegin != null) listOfNotNull(byDecade[decadeBegin]) else byDecade.values.toList()
        return decades.flatMap { d ->
            val raw = d.es + d.intl
            val filtered = if (originGroup != null) {
                raw.filter { OriginGroup.of(it.country) == originGroup }
            } else {
                raw
            }
            filtered.map { KnownHit(it.artist, it.song, it.genre, it.genres, it.country) }
        }
    }

    /**
     * Todos los éxitos conocidos de una década (sin filtrar por
     * género), para pre-cargar como fuente rápida por defecto de una
     * sesión de "década sola" en miMooutCast -- ver el comentario real
     * de esta decisión en `PlayerManager.startRadioFromManualAnchor()`.
     * Simple envoltorio público de `pool()`, misma lista que ya usa
     * `randomHit(relaxGenre = true)` internamente.
     */
    fun allHitsForDecade(decadeBegin: Int, originGroup: OriginGroup? = null): List<KnownHit> =
        pool(decadeBegin, originGroup)

    /** Comprueba si `artist` es un "éxito conocido" para la década+grupo dados (ignora may/min). */
    fun isKnownHitArtist(artist: String, decadeBegin: Int?, originGroup: OriginGroup?): Boolean =
        lookupHit(artist, decadeBegin, originGroup) != null

    /** Devuelve el par artista+canción exacto si `artist` está en el diccionario para esa década/grupo. */
    fun lookupHit(artist: String, decadeBegin: Int?, originGroup: OriginGroup?): KnownHit? {
        val artistLower = artist.trim().lowercase()
        if (artistLower.isBlank()) return null
        return pool(decadeBegin, originGroup).firstOrNull { it.artist.lowercase() == artistLower }
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
        if (anchors.any { anchor -> hits.any { genreTree.shareImmediateParent(it, anchor) } }) {
            return true
        }

        // 4 -- S023, tras verlo fallar en dispositivo: si el ancla NO
        // tiene NINGÚN género concreto, lo ancho es lo único que hay y
        // tiene que contar.
        //
        // Miguel Ángel puso "Radio Futura - Divina". MusicBrainz le
        // atribuye un solo género: `rock`, con 129 descendientes. Como
        // el peldaño 1 exige que lo compartido sea concreto, NINGUNA de
        // las 777 entradas podía encajar y la porción del diccionario
        // -- el 80% del cupo -- se agotó en dos décimas de segundo.
        //
        // El umbral se midió contra anclas de siete géneros (P!nk, Dead
        // Can Dance, Led Zeppelin), todas con alguna carpeta concreta
        // entre ellos. Nunca contra una que solo tuviera una raíz.
        //
        // Esto NO afloja lo anterior: solo se aplica cuando el ancla
        // entera es genérica. Medido -- Radio Futura pasa de 0 a 260
        // candidatos, mientras P!nk (349) y Dead Can Dance (30) no se
        // mueven, así que Creed y Café Tacvba siguen fuera.
        if (anchors.none { genreTree.isSpecific(it) }) {
            return hits.any { it in anchors }
        }

        return false
    }

    fun randomHit(
        genre: String?,
        decadeBegin: Int?,
        originGroup: OriginGroup?,
        excludeSongKeys: Set<String>,
        avoidArtists: Set<String> = emptySet(),
        relaxGenre: Boolean = false,
        anchorGenres: Set<String> = emptySet(),
        /**
         * Orden en que han sonado los temas de esta sesión, del más
         * antiguo al más reciente (S024).
         *
         * Cuando NO está vacío se entiende que estamos repitiendo a la
         * fuerza, y entonces no se elige al azar: se elige el que
         * sonó hace más tiempo. Con diez temas disponibles, el azar
         * daba "Cadillac Solitario" siete veces mientras otros no
         * salían ninguna -- verificado en log real. Por antigüedad
         * suenan los diez antes de repetir ninguno.
         *
         * `radioUsedSongs` es un `mutableSetOf()`, que en Kotlin es un
         * LinkedHashSet: ya conserva el orden de inserción, así que la
         * antigüedad sale gratis sin estructura nueva.
         */
        playOrder: List<String> = emptyList(),
        /** S025 -- ancla de repertorio clásico: sin década ni país. */
        classical: Boolean = false,
        /**
         * S026 -- umbral de `GenreMatchQuality` (% mínimo de
         * intersección/unión de géneros específicos), configurable en
         * Ajustes -- ver `UiPreferencesManager.radioGenreMatchThresholdPercent`.
         */
        genreMatchThresholdPercent: Int = 40,
    ): KnownHit? {
        val avoidLower = avoidArtists.map { it.lowercase() }.toSet()
        fun pick(candidates: List<KnownHit>): KnownHit? {
            val allowed = candidates.filter { songKey(it.artist, it.song) !in excludeSongKeys }
            val preferred = allowed.filter { it.artist.lowercase() !in avoidLower }
            val usable = preferred.ifEmpty { allowed }
            if (playOrder.isEmpty()) return usable.randomOrNull()
            // El que no haya sonado nunca gana a cualquiera que sí.
            return usable.minByOrNull { hit ->
                val position = playOrder.indexOf(songKey(hit.artist, hit.song))
                if (position < 0) Int.MIN_VALUE else position
            }
        }

        // S025 -- la clásica entra por su propia lista, sin década ni
        // país, y por tanto ANTES del guardián de década: es
        // precisamente ese guardián el que dejaba muda la Radio de
        // clásica (ancla con `decadeBegin=null` -> `return null`).
        if (classical) {
            val anchorSet = anchorGenres.ifEmpty { genre?.let { setOf(it) } ?: emptySet() }
            val pooled = if (anchorSet.isEmpty()) {
                classicalHits
            } else {
                classicalHits.filter { matchesGenre(it.genreSet, anchorSet) }
            }
            return pick(pooled.ifEmpty { classicalHits })
        }

        // S024 -- sin década NO se sirve nada de aquí. Ver
        // `pool()`: una década nula abría las SIETE, y eso es
        // exactamente lo contrario de filtrar. En el log de S023 una
        // copla de Carlos Cano se quedó sin fechar y su radio sirvió
        // Aitana y Dvicio.
        if (decadeBegin == null) return null

        // S022 -- MODO DEGRADADO. Con MusicBrainz caído, el
        // diccionario es lo único que sostiene la Radio, y filtrar
        // además por género lo deja seco: una sesión anclada en
        // 'electropop'/ES/1980 se quedó sin candidatos en 0,7 segundos
        // y acabó sirviendo doce temas del mismo artista. Decisión de
        // Miguel Ángel ("habrá que soltarlo"): en degradado se
        // conservan origen y década -- que es lo que se percibe -- y
        // se suelta el género. Que suene Mecano es infinitamente mejor
        // que no sonar nada o repetir.
        if (relaxGenre) return pick(pool(decadeBegin, originGroup))
        if (genre == null) return null
        val anchorSet = anchorGenres.ifEmpty { setOf(genre) }
        // S026 -- un único filtro por PORCENTAJE (ver GenreMatchQuality)
        // en vez del sistema anterior de dos niveles fuerte/débil.
        val candidates = pool(decadeBegin, originGroup)
        val matching = candidates.filter {
            GenreMatchQuality.of(it.genreSet, anchorSet, genreTree, genreMatchThresholdPercent).matches
        }
        return pick(matching)
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
        originGroup: OriginGroup?,
        avoidArtists: Set<String> = emptySet(),
        relaxGenre: Boolean = false,
        anchorGenres: Set<String> = emptySet(),
        /** S025 -- ancla de repertorio clásico: sin década ni país. */
        classical: Boolean = false,
        /** S026 -- ver randomHit(). */
        genreMatchThresholdPercent: Int = 40,
    ): List<String> {
        val avoidLower = avoidArtists.map { it.lowercase() }.toSet()
        fun artistsOf(candidates: List<KnownHit>): List<String> =
            candidates.map { it.artist }.distinct()

        // S025 -- igual que en `randomHit()`: la clásica va por su
        // lista y antes del guardián de década.
        if (classical) {
            val anchorSet = anchorGenres.ifEmpty { genre?.let { setOf(it) } ?: emptySet() }
            val pooled = if (anchorSet.isEmpty()) {
                classicalHits
            } else {
                classicalHits.filter { matchesGenre(it.genreSet, anchorSet) }
            }
            val names = artistsOf(pooled.ifEmpty { classicalHits })
            val (rep, fre) = names.partition { it.lowercase() in avoidLower }
            return fre.shuffled() + rep.shuffled()
        }

        // S024 -- mismo guardián que `randomHit()`: sin década no se
        // sirve, en vez de servir las siete.
        if (decadeBegin == null) return emptyList()

        // S022 -- ver el comentario de `randomHit()`: en modo degradado
        // se sueltan los géneros y se conservan origen y década.
        val all = if (relaxGenre) {
            artistsOf(pool(decadeBegin, originGroup))
        } else {
            if (genre == null) return emptyList()
            val anchorSet = anchorGenres.ifEmpty { setOf(genre) }
            // S026 -- un único filtro por porcentaje, ver randomHit().
            val candidates = pool(decadeBegin, originGroup)
            artistsOf(
                candidates.filter {
                    GenreMatchQuality.of(it.genreSet, anchorSet, genreTree, genreMatchThresholdPercent).matches
                },
            )
        }
        val (repeated, fresh) = all.partition { it.lowercase() in avoidLower }
        return fresh.shuffled() + repeated.shuffled()
    }

    /**
     * Clave de no-repetición de un tema.
     *
     * S025 -- antes era `artist.trim().lowercase() + "|" +
     * song.trim().lowercase()`, y con eso la regla "un tema jamás
     * vuelve a sonar" era inaplicable en la práctica: el tema se
     * REGISTRA en `radioUsedSongs` con el título del vídeo de YouTube
     * ('LA UNIÓN - Lobo Hombre en París (1984)') y se COMPRUEBA contra
     * el título del diccionario ('Lobo-Hombre en París'). Dos cadenas
     * que nunca van a ser iguales, así que la exclusión dura solo
     * pillaba el caso de que YouTube devolviese EXACTAMENTE el mismo
     * vídeo. En el log de Miguel Ángel esa canción sonó tres veces en
     * una hora, y las tres veces el sistema la dio por "sin estrenar".
     *
     * Ahora las dos mitades se reducen a su esqueleto -- artista vía
     * `normalizeArtistName()`, título vía `songTitleKey()`, ambas sin
     * acentos, sin puntuación, sin mayúsculas y sin espacios -- de
     * modo que la clave es la misma se llegue por donde se llegue: por
     * el diccionario, por YouTube o por la biblioteca local.
     * ---
     * S025 -- both halves are now reduced to a stable skeleton so the
     * key is identical whether the track arrives from the dictionary,
     * from YouTube or from the local library. It used to compare a raw
     * YouTube video title against a dictionary title, which made the
     * "never repeat a song" rule unenforceable.
     */
    fun songKey(artist: String?, song: String?): String {
        val artistPart = SearchNormalizer.tight(
            SearchNormalizer.normalizeArtistName(artist.orEmpty())
        )
        val songPart = SearchNormalizer.songTitleKey(song.orEmpty(), artist)
        return "$artistPart|$songPart"
    }


    /**
     * S013, punto 4 -- primer filtro (barato, sin red) para saber si
     * un candidato encontrado por otra vía (MusicBrainz, biblioteca
     * local) "es de aquí": se comprueba contra la sublista `es` del
     * diccionario en CUALQUIER década, antes de caer al campo
     * `country` de MusicBrainz como respaldo (ver RadioRepository).
     */
    /**
     * Géneros que el diccionario conoce de un artista, mirando todas
     * las décadas y los dos orígenes.
     *
     * S024 -- existe para enriquecer el ANCLA. `resolveAnchor()` la
     * construye desde MusicBrainz, y para el bloque español MusicBrainz
     * es pobre: de Radio Futura solo da `rock`, carpeta raíz de 129
     * descendientes con la que `matchesGenre()` cae al último peldaño y
     * solo acepta lo que lleve literalmente `rock`. El diccionario, tras
     * el enriquecimiento con Discogs, tiene de ese mismo grupo
     * `[rock, pop rock, new wave, alternative rock, synth-pop]`.
     *
     * Sin esto, las 675 entradas nuevas del bloque español no se
     * aprovechan cuando el ancla es justo uno de esos artistas.
     */
    fun genresOfArtist(artist: String): Set<String> {
        if (artist.isBlank()) return emptySet()
        // S025 -- se compara NORMALIZADO, no en minúsculas a secas: así
        // 'Beethoven, Ludwig van' del catálogo de H05 casa con
        // 'Ludwig van Beethoven' del diccionario. Ver
        // SearchNormalizer.reorderCommaName().
        val wanted = SearchNormalizer.normalizeArtistName(artist)
        if (wanted.isBlank()) return emptySet()
        val found = mutableSetOf<String>()
        for (hit in classicalHits) {
            if (SearchNormalizer.normalizeArtistName(hit.artist) != wanted) continue
            found += hit.genreSet
        }
        for (decade in byDecade.values) {
            for (hit in decade.es + decade.intl) {
                if (SearchNormalizer.normalizeArtistName(hit.artist) != wanted) continue
                // `RawHit` es el reflejo crudo del JSON: `genres` puede
                // venir vacío, y entonces vale el `genre` suelto. Es la
                // misma convención que usa el resto de la clase al
                // construir `KnownHit.genreSet`.
                found += hit.genres.ifEmpty { listOf(hit.genre) }
                    .map { it.lowercase().trim() }
                    .filter { it.isNotBlank() }
            }
        }
        return found
    }

    /**
     * S026 -- sustituye a `isKnownSpanishArtist()`, generalizado a los
     * cuatro grupos. Busca al artista en TODO el diccionario (es+intl,
     * cualquier década) y devuelve el grupo de origen de su país
     * catalogado -- `null` si no está en el diccionario. Usado por
     * `RadioRepository.resolveAnchor()` como respaldo barato cuando
     * MusicBrainz no da país para el artista ancla.
     */
    fun originGroupOfKnownArtist(artist: String): OriginGroup? {
        val artistLower = artist.trim().lowercase()
        if (artistLower.isBlank()) return null
        val hit = byDecade.values
            .flatMap { it.es + it.intl }
            .firstOrNull { it.artist.lowercase() == artistLower }
            ?: return null
        return OriginGroup.of(hit.country)
    }

    /**
     * S026 -- ¿está `artist` en el diccionario de éxitos, en CUALQUIER
     * década y bloque (`es` o `intl`)? El bloque `intl` no significa
     * "extranjero cualquiera" -- está curado para artistas CONOCIDOS
     * EN ESPAÑA aunque no sean españoles (Shakira, Bad Bunny, Karol
     * G...), nunca "cualquier tema del Billboard sin más".
     *
     * Sirve de salvaguarda cuando el grupo de origen abre a todo
     * Hispanoamérica (S026, ver `OriginGroup`): la semilla de
     * Exploración (1.161 artistas) NO tiene ese filtro de "conocido en
     * España" -- es solo género+país de MusicBrainz. Orden de Miguel
     * Ángel, con ejemplo: *"si en España Shakira es número 1... Shakira
     * debe ser preferente junto a los demás éxitos en español... y que
     * no me salga Karumanta, que es un éxito en Perú."* Ver
     * `RadioRepository.suggestRelatedArtist()`.
     */
    fun isKnownArtistAnywhere(artist: String): Boolean {
        val artistLower = artist.trim().lowercase()
        if (artistLower.isBlank()) return false
        return byDecade.values.any { d -> (d.es + d.intl).any { it.artist.lowercase() == artistLower } }
    }
}

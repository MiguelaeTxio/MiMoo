package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzArtistSummary
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzGenre
import com.miguelaetxio.mimoo.util.SearchNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Género + país + década + origen fijados UNA SOLA VEZ al arrancar
 * una sesión de Radio (S010 género/país, S011 década, S013/S014
 * origen). Se calculan del PRIMER artista y no se vuelven a tocar
 * mientras dure la sesión.
 *
 * `isSpanishOrigin` -- ABSOLUTO para el resto de la sesión, nunca se
 * relaja en ningún peldaño de ningún cupo. `true` si el primer tema es
 * de un grupo ESPAÑOL (el idioma es irrelevante: hay grupos españoles
 * que cantan en inglés, p.ej. Los Bravos).
 *
 * **S020 -- separación dura en los DOS sentidos, regla cerrada por
 * Miguel Ángel.** `true` -> solo artistas españoles. `false` -> solo
 * artistas NO españoles. Ya no existe el "modo mixto": hasta S020,
 * `false` significaba "sin restricción de origen", y eso metía el
 * bloque español entero del diccionario en cualquier sesión anclada en
 * un artista extranjero -- medido sobre log real, con ancla Pixies
 * (rock/US/1980) el 60% del pool disponible era música española.
 * Lo extranjero sigue teniendo que ser CONOCIDO EN ESPAÑA cuando sale
 * del diccionario (ver KnownHitsRepository, bloque "intl"), nunca
 * cualquier tema del Billboard sin más.
 * ---
 * Genre + country + decade + origin fixed ONCE when a Radio session
 * starts. Computed from the FIRST artist and never recalculated for
 * the rest of the session.
 */
data class RadioAnchor(
    val genre: String,
    /**
     * TODOS los géneros que MusicBrainz atribuye al artista ancla, no
     * solo el más votado.
     *
     * S022 -- el fallo que Miguel Ángel calificó de aberración:
     * MusicBrainz describe a Dead Can Dance como
     * `dark wave(13), ethereal wave(9), gothic(5),
     * neoclassical dark wave(5), new age(4), ambient(1), post-punk(1)`,
     * y el código se quedaba con `dark wave` y tiraba los otros seis.
     * A partir de ahí había que reconstruir a mano, con familias de
     * géneros escritas a ojo, la información que se acababa de
     * descartar -- y así es como Pet Shop Boys acabó en una radio de
     * Dead Can Dance: agrupados por usar sintetizadores, que es como
     * juntar a Bob Marley con Bad Bunny porque reggae y reggaetón
     * suenan parecido.
     *
     * Conservando el conjunto, la pertenencia se decide por
     * INTERSECCIÓN con los géneros del candidato, que es un dato real
     * de la misma fuente y no una taxonomía inventada:
     *
     *   Joy Division   {post-punk, new wave, gothic rock} -> corta en
     *                  `post-punk` -> entra
     *   Pet Shop Boys  {synth-pop, dance-pop, house}      -> vacía -> fuera
     *   Guns N' Roses  {hard rock, glam metal}            -> vacía -> fuera
     *
     * `genre` se conserva porque las consultas a MusicBrainz necesitan
     * un único término de búsqueda.
     */
    val genres: Set<String> = setOf(genre),
    val country: String?,
    val decadeBegin: Int? = null,
    val isSpanishOrigin: Boolean = false,
) {
    /** ¿Comparte este candidato algún género con el ancla? */
    fun sharesGenreWith(candidateGenres: Set<String>): Boolean {
        if (candidateGenres.isEmpty()) return false
        val mine = genres.map { it.lowercase().trim() }.toSet()
        return candidateGenres.any { it.lowercase().trim() in mine }
    }
}

/**
 * H08 PARTE 2 -- "Radio": dado el artista que estaba sonando, sugiere
 * otro relacionado vía MusicBrainz, para continuar la reproducción en
 * streaming cuando la cola se queda sin nada más y el cíclico está
 * desactivado (PlayerManager).
 *
 * S013/S014 -- REDISEÑO DE ORIGEN (ver DOCS/ANNEX_H08.md, sección
 * "S013", motivación completa). `suggestRelatedArtist()` es AHORA
 * únicamente el mecanismo del cupo de "exploración" (10% de las
 * pistas que añade Radio, ver PlayerManager) -- ya NO decide por sí
 * solo si un candidato es aceptable (eso lo hace el cupo 80/10/10 en
 * PlayerManager, que combina esta clase con KnownHitsRepository y la
 * biblioteca local). Dentro de esta búsqueda, el origen (país=ES si
 * `anchor.isSpanishOrigin`, sin restricción de país si no) se
 * mantiene FIJO durante toda la cascada género/década -- nunca se
 * relaja aquí dentro (petición explícita de Miguel Ángel: "el origen
 * NO se relaja nunca" para este cupo). Cascada (prioridad género >
 * década, ver ANNEX_H08.md S013 punto 5):
 *   1. género + década exacta (+ origen)
 *   2. género, cualquier década (+ origen)
 *   3. década exacta, cualquier género (+ origen)
 *   4. sin candidatos -- null (el llamante decide el fallback final,
 *      que si acaso relaja el origen, ver PlayerManager).
 * ---
 * H08 PART 2 -- "Radio": given the artist that was playing, suggests
 * a related one via MusicBrainz.
 *
 * S013/S014 -- ORIGIN REDESIGN. `suggestRelatedArtist()` is now only
 * the "exploration" quota's mechanism (10% of the tracks Radio adds)
 * -- it no longer decides on its own whether a candidate is
 * acceptable. Origin stays FIXED through the whole genre/decade
 * cascade -- never relaxed inside this function.
 */
@Singleton
class RadioRepository @Inject constructor(
    private val musicBrainzApiService: MusicBrainzApiService,
    private val knownHitsRepository: KnownHitsRepository,
    @ApplicationContext private val appContext: Context,
    private val storageManager: StorageManager,
) {
    /**
     * Perfil de un artista para la fuente de "disco" (10% de la
     * biblioteca local, S013/S014, ver PlayerManager.pickDiscoCandidate()).
     * A diferencia de RadioAnchor (un único género elegido al azar),
     * aquí se devuelve el conjunto completo de géneros del artista,
     * para poder comprobar si contiene el género del ancla sin perder
     * información por el camino.
     */
    data class ArtistProfile(val genres: Set<String>, val country: String?, val decadeBegin: Int?)

    /**
     * SOLO se llama una vez, al arrancar una sesión de Radio -- ver
     * comentario de clase y PlayerManager.radioAnchor.
     * ---
     * ONLY called once, when a Radio session starts.
     */
    /**
     * Fallos transitorios de MusicBrainz seguidos (503, 429, timeout,
     * corte de red). Se pone a cero en cuanto una llamada responde.
     *
     * S022 -- MusicBrainz es gratuito y notoriamente inestable, y sus
     * caídas envenenaban la Radio entera: `lookupArtistProfile()` y
     * `findCandidates()` devuelven `null`/vacío tanto cuando NO HAY
     * candidatos como cuando NO SE HA PODIDO PREGUNTAR, y el motor
     * trataba ambos como "porción agotada", que es irreversible. Ocho
     * timeouts seguidos bastaron para dejar una sesión sirviendo doce
     * temas del mismo artista.
     */
    @Volatile
    var consecutiveTransientFailures: Int = 0
        private set

    /** ¿Está MusicBrainz dando problemas ahora mismo? */
    val isServiceDegraded: Boolean
        get() = consecutiveTransientFailures >= DEGRADED_THRESHOLD

    /**
     * True si el último fallo fue de red y no una respuesta legítima.
     * Lo consulta el motor para no derivar un ancla de la biblioteca
     * local cuando lo único que ha pasado es que MusicBrainz no
     * contesta.
     */
    @Volatile
    var lastFailureWasTransient: Boolean = false
        private set

    private fun isTransient(e: Exception): Boolean = when (e) {
        is retrofit2.HttpException -> e.code() == 429 || e.code() >= 500
        is java.io.IOException -> true
        else -> false
    }

    private fun noteFailure(e: Exception) {
        val transient = isTransient(e)
        lastFailureWasTransient = transient
        if (transient) consecutiveTransientFailures++
    }

    private fun noteSuccess() {
        consecutiveTransientFailures = 0
        lastFailureWasTransient = false
    }

    suspend fun resolveAnchor(
        sourceArtist: String,
        sourceTrackTitle: String? = null,
    ): RadioAnchor? {
        if (sourceArtist.isBlank() || isPlaceholderArtist(sourceArtist)) {
            log("resolveAnchor('$sourceArtist') -- origen vacío o placeholder, se descarta sin buscar")
            return null
        }
        return try {
            // S023 -- antes esto era `.artists.firstOrNull()?.id`: se
            // aceptaba el PRIMER resultado sin comprobar que el nombre
            // devuelto se pareciera al buscado. Con nombres cortos o
            // ambiguos eso fijaba el ancla equivocada, y como el ancla
            // congela género y país desde el primer tema, el error
            // contaminaba la cadena entera y no un tema suelto:
            //
            //   Pink        -> Pink Floyd                (progressive rock)
            //   Los Ángeles -> Los Angeles Philharmonic  (classical)
            //   Burning     -> Burning Spear             (reggae)
            //
            // Nótese que 'classical' reentraba por aquí pese a haberse
            // ordenado sacarlo del todo en S016: no llegaba como género
            // de un tema, sino de un ancla mal resuelta.
            val candidates = musicBrainzApiService
                .searchArtists(
                    query = buildArtistQuery(sourceArtist),
                    limit = ANCHOR_SEARCH_LIMIT,
                )
                .artists
            val sourceMbid = pickAnchorArtist(sourceArtist, candidates)
            if (sourceMbid == null) {
                return null
            }

            val sourceDetail = musicBrainzApiService.lookupArtist(sourceMbid)
            noteSuccess()
            val genres = sourceDetail.genres
                .filter { it.name.isNotBlank() }
            if (genres.isEmpty()) {
                log("resolveAnchor('$sourceArtist', mbid=$sourceMbid) -- encontrado en MusicBrainz pero SIN géneros propios (inc=genres vacío) -- no se puede fijar ancla")
                return null
            }
            // S020 -- ancla DETERMINISTA. Antes era `genres.random()`:
            // de todos los géneros del artista se echaba a suertes uno
            // y ese decidía la sesión entera. Ahora manda el más
            // votado por la comunidad de MusicBrainz, con desempate
            // alfabético para que el mismo artista dé SIEMPRE el mismo
            // ancla (dos sesiones de Pixies deben anclarse igual).
            val chosenGenre = genres
                .sortedWith(compareByDescending<MusicBrainzGenre> { it.count }.thenBy { it.name.lowercase() })
                .first()
                .name
            log(
                "resolveAnchor('$sourceArtist') -- géneros de MusicBrainz por votos: " +
                    genres.sortedByDescending { it.count }.joinToString { "${it.name}(${it.count})" } +
                    " -> elegido '$chosenGenre'"
            )
            val sourceCountry = sourceDetail.country?.trim()?.ifBlank { null }
            val decadeBegin = resolveTrackDecade(sourceArtist, sourceTrackTitle)
            // S013/S014, punto 4 -- "grupo español" se decide primero
            // por el diccionario de éxitos (barato, sin ambigüedad de
            // MusicBrainz) y, si el artista no está en él, por el
            // campo country=ES de MusicBrainz como respaldo.
            val isSpanishOrigin = knownHitsRepository.isKnownSpanishArtist(sourceArtist) ||
                sourceCountry == "ES"
            val allGenres = genres.map { it.name.lowercase().trim() }
                .filter { it.isNotBlank() }
                .toSet()
            log(
                "resolveAnchor('$sourceArtist') -> ancla fijada para toda la sesión: " +
                    "género='$chosenGenre', país=$sourceCountry, década=$decadeBegin, " +
                    "origen español=$isSpanishOrigin, géneros=[${allGenres.joinToString()}]"
            )
            RadioAnchor(
                genre = chosenGenre,
                genres = allGenres.ifEmpty { setOf(chosenGenre.lowercase()) },
                country = sourceCountry,
                decadeBegin = decadeBegin,
                isSpanishOrigin = isSpanishOrigin,
            )
        } catch (e: Exception) {
            noteFailure(e)
            log("resolveAnchor('$sourceArtist') -- EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * Cupo de "exploración" (10%, S013/S014) -- ver comentario de
     * clase para la cascada exacta. El origen (`anchor.isSpanishOrigin`
     * -> país=ES fijo; si no, sin restricción de país) se mantiene
     * FIJO en las tres vueltas de la cascada, nunca se relaja aquí.
     * `excludeArtists` son los nombres ya usados en esta sesión.
     * `avoidArtists` (S016, `RadioSessionHistoryManager`): preferencia
     * SUAVE entre sesiones -- si evitarlos deja una vuelta de la
     * cascada sin candidatos, se ignora para esa vuelta y se elige
     * igual de ella, nunca se salta una vuelta entera por esto.
     */
    suspend fun suggestRelatedArtist(
        anchor: RadioAnchor,
        excludeArtists: Set<String>,
        avoidArtists: Set<String> = emptySet(),
    ): String? {
        val excludeLower = excludeArtists.map { it.lowercase() }.toSet()
        val avoidLower = avoidArtists.map { it.lowercase() }.toSet()

        // S020 -- cascada de DOS peldaños, nunca tres. El tercero
        // (`findCandidatesAnyGenre`: mantener década, soltar el género)
        // se elimina por la regla suprema de Miguel Ángel: "el género no
        // se abandona nunca".
        //
        // S021 -- y ahora tampoco quedan DOS: vuelta ÚNICA. El segundo
        // peldaño mantenía el género pero soltaba la década
        // (`decadeBegin = null`), lo que contradecía la otra mitad de la
        // misma regla: *"siempre se respeta género y década, siempre"*.
        // `findCandidates()` ya omite el rango de fechas en la consulta
        // a MusicBrainz cuando `decadeBegin` es null, así que pasarle
        // directamente `anchor.decadeBegin` cubre los dos casos: ancla
        // con década (se respeta) y ancla sin ella (no hay nada que
        // respetar). Mismo cambio y misma razón que en
        // KnownHitsRepository.randomHit() y en
        // PlayerManager.pickDiscoCandidate().
        val candidates = findCandidates(anchor.genre, anchor.isSpanishOrigin, anchor.decadeBegin, excludeLower)
        val preferred = candidates.filter { it.lowercase() !in avoidLower }
        val chosen = preferred.ifEmpty { candidates }.randomOrNull()
        if (chosen == null) {
            log(
                "suggestRelatedArtist(género='${anchor.genre}', origen_es=${anchor.isSpanishOrigin}, " +
                    "década=${anchor.decadeBegin}) -- 0 candidatos en la vuelta única género+década " +
                    "(tras excluir ${excludeArtists.size} ya usados) -- eslabón roto para este cupo"
            )
        } else {
            log(
                "suggestRelatedArtist(género='${anchor.genre}', origen_es=${anchor.isSpanishOrigin}, " +
                    "década=${anchor.decadeBegin}) -> '$chosen' (${candidates.size} candidatos)"
            )
        }
        return chosen
    }

    /**
     * S013/S014, punto 8 -- fuente de "disco" (10%, biblioteca local
     * sin género/país/década guardados): resuelve el perfil completo
     * de un artista bajo demanda, para que PlayerManager pueda
     * comprobar si contiene el género del ancla sin descartar
     * artistas por elegir un único género al azar (a diferencia de
     * resolveAnchor(), que sí necesita reducir a uno solo).
     */
    suspend fun lookupArtistProfile(artistName: String): ArtistProfile? {
        if (artistName.isBlank() || isPlaceholderArtist(artistName)) return null
        return try {
            // S023 -- mismo arreglo que en resolveAnchor(): se
            // comprueba que el candidato devuelto SEA el artista
            // buscado, en vez de aceptar el primero que llegue.
            val mbid = pickAnchorArtist(
                artistName,
                musicBrainzApiService
                    .searchArtists(
                        query = buildArtistQuery(artistName),
                        limit = ANCHOR_SEARCH_LIMIT,
                    )
                    .artists,
            ) ?: return null
            val detail = musicBrainzApiService.lookupArtist(mbid)
            noteSuccess()
            val genres = detail.genres.map { it.name }.filter { it.isNotBlank() }.toSet()
            ArtistProfile(
                genres = genres,
                country = detail.country?.trim()?.ifBlank { null },
                decadeBegin = parseDecadeBegin(detail.lifeSpan?.begin),
            )
        } catch (e: Exception) {
            noteFailure(e)
            log("lookupArtistProfile('$artistName') -- EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}")
            null
        }
    }

    private suspend fun findCandidates(
        genre: String,
        isSpanishOrigin: Boolean,
        decadeBegin: Int?,
        excludeLower: Set<String>,
    ): List<String> = try {
        // S010 -- offset aleatorio, no siempre 0, para variar entre
        // sesiones de Radio con el mismo ancla (ver historial de esta
        // función en versiones anteriores del archivo).
        val randomOffset = (0..90 step 10).toList().random()
        val found = musicBrainzApiService
            .searchArtists(query = buildGenreQuery(genre, isSpanishOrigin, decadeBegin), limit = 10, offset = randomOffset)
            .artists
            .map { it.name }
            .filter { it.lowercase() !in excludeLower && !isPlaceholderArtist(it) }
        // El servicio ha respondido. Que la lista venga vacía es una
        // respuesta legítima, no un fallo: el contador se reinicia
        // igual.
        noteSuccess()
        found
    } catch (e: Exception) {
        noteFailure(e)
        log("findCandidates(género='$genre', origen_es=$isSpanishOrigin, década=$decadeBegin) -- EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}")
        emptyList()
    }

    /**
     * S020 -- el origen separa España y extranjero en los DOS
     * sentidos, igual que el diccionario
     * (`KnownHitsRepository.Origin`). Ancla española -> `country:ES`;
     * ancla no española -> `NOT country:ES`, para que el cupo de
     * artistas desconocidos no devuelva españoles en una sesión
     * extranjera.
     */
    private fun buildGenreQuery(genre: String, isSpanishOrigin: Boolean, decadeBegin: Int?): String {
        fun escape(value: String) = value.replace("\"", "")
        var query = "tag:\"${escape(genre)}\""
        query += if (isSpanishOrigin) " AND country:ES" else " AND NOT country:ES"
        if (decadeBegin != null) query += " AND begin:[$decadeBegin TO ${decadeBegin + 9}]"
        return query
    }

    /**
     * Década del TEMA que arranca la sesión (S023).
     *
     * **Qué sustituye y por qué.** Hasta S023 esto era
     * `parseDecadeBegin(sourceDetail.lifeSpan?.begin)`: la década salía
     * del `life-span` del ARTISTA. Para un grupo eso es el año de
     * formación y colaba; para un solista es su fecha de NACIMIENTO, y
     * mentía siempre. Verificado en log real: P!nk, nacida en 1979,
     * anclaba una sesión en la década de 1970 y la Radio devolvía Cat
     * Stevens, Lynyrd Skynyrd, ELO y Supertramp -- todos correctos
     * para ese ancla, que era el problema. El motor obedecía; el dato
     * era falso.
     *
     * Miguel Ángel cerró la regla al ver el diagnóstico, y va más allá
     * de los solistas: **la década la marca el tema, nunca el
     * artista.** Yes se formó en 1968; "Roundabout" es de 1971 y
     * "Owner of a Lonely Heart" de 1983. No es lo mismo escuchar una
     * que otra, y fechar por el grupo no acierta con ninguna. Ese caso
     * exacto estaba en el log desde antes: una radio anclada en Led
     * Zeppelin (formados en 1968) trayendo "Owner of a Lonely Heart".
     *
     * **Cascada, en orden de fiabilidad:**
     *
     * 1. El diccionario local, si conoce ese artista Y ese tema. Es
     *    gratis, no gasta petición y no depende de que MusicBrainz
     *    esté en pie.
     * 2. `first-release-date` de la grabación en MusicBrainz. Una
     *    petición más por sesión, solo al arrancar.
     * 3. Nada. Se deja la década SIN FIJAR antes que inventarla. Sin
     *    década la Radio filtra por género y origen: menos preciso,
     *    pero no falso. `pool()` ya contempla `decadeBegin == null`.
     *
     * El año del tema local no entra en la cascada porque no existe:
     * `SearchResultTrack` no guarda fecha. Si algún día la guarda,
     * este es el sitio donde entraría, por delante de todo lo demás.
     */
    /**
     * Década a partir del `life-span.begin` de un ARTISTA.
     *
     * S023 -- ya NO se usa para el ancla; ahí se fecha el tema (ver
     * `resolveTrackDecade()`). Sobrevive solo para
     * `lookupArtistProfile()`, que perfila artistas CANDIDATOS de la
     * biblioteca local.
     *
     * ATENCIÓN: arrastra el mismo defecto de fondo. Para un solista
     * esto es su fecha de nacimiento, y para un grupo el año de
     * formación, que tampoco es la década de sus temas. Aquí hace
     * menos daño que en el ancla -- filtra un candidato suelto, no
     * condiciona la sesión entera -- pero sigue estando mal.
     * Pendiente, anotado en ANNEX_H08.md.
     */
    private fun parseDecadeBegin(begin: String?): Int? {
        val year = begin?.take(4)?.toIntOrNull() ?: return null
        return (year / 10) * 10
    }

    private suspend fun resolveTrackDecade(artist: String, trackTitle: String?): Int? {
        val cleanTitle = trackTitle?.let { stripTitleNoise(it) }

        knownHitsRepository.decadeOfTrack(artist, cleanTitle)?.let { decade ->
            log("resolveTrackDecade('$artist' -- '$cleanTitle') -> década $decade, del diccionario local")
            return decade
        }

        if (cleanTitle.isNullOrBlank()) {
            log("resolveTrackDecade('$artist') -- sin título de tema utilizable, década SIN FIJAR")
            return null
        }

        return try {
            val query = "recording:\"${cleanTitle.replace("\"", "")}\" " +
                "AND artist:\"${artist.replace("\"", "")}\""
            val wantedTitle = SearchNormalizer.normalize(cleanTitle)
            val dated = musicBrainzApiService.searchRecordings(query = query)
                .recordings
                .filter { SearchNormalizer.normalize(it.title) == wantedTitle }
                .mapNotNull { it.firstReleaseDate?.take(4)?.toIntOrNull() }
            // La MÁS ANTIGUA: la primera publicación del tema, no la
            // recopilación o reedición que se esté escuchando.
            val year = dated.minOrNull()
            if (year == null) {
                log("resolveTrackDecade('$artist' -- '$cleanTitle') -- MusicBrainz no da fecha para el tema, década SIN FIJAR")
                null
            } else {
                val decade = (year / 10) * 10
                log("resolveTrackDecade('$artist' -- '$cleanTitle') -> década $decade (primera publicación $year), de MusicBrainz")
                decade
            }
        } catch (e: Exception) {
            // Un fallo aquí NO invalida el ancla: se pierde la década,
            // no la sesión. Tampoco cuenta como fallo de servicio: el
            // ancla en sí ya se resolvió bien justo antes.
            log("resolveTrackDecade('$artist' -- '$cleanTitle') -- ${e::class.java.simpleName}, década SIN FIJAR")
            null
        }
    }

    /**
     * Quita del título el ruido que trae YouTube y que impediría casar
     * el tema con MusicBrainz o con el diccionario: "(Official Video)",
     * "[Lyric Video]", "(Remastered 2011)" y compañía. También corta un
     * prefijo "Artista - " si viene pegado delante, que es la forma
     * habitual en que YouTube titula los vídeos musicales.
     */
    private fun stripTitleNoise(rawTitle: String): String {
        val withoutBrackets = rawTitle
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^]]*]"), " ")
        val withoutArtistPrefix = withoutBrackets.substringAfter(" - ", withoutBrackets)
        return withoutArtistPrefix.replace(Regex("\\s+"), " ").trim()
    }

    private fun log(line: String) = RadioDebugLogger.log(appContext, storageManager, line)

    private fun isPlaceholderArtist(name: String): Boolean =
        name.equals("Various Artists", ignoreCase = true) ||
            name.equals("[unknown]", ignoreCase = true) ||
            name.equals("[anonymous]", ignoreCase = true) ||
            name.equals("[traditional]", ignoreCase = true)

    private fun buildArtistQuery(artist: String): String {
        fun escape(value: String) = value.replace("\"", "")
        return "artist:\"${escape(artist)}\""
    }

    /**
     * Lista de desambiguación cargada de `artist_disambiguation.json`
     * (S023). Se lee una sola vez y se conserva; es un asset del APK,
     * no cambia en ejecución.
     */
    private data class Disambiguation(
        /** Nombre normalizado -> MBID fijado a mano. */
        val forced: Map<String, String>,
        /** Normalizados sin MBID: MusicBrainz no tiene al artista. */
        val blocked: Set<String>,
        /** Normalizado -> nombre canónico que MusicBrainz devuelve. */
        val confirmed: Map<String, String>,
    )

    private val disambiguation: Disambiguation by lazy { loadDisambiguation() }

    private fun loadDisambiguation(): Disambiguation {
        return try {
            val json = appContext.assets.open("artist_disambiguation.json")
                .bufferedReader()
                .use { it.readText() }
            val root = org.json.JSONObject(json)

            val forced = mutableMapOf<String, String>()
            val blocked = mutableSetOf<String>()
            val wrong = root.optJSONObject("incorrectos")
            wrong?.keys()?.forEach { name ->
                val key = SearchNormalizer.normalizeArtistName(name)
                val mbid = wrong.optJSONObject(name)?.optString("mbid").orEmpty()
                if (mbid.isNotBlank() && mbid != "null") forced[key] = mbid else blocked += key
            }

            val confirmed = mutableMapOf<String, String>()
            val ok = root.optJSONObject("confirmados")
            ok?.keys()?.forEach { name ->
                confirmed[SearchNormalizer.normalizeArtistName(name)] =
                    SearchNormalizer.normalizeArtistName(ok.optString(name))
            }

            log("desambiguación cargada -- ${forced.size} con MBID fijado, ${blocked.size} sin resolver, ${confirmed.size} confirmados")
            Disambiguation(forced, blocked, confirmed)
        } catch (e: Exception) {
            // Sin la lista se sigue funcionando: lo que se pierde son
            // las correcciones manuales, no la comprobación de nombre.
            log("desambiguación NO disponible (${e.javaClass.simpleName}) -- se sigue con verificación de nombre")
            Disambiguation(emptyMap(), emptySet(), emptyMap())
        }
    }

    /**
     * Elige el artista del que se va a fijar el ancla, en vez de
     * aceptar el primer resultado (S023).
     *
     * Orden: primero la corrección manual, si la hay; después el
     * primer candidato cuyo nombre coincida de verdad con el buscado,
     * ya plegados acentos y tipografía por `normalizeArtistName()`
     * -- que es lo que hace que 'Guns N'Roses' case con
     * 'Guns N' Roses' y 'a‐ha' con 'a-ha'.
     *
     * Si ningún candidato coincide se devuelve null y NO se ancla.
     * Preferimos quedarnos sin radio a construir una cadena entera
     * sobre un artista que no es.
     */
    private fun pickAnchorArtist(
        sourceArtist: String,
        candidates: List<MusicBrainzArtistSummary>,
    ): String? {
        val wanted = SearchNormalizer.normalizeArtistName(sourceArtist)

        disambiguation.forced[wanted]?.let { mbid ->
            log("resolveAnchor('$sourceArtist') -- MBID fijado a mano ($mbid), no se usa la búsqueda")
            return mbid
        }
        if (wanted in disambiguation.blocked) {
            log("resolveAnchor('$sourceArtist') -- artista marcado como no resoluble en MusicBrainz, no se fija ancla")
            return null
        }

        if (candidates.isEmpty()) {
            log("resolveAnchor('$sourceArtist') -- MusicBrainz no encontró NINGÚN artista con ese nombre (searchArtists vacío)")
            return null
        }

        val canonical = disambiguation.confirmed[wanted]
        // `normalize()` BORRA la puntuación en vez de sustituirla por
        // espacio, así que 'M-Clan' queda como "mclan" y 'M Clan' como
        // "m clan": el mismo grupo, y no casaban. Verificado en log
        // real de S023 -- se rechazó a M-Clan, que era el correcto.
        // Comparar además sin espacios cierra ese hueco sin tocar
        // `normalize()`, que lo usan también H12 y favoritos.
        fun tight(value: String) = value.replace(" ", "")
        val match = candidates.firstOrNull { candidate ->
            val got = SearchNormalizer.normalizeArtistName(candidate.name)
            got == wanted ||
                tight(got) == tight(wanted) ||
                (canonical != null && (got == canonical || tight(got) == tight(canonical)))
        }

        if (match == null) {
            log(
                "resolveAnchor('$sourceArtist') -- ningún candidato coincide con el nombre buscado; " +
                    "descartados: ${candidates.joinToString(", ") { it.name }}. No se fija ancla."
            )
            return null
        }
        if (match !== candidates.first()) {
            log("resolveAnchor('$sourceArtist') -- se descarta '${candidates.first().name}' y se toma '${match.name}' por coincidencia de nombre")
        }
        return match.id
    }

    private companion object {
        /**
         * Fallos transitorios seguidos a partir de los cuales se
         * considera que MusicBrainz no está disponible y la Radio pasa
         * a modo degradado.
         *
         * S022 -- subido de 2 a 4 tras verlo en dispositivo: dos 503
         * sueltos de `lookupArtistProfile()` bastaron para declarar
         * caído un servicio que en esa misma sesión estaba devolviendo
         * diez candidatos sin problema. Con `noteSuccess()` ya
         * presente en todos los caminos de éxito, llegar a cuatro
         * significa cuatro fallos SEGUIDOS de verdad.
         */
        const val DEGRADED_THRESHOLD = 4

        /**
         * Candidatos que se piden al buscar el artista del ancla.
         *
         * S023 -- subido del 5 por defecto. La búsqueda por
         * `artist:"NOMBRE"` devuelve coincidencias PARCIALES antes que
         * la exacta: pidiendo cinco, de 'Kanye West' salían una banda
         * tributo y una colaboración, y el artista real no aparecía en
         * la ventana. Con `pickAnchorArtist()` descartando por nombre,
         * pedir de más no cuesta precisión -- cuesta no encontrarlo.
         */
        const val ANCHOR_SEARCH_LIMIT = 25
    }
}

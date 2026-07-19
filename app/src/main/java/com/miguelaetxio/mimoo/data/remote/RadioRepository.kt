package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import com.miguelaetxio.mimoo.data.download.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Género + país + década + origen fijados UNA SOLA VEZ al arrancar
 * una sesión de Radio (S010 género/país, S011 década, S013/S014
 * origen). Se calculan del PRIMER artista y no se vuelven a tocar
 * mientras dure la sesión.
 *
 * `isSpanishOrigin` (S013/S014, ver DOCS/ANNEX_H08.md sección "S013"
 * punto 1) -- ABSOLUTO para el resto de la sesión de Radio, nunca se
 * relaja ni siquiera en el último peldaño del fallback normal (solo
 * el fallback de "clásica" lo ignora del todo, como red de seguridad
 * final). true si el primer tema es de un grupo ESPAÑOL (idioma
 * irrelevante -- hay grupos españoles que cantan en inglés, p.ej. Los
 * Bravos). false ("modo mixto") en cualquier otro caso -- el resto de
 * la sesión puede ser español o extranjero sin restricción de origen,
 * pero lo extranjero debe ser CONOCIDO EN ESPAÑA (ver
 * KnownHitsRepository, "intl"), nunca cualquier tema del Billboard
 * sin más.
 * ---
 * Genre + country + decade + origin fixed ONCE when a Radio session
 * starts. Computed from the FIRST artist and never recalculated for
 * the rest of the session.
 */
data class RadioAnchor(
    val genre: String,
    val country: String?,
    val decadeBegin: Int? = null,
    val isSpanishOrigin: Boolean = false,
)

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
    suspend fun resolveAnchor(sourceArtist: String): RadioAnchor? {
        if (sourceArtist.isBlank() || isPlaceholderArtist(sourceArtist)) {
            log("resolveAnchor('$sourceArtist') -- origen vacío o placeholder, se descarta sin buscar")
            return null
        }
        return try {
            val sourceMbid = musicBrainzApiService
                .searchArtists(query = buildArtistQuery(sourceArtist))
                .artists
                .firstOrNull()
                ?.id
            if (sourceMbid == null) {
                log("resolveAnchor('$sourceArtist') -- MusicBrainz no encontró NINGÚN artista con ese nombre (searchArtists vacío)")
                return null
            }

            val sourceDetail = musicBrainzApiService.lookupArtist(sourceMbid)
            val genres = sourceDetail.genres
                .map { it.name }
                .filter { it.isNotBlank() }
            if (genres.isEmpty()) {
                log("resolveAnchor('$sourceArtist', mbid=$sourceMbid) -- encontrado en MusicBrainz pero SIN géneros propios (inc=genres vacío) -- no se puede fijar ancla")
                return null
            }
            val chosenGenre = genres.random()
            val sourceCountry = sourceDetail.country?.trim()?.ifBlank { null }
            val decadeBegin = parseDecadeBegin(sourceDetail.lifeSpan?.begin)
            // S013/S014, punto 4 -- "grupo español" se decide primero
            // por el diccionario de éxitos (barato, sin ambigüedad de
            // MusicBrainz) y, si el artista no está en él, por el
            // campo country=ES de MusicBrainz como respaldo.
            val isSpanishOrigin = knownHitsRepository.isKnownSpanishArtist(sourceArtist) ||
                sourceCountry == "ES"
            log(
                "resolveAnchor('$sourceArtist') -> ancla fijada para toda la sesión: " +
                    "género='$chosenGenre', país=$sourceCountry, década=$decadeBegin, " +
                    "origen español=$isSpanishOrigin"
            )
            RadioAnchor(
                genre = chosenGenre,
                country = sourceCountry,
                decadeBegin = decadeBegin,
                isSpanishOrigin = isSpanishOrigin,
            )
        } catch (e: Exception) {
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
        val originCountry = if (anchor.isSpanishOrigin) "ES" else null

        val genreAndDecade = if (anchor.decadeBegin != null) {
            findCandidates(anchor.genre, originCountry, anchor.decadeBegin, excludeLower)
        } else {
            emptyList()
        }
        val genreAnyDecade = if (genreAndDecade.isEmpty()) {
            findCandidates(anchor.genre, originCountry, decadeBegin = null, excludeLower)
        } else {
            emptyList()
        }
        val decadeAnyGenre = if (genreAndDecade.isEmpty() && genreAnyDecade.isEmpty() && anchor.decadeBegin != null) {
            findCandidatesAnyGenre(originCountry, anchor.decadeBegin, excludeLower)
        } else {
            emptyList()
        }

        val candidates = genreAndDecade.ifEmpty { genreAnyDecade.ifEmpty { decadeAnyGenre } }
        val preferred = candidates.filter { it.lowercase() !in avoidLower }
        val chosen = preferred.ifEmpty { candidates }.randomOrNull()
        if (chosen == null) {
            log(
                "suggestRelatedArtist(género='${anchor.genre}', origen_es=${anchor.isSpanishOrigin}, " +
                    "década=${anchor.decadeBegin}) -- 0 candidatos en ninguna vuelta de la cascada " +
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
            val mbid = musicBrainzApiService
                .searchArtists(query = buildArtistQuery(artistName))
                .artists
                .firstOrNull()
                ?.id ?: return null
            val detail = musicBrainzApiService.lookupArtist(mbid)
            val genres = detail.genres.map { it.name }.filter { it.isNotBlank() }.toSet()
            ArtistProfile(
                genres = genres,
                country = detail.country?.trim()?.ifBlank { null },
                decadeBegin = parseDecadeBegin(detail.lifeSpan?.begin),
            )
        } catch (e: Exception) {
            log("lookupArtistProfile('$artistName') -- EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}")
            null
        }
    }

    private suspend fun findCandidates(
        genre: String,
        countryCode: String?,
        decadeBegin: Int?,
        excludeLower: Set<String>,
    ): List<String> = try {
        // S010 -- offset aleatorio, no siempre 0, para variar entre
        // sesiones de Radio con el mismo ancla (ver historial de esta
        // función en versiones anteriores del archivo).
        val randomOffset = (0..90 step 10).toList().random()
        musicBrainzApiService
            .searchArtists(query = buildGenreQuery(genre, countryCode, decadeBegin), limit = 10, offset = randomOffset)
            .artists
            .map { it.name }
            .filter { it.lowercase() !in excludeLower && !isPlaceholderArtist(it) }
    } catch (e: Exception) {
        log("findCandidates(género='$genre', país=$countryCode, década=$decadeBegin) -- EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}")
        emptyList()
    }

    /**
     * S013/S014 -- paso 3 de la cascada de suggestRelatedArtist():
     * abandona el género, mantiene la década (y el origen) exacta.
     */
    private suspend fun findCandidatesAnyGenre(
        countryCode: String?,
        decadeBegin: Int,
        excludeLower: Set<String>,
    ): List<String> = try {
        val randomOffset = (0..90 step 10).toList().random()
        musicBrainzApiService
            .searchArtists(query = buildDecadeOnlyQuery(countryCode, decadeBegin), limit = 10, offset = randomOffset)
            .artists
            .map { it.name }
            .filter { it.lowercase() !in excludeLower && !isPlaceholderArtist(it) }
    } catch (e: Exception) {
        log("findCandidatesAnyGenre(país=$countryCode, década=$decadeBegin) -- EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}")
        emptyList()
    }

    private fun buildGenreQuery(genre: String, countryCode: String?, decadeBegin: Int?): String {
        fun escape(value: String) = value.replace("\"", "")
        var query = "tag:\"${escape(genre)}\""
        if (countryCode != null) query += " AND country:${escape(countryCode)}"
        if (decadeBegin != null) query += " AND begin:[$decadeBegin TO ${decadeBegin + 9}]"
        return query
    }

    private fun buildDecadeOnlyQuery(countryCode: String?, decadeBegin: Int): String {
        fun escape(value: String) = value.replace("\"", "")
        var query = "begin:[$decadeBegin TO ${decadeBegin + 9}]"
        if (countryCode != null) query += " AND country:${escape(countryCode)}"
        return query
    }

    /**
     * "1983-05-12", "1983-05", "1983" -- MusicBrainz life-span.begin
     * viene con distinta precisión según lo que conste en su base. Se
     * usan solo los 4 primeros dígitos (el año) y se redondea hacia
     * abajo a la década ("1983" -> 1980).
     */
    private fun parseDecadeBegin(begin: String?): Int? {
        val year = begin?.take(4)?.toIntOrNull() ?: return null
        return (year / 10) * 10
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
}

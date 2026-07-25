package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzGenre
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
        isSpanishOrigin: Boolean,
        decadeBegin: Int?,
        excludeLower: Set<String>,
    ): List<String> = try {
        // S010 -- offset aleatorio, no siempre 0, para variar entre
        // sesiones de Radio con el mismo ancla (ver historial de esta
        // función en versiones anteriores del archivo).
        val randomOffset = (0..90 step 10).toList().random()
        musicBrainzApiService
            .searchArtists(query = buildGenreQuery(genre, isSpanishOrigin, decadeBegin), limit = 10, offset = randomOffset)
            .artists
            .map { it.name }
            .filter { it.lowercase() !in excludeLower && !isPlaceholderArtist(it) }
    } catch (e: Exception) {
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

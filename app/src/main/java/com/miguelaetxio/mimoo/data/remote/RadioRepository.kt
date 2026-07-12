package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import com.miguelaetxio.mimoo.data.download.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * H08 PARTE 2 (S009, fix de sesgo hacia música inglesa e
 * instrumentación de diagnóstico en S010) -- "Radio": dado el artista
 * que estaba sonando, sugiere otro relacionado, para continuar la
 * reproducción en streaming cuando la cola se queda sin nada más y el
 * cíclico está desactivado (PlayerManager). Decisión de diseño
 * explícita de Miguel Ángel: MusicBrainz (géneros compartidos), no el
 * Mix automático de YouTube -- descartado por inestabilidad
 * documentada de yt-dlp en esa área (ver ANNEX_H08.md).
 *
 * Algoritmo (deliberadamente simple, sin pretender ser un motor de
 * recomendación real):
 *   1. Buscar el artista de origen en MusicBrainz -> MBID + país.
 *   2. Consultar sus géneros (`inc=genres`) y confirmar su país
 *      (campo de primer nivel, siempre presente si MusicBrainz lo
 *      tiene, ver MusicBrainzArtistDetail.country).
 *   3. Buscar otros artistas con uno de esos géneros Y el MISMO país
 *      (`tag:"<género>" AND country:<país> AND NOT arid:<origen>`).
 *      Si el artista de origen no tiene país registrado, o si la
 *      búsqueda con país no da ningún candidato, se reintenta sin la
 *      restricción de país (solo género) -- nunca debe bloquear la
 *      Radio por ser demasiado estricta.
 *   4. Elegir uno al azar entre los candidatos -- varía en cada
 *      disparo en vez de ser siempre el mismo, y evita sugerir el
 *      propio artista de origen.
 *
 * Bug real corregido en S010 (reportado por Miguel Ángel, con
 * ejemplos concretos: sembrar con música española o con un tema
 * tecno terminaba en un "popurrí de música inglesa" de todos modos).
 * Causa raíz: el género por sí solo no filtra nada por idioma/región
 * -- los tags de género de MusicBrainz son globales y están dominados
 * de forma aplastante por artistas anglosajones (sesgo real y
 * documentado de esa base de datos, no un fallo de programación), así
 * que `tag:"pop"` o `tag:"techno"` sin más devuelve mayoría de
 * artistas ingleses/americanos venga de donde venga el origen. El
 * país SÍ acota correctamente por región/idioma, y MusicBrainz lo
 * soporta de forma nativa en su sintaxis Lucene
 * (`country:ES` -- verificado en vivo esta sesión contra el ejemplo
 * oficial en musicbrainz.org/doc/Search_Server:
 * "artist:fred AND type:group AND country:US").
 *
 * Instrumentado con RadioDebugLogger (S010, tras reporte real de
 * Miguel Ángel: la Radio se cortaba tras una sola pista y "no sé qué
 * es lo que está fallando"). Un fallo AQUÍ (artista no encontrado, sin
 * géneros propios, sin candidatos ni con país ni sin él) es
 * exactamente el tipo de eslabón roto que corta la cadena de
 * "relacionados" -- cada motivo de fallo se registra explícitamente en
 * vez de devolver null en silencio.
 *
 * El nombre elegido NO se reproduce directamente desde MusicBrainz
 * (que no aloja audio) -- quien llama (PlayerManager) lo busca
 * después con el motor gratuito ya existente
 * (ExternalLinkResolver.searchYoutube()), igual que cualquier otra
 * búsqueda de la app.
 *
 * Nunca lanza excepción hacia quien llama -- cualquier fallo de red,
 * de parseo, o simplemente "MusicBrainz no tiene géneros para este
 * artista" se trata igual que "no hay sugerencia", mismo patrón que
 * CoverArtRepository. La Radio es una mejora de la experiencia, no
 * debe poder romper la reproducción si no encuentra nada -- pero ahora
 * el motivo queda registrado antes de devolver null.
 * ---
 * H08 PART 2 (S009, English-music bias fix + diagnostic
 * instrumentation in S010) -- "Radio": given the artist that was
 * playing, suggests a related one, to continue streaming playback
 * once the queue runs out and repeat is off (PlayerManager).
 *
 * Never throws to the caller -- any network failure, parse failure, or
 * simply "MusicBrainz has no genres for this artist" is treated the
 * same as "no suggestion", same pattern as CoverArtRepository -- but
 * now the reason is logged before returning null.
 */
@Singleton
class RadioRepository @Inject constructor(
    private val musicBrainzApiService: MusicBrainzApiService,
    @ApplicationContext private val appContext: Context,
    private val storageManager: StorageManager,
) {
    suspend fun suggestRelatedArtist(sourceArtist: String): String? {
        if (sourceArtist.isBlank() || isPlaceholderArtist(sourceArtist)) {
            log("suggestRelatedArtist('$sourceArtist') -- origen vacío o placeholder, se descarta sin buscar")
            return null
        }
        return try {
            val sourceMbid = musicBrainzApiService
                .searchArtists(query = buildArtistQuery(sourceArtist))
                .artists
                .firstOrNull()
                ?.id
            if (sourceMbid == null) {
                log("suggestRelatedArtist('$sourceArtist') -- MusicBrainz no encontró NINGÚN artista con ese nombre (searchArtists vacío)")
                return null
            }

            val sourceDetail = musicBrainzApiService.lookupArtist(sourceMbid)
            val genres = sourceDetail.genres
                .map { it.name }
                .filter { it.isNotBlank() }
            if (genres.isEmpty()) {
                log("suggestRelatedArtist('$sourceArtist', mbid=$sourceMbid) -- encontrado en MusicBrainz pero SIN géneros propios (inc=genres vacío) -- eslabón roto")
                return null
            }
            val chosenGenre = genres.random()
            val sourceCountry = sourceDetail.country?.trim()?.ifBlank { null }

            val countryScopedCandidates = if (sourceCountry != null) {
                findCandidates(chosenGenre, sourceMbid, sourceArtist, sourceCountry)
            } else {
                emptyList()
            }

            val candidates = if (countryScopedCandidates.isNotEmpty()) {
                countryScopedCandidates
            } else {
                if (sourceCountry != null) {
                    log("suggestRelatedArtist('$sourceArtist', género='$chosenGenre', país=$sourceCountry) -- 0 candidatos CON país, reintentando solo por género")
                }
                findCandidates(chosenGenre, sourceMbid, sourceArtist, countryCode = null)
            }

            val chosen = candidates.randomOrNull()
            if (chosen == null) {
                log("suggestRelatedArtist('$sourceArtist', género='$chosenGenre', país=$sourceCountry) -- 0 candidatos NI con país NI sin él -- eslabón roto")
            } else {
                log("suggestRelatedArtist('$sourceArtist', género='$chosenGenre', país=$sourceCountry) -> '$chosen' (${candidates.size} candidatos, filtrado por país: ${countryScopedCandidates.isNotEmpty()})")
            }
            chosen
        } catch (e: Exception) {
            log("suggestRelatedArtist('$sourceArtist') -- EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}")
            null
        }
    }

    private suspend fun findCandidates(
        genre: String,
        excludeMbid: String,
        sourceArtist: String,
        countryCode: String?,
    ): List<String> =
        musicBrainzApiService
            .searchArtists(
                query = buildRelatedQuery(genre, excludeMbid, countryCode),
                limit = 10,
            )
            .artists
            .filter {
                !it.name.equals(sourceArtist, ignoreCase = true) &&
                    !isPlaceholderArtist(it.name)
            }
            .map { it.name }

    private fun log(line: String) = RadioDebugLogger.log(appContext, storageManager, line)

    /**
     * H08 -- descarta entidades "cajón de sastre" de MusicBrainz que
     * no son un artista real, no tienen géneros propios, y por tanto
     * rompen la cadena de "relacionado" en cuanto se eligen (causa
     * raíz confirmada en pruebas reales, S009: la Radio se paró en 3
     * temas porque MusicBrainz sugirió "Various Artists", que no
     * tiene géneros -- la siguiente búsqueda falló y nadie volvió a
     * intentarlo). "Various Artists" es una entidad real de
     * MusicBrainz (MBID fijo `89ad4ac3-39f7-470e-963a-56509c546377`,
     * usada para créditos de compilaciones), así que puede aparecer
     * legítimamente en resultados de búsqueda por género.
     * ---
     * H08 -- discards MusicBrainz "catch-all" entities that aren't a
     * real artist, have no genres of their own, and therefore break
     * the "related" chain as soon as one gets picked.
     */
    private fun isPlaceholderArtist(name: String): Boolean =
        name.equals("Various Artists", ignoreCase = true) ||
            name.equals("[unknown]", ignoreCase = true) ||
            name.equals("[anonymous]", ignoreCase = true) ||
            name.equals("[traditional]", ignoreCase = true)

    private fun buildArtistQuery(artist: String): String {
        fun escape(value: String) = value.replace("\"", "")
        return "artist:\"${escape(artist)}\""
    }

    private fun buildRelatedQuery(genre: String, excludeMbid: String, countryCode: String?): String {
        fun escape(value: String) = value.replace("\"", "")
        val base = "tag:\"${escape(genre)}\" AND NOT arid:$excludeMbid"
        return if (countryCode != null) "$base AND country:${escape(countryCode)}" else base
    }
}

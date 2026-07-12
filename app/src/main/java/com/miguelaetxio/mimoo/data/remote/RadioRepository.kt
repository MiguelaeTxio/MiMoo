package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import com.miguelaetxio.mimoo.data.download.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Género + país fijados UNA SOLA VEZ al arrancar una sesión de Radio
 * (S010, rediseño de sesión-ancla -- petición explícita de Miguel
 * Ángel). Se calcula del PRIMER artista (el que arrancó la Radio) y no
 * se vuelve a tocar mientras dure la sesión, aunque se vayan
 * encadenando pistas nuevas.
 * ---
 * Genre + country fixed ONCE when a Radio session starts (S010,
 * anchor-session redesign). Computed from the FIRST artist and never
 * recalculated for the rest of the session, no matter how many tracks
 * get chained afterward.
 */
data class RadioAnchor(val genre: String, val country: String?)

/**
 * H08 PARTE 2 (S009, fix de sesgo hacia música inglesa e
 * instrumentación de diagnóstico en S010, REDISEÑO DE ANCLA DE SESIÓN
 * en S010 continuación) -- "Radio": dado el artista que estaba
 * sonando, sugiere otro relacionado, para continuar la reproducción en
 * streaming cuando la cola se queda sin nada más y el cíclico está
 * desactivado (PlayerManager). Decisión de diseño explícita de Miguel
 * Ángel: MusicBrainz (géneros compartidos), no el Mix automático de
 * YouTube -- descartado por inestabilidad documentada de yt-dlp en esa
 * área (ver ANNEX_H08.md).
 *
 * BUG REAL corregido en S010 (segunda vuelta, reportado por Miguel
 * Ángel con caso concreto: Radio arrancada con Jeff Mills -- techno --
 * terminó en Led Zeppelin -- rock). Causa raíz: el diseño anterior
 * volvía a elegir un género AL AZAR del artista recién añadido en
 * CADA salto de la cadena (Jeff Mills -> género de Underground
 * Resistance -> género de Wink -> ...) -- una pista puede compartir
 * varios géneros por fusión/etiquetado cruzado, así que el género
 * "deriva" salto a salto y varios saltos después ya no tiene nada que
 * ver con el género original. País tampoco basta por sí solo: Miguel
 * Ángel señaló explícitamente que "España" no es un género -- Rocío
 * Jurado, La Pantoja, Radio Futura y Loquillo son todos españoles pero
 * musicalmente no tienen nada que ver entre sí.
 *
 * Solución: el género Y el país se fijan UNA SOLA VEZ, del PRIMER
 * artista que arrancó la Radio (ver RadioAnchor, resolveAnchor()), y
 * TODOS los saltos posteriores de la misma sesión buscan candidatos
 * con ese mismo género+país fijo -- nunca se recalculan a partir de un
 * artista intermedio. Esto es justo lo que pidió Miguel Ángel: "la
 * relación tiene que ir con el primer tema con el que se inicia la
 * radio".
 *
 * Algoritmo:
 *   1. resolveAnchor(artista) -- SOLO al arrancar la sesión (PlayerManager
 *      cachea el resultado): busca el artista en MusicBrainz -> MBID,
 *      consulta sus géneros e idioma/país, elige UN género al azar de
 *      esa lista. Ese (género, país) es el ancla de TODA la sesión.
 *   2. suggestRelatedArtist(ancla, excluidos) -- en CADA salto: busca
 *      artistas con el género del ancla Y su país (reintento sin país
 *      si no hay candidatos), excluye los ya usados en esta sesión
 *      (para variar) y los "cajón de sastre" (Various Artists...),
 *      elige uno al azar entre los que queden.
 *
 * El nombre elegido NO se reproduce directamente desde MusicBrainz
 * (que no aloja audio) -- quien llama (PlayerManager) lo busca
 * después con el motor gratuito ya existente
 * (ExternalLinkResolver.searchYoutube()), igual que cualquier otra
 * búsqueda de la app.
 *
 * Instrumentado con RadioDebugLogger (S010) -- cada fallo se registra
 * con el motivo exacto en radio_relacionados_debug.txt antes de
 * devolver null; nunca lanza excepción hacia quien llama.
 * ---
 * H08 PART 2 -- "Radio": given the artist that was playing, suggests a
 * related one, to continue streaming playback once the queue runs out
 * and repeat is off.
 *
 * REAL BUG fixed in S010 (second round, reported by Miguel Ángel with
 * a concrete case: Radio started with Jeff Mills -- techno -- ended up
 * at Led Zeppelin -- rock). Root cause: the previous design re-picked
 * a RANDOM genre from the just-added artist on EVERY hop of the chain
 * -- a track can share several genres via fusion/cross-tagging, so the
 * genre "drifts" hop by hop and several hops later has nothing to do
 * with the original genre anymore. Country alone isn't enough either:
 * Miguel Ángel explicitly pointed out that "Spain" isn't a genre.
 *
 * Fix: genre AND country are fixed ONCE, from the FIRST artist that
 * started Radio, and EVERY later hop in the same session searches for
 * candidates with that same fixed genre+country -- never recalculated
 * from an intermediate artist. This is exactly what Miguel Ángel asked
 * for: the relation has to go with the first track Radio started with.
 */
@Singleton
class RadioRepository @Inject constructor(
    private val musicBrainzApiService: MusicBrainzApiService,
    @ApplicationContext private val appContext: Context,
    private val storageManager: StorageManager,
) {
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
            log("resolveAnchor('$sourceArtist') -> ancla fijada para toda la sesión: género='$chosenGenre', país=$sourceCountry")
            RadioAnchor(genre = chosenGenre, country = sourceCountry)
        } catch (e: Exception) {
            log("resolveAnchor('$sourceArtist') -- EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * Se llama en CADA salto de la cadena -- siempre busca con el
     * mismo (género, país) del ancla, nunca recalcula nada a partir
     * del artista recién añadido. `excludeArtists` son los nombres ya
     * usados en esta sesión (comparación sin mayúsculas/minúsculas),
     * para no repetir siempre el mismo puñado de candidatos.
     * ---
     * Called on EVERY hop of the chain -- always searches with the
     * same anchor (genre, country), never recalculates anything from
     * the just-added artist. `excludeArtists` are the names already
     * used this session, to avoid repeating the same handful of
     * candidates.
     */
    suspend fun suggestRelatedArtist(anchor: RadioAnchor, excludeArtists: Set<String>): String? {
        val excludeLower = excludeArtists.map { it.lowercase() }.toSet()

        val countryScoped = if (anchor.country != null) {
            findCandidates(anchor.genre, anchor.country, excludeLower)
        } else {
            emptyList()
        }

        val candidates = if (countryScoped.isNotEmpty()) {
            countryScoped
        } else {
            if (anchor.country != null) {
                log("suggestRelatedArtist(género='${anchor.genre}', país=${anchor.country}) -- 0 candidatos CON país, reintentando solo por género")
            }
            findCandidates(anchor.genre, countryCode = null, excludeLower)
        }

        val chosen = candidates.randomOrNull()
        if (chosen == null) {
            log("suggestRelatedArtist(género='${anchor.genre}', país=${anchor.country}) -- 0 candidatos NI con país NI sin él (tras excluir ${excludeArtists.size} ya usados) -- eslabón roto")
        } else {
            log("suggestRelatedArtist(género='${anchor.genre}', país=${anchor.country}) -> '$chosen' (${candidates.size} candidatos, filtrado por país: ${countryScoped.isNotEmpty()})")
        }
        return chosen
    }

    private suspend fun findCandidates(
        genre: String,
        countryCode: String?,
        excludeLower: Set<String>,
    ): List<String> = try {
        musicBrainzApiService
            .searchArtists(query = buildGenreQuery(genre, countryCode), limit = 10)
            .artists
            .map { it.name }
            .filter { it.lowercase() !in excludeLower && !isPlaceholderArtist(it) }
    } catch (e: Exception) {
        log("findCandidates(género='$genre', país=$countryCode) -- EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}")
        emptyList()
    }

    private fun buildGenreQuery(genre: String, countryCode: String?): String {
        fun escape(value: String) = value.replace("\"", "")
        val base = "tag:\"${escape(genre)}\""
        return if (countryCode != null) "$base AND country:${escape(countryCode)}" else base
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

package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzTrack
import com.miguelaetxio.mimoo.data.remote.dto.TrackDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * One MusicBrainz tracklist entry matched (or not) against a YouTube
 * video by duration (Hito 05).
 * ---
 * Una entrada del tracklist de MusicBrainz emparejada (o no) con un
 * vídeo de YouTube por duración (Hito 05).
 */
data class AlbumTrackMatch(
    val position: Int,
    val mbTitle: String,
    val mbDurationSeconds: Int?,
    val matchedTrack: TrackDto?,
    val isAutoMatched: Boolean,
    // PASO 6e: distingue "no se encontró nada en YouTube" (matchError
    // null, matchedTrack null) de "no se pudo ni intentar" (matchError
    // con el motivo real -- cuota agotada, fallo de red...). Antes
    // ambos casos se veían igual ("Sin emparejar"), lo que ocultó el
    // agotamiento de cuota real de la sesión "1 de 11 pistas".
    val matchError: String? = null,
)

/**
 * One MusicBrainz release candidate shown in the album picker (PASO
 * 6d) before committing to fetch its tracklist. `coverArtUrl` is
 * built directly, same as CoverArtRepository — no need for a second
 * network call or JSON parse, Cover Art Archive 307-redirects on
 * success and 404s otherwise, which Coil already handles as a normal
 * image-load failure.
 * ---
 * Un release candidato de MusicBrainz mostrado en el selector de
 * álbum (PASO 6d) antes de comprometerse a pedir su tracklist.
 * `coverArtUrl` se construye directamente, igual que en
 * CoverArtRepository — no hace falta una segunda llamada de red ni
 * parsear JSON, Cover Art Archive redirige (307) si hay éxito y
 * devuelve 404 si no, que Coil ya trata como un fallo de carga de
 * imagen normal.
 */
data class AlbumCandidate(
    val mbid: String,
    val title: String,
    val artist: String?,
    val year: String?,
    val coverArtUrl: String,
)

/**
 * Searches MusicBrainz release candidates for an artist/album query
 * and, once one is chosen, matches its tracklist to YouTube videos by
 * closest duration (Hito 05). No session cache here, unlike
 * CoverArtRepository — searching an album is a deliberate, one-off
 * user action, not something re-triggered on every recomposition of
 * a list.
 * ---
 * Busca releases candidatos de MusicBrainz para una consulta de
 * artista/álbum y, una vez elegido uno, empareja su tracklist con
 * vídeos de YouTube por cercanía de duración (Hito 05). Sin caché de
 * sesión aquí, a diferencia de CoverArtRepository — buscar un álbum
 * es una acción deliberada y puntual del usuario, no algo que se
 * repita en cada recomposición de una lista.
 */
@Singleton
class AlbumMatchRepository @Inject constructor(
    private val musicBrainzApiService: MusicBrainzApiService,
    private val youTubeRepository: YouTubeRepository,
) {
    companion object {
        /**
         * Tolerance for treating a duration match as automatic/
         * trustworthy, in seconds. Chosen as a reasonable starting
         * point (covers small differences from intros/outros or
         * fade edits between the studio recording and a given
         * YouTube upload) rather than validated against a corpus of
         * real albums — revisit with Miguel Ángel if PASO 6
         * (verificación funcional) shows it's too strict o too
         * loose in practice.
         * ---
         * Tolerancia para considerar un emparejamiento de duración
         * como automático/fiable, en segundos. Elegida como punto de
         * partida razonable (cubre pequeñas diferencias por
         * intros/outros o ediciones de fade entre la grabación de
         * estudio y una subida concreta de YouTube) en vez de
         * validada contra un corpus de álbumes reales — revisar con
         * Miguel Ángel si el PASO 6 (verificación funcional) muestra
         * que es demasiado estricta o demasiado laxa en la práctica.
         */
        const val DURATION_TOLERANCE_SECONDS = 7

        /**
         * Cuántos releases candidatos se piden a MusicBrainz por
         * búsqueda (PASO 6d). Petición explícita de Miguel Ángel:
         * "las quince mejores coincidencias... diez, quince, veinte,
         * según ocupen" — 20 es el límite superior de ese rango, y
         * MusicBrainz ya devuelve los resultados ordenados por
         * relevancia (motor Lucene), así que pedir de más no cambia
         * cuáles son los mejores, solo cuántos se listan.
         */
        const val CANDIDATE_SEARCH_LIMIT = 20

        /**
         * Tolerancia de duración para el emparejamiento vía playlist
         * (PASO 6e), más laxa que DURATION_TOLERANCE_SECONDS porque
         * aquí no se busca la mejor coincidencia entre varios
         * candidatos -- se confía en el orden de una playlist ya
         * curada, y esta tolerancia es solo una comprobación de
         * cordura para detectar un desalineamiento grave de posición
         * (p. ej. bonus tracks intercalados en la playlist real que
         * no están en el tracklist de MusicBrainz).
         */
        const val PLAYLIST_DURATION_SANITY_TOLERANCE_SECONDS = 20
    }

    /**
     * Returns up to CANDIDATE_SEARCH_LIMIT release candidates matching
     * artist/album (PASO 6d) — the user picks one before any tracklist
     * or YouTube lookup happens. Propagates on a genuine MusicBrainz
     * failure (network/parsing); an empty result list is not an
     * error, the caller shows "sin resultados".
     * ---
     * Devuelve hasta CANDIDATE_SEARCH_LIMIT releases candidatos que
     * coinciden con artista/álbum (PASO 6d) — el usuario elige uno
     * antes de que se consulte tracklist o YouTube. Propaga en un
     * fallo real de MusicBrainz (red/parseo); una lista vacía no es un
     * error, quien llama muestra "sin resultados".
     */
    suspend fun searchAlbumCandidates(
        artist: String?,
        album: String?,
    ): List<AlbumCandidate> {
        val queryClauses = buildList {
            if (!artist.isNullOrBlank()) add("artist:\"${escape(artist)}\"")
            if (!album.isNullOrBlank()) add("release:\"${escape(album)}\"")
        }
        val query = queryClauses.joinToString(" AND ")
        return musicBrainzApiService
            .searchReleases(query = query, limit = CANDIDATE_SEARCH_LIMIT)
            .releases
            .map { release ->
                AlbumCandidate(
                    mbid = release.id,
                    title = release.title ?: "(sin título)",
                    artist = release.artistCredit.firstOrNull()?.name,
                    year = release.date?.take(4)?.takeIf { it.length == 4 },
                    coverArtUrl = "https://coverartarchive.org/release/${release.id}/front",
                )
            }
    }

    /**
     * Returns the tracklist of the release already chosen by the user
     * (PASO 6d), each entry matched against YouTube.
     *
     * PASO 6e — estrategia de playlist primero: en vez de una
     * search.list (100 unidades de cuota) por cada pista del álbum,
     * primero se intenta encontrar una playlist de YouTube con el
     * álbum completo (1 search.list de tipo playlist + 1
     * playlistItems.list = 100 + 1 unidades, TOTAL fijo por álbum,
     * independiente del número de pistas) y se empareja por posición.
     * Solo si no hay playlist utilizable (ninguna encontrada, o con
     * menos pistas que el tracklist real) se cae al emparejamiento
     * pista a pista de antes, que sigue costando N × 100 unidades pero
     * ahora es la red de seguridad, no el camino principal.
     *
     * Petición explícita de Miguel Ángel (2026-07-02, tras ver "1 de
     * 11 pistas emparejadas" en Transformer de Lou Reed): "las
     * canciones están todas en YouTube... hay listas de reproducción
     * de ese disco". Confirmado además que search.list cuesta 100
     * unidades/llamada, no 1 como decía la documentación anterior (ver
     * YouTubeApiService) — de ahí que un álbum de 11 pistas agotara la
     * cuota diaria a media búsqueda.
     * ---
     * Devuelve el tracklist del release ya elegido por el usuario
     * (PASO 6d), cada entrada emparejada contra YouTube.
     *
     * PASO 6e — estrategia de playlist primero: en vez de una
     * search.list (100 unidades de cuota) por cada pista del álbum,
     * primero se intenta encontrar una playlist de YouTube con el
     * álbum completo (1 search.list tipo playlist + 1
     * playlistItems.list = 100 + 1 unidades, TOTAL fijo por álbum,
     * independiente del número de pistas) y se empareja por posición.
     * Solo si no hay playlist utilizable se cae al emparejamiento
     * pista a pista de antes.
     */
    suspend fun matchAlbumTracks(
        mbid: String,
        artist: String?,
        album: String,
        youtubeApiKey: String,
    ): List<AlbumTrackMatch> {
        val tracklist = musicBrainzApiService.lookupRelease(mbid).media
            .flatMap { it.tracks }
            .sortedBy { it.position }

        val playlistQuery = if (!artist.isNullOrBlank()) "$artist $album" else album
        val playlistMatches = try {
            matchFromPlaylist(playlistQuery, tracklist, youtubeApiKey)
        } catch (e: Exception) {
            if (isQuotaError(e)) {
                // Sin sentido caer al fallback pista a pista si ya no
                // queda cuota -- volvería a fallar en la primera
                // pista. Propagar para que la UI muestre el motivo
                // real en vez de "Sin emparejar" en las 11 filas.
                throw e
            }
            null
        }
        if (playlistMatches != null) return playlistMatches

        return matchTrackByTrack(tracklist, artist, youtubeApiKey)
    }

    /**
     * Attempts the playlist-first match. Returns null (not an error)
     * when there is no usable playlist — either MusicBrainz/YouTube
     * found none, or the playlist found has fewer items than the real
     * tracklist (probably not the full album — a fan edit, a single,
     * a reaction video, etc.).
     * ---
     * Intenta el emparejamiento vía playlist. Devuelve null (no es un
     * error) cuando no hay playlist utilizable — o no se encontró
     * ninguna, o la encontrada tiene menos items que el tracklist real
     * (probablemente no es el álbum completo -- un fan edit, un
     * sencillo, un vídeo de reacción...).
     */
    private suspend fun matchFromPlaylist(
        query: String,
        tracklist: List<MusicBrainzTrack>,
        youtubeApiKey: String,
    ): List<AlbumTrackMatch>? {
        val playlistId = youTubeRepository.searchPlaylist(query, youtubeApiKey)
            ?: return null
        val playlistTracks = youTubeRepository.getPlaylistTracks(playlistId, youtubeApiKey)
        if (playlistTracks.size < tracklist.size) return null

        return tracklist.mapIndexed { index, mbTrack ->
            val candidate = playlistTracks.getOrNull(index)
            val mbDurationSeconds = mbTrack.length?.let { it / 1000 }
            val isAutoMatched = candidate != null && (
                mbDurationSeconds == null ||
                    abs(candidate.durationSeconds - mbDurationSeconds) <=
                        PLAYLIST_DURATION_SANITY_TOLERANCE_SECONDS
            )
            AlbumTrackMatch(
                position = mbTrack.position,
                mbTitle = mbTrack.title,
                mbDurationSeconds = mbDurationSeconds,
                matchedTrack = candidate,
                isAutoMatched = isAutoMatched,
            )
        }
    }

    /**
     * Fallback: one search.list call per track (100 units each — see
     * YouTubeApiService), same algorithm as before PASO 6e. If a
     * track search fails with a quota error, every remaining track is
     * marked with matchError instead of silently retrying (which
     * would just fail again) — surfaces the real cause instead of
     * looking like "no encontrado" for the rest of the album.
     * ---
     * Red de seguridad: una llamada search.list por pista (100
     * unidades cada una — ver YouTubeApiService), mismo algoritmo que
     * antes del PASO 6e. Si la búsqueda de una pista falla por cuota,
     * el resto de pistas se marca con matchError en vez de
     * reintentar en silencio (que solo volvería a fallar) — muestra
     * la causa real en vez de parecer "no encontrado" en el resto del
     * álbum.
     */
    private suspend fun matchTrackByTrack(
        tracklist: List<MusicBrainzTrack>,
        artist: String?,
        youtubeApiKey: String,
    ): List<AlbumTrackMatch> {
        var quotaExhausted = false

        return tracklist.map { mbTrack ->
            val mbDurationSeconds = mbTrack.length?.let { it / 1000 }

            if (quotaExhausted) {
                return@map AlbumTrackMatch(
                    position = mbTrack.position,
                    mbTitle = mbTrack.title,
                    mbDurationSeconds = mbDurationSeconds,
                    matchedTrack = null,
                    isAutoMatched = false,
                    matchError = "Cuota de YouTube agotada por hoy.",
                )
            }

            val searchQuery = if (!artist.isNullOrBlank()) {
                "$artist ${mbTrack.title}"
            } else {
                mbTrack.title
            }

            var trackError: String? = null
            val candidates = try {
                youTubeRepository.search(query = searchQuery, apiKey = youtubeApiKey)
            } catch (e: Exception) {
                if (isQuotaError(e)) {
                    quotaExhausted = true
                    trackError = "Cuota de YouTube agotada por hoy."
                } else {
                    trackError = e.message ?: "Error al buscar en YouTube"
                }
                emptyList()
            }

            val best = if (mbDurationSeconds != null) {
                candidates.minByOrNull { abs(it.durationSeconds - mbDurationSeconds) }
            } else {
                // No reliable duration to compare against — take the
                // first result rather than guessing, and always flag
                // it for manual review (isAutoMatched = false).
                candidates.firstOrNull()
            }

            val isAutoMatched = best != null && mbDurationSeconds != null &&
                abs(best.durationSeconds - mbDurationSeconds) <= DURATION_TOLERANCE_SECONDS

            AlbumTrackMatch(
                position = mbTrack.position,
                mbTitle = mbTrack.title,
                mbDurationSeconds = mbDurationSeconds,
                matchedTrack = best,
                isAutoMatched = isAutoMatched,
                matchError = trackError,
            )
        }
    }

    /**
     * Detects a YouTube quota error from the wrapped exception message
     * (see YouTubeRepository.wrapHttpErrors) — checks Google's real
     * `reason` values, not just the HTTP status, since 403 also covers
     * unrelated causes (bad API key, restricted key, etc.).
     * ---
     * Detecta un error de cuota de YouTube a partir del mensaje de la
     * excepción envuelta (ver YouTubeRepository.wrapHttpErrors) —
     * comprueba los valores reales de `reason` de Google, no solo el
     * estado HTTP, ya que 403 también cubre causas no relacionadas
     * (API key incorrecta, key restringida, etc.).
     */
    private fun isQuotaError(e: Exception): Boolean {
        val message = e.message ?: return false
        return message.contains("quotaExceeded", ignoreCase = true) ||
            message.contains("dailyLimitExceeded", ignoreCase = true) ||
            message.contains("rateLimitExceeded", ignoreCase = true)
    }

    private fun escape(value: String) = value.replace("\"", "")
}

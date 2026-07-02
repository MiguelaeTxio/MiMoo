package com.miguelaetxio.mimoo.data.remote

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
     * (PASO 6d), each entry matched to its best YouTube candidate if
     * one was found within tolerance. Never throws for individual
     * track match failures — a track search error or "no candidates"
     * just yields matchedTrack = null for that entry, letting the rest
     * of the album still be usable.
     * ---
     * Devuelve el tracklist del release ya elegido por el usuario
     * (PASO 6d), cada entrada emparejada con su mejor candidato de
     * YouTube si se encontró uno dentro de tolerancia. Nunca lanza
     * excepción por fallos de emparejamiento de una pista individual —
     * un error de búsqueda o "sin candidatos" simplemente deja
     * matchedTrack = null para esa entrada, dejando el resto del álbum
     * utilizable.
     */
    suspend fun matchAlbumTracks(
        mbid: String,
        artist: String?,
        youtubeApiKey: String,
    ): List<AlbumTrackMatch> {
        val tracklist = musicBrainzApiService.lookupRelease(mbid).media
            .flatMap { it.tracks }
            .sortedBy { it.position }

        return tracklist.map { mbTrack ->
            val mbDurationSeconds = mbTrack.length?.let { it / 1000 }
            val searchQuery = if (!artist.isNullOrBlank()) {
                "$artist ${mbTrack.title}"
            } else {
                mbTrack.title
            }
            val candidates = try {
                youTubeRepository.search(
                    query = searchQuery,
                    apiKey = youtubeApiKey,
                )
            } catch (e: Exception) {
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
            )
        }
    }

    private fun escape(value: String) = value.replace("\"", "")
}

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
 * Searches a full album on MusicBrainz and matches each of its
 * tracks to a YouTube video by closest duration (Hito 05). No session
 * cache here, unlike CoverArtRepository — searching an album is a
 * deliberate, one-off user action, not something re-triggered on
 * every recomposition of a list.
 * ---
 * Busca un álbum completo en MusicBrainz y empareja cada una de sus
 * pistas con un vídeo de YouTube por cercanía de duración (Hito 05).
 * Sin caché de sesión aquí, a diferencia de CoverArtRepository —
 * buscar un álbum es una acción deliberada y puntual del usuario, no
 * algo que se repita en cada recomposición de una lista.
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
         * (verificación funcional) shows it's too strict or too
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
    }

    /**
     * Returns the album's tracklist, each entry matched to its best
     * YouTube candidate if one was found within tolerance. Never
     * throws for individual track match failures — a track search
     * error or "no candidates" just yields matchedTrack = null for
     * that entry, letting the rest of the album still be usable.
     * Only a MusicBrainz search/lookup failure (no release found at
     * all) propagates, since without a tracklist there is nothing to
     * match.
     * ---
     * Devuelve el tracklist del álbum, cada entrada emparejada con su
     * mejor candidato de YouTube si se encontró uno dentro de
     * tolerancia. Nunca lanza excepción por fallos de emparejamiento
     * de una pista individual — un error de búsqueda o "sin
     * candidatos" simplemente deja matchedTrack = null para esa
     * entrada, dejando el resto del álbum utilizable. Solo un fallo
     * de búsqueda/lookup en MusicBrainz (ningún release encontrado)
     * se propaga, ya que sin tracklist no hay nada que emparejar.
     */
    suspend fun matchAlbum(
        artist: String,
        album: String,
        youtubeApiKey: String,
    ): List<AlbumTrackMatch> {
        val query = "artist:\"${escape(artist)}\" AND release:\"${escape(album)}\""
        val mbid = musicBrainzApiService.searchReleases(query = query)
            .releases
            .firstOrNull()
            ?.id
            ?: throw NoSuchElementException(
                "No se encontró el álbum \"$album\" de \"$artist\" en MusicBrainz."
            )

        val tracklist = musicBrainzApiService.lookupRelease(mbid).media
            .flatMap { it.tracks }
            .sortedBy { it.position }

        return tracklist.map { mbTrack ->
            val mbDurationSeconds = mbTrack.length?.let { it / 1000 }
            val candidates = try {
                youTubeRepository.search(
                    query = "$artist ${mbTrack.title}",
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

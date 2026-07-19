package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.local.repository.ArtistDisambiguationRepository
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzArtistSummary
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzReleaseGroup
import com.miguelaetxio.mimoo.util.SearchNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resultado de resolver un nombre de artista a un MBID concreto (H12).
 * Distingue el caso feliz (un único candidato tras normalizar) del
 * caso de homónimos reales, que ArtistScreen debe resolver mostrando
 * un selector antes de continuar -- ver diseño S017,
 * DOCS/ANNEX_H12.md.
 * ---
 * Result of resolving an artist name to a concrete MBID (H12).
 * Distinguishes the happy path (a single candidate after
 * normalization) from the real-homonym case, which ArtistScreen must
 * resolve by showing a picker before continuing -- see the S017
 * design, DOCS/ANNEX_H12.md.
 */
sealed class ArtistResolution {
    data class Resolved(val mbid: String, val canonicalName: String) : ArtistResolution()
    data class NeedsDisambiguation(
        val normalizedNameKey: String,
        val candidates: List<MusicBrainzArtistSummary>,
    ) : ArtistResolution()
    object NotFound : ArtistResolution()
}

/**
 * Resuelve nombres de artista a MBIDs de MusicBrainz y lista sus
 * álbumes/sencillos (H12) -- capa nueva que complementa a
 * AlbumMatchRepository (que resuelve RELEASES por artista+álbum, no
 * ARTISTAS por nombre). Reutiliza `searchArtists`/`lookupArtist` de
 * MusicBrainzApiService, ya existentes desde H08 PARTE 2 (Radio), y
 * el patrón de dos mecanismos de homónimos cerrado en S017:
 * normalización (`SearchNormalizer.normalizeArtistName`, sin tabla)
 * frente a homónimos reales (`ArtistDisambiguationRepository`, Room).
 * ---
 * Resolves artist names to MusicBrainz MBIDs and lists their albums/
 * singles (H12) -- a new layer complementing AlbumMatchRepository
 * (which resolves RELEASES by artist+album, not ARTISTS by name).
 * Reuses `searchArtists`/`lookupArtist` from MusicBrainzApiService,
 * already existing since H08 PART 2 (Radio), and the two-mechanism
 * homonym pattern closed in S017: normalization
 * (`SearchNormalizer.normalizeArtistName`, no table) versus real
 * homonyms (`ArtistDisambiguationRepository`, Room).
 */
@Singleton
class ArtistDirectoryRepository @Inject constructor(
    private val musicBrainzApiService: MusicBrainzApiService,
    private val artistDisambiguationRepository: ArtistDisambiguationRepository,
) {
    companion object {
        /**
         * Cuántos candidatos se piden a MusicBrainz al buscar un
         * nombre de artista (S018). Menor que CANDIDATE_SEARCH_LIMIT
         * de AlbumMatchRepository (20) -- aquí solo interesan los
         * candidatos con el MISMO nombre normalizado, no una lista
         * amplia de relevancia aproximada, así que un límite más
         * pequeño ya cubre el caso real de homónimos (2-3 artistas
         * distintos, nunca decenas).
         */
        const val ARTIST_SEARCH_LIMIT = 10
    }

    /**
     * Resuelve `artistName` a un MBID -- primero contra la elección ya
     * persistida (`ArtistDisambiguationRepository`), y solo si no hay
     * ninguna, contra MusicBrainz. Entre los resultados de
     * `searchArtists` (que devuelve por relevancia, no por coincidencia
     * exacta) solo cuentan como candidatos de ESTA página los que
     * normalizan al mismo `normalizedNameKey` -- así "Bon Jovi" no
     * arrastra resultados de relevancia parcial sin relación real.
     * ---
     * Resolves `artistName` to an MBID -- first against an already
     * persisted choice (`ArtistDisambiguationRepository`), and only if
     * there is none, against MusicBrainz. Among `searchArtists`'
     * results (returned by relevance, not exact match), only the ones
     * that normalize to the same `normalizedNameKey` count as
     * candidates for THIS page -- so "Bon Jovi" doesn't drag in
     * partial-relevance results with no real relation.
     */
    suspend fun resolveArtist(artistName: String): ArtistResolution {
        val normalizedNameKey = SearchNormalizer.normalizeArtistName(artistName)

        val savedChoice = artistDisambiguationRepository.getChoice(normalizedNameKey)
        if (savedChoice != null) {
            val canonicalName = musicBrainzApiService
                .lookupArtist(savedChoice.chosenMbid)
                .name
            return ArtistResolution.Resolved(savedChoice.chosenMbid, canonicalName)
        }

        val candidates = musicBrainzApiService
            .searchArtists(query = artistName, limit = ARTIST_SEARCH_LIMIT)
            .artists
            .filter { SearchNormalizer.normalizeArtistName(it.name) == normalizedNameKey }
            .distinctBy { it.id }

        return when (candidates.size) {
            0 -> ArtistResolution.NotFound
            1 -> ArtistResolution.Resolved(candidates.first().id, candidates.first().name)
            else -> ArtistResolution.NeedsDisambiguation(normalizedNameKey, candidates)
        }
    }

    /**
     * Persiste la elección del usuario tras el diálogo de
     * desambiguación (S018, ver ArtistScreen) -- las siguientes visitas
     * a este nombre normalizado resuelven directo, sin volver a
     * preguntar.
     * ---
     * Persists the user's choice after the disambiguation dialog
     * (S018, see ArtistScreen) -- future visits to this normalized name
     * resolve directly, without asking again.
     */
    suspend fun saveDisambiguationChoice(normalizedNameKey: String, chosenMbid: String) {
        artistDisambiguationRepository.saveChoice(normalizedNameKey, chosenMbid)
    }

    /** Álbumes del artista (release-groups tipo "album"), H12. */
    suspend fun getAlbums(artistMbid: String): List<MusicBrainzReleaseGroup> =
        musicBrainzApiService
            .browseReleaseGroupsByArtist(artistMbid, type = "album")
            .releaseGroups

    /** Sencillos sueltos del artista (release-groups tipo "single"), H12. */
    suspend fun getSingles(artistMbid: String): List<MusicBrainzReleaseGroup> =
        musicBrainzApiService
            .browseReleaseGroupsByArtist(artistMbid, type = "single")
            .releaseGroups

    /**
     * Número de pistas de un álbum, solo vía MusicBrainz (sin
     * YouTube) -- usado por ArtistScreen para el conteo "álbum
     * completo / álbum parcial" (S018). Resuelve una release
     * representativa del release-group y cuenta su tracklist. Null si
     * el release-group no tiene ninguna release resoluble o falla la
     * consulta -- el llamante trata "desconocido" como "no marcar
     * completo", nunca como 0 (0 pistas sería un dato falso, no una
     * ausencia real de información).
     * ---
     * Track count for an album, MusicBrainz only (no YouTube) -- used
     * by ArtistScreen for the "complete album / partial album" count
     * (S018). Resolves a representative release from the release-group
     * and counts its tracklist. Null if the release-group has no
     * resolvable release or the lookup fails -- the caller treats
     * "unknown" as "don't mark complete", never as 0 (0 tracks would be
     * a false data point, not a real absence of information).
     */
    suspend fun getTrackCount(releaseGroupMbid: String): Int? =
        try {
            val releaseId = musicBrainzApiService
                .browseReleasesByReleaseGroup(releaseGroupMbid)
                .releases
                .firstOrNull()
                ?.id
                ?: return null
            musicBrainzApiService.lookupRelease(releaseId).media
                .sumOf { it.tracks.size }
        } catch (e: Exception) {
            null
        }
}

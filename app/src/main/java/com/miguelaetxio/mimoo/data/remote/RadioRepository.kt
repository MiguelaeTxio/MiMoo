package com.miguelaetxio.mimoo.data.remote

import javax.inject.Inject
import javax.inject.Singleton

/**
 * H08 PARTE 2 (S009) -- "Radio": dado el artista que estaba sonando,
 * sugiere otro relacionado, para continuar la reproducción en
 * streaming cuando la cola se queda sin nada más y el cíclico está
 * desactivado (PlayerManager). Decisión de diseño explícita de
 * Miguel Ángel: MusicBrainz (géneros compartidos), no el Mix
 * automático de YouTube -- descartado por inestabilidad documentada
 * de yt-dlp en esa área (ver ANNEX_H08.md).
 *
 * Algoritmo (deliberadamente simple, sin pretender ser un motor de
 * recomendación real):
 *   1. Buscar el artista de origen en MusicBrainz -> MBID.
 *   2. Consultar sus géneros (`inc=genres`).
 *   3. Buscar otros artistas con uno de esos géneros
 *      (`tag:"<género>" AND NOT arid:<origen>`).
 *   4. Elegir uno al azar entre los candidatos -- varía en cada
 *      disparo en vez de ser siempre el mismo, y evita sugerir el
 *      propio artista de origen.
 *
 * El nombre elegido NO se reproduce directamente desde MusicBrainz
 * (que no aloja audio) -- quien llama (PlayerManager) lo busca
 * después con el motor gratuito ya existente
 * (ExternalLinkResolver.searchYoutube()), igual que cualquier otra
 * búsqueda de la app.
 *
 * Nunca lanza excepción -- cualquier fallo de red, de parseo, o
 * simplemente "MusicBrainz no tiene géneros para este artista" se
 * trata igual que "no hay sugerencia", mismo patrón que
 * CoverArtRepository. La Radio es una mejora de la experiencia, no
 * debe poder romper la reproducción si no encuentra nada.
 * ---
 * H08 PARTE 2 (S009) -- "Radio": given the artist that was playing,
 * suggests a related one, to continue streaming playback once the
 * queue runs out and repeat is off (PlayerManager). Explicit design
 * decision from Miguel Ángel: MusicBrainz (shared genres), not
 * YouTube's automatic Mix -- discarded due to yt-dlp's documented
 * instability in that area (see ANNEX_H08.md).
 *
 * Algorithm (deliberately simple, not meant to be a real
 * recommendation engine):
 *   1. Search the source artist on MusicBrainz -> MBID.
 *   2. Look up its genres (`inc=genres`).
 *   3. Search other artists sharing one of those genres
 *      (`tag:"<genre>" AND NOT arid:<source>`).
 *   4. Pick one at random among the candidates -- varies on every
 *      trigger instead of always being the same, and avoids
 *      suggesting the source artist itself.
 *
 * The chosen name is NOT played directly from MusicBrainz (which
 * hosts no audio) -- the caller (PlayerManager) looks it up
 * afterwards with the app's existing free search engine
 * (ExternalLinkResolver.searchYoutube()), same as any other search in
 * the app.
 *
 * Never throws -- any network failure, parse failure, or simply
 * "MusicBrainz has no genres for this artist" is treated the same as
 * "no suggestion", same pattern as CoverArtRepository. Radio is an
 * experience enhancement, it must never be able to break playback by
 * finding nothing.
 */
@Singleton
class RadioRepository @Inject constructor(
    private val musicBrainzApiService: MusicBrainzApiService,
) {
    suspend fun suggestRelatedArtist(sourceArtist: String): String? {
        if (sourceArtist.isBlank() || isPlaceholderArtist(sourceArtist)) return null
        return try {
            val sourceMbid = musicBrainzApiService
                .searchArtists(query = buildArtistQuery(sourceArtist))
                .artists
                .firstOrNull()
                ?.id ?: return null

            val genres = musicBrainzApiService
                .lookupArtist(sourceMbid)
                .genres
                .map { it.name }
                .filter { it.isNotBlank() }
            if (genres.isEmpty()) return null
            val chosenGenre = genres.random()

            val candidates = musicBrainzApiService
                .searchArtists(
                    query = buildRelatedQuery(chosenGenre, sourceMbid),
                    limit = 10,
                )
                .artists
                .filter {
                    !it.name.equals(sourceArtist, ignoreCase = true) &&
                        !isPlaceholderArtist(it.name)
                }
            candidates.randomOrNull()?.name
        } catch (e: Exception) {
            null
        }
    }

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
     * the "related" chain as soon as one gets picked (confirmed root
     * cause in real testing, S009: Radio stopped after 3 tracks
     * because MusicBrainz suggested "Various Artists", which has no
     * genres -- the next search failed and nothing ever retried).
     * "Various Artists" is a real MusicBrainz entity (fixed MBID
     * `89ad4ac3-39f7-470e-963a-56509c546377`, used for compilation
     * credits), so it can legitimately show up in genre-based search
     * results.
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

    private fun buildRelatedQuery(genre: String, excludeMbid: String): String {
        fun escape(value: String) = value.replace("\"", "")
        return "tag:\"${escape(genre)}\" AND NOT arid:$excludeMbid"
    }
}

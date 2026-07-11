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
        if (sourceArtist.isBlank()) return null
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
                .filter { !it.name.equals(sourceArtist, ignoreCase = true) }
            candidates.randomOrNull()?.name
        } catch (e: Exception) {
            null
        }
    }

    private fun buildArtistQuery(artist: String): String {
        fun escape(value: String) = value.replace("\"", "")
        return "artist:\"${escape(artist)}\""
    }

    private fun buildRelatedQuery(genre: String, excludeMbid: String): String {
        fun escape(value: String) = value.replace("\"", "")
        return "tag:\"${escape(genre)}\" AND NOT arid:$excludeMbid"
    }
}

package com.miguelaetxio.mimoo.data.remote

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves album cover art via MusicBrainz + Cover Art Archive
 * (Hito 03, PASO 6), with a fallback to the iTunes Search API when
 * MusicBrainz has no match (S011 -- explicit request from Miguel
 * Ángel: "falta descargar carátula cuando no existe"; before this,
 * an album with no MusicBrainz release simply had no cover forever,
 * with no second source ever attempted).
 *
 * Two-step lookup collapsed into one network call: a MusicBrainz
 * release search by artist+album yields an MBID; the front cover
 * image lives directly at `coverartarchive.org/release/{mbid}/front`,
 * which 307-redirects to the real image on success or 404s if the
 * release has no art (verified against the CAA spec, 2026-07-02).
 * No second API call or JSON parse is needed for that part — Coil
 * follows the redirect itself, and a 404 is just a normal image-load
 * failure that LibraryScreen already falls back from to the YouTube
 * thumbnail.
 *
 * Caching has two layers: a permanent one in Room (`coverArtUrl` on
 * every track of the album, written once resolved — see
 * SearchResultTrackRepository.updateCoverArtForAlbum) and a
 * process-lifetime one here (`sessionCache`) so that within the same
 * app run, an album whose MusicBrainz lookup failed isn't retried on
 * every recomposition before the negative result would otherwise
 * reach Room.
 * ---
 * Resuelve la carátula de un álbum vía MusicBrainz + Cover Art
 * Archive (Hito 03, PASO 6), con respaldo en la API de búsqueda de
 * iTunes cuando MusicBrainz no tiene coincidencia (S011 -- petición
 * explícita de Miguel Ángel: "falta descargar carátula cuando no
 * existe"; antes de esto, un álbum sin release en MusicBrainz se
 * quedaba sin carátula para siempre, sin intentar nunca una segunda
 * fuente).
 *
 * Búsqueda en dos pasos reducida a una sola llamada de red: una
 * búsqueda de release en MusicBrainz por artista+álbum da un MBID; la
 * carátula frontal vive directamente en
 * `coverartarchive.org/release/{mbid}/front`, que redirige (307) a la
 * imagen real si existe o devuelve 404 si el release no tiene
 * carátula (verificado contra la especificación de CAA, 2026-07-02).
 * No hace falta una segunda llamada ni parsear JSON para esa parte —
 * Coil sigue la redirección solo, y un 404 es un fallo de carga de
 * imagen normal del que LibraryScreen ya hace fallback a la
 * miniatura de YouTube.
 *
 * El cacheo tiene dos capas: una permanente en Room (`coverArtUrl` en
 * cada pista del álbum, escrita una vez resuelta — ver
 * SearchResultTrackRepository.updateCoverArtForAlbum) y una de vida
 * de proceso aquí (`sessionCache`) para que, dentro de la misma
 * ejecución de la app, un álbum cuya búsqueda falló (ni MusicBrainz
 * ni iTunes) no se reintente en cada recomposición antes de que el
 * resultado negativo llegue a Room.
 */
@Singleton
class CoverArtRepository @Inject constructor(
    private val musicBrainzApiService: MusicBrainzApiService,
    private val itunesApiService: ItunesApiService,
) {
    /**
     * ConcurrentHashMap.put() lanza NullPointerException si el valor
     * es null — no lo admite bajo ninguna circunstancia. sessionCache
     * guardaba `url` directamente (nullable), así que cualquier álbum
     * sin match en MusicBrainz (mbid null -> url null) crasheaba la
     * app entera al intentar cachear el resultado negativo — causa
     * real del cierre al entrar en Biblioteca (Miguel Ángel,
     * 2026-07-03). NO_MATCH es el centinela: "ya se buscó, no hay
     * carátula", sin tener que guardar null.
     * ---
     * ConcurrentHashMap.put() throws NullPointerException if the
     * value is null — not allowed under any circumstance. sessionCache
     * used to store `url` directly (nullable), so any album with no
     * MusicBrainz match (mbid null -> url null) crashed the whole app
     * when trying to cache the negative result — the real cause of
     * the crash on entering Biblioteca (Miguel Ángel, 2026-07-03).
     * NO_MATCH is the sentinel: "already searched, no cover art",
     * without ever storing null.
     */
    private val sessionCache = ConcurrentHashMap<String, String>()

    /**
     * Returns the front cover URL for artist+album, or null if
     * neither MusicBrainz nor the iTunes fallback have a matching
     * release. Never throws — any network or parsing failure is
     * treated the same as "no match found", since a missing cover is
     * not an error state for the caller.
     * ---
     * Devuelve la URL de la carátula frontal para artista+álbum, o
     * null si ni MusicBrainz ni el respaldo de iTunes tienen un
     * release que coincida. Nunca lanza excepción — cualquier fallo
     * de red o de parseo se trata igual que "sin coincidencia", ya
     * que una carátula ausente no es un estado de error para quien
     * llama.
     */
    suspend fun resolveCoverArtUrl(artist: String, album: String): String? {
        val key = "$artist|$album"
        sessionCache[key]?.let { cached -> return cached.ifEmpty { null } }

        val musicBrainzUrl = resolveViaMusicBrainz(artist, album)
        val url = musicBrainzUrl ?: resolveViaItunes(artist, album)

        sessionCache[key] = url ?: NO_MATCH
        return url
    }

    private suspend fun resolveViaMusicBrainz(artist: String, album: String): String? {
        val mbid = try {
            musicBrainzApiService
                .searchReleases(query = buildMusicBrainzQuery(artist, album))
                .releases
                .firstOrNull()
                ?.id
        } catch (e: Exception) {
            null
        }
        return mbid?.let { "https://coverartarchive.org/release/$it/front" }
    }

    /**
     * Respaldo (S011): API de búsqueda de iTunes, sin clave ni
     * autenticación, verificada 2026-07-16 contra
     * performance-partners.apple.com/search-api. Solo se llega aquí
     * si MusicBrainz ya ha fallado -- volumen de peticiones bajo, no
     * hace falta rate limiting propio.
     */
    private suspend fun resolveViaItunes(artist: String, album: String): String? {
        val artworkUrl100 = try {
            itunesApiService
                .searchAlbums(term = "$artist $album")
                .results
                .firstOrNull()
                ?.artworkUrl100
        } catch (e: Exception) {
            null
        }
        return artworkUrl100?.let { upscaleItunesArtworkUrl(it) }
    }

    /**
     * iTunes no tiene un parámetro de consulta para elegir
     * resolución -- el tamaño va codificado en el propio nombre del
     * archivo de la URL ("100x100bb.jpg"). Sustituir por una
     * resolución mayor es la técnica estándar y documentada para
     * pedir una imagen más grande sin una segunda llamada.
     */
    private fun upscaleItunesArtworkUrl(artworkUrl100: String): String =
        artworkUrl100.replace("100x100bb", "600x600bb")

    private fun buildMusicBrainzQuery(artist: String, album: String): String {
        fun escape(value: String) = value.replace("\"", "")
        return "artist:\"${escape(artist)}\" AND release:\"${escape(album)}\""
    }

    private companion object {
        private const val NO_MATCH = ""
    }
}

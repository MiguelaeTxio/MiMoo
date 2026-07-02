package com.miguelaetxio.mimoo.data.remote

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves album cover art via MusicBrainz + Cover Art Archive
 * (Hito 03, PASO 6).
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
 * Archive (Hito 03, PASO 6).
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
 * ejecución de la app, un álbum cuya búsqueda en MusicBrainz falló no
 * se reintente en cada recomposición antes de que el resultado
 * negativo llegue a Room.
 */
@Singleton
class CoverArtRepository @Inject constructor(
    private val musicBrainzApiService: MusicBrainzApiService,
) {
    private val sessionCache = ConcurrentHashMap<String, String?>()

    /**
     * Returns the front cover URL for artist+album, or null if
     * MusicBrainz has no matching release. Never throws — any network
     * or parsing failure is treated the same as "no match found",
     * since a missing cover is not an error state for the caller.
     * ---
     * Devuelve la URL de la carátula frontal para artista+álbum, o
     * null si MusicBrainz no tiene un release que coincida. Nunca
     * lanza excepción — cualquier fallo de red o de parseo se trata
     * igual que "sin coincidencia", ya que una carátula ausente no es
     * un estado de error para quien llama.
     */
    suspend fun resolveCoverArtUrl(artist: String, album: String): String? {
        val key = "$artist|$album"
        if (sessionCache.containsKey(key)) return sessionCache[key]

        val mbid = try {
            musicBrainzApiService
                .searchReleases(query = buildQuery(artist, album))
                .releases
                .firstOrNull()
                ?.id
        } catch (e: Exception) {
            null
        }

        val url = mbid?.let { "https://coverartarchive.org/release/$it/front" }
        sessionCache[key] = url
        return url
    }

    private fun buildQuery(artist: String, album: String): String {
        fun escape(value: String) = value.replace("\"", "")
        return "artist:\"${escape(artist)}\" AND release:\"${escape(album)}\""
    }
}

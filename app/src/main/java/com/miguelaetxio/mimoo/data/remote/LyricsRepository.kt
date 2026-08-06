package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.local.dao.LyricsCacheDao
import com.miguelaetxio.mimoo.data.local.entity.LyricsCache
import com.miguelaetxio.mimoo.data.remote.dto.LrcLibLyricsResult
import com.miguelaetxio.mimoo.util.SearchNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resultado de resolver la letra de un tema (H17, S031, punto 2 del
 * anexo) -- las tres ramas cerradas con Miguel Ángel:
 * `syncedLyrics != null` -> karaoke real con resaltado línea a línea;
 * `syncedLyrics == null && plainLyrics != null` -> letra completa
 * scrolleable, sin aviso; ambas null -> sin letra, mensaje
 * informativo. La UI (bloque 2 del anexo) decide el tamaño del panel
 * a partir de estos mismos campos, no de un enum aparte.
 * ---
 * Result of resolving a track's lyrics (H17, S031). The UI (annex
 * block 2) decides the karaoke panel's size directly from these
 * fields.
 */
data class LyricsResult(
    val artist: String,
    val title: String,
    val plainLyrics: String?,
    val syncedLyrics: String?,
)

/**
 * Fuente de letras y caché local (H17, S031) -- ver DOCS/ANNEX_H17.md.
 * Todas las mutaciones de caché son puramente locales/derivadas de una
 * consulta de solo lectura a lrclib.net, así que a diferencia de
 * Favoritos/Lista Negra no pasan por AutoSyncPusher: no son datos que
 * el usuario decide, son un resultado cacheado de una API pública,
 * reconstruible en cualquier momento con la misma consulta.
 * ---
 * Lyrics source + local cache (H17, S031). Cache writes are purely a
 * derived, read-only lookup against lrclib.net, so unlike
 * Favorites/Blocklist they don't go through AutoSyncPusher -- they're
 * not user decisions, just a cached result of a public API call,
 * reconstructible at any time with the same query.
 */
@Singleton
class LyricsRepository @Inject constructor(
    private val api: LrcLibApiService,
    private val dao: LyricsCacheDao,
) {
    /**
     * Resuelve la letra de artista+título, sirviendo de caché si ya se
     * consultó antes. `album`/`durationSeconds` son opcionales y
     * afinan la coincidencia exacta de `GET /api/get` cuando se
     * conocen (p.ej. desde el tema en curso del ExoPlayer), pero NO
     * entran en la clave de caché -- la clave es solo artista+título
     * normalizados (mismo criterio que DislikedTrack: la letra de un
     * tema no cambia entre ediciones/álbumes).
     */
    suspend fun getLyrics(
        artist: String,
        title: String,
        album: String? = null,
        durationSeconds: Int? = null,
    ): LyricsResult = withContext(Dispatchers.IO) {
        val artistKey = SearchNormalizer.normalizeArtistName(artist)
        val titleKey = SearchNormalizer.songTitleKey(title, artist)

        dao.get(artistKey, titleKey)?.let { cached ->
            return@withContext LyricsResult(
                artist = cached.artist,
                title = cached.title,
                plainLyrics = cached.plainLyrics,
                syncedLyrics = cached.syncedLyrics,
            )
        }

        val remote: LrcLibLyricsResult? = try {
            api.getLyrics(
                trackName = title,
                artistName = artist,
                albumName = album,
                durationSeconds = durationSeconds,
            )
        } catch (e: Exception) {
            // 404 (HttpException) cuando lrclib.net no tiene coincidencia
            // exacta, o cualquier otro fallo de red -- se trata igual
            // que "sin letra" y se cachea como tal para no repetir la
            // consulta en cada reproducción del mismo tema. Un fallo de
            // red puntual se corrige solo en la siguiente consulta si
            // Miguel Ángel vuelve a pedir el mismo tema con conexión.
            null
        }

        val plainLyrics = remote?.plainLyrics?.takeIf { it.isNotBlank() }
        val syncedLyrics = remote?.syncedLyrics?.takeIf { it.isNotBlank() }

        dao.insert(
            LyricsCache(
                artistKey = artistKey,
                titleKey = titleKey,
                artist = artist,
                title = title,
                plainLyrics = plainLyrics,
                syncedLyrics = syncedLyrics,
                hasLyrics = plainLyrics != null || syncedLyrics != null,
                cachedAt = System.currentTimeMillis(),
            )
        )

        LyricsResult(
            artist = artist,
            title = title,
            plainLyrics = plainLyrics,
            syncedLyrics = syncedLyrics,
        )
    }

    /**
     * Búsqueda libre contra lrclib.net (punto 5 del anexo, pantalla del
     * drawer) -- sin pasar por la caché de `getLyrics()`, que está
     * indexada por artista+título exactos, no por texto libre. La
     * distinción visual "ya en tu biblioteca" (chip/icono) se resuelve
     * en la capa de UI del bloque 3, cruzando estos resultados contra
     * la biblioteca local -- este repositorio solo entrega los
     * resultados crudos de lrclib.net.
     */
    suspend fun searchLyrics(query: String): List<LrcLibLyricsResult> = withContext(Dispatchers.IO) {
        try {
            api.searchLyrics(query = query)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

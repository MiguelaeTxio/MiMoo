package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.local.dao.LyricsCacheDao
import com.miguelaetxio.mimoo.data.local.entity.LyricsCache
import com.miguelaetxio.mimoo.data.remote.dto.LrcLibLyricsResult
import com.miguelaetxio.mimoo.util.SearchNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val appContext: Context,
    private val storageManager: StorageManager,
) {
    private fun log(line: String) = LyricsDebugLogger.log(appContext, storageManager, line)

    /**
     * S037 -- diagnosticado con letras_debug.txt (2026-11-14): "nunca
     * encuentra letra, por famosa que sea". Tres causas reales:
     * 1. Pistas importadas de recopilatorios llegan con artista
     *    "Various Artists" y el título "Pink Floyd - Another Brick...":
     *    lrclib /get exige coincidencia exacta y respondía 404.
     *    Ahora se separa "Artista - Título" cuando el crédito es
     *    genérico.
     * 2. Un 404 de /get no es el final: se prueba /search (tolerante).
     * 3. Cualquier excepción (503 incluido) se cacheaba como "sin letra
     *    confirmada" para siempre. Ahora solo se cachea una respuesta
     *    real de lrclib; los fallos de red/servidor no se cachean.
     * ---
     * Fetches lyrics (cache first, then lrclib.net /get, then /search).
     */
    suspend fun getLyrics(
        artist: String,
        title: String,
        album: String? = null,
        durationSeconds: Int? = null,
    ): LyricsResult = withContext(Dispatchers.IO) {
        val (queryArtist, queryTitle) = splitGenericCredit(artist, title)
        val artistKey = SearchNormalizer.normalizeArtistName(queryArtist)
        val titleKey = SearchNormalizer.songTitleKey(queryTitle, queryArtist)

        dao.get(artistKey, titleKey)
            // Los "sin letra" anteriores a la corrección salían de
            // consultas mal formadas o de fallos de red cacheados: se
            // ignoran y se vuelve a consultar.
            ?.takeIf { it.hasLyrics || it.cachedAt >= NEGATIVE_CACHE_VALID_FROM }
            ?.let { cached ->
                log(
                    "getLyrics(artist='$artist', title='$title') -- CACHÉ: " +
                        if (cached.hasLyrics) {
                            if (cached.syncedLyrics != null) "sincronizada" else "plana"
                        } else {
                            "sin letra (confirmado previamente)"
                        },
                )
                return@withContext LyricsResult(
                    artist = cached.artist,
                    title = cached.title,
                    plainLyrics = cached.plainLyrics,
                    syncedLyrics = cached.syncedLyrics,
                )
            }

        log(
            "getLyrics(artist='$artist', title='$title') -- sin caché, consultando lrclib.net " +
                "como artista='$queryArtist' título='$queryTitle' album=$album durationSeconds=$durationSeconds",
        )

        var transientFailure = false
        var remote: LrcLibLyricsResult? = try {
            api.getLyrics(
                trackName = queryTitle,
                artistName = queryArtist,
                albumName = album,
                durationSeconds = durationSeconds,
            )
        } catch (e: retrofit2.HttpException) {
            if (e.code() != 404) transientFailure = true
            log("getLyrics -- /get HTTP ${e.code()}")
            null
        } catch (e: Exception) {
            transientFailure = true
            log("getLyrics -- /get EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}")
            null
        }

        if (remote == null && !transientFailure) {
            remote = try {
                val found = pickBestSearchResult(
                    api.searchLyrics(trackName = queryTitle, artistName = queryArtist),
                    durationSeconds,
                )
                log("getLyrics -- /search ${if (found != null) "id=${found.id}" else "sin resultados útiles"}")
                found
            } catch (e: Exception) {
                transientFailure = true
                log("getLyrics -- /search EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}")
                null
            }
        }

        val plainLyrics = remote?.plainLyrics?.takeIf { it.isNotBlank() }
        val syncedLyrics = remote?.syncedLyrics?.takeIf { it.isNotBlank() }

        log(
            "getLyrics(artist='$artist', title='$title') -- RESULTADO: " +
                when {
                    transientFailure -> "fallo de red/servidor -- NO se cachea"
                    remote == null -> "sin letra en lrclib -- cacheada"
                    syncedLyrics != null -> "sincronizada (lrclib id=${remote.id}) -- cacheada"
                    plainLyrics != null -> "plana, sin sincronizar (lrclib id=${remote.id}) -- cacheada"
                    remote.instrumental -> "instrumental (lrclib id=${remote.id}, sin letra) -- cacheada"
                    else -> "respuesta sin ninguna letra (lrclib id=${remote.id}) -- cacheada"
                },
        )

        if (!transientFailure) {
            dao.insert(
                LyricsCache(
                    artistKey = artistKey,
                    titleKey = titleKey,
                    artist = queryArtist,
                    title = queryTitle,
                    plainLyrics = plainLyrics,
                    syncedLyrics = syncedLyrics,
                    hasLyrics = plainLyrics != null || syncedLyrics != null,
                    cachedAt = System.currentTimeMillis(),
                )
            )
        }

        LyricsResult(
            artist = queryArtist,
            title = queryTitle,
            plainLyrics = plainLyrics,
            syncedLyrics = syncedLyrics,
        )
    }

    /**
     * "Various Artists" / "Artista desconocido" + "Grupo - Canción" ->
     * ("Grupo", "Canción"). Con un artista real no se toca nada.
     */
    private fun splitGenericCredit(artist: String, title: String): Pair<String, String> {
        val generic = artist.isBlank() ||
            SearchNormalizer.normalizeArtistName(artist) in GENERIC_CREDITS
        if (!generic) return artist to title
        val idx = listOf(" - ", " – ", " — ")
            .map { title.indexOf(it) to it.length }
            .filter { it.first > 0 }
            .minByOrNull { it.first }
            ?: return artist to title
        val left = title.substring(0, idx.first).trim()
        val right = title.substring(idx.first + idx.second).trim()
        if (left.isEmpty() || right.isEmpty()) return artist to title
        return left to SearchNormalizer.cleanSongTitle(right, left)
    }

    /** Mejor resultado de /search: con letra, de duración parecida y sincronizado si se puede. */
    private fun pickBestSearchResult(
        results: List<LrcLibLyricsResult>,
        durationSeconds: Int?,
    ): LrcLibLyricsResult? {
        val withLyrics = results.filter {
            !it.instrumental && (!it.plainLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank())
        }
        if (withLyrics.isEmpty()) return null
        if (durationSeconds == null) {
            return withLyrics.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: withLyrics.first()
        }
        val close = withLyrics.filter { r ->
            r.duration != null && kotlin.math.abs(r.duration - durationSeconds) <= 10
        }
        close.firstOrNull { !it.syncedLyrics.isNullOrBlank() }?.let { return it }
        close.firstOrNull()?.let { return it }
        // Duración distinta: la letra plana sirve, la sincronizada no (iría desfasada).
        return withLyrics.firstOrNull { !it.plainLyrics.isNullOrBlank() }
            ?.copy(syncedLyrics = null)
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
            val results = api.searchLyrics(query = query)
            log("searchLyrics(query='$query') -- ${results.size} resultado(s)")
            results
        } catch (e: Exception) {
            log("searchLyrics(query='$query') -- EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}")
            emptyList()
        }
    }

    private companion object {
        val GENERIC_CREDITS = setOf("various artists", "varios artistas", "artista desconocido")

        /** 2026-11-14 12:00 UTC: los "sin letra" anteriores se consideran envenenados. */
        const val NEGATIVE_CACHE_VALID_FROM = 1_794_657_600_000L
    }
}

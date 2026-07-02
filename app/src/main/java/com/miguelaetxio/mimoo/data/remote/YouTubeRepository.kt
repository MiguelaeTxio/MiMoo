package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.TrackDto
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for YouTube Data API v3 operations.
 * Replaces the previous playlist-import flow with free-text search,
 * as of the S002-H02 refoundation (search → stream/download, no
 * manual playlist import). Hito 05 reintroduces playlists, but only
 * as an internal matching strategy for full albums — never as a
 * user-facing import flow.
 * ---
 * Repositorio para operaciones de la YouTube Data API v3.
 * Sustituye el flujo previo de importación de playlist por búsqueda
 * de texto libre, a raíz de la refundación S002-H02 (buscar →
 * streaming/descarga, sin importación manual de playlist). El Hito 05
 * reintroduce las playlists, pero solo como estrategia interna de
 * emparejamiento de álbumes completos — nunca como flujo de
 * importación de cara al usuario.
 */
@Singleton
class YouTubeRepository @Inject constructor(
    private val apiService: YouTubeApiService,
) {

    /**
     * Searches videos by term and enriches results with duration via
     * videos.list (1 unit/call, batched up to 50).
     * On HTTP error, wraps the exception with Google's real JSON error
     * body (reason/message), since HttpException.message alone is
     * uninformative ("HTTP 403") for diagnosing the actual cause.
     * 100 units/call — see YouTubeApiService for the corrected quota
     * cost. Prefer getPlaylistTracks() when a whole album/playlist is
     * needed; this call is for one-off free-text lookups.
     * ---
     * Busca vídeos por término y enriquece los resultados con
     * duración vía videos.list (1 unidad/llamada, batch hasta 50).
     * En error HTTP, envuelve la excepción con el cuerpo JSON real de
     * error de Google (reason/message), ya que HttpException.message
     * por sí solo es poco informativo ("HTTP 403") para diagnosticar
     * la causa real. 100 unidades/llamada — ver YouTubeApiService
     * para el coste de cuota corregido. Preferir getPlaylistTracks()
     * cuando se necesita un álbum/playlist entero; esta llamada es
     * para búsquedas puntuales de texto libre.
     */
    suspend fun search(query: String, apiKey: String): List<TrackDto> {
        val response = wrapHttpErrors("search.list") {
            apiService.search(query = query, apiKey = apiKey)
        }
        val videoIds = response.items.mapNotNull { it.id.videoId }
        if (videoIds.isEmpty()) return emptyList()

        val durationMap = fetchDurations(videoIds, apiKey)

        return response.items.mapNotNull { item ->
            val videoId = item.id.videoId ?: return@mapNotNull null
            TrackDto(
                youtubeId = videoId,
                title = item.snippet.title,
                durationSeconds = durationMap[videoId] ?: 0,
                thumbnailUrl = item.snippet.thumbnails?.high?.url
                    ?: item.snippet.thumbnails?.medium?.url,
                channelTitle = item.snippet.channelTitle,
            )
        }
    }

    /**
     * Finds a playlist matching the free-text query and returns its
     * ID, or null if MusicBrainz returned no candidates (never
     * throws for "no results" — same convention as the rest of the
     * album-matching flow). 100 units/call (search.list) — the one
     * unavoidable expensive call per album in the playlist-first
     * strategy (Hito 05, PASO 6e).
     * ---
     * Busca una playlist que coincida con el término de texto libre y
     * devuelve su ID, o null si no hay candidatas (nunca lanza
     * excepción por "sin resultados" — misma convención que el resto
     * del flujo de emparejamiento de álbum). 100 unidades/llamada
     * (search.list) — la única llamada cara e inevitable por álbum en
     * la estrategia de playlist primero (Hito 05, PASO 6e).
     */
    suspend fun searchPlaylist(query: String, apiKey: String): String? {
        val response = wrapHttpErrors("search.list (playlist)") {
            apiService.searchPlaylists(query = query, apiKey = apiKey)
        }
        return response.items.firstOrNull()?.id?.playlistId
    }

    /**
     * Lists every video in a playlist, in playlist order, enriched
     * with duration — same shape as search() but backed by
     * playlistItems.list (1 unit) + videos.list (1 unit/batch) instead
     * of search.list (100 units). This is the cheap path for matching
     * a whole album at once (Hito 05, PASO 6e).
     * ---
     * Lista todos los vídeos de una playlist, en el orden de la
     * playlist, enriquecidos con duración — misma forma que search()
     * pero respaldada por playlistItems.list (1 unidad) +
     * videos.list (1 unidad/batch) en vez de search.list (100
     * unidades). Es el camino barato para emparejar un álbum entero
     * de una vez (Hito 05, PASO 6e).
     */
    suspend fun getPlaylistTracks(playlistId: String, apiKey: String): List<TrackDto> {
        val response = wrapHttpErrors("playlistItems.list") {
            apiService.getPlaylistItems(playlistId = playlistId, apiKey = apiKey)
        }
        val videoIds = response.items.mapNotNull { it.snippet.resourceId.videoId }
        if (videoIds.isEmpty()) return emptyList()

        val durationMap = fetchDurations(videoIds, apiKey)

        // sortedBy position: playlistItems.list ya suele devolver los
        // items en orden, pero el campo position es la fuente de
        // verdad explícita -- no asumir el orden de la respuesta.
        return response.items
            .sortedBy { it.snippet.position }
            .mapNotNull { item ->
                val videoId = item.snippet.resourceId.videoId ?: return@mapNotNull null
                TrackDto(
                    youtubeId = videoId,
                    title = item.snippet.title,
                    durationSeconds = durationMap[videoId] ?: 0,
                    thumbnailUrl = item.snippet.thumbnails?.high?.url
                        ?: item.snippet.thumbnails?.medium?.url,
                    channelTitle = item.snippet.channelTitle,
                )
            }
    }

    private suspend fun fetchDurations(
        videoIds: List<String>,
        apiKey: String,
    ): Map<String, Int> {
        val durationMap = mutableMapOf<String, Int>()
        videoIds.chunked(50).forEach { batch ->
            val videoResponse = apiService.getVideos(
                ids = batch.joinToString(","),
                apiKey = apiKey,
            )
            for (video in videoResponse.items) {
                durationMap[video.id] = parseDuration(video.contentDetails.duration)
            }
        }
        return durationMap
    }

    private suspend fun <T> wrapHttpErrors(callName: String, block: suspend () -> T): T =
        try {
            block()
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            throw RuntimeException(
                "YouTube $callName HTTP ${e.code()}: " +
                    (errorBody ?: e.message()),
                e,
            )
        }

    /**
     * Parses ISO 8601 duration string to seconds.
     * Example: "PT1H2M3S" -> 3723
     * ---
     * Parsea duración ISO 8601 a segundos.
     */
    private fun parseDuration(iso: String): Int {
        val regex = Regex(
            """PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?"""
        )
        val match = regex.find(iso) ?: return 0
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        val seconds = match.groupValues[3].toIntOrNull() ?: 0
        return hours * 3600 + minutes * 60 + seconds
    }
}

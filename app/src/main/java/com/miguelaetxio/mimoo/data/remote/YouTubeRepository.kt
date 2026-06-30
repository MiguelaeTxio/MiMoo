package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.TrackDto
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for YouTube Data API v3 operations.
 * Replaces the previous playlist-import flow with free-text search,
 * as of the S002-H02 refoundation (search → stream/download, no
 * manual playlist import).
 * ---
 * Repositorio para operaciones de la YouTube Data API v3.
 * Sustituye el flujo previo de importación de playlist por búsqueda
 * de texto libre, a raíz de la refundación S002-H02 (buscar →
 * streaming/descarga, sin importación manual de playlist).
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
     * ---
     * Busca vídeos por término y enriquece los resultados con
     * duración vía videos.list (1 unidad/llamada, batch hasta 50).
     * En error HTTP, envuelve la excepción con el cuerpo JSON real de
     * error de Google (reason/message), ya que HttpException.message
     * por sí solo es poco informativo ("HTTP 403") para diagnosticar
     * la causa real.
     */
    suspend fun search(query: String, apiKey: String): List<TrackDto> {
        val response = try {
            apiService.search(query = query, apiKey = apiKey)
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            throw RuntimeException(
                "YouTube search.list HTTP ${e.code()}: " +
                    (errorBody ?: e.message()),
                e,
            )
        }
        val videoIds = response.items.mapNotNull { it.id.videoId }
        if (videoIds.isEmpty()) return emptyList()

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

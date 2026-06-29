package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.TrackDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for YouTube Data API v3 operations.
 * Implements full pagination and video batching (50/call).
 * ---
 * Repositorio para operaciones de la YouTube Data API v3.
 * Implementa paginación completa y batching de videos (50/llamada).
 */
@Singleton
class YouTubeRepository @Inject constructor(
    private val apiService: YouTubeApiService,
) {

    /**
     * Fetches all tracks from a YouTube playlist.
     * Uses playlistItems.list (1u) + videos.list in batches of 50 (1u each).
     * ---
     * Obtiene todos los tracks de una playlist de YouTube.
     * Usa playlistItems.list (1u) + videos.list en batches de 50 (1u cada uno).
     */
    suspend fun fetchPlaylistItems(
        playlistId: String,
        apiKey: String,
    ): List<TrackDto> {
        val videoIds = mutableListOf<String>()
        val titleMap = mutableMapOf<String, String>()
        val thumbMap = mutableMapOf<String, String?>()
        var pageToken: String? = null

        // Paginate through all playlist items
        do {
            val response = apiService.getPlaylistItems(
                playlistId = playlistId,
                pageToken = pageToken,
                apiKey = apiKey,
            )
            for (item in response.items) {
                val videoId = item.snippet.resourceId.videoId
                videoIds.add(videoId)
                titleMap[videoId] = item.snippet.title
                thumbMap[videoId] = item.snippet.thumbnails?.high?.url
                    ?: item.snippet.thumbnails?.medium?.url
            }
            pageToken = response.nextPageToken
        } while (pageToken != null)

        // Fetch video details in batches of 50
        val durationMap = mutableMapOf<String, Int>()
        val channelMap = mutableMapOf<String, String>()
        videoIds.chunked(50).forEach { batch ->
            val videoResponse = apiService.getVideos(
                ids = batch.joinToString(","),
                apiKey = apiKey,
            )
            for (video in videoResponse.items) {
                durationMap[video.id] = parseDuration(video.contentDetails.duration)
                channelMap[video.id] = video.snippet?.channelTitle ?: ""
            }
        }

        return videoIds.map { id ->
            TrackDto(
                youtubeId = id,
                title = titleMap[id] ?: id,
                durationSeconds = durationMap[id] ?: 0,
                thumbnailUrl = thumbMap[id],
                channelTitle = channelMap[id] ?: "",
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

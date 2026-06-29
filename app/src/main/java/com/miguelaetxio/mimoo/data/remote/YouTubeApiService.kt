package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.PlaylistItemsResponse
import com.miguelaetxio.mimoo.data.remote.dto.VideoListResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for YouTube Data API v3.
 * search.list is intentionally excluded (100 units/call quota cost).
 * ---
 * Interfaz Retrofit para la YouTube Data API v3.
 * search.list excluido intencionalmente (coste 100 unidades/llamada).
 */
interface YouTubeApiService {

    /**
     * Fetches items from a playlist (1 unit/call).
     * ---
     * Obtiene items de una playlist (1 unidad/llamada).
     */
    @GET("playlistItems")
    suspend fun getPlaylistItems(
        @Query("part") part: String = "snippet",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null,
        @Query("key") apiKey: String,
    ): PlaylistItemsResponse

    /**
     * Fetches video details in batch of up to 50 (1 unit/call).
     * ---
     * Obtiene detalles de videos en batch de hasta 50 (1 unidad/llamada).
     */
    @GET("videos")
    suspend fun getVideos(
        @Query("part") part: String = "contentDetails,snippet",
        @Query("id") ids: String,
        @Query("key") apiKey: String,
    ): VideoListResponse
}

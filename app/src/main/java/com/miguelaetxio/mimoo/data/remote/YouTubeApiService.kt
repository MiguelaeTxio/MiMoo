package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.SearchListResponse
import com.miguelaetxio.mimoo.data.remote.dto.VideoListResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for YouTube Data API v3.
 * search.list quota cost verified 2026-06-30 against the official
 * Google quota documentation (last updated 2026-06-01): 1 unit per
 * call, with its own separate daily limit of 100 calls/day, distinct
 * from the general 10,000-unit project pool. The previous project
 * version avoided search.list assuming a 100-unit cost; that
 * assumption was outdated and has been corrected.
 * ---
 * Interfaz Retrofit para la YouTube Data API v3.
 * Coste de cuota de search.list verificado el 2026-06-30 contra la
 * documentación oficial de cuotas de Google (última actualización
 * 2026-06-01): 1 unidad por llamada, con límite diario propio de 100
 * llamadas/día, separado del pool general de 10.000 unidades del
 * proyecto. La versión anterior del proyecto evitaba search.list
 * asumiendo un coste de 100 unidades; esa suposición estaba obsoleta
 * y ha sido corregida.
 */
interface YouTubeApiService {

    /**
     * Searches videos by free-text term.
     * ---
     * Busca vídeos por término de texto libre.
     */
    @GET("search")
    suspend fun search(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("videoCategoryId") videoCategoryId: String = "10", // Music
        @Query("maxResults") maxResults: Int = 25,
        @Query("key") apiKey: String,
    ): SearchListResponse

    /**
     * Fetches video details in batch of up to 50 (1 unit/call).
     * ---
     * Obtiene detalles de videos en batch de hasta 50 (1 unidad/llamada).
     */
    @GET("videos")
    suspend fun getVideos(
        @Query("part") part: String = "contentDetails",
        @Query("id") ids: String,
        @Query("key") apiKey: String,
    ): VideoListResponse
}

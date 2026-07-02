package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.PlaylistItemsResponse
import com.miguelaetxio.mimoo.data.remote.dto.SearchListResponse
import com.miguelaetxio.mimoo.data.remote.dto.VideoListResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for YouTube Data API v3.
 *
 * CORRECCIÓN DE COSTE DE CUOTA (2026-07-02, H05 sesión de "1 de 11
 * pistas emparejadas"): la afirmación anterior de que search.list
 * cuesta 1 unidad/llamada era incorrecta — verificado ahora contra la
 * documentación oficial de cuotas de Google
 * (developers.google.com/youtube/v3/determine_quota_cost, actualizada
 * 2026-06-01) y contra múltiples fuentes independientes recientes:
 * **search.list cuesta 100 unidades/llamada**, sobre un pool diario de
 * 10.000 unidades por proyecto — es decir, ~100 llamadas/día como
 * mucho, y cada llamada individual se come el 1% del presupuesto
 * diario entero. videos.list y playlistItems.list cuestan solo 1
 * unidad/llamada cada una. Esto es la causa real de que un álbum de
 * 11 pistas (11 × search.list = 1.100 unidades) agote la cuota a
 * media búsqueda tras unas pocas pruebas en la misma sesión — ver
 * AlbumMatchRepository, que ahora prioriza playlistItems.list (1
 * unidad, trae el álbum completo de una vez) sobre search.list
 * pista a pista.
 * ---
 * Interfaz Retrofit para la YouTube Data API v3.
 *
 * CORRECCIÓN DE COSTE DE CUOTA (2026-07-02, sesión del "1 de 11
 * pistas emparejadas"): la afirmación anterior de que search.list
 * cuesta 1 unidad/llamada era incorrecta — verificado contra la
 * documentación oficial de cuotas de Google (actualizada 2026-06-01)
 * y varias fuentes independientes recientes: search.list cuesta
 * **100 unidades/llamada**, sobre un pool diario de 10.000 unidades
 * por proyecto. videos.list y playlistItems.list cuestan solo 1
 * unidad/llamada cada una — ver AlbumMatchRepository, que ahora
 * prioriza playlistItems.list sobre search.list pista a pista.
 */
interface YouTubeApiService {

    /**
     * Searches videos by free-text term. 100 units/call.
     * ---
     * Busca vídeos por término de texto libre. 100 unidades/llamada.
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
     * Searches playlists by free-text term (Hito 05, coincidencia de
     * álbum completo). 100 units/call — igual de caro que la búsqueda
     * de vídeos, pero se paga UNA vez por álbum entero en vez de una
     * vez por pista.
     * ---
     * Busca playlists por término de texto libre (Hito 05,
     * emparejamiento de álbum completo). 100 unidades/llamada — igual
     * de cara que la búsqueda de vídeos, pero se paga UNA vez por
     * álbum entero en vez de una vez por pista.
     */
    @GET("search")
    suspend fun searchPlaylists(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "playlist",
        @Query("maxResults") maxResults: Int = 5,
        @Query("key") apiKey: String,
    ): SearchListResponse

    /**
     * Lists the videos of a playlist, in playlist order. 1 unit/call
     * regardless of how many items come back (up to maxResults, 50
     * max per page) — the cheap alternative to N × search.list.
     * ---
     * Lista los vídeos de una playlist, en el orden de la playlist. 1
     * unidad/llamada sin importar cuántos items devuelva (hasta
     * maxResults, 50 como máximo por página) — la alternativa barata
     * a N × search.list.
     */
    @GET("playlistItems")
    suspend fun getPlaylistItems(
        @Query("part") part: String = "snippet",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 50,
        @Query("key") apiKey: String,
    ): PlaylistItemsResponse

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

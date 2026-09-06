package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.DeezerArtistSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API pública de Deezer -- fuente de fotos reales de artista (S059).
 * Raíz https://api.deezer.com/ (enlazada en NetworkModule). Sin clave
 * ni autenticación, mismo espíritu que ItunesApiService (fallback de
 * carátula): un servicio público gratuito, sin registro, para un dato
 * que ni MusicBrainz ni Cover Art Archive ofrecen (ninguno de los dos
 * tiene fotos de artista, solo carátulas de álbum).
 */
interface DeezerApiService {
    @GET("search/artist")
    suspend fun searchArtists(
        @Query("q") query: String,
        @Query("limit") limit: Int = 1,
    ): DeezerArtistSearchResponse
}

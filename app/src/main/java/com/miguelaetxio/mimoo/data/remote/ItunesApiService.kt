package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.ItunesSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API de búsqueda de iTunes -- fallback de carátula (H03, S011). Raíz
 * https://itunes.apple.com/ (enlazada en NetworkModule). Sin clave ni
 * autenticación, sin límite de tasa documentado exigente (a
 * diferencia de MusicBrainz) -- solo se usa cuando MusicBrainz/Cover
 * Art Archive ya ha fallado, así que el volumen real de peticiones es
 * bajo.
 */
interface ItunesApiService {
    @GET("search")
    suspend fun searchAlbums(
        @Query("term") term: String,
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 1,
    ): ItunesSearchResponse
}

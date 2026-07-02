package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * MusicBrainz API v2 — release search only (PASO 6, H03). Root
 * https://musicbrainz.org/ws/2/ (bound in NetworkModule). Every
 * request through this service already carries a rate-limiting
 * interceptor and a meaningful User-Agent — see NetworkModule,
 * required per MusicBrainz's rate-limiting rules
 * (musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting, verified
 * 2026-07-02).
 * ---
 * API v2 de MusicBrainz — solo búsqueda de releases (PASO 6, H03).
 * Raíz https://musicbrainz.org/ws/2/ (enlazada en NetworkModule).
 * Toda petición a través de este servicio ya lleva un interceptor de
 * limitación de tasa y un User-Agent significativo — ver
 * NetworkModule, requisito según las reglas de rate-limiting de
 * MusicBrainz (verificado 2026-07-02).
 */
interface MusicBrainzApiService {
    @GET("release/")
    suspend fun searchReleases(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 1,
    ): MusicBrainzSearchResponse
}

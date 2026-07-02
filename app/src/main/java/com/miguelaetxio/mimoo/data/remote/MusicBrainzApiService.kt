package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzReleaseDetail
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzSearchResponse
import retrofit2.http.GET
import retrofit2.http.Path
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

    /**
     * Full release lookup with tracklist (Hito 05) — a second,
     * separate call after searchReleases() has resolved the MBID.
     * `inc=recordings` is what makes MusicBrainz include the tracklist
     * in the response; without it the release comes back with no
     * `media`/`tracks` at all.
     * ---
     * Lookup completo de un release con tracklist (Hito 05) — una
     * segunda llamada, separada, tras haber resuelto el MBID con
     * searchReleases(). `inc=recordings` es lo que hace que
     * MusicBrainz incluya el tracklist en la respuesta; sin él el
     * release vuelve sin `media`/`tracks` en absoluto.
     */
    @GET("release/{mbid}")
    suspend fun lookupRelease(
        @Path("mbid") mbid: String,
        @Query("inc") inc: String = "recordings",
        @Query("fmt") format: String = "json",
    ): MusicBrainzReleaseDetail
}

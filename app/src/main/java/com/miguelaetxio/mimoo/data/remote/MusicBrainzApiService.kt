package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzArtistDetail
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzArtistSearchResponse
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzRecordingSearchResponse
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzReleaseDetail
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzReleaseGroupSearchResponse
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
    /**
     * Búsqueda de grabaciones (S023). Se usa para fechar el TEMA que
     * arranca una sesión de Radio.
     *
     * Hasta S023 la década del ancla salía de `life-span.begin` del
     * artista, que para un solista es su fecha de nacimiento -- P!nk
     * (1979) anclaba en los 70. Pero incluso para grupos el artista es
     * la fuente equivocada: Yes se formó en 1968, y entre "Roundabout"
     * (1971) y "Owner of a Lonely Heart" (1983) no hay nada en común.
     * Regla cerrada por Miguel Ángel en S023: **fecha el tema, nunca
     * el artista.**
     *
     * `first-release-date` de la grabación es la primera publicación
     * conocida, que es exactamente la fecha que queremos: la del tema,
     * no la de la reedición que se esté escuchando.
     */
    @GET("recording/")
    suspend fun searchRecordings(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 10,
    ): MusicBrainzRecordingSearchResponse

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

    /**
     * H08 PARTE 2 (S009) -- busca un artista por nombre, para resolver
     * su MBID antes de poder consultar sus géneros. Primer paso de la
     * Radio: dado el artista que estaba sonando, encontrar "artistas
     * relacionados" vía géneros compartidos.
     * ---
     * H08 PARTE 2 (S009) -- searches an artist by name, to resolve
     * its MBID before genres can be looked up. First step of Radio:
     * given the artist that was playing, find "related artists" via
     * shared genres.
     */
    @GET("artist/")
    suspend fun searchArtists(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 5,
        @Query("offset") offset: Int = 0,
    ): MusicBrainzArtistSearchResponse

    /**
     * H08 PARTE 2 (S009) -- lookup de un artista por MBID con
     * `inc=genres`, verificado contra los ejemplos oficiales de la API
     * (musicbrainz.org/doc/MusicBrainz_API/Examples): sin `inc=genres`
     * el artista vuelve sin ningún género en la respuesta.
     * ---
     * H08 PARTE 2 (S009) -- artist lookup by MBID with `inc=genres`,
     * verified against the API's official examples
     * (musicbrainz.org/doc/MusicBrainz_API/Examples): without
     * `inc=genres` the artist comes back with no genre data at all.
     */
    @GET("artist/{mbid}")
    suspend fun lookupArtist(
        @Path("mbid") mbid: String,
        @Query("inc") inc: String = "genres",
        @Query("fmt") format: String = "json",
    ): MusicBrainzArtistDetail

    /**
     * H12 (S018) -- browse de release-groups (álbumes/sencillos) de un
     * artista ya resuelto por MBID. Endpoint de BROWSE, no de
     * búsqueda: se filtra por `artist={mbid}` (relación directa),
     * nunca por `query=` con sintaxis Lucene -- mismo patrón que
     * `lookupArtist`/`lookupRelease`, distinto de `searchReleases`/
     * `searchArtists`. `type` filtra por tipo de release-group
     * (`album` o `single`), verificado contra los ejemplos oficiales
     * de browse (musicbrainz.org/doc/MusicBrainz_API/Search#Browse,
     * 2026-07-19) -- separa "álbumes" de "sencillos sueltos" tal como
     * pide el diseño de S017, sin traer EPs/compilaciones mezclados.
     * ---
     * H12 (S018) -- browse of release-groups (albums/singles) for an
     * artist already resolved by MBID. BROWSE endpoint, not search:
     * filtered by `artist={mbid}` (direct relationship), never by
     * Lucene `query=` -- same pattern as `lookupArtist`/
     * `lookupRelease`, unlike `searchReleases`/`searchArtists`. `type`
     * filters by release-group type (`album` or `single`), verified
     * against the official browse examples
     * (musicbrainz.org/doc/MusicBrainz_API/Search#Browse, 2026-07-19)
     * -- separates "albums" from "loose singles" per the S017 design,
     * without mixing in EPs/compilations.
     */
    @GET("release-group/")
    suspend fun browseReleaseGroupsByArtist(
        @Query("artist") artistMbid: String,
        @Query("type") type: String,
        @Query("limit") limit: Int = 100,
        @Query("fmt") format: String = "json",
    ): MusicBrainzReleaseGroupSearchResponse

    /**
     * H12 (S018) -- browse de releases dentro de un release-group ya
     * conocido, para obtener una release representativa y con ella su
     * número de pistas vía `lookupRelease` -- exclusivamente
     * MusicBrainz, SIN pasar por YouTube (a diferencia de
     * AlbumMatchRepository.matchAlbumTracks(), que sí empareja cada
     * pista con un vídeo). Necesario para el conteo "álbum completo /
     * álbum parcial" de ArtistScreen: llamar a matchAlbumTracks() para
     * cada álbum del artista solo para contar pistas dispararía una
     * búsqueda de YouTube por pista y por álbum, un coste que este
     * conteo no necesita.
     * ---
     * H12 (S018) -- browse of releases inside an already-known
     * release-group, to get a representative release and, from it, its
     * track count via `lookupRelease` -- MusicBrainz only, WITHOUT
     * touching YouTube (unlike AlbumMatchRepository.matchAlbumTracks(),
     * which does match each track to a video). Needed for
     * ArtistScreen's "complete album / partial album" count: calling
     * matchAlbumTracks() for every one of the artist's albums just to
     * count tracks would fire a YouTube search per track per album, a
     * cost this count doesn't need.
     */
    @GET("release/")
    suspend fun browseReleasesByReleaseGroup(
        @Query("release-group") releaseGroupMbid: String,
        @Query("limit") limit: Int = 1,
        @Query("fmt") format: String = "json",
    ): MusicBrainzSearchResponse
}

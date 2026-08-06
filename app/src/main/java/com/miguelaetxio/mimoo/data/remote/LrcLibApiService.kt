package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.LrcLibLyricsResult
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * lrclib.net (H17, S031) -- fuente de letras elegida en la sesión de
 * diseño de Karaoke & Lyrics, ver DOCS/ANNEX_H17.md, punto 1. API
 * abierta, gratuita, sin API key ni registro, sin límite de
 * peticiones (confirmado en línea esta sesión) -- aun así se envía
 * User-Agent identificable en NetworkModule por buena práctica
 * recomendada por el propio servicio, mismo espíritu que
 * MusicBrainz/Radio-Browser aunque aquí no sea obligatorio.
 *
 * Raíz fija https://lrclib.net/api/ (enlazada en NetworkModule).
 *
 * `getLyrics` -- búsqueda exacta por track+artista(+álbum+duración
 * opcionales), la que usa la caché de LyricsRepository al resolver el
 * tema que suena en el ExoPlayer. Devuelve 404 (HttpException en
 * Retrofit) si no hay coincidencia exacta -- se captura en
 * LyricsRepository, nunca aquí.
 *
 * `searchLyrics` -- búsqueda flexible (uno o varios campos, incluido
 * `query` libre), la que usa la pantalla de búsqueda del drawer
 * (punto 5 del anexo) para encontrar letra de cualquier canción,
 * tenga o no algo descargado de ella.
 * ---
 * lrclib.net (H17, S031) -- lyrics source chosen in the Karaoke &
 * Lyrics design session. Open, free API, no API key or registration,
 * no rate limit (confirmed online this session).
 */
interface LrcLibApiService {

    @GET("get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") durationSeconds: Int? = null,
    ): LrcLibLyricsResult

    @GET("search")
    suspend fun searchLyrics(
        @Query("q") query: String? = null,
        @Query("track_name") trackName: String? = null,
        @Query("artist_name") artistName: String? = null,
        @Query("album_name") albumName: String? = null,
    ): List<LrcLibLyricsResult>
}

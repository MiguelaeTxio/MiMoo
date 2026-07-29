package com.miguelaetxio.mimoo.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * S025 -- DISCOGS COMO FUENTE DE LA FECHA DE PRIMERA EDICIÓN.
 *
 * Orden de Miguel Ángel: *"si hay que mirarlo en Wikipedia, en Discogs,
 * en mierda frita, donde se ponga que se mira."*
 *
 * Discogs es un catálogo de EDICIONES FÍSICAS, no de obras abstractas,
 * y eso es justo lo que hace falta aquí: cada ficha lleva su año de
 * edición, así que pidiendo todas las de un tema y quedándose con la
 * más antigua sale la primera vez que se publicó. Es la fuente que
 * mejor cubre lo que a MusicBrainz se le escapa, sobre todo en
 * repertorio español antiguo.
 *
 * Necesita token personal, que viaja como secreto de repositorio
 * `DISCOGS_TOKEN` y llega al APK vía `BuildConfig`. Si está vacío
 * -- compilación local sin secreto -- la cascada se salta este peldaño
 * sin romperse.
 *
 * Discogs pide User-Agent identificable y limita a 60 peticiones por
 * minuto con token. Ver `NetworkModule`.
 * ---
 * S025 -- Discogs as a source for a track's first release year. It
 * catalogues physical editions rather than abstract works, so taking
 * the oldest year across a track's releases gives the original
 * publication. Needs a personal token; if absent the cascade simply
 * skips this rung.
 */
interface DiscogsApiService {

    @GET("database/search")
    suspend fun search(
        @Query("track") track: String,
        @Query("artist") artist: String,
        @Query("type") type: String = "release",
        @Query("per_page") perPage: Int = 25,
        @Query("token") token: String,
    ): DiscogsSearchResponse
}

data class DiscogsSearchResponse(
    @SerializedName("results") val results: List<DiscogsResult> = emptyList(),
)

data class DiscogsResult(
    @SerializedName("title") val title: String = "",
    /** Año de esta edición concreta. Puede venir vacío o como texto. */
    @SerializedName("year") val year: String? = null,
)

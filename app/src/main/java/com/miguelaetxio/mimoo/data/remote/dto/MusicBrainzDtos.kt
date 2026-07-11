package com.miguelaetxio.mimoo.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTOs for the MusicBrainz release search response (PASO 6, H03).
 * Only the fields MiMoo actually reads are mapped — MusicBrainz
 * release objects carry many more (release-group, media, labels...)
 * that this app has no use for.
 * ---
 * DTOs para la respuesta de búsqueda de releases de MusicBrainz
 * (PASO 6, H03). Solo se mapean los campos que MiMoo realmente lee —
 * los objetos release de MusicBrainz traen muchos más (release-group,
 * media, labels...) que esta app no usa.
 */

data class MusicBrainzSearchResponse(
    @SerializedName("releases") val releases: List<MusicBrainzRelease> = emptyList(),
)

/**
 * `artist-credit` and `date` are returned by the search endpoint
 * (`/ws/2/release/?query=...`) directly, without needing `inc=` —
 * unlike the release lookup endpoint (`lookupRelease`), which does
 * need `inc=` for extra data. Verified against the official examples
 * (musicbrainz.org/doc/MusicBrainz_API/Examples, 2026-07-02): a
 * release JSON object carries `artist-credit: [{ name, ... }]` and
 * `date: "YYYY-MM-DD"` (or a shorter partial date) at the top level.
 * ---
 * `artist-credit` y `date` los devuelve directamente el endpoint de
 * búsqueda (`/ws/2/release/?query=...`), sin necesitar `inc=` — a
 * diferencia del endpoint de lookup (`lookupRelease`), que sí lo
 * necesita para datos extra. Verificado contra los ejemplos oficiales
 * (musicbrainz.org/doc/MusicBrainz_API/Examples, 2026-07-02): un
 * objeto release en JSON trae `artist-credit: [{ name, ... }]` y
 * `date: "YYYY-MM-DD"` (o una fecha parcial más corta) a nivel raíz.
 */
data class MusicBrainzRelease(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("artist-credit") val artistCredit: List<MusicBrainzArtistCredit> = emptyList(),
    @SerializedName("date") val date: String? = null,
)

data class MusicBrainzArtistCredit(
    @SerializedName("name") val name: String,
)

/**
 * Full release detail with tracklist, used by the album lookup
 * (Hito 05) after searchReleases() has already narrowed down the
 * MBID. `length` is milliseconds and nullable — MusicBrainz does not
 * guarantee a duration for every recording, so it must never be
 * treated as 0 when matching against YouTube durations (Hito 05
 * PASO 2 would then wrongly prefer very short videos).
 * ---
 * Detalle completo del release con tracklist, usado por la búsqueda
 * de álbum (Hito 05) tras haber acotado el MBID con searchReleases().
 * `length` está en milisegundos y es nullable — MusicBrainz no
 * garantiza duración para cada grabación, así que nunca debe tratarse
 * como 0 al comparar con las duraciones de YouTube (el PASO 2 del
 * Hito 05 preferiría entonces, por error, vídeos muy cortos).
 */
data class MusicBrainzReleaseDetail(
    @SerializedName("media") val media: List<MusicBrainzMedia> = emptyList(),
)

data class MusicBrainzMedia(
    @SerializedName("tracks") val tracks: List<MusicBrainzTrack> = emptyList(),
)

data class MusicBrainzTrack(
    @SerializedName("position") val position: Int,
    @SerializedName("title") val title: String,
    @SerializedName("length") val length: Int? = null,
)

/**
 * H08 PARTE 2 (S009) -- DTOs para búsqueda y lookup de artista, usados
 * por RadioRepository para encontrar "artistas relacionados" vía
 * géneros compartidos de MusicBrainz.
 * ---
 * H08 PARTE 2 (S009) -- DTOs for artist search and lookup, used by
 * RadioRepository to find "related artists" via MusicBrainz's shared
 * genres.
 */
data class MusicBrainzArtistSearchResponse(
    @SerializedName("artists") val artists: List<MusicBrainzArtistSummary> = emptyList(),
)

data class MusicBrainzArtistSummary(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
)

data class MusicBrainzArtistDetail(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("genres") val genres: List<MusicBrainzGenre> = emptyList(),
)

data class MusicBrainzGenre(
    @SerializedName("name") val name: String,
)

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

data class MusicBrainzRelease(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String? = null,
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

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

package com.miguelaetxio.mimoo.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTOs for Radio-Browser.info (H09, S010). Fields verified against
 * the official documentation (docs.radio-browser.info /
 * api.radio-browser.info, checked live this session, not assumed
 * from the ANNEX_H09.md draft written in S009) -- only the fields
 * MiMoo actually reads are mapped, the real API returns many more per
 * station (lastchecktime, clickcount, clicktrend, ssl_error,
 * has_extended_info...) that this app has no use for.
 *
 * `stationcount` on RadioTag/RadioCountry is mapped as String -- the
 * official docs show it quoted ("6") in some examples and bare (1250)
 * in others depending on which mirror/version answers, so a Gson Int
 * field would risk a JsonSyntaxException on some responses. Parse
 * with `.toIntOrNull() ?: 0` at the call site instead of trusting a
 * fixed numeric type.
 * ---
 * DTOs para Radio-Browser.info (H09, S010). Campos verificados contra
 * la documentación oficial (comprobado en vivo esta sesión, no
 * asumido del borrador de ANNEX_H09.md escrito en S009) -- solo se
 * mapean los campos que MiMoo realmente lee.
 *
 * `stationcount` en RadioTag/RadioCountry se mapea como String -- la
 * documentación oficial lo muestra entrecomillado ("6") en algunos
 * ejemplos y sin comillas (1250) en otros según qué mirror/versión
 * responda, así que un campo Int de Gson arriesgaría
 * JsonSyntaxException en algunas respuestas. Parsear con
 * `.toIntOrNull() ?: 0` en el punto de uso en vez de confiar en un
 * tipo numérico fijo.
 */

data class RadioStation(
    @SerializedName("stationuuid") val stationUuid: String,
    @SerializedName("name") val name: String,
    @SerializedName("url_resolved") val urlResolved: String,
    @SerializedName("favicon") val favicon: String? = null,
    @SerializedName("tags") val tags: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("countrycode") val countryCode: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("codec") val codec: String? = null,
    @SerializedName("bitrate") val bitrate: Int? = null,
    @SerializedName("votes") val votes: Int? = null,
    @SerializedName("lastcheckok") val lastCheckOk: Int? = null,
)

/**
 * `/json/tags` -- no `country`/`iso_*` field, just name + count.
 * ---
 * `/json/tags` -- sin campo `country`/`iso_*`, solo nombre + cuenta.
 */
data class RadioTag(
    @SerializedName("name") val name: String,
    @SerializedName("stationcount") val stationCount: String? = null,
)

/**
 * `/json/countries` -- verified live this session: the real field is
 * `iso_3166_1`, NOT `countrycode` as the S009 draft in ANNEX_H09.md
 * assumed. `countrycode` only exists on the Station object
 * (`RadioStation.countryCode` above), not on this endpoint.
 * ---
 * `/json/countries` -- verificado en vivo esta sesión: el campo real
 * es `iso_3166_1`, NO `countrycode` como asumía el borrador de S009
 * en ANNEX_H09.md. `countrycode` solo existe en el objeto Station
 * (`RadioStation.countryCode` arriba), no en este endpoint.
 */
data class RadioCountry(
    @SerializedName("name") val name: String,
    @SerializedName("iso_3166_1") val isoCode: String? = null,
    @SerializedName("stationcount") val stationCount: String? = null,
)

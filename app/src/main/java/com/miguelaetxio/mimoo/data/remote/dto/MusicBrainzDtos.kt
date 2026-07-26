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
 *
 * `country` (S010, fix del sesgo hacia música inglesa): código ISO de
 * país del artista -- campo de primer nivel, SIEMPRE presente en la
 * respuesta si MusicBrainz lo tiene, sin necesitar ningún `inc=`
 * especial (verificado en vivo esta sesión contra el ejemplo oficial
 * de Nirvana en musicbrainz.org/doc/MusicBrainz_API/Examples, que
 * trae "country":"US" con solo inc=aliases). Puede venir null -- no
 * todos los artistas tienen país registrado en MusicBrainz.
 * ---
 * H08 PARTE 2 (S009) -- DTOs for artist search and lookup, used by
 * RadioRepository to find "related artists" via MusicBrainz's shared
 * genres.
 *
 * `country` (S010, fix for the English-music bias): the artist's ISO
 * country code -- a top-level field, ALWAYS present in the response
 * if MusicBrainz has it, no special `inc=` needed (verified live this
 * session against the official Nirvana example, which shows
 * "country":"US" with just inc=aliases). Can come back null -- not
 * every artist has a country on file in MusicBrainz.
 */
data class MusicBrainzArtistSearchResponse(
    @SerializedName("artists") val artists: List<MusicBrainzArtistSummary> = emptyList(),
)

data class MusicBrainzArtistSummary(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("country") val country: String? = null,
)

/**
 * S011 -- `life-span.begin` (año de inicio de actividad del
 * artista/grupo), añadido para poder anclar la Radio también por
 * década, no solo por género+país. Petición explícita de Miguel
 * Ángel, con un caso concreto: "he puesto una canción de Alaska y
 * Dinarama y ahora me pone reguetón... si pones una canción de los
 * Beatles no es lógico que después te ponga reguetón". Igual que
 * `country`, es un campo de primer nivel que MusicBrainz siempre
 * devuelve si lo tiene, sin necesitar ningún `inc=` especial.
 */
data class MusicBrainzArtistDetail(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("genres") val genres: List<MusicBrainzGenre> = emptyList(),
    @SerializedName("country") val country: String? = null,
    @SerializedName("life-span") val lifeSpan: MusicBrainzLifeSpan? = null,
)

data class MusicBrainzLifeSpan(
    @SerializedName("begin") val begin: String? = null,
)

/**
 * S020 -- `count` es el número de votos de la comunidad para ese
 * género en ese artista. Sin él, el ancla de la Radio elegía género
 * AL AZAR entre todos los que MusicBrainz devolviera, y ese sorteo
 * decidía las siguientes horas de escucha. Documentado por MetaBrainz
 * ("puedes quedarte con los n géneros con más votos"); algunos vienen
 * a 0, así que el valor por defecto es 0 y el desempate no depende de
 * que exista.
 */
data class MusicBrainzGenre(
    @SerializedName("name") val name: String,
    @SerializedName("count") val count: Int = 0,
)

/**
 * H12 (S018) -- DTOs para el browse de release-groups de un artista
 * (`browseReleaseGroupsByArtist`). Un release-group agrupa las
 * distintas ediciones/reediciones de un mismo álbum o sencillo bajo
 * un único id estable -- es el nivel correcto para listar "álbumes de
 * un artista" sin duplicados por cada reedición.
 * ---
 * H12 (S018) -- DTOs for browsing an artist's release-groups
 * (`browseReleaseGroupsByArtist`). A release-group bundles the
 * different editions/reissues of the same album or single under one
 * stable id -- the right level for listing "an artist's albums"
 * without duplicates per reissue.
 */
data class MusicBrainzReleaseGroupSearchResponse(
    @SerializedName("release-groups") val releaseGroups: List<MusicBrainzReleaseGroup> = emptyList(),
)

data class MusicBrainzReleaseGroup(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("first-release-date") val firstReleaseDate: String? = null,
    @SerializedName("primary-type") val primaryType: String? = null,
)

/**
 * Respuesta de búsqueda de grabaciones (S023) -- se usa para fechar el
 * TEMA que arranca una sesión de Radio, no al artista.
 *
 * `first-release-date` es la primera publicación conocida de esa
 * grabación. Es la fecha que interesa: la del tema, no la de la
 * recopilación o reedición concreta que se esté escuchando.
 */
data class MusicBrainzRecordingSearchResponse(
    @SerializedName("recordings") val recordings: List<MusicBrainzRecording> = emptyList(),
)

data class MusicBrainzRecording(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String = "",
    @SerializedName("first-release-date") val firstReleaseDate: String? = null,
    @SerializedName("artist-credit") val artistCredit: List<MusicBrainzArtistCredit> = emptyList(),
)

package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Emisora de Radio-Browser.info marcada como favorita (H09, S010) --
 * concepto NUEVO y separado del favorito por pista
 * (SearchResultTrack.isFavorite): una emisora es un stream en directo,
 * no un vídeo de YouTube, así que no tiene `youtubeId` y nunca puede
 * descargarse -- solo guardar/quitar de favoritos, streaming siempre.
 *
 * Clave primaria `stationUuid` (el identificador estable que la propia
 * documentación de Radio-Browser.info exige usar en vez de "id", ver
 * RadioBrowserApiService). Se guardan también los datos mínimos para
 * pintar la fila de favoritos sin depender de que la emisora siga
 * apareciendo en una búsqueda futura (nombre, favicon, país, tags) --
 * mismo motivo por el que RadioStation ya trae esos campos.
 * ---
 * Radio-Browser.info station marked as favorite (H09, S010) -- a NEW
 * concept, separate from the per-track favorite
 * (SearchResultTrack.isFavorite): a station is a live stream, not a
 * YouTube video, so it has no `youtubeId` and can never be downloaded
 * -- favorite/unfavorite only, always streaming.
 *
 * Primary key `stationUuid` (the stable identifier Radio-Browser.info's
 * own documentation requires using instead of "id"). The minimum data
 * needed to render the favorites row is also stored, so it doesn't
 * depend on the station still showing up in some future search.
 */
@Entity(tableName = "favorite_radio_stations")
data class FavoriteRadioStation(
    @PrimaryKey val stationUuid: String,
    val name: String,
    val favicon: String?,
    val country: String?,
    val tags: String?,
)

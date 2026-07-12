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
 * RadioBrowserApiService). Se guarda también `urlResolved` -- a
 * diferencia de un vídeo de YouTube, la URL de un stream de radio no
 * caduca ni necesita resolución de token, así que basta con guardarla
 * tal cual para poder reproducir la emisora favorita en el futuro sin
 * tener que volver a buscarla -- y el resto de datos mínimos para
 * pintar la fila de favoritos sin depender de eso (nombre, favicon,
 * país, tags), mismo motivo por el que RadioStation ya trae esos
 * campos.
 * ---
 * Radio-Browser.info station marked as favorite (H09, S010) -- a NEW
 * concept, separate from the per-track favorite
 * (SearchResultTrack.isFavorite): a station is a live stream, not a
 * YouTube video, so it has no `youtubeId` and can never be downloaded
 * -- favorite/unfavorite only, always streaming.
 *
 * Primary key `stationUuid`. Also stores `urlResolved` -- unlike a
 * YouTube video, a radio stream URL doesn't expire or need token
 * resolution, so storing it as-is is enough to play the favorited
 * station later without searching for it again -- plus the minimum
 * data needed to render the favorites row without depending on that.
 */
@Entity(tableName = "favorite_radio_stations")
data class FavoriteRadioStation(
    @PrimaryKey val stationUuid: String,
    val name: String,
    val urlResolved: String,
    val favicon: String?,
    val country: String?,
    val tags: String?,
)

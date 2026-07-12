package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.FavoriteRadioStationDao
import com.miguelaetxio.mimoo.data.local.entity.FavoriteRadioStation
import com.miguelaetxio.mimoo.data.remote.dto.RadioStation
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Favoritos de EMISORA (H09, S010) -- petición explícita de Miguel
 * Ángel, concepto separado del favorito por pista
 * (SearchResultTrackRepository.updateFavorite()): una emisora es un
 * stream en directo, nunca se descarga.
 * ---
 * STATION-level favorites (H09, S010) -- explicit request from Miguel
 * Ángel, a separate concept from the per-track favorite: a station is
 * a live stream, never downloaded.
 */
@Singleton
class FavoriteRadioStationRepository @Inject constructor(
    private val dao: FavoriteRadioStationDao,
) {
    fun getAll(): Flow<List<FavoriteRadioStation>> = dao.getAll()

    suspend fun getAllOnce(): List<FavoriteRadioStation> = dao.getAllOnce()

    fun getAllUuids(): Flow<List<String>> = dao.getAllUuids()

    suspend fun toggle(station: RadioStation) {
        if (dao.isFavorite(station.stationUuid)) {
            dao.delete(
                FavoriteRadioStation(
                    stationUuid = station.stationUuid,
                    name = station.name,
                    urlResolved = station.urlResolved,
                    favicon = station.favicon,
                    country = station.country,
                    tags = station.tags,
                ),
            )
        } else {
            dao.insert(
                FavoriteRadioStation(
                    stationUuid = station.stationUuid,
                    name = station.name,
                    urlResolved = station.urlResolved,
                    favicon = station.favicon,
                    country = station.country,
                    tags = station.tags,
                ),
            )
        }
    }
}

/**
 * De vuelta a RadioStation -- para poder reutilizar
 * RadioBrowserViewModel.playStation() tal cual con una favorita
 * guardada, sin duplicar la lógica de reproducción. `votes` y
 * `lastCheckOk` no se guardaron (no hacen falta para reproducir ni
 * para pintar la fila) -- lastCheckOk se rellena a 1 a propósito: si
 * está en favoritos es porque el usuario ya la quiso guardar, no tiene
 * sentido volver a filtrarla por disponibilidad aquí.
 * ---
 * Back to RadioStation -- so RadioBrowserViewModel.playStation() can be
 * reused as-is with a saved favorite. lastCheckOk is filled with 1 on
 * purpose: if it's in favorites the user already chose to keep it,
 * re-filtering it by availability here doesn't make sense.
 */
fun FavoriteRadioStation.toRadioStation() = RadioStation(
    stationUuid = stationUuid,
    name = name,
    urlResolved = urlResolved,
    favicon = favicon,
    tags = tags,
    country = country,
    countryCode = null,
    language = null,
    codec = null,
    bitrate = null,
    votes = null,
    lastCheckOk = 1,
)

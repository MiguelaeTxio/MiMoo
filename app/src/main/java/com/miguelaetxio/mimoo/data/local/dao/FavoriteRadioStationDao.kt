package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miguelaetxio.mimoo.data.local.entity.FavoriteRadioStation
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteRadioStationDao {
    @Query("SELECT * FROM favorite_radio_stations")
    fun getAll(): Flow<List<FavoriteRadioStation>>

    /** Variante de una sola lectura de getAll() -- ver FavoriteAlbumDao.getAllOnce(). */
    @Query("SELECT * FROM favorite_radio_stations")
    suspend fun getAllOnce(): List<FavoriteRadioStation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteRadioStation)

    @Delete
    suspend fun delete(favorite: FavoriteRadioStation)

    /** Borrado total -- usado por la importación/sincronización de réplica completa (H07). */
    @Query("DELETE FROM favorite_radio_stations")
    suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_radio_stations WHERE stationUuid = :stationUuid)")
    suspend fun isFavorite(stationUuid: String): Boolean

    /** Set en vez de List -- consulta rápida "¿está esta pantalla llena de favoritas?" sin más vueltas. */
    @Query("SELECT stationUuid FROM favorite_radio_stations")
    fun getAllUuids(): Flow<List<String>>
}

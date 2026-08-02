package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miguelaetxio.mimoo.data.local.entity.FavoriteTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteTrackDao {
    @Query("SELECT * FROM favorite_tracks")
    fun getAll(): Flow<List<FavoriteTrack>>

    /** Variante de una sola lectura de getAll() -- ver FavoriteAlbumDao.getAllOnce(). */
    @Query("SELECT * FROM favorite_tracks")
    suspend fun getAllOnce(): List<FavoriteTrack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteTrack)

    @Delete
    suspend fun delete(favorite: FavoriteTrack)

    @Query("DELETE FROM favorite_tracks WHERE youtubeId = :youtubeId")
    suspend fun deleteById(youtubeId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tracks WHERE youtubeId = :youtubeId)")
    suspend fun isFavorite(youtubeId: String): Boolean

    /** Borrado total -- mismo patrón que FavoriteRadioStationDao.deleteAll() (réplica completa H07). */
    @Query("DELETE FROM favorite_tracks")
    suspend fun deleteAll()
}

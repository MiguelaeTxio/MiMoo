package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miguelaetxio.mimoo.data.local.entity.DislikedTrack
import kotlinx.coroutines.flow.Flow

/** H16 -- mismo patrón que FavoriteAlbumDao (clave compuesta artist+title). */
@Dao
interface DislikedTrackDao {
    @Query("SELECT * FROM disliked_tracks ORDER BY dislikedAt DESC")
    fun getAll(): Flow<List<DislikedTrack>>

    @Query("SELECT * FROM disliked_tracks")
    suspend fun getAllOnce(): List<DislikedTrack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(disliked: DislikedTrack)

    @Delete
    suspend fun delete(disliked: DislikedTrack)

    @Query("SELECT EXISTS(SELECT 1 FROM disliked_tracks WHERE artist = :artist AND title = :title)")
    suspend fun isDisliked(artist: String, title: String): Boolean

    /** Borra TODAS las filas -- usado por la importación destructiva de backup. */
    @Query("DELETE FROM disliked_tracks")
    suspend fun deleteAll()
}

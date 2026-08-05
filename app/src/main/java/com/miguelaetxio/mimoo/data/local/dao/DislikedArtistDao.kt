package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miguelaetxio.mimoo.data.local.entity.DislikedArtist
import kotlinx.coroutines.flow.Flow

/** H16 -- mismo patrón que FavoriteArtistDao. */
@Dao
interface DislikedArtistDao {
    @Query("SELECT * FROM disliked_artists ORDER BY dislikedAt DESC")
    fun getAll(): Flow<List<DislikedArtist>>

    @Query("SELECT * FROM disliked_artists")
    suspend fun getAllOnce(): List<DislikedArtist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(disliked: DislikedArtist)

    @Delete
    suspend fun delete(disliked: DislikedArtist)

    @Query("SELECT EXISTS(SELECT 1 FROM disliked_artists WHERE artist = :artist)")
    suspend fun isDisliked(artist: String): Boolean

    /** Borra TODAS las filas -- usado por la importación destructiva de backup. */
    @Query("DELETE FROM disliked_artists")
    suspend fun deleteAll()
}

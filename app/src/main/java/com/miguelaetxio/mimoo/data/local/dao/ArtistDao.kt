package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.*
import com.miguelaetxio.mimoo.data.local.entity.Artist
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAll(): Flow<List<Artist>>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getById(id: Long): Artist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artist: Artist): Long

    @Update
    suspend fun update(artist: Artist)

    @Delete
    suspend fun delete(artist: Artist)
}

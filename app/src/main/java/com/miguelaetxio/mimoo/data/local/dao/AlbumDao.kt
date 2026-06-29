package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.*
import com.miguelaetxio.mimoo.data.local.entity.Album
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY title ASC")
    fun getAll(): Flow<List<Album>>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getById(id: Long): Album?

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY year ASC")
    fun getByArtist(artistId: Long): Flow<List<Album>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: Album): Long

    @Update
    suspend fun update(album: Album)

    @Delete
    suspend fun delete(album: Album)
}

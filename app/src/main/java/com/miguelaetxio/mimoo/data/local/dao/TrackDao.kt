package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.*
import com.miguelaetxio.mimoo.data.local.entity.Track
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title ASC")
    fun getAll(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: Long): Track?

    @Query("SELECT * FROM tracks WHERE artistId = :artistId ORDER BY title ASC")
    fun getByArtist(artistId: Long): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY title ASC")
    fun getByAlbum(albumId: Long): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE downloadStatus = :status")
    fun getByStatus(status: DownloadStatus): Flow<List<Track>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: Track): Long

    @Update
    suspend fun update(track: Track)

    @Delete
    suspend fun delete(track: Track)
}

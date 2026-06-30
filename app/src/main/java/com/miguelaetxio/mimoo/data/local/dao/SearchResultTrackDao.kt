package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.*
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchResultTrackDao {
    @Query("SELECT * FROM search_result_tracks ORDER BY lastSearchedAt DESC")
    fun getAll(): Flow<List<SearchResultTrack>>

    @Query("SELECT * FROM search_result_tracks WHERE youtubeId = :youtubeId")
    suspend fun getById(youtubeId: String): SearchResultTrack?

    @Query(
        "SELECT * FROM search_result_tracks " +
        "WHERE downloadStatus = :status ORDER BY lastSearchedAt DESC"
    )
    fun getByStatus(status: DownloadStatus): Flow<List<SearchResultTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: SearchResultTrack)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<SearchResultTrack>)

    @Update
    suspend fun update(track: SearchResultTrack)

    @Delete
    suspend fun delete(track: SearchResultTrack)
}

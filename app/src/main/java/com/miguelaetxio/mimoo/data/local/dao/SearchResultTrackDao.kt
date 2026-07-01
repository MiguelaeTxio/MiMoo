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

    /**
     * Partial update: change only downloadStatus for a given track.
     * More efficient than a full @Update when only state changes.
     * ---
     * Actualización parcial: cambia solo downloadStatus para una pista.
     * Más eficiente que @Update completo cuando solo cambia el estado.
     */
    @Query(
        "UPDATE search_result_tracks " +
        "SET downloadStatus = :status " +
        "WHERE youtubeId = :youtubeId"
    )
    suspend fun updateDownloadStatus(
        youtubeId: String,
        status: DownloadStatus,
    )

    /**
     * Partial update: persists the local file path and final status
     * (DONE or ERROR) once a download job completes.
     * ---
     * Actualización parcial: persiste la ruta local del archivo y el
     * estado final (DONE o ERROR) cuando termina un trabajo de descarga.
     */
    @Query(
        "UPDATE search_result_tracks " +
        "SET filePath = :filePath, downloadStatus = :status " +
        "WHERE youtubeId = :youtubeId"
    )
    suspend fun updateDownloadResult(
        youtubeId: String,
        filePath: String,
        status: DownloadStatus,
    )

    /**
     * Partial update: sets the favorite flag for a given track
     * (PASO 4, H03).
     * ---
     * Actualización parcial: fija el marcador de favorito para una
     * pista (PASO 4, H03).
     */
    @Query(
        "UPDATE search_result_tracks " +
        "SET isFavorite = :isFavorite " +
        "WHERE youtubeId = :youtubeId"
    )
    suspend fun updateFavorite(youtubeId: String, isFavorite: Boolean)
}

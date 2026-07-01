package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.SearchResultTrackDao
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for SearchResultTrack persistence. Search results are
 * cached on arrival so the player and download flows can read the
 * same source of truth without re-querying YouTube.
 * ---
 * Repositorio de persistencia de SearchResultTrack. Los resultados de
 * búsqueda se cachean al llegar para que el reproductor y la descarga
 * lean la misma fuente de verdad sin volver a consultar YouTube.
 */
@Singleton
class SearchResultTrackRepository @Inject constructor(
    private val dao: SearchResultTrackDao,
) {
    fun getAll(): Flow<List<SearchResultTrack>> = dao.getAll()

    fun getByStatus(
        status: DownloadStatus,
    ): Flow<List<SearchResultTrack>> = dao.getByStatus(status)

    suspend fun getById(youtubeId: String): SearchResultTrack? =
        dao.getById(youtubeId)

    suspend fun cacheSearchResults(tracks: List<SearchResultTrack>) =
        dao.insertAll(tracks)

    suspend fun update(track: SearchResultTrack) = dao.update(track)

    /**
     * Updates only the downloadStatus column for the given track.
     * Called by DownloadWorker at DOWNLOADING and ERROR transitions.
     * ---
     * Actualiza solo la columna downloadStatus para la pista indicada.
     * Llamado por DownloadWorker en las transiciones a DOWNLOADING y
     * ERROR.
     */
    suspend fun updateDownloadStatus(
        youtubeId: String,
        status: DownloadStatus,
    ) = dao.updateDownloadStatus(youtubeId, status)

    /**
     * Persists the local file path and final status once a download
     * job completes successfully.
     * ---
     * Persiste la ruta local del archivo y el estado final cuando un
     * trabajo de descarga termina con éxito.
     */
    suspend fun updateDownloadResult(
        youtubeId: String,
        filePath: String,
        status: DownloadStatus,
    ) = dao.updateDownloadResult(youtubeId, filePath, status)
}

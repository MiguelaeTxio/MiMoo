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
     * Deletes a track row entirely (PASO 5, H03). Used only for
     * synthetic rows (from LibraryReconciler) that have no real
     * youtubeId to fall back to — there is nothing to re-download.
     * ---
     * Elimina una fila de pista por completo (PASO 5, H03). Se usa
     * solo para filas sintéticas (de LibraryReconciler) que no tienen
     * un youtubeId real al que volver — no hay nada que re-descargar.
     */
    suspend fun delete(track: SearchResultTrack) = dao.delete(track)

    /**
     * Clears the download (filePath -> null, downloadStatus ->
     * PENDING) for a real, search-originated track, keeping the row
     * itself so it can be re-downloaded later (PASO 5, H03).
     * ---
     * Limpia la descarga (filePath -> null, downloadStatus ->
     * PENDING) de una pista real originada de una búsqueda,
     * conservando la fila para que pueda volver a descargarse más
     * adelante (PASO 5, H03).
     */
    suspend fun clearDownload(youtubeId: String) {
        val track = dao.getById(youtubeId) ?: return
        dao.update(
            track.copy(filePath = null, downloadStatus = DownloadStatus.PENDING)
        )
    }

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

    /**
     * Sets the favorite flag for a given track (PASO 4, H03).
     * ---
     * Fija el marcador de favorito para una pista (PASO 4, H03).
     */
    suspend fun updateFavorite(youtubeId: String, isFavorite: Boolean) =
        dao.updateFavorite(youtubeId, isFavorite)

    /**
     * Persists a resolved cover art URL for every track of the given
     * artist+album (PASO 6, H03).
     * ---
     * Persiste una URL de carátula resuelta para todas las pistas del
     * artista+álbum indicado (PASO 6, H03).
     */
    suspend fun updateCoverArtForAlbum(
        artist: String,
        album: String,
        coverArtUrl: String,
    ) = dao.updateCoverArtForAlbum(artist, album, coverArtUrl)
}

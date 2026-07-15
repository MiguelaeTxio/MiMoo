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

    /** H11 (S011) -- usado por ChannelCheckWorker para diferenciar "vídeo nuevo" de "ya existía". Ya existía en el DAO, solo faltaba exponerlo aquí. */
    suspend fun getAllOnce(): List<SearchResultTrack> = dao.getAllOnce()

    fun getByStatus(
        status: DownloadStatus,
    ): Flow<List<SearchResultTrack>> = dao.getByStatus(status)

    /** Todas las favoritas, descargadas o no (S010) -- ver SearchResultTrackDao.getFavorites(). */
    fun getFavorites(): Flow<List<SearchResultTrack>> = dao.getFavorites()

    /**
     * Pistas con una descarga QUEUED o DOWNLOADING (pantalla
     * "Descargas").
     * ---
     * Tracks with a QUEUED or DOWNLOADING download ("Descargas" screen).
     */
    fun getActiveDownloads(): Flow<List<SearchResultTrack>> =
        dao.getActiveDownloads()

    /**
     * Versión de una sola vez de getActiveDownloads() -- usada por
     * DownloadQueueManager.reconcileOrphanedDownloads() al arrancar la
     * app.
     * ---
     * One-shot version of getActiveDownloads() -- used by
     * DownloadQueueManager.reconcileOrphanedDownloads() at app startup.
     */
    suspend fun getActiveDownloadsOnce(): List<SearchResultTrack> =
        dao.getActiveDownloadsOnce()

    suspend fun getById(youtubeId: String): SearchResultTrack? =
        dao.getById(youtubeId)

    /** Versión reactiva de getById() (S010) -- ver SearchResultTrackDao.getByIdFlow(). */
    fun getByIdFlow(youtubeId: String): Flow<SearchResultTrack?> =
        dao.getByIdFlow(youtubeId)

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
     * Marca una pista como QUEUED — llamado por
     * DownloadQueueManager.enqueue() en el momento exacto en que se
     * pide la descarga, antes de que DownloadWorker haya arrancado.
     * ---
     * Marks a track as QUEUED — called by
     * DownloadQueueManager.enqueue() at the exact moment the download
     * is requested, before DownloadWorker has started.
     */
    suspend fun markQueued(youtubeId: String) = dao.markQueued(youtubeId)

    /**
     * Persiste el porcentaje real de descarga (0-100), reportado por
     * progress_hooks de yt-dlp vía Chaquopy.
     * ---
     * Persists the real download percentage (0-100), reported by
     * yt-dlp's progress_hooks via Chaquopy.
     */
    suspend fun updateDownloadProgress(youtubeId: String, progress: Int) =
        dao.updateDownloadProgress(youtubeId, progress)

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
     * Igual que updateFavorite(), pero a prueba de pistas que nunca
     * llegaron a tener fila en la base de datos (S010, reportado por
     * Miguel Ángel: "añadir a favoritos solo funciona con algunas
     * canciones de la cola"). Causa real: las pistas que añade la
     * Radio (H08, fetchOneRadioTrack) son transitorias -- viven solo
     * en la cola en memoria de PlayerManager, nunca se insertan en
     * Room. updateFavorite() es un UPDATE puro por youtubeId (ver
     * SearchResultTrackDao) -- si no hay fila con ese youtubeId, el
     * UPDATE no toca 0 filas y no pasa nada, en silencio total, sin
     * ningún error que avisara del fallo.
     *
     * Usar SIEMPRE que el favorito se marque desde el reproductor/cola
     * (PlayerBar, QueueScreen) en vez de desde una pantalla que ya
     * garantiza que la fila existe (Búsqueda, Biblioteca, donde el
     * propio SearchResultTrack ya viene de una fila real de Room).
     * ---
     * Same as updateFavorite(), but safe for tracks that never got a
     * database row at all (S010). Real cause: tracks Radio adds (H08,
     * fetchOneRadioTrack) are transient -- they only live in
     * PlayerManager's in-memory queue, never inserted into Room.
     * updateFavorite() is a plain UPDATE by youtubeId -- if no row
     * exists, it silently updates 0 rows, with no error to signal the
     * failure.
     *
     * ALWAYS use when the favorite is toggled from the player/queue
     * (PlayerBar, QueueScreen) instead of from a screen that already
     * guarantees the row exists (Search, Library).
     */
    suspend fun setFavoriteEnsuringRow(
        youtubeId: String,
        isFavorite: Boolean,
        title: String,
        channelTitle: String,
        artist: String?,
    ) {
        if (dao.getById(youtubeId) != null) {
            dao.updateFavorite(youtubeId, isFavorite)
        } else {
            dao.insert(
                SearchResultTrack(
                    youtubeId = youtubeId,
                    title = title,
                    channelTitle = channelTitle,
                    durationSeconds = 0,
                    thumbnailUrl = null,
                    artist = artist,
                    isFavorite = isFavorite,
                ),
            )
        }
    }

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

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
     * Actualiza varias filas en una sola transacción -- ver
     * `SearchResultTrackDao.updateAll()`.
     */
    suspend fun updateAll(tracks: List<SearchResultTrack>) = dao.updateAll(tracks)

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
     * S025 -- igual que markQueued(), pero a prueba de pistas que nunca
     * llegaron a tener fila en Room. Fallo reportado por Miguel Ángel:
     * *"el botón de descargar del reproductor no descarga. Pulsas y no
     * descarga nada."*
     *
     * Es EXACTAMENTE el mismo fallo que S010 encontró y arregló para el
     * botón de favorito (ver setFavoriteEnsuringRow más abajo), solo
     * que al de descarga nunca se le dio el mismo tratamiento. Las
     * pistas que añade la Radio (H08, fetchOneRadioTrack) y las que
     * llegan de MusicBrainz en H12 son transitorias: viven solo en la
     * cola en memoria de PlayerManager, sin fila en Room. Y TODAS las
     * escrituras de descarga del DAO son UPDATE puros por youtubeId
     * (markQueued, updateDownloadStatus, updateDownloadProgress,
     * updateDownloadResult): sin fila, actualizan cero filas en
     * silencio, sin ningún error.
     *
     * El efecto visible es justo el descrito. El botón se pinta porque
     * `downloadStatus` es null y null no es DONE/QUEUED/DOWNLOADING;
     * al pulsarlo se encola el WorkRequest y el worker incluso puede
     * bajar el archivo (no lee Room, saca todo de su inputData), pero
     * ni el estado ni la ruta del archivo se guardan en ninguna parte.
     * Ni cambia el icono, ni aparece en Biblioteca, ni figura en
     * Descargas. Desde el asiento del usuario: no pasa nada.
     *
     * Se llama desde DownloadQueueManager.enqueue(), que es el paso
     * único por el que entran los ocho llamantes (reproductor,
     * notificación, Búsqueda, Álbum, Canción, Descargas, Ajustes e
     * importación por enlace), así que cierra la fuga para todos.
     * ---
     * S025 -- same as markQueued(), but safe for tracks that never got
     * a row in Room. Exactly the same bug S010 found and fixed for the
     * favorite button; the download button never got the same
     * treatment. Every download write in the DAO is a plain UPDATE by
     * youtubeId, so with no row they all silently update zero rows: the
     * worker may even fetch the file, but nothing is ever recorded.
     */
    suspend fun markQueuedEnsuringRow(
        youtubeId: String,
        title: String,
        artist: String?,
        album: String?,
        trackPosition: Int?,
    ) {
        if (dao.getById(youtubeId) != null) {
            dao.markQueued(youtubeId)
        } else {
            dao.insert(
                SearchResultTrack(
                    youtubeId = youtubeId,
                    title = title,
                    // `channelTitle` no es nulable en la entidad y aquí
                    // no siempre se conoce: en el reproductor el propio
                    // enqueue() ya resuelve `artist` como
                    // `artist ?: channelTitle ?: title`, así que este
                    // es el mejor dato disponible en este punto.
                    channelTitle = artist.orEmpty(),
                    durationSeconds = 0,
                    thumbnailUrl = null,
                    artist = artist,
                    album = album,
                    trackPosition = trackPosition,
                    downloadStatus = DownloadStatus.QUEUED,
                ),
            )
        }
    }

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
     * S053 -- mismo problema de fondo que `setFavoriteEnsuringRow()`
     * (S010), esta vez para "añadir a lista": crash real reportado por
     * Miguel Ángel (`SQLiteConstraintException: FOREIGN KEY constraint
     * failed` en `PlaylistDao.addTrackToPlaylist`). A diferencia de
     * `updateFavorite()` (un UPDATE silencioso que no hace nada si la
     * fila no existe), `PlaylistTrackCrossRef` tiene una FOREIGN KEY
     * real sobre `youtubeId` -- sin fila, el INSERT del cross-ref
     * PETA directamente en vez de fallar en silencio. Se usa desde
     * `PlaylistRepository.addTrackToPlaylist()`, siempre ANTES de
     * insertar el cross-ref.
     * ---
     * S053 -- same underlying problem as `setFavoriteEnsuringRow()`
     * (S010), this time for "add to playlist": real crash reported by
     * Miguel Ángel (`SQLiteConstraintException: FOREIGN KEY constraint
     * failed` in `PlaylistDao.addTrackToPlaylist`). Unlike
     * `updateFavorite()` (a silent UPDATE that does nothing if the row
     * doesn't exist), `PlaylistTrackCrossRef` has a real FOREIGN KEY on
     * `youtubeId` -- without a row, the cross-ref INSERT crashes
     * outright instead of failing silently. Used from
     * `PlaylistRepository.addTrackToPlaylist()`, always BEFORE
     * inserting the cross-ref.
     */
    suspend fun ensureRowExists(
        youtubeId: String,
        title: String,
        channelTitle: String,
        artist: String?,
    ) {
        if (dao.getById(youtubeId) == null) {
            dao.insert(
                SearchResultTrack(
                    youtubeId = youtubeId,
                    title = title,
                    channelTitle = channelTitle,
                    durationSeconds = 0,
                    thumbnailUrl = null,
                    artist = artist,
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

    /** S011 -- fuerza un reintento real cuando la URL guardada estaba rota. Ver comentario del DAO. */
    suspend fun clearCoverArtForAlbum(artist: String, album: String) =
        dao.clearCoverArtForAlbum(artist, album)
}

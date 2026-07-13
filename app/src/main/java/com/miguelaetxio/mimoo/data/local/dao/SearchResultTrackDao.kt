package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.*
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchResultTrackDao {
    @Query("SELECT * FROM search_result_tracks ORDER BY lastSearchedAt DESC")
    fun getAll(): Flow<List<SearchResultTrack>>

    /**
     * Variante de una sola lectura de getAll(), sin Flow -- para
     * construir un BackupBundle de exportación (H06 PASO 1), donde
     * hace falta una foto fija del repositorio en el momento de
     * exportar, no una suscripción continua.
     */
    @Query("SELECT * FROM search_result_tracks ORDER BY lastSearchedAt DESC")
    suspend fun getAllOnce(): List<SearchResultTrack>

    @Query("SELECT * FROM search_result_tracks WHERE youtubeId = :youtubeId")
    suspend fun getById(youtubeId: String): SearchResultTrack?

    /**
     * Versión reactiva de getById() (S010) -- la Biblioteca resuelve y
     * guarda la carátula (requestCoverArtIfMissing() ->
     * updateCoverArtForAlbum()) de forma asíncrona, en segundo plano.
     * Si una pista empieza a sonar antes de que eso termine,
     * getById() (una sola lectura) nunca se entera de que la carátula
     * llegó después -- necesario para que PlayerBar la muestre en
     * cuanto esté disponible.
     * ---
     * Reactive version of getById() (S010) -- the Library resolves and
     * saves cover art asynchronously, in the background. If a track
     * starts playing before that finishes, getById() (a single read)
     * never finds out the cover arrived later.
     */
    @Query("SELECT * FROM search_result_tracks WHERE youtubeId = :youtubeId")
    fun getByIdFlow(youtubeId: String): Flow<SearchResultTrack?>

    /**
     * Borra TODAS las filas -- solo para la importación destructiva de
     * H06 PASO 4 (sustitución completa del repositorio). Se llama
     * dentro de AppDatabase.withTransaction junto al borrado de las
     * otras tres tablas, nunca sola.
     */
    @Query("DELETE FROM search_result_tracks")
    suspend fun deleteAll()

    @Query(
        "SELECT * FROM search_result_tracks " +
        "WHERE downloadStatus = :status ORDER BY lastSearchedAt DESC"
    )
    fun getByStatus(status: DownloadStatus): Flow<List<SearchResultTrack>>

    /**
     * TODAS las pistas marcadas favoritas, descargadas o no (S010,
     * reportado por Miguel Ángel: una pista favoritada desde el
     * reproductor/cola sin descargar no aparecía en ningún sitio de
     * la Biblioteca, porque el resto de la pantalla solo carga
     * getByStatus(DONE) -- un catálogo de lo descargado, por diseño).
     * Esta consulta es la única fuente de la sección "Favoritos" de la
     * Biblioteca, independiente de si están descargadas.
     * ---
     * ALL tracks marked favorite, downloaded or not (S010). This is
     * the sole source for the Library's "Favorites" section,
     * independent of download status.
     */
    @Query("SELECT * FROM search_result_tracks WHERE isFavorite = 1 ORDER BY title")
    fun getFavorites(): Flow<List<SearchResultTrack>>

    /**
     * Pistas con una descarga en curso o esperando turno (pantalla
     * "Descargas"). QUEUED y DOWNLOADING son los dos únicos estados
     * que representan una descarga realmente pedida y no terminada
     * todavía — PENDING no cuenta (nunca se pidió), DONE/ERROR ya
     * terminaron.
     * ---
     * Pistas con una descarga en curso o esperando turno (pantalla
     * "Descargas"). QUEUED y DOWNLOADING son los dos únicos estados
     * que representan una descarga realmente pedida y no terminada
     * todavía — PENDING no cuenta (nunca se pidió), DONE/ERROR ya
     * terminaron.
     */
    @Query(
        "SELECT * FROM search_result_tracks " +
        "WHERE downloadStatus IN ('QUEUED', 'DOWNLOADING') " +
        "ORDER BY lastSearchedAt DESC"
    )
    fun getActiveDownloads(): Flow<List<SearchResultTrack>>

    /**
     * Misma consulta que getActiveDownloads() pero de una sola vez
     * (no Flow) — usada al arrancar la app para reconciliar contra el
     * estado real de WorkManager (ver DownloadQueueManager.
     * reconcileOrphanedDownloads()). Una fila QUEUED/DOWNLOADING puede
     * quedar huérfana si el proceso murió o el sistema canceló el
     * WorkRequest antes de que DownloadWorker llegara a DONE o ERROR
     * -- sin esta reconciliación esa fila se queda así para siempre,
     * visible en "Descargas" pero sin ningún trabajo real detrás.
     * ---
     * Same query as getActiveDownloads() but one-shot (not a Flow) --
     * used at app startup to reconcile against WorkManager's real
     * state (see DownloadQueueManager.reconcileOrphanedDownloads()). A
     * QUEUED/DOWNLOADING row can become orphaned if the process died
     * or the system cancelled the WorkRequest before DownloadWorker
     * reached DONE or ERROR -- without this reconciliation that row
     * stays that way forever, visible in "Descargas" but with no real
     * work behind it.
     */
    @Query(
        "SELECT * FROM search_result_tracks " +
        "WHERE downloadStatus IN ('QUEUED', 'DOWNLOADING')"
    )
    suspend fun getActiveDownloadsOnce(): List<SearchResultTrack>

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
     * downloadProgress se resetea a 0 en el mismo write — hoy el único
     * llamante real es DownloadWorker pasando DOWNLOADING al arrancar
     * (o ERROR al fallar), y en ambos casos un progreso previo residual
     * no tiene sentido.
     * ---
     * Actualización parcial: cambia solo downloadStatus para una pista.
     * Más eficiente que @Update completo cuando solo cambia el estado.
     * downloadProgress se resetea a 0 en la misma escritura — hoy el
     * único llamante real es DownloadWorker pasando DOWNLOADING al
     * arrancar (o ERROR al fallar), y en ambos casos un progreso previo
     * residual no tiene sentido.
     */
    @Query(
        "UPDATE search_result_tracks " +
        "SET downloadStatus = :status, downloadProgress = 0 " +
        "WHERE youtubeId = :youtubeId"
    )
    suspend fun updateDownloadStatus(
        youtubeId: String,
        status: DownloadStatus,
    )

    /**
     * Partial update: marks a track as QUEUED right when
     * DownloadQueueManager.enqueue() is called — before DownloadWorker
     * has necessarily started running. Sin esto, una pista recién
     * pedida es indistinguible en Room de una que nadie ha pedido
     * nunca (ambas en PENDING), y la pantalla "Descargas" no puede
     * mostrarla como "en cola".
     * ---
     * Actualización parcial: marca una pista como QUEUED justo cuando
     * se llama a DownloadQueueManager.enqueue() — antes de que
     * DownloadWorker haya empezado necesariamente a ejecutarse. Sin
     * esto, una pista recién pedida es indistinguible en Room de una
     * que nadie ha pedido nunca (ambas en PENDING), y la pantalla
     * "Descargas" no puede mostrarla como "en cola".
     */
    @Query(
        "UPDATE search_result_tracks " +
        "SET downloadStatus = 'QUEUED', downloadProgress = 0 " +
        "WHERE youtubeId = :youtubeId"
    )
    suspend fun markQueued(youtubeId: String)

    /**
     * Partial update: persists the real download percentage (0-100)
     * during DOWNLOADING, reported by yt-dlp's progress_hooks via
     * Chaquopy (ver DownloadWorker). Throttled at the call site, no
     * aquí — cada llamada es una escritura real.
     * ---
     * Actualización parcial: persiste el porcentaje real de descarga
     * (0-100) durante DOWNLOADING, reportado por progress_hooks de
     * yt-dlp vía Chaquopy (ver DownloadWorker). El throttling se hace
     * en el punto de llamada, no aquí — cada llamada es una escritura
     * real.
     */
    @Query(
        "UPDATE search_result_tracks " +
        "SET downloadProgress = :progress " +
        "WHERE youtubeId = :youtubeId"
    )
    suspend fun updateDownloadProgress(youtubeId: String, progress: Int)

    /**
     * Partial update: persists the local file path and final status
     * (DONE or ERROR) once a download job completes. downloadProgress
     * se fija a 100 cuando el estado final es DONE (0 en ERROR, un
     * progreso residual no tiene sentido en un fallo).
     * ---
     * Actualización parcial: persiste la ruta local del archivo y el
     * estado final (DONE o ERROR) cuando termina un trabajo de
     * descarga. downloadProgress se fija a 100 cuando el estado final
     * es DONE (0 en ERROR, un progreso residual no tiene sentido en un
     * fallo).
     */
    @Query(
        "UPDATE search_result_tracks " +
        "SET filePath = :filePath, downloadStatus = :status, " +
        "downloadProgress = CASE WHEN :status = 'DONE' THEN 100 ELSE 0 END " +
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

    /**
     * Partial update: persists the resolved cover art URL for every
     * track sharing the same artist+album, in one write (PASO 6, H03).
     * A cover belongs to the album, not to an individual track, so a
     * single MusicBrainz+CAA lookup fans out to all its tracks at
     * once instead of repeating the lookup per track.
     * ---
     * Actualización parcial: persiste la URL de carátula resuelta
     * para todas las pistas que comparten artista+álbum, en una sola
     * escritura (PASO 6, H03). Una carátula pertenece al álbum, no a
     * una pista individual, así que una sola búsqueda MusicBrainz+CAA
     * se propaga a todas sus pistas de una vez en lugar de repetir la
     * búsqueda por pista.
     */
    @Query(
        "UPDATE search_result_tracks " +
        "SET coverArtUrl = :coverArtUrl " +
        "WHERE artist = :artist AND album = :album"
    )
    suspend fun updateCoverArtForAlbum(
        artist: String,
        album: String,
        coverArtUrl: String,
    )
}

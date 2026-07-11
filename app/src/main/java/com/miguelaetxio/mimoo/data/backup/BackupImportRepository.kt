package com.miguelaetxio.mimoo.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.local.AppDatabase
import com.miguelaetxio.mimoo.data.local.dao.FavoriteAlbumDao
import com.miguelaetxio.mimoo.data.local.dao.PlaylistDao
import com.miguelaetxio.mimoo.data.local.dao.SearchResultTrackDao
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.FavoriteAlbum
import com.miguelaetxio.mimoo.data.local.entity.Playlist
import com.miguelaetxio.mimoo.data.local.entity.PlaylistTrackCrossRef
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MiMoo-Backup-Import"

/**
 * Nombres de archivos de diagnóstico que la propia app escribe en la
 * raíz SAF (ver BackupDebugLogger, NotificationDebugLogger,
 * DownloadWorker.debug_error.txt, MiMooApp.crash_log.txt) --
 * excluidos EXPLÍCITAMENTE del borrado destructivo de
 * deleteExistingPhysicalFiles(). Sin esto, una importación se
 * borraría a sí misma el propio log de diagnóstico justo en el
 * momento en que más interesa conservarlo, y de paso perdería
 * cualquier historial de depuración de otras partes de la app sin
 * relación con la música. Estos archivos no son música, así que no
 * hay ninguna razón de producto para tratarlos como parte del
 * repositorio importado/exportado.
 * ---
 * Names of diagnostic files the app itself writes at the SAF root
 * (see BackupDebugLogger, NotificationDebugLogger,
 * DownloadWorker.debug_error.txt, MiMooApp.crash_log.txt) --
 * EXPLICITLY excluded from deleteExistingPhysicalFiles()'s
 * destructive wipe. Without this, an import would delete its own
 * diagnostic log right when it matters most, and would also wipe any
 * debugging history from unrelated parts of the app. These files
 * aren't music, so there's no product reason to treat them as part of
 * the imported/exported repository.
 */
private val PRESERVED_DEBUG_FILE_NAMES = setOf(
    "backup_debug.txt",
    "notification_debug.txt",
    "debug_error.txt",
    "crash_log.txt",
)

/** Resultado de una importación, para que la UI (PASO 4) muestre un resumen. */
data class BackupImportResult(
    val importedTracks: List<SearchResultTrack>,
    val favoriteAlbumCount: Int,
    val playlistCount: Int,
)

/**
 * Ejecuta la sustitución destructiva del repositorio completo (H06
 * PASO 4, decisión de Miguel Ángel: el repositorio destino MUERE y se
 * sustituye por la copia importada -- sin fusión). Vive fuera de los
 * repositorios normales de `data/local/repository` porque cruza
 * varios DAOs dentro de una única transacción Room
 * (`AppDatabase.withTransaction`), algo que ningún repositorio
 * individual hace hoy.
 * ---
 * Runs the destructive substitution of the whole repository (H06
 * PASO 4, Miguel Ángel's decision: the destination repository DIES
 * and gets replaced by the imported copy -- no merge). Lives outside
 * the normal `data/local/repository` repositories because it spans
 * several DAOs inside a single Room transaction
 * (`AppDatabase.withTransaction`), something no individual repository
 * does today.
 */
@Singleton
class BackupImportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val trackDao: SearchResultTrackDao,
    private val favoriteAlbumDao: FavoriteAlbumDao,
    private val playlistDao: PlaylistDao,
    private val storageManager: StorageManager,
) {
    /**
     * 1) Borra los archivos de audio físicos existentes en la raíz
     * SAF (si hay una elegida) -- si no se hiciera, quedarían
     * huérfanos en disco sin fila en Room, y una reconciliación
     * futura (LibraryReconciler) los recrearía como filas sintéticas
     * SIN los metadatos importados, contradiciendo el propósito del
     * hito (ver ANNEX_H06.md PASO 4).
     * 2) Transacción Room: borra las 4 tablas y reinserta desde
     * `bundle`, con `filePath = null` / `downloadStatus = PENDING` /
     * `downloadProgress = 0` en cada pista (se redescargan todas,
     * PASO 5), y las playlists con id autogenerado nuevo (nunca
     * `originalId`).
     * ---
     * 1) Deletes existing physical audio files under the SAF root (if
     * one was chosen) -- otherwise they'd be left orphaned on disk
     * with no Room row, and a future reconciliation (LibraryReconciler)
     * would recreate them as synthetic rows WITHOUT the imported
     * metadata, defeating the hito's purpose (see ANNEX_H06.md
     * PASO 4).
     * 2) Room transaction: deletes the 4 tables and reinserts from
     * `bundle`, with `filePath = null` / `downloadStatus = PENDING` /
     * `downloadProgress = 0` on every track (all get re-downloaded,
     * PASO 5), and playlists with a fresh autogenerated id (never
     * `originalId`).
     */
    suspend fun importDestructively(bundle: BackupBundle): BackupImportResult =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "importDestructively() -- borrando archivos físicos existentes...")
            BackupDebugLogger.log(context, storageManager, "importDestructively() -- borrando archivos físicos existentes...")
            deleteExistingPhysicalFiles()
            Log.d(TAG, "importDestructively() -- archivos físicos borrados. Iniciando transacción Room...")
            BackupDebugLogger.log(context, storageManager, "importDestructively() -- archivos físicos borrados. Iniciando transacción Room...")

            lateinit var result: BackupImportResult
            database.withTransaction {
                playlistDao.deleteAllCrossRefs()
                playlistDao.deleteAllPlaylists()
                favoriteAlbumDao.deleteAll()
                trackDao.deleteAll()
                val stepTables = "importDestructively() -- 4 tablas borradas. Insertando ${bundle.tracks.size} pistas..."
                Log.d(TAG, stepTables)
                BackupDebugLogger.log(context, storageManager, stepTables)

                val newTracks = bundle.tracks.map { it.toEntity() }
                trackDao.insertAll(newTracks)

                bundle.favoriteAlbums.forEach { dto ->
                    favoriteAlbumDao.insert(FavoriteAlbum(artist = dto.artist, album = dto.album))
                }

                bundle.playlists.forEach { playlistDto ->
                    val newPlaylistId = playlistDao.insertPlaylist(
                        Playlist(name = playlistDto.name, createdAt = playlistDto.createdAt)
                    )
                    playlistDto.trackYoutubeIdsInOrder.forEachIndexed { index, youtubeId ->
                        playlistDao.addTrackToPlaylist(
                            PlaylistTrackCrossRef(
                                playlistId = newPlaylistId,
                                youtubeId = youtubeId,
                                position = index,
                            )
                        )
                    }
                }

                result = BackupImportResult(
                    importedTracks = newTracks,
                    favoriteAlbumCount = bundle.favoriteAlbums.size,
                    playlistCount = bundle.playlists.size,
                )
                val stepDone = "importDestructively() -- transacción completa: ${newTracks.size} pistas, " +
                    "${bundle.favoriteAlbums.size} favoritos, ${bundle.playlists.size} playlists"
                Log.d(TAG, stepDone)
                BackupDebugLogger.log(context, storageManager, stepDone)
            }
            result
        }

    /**
     * H07 PARTE 1 (S008, sexta vuelta) -- versión SELECTIVA de
     * "la nube gana", para la sincronización automática. Fallo real
     * reportado por Miguel Ángel: `importDestructively()` (arriba) fue
     * pensada para H06 (importación MANUAL, ocasional, "sustituye todo
     * por la copia") y hasta ahora se reutilizaba tal cual también
     * aquí -- pero en H07 eso significaba que, teniendo 29 de 43
     * pistas ya descargadas, confirmar "usar la copia de Drive"
     * borraba las 29 que ya tenía y las volvía a descargar todas, en
     * vez de traer solo las 14 que faltaban.
     *
     * Solo toca lo que realmente difiere, comparando por `youtubeId`
     * (nunca por índice/posición):
     * - Pistas que tengo `DONE` y ya no están en la copia ganadora →
     *   se borran (fila + archivo físico).
     * - Pistas de la copia ganadora que no tengo `DONE` todavía → se
     *   insertan como `PENDING` para que el llamante las encole.
     * - Pistas presentes en ambos lados → **no se tocan en absoluto**,
     *   ni la fila ni el archivo -- exactamente lo que faltaba.
     *
     * Favoritos y playlists sí se siguen reemplazando enteros (sin
     * archivos físicos de por medio, es barato y no tiene el mismo
     * problema).
     * ---
     * H07 PART 1 (S008, sixth round) -- SELECTIVE version of "cloud
     * wins", for automatic sync. Real bug reported by Miguel Ángel:
     * `importDestructively()` (above) was designed for H06 (MANUAL,
     * occasional import, "replace everything with the copy") and until
     * now was reused as-is here too -- but in H07 that meant that,
     * having 29 of 43 tracks already downloaded, confirming "use the
     * Drive copy" deleted the 29 already there and re-downloaded all
     * of them, instead of only fetching the 14 that were missing.
     *
     * Only touches what genuinely differs, comparing by `youtubeId`
     * (never by index/position):
     * - Tracks I have `DONE` that are no longer in the winning copy →
     *   deleted (row + physical file).
     * - Tracks in the winning copy I don't have `DONE` yet → inserted
     *   as `PENDING` for the caller to queue.
     * - Tracks present on both sides → **not touched at all**, neither
     *   the row nor the file -- exactly what was missing.
     *
     * Favorites and playlists are still replaced wholesale (no
     * physical files involved, cheap, doesn't have the same problem).
     */
    suspend fun applyCloudWinsTargeted(bundle: BackupBundle): BackupImportResult =
        withContext(Dispatchers.IO) {
            val localTracks = trackDao.getAllOnce()
            val localDoneByYoutubeId = localTracks
                .filter { it.downloadStatus == DownloadStatus.DONE }
                .associateBy { it.youtubeId }
            val remoteIds = bundle.tracks.map { it.youtubeId }.toSet()

            val toDelete = localDoneByYoutubeId.values.filter { it.youtubeId !in remoteIds }
            val toDownloadDtos = bundle.tracks.filter { it.youtubeId !in localDoneByYoutubeId.keys }
            val newRows = toDownloadDtos.map { it.toEntity() }

            val stepMsg = "applyCloudWinsTargeted() -- ${toDelete.size} pista(s) a borrar, " +
                "${newRows.size} a descargar, " +
                "${localDoneByYoutubeId.size - toDelete.size} sin tocar"
            Log.d(TAG, stepMsg)
            BackupDebugLogger.log(context, storageManager, stepMsg)

            toDelete.forEach { track -> track.filePath?.let { deleteSingleFile(it) } }

            database.withTransaction {
                toDelete.forEach { trackDao.delete(it) }
                if (newRows.isNotEmpty()) {
                    trackDao.insertAll(newRows)
                }

                playlistDao.deleteAllCrossRefs()
                playlistDao.deleteAllPlaylists()
                favoriteAlbumDao.deleteAll()
                bundle.favoriteAlbums.forEach { dto ->
                    favoriteAlbumDao.insert(FavoriteAlbum(artist = dto.artist, album = dto.album))
                }
                bundle.playlists.forEach { playlistDto ->
                    val newPlaylistId = playlistDao.insertPlaylist(
                        Playlist(name = playlistDto.name, createdAt = playlistDto.createdAt)
                    )
                    playlistDto.trackYoutubeIdsInOrder.forEachIndexed { index, youtubeId ->
                        playlistDao.addTrackToPlaylist(
                            PlaylistTrackCrossRef(
                                playlistId = newPlaylistId,
                                youtubeId = youtubeId,
                                position = index,
                            )
                        )
                    }
                }
            }

            BackupImportResult(
                importedTracks = newRows,
                favoriteAlbumCount = bundle.favoriteAlbums.size,
                playlistCount = bundle.playlists.size,
            )
        }

    /** Borra un único archivo por su Uri -- fallo puntual no debe abortar el resto del diff. */
    private fun deleteSingleFile(uriString: String) {
        try {
            DocumentFile.fromSingleUri(context, Uri.parse(uriString))?.delete()
        } catch (e: Exception) {
            Log.w(TAG, "deleteSingleFile() -- no se pudo borrar $uriString", e)
        }
    }

    /**
     * Borra el CONTENIDO de la raíz SAF (no la raíz en sí -- solo sus
     * hijos). Si todavía no hay raíz elegida (`hasRootUri() ==
     * false`), no hay nada que borrar -- el primer inicio de la app
     * tras instalar limpio pedirá la carpeta como siempre.
     *
     * Recursión explícita, no una sola llamada a `delete()` sobre
     * cada hijo: no todos los proveedores SAF garantizan borrar un
     * subárbol de carpetas no vacío de una sola vez, así que se vacía
     * cada carpeta primero y solo entonces se borra ella misma --
     * mismo principio de cautela que `LibraryReconciler.pruneEmptyFolders`.
     */
    private fun deleteExistingPhysicalFiles() {
        val rootUri = storageManager.getRootUri()
        if (rootUri == null) {
            Log.d(TAG, "deleteExistingPhysicalFiles() -- sin raíz SAF elegida todavía, nada que borrar")
            return
        }
        val root = DocumentFile.fromTreeUri(context, rootUri)
        if (root == null) {
            Log.w(TAG, "deleteExistingPhysicalFiles() -- DocumentFile.fromTreeUri devolvió null para $rootUri")
            return
        }
        val children = root.listFiles().filterNot { it.name in PRESERVED_DEBUG_FILE_NAMES }
        val msg = "deleteExistingPhysicalFiles() -- borrando ${children.size} elementos bajo la raíz " +
            "(preservando los archivos de diagnóstico)"
        Log.d(TAG, msg)
        BackupDebugLogger.log(context, storageManager, msg)
        children.forEach { child -> deleteRecursively(child) }
    }

    private fun deleteRecursively(doc: DocumentFile) {
        if (doc.isDirectory) {
            doc.listFiles().forEach { child -> deleteRecursively(child) }
        }
        doc.delete()
    }
}

private fun TrackBackupDto.toEntity(): SearchResultTrack = SearchResultTrack(
    youtubeId = youtubeId,
    title = title,
    channelTitle = channelTitle,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    filePath = null,
    downloadStatus = DownloadStatus.PENDING,
    downloadProgress = 0,
    artist = artist,
    album = album,
    isFavorite = isFavorite,
    coverArtUrl = coverArtUrl,
    trackPosition = trackPosition,
    sourceUrl = sourceUrl,
)

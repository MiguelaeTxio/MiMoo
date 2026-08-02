package com.miguelaetxio.mimoo.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.miguelaetxio.mimoo.data.access.UiPreferencesManager
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.remote.AnchorDictionary
import com.miguelaetxio.mimoo.data.local.AppDatabase
import com.miguelaetxio.mimoo.data.local.dao.ChannelSubscriptionDao
import com.miguelaetxio.mimoo.data.local.dao.FavoriteAlbumDao
import com.miguelaetxio.mimoo.data.local.dao.FavoriteArtistDao
import com.miguelaetxio.mimoo.data.local.dao.FavoritePlaylistDao
import com.miguelaetxio.mimoo.data.local.dao.FavoriteRadioStationDao
import com.miguelaetxio.mimoo.data.local.dao.FavoriteTrackDao
import com.miguelaetxio.mimoo.data.local.dao.PlaylistDao
import com.miguelaetxio.mimoo.data.local.dao.SearchResultTrackDao
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.FavoriteAlbum
import com.miguelaetxio.mimoo.data.local.entity.FavoriteArtist
import com.miguelaetxio.mimoo.data.local.entity.FavoritePlaylist
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
    private val favoriteArtistDao: FavoriteArtistDao,
    private val favoriteTrackDao: FavoriteTrackDao,
    private val favoritePlaylistDao: FavoritePlaylistDao,
    private val playlistDao: PlaylistDao,
    private val favoriteRadioStationDao: FavoriteRadioStationDao,
    private val channelSubscriptionDao: ChannelSubscriptionDao,
    private val uiPreferencesManager: UiPreferencesManager,
    private val storageManager: StorageManager,
    // S025 -- el diccionario del ancla (H08) entra en la importación
    // FUSIONANDO, nunca reemplazando. Ver AnchorDictionary.mergeFromBackup().
    private val anchorDictionary: AnchorDictionary,
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
                favoriteArtistDao.deleteAll()
                favoriteTrackDao.deleteAll()
                favoritePlaylistDao.deleteAll()
                trackDao.deleteAll()
                favoriteRadioStationDao.deleteAll()
                channelSubscriptionDao.deleteAll()
                val stepTables = "importDestructively() -- 9 tablas borradas. Insertando ${bundle.tracks.size} pistas..."
                Log.d(TAG, stepTables)
                BackupDebugLogger.log(context, storageManager, stepTables)

                val newTracks = bundle.tracks.map { it.toEntity() }
                trackDao.insertAll(newTracks)

                bundle.favoriteAlbums.forEach { dto ->
                    favoriteAlbumDao.insert(FavoriteAlbum(artist = dto.artist, album = dto.album))
                }
                bundle.favoriteArtists.forEach { dto ->
                    favoriteArtistDao.insert(FavoriteArtist(artist = dto.artist))
                }
                bundle.favoriteTracks.forEach { dto -> favoriteTrackDao.insert(dto.toEntity()) }

                val newPlaylistIdsByName = mutableMapOf<String, Long>()
                bundle.playlists.forEach { playlistDto ->
                    val newPlaylistId = playlistDao.insertPlaylist(
                        Playlist(name = playlistDto.name, createdAt = playlistDto.createdAt)
                    )
                    newPlaylistIdsByName[playlistDto.name] = newPlaylistId
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
                bundle.favoritePlaylists.forEach { dto ->
                    val newPlaylistId = newPlaylistIdsByName[dto.playlistName] ?: return@forEach
                    favoritePlaylistDao.insert(FavoritePlaylist(playlistId = newPlaylistId))
                }

                bundle.radioStations.forEach { dto -> favoriteRadioStationDao.insert(dto.toEntity()) }
                bundle.channelSubscriptions.forEach { dto -> channelSubscriptionDao.insert(dto.toEntity()) }

                result = BackupImportResult(
                    importedTracks = newTracks,
                    favoriteAlbumCount = bundle.favoriteAlbums.size,
                    playlistCount = bundle.playlists.size,
                )
                val stepDone = "importDestructively() -- transacción completa: ${newTracks.size} pistas, " +
                    "${bundle.favoriteAlbums.size} favoritos de álbum, ${bundle.favoriteArtists.size} de artista, " +
                    "${bundle.favoriteTracks.size} de sencillo en streaming, ${bundle.playlists.size} playlists " +
                    "(${bundle.favoritePlaylists.size} favoritas), ${bundle.radioStations.size} emisoras, " +
                    "${bundle.channelSubscriptions.size} canales"
                Log.d(TAG, stepDone)
                BackupDebugLogger.log(context, storageManager, stepDone)
            }
            // Los ajustes de UI viven fuera de Room (SharedPreferences) -- se aplican
            // fuera de la transacción, como el propio UiPreferencesManager los expone.
            uiPreferencesManager.setGlassBorderEnabled(bundle.uiSettings.glassBorderEnabled)
            // S025 -- el diccionario del ancla se FUSIONA: lo que este
            // dispositivo ya sabía se conserva, y se suma lo que traiga
            // la copia. Una copia de versión 2 no trae nada y estas dos
            // listas llegan vacías, así que no hay nada que migrar.
            mergeAnchorDictionary(bundle)
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
                favoriteArtistDao.deleteAll()
                favoriteTrackDao.deleteAll()
                favoritePlaylistDao.deleteAll()
                bundle.favoriteAlbums.forEach { dto ->
                    favoriteAlbumDao.insert(FavoriteAlbum(artist = dto.artist, album = dto.album))
                }
                bundle.favoriteArtists.forEach { dto ->
                    favoriteArtistDao.insert(FavoriteArtist(artist = dto.artist))
                }
                bundle.favoriteTracks.forEach { dto -> favoriteTrackDao.insert(dto.toEntity()) }

                val newPlaylistIdsByName = mutableMapOf<String, Long>()
                bundle.playlists.forEach { playlistDto ->
                    val newPlaylistId = playlistDao.insertPlaylist(
                        Playlist(name = playlistDto.name, createdAt = playlistDto.createdAt)
                    )
                    newPlaylistIdsByName[playlistDto.name] = newPlaylistId
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
                bundle.favoritePlaylists.forEach { dto ->
                    val newPlaylistId = newPlaylistIdsByName[dto.playlistName] ?: return@forEach
                    favoritePlaylistDao.insert(FavoritePlaylist(playlistId = newPlaylistId))
                }

                // Favoritos de radio (H09) y suscripciones de canal (H11) -- H07
                // Ampliación S014/S015, "réplica total". Sin archivos físicos de por
                // medio (igual que favoritos/playlists), así que se reemplazan
                // enteros: mismo patrón, no hace falta diff selectivo por clave.
                favoriteRadioStationDao.deleteAll()
                bundle.radioStations.forEach { dto -> favoriteRadioStationDao.insert(dto.toEntity()) }
                channelSubscriptionDao.deleteAll()
                bundle.channelSubscriptions.forEach { dto -> channelSubscriptionDao.insert(dto.toEntity()) }
            }

            // Ajustes de UI (SharedPreferences, fuera de Room) -- la nube gana, igual
            // que el resto del bundle en esta ruta.
            uiPreferencesManager.setGlassBorderEnabled(bundle.uiSettings.glassBorderEnabled)
            // S025 -- el diccionario del ancla se FUSIONA: lo que este
            // dispositivo ya sabía se conserva, y se suma lo que traiga
            // la copia. Una copia de versión 2 no trae nada y estas dos
            // listas llegan vacías, así que no hay nada que migrar.
            mergeAnchorDictionary(bundle)

            val stepDone2 = "applyCloudWinsTargeted() -- ${bundle.radioStations.size} emisora(s), " +
                "${bundle.channelSubscriptions.size} canal(es), cristal=${bundle.uiSettings.glassBorderEnabled}"
            Log.d(TAG, stepDone2)
            BackupDebugLogger.log(context, storageManager, stepDone2)

            BackupImportResult(
                importedTracks = newRows,
                favoriteAlbumCount = bundle.favoriteAlbums.size,
                playlistCount = bundle.playlists.size,
            )
        }

    /**
     * Importación ADITIVA para H10 (compartir contenido entre
     * personas distintas, no entre dispositivos del mismo usuario).
     * Decisión explícita tomada al construir esto, a falta de que
     * Miguel Ángel diga lo contrario: recibir un código de
     * compartición **nunca** borra nada de lo que el receptor ya
     * tenía -- ni `importDestructively()` (H06, sustitución total,
     * pensada para "mi propio backup") ni
     * `applyCloudWinsTargeted()` (H07, sincronización entre MIS
     * propios dispositivos) encajan aquí: ambas asumen que el
     * contenido que se importa es "toda mi biblioteca real" y actúan
     * en consecuencia borrando lo que sobra. Un código de
     * compartición de otra persona es justo lo contrario -- un
     * añadido, nunca una sustitución de lo que el receptor ya tiene.
     *
     * Por pista (comparando por `youtubeId`, la clave real):
     * - Si no existe localmente: se inserta nueva, `PENDING` (se
     *   redescarga desde el mismo sitio de origen, YouTube --
     *   petición explícita de Miguel Ángel, S011: "el contenido se
     *   debe descargar del mismo sitio de donde lo descargó el
     *   original").
     * - Si ya existe localmente: se actualizan los metadatos
     *   (título/artista/álbum/carátula/posición/enlace de origen) con
     *   los del código recibido -- "réplica total" incluye ediciones y
     *   renombrados (S011) -- pero el archivo físico y el estado de
     *   descarga NUNCA se tocan si ya estaba `DONE`; solo se encola
     *   descarga si no lo estaba.
     * - `isFavorite` nunca lo desmarca: `existing.isFavorite ||
     *   incoming.isFavorite` -- recibir un código no debería
     *   quitarle un favorito propio al receptor.
     *
     * Favoritos de álbum: se insertan los que falten (clave compuesta
     * artist+album, `OnConflictStrategy.REPLACE` ya es idempotente).
     *
     * Playlists: cada playlist del código llega SIEMPRE como una
     * playlist NUEVA (id autogenerado nuevo, nunca se fusiona con una
     * existente aunque el nombre coincida) -- fusionar automáticamente
     * dentro de una playlist ya existente por nombre sería una
     * suposición arriesgada (¿y si el nombre coincide por casualidad
     * con una playlist sin relación?). Si el nombre ya existe
     * localmente, se añade el sufijo " (compartida)" para que quede
     * claro cuál es cuál sin preguntar nada.
     * ---
     * ADDITIVE import for H10 (sharing content between different
     * people, not between the same user's devices). Explicit decision
     * made while building this, absent Miguel Ángel saying otherwise:
     * receiving a share code **never** deletes anything the receiver
     * already had -- neither `importDestructively()` (H06, full
     * replace, meant for "my own backup") nor
     * `applyCloudWinsTargeted()` (H07, sync between MY OWN devices)
     * fit here -- both assume the imported content is "my whole real
     * library" and act accordingly by deleting whatever's extra. A
     * share code from someone else is the opposite -- an addition,
     * never a replacement of what the receiver already has.
     */
    /**
     * S025 -- vuelca el diccionario del ancla que trae la copia sobre
     * el de este dispositivo, sumando. Ver
     * `AnchorDictionary.mergeFromBackup()` para por qué es fusión y no
     * reemplazo, y para el ámbito de misma cuenta.
     */
    private fun mergeAnchorDictionary(bundle: BackupBundle) {
        if (bundle.anchorArtists.isEmpty() && bundle.anchorTracks.isEmpty()) return
        anchorDictionary.mergeFromBackup(
            artists = bundle.anchorArtists.map {
                AnchorDictionary.ArtistFacts(
                    artist = it.artist,
                    country = it.country,
                    genres = it.genres,
                    source = it.source.ifBlank { "copia" },
                )
            },
            tracks = bundle.anchorTracks.map {
                AnchorDictionary.TrackFacts(
                    artist = it.artist,
                    title = it.title,
                    year = it.year,
                    source = it.source.ifBlank { "copia" },
                )
            },
        )
    }

    suspend fun importSharedBundle(bundle: BackupBundle): BackupImportResult =
        withContext(Dispatchers.IO) {
            val toEnqueue = mutableListOf<SearchResultTrack>()

            database.withTransaction {
                bundle.tracks.forEach { dto ->
                    val existing = trackDao.getById(dto.youtubeId)
                    if (existing == null) {
                        val newTrack = dto.toEntity()
                        trackDao.insert(newTrack)
                        toEnqueue += newTrack
                    } else {
                        val merged = existing.copy(
                            title = dto.title,
                            channelTitle = dto.channelTitle,
                            durationSeconds = dto.durationSeconds,
                            thumbnailUrl = dto.thumbnailUrl,
                            artist = dto.artist,
                            album = dto.album,
                            isFavorite = existing.isFavorite || dto.isFavorite,
                            coverArtUrl = dto.coverArtUrl ?: existing.coverArtUrl,
                            trackPosition = dto.trackPosition,
                            sourceUrl = dto.sourceUrl ?: existing.sourceUrl,
                            // filePath/downloadStatus/downloadProgress NUNCA se tocan aquí --
                            // ver comentario de la función.
                        )
                        trackDao.insert(merged)
                        if (existing.downloadStatus != DownloadStatus.DONE) {
                            toEnqueue += merged
                        }
                    }
                }

                bundle.favoriteAlbums.forEach { dto ->
                    favoriteAlbumDao.insert(FavoriteAlbum(artist = dto.artist, album = dto.album))
                }
                bundle.favoriteArtists.forEach { dto ->
                    favoriteArtistDao.insert(FavoriteArtist(artist = dto.artist))
                }

                val existingPlaylistNames = playlistDao.getAllPlaylistsOnce().map { it.name }.toSet()
                bundle.playlists.forEach { playlistDto ->
                    val name = if (playlistDto.name in existingPlaylistNames) {
                        "${playlistDto.name} (compartida)"
                    } else {
                        playlistDto.name
                    }
                    val newPlaylistId = playlistDao.insertPlaylist(
                        Playlist(name = name, createdAt = System.currentTimeMillis())
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

            val stepDone = "importSharedBundle() -- ${bundle.tracks.size} pista(s) del código procesadas, " +
                "${toEnqueue.size} a encolar, ${bundle.favoriteAlbums.size} favoritos de álbum, " +
                "${bundle.playlists.size} playlist(s) nueva(s)"
            Log.d(TAG, stepDone)
            BackupDebugLogger.log(context, storageManager, stepDone)

            BackupImportResult(
                importedTracks = toEnqueue,
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

package com.miguelaetxio.mimoo.data.backup

import android.content.Context
import android.util.Log
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.remote.DriveApiService
import com.miguelaetxio.mimoo.data.remote.DriveUploadApiService
import com.miguelaetxio.mimoo.data.remote.dto.DriveFileCreateDto
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MiMoo-Backup-Drive"

/** Carpeta fija en Drive donde viven todos los backups de MiMoo -- decisión de ANNEX_H06.md. */
private const val BACKUP_FOLDER_NAME = "MiMoo Backups"
private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"

/**
 * Carpeta y nombre de archivo FIJOS para la copia de respaldo
 * automática (H07 PARTE 1) -- deliberadamente distinta de "MiMoo
 * Backups" (H06, snapshots manuales con timestamp): esta es una
 * carpeta y un único archivo que se sobreescriben en su sitio, no un
 * histórico.
 */
private const val SYNC_FOLDER_NAME = "MiMoo Sync"
private const val SYNC_FILE_NAME = "mimoo_sync_state.json"

/** Un backup listado desde Drive -- lo que PASO 4 (pantalla Importar) necesita mostrar. */
data class DriveBackupFile(
    val id: String,
    val name: String,
    val createdTime: String?,
)

/**
 * Combina DriveApiService (metadatos/listado/descarga) y
 * DriveUploadApiService (subida de contenido) para las operaciones
 * reales del hito: asegurar la carpeta, subir un backup, listarlos,
 * descargar uno. Recibe siempre el access token ya resuelto por la UI
 * (vía DriveAuthorizationHelper) como parámetro -- este repositorio
 * no sabe nada de OAuth, solo habla REST.
 * ---
 * Combines DriveApiService (metadata/list/download) and
 * DriveUploadApiService (content upload) for the hito's real
 * operations: ensure the folder, upload a backup, list them,
 * download one. Always receives the access token already resolved by
 * the UI (via DriveAuthorizationHelper) as a parameter -- this
 * repository knows nothing about OAuth, only REST.
 */
@Singleton
class BackupDriveRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveApi: DriveApiService,
    private val driveUploadApi: DriveUploadApiService,
    private val storageManager: StorageManager,
) {
    private fun bearer(accessToken: String) = "Bearer $accessToken"

    /**
     * Busca la carpeta `MiMoo Backups`; si no existe, la crea. Query
     * de Drive verificada en línea en S006 contra
     * developers.google.com/workspace/drive/api/guides/search-files:
     * `mimeType = '...folder' and name = '...' and trashed = false`.
     * ---
     * Looks up the `MiMoo Backups` folder; creates it if missing.
     * Drive query verified online in S006 against
     * developers.google.com/workspace/drive/api/guides/search-files:
     * `mimeType = '...folder' and name = '...' and trashed = false`.
     */
    suspend fun ensureBackupFolder(accessToken: String): String {
        val query = "mimeType = '$FOLDER_MIME_TYPE' and name = '$BACKUP_FOLDER_NAME' and trashed = false"
        val existing = driveApi.listFiles(bearer(accessToken), query = query, fields = "files(id,name)")
        existing.files.firstOrNull()?.let {
            val msg = "ensureBackupFolder() -- carpeta ya existía, id=${it.id}"
            Log.d(TAG, msg)
            BackupDebugLogger.log(context, storageManager, msg)
            return it.id
        }

        Log.d(TAG, "ensureBackupFolder() -- carpeta no existía, creándola")
        BackupDebugLogger.log(context, storageManager, "ensureBackupFolder() -- carpeta no existía, creándola")
        val created = driveApi.createFileMetadata(
            bearer(accessToken),
            DriveFileCreateDto(name = BACKUP_FOLDER_NAME, mimeType = FOLDER_MIME_TYPE),
        )
        val msg2 = "ensureBackupFolder() -- carpeta creada, id=${created.id}"
        Log.d(TAG, msg2)
        BackupDebugLogger.log(context, storageManager, msg2)
        return created.id
    }

    /**
     * Sube un backup nuevo: 1) crea el archivo vacío con nombre y
     * carpeta (metadatos), 2) rellena el contenido (PATCH
     * uploadType=media, endpoint de subida). Nombre con timestamp
     * legible, decidido en ANNEX_H06.md PASO 2:
     * `mimoo_backup_{yyyyMMdd_HHmmss}.json`.
     * ---
     * Uploads a new backup: 1) creates the empty file with name and
     * folder (metadata), 2) fills the content (PATCH
     * uploadType=media, upload endpoint). Timestamped, human-readable
     * name, decided in ANNEX_H06.md PASO 2:
     * `mimoo_backup_{yyyyMMdd_HHmmss}.json`.
     */
    suspend fun uploadBackup(accessToken: String, json: String): DriveBackupFile {
        val folderId = ensureBackupFolder(accessToken)
        val fileName = "mimoo_backup_${timestampForFileName()}.json"
        val step1 = "uploadBackup() -- creando metadatos de '$fileName' en carpeta $folderId"
        Log.d(TAG, step1)
        BackupDebugLogger.log(context, storageManager, step1)

        val createdMetadata = driveApi.createFileMetadata(
            bearer(accessToken),
            DriveFileCreateDto(name = fileName, parents = listOf(folderId)),
        )
        val step2 = "uploadBackup() -- metadatos creados, id=${createdMetadata.id}. Subiendo contenido (${json.length} chars)..."
        Log.d(TAG, step2)
        BackupDebugLogger.log(context, storageManager, step2)

        val body = json.toRequestBody(JSON_MEDIA_TYPE.toMediaType())
        val uploaded = driveUploadApi.uploadMediaContent(
            bearerToken = bearer(accessToken),
            fileId = createdMetadata.id,
            content = body,
        )
        val step3 = "uploadBackup() -- contenido subido OK para id=${uploaded.id}"
        Log.d(TAG, step3)
        BackupDebugLogger.log(context, storageManager, step3)

        return DriveBackupFile(
            id = uploaded.id,
            name = uploaded.name ?: fileName,
            createdTime = uploaded.createdTime,
        )
    }

    /** Backups disponibles en la carpeta, más recientes primero (orderBy ya lo hace DriveApiService). */
    suspend fun listBackups(accessToken: String): List<DriveBackupFile> {
        val folderId = ensureBackupFolder(accessToken)
        val query = "'$folderId' in parents and trashed = false"
        val result = driveApi.listFiles(bearer(accessToken), query = query)
        val msg = "listBackups() -- ${result.files.size} archivos encontrados en carpeta $folderId"
        Log.d(TAG, msg)
        BackupDebugLogger.log(context, storageManager, msg)
        return result.files.map { DriveBackupFile(id = it.id, name = it.name ?: it.id, createdTime = it.createdTime) }
    }

    /** Descarga el contenido JSON de un backup por su id de Drive. */
    suspend fun downloadBackupJson(accessToken: String, fileId: String): String {
        Log.d(TAG, "downloadBackupJson() -- descargando id=$fileId")
        BackupDebugLogger.log(context, storageManager, "downloadBackupJson() -- descargando id=$fileId")
        val content = driveApi.downloadFileContent(bearer(accessToken), fileId).string()
        val msg = "downloadBackupJson() -- descargados ${content.length} caracteres"
        Log.d(TAG, msg)
        BackupDebugLogger.log(context, storageManager, msg)
        return content
    }

    // ==================== H07 PARTE 1 — Sincronización automática ====================

    /** Carpeta "MiMoo Sync" -- mismo patrón que ensureBackupFolder(), carpeta distinta. */
    private suspend fun ensureSyncFolder(accessToken: String): String {
        val query = "mimeType = '$FOLDER_MIME_TYPE' and name = '$SYNC_FOLDER_NAME' and trashed = false"
        val existing = driveApi.listFiles(bearer(accessToken), query = query, fields = "files(id,name)")
        existing.files.firstOrNull()?.let { return it.id }

        val created = driveApi.createFileMetadata(
            bearer(accessToken),
            DriveFileCreateDto(name = SYNC_FOLDER_NAME, mimeType = FOLDER_MIME_TYPE),
        )
        return created.id
    }

    /**
     * Busca el archivo fijo `mimoo_sync_state.json` dentro de "MiMoo
     * Sync". Devuelve `null` si todavía no existe -- primera
     * sincronización de la cuenta, ningún dispositivo ha subido nada
     * todavía.
     * ---
     * Looks up the fixed `mimoo_sync_state.json` file inside "MiMoo
     * Sync". Returns `null` if it doesn't exist yet -- account's
     * first-ever sync, no device has uploaded anything yet.
     */
    private suspend fun findSyncFile(accessToken: String, folderId: String): DriveBackupFile? {
        val query = "'$folderId' in parents and name = '$SYNC_FILE_NAME' and trashed = false"
        val result = driveApi.listFiles(bearer(accessToken), query = query, fields = "files(id,name,createdTime)")
        return result.files.firstOrNull()?.let {
            DriveBackupFile(id = it.id, name = it.name ?: SYNC_FILE_NAME, createdTime = it.createdTime)
        }
    }

    /**
     * Sube `json` como la copia de respaldo automática, en su sitio:
     * si el archivo fijo ya existe, sobreescribe su contenido
     * (`PATCH` sobre el mismo `fileId`, reutilizando
     * `uploadMediaContent()` tal cual); si no existe todavía, lo crea
     * -- nunca genera un archivo nuevo con timestamp como
     * `uploadBackup()` (H06), siempre el mismo archivo.
     * ---
     * Uploads `json` as the automatic backup copy, in place: if the
     * fixed file already exists, overwrites its content (`PATCH` on
     * the same `fileId`, reusing `uploadMediaContent()` as-is); if it
     * doesn't exist yet, creates it -- never generates a new
     * timestamped file like `uploadBackup()` (H06), always the same
     * file.
     */
    suspend fun pushSyncState(accessToken: String, json: String) {
        val folderId = ensureSyncFolder(accessToken)
        val existing = findSyncFile(accessToken, folderId)
        val body = json.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

        val fileId = existing?.id ?: run {
            val created = driveApi.createFileMetadata(
                bearer(accessToken),
                DriveFileCreateDto(name = SYNC_FILE_NAME, parents = listOf(folderId)),
            )
            created.id
        }

        driveUploadApi.uploadMediaContent(bearerToken = bearer(accessToken), fileId = fileId, content = body)
        val msg = "pushSyncState() -- copia de respaldo automática actualizada (id=$fileId)"
        Log.d(TAG, msg)
        BackupDebugLogger.log(context, storageManager, msg)
    }

    /**
     * Descarga la copia de respaldo automática, o `null` si todavía
     * no existe (primera vez que se sincroniza esta cuenta desde
     * cualquier dispositivo).
     * ---
     * Downloads the automatic backup copy, or `null` if it doesn't
     * exist yet (first time this account syncs from any device).
     */
    suspend fun pullSyncState(accessToken: String): String? {
        val folderId = ensureSyncFolder(accessToken)
        val existing = findSyncFile(accessToken, folderId) ?: return null
        return downloadBackupJson(accessToken, existing.id)
    }

    private fun timestampForFileName(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(Date())
    }
}

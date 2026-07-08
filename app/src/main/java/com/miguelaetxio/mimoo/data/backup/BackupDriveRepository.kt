package com.miguelaetxio.mimoo.data.backup

import com.miguelaetxio.mimoo.data.remote.DriveApiService
import com.miguelaetxio.mimoo.data.remote.DriveUploadApiService
import com.miguelaetxio.mimoo.data.remote.dto.DriveFileCreateDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** Carpeta fija en Drive donde viven todos los backups de MiMoo -- decisión de ANNEX_H06.md. */
private const val BACKUP_FOLDER_NAME = "MiMoo Backups"
private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"

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
    private val driveApi: DriveApiService,
    private val driveUploadApi: DriveUploadApiService,
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
        existing.files.firstOrNull()?.let { return it.id }

        val created = driveApi.createFileMetadata(
            bearer(accessToken),
            DriveFileCreateDto(name = BACKUP_FOLDER_NAME, mimeType = FOLDER_MIME_TYPE),
        )
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

        val createdMetadata = driveApi.createFileMetadata(
            bearer(accessToken),
            DriveFileCreateDto(name = fileName, parents = listOf(folderId)),
        )

        val body = json.toRequestBody(JSON_MEDIA_TYPE.toMediaType())
        val uploaded = driveUploadApi.uploadMediaContent(
            bearerToken = bearer(accessToken),
            fileId = createdMetadata.id,
            content = body,
        )

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
        return result.files.map { DriveBackupFile(id = it.id, name = it.name ?: it.id, createdTime = it.createdTime) }
    }

    /** Descarga el contenido JSON de un backup por su id de Drive. */
    suspend fun downloadBackupJson(accessToken: String, fileId: String): String =
        driveApi.downloadFileContent(bearer(accessToken), fileId).string()

    private fun timestampForFileName(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(Date())
    }
}

package com.miguelaetxio.mimoo.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTOs para Google Drive REST API v3 (H06 PASO 2). Verificados en
 * línea en S006 (developer.android.com/identity/authorization,
 * developers.google.com/workspace/drive/api/reference/rest/v3).
 * Solo los campos que MiMoo realmente usa -- un archivo de Drive real
 * trae muchos más (webViewLink, owners, permissions...) sin uso aquí.
 * ---
 * DTOs for the Google Drive REST API v3 (H06 PASO 2). Verified online
 * in S006. Only the fields MiMoo actually uses -- a real Drive file
 * carries many more (webViewLink, owners, permissions...) unused here.
 */

data class DriveFileDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("createdTime") val createdTime: String? = null,
    @SerializedName("mimeType") val mimeType: String? = null,
)

data class DriveFileListDto(
    @SerializedName("files") val files: List<DriveFileDto> = emptyList(),
)

/**
 * Body para crear metadatos de un archivo/carpeta (POST /files, base
 * `https://www.googleapis.com/drive/v3/` -- endpoint de metadatos,
 * distinto del endpoint de subida de contenido). `mimeType` se omite
 * para archivos normales (Drive infiere) y se fija a
 * `application/vnd.google-apps.folder` solo al crear la carpeta
 * `MiMoo Backups`.
 * ---
 * Body to create file/folder metadata (POST /files, base
 * `https://www.googleapis.com/drive/v3/` -- metadata endpoint,
 * different from the content-upload endpoint). `mimeType` is omitted
 * for regular files (Drive infers it) and set to
 * `application/vnd.google-apps.folder` only when creating the
 * `MiMoo Backups` folder.
 */
data class DriveFileCreateDto(
    @SerializedName("name") val name: String,
    @SerializedName("parents") val parents: List<String>? = null,
    @SerializedName("mimeType") val mimeType: String? = null,
)

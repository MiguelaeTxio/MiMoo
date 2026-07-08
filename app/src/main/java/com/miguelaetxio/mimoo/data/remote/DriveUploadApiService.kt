package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.DriveFileDto
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit para el endpoint de SUBIDA de contenido de Drive REST v3
 * (`https://www.googleapis.com/upload/drive/v3/`) -- base URL
 * DISTINTA de DriveApiService (metadatos), Google los separa
 * deliberadamente. H06 PASO 2.
 *
 * Subida "simple" en dos pasos, no multipart/related: 1)
 * DriveApiService.createFileMetadata() crea el archivo vacío con
 * nombre+carpeta, 2) este PATCH rellena el contenido con
 * `uploadType=media` -- evita tener que montar un cuerpo
 * multipart/related a mano (Retrofit's @Multipart genera
 * multipart/form-data, que Drive no acepta para uploadType=multipart;
 * la subida simple en dos pasos no tiene ese problema).
 * ---
 * Retrofit for the Drive REST v3 CONTENT-UPLOAD endpoint
 * (`https://www.googleapis.com/upload/drive/v3/`) -- a DIFFERENT
 * base URL from DriveApiService (metadata), Google splits them on
 * purpose. H06 PASO 2.
 *
 * Two-step "simple" upload, not multipart/related: 1)
 * DriveApiService.createFileMetadata() creates the empty file with
 * name+parent, 2) this PATCH fills the content with
 * `uploadType=media` -- avoids hand-building a multipart/related
 * body (Retrofit's @Multipart produces multipart/form-data, which
 * Drive rejects for uploadType=multipart; the two-step simple upload
 * sidesteps that entirely).
 */
interface DriveUploadApiService {

    @PATCH("files/{fileId}")
    suspend fun uploadMediaContent(
        @Header("Authorization") bearerToken: String,
        @Path("fileId") fileId: String,
        @Query("uploadType") uploadType: String = "media",
        @Body content: RequestBody,
    ): DriveFileDto
}

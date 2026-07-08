package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.DriveFileCreateDto
import com.miguelaetxio.mimoo.data.remote.dto.DriveFileDto
import com.miguelaetxio.mimoo.data.remote.dto.DriveFileListDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit para el endpoint de METADATOS de Drive REST v3
 * (`https://www.googleapis.com/drive/v3/`) -- listar, crear metadatos
 * (archivo vacío o carpeta) y descargar contenido (`alt=media` vive
 * en este mismo endpoint, no en el de subida). H06 PASO 2.
 *
 * El token de autorización viaja como `@Header` por llamada -- lo
 * obtiene la UI/ViewModel vía DriveAuthorizationHelper antes de cada
 * operación, este servicio no sabe nada de OAuth.
 * ---
 * Retrofit for the Drive REST v3 METADATA endpoint
 * (`https://www.googleapis.com/drive/v3/`) -- list, create metadata
 * (empty file or folder) and download content (`alt=media` lives on
 * this same endpoint, not the upload one). H06 PASO 2.
 *
 * The authorization token travels as a per-call `@Header` -- obtained
 * by the UI/ViewModel via DriveAuthorizationHelper before each
 * operation, this service knows nothing about OAuth.
 */
interface DriveApiService {

    @GET("files")
    suspend fun listFiles(
        @Header("Authorization") bearerToken: String,
        @Query("q") query: String,
        @Query("fields") fields: String = "files(id,name,createdTime,mimeType)",
        @Query("orderBy") orderBy: String = "createdTime desc",
    ): DriveFileListDto

    @POST("files")
    suspend fun createFileMetadata(
        @Header("Authorization") bearerToken: String,
        @Body metadata: DriveFileCreateDto,
    ): DriveFileDto

    @GET("files/{fileId}")
    suspend fun getFileMetadata(
        @Header("Authorization") bearerToken: String,
        @Path("fileId") fileId: String,
        @Query("fields") fields: String = "id,name,createdTime,mimeType",
    ): DriveFileDto

    @GET("files/{fileId}")
    suspend fun downloadFileContent(
        @Header("Authorization") bearerToken: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media",
    ): ResponseBody
}

package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.UpdateManifest
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Lee el manifest.json de la última Release publicada en
 * AndroidReleases (H07 PARTE 2, PASO 2.4). Usa la URL especial de
 * GitHub `releases/latest/download/{asset}`, que redirige siempre al
 * asset de la Release más reciente sin necesitar saber su tag de
 * antemano ni pasar por la API REST de GitHub (sin rate limit propio,
 * sin autenticación -- el repo es público) -- verificado en línea
 * (S008) contra la propia Release recién publicada.
 * ---
 * Reads the manifest.json from the latest Release published in
 * AndroidReleases (H07 PART 2, STEP 2.4). Uses GitHub's special
 * `releases/latest/download/{asset}` URL, which always redirects to
 * the most recent Release's asset without needing to know its tag
 * beforehand or going through GitHub's REST API (no rate limit of its
 * own, no authentication -- the repo is public) -- verified online
 * (S008) against the actual freshly published Release.
 */
interface AppUpdateApiService {
    @GET("MiguelaeTxio/AndroidReleases/releases/latest/download/manifest.json")
    suspend fun getLatestManifest(): UpdateManifest

    /**
     * Descarga el APK desde `manifest.apkUrl` -- una URL absoluta que
     * no vive bajo la base de este Retrofit, por eso @Url la
     * sobreescribe. @Streaming evita que Retrofit intente cargar los
     * ~50 MB del APK enteros en memoria antes de devolver la
     * respuesta.
     * ---
     * Downloads the APK from `manifest.apkUrl` -- an absolute URL
     * that doesn't live under this Retrofit's base, hence @Url
     * overriding it. @Streaming avoids Retrofit trying to load the
     * whole ~50 MB APK into memory before returning the response.
     */
    @Streaming
    @GET
    suspend fun downloadApk(@Url apkUrl: String): ResponseBody
}

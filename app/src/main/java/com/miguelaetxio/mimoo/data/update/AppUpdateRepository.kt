package com.miguelaetxio.mimoo.data.update

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.miguelaetxio.mimoo.data.remote.AppUpdateApiService
import com.miguelaetxio.mimoo.data.remote.dto.UpdateManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * H07 PARTE 2, PASO 2.4-2.5: comprueba si hay una versión de MiMoo
 * más reciente publicada en AndroidReleases, descarga el APK a un
 * archivo temporal y prepara el Uri (vía FileProvider) que
 * Intent(ACTION_VIEW) necesita para lanzar la instalación -- MiMoo no
 * está en Google Play, así que Android exige el flujo estándar de
 * "fuentes desconocidas".
 * ---
 * H07 PART 2, STEP 2.4-2.5: checks whether a newer MiMoo version is
 * published on AndroidReleases, downloads the APK to a temp file, and
 * prepares the Uri (via FileProvider) that Intent(ACTION_VIEW) needs
 * to launch the install -- MiMoo isn't on Google Play, so Android
 * requires the standard "unknown sources" flow.
 */
@Singleton
class AppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appUpdateApiService: AppUpdateApiService,
) {
    /**
     * Compara `currentVersionCode` (BuildConfig.VERSION_CODE, leído
     * por el ViewModel -- este repositorio no depende de BuildConfig
     * directamente) contra el manifiesto publicado. Nunca lanza: un
     * fallo de red/parseo se traduce en UpdateCheckResult.Error con
     * un mensaje legible.
     * ---
     * Compares `currentVersionCode` (BuildConfig.VERSION_CODE, read
     * by the ViewModel -- this repository doesn't depend on
     * BuildConfig directly) against the published manifest. Never
     * throws: a network/parse failure turns into
     * UpdateCheckResult.Error with a readable message.
     */
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult {
        return try {
            val manifest = appUpdateApiService.getLatestManifest()
            if (manifest.versionCode > currentVersionCode) {
                UpdateCheckResult.UpdateAvailable(manifest)
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(
                e.message ?: "No se pudo comprobar si hay actualizaciones."
            )
        }
    }

    /**
     * Descarga `manifest.apkUrl` a `cacheDir/apk_updates/` (la única
     * subcarpeta expuesta por FileProvider, ver
     * file_provider_paths.xml) y devuelve un Uri `content://` listo
     * para `Intent(ACTION_VIEW)`. Sobrescribe cualquier descarga
     * anterior con el mismo nombre -- no hace falta conservar
     * versiones viejas de este archivo temporal.
     * ---
     * Downloads `manifest.apkUrl` to `cacheDir/apk_updates/` (the only
     * subfolder exposed by FileProvider, see
     * file_provider_paths.xml) and returns a `content://` Uri ready
     * for `Intent(ACTION_VIEW)`. Overwrites any previous download
     * with the same name -- no need to keep old versions of this temp
     * file around.
     */
    suspend fun downloadApk(manifest: UpdateManifest): Uri = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "apk_updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "MiMoo-update.apk")

        val body = appUpdateApiService.downloadApk(manifest.apkUrl)
        body.byteStream().use { input ->
            apkFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
    }
}

sealed class UpdateCheckResult {
    object UpToDate : UpdateCheckResult()
    data class UpdateAvailable(val manifest: UpdateManifest) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

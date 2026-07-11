package com.miguelaetxio.mimoo.data.backup

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.miguelaetxio.mimoo.data.download.StorageManager
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "MiMoo-Backup-Auth"

/**
 * Scope mínimo para Drive: solo los archivos que la propia app cree
 * (`drive.file`), nunca todo el Drive del usuario -- ver
 * ANNEX_H06.md, prerrequisito de Google Cloud.
 */
private const val DRIVE_FILE_SCOPE_URL = "https://www.googleapis.com/auth/drive.file"

/**
 * Resultado de pedir autorización. `Authorized` es el caso común tras
 * la primera vez (Google recuerda el permiso y no vuelve a pedir
 * confirmación al usuario, ver "Maintain ongoing access" en la doc
 * oficial). `NeedsUserConsent` solo aparece la primera vez o si el
 * usuario revocó el acceso -- la UI debe lanzar el
 * `IntentSenderRequest` con un `ActivityResultLauncher` y, al volver,
 * llamar a `extractAccessTokenFromResolution()`.
 * ---
 * Result of requesting authorization. `Authorized` is the common case
 * after the first time (Google remembers the grant and doesn't ask
 * the user again, see "Maintain ongoing access" in the official
 * docs). `NeedsUserConsent` only shows up the first time or if the
 * user revoked access -- the UI must launch the
 * `IntentSenderRequest` with an `ActivityResultLauncher` and, on
 * return, call `extractAccessTokenFromResolution()`.
 */
sealed class DriveAuthorizationOutcome {
    data class Authorized(val accessToken: String) : DriveAuthorizationOutcome()
    data class NeedsUserConsent(val intentSenderRequest: IntentSenderRequest) : DriveAuthorizationOutcome()
}

/**
 * Sin backend (MiMoo no tiene servidor remoto, ver MASTER_DOCUMENT.md
 * §1) así que nunca se pide `requestOfflineAccess`/`serverAuthCode`
 * ni se maneja refresh token -- basta con volver a llamar a
 * `authorize()` cada vez que hace falta un access token nuevo; si el
 * permiso ya fue concedido, Google lo devuelve sin ninguna
 * interacción del usuario (`hasResolution() == false`).
 * ---
 * No backend (MiMoo has no remote server, see MASTER_DOCUMENT.md §1)
 * so `requestOfflineAccess`/`serverAuthCode` and refresh-token
 * handling are never used -- calling `authorize()` again whenever a
 * fresh access token is needed is enough; if the grant already
 * exists, Google returns it with zero user interaction
 * (`hasResolution() == false`).
 */
@Singleton
class DriveAuthorizationHelper @Inject constructor(
    private val storageManager: StorageManager,
) {

    /**
     * Pide (o renueva silenciosamente) el access token para el scope
     * `drive.file`. Acepta cualquier `Context` -- `Identity.getAuthorizationClient()`
     * no exige una `Activity` real (verificado en línea, S008, contra
     * un ejemplo oficial de Google que lo llama con `context` a
     * secas), lo que permite invocar esto también desde
     * `DownloadWorker` en segundo plano, sin `Activity` disponible.
     * Si hace falta consentimiento del usuario (`NeedsUserConsent`),
     * mostrar ese diálogo SÍ requiere una `Activity` real -- eso sigue
     * siendo responsabilidad de quien llama, y en segundo plano
     * simplemente no se puede resolver (ver `AutoSyncPusher`/
     * `DownloadWorker`, que se saltan la subida en silencio en ese
     * caso). El token NUNCA se cachea aquí; cada llamada consulta a
     * Google, que a su vez cachea internamente (ver "Clear the token
     * cache" en la doc oficial si algún día hiciera falta invalidar
     * uno a mano).
     * ---
     * Requests (or silently renews) the access token for the
     * `drive.file` scope. Accepts any `Context` -- `Identity.getAuthorizationClient()`
     * doesn't require a real `Activity` (verified online, S008,
     * against an official Google example that calls it with a plain
     * `context`), which allows this to also be called from
     * `DownloadWorker` in the background, with no `Activity`
     * available. If user consent is needed (`NeedsUserConsent`),
     * showing that dialog DOES require a real `Activity` -- that
     * remains the caller's responsibility, and in the background it
     * simply can't be resolved (see `AutoSyncPusher`/`DownloadWorker`,
     * which silently skip the upload in that case). The token is
     * NEVER cached here; each call asks Google, which caches
     * internally (see "Clear the token cache" in the official docs
     * if a manual invalidation is ever needed).
     */
    suspend fun requestAuthorization(context: Context): DriveAuthorizationOutcome =
        suspendCancellableCoroutine { continuation ->
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE_URL)))
                .build()

            Log.d(TAG, "authorize(): pidiendo scope drive.file")
            BackupDebugLogger.log(context, storageManager, "authorize() -- pidiendo scope drive.file")
            Identity.getAuthorizationClient(context)
                .authorize(request)
                .addOnSuccessListener { result: AuthorizationResult ->
                    val summary = "authorize() OK -- hasResolution=${result.hasResolution()} " +
                        "accessToken=${if (result.accessToken != null) "presente" else "null"}"
                    Log.d(TAG, summary)
                    BackupDebugLogger.log(context, storageManager, summary)
                    val outcome = resultToOutcome(result)
                    if (continuation.isActive) continuation.resume(outcome)
                }
                .addOnFailureListener { e ->
                    // Mismo criterio que extractAccessTokenFromResolution
                    // (S007): si el fallo es un ApiException, volcar
                    // status completo, no solo el mensaje genérico.
                    val detail = if (e is ApiException) {
                        "authorize() FALLÓ -- statusCode=${e.statusCode} " +
                            "statusMessage=${e.status.statusMessage} status.toString()=${e.status}"
                    } else {
                        "authorize() FALLÓ -- ${e::class.java.name}"
                    }
                    Log.e(TAG, detail, e)
                    BackupDebugLogger.logError(context, storageManager, detail, e)
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
        }

    private fun resultToOutcome(result: AuthorizationResult): DriveAuthorizationOutcome {
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
                ?: error("Google devolvió hasResolution=true sin pendingIntent")
            return DriveAuthorizationOutcome.NeedsUserConsent(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            )
        }
        val token = result.accessToken
            ?: error("Autorización resuelta sin resolución pendiente pero sin accessToken")
        return DriveAuthorizationOutcome.Authorized(token)
    }

    /**
     * Tras lanzar el `IntentSenderRequest` de `NeedsUserConsent` con
     * un `ActivityResultLauncher<IntentSenderRequest>`, extrae el
     * access token del propio Intent de vuelta -- no hace falta
     * volver a llamar a `authorize()`.
     *
     * **IMPORTANTE (bug real corregido en S006):** esta función se
     * llama SIEMPRE con el `data` del resultado, sin mirar antes el
     * `resultCode` -- el ejemplo oficial de Google
     * (developer.android.com/identity/authorization) tampoco
     * comprueba `resultCode == RESULT_OK`, extrae directamente y dejar
     * que `getAuthorizationResultFromIntent` lance `ApiException` si
     * algo falló. Comprobar `resultCode` antes (como hacía la versión
     * anterior de `SettingsScreen`) trataba como "cancelado por el
     * usuario" un resultado que en realidad sí traía autorización
     * válida, y la exportación/importación se abandonaba en silencio
     * sin ningún mensaje de error.
     * ---
     * After launching the `NeedsUserConsent`'s `IntentSenderRequest`
     * with an `ActivityResultLauncher<IntentSenderRequest>`, extracts
     * the access token from the returned Intent itself -- no need to
     * call `authorize()` again.
     *
     * **IMPORTANT (real bug fixed in S006):** this function is ALWAYS
     * called with the result's `data`, without checking `resultCode`
     * first -- Google's official example
     * (developer.android.com/identity/authorization) doesn't check
     * `resultCode == RESULT_OK` either, it extracts directly and lets
     * `getAuthorizationResultFromIntent` throw `ApiException` if
     * something failed. Checking `resultCode` beforehand (as the
     * previous version of `SettingsScreen` did) treated a result that
     * actually carried valid authorization as "cancelled by the
     * user", and the export/import was silently abandoned with no
     * error message at all.
     */
    fun extractAccessTokenFromResolution(context: Context, resultData: Intent?): String {
        val result = try {
            Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(resultData)
        } catch (e: ApiException) {
            // AMPLIADO (S007): e.statusCode y e.message por sí solos no
            // bastan -- ApiException.status lleva más información real
            // que Google devuelve (statusMessage, resolution) y que
            // hasta ahora nunca llegaba a ver la luz. Se loguea todo el
            // objeto Status (su toString() incluye statusCode +
            // statusMessage + resolution si los hay) para dejar de
            // adivinar con solo el número.
            // ---
            // EXPANDED (S007): e.statusCode and e.message alone aren't
            // enough -- ApiException.status carries more real
            // information Google returns (statusMessage, resolution)
            // that was never surfaced until now. Logging the full
            // Status object (its toString() includes statusCode +
            // statusMessage + resolution if present) instead of
            // guessing from the number alone.
            val msg = "getAuthorizationResultFromIntent() FALLÓ -- " +
                "statusCode=${e.statusCode} statusMessage=${e.status.statusMessage} " +
                "status.toString()=${e.status} hasResolution=${e.status.hasResolution()}"
            Log.e(TAG, msg, e)
            BackupDebugLogger.logError(context, storageManager, msg, e)
            throw IllegalStateException(
                "Google no concedió el acceso a Drive (código ${e.statusCode}: ${e.status.statusMessage ?: e.status}).", e
            )
        }
        val summary = "getAuthorizationResultFromIntent() OK -- " +
            "accessToken=${if (result.accessToken != null) "presente" else "null"}"
        Log.d(TAG, summary)
        BackupDebugLogger.log(context, storageManager, summary)
        return result.accessToken
            ?: error("La resolución de autorización no devolvió un accessToken")
    }
}

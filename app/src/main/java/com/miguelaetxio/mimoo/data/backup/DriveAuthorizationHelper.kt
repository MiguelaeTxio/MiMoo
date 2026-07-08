package com.miguelaetxio.mimoo.data.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
class DriveAuthorizationHelper @Inject constructor() {

    /**
     * Pide (o renueva silenciosamente) el access token para el scope
     * `drive.file`. Debe llamarse con una Activity real -- el token
     * NUNCA se cachea aquí; cada llamada consulta a Google, que a su
     * vez cachea internamente (ver "Clear the token cache" en la doc
     * oficial si algún día hiciera falta invalidar uno a mano).
     * ---
     * Requests (or silently renews) the access token for the
     * `drive.file` scope. Must be called with a real Activity -- the
     * token is NEVER cached here; each call asks Google, which
     * caches internally (see "Clear the token cache" in the official
     * docs if a manual invalidation is ever needed).
     */
    suspend fun requestAuthorization(activity: Activity): DriveAuthorizationOutcome =
        suspendCancellableCoroutine { continuation ->
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE_URL)))
                .build()

            Identity.getAuthorizationClient(activity)
                .authorize(request)
                .addOnSuccessListener { result: AuthorizationResult ->
                    val outcome = resultToOutcome(result)
                    if (continuation.isActive) continuation.resume(outcome)
                }
                .addOnFailureListener { e ->
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
     * un `ActivityResultLauncher<IntentSenderRequest>` y recibir el
     * resultado, extrae el access token del propio Intent de vuelta
     * -- no hace falta volver a llamar a `authorize()`.
     * ---
     * After launching the `NeedsUserConsent`'s `IntentSenderRequest`
     * with an `ActivityResultLauncher<IntentSenderRequest>` and
     * getting the result back, extracts the access token from the
     * returned Intent itself -- no need to call `authorize()` again.
     */
    fun extractAccessTokenFromResolution(context: Context, resultData: Intent?): String {
        val result = Identity.getAuthorizationClient(context)
            .getAuthorizationResultFromIntent(resultData)
        return result.accessToken
            ?: error("La resolución de autorización no devolvió un accessToken")
    }
}

package com.miguelaetxio.mimoo.data.backup

import android.app.Activity
import android.util.Log
import com.miguelaetxio.mimoo.data.download.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MiMoo-AutoSync-Push"

/**
 * H07 PARTE 1, PASO 1.2: pieza reutilizable para que cualquier
 * ViewModel de pantalla (Biblioteca, Playlists...) suba el estado
 * actual a la copia de respaldo automática justo después de una
 * operación que añade/borra un álbum, sencillo o playlist -- sin
 * bloquear ni interrumpir al usuario.
 *
 * Deliberadamente silenciosa si hace falta consentimiento del
 * usuario: un `toggleFavoriteAlbum()` no es el momento de lanzarle a
 * Miguel Ángel un diálogo de Google de la nada. Si `requestAuthorization()`
 * devuelve `NeedsUserConsent`, esta subida puntual simplemente se
 * salta -- la sincronización real ocurrirá la próxima vez que la app
 * arranque (ver AutoSyncViewModel), que sí es el punto pensado para
 * pedir consentimiento la primera vez. Cualquier otro fallo (red,
 * Drive caído) también se traga en silencio, con un log -- un fallo
 * de sincronización en segundo plano no debe interrumpir la acción
 * real que el usuario sí pidió (marcar un favorito, crear una
 * playlist).
 * ---
 * H07 PART 1, STEP 1.2: reusable piece so any screen ViewModel
 * (Library, Playlists...) can push the current state to the
 * automatic backup copy right after an operation that adds/removes
 * an album, single, or playlist -- without blocking or interrupting
 * the user.
 *
 * Deliberately silent if user consent is needed: a
 * `toggleFavoriteAlbum()` isn't the moment to throw a Google dialog
 * at Miguel Ángel out of nowhere. If `requestAuthorization()` returns
 * `NeedsUserConsent`, this one-off push simply gets skipped -- the
 * real sync will happen next time the app starts (see
 * AutoSyncViewModel), which IS the intended place to ask for consent
 * the first time. Any other failure (network, Drive down) is also
 * swallowed silently, with a log -- a background sync failure
 * shouldn't interrupt the real action the user actually asked for
 * (marking a favorite, creating a playlist).
 */
@Singleton
class AutoSyncPusher @Inject constructor(
    private val authorizationHelper: DriveAuthorizationHelper,
    private val driveRepository: BackupDriveRepository,
    private val backupRepository: BackupRepository,
    private val storageManager: StorageManager,
) {
    suspend fun pushIfAuthorized(activity: Activity) {
        try {
            val outcome = authorizationHelper.requestAuthorization(activity)
            if (outcome !is DriveAuthorizationOutcome.Authorized) {
                Log.d(TAG, "pushIfAuthorized() -- hace falta consentimiento, se salta esta subida puntual")
                return
            }
            val bundle = backupRepository.buildCurrentBundle()
            driveRepository.pushSyncState(outcome.accessToken, backupRepository.toJson(bundle))
            val msg = "pushIfAuthorized() -- copia de respaldo automática actualizada tras cambio local"
            Log.d(TAG, msg)
            BackupDebugLogger.log(activity, storageManager, msg)
        } catch (e: Exception) {
            Log.w(TAG, "pushIfAuthorized() -- fallo silencioso, no interrumpe la acción del usuario", e)
        }
    }
}

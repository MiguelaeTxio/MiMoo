package com.miguelaetxio.mimoo.data.backup

import android.content.Context
import android.util.Log
import com.miguelaetxio.mimoo.data.download.StorageManager
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MiMoo-AutoSync-Push"

/**
 * Resultado de intentar ejecutar una mutación (añadir/borrar pista,
 * favorito o playlist) a través de [AutoSyncPusher.executeIfConnected].
 * ---
 * Result of trying to run a mutation (add/remove track, favorite, or
 * playlist) through [AutoSyncPusher.executeIfConnected].
 */
sealed class MutationOutcome {
    object Success : MutationOutcome()

    /** No había conexión -- la mutación NO se ejecutó, ni siquiera en local. */
    object NoConnection : MutationOutcome()
}

/**
 * H07 PARTE 1 (redefinición S008, regla de negocio de Miguel Ángel):
 * punto único por el que debe pasar CUALQUIER añadido o borrado de
 * pista/favorito/playlist. Dos garantías en una sola llamada:
 *
 * 1. **Sin conexión, la mutación no se ejecuta en absoluto** -- ni
 *    siquiera en local. Textual de Miguel Ángel: "si no hay conexión
 *    de red, los borrados y los añadidos no están permitidos hasta
 *    que se recupere la conexión". Así nunca puede volver a existir
 *    un estado "solo local, todavía sin subir" -- la causa raíz del
 *    bug real de las 5 pistas fantasma.
 * 2. **Con conexión, la mutación se ejecuta y se sube a Drive como
 *    parte de la misma operación** -- se sube el estado COMPLETO
 *    (`backupRepository.buildCurrentBundle()`, ya incluyendo el
 *    cambio recién aplicado), envuelto con la identidad de este
 *    dispositivo y la hora ([SyncEnvelope]).
 *
 * Si falla conseguir el token de Drive (hace falta consentimiento del
 * usuario, o cualquier otro fallo de red/Drive), la subida se salta
 * en silencio -- la mutación YA se aplicó en local con conexión
 * confirmada, así que no tiene sentido deshacerla; la próxima
 * sincronización (siguiente arranque) se encarga de reconciliar
 * cualquier desfase mediante la comparación de sobres
 * (`AutoSyncViewModel`).
 * ---
 * H07 PART 1 (S008 redefinition, Miguel Ángel's business rule):
 * single point through which ANY track/favorite/playlist addition or
 * removal must pass. Two guarantees in one call:
 *
 * 1. **Without connection, the mutation doesn't run at all** -- not
 *    even locally. Miguel Ángel's words: "if there's no network
 *    connection, deletions and additions aren't allowed until the
 *    connection is back". This way a "local-only, not yet uploaded"
 *    state can never exist again -- the root cause of the real
 *    5-ghost-tracks bug.
 * 2. **With connection, the mutation runs and gets uploaded to Drive
 *    as part of the same operation** -- the FULL state gets uploaded
 *    (`backupRepository.buildCurrentBundle()`, already including the
 *    just-applied change), wrapped with this device's identity and
 *    the time ([SyncEnvelope]).
 *
 * If getting the Drive token fails (user consent needed, or any other
 * network/Drive failure), the upload is skipped silently -- the
 * mutation was ALREADY applied locally with confirmed connectivity,
 * so undoing it makes no sense; the next sync (next startup) takes
 * care of reconciling any gap through envelope comparison
 * (`AutoSyncViewModel`).
 */
@Singleton
class AutoSyncPusher @Inject constructor(
    private val networkChecker: NetworkConnectivityChecker,
    private val authorizationHelper: DriveAuthorizationHelper,
    private val driveRepository: BackupDriveRepository,
    private val backupRepository: BackupRepository,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val storageManager: StorageManager,
) {
    /**
     * `context` puede ser una `Activity` (pantallas normales) o el
     * `applicationContext` (llamadas desde `DownloadWorker` en
     * segundo plano) -- `DriveAuthorizationHelper.requestAuthorization()`
     * acepta cualquiera de los dos, ver su comentario.
     * ---
     * `context` can be an `Activity` (normal screens) or the
     * `applicationContext` (calls from `DownloadWorker` in the
     * background) -- `DriveAuthorizationHelper.requestAuthorization()`
     * accepts either, see its comment.
     */
    suspend fun executeIfConnected(context: Context, mutation: suspend () -> Unit): MutationOutcome {
        if (!networkChecker.isConnected()) {
            Log.d(TAG, "executeIfConnected() -- sin conexión, mutación rechazada")
            return MutationOutcome.NoConnection
        }

        mutation()
        pushCurrentState(context)
        return MutationOutcome.Success
    }

    private suspend fun pushCurrentState(context: Context) {
        try {
            val outcome = authorizationHelper.requestAuthorization(context)
            if (outcome !is DriveAuthorizationOutcome.Authorized) {
                Log.d(TAG, "pushCurrentState() -- hace falta consentimiento, se salta esta subida puntual")
                return
            }
            val bundle = backupRepository.buildCurrentBundle()
            val envelope = SyncEnvelope(
                deviceId = deviceIdentityManager.deviceId,
                deviceLabel = deviceIdentityManager.deviceLabel,
                timestamp = System.currentTimeMillis(),
                bundle = bundle,
            )
            driveRepository.pushSyncState(outcome.accessToken, backupRepository.toSyncJson(envelope))
            val msg = "pushCurrentState() -- copia de respaldo automática actualizada tras cambio local"
            Log.d(TAG, msg)
            BackupDebugLogger.log(context, storageManager, msg)
        } catch (e: Exception) {
            Log.w(TAG, "pushCurrentState() -- fallo silencioso, no interrumpe la acción del usuario", e)
        }
    }
}

package com.miguelaetxio.mimoo.data.backup

import android.content.Context
import android.util.Log
import com.miguelaetxio.mimoo.data.download.CookiesManager
import com.miguelaetxio.mimoo.data.download.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MiMoo-AutoSync-Push"

/**
 * Ventana de amortiguación: las mutaciones que llegan dentro de este
 * plazo colapsan en un único push a Drive.
 */
private const val PUSH_DEBOUNCE_MS = 5_000L

/**
 * Tope de espera. Si las mutaciones no dejan de llegar (una descarga
 * masiva puede producirlas durante horas), el push sale igualmente
 * pasado este tiempo desde el último efectivo, en vez de aplazarse
 * indefinidamente.
 */
private const val MAX_PUSH_INTERVAL_MS = 60_000L

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
    private val cookiesManager: CookiesManager,
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
    /**
     * Ámbito propio del amortiguador. No puede ser el `viewModelScope`
     * de nadie: el push va diferido y debe sobrevivir a la pantalla
     * que originó la mutación.
     */
    private val pushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Un solo push construyéndose/serializándose a la vez. Es el tope
     * de memoria: sin esto, varios `DownloadWorker` en paralelo pueden
     * tener cada uno su copia completa del bundle serializado en el
     * heap al mismo tiempo.
     */
    private val pushMutex = Mutex()

    private var pendingPush: Job? = null

    @Volatile
    private var lastPushAt: Long = 0L

    /**
     * Fix real (S034, MiMoo-S34H12): esta comprobación usaba
     * `networkChecker.isConnected()` (basado en
     * `NET_CAPABILITY_VALIDATED`), el mismo indicador ya documentado
     * como propenso a falsos negativos transitorios en
     * `NetworkConnectivityChecker.hasRealInternetAccess()` -- causa
     * confirmada del bug real "Radio detenida" con conexión real
     * funcionando, corregido en su día en `RadioRepository` pero nunca
     * replicado aquí. Resultado: con conexión real funcionando pero la
     * sonda interna de Android todavía sin validar (tras salir de
     * Doze, cambio de red, etc.), cualquier añadido/borrado de
     * favorito/pista/playlist se descartaba en silencio -- ni se
     * aplicaba en local ni se avisaba al usuario, indistinguible de
     * "no persiste". Mismo fix que RadioRepository: sonda HTTP real
     * (`hasRealInternetAccess()`) en vez de la bandera de Android.
     * ---
     * Real fix (S034, MiMoo-S34H12): this check used
     * `networkChecker.isConnected()` (based on
     * `NET_CAPABILITY_VALIDATED`), the same flag already documented as
     * prone to transient false negatives in
     * `NetworkConnectivityChecker.hasRealInternetAccess()` -- the
     * confirmed cause of the real "Radio detenida" bug with real
     * connectivity working, fixed back then in `RadioRepository` but
     * never replicated here. Result: with real connectivity working
     * but Android's internal validation probe not yet done (after
     * leaving Doze, a network switch, etc.), any favorite/track/
     * playlist add or remove was silently dropped -- neither applied
     * locally nor surfaced to the user, indistinguishable from "it
     * doesn't persist". Same fix as RadioRepository: a real HTTP probe
     * (`hasRealInternetAccess()`) instead of Android's flag.
     */
    suspend fun executeIfConnected(context: Context, mutation: suspend () -> Unit): MutationOutcome {
        if (!networkChecker.hasRealInternetAccess()) {
            Log.d(TAG, "executeIfConnected() -- sin conexión real, mutación rechazada")
            return MutationOutcome.NoConnection
        }

        mutation()
        schedulePush(context)
        return MutationOutcome.Success
    }

    /**
     * S022 -- `OutOfMemoryError` real en la tablet de Miguel Ángel
     * durante la restauración de 763 pistas desde Drive:
     *
     *     Failed to allocate a 32 byte allocation with 1608096 free
     *     bytes (...) growth limit 268435456
     *
     * Hasta aquí, CADA mutación llamaba directamente a
     * `pushCurrentState()`, que construye el bundle entero
     * (`buildCurrentBundle()`) y lo serializa completo a un String
     * JSON (`toSyncJson()`). Con una biblioteca de 763 pistas eso son
     * varios MB por pasada, y durante una descarga masiva hay una
     * mutación por cada cambio de estado de cada pista, con varios
     * `DownloadWorker` corriendo en paralelo. El resultado es un
     * puñado de copias completas del bundle vivas a la vez contra un
     * heap de 256 MB. Es también lo que llenaba `backup_debug.txt` con
     * un push por segundo.
     *
     * Dos medidas, y ninguna cambia lo que acaba en Drive -- solo
     * cuántas veces y cuántas a la vez:
     *
     * - **Serialización exclusiva** (`pushMutex`): como mucho un
     *   bundle construyéndose en memoria en cada momento.
     * - **Amortiguación**: las mutaciones que llegan seguidas colapsan
     *   en un único push. Cada nueva cancela el push pendiente y
     *   reprograma. `MAX_PUSH_INTERVAL_MS` evita la inanición: si
     *   llevan más de un minuto llegando mutaciones sin parar, el
     *   siguiente push sale ya, sin esperar.
     *
     * El contexto se degrada a `applicationContext` a propósito: el
     * push es diferido y guardar una `Activity` en un campo la
     * filtraría. `requestAuthorization()` acepta los dos (ver su
     * comentario) y, si hiciera falta consentimiento interactivo, esta
     * subida puntual se salta en silencio -- que es exactamente lo que
     * ya hacía antes.
     */
    private fun schedulePush(context: Context) {
        val appContext = context.applicationContext
        pendingPush?.cancel()
        pendingPush = pushScope.launch {
            if (System.currentTimeMillis() - lastPushAt < MAX_PUSH_INTERVAL_MS) {
                delay(PUSH_DEBOUNCE_MS)
            }
            pushMutex.withLock {
                pushCurrentState(appContext)
                lastPushAt = System.currentTimeMillis()
            }
        }
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
                // Fix real (2026-07-24) -- ver comentario de
                // SyncEnvelope.cookiesTxtContent en BackupDto.kt.
                cookiesTxtContent = cookiesManager.currentContentOrNull(),
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

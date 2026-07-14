package com.miguelaetxio.mimoo.ui.sync

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.BackupBundle
import com.miguelaetxio.mimoo.data.backup.BackupDebugLogger
import com.miguelaetxio.mimoo.data.backup.BackupDriveRepository
import com.miguelaetxio.mimoo.data.backup.BackupImportRepository
import com.miguelaetxio.mimoo.data.backup.BackupMirrorRepository
import com.miguelaetxio.mimoo.data.backup.BackupRepository
import com.miguelaetxio.mimoo.data.backup.BundleComparison
import com.miguelaetxio.mimoo.data.backup.DeviceIdentityManager
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationHelper
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationOutcome
import com.miguelaetxio.mimoo.data.backup.SyncEnvelope
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.library.LibraryReconciler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MiMoo-AutoSync-Pull"

/**
 * H07 PARTE 1 (redefinición S008, regla de negocio completa de
 * Miguel Ángel). Al arrancar la app, exactamente uno de estos tres
 * casos:
 *
 * 1. **No hay copia en Drive todavía** -- este dispositivo crea la
 *    copia (con su identidad y la hora), sin preguntar nada.
 * 2. **Hay copia, y la hizo ESTE MISMO dispositivo** -- deberían
 *    coincidir. Si no coinciden, el disco local se desincronizó por
 *    su cuenta (archivos tocados a mano, etc.) -- la nube SIEMPRE
 *    manda en este caso, sin preguntar: se restaura y se avisa
 *    ([RestoredFromCloud]).
 * 3. **Hay copia, y la hizo OTRO dispositivo** -- aquí sí se
 *    pregunta explícitamente ([ConflictOtherDevice]): "¿se han
 *    añadido o eliminado pistas desde otro dispositivo?". Responder
 *    que sí sustituye local por la nube; responder que no sustituye
 *    la nube por local.
 *
 * Todo-o-nada: a diferencia del primer diseño (S008, primera vuelta,
 * `MirrorDiff` con altas/bajas independientes), aquí una de las dos
 * copias completas sustituye siempre a la otra entera
 * (`BackupImportRepository.importDestructively()`, ya construido para
 * H06) -- nunca una fusión parcial.
 * ---
 * H07 PART 1 (S008 redefinition, Miguel Ángel's full business rule).
 * On app startup, exactly one of these three cases:
 *
 * 1. **There's no copy on Drive yet** -- this device creates the
 *    copy (with its identity and the time), without asking anything.
 * 2. **There's a copy, and THIS SAME device made it** -- they should
 *    match. If they don't, the local disk got out of sync on its own
 *    (files touched by hand, etc.) -- the cloud ALWAYS wins in this
 *    case, no asking: it gets restored and a notice is shown
 *    ([RestoredFromCloud]).
 * 3. **There's a copy, and ANOTHER device made it** -- here it DOES
 *    ask explicitly ([ConflictOtherDevice]): "were tracks added or
 *    removed from another device?". Answering yes replaces local
 *    with the cloud copy; answering no replaces the cloud copy with
 *    local.
 *
 * All-or-nothing: unlike the first design (S008, first round,
 * `MirrorDiff` with independent additions/deletions), here one of the
 * two full copies always replaces the other entirely
 * (`BackupImportRepository.importDestructively()`, already built for
 * H06) -- never a partial merge.
 */
sealed class AutoSyncUiState {
    object Idle : AutoSyncUiState()
    object Checking : AutoSyncUiState()

    /** Caso 2: mismo dispositivo desincronizado -- la nube ya se restauró, solo se informa. */
    data class RestoredFromCloud(val comparison: BundleComparison) : AutoSyncUiState()

    /** Caso 3: copia de otro dispositivo -- pendiente de que Miguel Ángel responda sí/no. */
    data class ConflictOtherDevice(
        val envelope: SyncEnvelope,
        val comparison: BundleComparison,
    ) : AutoSyncUiState()

    /** Nada que hacer (copias idénticas), o resolución ya aplicada tras el caso 3. */
    data class Done(val message: String? = null) : AutoSyncUiState()

    data class Error(val message: String) : AutoSyncUiState()
}

@HiltViewModel
class AutoSyncViewModel @Inject constructor(
    private val authorizationHelper: DriveAuthorizationHelper,
    private val driveRepository: BackupDriveRepository,
    private val backupRepository: BackupRepository,
    private val mirrorRepository: BackupMirrorRepository,
    private val importRepository: BackupImportRepository,
    private val downloadQueueManager: DownloadQueueManager,
    private val libraryReconciler: LibraryReconciler,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val storageManager: StorageManager,
    @ApplicationContext private val applicationContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AutoSyncUiState>(AutoSyncUiState.Idle)
    val uiState: StateFlow<AutoSyncUiState> = _uiState.asStateFlow()

    private val _pendingConsent = MutableStateFlow<IntentSenderRequest?>(null)
    val pendingConsent: StateFlow<IntentSenderRequest?> = _pendingConsent.asStateFlow()

    /** Access token ya conseguido, guardado solo mientras se espera la respuesta del caso 3. */
    private var pendingAccessToken: String? = null

    /** Llamado una vez al arrancar MainActivity, si ya hay carpeta SAF elegida. */
    fun startAutoSync(activity: Activity) {
        if (_uiState.value != AutoSyncUiState.Idle) return
        _uiState.value = AutoSyncUiState.Checking
        viewModelScope.launch {
            try {
                when (val outcome = authorizationHelper.requestAuthorization(activity)) {
                    is DriveAuthorizationOutcome.Authorized -> runSync(outcome.accessToken)
                    is DriveAuthorizationOutcome.NeedsUserConsent ->
                        _pendingConsent.value = outcome.intentSenderRequest
                }
            } catch (e: Exception) {
                Log.w(TAG, "startAutoSync() -- fallo pidiendo autorización", e)
                _uiState.value = AutoSyncUiState.Error(
                    e.message ?: "No se pudo comprobar la sincronización con Drive."
                )
            }
        }
    }

    /** Llamado por la UI tras resolver el IntentSenderRequest de pendingConsent. */
    fun onConsentResolved(activity: Activity, resultData: Intent?) {
        _pendingConsent.value = null
        viewModelScope.launch {
            try {
                val token = authorizationHelper.extractAccessTokenFromResolution(activity, resultData)
                runSync(token)
            } catch (e: Exception) {
                Log.w(TAG, "onConsentResolved() -- fallo extrayendo el token", e)
                _uiState.value = AutoSyncUiState.Error(
                    e.message ?: "Google no concedió el acceso a Drive."
                )
            }
        }
    }

    private suspend fun runSync(accessToken: String) {
        val remoteJson = driveRepository.pullSyncState(accessToken)
        if (remoteJson == null) {
            // Caso 1: no hay copia todavía -- se crea, sin preguntar nada.
            pushAsNewEnvelope(accessToken)
            verifyDiskAndReconcile()
            _uiState.value = AutoSyncUiState.Done()
            return
        }

        val envelope = try {
            backupRepository.fromSyncJson(remoteJson)
        } catch (e: BackupRepository.BackupParseException) {
            // No hay forma útil de recuperar nada de un archivo que
            // no se puede leer (formato antiguo, corrupto, etc.) --
            // en vez de dejar a Miguel Ángel atascado con el mismo
            // error cada vez que abre la app (bug real reportado,
            // reproducido con un archivo del formato anterior a esta
            // sesión ya en Drive), se trata igual que el caso 1: se
            // sobreescribe con el estado actual de este dispositivo y
            // se sigue adelante, sin preguntar nada raro.
            // ---
            // There's no useful way to recover anything from a file
            // that can't be read (old format, corrupt, etc.) --
            // instead of leaving Miguel Ángel stuck with the same
            // error every time the app opens (real bug reported,
            // reproduced with a file from before this session's
            // format already in Drive), it's treated the same as
            // case 1: overwritten with this device's current state
            // and moves on, without asking anything odd.
            Log.w(TAG, "runSync() -- copia remota ilegible (${e.message}), se sobreescribe", e)
            pushAsNewEnvelope(accessToken)
            verifyDiskAndReconcile()
            _uiState.value = AutoSyncUiState.Done()
            return
        }

        val localBundle = backupRepository.buildCurrentBundle()
        val comparison = mirrorRepository.compare(localBundle, envelope.bundle)
        val compareMsg = "runSync() -- comparación: local=${comparison.localTrackCount} " +
            "remoto=${comparison.remoteTrackCount} idéntico=${comparison.identical} " +
            "mismoDispositivo=${envelope.deviceId == deviceIdentityManager.deviceId}"
        Log.d(TAG, compareMsg)
        BackupDebugLogger.log(applicationContext, storageManager, compareMsg)

        if (comparison.identical) {
            // H07 PARTE 1 -- fallo real señalado por Miguel Ángel:
            // que la base de datos coincida con Drive NO significa
            // que el disco coincida con la base de datos. Se
            // verifica siempre, incluso cuando no hay nada que
            // resolver contra la nube (p.ej. alguien borró un
            // archivo a mano con el explorador de archivos sin
            // tocar Room para nada).
            // ---
            // H07 PART 1 -- real gap flagged by Miguel Ángel: the
            // database matching Drive does NOT mean the disk matches
            // the database. Always verified, even when there's
            // nothing to resolve against the cloud (e.g. someone
            // deleted a file by hand with a file explorer without
            // touching Room at all).
            verifyDiskAndReconcile()
            _uiState.value = AutoSyncUiState.Done()
            return
        }

        if (envelope.deviceId == deviceIdentityManager.deviceId) {
            // Caso 2: mismo dispositivo, desincronizado -- la nube manda siempre, sin preguntar.
            restoreFromCloud(envelope.bundle)
            verifyDiskAndReconcile()
            _uiState.value = AutoSyncUiState.RestoredFromCloud(comparison)
        } else {
            // Caso 3: otro dispositivo -- se pregunta antes de tocar
            // nada (la verificación de disco se hace después de
            // resolver, en confirmCloudWins()/confirmLocalWins()).
            pendingAccessToken = accessToken
            _uiState.value = AutoSyncUiState.ConflictOtherDevice(envelope, comparison)
        }
    }

    /**
     * H07 PARTE 1 -- cierra el hueco señalado por Miguel Ángel: hasta
     * ahora todo el pipeline comparaba base de datos (local) contra
     * base de datos (Drive), sin comprobar nunca que el disco físico
     * coincidiera con lo que Room decía. Dos direcciones, ambas ya
     * construidas por separado (`LibraryReconciler`), aquí solo se
     * orquestan juntas en cada sincronización:
     *
     * 1. `verifyDiskState()`: filas `DONE` cuyo archivo ya no existe
     *    -- se marcan `PENDING` y se reencolan aquí mismo.
     * 2. `rescan()`: archivos huérfanos en disco sin fila en Room --
     *    se recuperan (mismo mecanismo que al elegir la carpeta SAF
     *    por primera vez o pulsar "Actualizar" en Biblioteca, ahora
     *    también en cada sincronización automática, no solo en esos
     *    dos momentos).
     * ---
     * H07 PART 1 -- closes the gap Miguel Ángel flagged: until now the
     * whole pipeline compared database (local) against database
     * (Drive), never checking whether the physical disk matched what
     * Room claimed. Two directions, both already built separately
     * (`LibraryReconciler`), just orchestrated together here on every
     * sync:
     *
     * 1. `verifyDiskState()`: `DONE` rows whose file no longer exists
     *    -- marked `PENDING` and re-queued right here.
     * 2. `rescan()`: orphaned files on disk with no Room row -- get
     *    recovered (same mechanism as first picking the SAF folder or
     *    tapping "Refresh" in Library, now also on every automatic
     *    sync, not just those two moments).
     */
    private suspend fun verifyDiskAndReconcile() {
        val missing = libraryReconciler.verifyDiskState()
        missing.forEach { track ->
            downloadQueueManager.enqueue(
                youtubeId = track.youtubeId,
                title = track.title,
                artist = track.artist ?: track.channelTitle,
                album = track.album,
                trackPosition = track.trackPosition,
            )
        }
        if (missing.isNotEmpty()) {
            val msg = "verifyDiskAndReconcile() -- ${missing.size} pista(s) DONE sin " +
                "archivo real, reencoladas"
            Log.d(TAG, msg)
            BackupDebugLogger.log(applicationContext, storageManager, msg)
        }

        storageManager.getRootUri()?.let { rootUri ->
            val result = libraryReconciler.rescan(rootUri)
            if (result.tracksDiscovered > 0) {
                val msg = "verifyDiskAndReconcile() -- ${result.tracksDiscovered} " +
                    "archivo(s) huérfano(s) recuperado(s) en disco"
                Log.d(TAG, msg)
                BackupDebugLogger.log(applicationContext, storageManager, msg)
            }
        }
    }

    /**
     * Caso 3, respuesta "sí, se añadieron/borraron pistas en otro
     * dispositivo" -- la nube sustituye a local.
     *
     * Fallo real detectado en logs de Miguel Ángel (2026-07-14): esta
     * función lanzaba la llamada de red sin `try/catch`, a diferencia
     * de `startAutoSync()`/`onConsentResolved()`. Un timeout real de
     * Drive (confirmado en `crash_log`) dejaba la excepción sin
     * capturar -- la app se caía, y al reabrirse volvía a preguntar
     * lo mismo desde cero, en bucle. Además, como el estado no
     * cambiaba hasta que la llamada de red terminaba, nada impedía
     * pulsar otra vez mientras la anterior seguía en vuelo (varias
     * subidas concurrentes). Fix: capturar el fallo como ya se hacía
     * en `startAutoSync()`, y pasar a `Checking` de forma síncrona en
     * cuanto se pulsa, para que el diálogo desaparezca al instante y
     * un segundo toque no dispare otra llamada.
     * ---
     * Case 3, "yes, tracks were added/removed on another device" --
     * cloud replaces local.
     *
     * Real bug found in Miguel Ángel's logs (2026-07-14): this
     * function fired the network call with no `try/catch`, unlike
     * `startAutoSync()`/`onConsentResolved()`. A real Drive timeout
     * (confirmed in `crash_log`) left the exception uncaught -- the
     * app crashed, and on reopening asked the same question again
     * from scratch, in a loop. Also, since the state didn't change
     * until the network call finished, nothing stopped a second tap
     * while the first was still in flight (multiple concurrent
     * uploads). Fix: catch the failure the same way
     * `startAutoSync()` already does, and move to `Checking`
     * synchronously the moment it's tapped, so the dialog disappears
     * instantly and a second tap can't fire another call.
     */
    fun confirmCloudWins() {
        val state = _uiState.value as? AutoSyncUiState.ConflictOtherDevice ?: return
        _uiState.value = AutoSyncUiState.Checking
        viewModelScope.launch {
            try {
                restoreFromCloud(state.envelope.bundle)
                verifyDiskAndReconcile()
                pendingAccessToken = null
                _uiState.value = AutoSyncUiState.Done(
                    "Restaurado desde la copia de ${state.envelope.deviceLabel}."
                )
            } catch (e: Exception) {
                Log.w(TAG, "confirmCloudWins() -- fallo restaurando desde Drive", e)
                _uiState.value = AutoSyncUiState.Error(
                    e.message ?: "No se pudo restaurar la copia de Drive."
                )
            }
        }
    }

    /**
     * Reconcilia el repositorio local contra `bundle` de forma
     * SELECTIVA (`BackupImportRepository.applyCloudWinsTargeted()`,
     * S008 sexta vuelta -- nunca `importDestructively()`, esa es solo
     * para H06 manual) y encola la descarga únicamente de las pistas
     * nuevas resultantes -- las que ya se tenían `DONE` no se tocan ni
     * se redescargan.
     * ---
     * Reconciles the local repository against `bundle` SELECTIVELY
     * (`BackupImportRepository.applyCloudWinsTargeted()`, S008 sixth
     * round -- never `importDestructively()`, that one's only for
     * manual H06) and queues the download of only the resulting new
     * tracks -- the ones already `DONE` aren't touched or
     * re-downloaded.
     */
    private suspend fun restoreFromCloud(bundle: BackupBundle) {
        val result = importRepository.applyCloudWinsTargeted(bundle)
        val step = "restoreFromCloud() -- encolando ${result.importedTracks.size} descarga(s)..."
        Log.d(TAG, step)
        BackupDebugLogger.log(applicationContext, storageManager, step)
        result.importedTracks.forEach { track ->
            downloadQueueManager.enqueue(
                youtubeId = track.youtubeId,
                title = track.title,
                artist = track.artist ?: track.channelTitle,
                album = track.album,
                trackPosition = track.trackPosition,
            )
        }
        val done = "restoreFromCloud() -- ${result.importedTracks.size} descarga(s) encoladas"
        Log.d(TAG, done)
        BackupDebugLogger.log(applicationContext, storageManager, done)
    }

    /**
     * Caso 3, respuesta "no" -- local sustituye a la nube (y se
     * sube). Mismo fix que `confirmCloudWins()` -- ver comentario ahí
     * para el diagnóstico completo (2026-07-14).
     */
    fun confirmLocalWins() {
        val state = _uiState.value as? AutoSyncUiState.ConflictOtherDevice ?: return
        val accessToken = pendingAccessToken ?: return
        _uiState.value = AutoSyncUiState.Checking
        viewModelScope.launch {
            try {
                verifyDiskAndReconcile()
                pushAsNewEnvelope(accessToken)
                pendingAccessToken = null
                _uiState.value = AutoSyncUiState.Done("Tu copia local ha sustituido a la de Drive.")
            } catch (e: Exception) {
                Log.w(TAG, "confirmLocalWins() -- fallo subiendo la copia local", e)
                _uiState.value = AutoSyncUiState.Error(
                    e.message ?: "No se pudo subir tu copia a Drive."
                )
            }
        }
    }

    fun dismiss() {
        pendingAccessToken = null
        _uiState.value = AutoSyncUiState.Idle
    }

    private suspend fun pushAsNewEnvelope(accessToken: String) {
        val bundle = backupRepository.buildCurrentBundle()
        val envelope = SyncEnvelope(
            deviceId = deviceIdentityManager.deviceId,
            deviceLabel = deviceIdentityManager.deviceLabel,
            timestamp = System.currentTimeMillis(),
            bundle = bundle,
        )
        driveRepository.pushSyncState(accessToken, backupRepository.toSyncJson(envelope))
    }
}

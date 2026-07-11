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
            _uiState.value = AutoSyncUiState.Done()
            return
        }

        if (envelope.deviceId == deviceIdentityManager.deviceId) {
            // Caso 2: mismo dispositivo, desincronizado -- la nube manda siempre, sin preguntar.
            restoreFromCloud(envelope.bundle)
            _uiState.value = AutoSyncUiState.RestoredFromCloud(comparison)
        } else {
            // Caso 3: otro dispositivo -- se pregunta antes de tocar nada.
            pendingAccessToken = accessToken
            _uiState.value = AutoSyncUiState.ConflictOtherDevice(envelope, comparison)
        }
    }

    /** Caso 3, respuesta "sí, se añadieron/borraron pistas en otro dispositivo" -- la nube sustituye a local. */
    fun confirmCloudWins() {
        val state = _uiState.value as? AutoSyncUiState.ConflictOtherDevice ?: return
        viewModelScope.launch {
            restoreFromCloud(state.envelope.bundle)
            pendingAccessToken = null
            _uiState.value = AutoSyncUiState.Done(
                "Restaurado desde la copia de ${state.envelope.deviceLabel}."
            )
        }
    }

    /**
     * Sustituye el repositorio local por `bundle` (destructivo, ver
     * `BackupImportRepository.importDestructively()`) y encola la
     * descarga de todas las pistas resultantes -- **fix real tras el
     * bug reportado por Miguel Ángel**: `importDestructively()` deja
     * cada pista insertada como `PENDING`, sin `filePath` (mismo
     * comportamiento que la importación manual de H06), pero nunca
     * dispara la descarga real por sí sola. La importación manual
     * (`SettingsViewModel.importNow()`, H06 PASO 5) sí lo hacía a
     * continuación con `DownloadQueueManager.enqueue()`; aquí faltaba
     * ese mismo paso, así que tras confirmar "usar la copia de
     * Drive" no pasaba nada visible -- ni pantalla de descarga, ni
     * nada en Biblioteca.
     * ---
     * Replaces the local repository with `bundle` (destructive, see
     * `BackupImportRepository.importDestructively()`) and queues the
     * download of every resulting track -- **real fix after the bug
     * Miguel Ángel reported**: `importDestructively()` leaves each
     * track inserted as `PENDING`, with no `filePath` (same behavior
     * as H06's manual import), but never triggers the actual download
     * on its own. Manual import
     * (`SettingsViewModel.importNow()`, H06 STEP 5) did follow up with
     * `DownloadQueueManager.enqueue()`; that same step was missing
     * here, so after confirming "use the Drive copy" nothing visible
     * happened -- no download screen, nothing in Library.
     */
    private suspend fun restoreFromCloud(bundle: BackupBundle) {
        val result = importRepository.importDestructively(bundle)
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

    /** Caso 3, respuesta "no" -- local sustituye a la nube (y se sube). */
    fun confirmLocalWins() {
        val state = _uiState.value as? AutoSyncUiState.ConflictOtherDevice ?: return
        val accessToken = pendingAccessToken ?: return
        viewModelScope.launch {
            pushAsNewEnvelope(accessToken)
            pendingAccessToken = null
            _uiState.value = AutoSyncUiState.Done("Tu copia local ha sustituido a la de Drive.")
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

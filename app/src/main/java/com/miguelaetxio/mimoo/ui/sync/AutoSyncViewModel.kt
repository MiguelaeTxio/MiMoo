package com.miguelaetxio.mimoo.ui.sync

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.BackupDriveRepository
import com.miguelaetxio.mimoo.data.backup.BackupImportRepository
import com.miguelaetxio.mimoo.data.backup.BackupMirrorRepository
import com.miguelaetxio.mimoo.data.backup.BackupRepository
import com.miguelaetxio.mimoo.data.backup.BundleComparison
import com.miguelaetxio.mimoo.data.backup.DeviceIdentityManager
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationHelper
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationOutcome
import com.miguelaetxio.mimoo.data.backup.SyncEnvelope
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val deviceIdentityManager: DeviceIdentityManager,
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
            _uiState.value = AutoSyncUiState.Error(
                e.message ?: "La copia de respaldo automática de Drive no es válida."
            )
            return
        }

        val localBundle = backupRepository.buildCurrentBundle()
        val comparison = mirrorRepository.compare(localBundle, envelope.bundle)

        if (comparison.identical) {
            _uiState.value = AutoSyncUiState.Done()
            return
        }

        if (envelope.deviceId == deviceIdentityManager.deviceId) {
            // Caso 2: mismo dispositivo, desincronizado -- la nube manda siempre, sin preguntar.
            importRepository.importDestructively(envelope.bundle)
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
            importRepository.importDestructively(state.envelope.bundle)
            pendingAccessToken = null
            _uiState.value = AutoSyncUiState.Done(
                "Restaurado desde la copia de ${state.envelope.deviceLabel}."
            )
        }
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

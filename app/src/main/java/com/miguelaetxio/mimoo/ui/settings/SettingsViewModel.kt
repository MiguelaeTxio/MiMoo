package com.miguelaetxio.mimoo.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.BackupDriveRepository
import com.miguelaetxio.mimoo.data.backup.BackupRepository
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationHelper
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationOutcome
import com.miguelaetxio.mimoo.data.backup.DriveBackupFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado de la pantalla Ajustes en lo relativo a H06. Un único
 * UiState (no dos, uno para exportar y otro para importar) porque
 * ambas operaciones comparten el mismo flujo de autorización y nunca
 * se hacen a la vez -- ver PendingAction.
 * ---
 * Settings screen state for H06. A single UiState (not two, one for
 * export and one for import) because both operations share the same
 * authorization flow and are never in flight at the same time -- see
 * PendingAction.
 */
sealed class BackupUiState {
    object Idle : BackupUiState()
    object Working : BackupUiState()
    data class ExportSuccess(val fileName: String) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

/**
 * ViewModel de la pantalla Ajustes (H06 PASO 3: exportación; PASO 4
 * añadirá la importación reutilizando el mismo mecanismo de
 * autorización). Coordina DriveAuthorizationHelper (auth),
 * BackupRepository (construir/serializar el bundle) y
 * BackupDriveRepository (hablar con Drive).
 *
 * El ViewModel nunca guarda una Activity más allá de la llamada que
 * la recibe -- cada método que la necesita la toma como parámetro
 * desde el Composable (`LocalContext.current as Activity`), nunca
 * como campo de la clase, para no arriesgar una fuga de memoria.
 * ---
 * Settings screen's ViewModel (H06 PASO 3: export; PASO 4 will add
 * import reusing the same authorization mechanism). Coordinates
 * DriveAuthorizationHelper (auth), BackupRepository (build/serialize
 * the bundle) and BackupDriveRepository (talk to Drive).
 *
 * The ViewModel never holds an Activity beyond the call that receives
 * it -- every method that needs one takes it as a parameter from the
 * Composable (`LocalContext.current as Activity`), never as a class
 * field, to avoid risking a memory leak.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val driveRepository: BackupDriveRepository,
    private val authorizationHelper: DriveAuthorizationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    /**
     * Cuando la autorización necesita confirmación del usuario
     * (primera vez, o acceso revocado), la UI observa esto y lanza el
     * Intent con un `ActivityResultLauncher<IntentSenderRequest>`.
     * `null` = nada pendiente.
     * ---
     * When authorization needs user confirmation (first time, or
     * revoked access), the UI observes this and launches the Intent
     * with an `ActivityResultLauncher<IntentSenderRequest>`. `null` =
     * nothing pending.
     */
    private val _pendingConsent = MutableStateFlow<IntentSenderRequest?>(null)
    val pendingConsent: StateFlow<IntentSenderRequest?> = _pendingConsent.asStateFlow()

    /**
     * Qué operación real se ejecuta en cuanto haya un accessToken
     * válido -- puede ser inmediatamente (ya autorizado) o tras
     * resolver el consentimiento del usuario.
     * ---
     * Which real operation runs as soon as there's a valid
     * accessToken -- either immediately (already authorized) or after
     * resolving the user's consent.
     */
    private sealed class PendingAction {
        object Export : PendingAction()
    }

    private var pendingAction: PendingAction? = null

    fun onExportClicked(activity: Activity) {
        pendingAction = PendingAction.Export
        beginAuthorization(activity)
    }

    private fun beginAuthorization(activity: Activity) {
        _uiState.value = BackupUiState.Working
        viewModelScope.launch {
            try {
                when (val outcome = authorizationHelper.requestAuthorization(activity)) {
                    is DriveAuthorizationOutcome.Authorized ->
                        runPendingAction(outcome.accessToken)
                    is DriveAuthorizationOutcome.NeedsUserConsent ->
                        _pendingConsent.value = outcome.intentSenderRequest
                }
            } catch (e: Exception) {
                pendingAction = null
                _uiState.value = BackupUiState.Error(
                    e.message ?: "No se pudo pedir autorización a Google."
                )
            }
        }
    }

    /**
     * Llamado por la UI tras resolver el `IntentSenderRequest`
     * (`ActivityResultLauncher`) que salió de `pendingConsent`, con
     * éxito.
     * ---
     * Called by the UI after resolving the `IntentSenderRequest`
     * (`ActivityResultLauncher`) that came out of `pendingConsent`,
     * on success.
     */
    fun onConsentResolved(activity: Activity, resultData: Intent?) {
        _pendingConsent.value = null
        try {
            val token = authorizationHelper.extractAccessTokenFromResolution(activity, resultData)
            viewModelScope.launch { runPendingAction(token) }
        } catch (e: Exception) {
            pendingAction = null
            _uiState.value = BackupUiState.Error("Google no concedió el acceso a Drive.")
        }
    }

    /** Llamado por la UI si el usuario cancela el diálogo de consentimiento. */
    fun onConsentCancelled() {
        _pendingConsent.value = null
        pendingAction = null
        _uiState.value = BackupUiState.Idle
    }

    fun dismissMessage() {
        _uiState.value = BackupUiState.Idle
    }

    private suspend fun runPendingAction(accessToken: String) {
        val action = pendingAction
        pendingAction = null
        if (action == null) return

        _uiState.value = BackupUiState.Working
        try {
            when (action) {
                is PendingAction.Export -> exportNow(accessToken)
            }
        } catch (e: Exception) {
            _uiState.value = BackupUiState.Error(
                e.message ?: "Error inesperado exportando a Drive."
            )
        }
    }

    private suspend fun exportNow(accessToken: String) {
        val bundle = backupRepository.buildCurrentBundle()
        val json = backupRepository.toJson(bundle)
        val uploaded: DriveBackupFile = driveRepository.uploadBackup(accessToken, json)
        _uiState.value = BackupUiState.ExportSuccess(uploaded.name)
    }
}

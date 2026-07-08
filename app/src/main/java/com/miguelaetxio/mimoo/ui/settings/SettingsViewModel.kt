package com.miguelaetxio.mimoo.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.BackupDriveRepository
import com.miguelaetxio.mimoo.data.backup.BackupImportRepository
import com.miguelaetxio.mimoo.data.backup.BackupRepository
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationHelper
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationOutcome
import com.miguelaetxio.mimoo.data.backup.DriveBackupFile
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
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
    /** Backups disponibles en Drive, listados tras pulsar "Importar" -- la UI muestra esta lista para elegir uno. */
    data class BackupsListed(val backups: List<DriveBackupFile>) : BackupUiState()
    data class ImportSuccess(val trackCount: Int) : BackupUiState()
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
    private val importRepository: BackupImportRepository,
    private val downloadQueueManager: DownloadQueueManager,
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
        object ListBackups : PendingAction()
        data class ImportBackup(val backup: DriveBackupFile) : PendingAction()
    }

    private var pendingAction: PendingAction? = null

    fun onExportClicked(activity: Activity) {
        pendingAction = PendingAction.Export
        beginAuthorization(activity)
    }

    /** Pide la lista de backups disponibles en Drive -- la UI la muestra para que Miguel Ángel elija uno. */
    fun onImportRequested(activity: Activity) {
        pendingAction = PendingAction.ListBackups
        beginAuthorization(activity)
    }

    /**
     * Llamado tras la confirmación explícita del diálogo destructivo
     * en la UI ("esto borrará tu repositorio actual..."), nunca
     * directamente al tocar un ítem de la lista.
     * ---
     * Called after the explicit confirmation of the destructive
     * dialog in the UI ("this will erase your current
     * repository..."), never directly on tapping a list item.
     */
    fun onImportConfirmed(activity: Activity, backup: DriveBackupFile) {
        pendingAction = PendingAction.ImportBackup(backup)
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
                is PendingAction.ListBackups -> listBackupsNow(accessToken)
                is PendingAction.ImportBackup -> importNow(accessToken, action.backup)
            }
        } catch (e: Exception) {
            _uiState.value = BackupUiState.Error(
                e.message ?: "Error inesperado hablando con Drive."
            )
        }
    }

    private suspend fun exportNow(accessToken: String) {
        val bundle = backupRepository.buildCurrentBundle()
        val json = backupRepository.toJson(bundle)
        val uploaded: DriveBackupFile = driveRepository.uploadBackup(accessToken, json)
        _uiState.value = BackupUiState.ExportSuccess(uploaded.name)
    }

    private suspend fun listBackupsNow(accessToken: String) {
        val backups = driveRepository.listBackups(accessToken)
        _uiState.value = BackupUiState.BackupsListed(backups)
    }

    /**
     * Descarga el backup elegido, lo deserializa (fromJson() ya
     * rechaza versiones no reconocidas), ejecuta la sustitución
     * destructiva (PASO 4) y encola automáticamente la descarga de
     * TODAS las pistas importadas con los metadatos ya fijados, sin
     * ningún diálogo de edición (PASO 5) -- reutiliza
     * DownloadQueueManager.enqueue(), el mismo mecanismo que H05
     * PASO 6b.
     * ---
     * Downloads the chosen backup, deserializes it (fromJson() already
     * rejects unrecognized versions), runs the destructive substitution
     * (PASO 4), and automatically enqueues the download of ALL
     * imported tracks with the metadata already set, with no edit
     * dialog (PASO 5) -- reuses DownloadQueueManager.enqueue(), the
     * same mechanism as H05 PASO 6b.
     */
    private suspend fun importNow(accessToken: String, backup: DriveBackupFile) {
        val json = driveRepository.downloadBackupJson(accessToken, backup.id)
        val bundle = backupRepository.fromJson(json)
        val result = importRepository.importDestructively(bundle)

        result.importedTracks.forEach { track ->
            downloadQueueManager.enqueue(
                youtubeId = track.youtubeId,
                title = track.title,
                artist = track.artist ?: track.channelTitle,
                album = track.album,
                trackPosition = track.trackPosition,
            )
        }

        _uiState.value = BackupUiState.ImportSuccess(result.importedTracks.size)
    }
}

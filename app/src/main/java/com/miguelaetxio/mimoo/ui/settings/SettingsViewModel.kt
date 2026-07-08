package com.miguelaetxio.mimoo.ui.settings

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.BackupDebugLogger
import com.miguelaetxio.mimoo.data.backup.BackupDriveRepository
import com.miguelaetxio.mimoo.data.backup.BackupImportRepository
import com.miguelaetxio.mimoo.data.backup.BackupRepository
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationHelper
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationOutcome
import com.miguelaetxio.mimoo.data.backup.DriveBackupFile
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.download.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MiMoo-Backup-VM"

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
    private val storageManager: StorageManager,
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
        Log.d(TAG, "beginAuthorization() -- pendingAction=$pendingAction")
        BackupDebugLogger.log(activity, storageManager, "beginAuthorization() -- pendingAction=$pendingAction")
        _uiState.value = BackupUiState.Working
        viewModelScope.launch {
            try {
                when (val outcome = authorizationHelper.requestAuthorization(activity)) {
                    is DriveAuthorizationOutcome.Authorized -> {
                        Log.d(TAG, "Ya autorizado, sin diálogo -- ejecutando pendingAction directamente")
                        BackupDebugLogger.log(activity, storageManager, "Ya autorizado, sin diálogo -- ejecutando pendingAction directamente")
                        runPendingAction(activity, outcome.accessToken)
                    }
                    is DriveAuthorizationOutcome.NeedsUserConsent -> {
                        Log.d(TAG, "Hace falta consentimiento del usuario -- lanzando IntentSenderRequest")
                        BackupDebugLogger.log(activity, storageManager, "Hace falta consentimiento del usuario -- lanzando IntentSenderRequest")
                        _pendingConsent.value = outcome.intentSenderRequest
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "beginAuthorization() FALLÓ", e)
                BackupDebugLogger.logError(activity, storageManager, "beginAuthorization() FALLÓ", e)
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
        Log.d(TAG, "onConsentResolved() -- resolviendo consentimiento devuelto")
        BackupDebugLogger.log(activity, storageManager, "onConsentResolved() -- resolviendo consentimiento devuelto")
        _pendingConsent.value = null
        try {
            val token = authorizationHelper.extractAccessTokenFromResolution(activity, resultData)
            Log.d(TAG, "onConsentResolved() OK -- token obtenido, ejecutando pendingAction")
            BackupDebugLogger.log(activity, storageManager, "onConsentResolved() OK -- token obtenido, ejecutando pendingAction")
            viewModelScope.launch { runPendingAction(activity, token) }
        } catch (e: Exception) {
            Log.e(TAG, "onConsentResolved() FALLÓ", e)
            BackupDebugLogger.logError(activity, storageManager, "onConsentResolved() FALLÓ", e)
            pendingAction = null
            _uiState.value = BackupUiState.Error(
                e.message ?: "Google no concedió el acceso a Drive."
            )
        }
    }

    fun dismissMessage() {
        _uiState.value = BackupUiState.Idle
    }

    private suspend fun runPendingAction(activity: Activity, accessToken: String) {
        val action = pendingAction
        pendingAction = null
        if (action == null) {
            Log.w(TAG, "runPendingAction() llamado sin ninguna acción pendiente -- no hace nada")
            return
        }

        Log.d(TAG, "runPendingAction() -- ejecutando $action")
        BackupDebugLogger.log(activity, storageManager, "runPendingAction() -- ejecutando $action")
        _uiState.value = BackupUiState.Working
        try {
            when (action) {
                is PendingAction.Export -> exportNow(activity, accessToken)
                is PendingAction.ListBackups -> listBackupsNow(activity, accessToken)
                is PendingAction.ImportBackup -> importNow(activity, accessToken, action.backup)
            }
            Log.d(TAG, "runPendingAction() -- $action terminado con éxito")
            BackupDebugLogger.log(activity, storageManager, "runPendingAction() -- $action terminado con éxito")
        } catch (e: Exception) {
            Log.e(TAG, "runPendingAction() -- $action FALLÓ", e)
            BackupDebugLogger.logError(activity, storageManager, "runPendingAction() -- $action FALLÓ", e)
            _uiState.value = BackupUiState.Error(
                e.message ?: "Error inesperado hablando con Drive."
            )
        }
    }

    private suspend fun exportNow(activity: Activity, accessToken: String) {
        val bundle = backupRepository.buildCurrentBundle()
        val step1 = "exportNow() -- bundle construido: ${bundle.tracks.size} pistas, ${bundle.favoriteAlbums.size} favoritos, ${bundle.playlists.size} playlists"
        Log.d(TAG, step1)
        BackupDebugLogger.log(activity, storageManager, step1)
        val json = backupRepository.toJson(bundle)
        val step2 = "exportNow() -- JSON serializado, ${json.length} caracteres. Subiendo a Drive..."
        Log.d(TAG, step2)
        BackupDebugLogger.log(activity, storageManager, step2)
        val uploaded: DriveBackupFile = driveRepository.uploadBackup(accessToken, json)
        val step3 = "exportNow() -- subida OK, archivo '${uploaded.name}' (id=${uploaded.id})"
        Log.d(TAG, step3)
        BackupDebugLogger.log(activity, storageManager, step3)
        _uiState.value = BackupUiState.ExportSuccess(uploaded.name)
    }

    private suspend fun listBackupsNow(activity: Activity, accessToken: String) {
        val backups = driveRepository.listBackups(accessToken)
        val step = "listBackupsNow() -- ${backups.size} backups encontrados en Drive"
        Log.d(TAG, step)
        BackupDebugLogger.log(activity, storageManager, step)
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
    private suspend fun importNow(activity: Activity, accessToken: String, backup: DriveBackupFile) {
        val step1 = "importNow() -- descargando '${backup.name}' (id=${backup.id})"
        Log.d(TAG, step1)
        BackupDebugLogger.log(activity, storageManager, step1)
        val json = driveRepository.downloadBackupJson(accessToken, backup.id)
        val step2 = "importNow() -- descarga OK, ${json.length} caracteres. Deserializando..."
        Log.d(TAG, step2)
        BackupDebugLogger.log(activity, storageManager, step2)
        val bundle = backupRepository.fromJson(json)
        val step3 = "importNow() -- bundle válido: ${bundle.tracks.size} pistas, ${bundle.favoriteAlbums.size} favoritos, ${bundle.playlists.size} playlists. Ejecutando sustitución destructiva..."
        Log.d(TAG, step3)
        BackupDebugLogger.log(activity, storageManager, step3)
        val result = importRepository.importDestructively(bundle)
        val step4 = "importNow() -- sustitución OK, ${result.importedTracks.size} pistas insertadas. Encolando descargas..."
        Log.d(TAG, step4)
        BackupDebugLogger.log(activity, storageManager, step4)

        result.importedTracks.forEach { track ->
            downloadQueueManager.enqueue(
                youtubeId = track.youtubeId,
                title = track.title,
                artist = track.artist ?: track.channelTitle,
                album = track.album,
                trackPosition = track.trackPosition,
            )
        }
        val step5 = "importNow() -- ${result.importedTracks.size} descargas encoladas"
        Log.d(TAG, step5)
        BackupDebugLogger.log(activity, storageManager, step5)

        _uiState.value = BackupUiState.ImportSuccess(result.importedTracks.size)
    }
}

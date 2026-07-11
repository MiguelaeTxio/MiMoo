package com.miguelaetxio.mimoo.ui.sync

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.BackupDriveRepository
import com.miguelaetxio.mimoo.data.backup.BackupMirrorRepository
import com.miguelaetxio.mimoo.data.backup.BackupRepository
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationHelper
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationOutcome
import com.miguelaetxio.mimoo.data.backup.MirrorDiff
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MiMoo-AutoSync-Pull"

/**
 * H07 PARTE 1: estado de la comprobación de sincronización automática
 * al arrancar la app. `Idle` es el estado normal de reposo -- no hay
 * nada que mostrar ni antes de arrancar ni después de terminar sin
 * incidencias.
 * ---
 * H07 PART 1: state for the automatic sync check on app startup.
 * `Idle` is the normal resting state -- nothing to show either before
 * starting or after finishing with no issues.
 */
sealed class AutoSyncUiState {
    object Idle : AutoSyncUiState()
    object Checking : AutoSyncUiState()
    data class ConfirmDeletions(val diff: MirrorDiff) : AutoSyncUiState()
    data class Done(val addedCount: Int, val removedCount: Int) : AutoSyncUiState()
    data class Error(val message: String) : AutoSyncUiState()
}

/**
 * H07 PARTE 1: se dispara una vez por arranque de app (ver
 * MainActivity) -- descarga la copia de respaldo automática, calcula
 * el `MirrorDiff` contra el estado local, aplica las altas sin pedir
 * nada (nunca borran), y si hay bajas, para y pide confirmación
 * explícita antes de tocar nada local -- nunca borra en silencio.
 *
 * Si es la primera sincronización de la cuenta (todavía no existe
 * `mimoo_sync_state.json` en Drive), sube el estado local tal cual en
 * vez de comparar contra nada.
 * ---
 * H07 PART 1: fires once per app startup (see MainActivity) --
 * downloads the automatic backup copy, computes the `MirrorDiff`
 * against local state, applies additions without asking anything
 * (they never delete), and if there are deletions, stops and asks for
 * explicit confirmation before touching anything local -- never
 * deletes silently.
 *
 * If this is the account's first-ever sync (`mimoo_sync_state.json`
 * doesn't exist on Drive yet), it uploads the local state as-is
 * instead of comparing against anything.
 */
@HiltViewModel
class AutoSyncViewModel @Inject constructor(
    private val authorizationHelper: DriveAuthorizationHelper,
    private val driveRepository: BackupDriveRepository,
    private val backupRepository: BackupRepository,
    private val mirrorRepository: BackupMirrorRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AutoSyncUiState>(AutoSyncUiState.Idle)
    val uiState: StateFlow<AutoSyncUiState> = _uiState.asStateFlow()

    private val _pendingConsent = MutableStateFlow<IntentSenderRequest?>(null)
    val pendingConsent: StateFlow<IntentSenderRequest?> = _pendingConsent.asStateFlow()

    private var pendingDiff: MirrorDiff? = null

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
            // Primera sincronización de la cuenta -- nada que comparar
            // todavía, se sube el estado local tal cual.
            val bundle = backupRepository.buildCurrentBundle()
            driveRepository.pushSyncState(accessToken, backupRepository.toJson(bundle))
            _uiState.value = AutoSyncUiState.Done(addedCount = 0, removedCount = 0)
            return
        }

        val remoteBundle = try {
            backupRepository.fromJson(remoteJson)
        } catch (e: BackupRepository.BackupParseException) {
            _uiState.value = AutoSyncUiState.Error(
                e.message ?: "La copia de respaldo automática de Drive no es válida."
            )
            return
        }

        val diff = mirrorRepository.computeDiff(remoteBundle)
        if (diff.isEmpty) {
            _uiState.value = AutoSyncUiState.Done(addedCount = 0, removedCount = 0)
            return
        }

        val addedCount = diff.tracksToDownload.size + diff.favoritesToAdd.size + diff.playlistsToCreate.size
        mirrorRepository.applyAdditions(diff)

        if (diff.hasDeletions) {
            pendingDiff = diff
            _uiState.value = AutoSyncUiState.ConfirmDeletions(diff)
        } else {
            _uiState.value = AutoSyncUiState.Done(addedCount = addedCount, removedCount = 0)
        }
    }

    /** Confirmación explícita del usuario -- solo aquí se ejecuta cualquier borrado. */
    fun confirmDeletions() {
        val diff = pendingDiff ?: return
        val removedCount = diff.tracksToDelete.size + diff.favoritesToRemove.size + diff.playlistsToDelete.size
        val addedCount = diff.tracksToDownload.size + diff.favoritesToAdd.size + diff.playlistsToCreate.size
        viewModelScope.launch {
            mirrorRepository.applyDeletions(diff)
            pendingDiff = null
            _uiState.value = AutoSyncUiState.Done(addedCount = addedCount, removedCount = removedCount)
        }
    }

    /** El usuario rechaza los borrados -- las altas ya aplicadas se quedan, los borrados no se tocan. */
    fun dismissDeletions() {
        val diff = pendingDiff
        pendingDiff = null
        val addedCount = diff?.let {
            it.tracksToDownload.size + it.favoritesToAdd.size + it.playlistsToCreate.size
        } ?: 0
        _uiState.value = AutoSyncUiState.Done(addedCount = addedCount, removedCount = 0)
    }

    fun dismiss() {
        _uiState.value = AutoSyncUiState.Idle
    }
}

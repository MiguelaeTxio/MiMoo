package com.miguelaetxio.mimoo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.remote.dto.UpdateManifest
import com.miguelaetxio.mimoo.data.update.AppUpdateRepository
import com.miguelaetxio.mimoo.data.update.UpdateCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * H07 PARTE 2, PASO 2.5 -- estado de la sección "Buscar
 * actualizaciones" de Ajustes. ViewModel propio, separado de
 * SettingsViewModel (que ya es H06, backup/Drive) -- dos
 * responsabilidades sin relación entre sí, mismo patrón que separar
 * PinViewModel del resto.
 * ---
 * H07 PART 2, STEP 2.5 -- state for the "Check for updates" section
 * of Settings. Own ViewModel, separate from SettingsViewModel (which
 * is already H06, backup/Drive) -- two unrelated responsibilities,
 * same pattern as keeping PinViewModel separate from the rest.
 */
sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    object UpToDate : UpdateUiState()
    data class UpdateAvailable(val manifest: UpdateManifest) : UpdateUiState()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : UpdateUiState()
    data class ReadyToInstall(val apkUri: android.net.Uri) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdate(currentVersionCode: Int) {
        _uiState.value = UpdateUiState.Checking
        viewModelScope.launch {
            _uiState.value = when (
                val result = appUpdateRepository.checkForUpdate(currentVersionCode)
            ) {
                is UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate
                is UpdateCheckResult.UpdateAvailable ->
                    UpdateUiState.UpdateAvailable(result.manifest)
                is UpdateCheckResult.Error -> UpdateUiState.Error(result.message)
            }
        }
    }

    fun downloadUpdate(manifest: UpdateManifest) {
        _uiState.value = UpdateUiState.Downloading(bytesDownloaded = 0L, totalBytes = 0L)
        viewModelScope.launch {
            _uiState.value = try {
                val apkUri = appUpdateRepository.downloadApk(manifest) { bytesDownloaded, totalBytes ->
                    _uiState.value = UpdateUiState.Downloading(bytesDownloaded, totalBytes)
                }
                UpdateUiState.ReadyToInstall(apkUri)
            } catch (e: Exception) {
                UpdateUiState.Error(e.message ?: "No se pudo descargar la actualización.")
            }
        }
    }

    fun dismiss() {
        _uiState.value = UpdateUiState.Idle
    }
}

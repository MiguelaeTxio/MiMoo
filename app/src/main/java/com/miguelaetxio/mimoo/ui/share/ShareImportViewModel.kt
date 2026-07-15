package com.miguelaetxio.mimoo.ui.share

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.BackupImportRepository
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.share.ShareBundle
import com.miguelaetxio.mimoo.data.share.ShareCodeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MiMoo-ShareImport"

/**
 * H10 -- estado de la pantalla/diálogo que recibe un código
 * "miMoo+hash" compartido por otra persona (vía el intent-filter
 * ACTION_SEND de MainActivity, ver AndroidManifest.xml). Mismo patrón
 * de UiState + confirmación explícita que `AutoSyncUiState` (H07),
 * pero sin autorización de por medio -- todo el contenido va ya
 * dentro del propio texto recibido, sin hablar con Drive ni con
 * ningún servidor.
 */
sealed class ShareImportUiState {
    object Idle : ShareImportUiState()

    /** Código decodificado, esperando que el receptor confirme antes de tocar su repositorio. */
    data class Confirm(val shareBundle: ShareBundle) : ShareImportUiState()

    object Importing : ShareImportUiState()

    data class Done(val trackCount: Int, val newDownloadsCount: Int) : ShareImportUiState()

    data class Error(val message: String) : ShareImportUiState()
}

@HiltViewModel
class ShareImportViewModel @Inject constructor(
    private val shareCodeRepository: ShareCodeRepository,
    private val importRepository: BackupImportRepository,
    private val downloadQueueManager: DownloadQueueManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShareImportUiState>(ShareImportUiState.Idle)
    val uiState: StateFlow<ShareImportUiState> = _uiState.asStateFlow()

    /**
     * Llamado desde MainActivity cuando llega un ACTION_SEND de texto
     * plano que empieza por "miMoo+". Solo decodifica y muestra la
     * confirmación -- nunca toca el repositorio todavía, eso es
     * `confirmImport()`.
     */
    fun handleIncomingShareCode(text: String) {
        try {
            val shareBundle = shareCodeRepository.decode(text)
            Log.d(TAG, "handleIncomingShareCode() -- decodificado OK: ${shareBundle.scopeLabel}")
            _uiState.value = ShareImportUiState.Confirm(shareBundle)
        } catch (e: ShareCodeRepository.ShareParseException) {
            Log.w(TAG, "handleIncomingShareCode() -- código inválido", e)
            _uiState.value = ShareImportUiState.Error(e.message ?: "Código de compartición no válido.")
        }
    }

    /**
     * Confirmación explícita del receptor. Importación siempre
     * ADITIVA (`BackupImportRepository.importSharedBundle()`) -- ver
     * el comentario de esa función para por qué nunca es destructiva
     * aquí, a diferencia de H06/H07.
     */
    fun confirmImport() {
        val state = _uiState.value as? ShareImportUiState.Confirm ?: return
        _uiState.value = ShareImportUiState.Importing
        viewModelScope.launch {
            try {
                val result = importRepository.importSharedBundle(state.shareBundle.bundle)
                result.importedTracks.forEach { track ->
                    downloadQueueManager.enqueue(
                        youtubeId = track.youtubeId,
                        title = track.title,
                        artist = track.artist ?: track.channelTitle,
                        album = track.album,
                        trackPosition = track.trackPosition,
                    )
                }
                Log.d(
                    TAG,
                    "confirmImport() -- OK, ${state.shareBundle.bundle.tracks.size} pista(s) del código, " +
                        "${result.importedTracks.size} descarga(s) nueva(s) encolada(s)",
                )
                _uiState.value = ShareImportUiState.Done(
                    trackCount = state.shareBundle.bundle.tracks.size,
                    newDownloadsCount = result.importedTracks.size,
                )
            } catch (e: Exception) {
                Log.e(TAG, "confirmImport() FALLÓ", e)
                _uiState.value = ShareImportUiState.Error(
                    e.message ?: "No se pudo importar el contenido compartido."
                )
            }
        }
    }

    fun dismiss() {
        _uiState.value = ShareImportUiState.Idle
    }
}

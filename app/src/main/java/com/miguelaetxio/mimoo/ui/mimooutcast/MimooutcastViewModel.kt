package com.miguelaetxio.mimoo.ui.mimooutcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.remote.MimooutcastCatalog
import com.miguelaetxio.mimoo.data.remote.RadioRepositoryimport dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MimooutcastTab { GENEROS, DECADAS, ORIGENES }

data class MimooutcastUiState(
    val tab: MimooutcastTab = MimooutcastTab.GENEROS,
    /** Etiqueta de la chapita en curso de resolución (null = ninguna en marcha). */
    val loadingLabel: String? = null,
    /** `true` cuando la última elección no encontró NI UN candidato -- ver PlayerManager.startRadioFromManualAnchor(). */
    val noResultsFor: String? = null,
)

/**
 * H15 (miMooutCast) -- selección de ancla a mano (género O década,
 * S029: una única dimensión, la otra libre) y arranque de la Radio
 * reutilizando el motor 80/10/10 ya construido -- ver
 * `RadioRepository.manualAnchor()`/`PlayerManager.startRadioFromManualAnchor()`.
 */
@HiltViewModel
class MimooutcastViewModel @Inject constructor(
    private val radioRepository: RadioRepository,
    private val playerManager: PlayerManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MimooutcastUiState())
    val uiState: StateFlow<MimooutcastUiState> = _uiState.asStateFlow()

    val genres = MimooutcastCatalog.genres
    val decades = MimooutcastCatalog.decades
    val origins = MimooutcastCatalog.origins

    fun selectTab(tab: MimooutcastTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    fun dismissNoResults() {
        _uiState.value = _uiState.value.copy(noResultsFor = null)
    }

    fun startWithGenre(mbGenre: String, label: String) = start(mbGenre, decadeBegin = null, originGroup = null, label = label)

    fun startWithDecade(decadeBegin: Int, label: String) = start(genre = null, decadeBegin = decadeBegin, originGroup = null, label = label)

    fun startWithOrigin(group: com.miguelaetxio.mimoo.data.remote.OriginGroup, label: String) =
        start(genre = null, decadeBegin = null, originGroup = group, label = label)

    private fun start(genre: String?, decadeBegin: Int?, originGroup: com.miguelaetxio.mimoo.data.remote.OriginGroup?, label: String) {
        if (_uiState.value.loadingLabel != null) return
        _uiState.value = _uiState.value.copy(loadingLabel = label, noResultsFor = null)
        viewModelScope.launch {
            val anchor = radioRepository.manualAnchor(genre, decadeBegin, originGroup)
            val started = playerManager.startRadioFromManualAnchor(anchor, "miMooutCast: $label")
            _uiState.value = _uiState.value.copy(
                loadingLabel = null,
                noResultsFor = if (started) null else label,
            )
        }
    }
}

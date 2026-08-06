package com.miguelaetxio.mimoo.ui.mimooutcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.remote.GenreTree
import com.miguelaetxio.mimoo.data.remote.MimooutcastCatalog
import com.miguelaetxio.mimoo.data.remote.MimooutcastGenre
import com.miguelaetxio.mimoo.data.remote.OriginGroup
import com.miguelaetxio.mimoo.data.remote.RadioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MimooutcastTab { GENEROS, DECADAS, ORIGENES }

data class MimooutcastUiState(
    val tab: MimooutcastTab = MimooutcastTab.GENEROS,
    /**
     * H15 -- género raíz desplegado en la pestaña Géneros (petición
     * de Miguel Ángel, 2026-08-06: "pinchar electrónica y mostrar un
     * nivel de géneros dentro"). `null` = mostrando la lista de
     * géneros raíz de `MimooutcastCatalog`.
     */
    val expandedGenre: MimooutcastGenre? = null,
    /** Etiqueta de la chapita en curso de resolución (null = ninguna en marcha). */
    val loadingLabel: String? = null,
    /** `true` cuando la última elección no encontró NI UN candidato -- ver PlayerManager.startRadioFromManualAnchor(). */
    val noResultsFor: String? = null,
)

/**
 * H15 (miMooutCast) -- selección de ancla a mano (género, década u
 * origen -- S029: una única dimensión, las otras libres) y arranque
 * de la Radio en streaming continuo, sin cupos -- ver
 * `PlayerManager.startRadioFromManualAnchor()`.
 */
@HiltViewModel
class MimooutcastViewModel @Inject constructor(
    private val radioRepository: RadioRepository,
    private val playerManager: PlayerManager,
    private val genreTree: GenreTree,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MimooutcastUiState())
    val uiState: StateFlow<MimooutcastUiState> = _uiState.asStateFlow()

    val genres = MimooutcastCatalog.genres
    val decades = MimooutcastCatalog.decades
    val origins = MimooutcastCatalog.origins

    fun selectTab(tab: MimooutcastTab) {
        _uiState.value = _uiState.value.copy(tab = tab, expandedGenre = null)
    }

    fun dismissNoResults() {
        _uiState.value = _uiState.value.copy(noResultsFor = null)
    }

    /**
     * H15 -- se llama al pinchar un género raíz. Si tiene hijos
     * catalogados en `genre_tree.json`, despliega el segundo nivel
     * (`subgenresOf()`) en vez de arrancar la Radio directamente --
     * "siempre que se pueda": los que no tengan hijos (hojas de la
     * taxonomía) arrancan igual que antes, sin segundo nivel posible.
     */
    fun tapGenre(genre: MimooutcastGenre) {
        if (subgenresOf(genre).isEmpty()) {
            startWithGenre(genre.mbGenre, genre.label)
        } else {
            _uiState.value = _uiState.value.copy(expandedGenre = genre)
        }
    }

    fun collapseGenre() {
        _uiState.value = _uiState.value.copy(expandedGenre = null)
    }

    /**
     * H15 -- hijos DIRECTOS reales de MusicBrainz (`GenreTree`, misma
     * taxonomía que ya usa H08), con una etiqueta capitalizada para
     * mostrar -- no hay lista curada de subgéneros, serían cientos
     * entre los 24 géneros raíz.
     */
    fun subgenresOf(genre: MimooutcastGenre): List<MimooutcastGenre> =
        genreTree.directChildren(genre.mbGenre).map { mb ->
            MimooutcastGenre(
                label = mb.split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) },
                mbGenre = mb,
            )
        }

    fun startWithGenre(mbGenre: String, label: String) = start(mbGenre, decadeBegin = null, originGroup = null, label = label)

    fun startWithDecade(decadeBegin: Int, label: String) = start(genre = null, decadeBegin = decadeBegin, originGroup = null, label = label)

    fun startWithOrigin(group: OriginGroup, label: String) =
        start(genre = null, decadeBegin = null, originGroup = group, label = label)

    private fun start(genre: String?, decadeBegin: Int?, originGroup: OriginGroup?, label: String) {
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

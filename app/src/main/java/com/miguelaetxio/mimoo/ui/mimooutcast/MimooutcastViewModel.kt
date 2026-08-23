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
    /**
     * H15 (miMooutCast), S032 -- botón TRANSVERSAL (afecta a las tres
     * pestañas por igual, no solo a la que esté abierta ahora mismo).
     * Orden explícita de Miguel Ángel: *"activar o desactivar las
     * listas de éxitos españolas y comparar contra estas listas de
     * éxitos. En géneros nicho la desactivamos para tener candidatos,
     * y en décadas como los 90 o en géneros como hard rock, podemos
     * activar conocido en España."* `false` por defecto -- streaming
     * puro, el comportamiento de toda esta sesión hasta ahora, sin
     * ningún filtro de fama.
     */
    val requireKnownInSpain: Boolean = false,
    /**
     * H15 (miMooutCast), S032 -- filtro de búsqueda de géneros pedido
     * por Miguel Ángel: *"hay que poner un filtro para buscar
     * géneros."* Filtra tanto la lista de géneros raíz como el
     * desplegable de subgéneros (cuando `expandedGenre != null`) por
     * coincidencia de texto en el nombre, sin distinguir mayúsculas.
     */
    val genreSearchQuery: String = "",
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

    /** H15 (miMooutCast), S032 -- ver el kdoc de `requireKnownInSpain` en el UiState. */
    fun toggleRequireKnownInSpain() {
        _uiState.value = _uiState.value.copy(requireKnownInSpain = !_uiState.value.requireKnownInSpain)
    }

    /**
     * H15 -- se llama al pinchar un género raíz. Si tiene hijos
     * catalogados en `genre_tree.json`, despliega el segundo nivel
     * (`subgenresOf()`) en vez de arrancar la Radio directamente --
     * "siempre que se pueda": los que no tengan hijos (hojas de la
     * taxonomía) arrancan igual que antes, sin segundo nivel posible.
     */
    /**
     * H15 (miMooutCast), S032 -- clásica ya no tiene subgéneros que
     * explorar, orden explícita de Miguel Ángel, repetida más de una
     * vez: *"clásica no es necesario buscar con tanto subgénero,
     * buscamos classical y punto."* Va directa a arrancar, nunca al
     * desplegable de subgéneros -- sea cual sea lo que devuelva el
     * árbol de géneros de MusicBrainz para "classical" (Andalusian
     * Classical, Chinese Classical, Japanese Classical... existen ahí,
     * pero ya no se muestran como opción). El resto de géneros, sin
     * cambios: si tienen hijos reales, se sigue mostrando el
     * desplegable.
     */
    fun tapGenre(genre: MimooutcastGenre) {
        if (genre.mbGenre == "classical" || subgenresOf(genre).isEmpty()) {
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
    /** H15, S032 -- delega en la fuente compartida, ver `MimooutcastCatalog.subgenresOf()`. */
    fun subgenresOf(genre: MimooutcastGenre): List<MimooutcastGenre> =
        com.miguelaetxio.mimoo.data.remote.MimooutcastCatalog.subgenresOf(genre, genreTree)

    /**
     * Coincidencia de búsqueda aplanada -- [rootLabel] es `null` cuando
     * [genre] es un género raíz, o el nombre del raíz cuando es un
     * subgénero (p.ej. "Big Beat" -> rootLabel = "EDM").
     */
    data class FlatGenreMatch(val genre: MimooutcastGenre, val rootLabel: String?)

    /**
     * Bug real reportado por Miguel Ángel (2026-08-23): "Big Beat" no
     * se podía elegir ni buscar en ningún sitio. Causa real: la caja
     * de búsqueda de la pantalla raíz solo filtraba `genres` (los 24
     * géneros raíz de `MimooutcastCatalog`) -- "Big Beat" es un
     * SUBGÉNERO de EDM (un nivel más abajo, forzado a mano en
     * `MimooutcastCatalog.subgenresOf()` por orden explícita de Miguel
     * Ángel en S032), así que nunca aparecía a menos que el usuario
     * pinchase antes "EDM" para desplegar su segundo nivel y buscase
     * ahí dentro -- nada en la pantalla indicaba que hiciera falta ese
     * paso previo.
     *
     * Ahora, en cuanto hay texto de búsqueda en la pantalla raíz, se
     * busca en TODOS los géneros y TODOS sus subgéneros a la vez
     * (aplanado), sin que el usuario tenga que saber de antemano bajo
     * qué género raíz cuelga el subgénero que busca. Clásica se
     * excluye del aplanado de subgéneros -- mismo criterio ya cerrado
     * con Miguel Ángel de que Clásica no expone su árbol de subgéneros
     * en ningún sitio de esta pantalla (ver `tapGenre()`).
     */
    fun searchAllGenres(query: String): List<FlatGenreMatch> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val results = mutableListOf<FlatGenreMatch>()
        for (root in genres) {
            if (root.label.contains(q, ignoreCase = true)) {
                results += FlatGenreMatch(root, rootLabel = null)
            }
            if (root.mbGenre != "classical") {
                for (sub in subgenresOf(root)) {
                    if (sub.label.contains(q, ignoreCase = true)) {
                        results += FlatGenreMatch(sub, rootLabel = root.label)
                    }
                }
            }
        }
        return results
    }

    fun startWithGenre(mbGenre: String, label: String) = start(mbGenre, decadeBegin = null, originGroup = null, label = label)

    fun startWithDecade(decadeBegin: Int, label: String) = start(genre = null, decadeBegin = decadeBegin, originGroup = null, label = label)

    fun startWithOrigin(group: OriginGroup, label: String) =
        start(genre = null, decadeBegin = null, originGroup = group, label = label)

    /**
     * H15 (miMooutCast), S032 -- distingue "el usuario canceló a
     * propósito" (botón "dejar de buscar") de "se buscó de verdad y no
     * se encontró nada" -- sin esto, cancelar mostraría el mismo aviso
     * "sin resultados" que un fallo real, lo cual sería engañoso.
     */
    private var searchCancelledByUser = false

    private fun start(genre: String?, decadeBegin: Int?, originGroup: OriginGroup?, label: String) {
        if (_uiState.value.loadingLabel != null) return
        searchCancelledByUser = false
        _uiState.value = _uiState.value.copy(loadingLabel = label, noResultsFor = null)
        viewModelScope.launch {
            val anchor = radioRepository.manualAnchor(genre, decadeBegin, originGroup)
            val started = playerManager.startRadioFromManualAnchor(
                anchor, "miMooutCast: $label",
                requireKnownInSpain = _uiState.value.requireKnownInSpain,
            )
            _uiState.value = _uiState.value.copy(
                loadingLabel = null,
                noResultsFor = if (started || searchCancelledByUser) null else label,
            )
        }
    }

    /**
     * H15 (miMooutCast), S032 -- botón "dejar de buscar" pedido por
     * Miguel Ángel: *"cuando ya veo que no encuentra absolutamente
     * nada y voy a escuchar otra cosa, te salta lo que estaba
     * buscando."* Ver `PlayerManager.cancelMimooutcastSearch()`.
     */
    fun cancelSearch() {
        searchCancelledByUser = true
        playerManager.cancelMimooutcastSearch()
        _uiState.value = _uiState.value.copy(loadingLabel = null)
    }

    /** H15 (miMooutCast), S032 -- ver el kdoc de `genreSearchQuery` en el UiState. */
    fun updateGenreSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(genreSearchQuery = query)
    }
}

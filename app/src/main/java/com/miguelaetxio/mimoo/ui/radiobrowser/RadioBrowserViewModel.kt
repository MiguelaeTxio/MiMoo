package com.miguelaetxio.mimoo.ui.radiobrowser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.remote.RadioBrowserRepository
import com.miguelaetxio.mimoo.data.remote.dto.RadioCountry
import com.miguelaetxio.mimoo.data.remote.dto.RadioStation
import com.miguelaetxio.mimoo.data.remote.dto.RadioTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RadioBrowserUiState(
    val query: String = "",
    val tags: List<RadioTag> = emptyList(),
    val countries: List<RadioCountry> = emptyList(),
    val selectedTag: String? = null,
    val selectedCountryCode: String? = null,
    val stations: List<RadioStation> = emptyList(),
    val isLoadingFilters: Boolean = false,
    val isSearching: Boolean = false,
)

/**
 * ViewModel de la pantalla "Radio Online" (H09 PASO 3, S010).
 *
 * Al abrir la pantalla se cargan los chips de género (`/json/tags`) y
 * la lista de países (`/json/countries`), y se lanza una búsqueda
 * inicial sin filtros (orden por votos) para que la pantalla no
 * aparezca vacía -- mismo criterio que cualquier directorio de radio.
 * Tocar un chip de género o elegir un país relanza la búsqueda de
 * inmediato (a diferencia del campo de texto, que sigue el patrón ya
 * establecido en SearchScreen: pulsar el icono de buscar). Todos los
 * filtros -- nombre, género, país -- se combinan en la misma llamada
 * a `searchStations()`, igual que hace la propia API.
 *
 * `playStation()` reproduce directamente `urlResolved` -- a diferencia
 * de SearchViewModel.playTrack(), no hace falta StreamResolver: la
 * URL que da Radio-Browser.info ya viene resuelta (playlists/
 * redirecciones ya procesadas por el propio servicio). `artist` se
 * deja a null a propósito -- una emisora no es "una canción de un
 * artista", y pasar el país o el género ahí dispararía sin sentido la
 * lógica de "Radio" de H08 (sugerencia de artista relacionado) si
 * esta emisora fuera lo último en la cola.
 * ---
 * ViewModel for the "Radio Online" screen (H09 STEP 3, S010).
 *
 * On opening the screen, genre chips (`/json/tags`) and the country
 * list (`/json/countries`) are loaded, and an initial filter-less
 * search (ordered by votes) runs so the screen isn't empty on open --
 * same approach any radio directory takes. Tapping a genre chip or
 * picking a country re-runs the search immediately (unlike the text
 * field, which follows SearchScreen's established pattern: press the
 * search icon). All filters -- name, genre, country -- combine into
 * the same `searchStations()` call, same as the API itself does.
 */
@HiltViewModel
class RadioBrowserViewModel @Inject constructor(
    private val radioBrowserRepository: RadioBrowserRepository,
    private val playerManager: PlayerManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RadioBrowserUiState())
    val uiState: StateFlow<RadioBrowserUiState> = _uiState.asStateFlow()

    init {
        loadFilters()
        search()
    }

    private fun loadFilters() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFilters = true)
            // Top 40 por número real de emisoras -- una lista de miles
            // de etiquetas/países no cabe como chips en pantalla, y las
            // menos usadas no aportan nada como filtro rápido. La API
            // ya las devuelve ordenadas por stationcount descendente
            // (ver RadioBrowserApiService), así que tomar los primeros
            // 40 ya da las más relevantes.
            val tags = radioBrowserRepository.getTags().take(40)
            val countries = radioBrowserRepository.getCountries().take(60)
            _uiState.value = _uiState.value.copy(
                tags = tags,
                countries = countries,
                isLoadingFilters = false,
            )
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun onTagSelect(tag: String?) {
        _uiState.value = _uiState.value.copy(selectedTag = tag)
        search()
    }

    fun onCountrySelect(countryCode: String?) {
        _uiState.value = _uiState.value.copy(selectedCountryCode = countryCode)
        search()
    }

    fun search() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            val state = _uiState.value
            val stations = radioBrowserRepository.searchStations(
                name = state.query,
                tag = state.selectedTag,
                countryCode = state.selectedCountryCode,
            )
            _uiState.value = _uiState.value.copy(
                stations = stations,
                isSearching = false,
            )
        }
    }

    fun playStation(station: RadioStation) {
        playerManager.play(
            streamUrl = station.urlResolved,
            title = station.name,
            isLocal = false,
            artist = null,
        )
    }
}

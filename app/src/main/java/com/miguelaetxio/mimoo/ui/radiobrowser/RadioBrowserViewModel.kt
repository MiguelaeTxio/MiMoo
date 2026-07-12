package com.miguelaetxio.mimoo.ui.radiobrowser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.remote.RadioBrowserRepository
import com.miguelaetxio.mimoo.data.remote.RadioDecade
import com.miguelaetxio.mimoo.data.remote.RadioGenreCatalog
import com.miguelaetxio.mimoo.data.remote.RadioGenreCategory
import com.miguelaetxio.mimoo.data.remote.dto.RadioCountry
import com.miguelaetxio.mimoo.data.remote.dto.RadioStation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RadioBrowserUiState(
    val query: String = "",
    val countries: List<RadioCountry> = emptyList(),
    val selectedGenre: RadioGenreCategory? = null,
    val selectedDecade: RadioDecade? = null,
    val selectedCountryCode: String? = null,
    val stations: List<RadioStation> = emptyList(),
    val isLoadingFilters: Boolean = false,
    val isSearching: Boolean = false,
) {
    val genres: List<RadioGenreCategory> get() = RadioGenreCatalog.genreCategories
    val decades: List<RadioDecade> get() = RadioGenreCatalog.decades
}

/**
 * ViewModel de la pantalla "Radio Online" (H09 PASO 3, S010; géneros
 * y décadas curados añadidos en la misma sesión a petición explícita
 * de Miguel Ángel).
 *
 * Género y década son catálogos propios (RadioGenreCatalog), no los
 * tags crudos de Radio-Browser.info -- cada uno agrupa varios
 * términos de búsqueda reales. País sigue viniendo en vivo de
 * `/json/countries`, ya que ahí sí hay una lista oficial y acotada
 * que tiene sentido usar tal cual.
 *
 * Combinación de filtros: género y década se buscan cada uno con
 * `searchByAnyTag()` (fusión OR de sus propios términos) y, si ambos
 * están activos a la vez, el resultado final es la INTERSECCIÓN de
 * los dos conjuntos por `stationuuid` -- "Electrónica de los 80" es
 * el cruce de "cualquier término de Electrónica" con "cualquier
 * término de los 80", no la unión de ambos. Si solo hay uno activo,
 * se usa directamente su resultado. Si no hay ninguno, se hace una
 * búsqueda simple (searchStations(), orden por votos) igual que
 * antes de tener el catálogo curado.
 * ---
 * ViewModel for the "Radio Online" screen (H09 STEP 3, S010; curated
 * genres/decades added in the same session per Miguel Ángel's
 * explicit request).
 *
 * Genre and decade are our own catalogs (RadioGenreCatalog), not
 * Radio-Browser.info's raw tags. Country still comes live from
 * `/json/countries`.
 *
 * Filter combination: genre and decade each search with
 * `searchByAnyTag()` (OR merge of their own terms) and, if both are
 * active at once, the final result is the INTERSECTION of the two
 * sets by `stationuuid`. If only one is active, its result is used
 * directly. If neither is active, a plain search runs (same as
 * before the curated catalog existed).
 */
@HiltViewModel
class RadioBrowserViewModel @Inject constructor(
    private val radioBrowserRepository: RadioBrowserRepository,
    private val playerManager: PlayerManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RadioBrowserUiState())
    val uiState: StateFlow<RadioBrowserUiState> = _uiState.asStateFlow()

    init {
        loadCountries()
        search()
    }

    fun retryLoadCountries() {
        loadCountries()
    }

    private fun loadCountries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFilters = true)
            // Top 60 por número real de emisoras -- ver comentario
            // equivalente ya existente para tags antes de esta
            // revisión: una lista de cientos de países no cabe como
            // chips, y `/json/countries` ya llega ordenado por
            // stationcount descendente (ver RadioBrowserApiService).
            //
            // distinctBy() (S010, tras crash real: "Key '1' was
            // already used" en el LazyRow) -- la base de datos
            // comunitaria de Radio-Browser.info es conocida por tener
            // filas duplicadas (ver documentación de terceros:
            // "API is brittle... returns duplicate stations"), y aquí
            // pasaba lo mismo con países: dos filas con el mismo
            // isoCode/name -- daba el mismo key en items(), y Compose
            // exige claves únicas dentro de un mismo LazyRow o lanza
            // excepción y revienta la pantalla entera.
            val countries = radioBrowserRepository.getCountries()
                .distinctBy { it.isoCode ?: it.name }
                .take(60)
            _uiState.value = _uiState.value.copy(
                countries = countries,
                isLoadingFilters = false,
            )
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun onGenreSelect(genre: RadioGenreCategory?) {
        _uiState.value = _uiState.value.copy(
            selectedGenre = if (_uiState.value.selectedGenre == genre) null else genre,
        )
        search()
    }

    fun onDecadeSelect(decade: RadioDecade?) {
        _uiState.value = _uiState.value.copy(
            selectedDecade = if (_uiState.value.selectedDecade == decade) null else decade,
        )
        search()
    }

    fun onCountrySelect(countryCode: String?) {
        _uiState.value = _uiState.value.copy(
            selectedCountryCode = if (_uiState.value.selectedCountryCode == countryCode) {
                null
            } else {
                countryCode
            },
        )
        search()
    }

    fun search() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            val state = _uiState.value
            val name = state.query
            val countryCode = state.selectedCountryCode
            val genre = state.selectedGenre
            val decade = state.selectedDecade

            val stations = when {
                genre != null && decade != null -> {
                    val genreMatches = radioBrowserRepository.searchByAnyTag(
                        genre.matchTerms,
                        name = name,
                        countryCode = countryCode,
                    )
                    val decadeIds = radioBrowserRepository.searchByAnyTag(
                        decade.matchTerms,
                        name = name,
                        countryCode = countryCode,
                    ).map { it.stationUuid }.toSet()
                    genreMatches.filter { it.stationUuid in decadeIds }
                }
                genre != null -> radioBrowserRepository.searchByAnyTag(
                    genre.matchTerms,
                    name = name,
                    countryCode = countryCode,
                )
                decade != null -> radioBrowserRepository.searchByAnyTag(
                    decade.matchTerms,
                    name = name,
                    countryCode = countryCode,
                )
                else -> radioBrowserRepository.searchStations(
                    name = name,
                    tag = null,
                    countryCode = countryCode,
                )
            }

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

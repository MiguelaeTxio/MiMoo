package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.RadioCountry
import com.miguelaetxio.mimoo.data.remote.dto.RadioStation
import com.miguelaetxio.mimoo.data.remote.dto.RadioTag
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositorio de Radio-Browser.info (H09 PASO 2, S010) -- emisoras de
 * radio online por género/tema/país/búsqueda de texto. Mismo patrón
 * defensivo que CoverArtRepository y RadioRepository (H08): nunca
 * lanza excepción, cualquier fallo de red o de parseo se trata igual
 * que "sin resultados", devolviendo lista vacía en vez de romper la
 * pantalla que llama.
 *
 * `getTags()`/`getCountries()` alimentan los filtros de la UI (PASO 3)
 * con datos reales del servicio, nunca una lista escrita a mano --
 * ordenados por número de emisoras descendente (ver
 * RadioBrowserApiService, `order=stationcount&reverse=true`).
 * ---
 * Radio-Browser.info repository (H09 STEP 2, S010) -- online radio
 * stations by genre/topic/country/free-text search. Same defensive
 * pattern as CoverArtRepository and RadioRepository (H08): never
 * throws, any network or parsing failure is treated the same as "no
 * results", returning an empty list instead of breaking the calling
 * screen.
 */
@Singleton
class RadioBrowserRepository @Inject constructor(
    private val radioBrowserApiService: RadioBrowserApiService,
) {
    /**
     * Búsqueda combinada -- todos los parámetros son opcionales y se
     * pasan tal cual a la API (name/tag/countryCode en blanco se
     * traducen a null para no mandar un filtro vacío que la API
     * podría interpretar como "coincide con cadena vacía").
     * ---
     * Combined search -- all parameters optional, passed through as-is
     * to the API (blank name/tag/countryCode become null so an empty
     * filter isn't sent, which the API could interpret as "matches
     * empty string").
     */
    suspend fun searchStations(
        name: String? = null,
        tag: String? = null,
        countryCode: String? = null,
    ): List<RadioStation> = try {
        radioBrowserApiService.searchStations(
            name = name?.trim()?.ifBlank { null },
            tag = tag?.trim()?.ifBlank { null },
            countryCode = countryCode?.trim()?.ifBlank { null },
        )
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun getTags(): List<RadioTag> = try {
        radioBrowserApiService.getTags()
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun getCountries(): List<RadioCountry> = try {
        radioBrowserApiService.getCountries()
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Búsqueda "género/década curado" (H09, S010) -- catálogo propio
     * en RadioGenreCatalog: cada categoría es un grupo de varios
     * términos de tag reales de Radio-Browser.info (p. ej.
     * "Electrónica" = house/techno/trance/dance/minimal...). La API
     * solo permite un `tag=` por llamada, así que se lanza una
     * búsqueda por término EN PARALELO (`async`, más rápido que
     * secuencial con varios términos) y se fusionan los resultados sin
     * duplicados por `stationuuid`, ordenados por votos descendente.
     *
     * Nunca lanza -- si un término concreto falla o no tiene
     * coincidencias, sus resultados simplemente no aportan nada; el
     * resto de términos sigue funcionando igual (mismo principio
     * defensivo que searchStations()).
     * ---
     * "Curated genre/decade" search (H09, S010) -- own catalog in
     * RadioGenreCatalog: each category is a group of several real
     * Radio-Browser.info tag terms. The API only allows one `tag=` per
     * call, so one search per term runs IN PARALLEL (`async`, faster
     * than sequential for several terms) and results are merged
     * without duplicates by `stationuuid`, sorted by votes descending.
     *
     * Never throws -- if a given term fails or has no matches, its
     * results simply contribute nothing; the rest of the terms keep
     * working the same (same defensive principle as searchStations()).
     */
    suspend fun searchByAnyTag(
        matchTerms: List<String>,
        name: String? = null,
        countryCode: String? = null,
    ): List<RadioStation> = coroutineScope {
        matchTerms
            .map { term ->
                async {
                    try {
                        radioBrowserApiService.searchStations(
                            name = name?.trim()?.ifBlank { null },
                            tag = term,
                            countryCode = countryCode?.trim()?.ifBlank { null },
                        )
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }
            .flatMap { it.await() }
            .distinctBy { it.stationUuid }
            .sortedByDescending { it.votes ?: 0 }
    }
}

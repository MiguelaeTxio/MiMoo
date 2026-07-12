package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.RadioCountry
import com.miguelaetxio.mimoo.data.remote.dto.RadioStation
import com.miguelaetxio.mimoo.data.remote.dto.RadioTag
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Radio-Browser.info (H09, PASO 1, S010) -- directorio comunitario de
 * emisoras de radio online, sin API key. Raíz fija
 * https://de1.api.radio-browser.info/ (enlazada en NetworkModule,
 * decisión de simplificación de ANNEX_H09.md: un único mirror en vez
 * de descubrimiento dinámico de servidores).
 *
 * Todos los endpoints aceptan GET con query params (verificado
 * también en la documentación oficial: los mismos parámetros pueden
 * ir por POST x-www-form-urlencoded, pero GET simple funciona igual
 * y es lo que ya usa el resto de la app). `searchStations` es el
 * único endpoint combinado (nombre + tag + país a la vez); las rutas
 * `bytag`/`bycountry` dedicadas del anexo no hacen falta porque
 * `search` ya cubre ambos casos con parámetros opcionales.
 * ---
 * Radio-Browser.info (H09, PASO 1, S010) -- community directory of
 * online radio stations, no API key required. Fixed root
 * https://de1.api.radio-browser.info/ (bound in NetworkModule,
 * simplification decision from ANNEX_H09.md: a single mirror instead
 * of dynamic server discovery).
 */
interface RadioBrowserApiService {

    /**
     * Búsqueda combinada por nombre/etiqueta/país, todos opcionales.
     * `hidebroken=true` descarta emisoras que fallaron el último
     * chequeo automático -- more reliable than filtering by
     * `lastcheckok` client-side after the fact, per the official docs.
     * ---
     * Combined search by name/tag/country, all optional. `hidebroken`
     * discards stations that failed their last automated check.
     */
    @GET("json/stations/search")
    suspend fun searchStations(
        @Query("name") name: String? = null,
        @Query("tag") tag: String? = null,
        @Query("countrycode") countryCode: String? = null,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true,
        @Query("limit") limit: Int = 50,
    ): List<RadioStation>

    /**
     * Lista de etiquetas (géneros/temas) con su número real de
     * emisoras -- de aquí sale el listado de filtros de la UI (PASO 3),
     * no de una lista inventada a mano.
     * ---
     * List of tags (genres/topics) with their real station count --
     * this is what feeds the UI's filter list (STEP 3), not a
     * hand-written list.
     */
    @GET("json/tags")
    suspend fun getTags(
        @Query("order") order: String = "stationcount",
        @Query("reverse") reverse: Boolean = true,
        @Query("hidebroken") hideBroken: Boolean = true,
    ): List<RadioTag>

    /**
     * Lista de países con su número real de emisoras.
     * ---
     * List of countries with their real station count.
     */
    @GET("json/countries")
    suspend fun getCountries(
        @Query("order") order: String = "stationcount",
        @Query("reverse") reverse: Boolean = true,
        @Query("hidebroken") hideBroken: Boolean = true,
    ): List<RadioCountry>
}

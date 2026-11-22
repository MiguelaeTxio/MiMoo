package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.remote.dto.RadioStation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * miMooutCast estilo Shoutcast (S037, orden de Miguel Ángel
 * 2026-11-22: "mira a ver lo que es Shoutcast y replícalo en MiMoo...
 * que funcione con géneros, con décadas y con países").
 *
 * Un Shoutcast no elige canciones sueltas: es un directorio de
 * EMISORAS reales ya emitiendo, filtradas por género, país o época.
 * Aquí se usa el mismo directorio que H09 (Radio-Browser.info) para
 * devolver emisoras en directo según la elección de miMooutCast -- sin
 * semillas de enlaces, sin YouTube, sin MusicBrainz, sin offsets.
 */
@Singleton
class MimooutcastStationRepository @Inject constructor(
    private val radioBrowserRepository: RadioBrowserRepository,
) {
    private val genreSynonyms = mapOf(
        "classical" to listOf("classical", "clasica", "clásica"),
        "latin" to listOf("latin", "latino"),
        "r&b" to listOf("r&b", "rnb"),
        "hip hop" to listOf("hip hop", "hiphop", "rap"),
        "electronic" to listOf("electronic", "electronica"),
        "edm" to listOf("edm", "dance"),
        "reggaeton" to listOf("reggaeton", "reggaetón"),
        "flamenco" to listOf("flamenco"),
        "indie rock" to listOf("indie rock", "indie"),
        "country" to listOf("country"),
    )

    private val worldCountries = listOf(
        "JP", "KR", "IN", "TR", "ZA", "NG", "EG", "MA", "ID", "TH", "PH", "IL", "LB", "GH", "KE", "IR",
    )

    private val excludedTags = listOf("news", "talk", "sport", "noticias", "podcast", "religio")

    private fun decadeTerms(decadeBegin: Int): List<String> = when (decadeBegin) {
        1950 -> listOf("50s", "1950s")
        1960 -> listOf("60s", "1960s")
        1970 -> listOf("70s", "1970s")
        1980 -> listOf("80s", "1980s")
        1990 -> listOf("90s", "1990s")
        2000 -> listOf("2000s", "00s")
        2010 -> listOf("2010s", "10s")
        else -> listOf("2020s", "20s")
    }

    /**
     * Emisoras reales para la elección, de más a menos votadas (se
     * barajan las mejores para no empezar siempre por la misma). Con
     * más de una dimensión se cruzan; si el cruce queda vacío se usa
     * la primera dimensión con resultados (género, década, origen).
     */
    suspend fun findStations(genre: String?, decadeBegin: Int?, group: OriginGroup?): List<RadioStation> =
        coroutineScope {
            val genreSet = genre?.takeIf { it.isNotBlank() }?.let { g ->
                async { radioBrowserRepository.searchByAnyTag(genreSynonyms[g] ?: listOf(g)) }
            }
            val decadeSet = decadeBegin?.let { d ->
                async { radioBrowserRepository.searchByAnyTag(decadeTerms(d)) }
            }
            val originSet = group?.let { gr ->
                val codes = if (gr == OriginGroup.MUNDIAL) worldCountries else OriginGroup.countriesOf(gr).toList()
                async {
                    codes.map { code -> async { radioBrowserRepository.searchStations(countryCode = code) } }
                        .flatMap { it.await() }
                }
            }
            val sets = listOfNotNull(genreSet?.await(), decadeSet?.await(), originSet?.await())
            val nonEmpty = sets.filter { it.isNotEmpty() }
            val merged = if (nonEmpty.size == sets.size && sets.size > 1) {
                val ids = sets.drop(1).map { s -> s.map { it.stationUuid }.toSet() }
                sets.first().filter { st -> ids.all { st.stationUuid in it } }
                    .ifEmpty { nonEmpty.firstOrNull().orEmpty() }
            } else {
                nonEmpty.firstOrNull().orEmpty()
            }
            val clean = merged
                .distinctBy { it.stationUuid }
                .filter { it.urlResolved.isNotBlank() }
                .filter { st ->
                    val tags = st.tags.orEmpty().lowercase()
                    excludedTags.none { it in tags }
                }
                .sortedByDescending { it.votes ?: 0 }
            clean.take(40).let { top -> top.take(10).shuffled() + top.drop(10) }
        }
}

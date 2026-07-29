package com.miguelaetxio.mimoo.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * S025 -- WIKIDATA COMO FUENTE DE LA FECHA DE PRIMERA EDICIÓN.
 *
 * Orden de Miguel Ángel: *"si hay que ir a Wikipedia, se va. Si hay que
 * mirarlo en Wikipedia, en Discogs, en mierda frita, donde se ponga que
 * se mira."*
 *
 * Se usa Wikidata y no Wikipedia a secas porque Wikidata es la parte
 * estructurada del mismo proyecto: los datos están en propiedades con
 * identificador fijo en vez de en prosa. Para lo que hace falta aquí
 * hay una propiedad exacta, `P577` (fecha de publicación), sobre la
 * entidad de la OBRA. No hay que interpretar texto ni adivinar.
 *
 * No necesita credenciales ni token, a diferencia de Discogs. Sí pide
 * un User-Agent identificable, igual que MusicBrainz.
 *
 * Se consulta vía SPARQL contra el endpoint público. La consulta busca
 * una obra cuyo título case con el tema y cuyo intérprete o autor case
 * con el artista, y devuelve el año más antiguo de `P577`.
 * ---
 * S025 -- Wikidata as a source for a track's first publication date.
 * Wikidata rather than Wikipedia because it's the structured half of
 * the same project: there's an exact property, `P577` (publication
 * date), on the work entity, so nothing has to be parsed out of prose.
 * Needs no credentials, only an identifiable User-Agent.
 */
interface WikidataApiService {

    @GET("sparql")
    suspend fun query(
        @Query("query") sparql: String,
        @Query("format") format: String = "json",
    ): WikidataSparqlResponse
}

data class WikidataSparqlResponse(
    val results: WikidataResults = WikidataResults(),
)

data class WikidataResults(
    val bindings: List<Map<String, WikidataValue>> = emptyList(),
)

data class WikidataValue(
    val type: String = "",
    val value: String = "",
)

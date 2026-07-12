package com.miguelaetxio.mimoo.data.remote

/**
 * Catálogo curado propio de géneros y décadas para "Radio Online"
 * (H09, S010). Radio-Browser.info no tiene una taxonomía oficial de
 * géneros -- `/json/tags` devuelve miles de etiquetas libres escritas
 * por la propia comunidad (a menudo variantes: "house", "deep house",
 * "tech house"...), inservibles como filtro directo en la UI.
 *
 * Petición explícita de Miguel Ángel (S010): "podríamos definir,
 * aunque ellos no lo tengan definido... música electrónica, y dentro
 * de música electrónica, meter dance, meter minimal... eso lo tendrías
 * que definir tú con la heurística". Cada categoría es un grupo de
 * términos de búsqueda reales (verificados contra ejemplos oficiales
 * de tags de Radio-Browser.info: "jazz,pop,rock,indie",
 * "pop,rock,news,bbc"...) que se envían como coincidencia parcial
 * (`tag=`, la API busca por subcadena -- ver RadioBrowserApiService),
 * así que un término no encontrado en un momento dado simplemente no
 * aporta resultados, sin romper nada (RadioBrowserRepository sigue
 * siendo defensivo).
 *
 * No pretende ser exhaustivo ni definitivo -- es una heurística de
 * partida, ampliable si en uso real se detectan géneros importantes
 * sin cubrir.
 * ---
 * Curated genre/decade catalog for "Radio Online" (H09, S010).
 * Radio-Browser.info has no official genre taxonomy -- `/json/tags`
 * returns thousands of free-text, community-written tags (often
 * variants of the same thing), useless as a direct UI filter.
 *
 * Explicit request from Miguel Ángel (S010): define our own curated
 * categories on top of the raw data via heuristics. Each category is
 * a group of real search terms sent as partial matches (`tag=`, the
 * API does substring matching), so a term that doesn't currently
 * exist in the data just contributes zero results, without breaking
 * anything.
 */

data class RadioGenreCategory(val label: String, val matchTerms: List<String>)

data class RadioDecade(val label: String, val matchTerms: List<String>)

object RadioGenreCatalog {

    val genreCategories: List<RadioGenreCategory> = listOf(
        RadioGenreCategory(
            "Electrónica",
            listOf(
                "electronic", "electronica", "house", "deep house", "tech house",
                "techno", "trance", "dance", "edm", "dubstep", "drum and bass",
                "dnb", "minimal", "disco", "chillout", "lounge",
            ),
        ),
        RadioGenreCategory(
            "Rock",
            listOf(
                "rock", "classic rock", "hard rock", "indie rock", "alternative rock",
                "grunge", "punk", "punk rock",
            ),
        ),
        RadioGenreCategory(
            "Metal",
            listOf("metal", "heavy metal", "death metal", "black metal", "thrash metal"),
        ),
        RadioGenreCategory(
            "Pop",
            listOf("pop", "top 40", "chart", "charts"),
        ),
        RadioGenreCategory(
            "Hip-Hop / Rap",
            listOf("hip hop", "hiphop", "rap", "trap", "urban"),
        ),
        RadioGenreCategory(
            "Jazz",
            listOf("jazz", "swing", "smooth jazz", "bossa nova"),
        ),
        RadioGenreCategory(
            "Clásica",
            listOf("classical", "classic", "opera", "orchestra", "orchestral"),
        ),
        RadioGenreCategory(
            "Reggae / Ska",
            listOf("reggae", "ska", "dancehall", "dub"),
        ),
        RadioGenreCategory(
            "Latina",
            listOf(
                "latin", "latino", "salsa", "reggaeton", "bachata", "merengue",
                "cumbia", "spanish",
            ),
        ),
        RadioGenreCategory(
            "Folk / Country",
            listOf("folk", "country", "bluegrass", "americana"),
        ),
        RadioGenreCategory(
            "Blues",
            listOf("blues", "rhythm and blues", "rnb", "soul", "funk"),
        ),
        RadioGenreCategory(
            "Noticias / Charla",
            listOf("news", "talk", "sport", "sports"),
        ),
    )

    val decades: List<RadioDecade> = listOf(
        RadioDecade("Años 50", listOf("50s", "1950s")),
        RadioDecade("Años 60", listOf("60s", "1960s")),
        RadioDecade("Años 70", listOf("70s", "1970s")),
        RadioDecade("Años 80", listOf("80s", "1980s")),
        RadioDecade("Años 90", listOf("90s", "1990s")),
        RadioDecade("Años 2000", listOf("2000s", "00s")),
        RadioDecade("Años 2010", listOf("2010s", "10s")),
        RadioDecade("Oldies", listOf("oldies")),
    )
}

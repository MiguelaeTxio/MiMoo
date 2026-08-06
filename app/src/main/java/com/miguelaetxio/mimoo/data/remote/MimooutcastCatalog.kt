package com.miguelaetxio.mimoo.data.remote

/**
 * H15 (miMooutCast) -- catálogo FIJO de géneros y décadas para elegir
 * el ancla de la Radio a mano, decisión cerrada con Miguel Ángel en
 * S029: lista fija, no una consulta en vivo a la API de géneros de
 * MusicBrainz -- ver `DOCS/ANNEX_H15.md`, "COMPLETADAS EN S029" punto
 * 3.
 *
 * **Géneros:** cadenas verificadas UNA A UNA contra `genre_tree.json`
 * (la misma taxonomía real de MusicBrainz que ya usa la Radio
 * automática, `GenreTree.kt`) -- no inventadas. `mbGenre` es el
 * término exacto que se manda como ancla (`RadioAnchor.genre`), el
 * mismo idioma/forma que usa el resto del motor. No pretende ser
 * exhaustivo, mismo criterio que `RadioGenreCatalog` (H09) -- lista de
 * partida, ampliable si en uso real se echa en falta un género
 * importante.
 *
 * **Décadas:** rango 1950-2020, mismo precedente ya usado en H09
 * (`RadioGenreCatalog.decades`, que llega hasta "Años 2010") extendido
 * una década más porque estamos en 2026. Asunción declarada a Miguel
 * Ángel al construir esto -- cambiar si no vale.
 */
data class MimooutcastGenre(val label: String, val mbGenre: String)

data class MimooutcastDecade(val label: String, val decadeBegin: Int)

data class MimooutcastOrigin(val label: String, val group: OriginGroup)

object MimooutcastCatalog {

    val genres: List<MimooutcastGenre> = listOf(        MimooutcastGenre("Rock", "rock"),
        MimooutcastGenre("Pop", "pop"),
        MimooutcastGenre("Hip Hop", "hip hop"),
        MimooutcastGenre("Electrónica", "electronic"),
        MimooutcastGenre("Jazz", "jazz"),
        MimooutcastGenre("Blues", "blues"),
        MimooutcastGenre("Clásica", "classical"),
        MimooutcastGenre("Folk", "folk"),
        MimooutcastGenre("Metal", "metal"),
        MimooutcastGenre("Punk", "punk"),
        MimooutcastGenre("Reggae", "reggae"),
        MimooutcastGenre("Soul", "soul"),
        MimooutcastGenre("Funk", "funk"),
        MimooutcastGenre("Country", "country"),
        MimooutcastGenre("Flamenco", "flamenco"),
        MimooutcastGenre("Latina", "latin"),
        MimooutcastGenre("R&B", "r&b"),
        MimooutcastGenre("Disco", "disco"),
        MimooutcastGenre("Rock indie", "indie rock"),
        MimooutcastGenre("Post-punk", "post-punk"),
        MimooutcastGenre("Reggaetón", "reggaeton"),
        MimooutcastGenre("Salsa", "salsa"),
        MimooutcastGenre("House", "house"),
        MimooutcastGenre("Techno", "techno"),
    )

    val decades: List<MimooutcastDecade> = listOf(
        MimooutcastDecade("Años 50", 1950),
        MimooutcastDecade("Años 60", 1960),
        MimooutcastDecade("Años 70", 1970),
        MimooutcastDecade("Años 80", 1980),
        MimooutcastDecade("Años 90", 1990),
        MimooutcastDecade("Años 2000", 2000),
        MimooutcastDecade("Años 2010", 2010),
        MimooutcastDecade("Años 2020", 2020),
    )

    /** Mismos cuatro grupos ya cerrados en H08 (S026) -- `OriginGroup`. */
    val origins: List<MimooutcastOrigin> = listOf(
        MimooutcastOrigin("Hispanoamérica", OriginGroup.HISPANOAMERICA),
        MimooutcastOrigin("Anglosajona", OriginGroup.ANGLOSAJONA),
        MimooutcastOrigin("Europea", OriginGroup.EUROPEA),
        MimooutcastOrigin("Mundial", OriginGroup.MUNDIAL),
    )
}

package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity

/**
 * Elección persistida de MusicBrainz ID para un nombre de artista
 * normalizado (H12) -- se dispara la primera vez que ArtistScreen
 * entra a un nombre y MusicBrainz devuelve más de un candidato con
 * MBID distinto para ese normalizedNameKey. Distinto de la
 * normalización en sí (normalizeArtistName(), función util, no tabla):
 * esta tabla solo guarda el caso de homónimos REALES (dos artistas
 * distintos con el mismo nombre normalizado), no las variantes del
 * mismo artista.
 * ---
 * Persisted MusicBrainz ID choice for a normalized artist name (H12)
 * -- triggered the first time ArtistScreen enters a name and
 * MusicBrainz returns more than one candidate with a distinct MBID
 * for that normalizedNameKey. Distinct from normalization itself
 * (normalizeArtistName(), a util function, not a table): this table
 * only stores the REAL-homonym case (two distinct artists sharing the
 * same normalized name), not variants of the same artist.
 */
@Entity(
    tableName = "artist_disambiguations",
    primaryKeys = ["normalizedNameKey"],
)
data class ArtistDisambiguation(
    val normalizedNameKey: String,
    val chosenMbid: String,
)

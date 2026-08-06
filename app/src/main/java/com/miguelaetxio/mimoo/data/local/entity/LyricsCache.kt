package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity

/**
 * Caché local de letras (H17, S031, punto 3 del anexo) -- primera
 * consulta a lrclib.net por artista+título, resultado guardado aquí;
 * consultas siguientes del mismo tema se sirven de esta tabla sin
 * red. Clave compuesta normalizada (artistKey, titleKey) vía
 * SearchNormalizer.normalizeArtistName()/songTitleKey() -- mismo
 * mecanismo que ya usa DislikedTrackRepository para colapsar
 * versiones distintas del mismo tema (directo/remasterizado/estudio)
 * a una única clave, así no se repite la consulta a lrclib.net para
 * cada versión de un mismo tema. `artist`/`title` guardan los valores
 * TAL CUAL se consultaron, para mostrarlos en la pantalla de búsqueda
 * del drawer sin tener que desnormalizarlos.
 *
 * `plainLyrics`/`syncedLyrics` nulos junto a `hasLyrics = false`
 * representa el caso "sin letra confirmado" (punto 2 del anexo): la
 * consulta a lrclib.net ya se hizo y no hay letra de ningún tipo, así
 * que no hace falta repetirla. Es un estado real distinto de "todavía
 * no consultado" (fila inexistente en la tabla).
 * ---
 * Local lyrics cache (H17, S031, annex point 3) -- first query to
 * lrclib.net by artist+title, result stored here; later queries for
 * the same track are served from this table without network.
 * Composite normalized key, same mechanism DislikedTrackRepository
 * already uses to collapse different versions of the same track into
 * one key. `hasLyrics = false` with both lyrics fields null is the
 * confirmed "no lyrics" state (annex point 2), distinct from "not
 * queried yet" (row doesn't exist).
 */
@Entity(
    tableName = "lyrics_cache",
    primaryKeys = ["artistKey", "titleKey"],
)
data class LyricsCache(
    val artistKey: String,
    val titleKey: String,
    val artist: String,
    val title: String,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val hasLyrics: Boolean,
    val cachedAt: Long,
)

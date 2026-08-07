package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Sencillo marcado como favorito EN STREAMING -- concepto nuevo
 * (sesión de diseño de Favoritos, 2026-08-02), separado del favorito
 * por pista ya descargada (SearchResultTrack.isFavorite, que exige
 * fila local y sigue existiendo tal cual). Mismo patrón que
 * FavoriteRadioStation: guarda lo mínimo para pintar la fila sin
 * depender de que exista una fila en search_result_tracks --
 * youtubeId, título, artista, miniatura y duración.
 *
 * Un sencillo puede estar favoriteado en las DOS tablas a la vez (se
 * favoriteó en streaming y más tarde se descargó, o al revés) -- no
 * se migra ni se fusiona: la pantalla de Favoritos lo considera
 * favorito si aparece en cualquiera de las dos, ver
 * FavoritesRepository.
 * ---
 * A single track marked as favorite IN STREAMING -- new concept
 * (Favorites design session, 2026-08-02), separate from the
 * already-downloaded per-track favorite (SearchResultTrack.isFavorite,
 * which requires a local row and keeps existing as-is). Same pattern
 * as FavoriteRadioStation: stores the minimum needed to render the row
 * without depending on a row existing in search_result_tracks --
 * youtubeId, title, artist, thumbnail and duration.
 *
 * A track can be favorited in BOTH tables at once (favorited in
 * streaming and later downloaded, or the other way around) -- no
 * migration or merge happens: the Favorites screen considers it a
 * favorite if it appears in either one, see FavoritesRepository.
 */
@Entity(tableName = "favorite_tracks")
data class FavoriteTrack(
    @PrimaryKey val youtubeId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val addedAt: Long = System.currentTimeMillis(),
)

package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity

/**
 * Álbum marcado como favorito -- concepto NUEVO y separado del
 * favorito por pista (SearchResultTrack.isFavorite). Petición
 * explícita de Miguel Ángel (2026-07-05): "para añadir álbumes no
 * tenemos favoritos... tenemos que tener dos tipos de listas, una de
 * favoritos de álbumes, y otra de sencillos favoritos". Clave
 * compuesta (artist, album) porque un álbum no es una entidad propia
 * en Room -- se agrupa por esos dos campos de texto en
 * LibraryViewModel, igual que en el resto de la app.
 * ---
 * Album marked as favorite -- a NEW concept, separate from the
 * per-track favorite (SearchResultTrack.isFavorite). Explicit request
 * from Miguel Ángel (2026-07-05): "for albums we don't have favorites
 * yet... we need two kinds of lists, one for favorite albums, and
 * another for favorite singles". Composite key (artist, album) because
 * an album isn't its own entity in Room -- it's grouped by those two
 * text fields in LibraryViewModel, same as everywhere else in the app.
 */
@Entity(
    tableName = "favorite_albums",
    primaryKeys = ["artist", "album"],
)
data class FavoriteAlbum(
    val artist: String,
    val album: String,
)

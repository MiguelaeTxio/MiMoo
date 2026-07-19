package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity

/**
 * Artista marcado como favorito -- concepto NUEVO (H12), independiente
 * de si hay algo descargado o favoriteado de él a nivel de álbum/pista
 * (FavoriteAlbum, SearchResultTrack.isFavorite). Clave primaria simple
 * por nombre de texto (artist), mismo patrón que FavoriteAlbum -- el
 * artista tampoco es una entidad propia en Room, se identifica por
 * nombre igual que en el resto de la app (Radio de H08, etc.).
 * ---
 * Artist marked as favorite -- NEW concept (H12), independent of
 * whether anything from that artist is downloaded or favorited at the
 * album/track level (FavoriteAlbum, SearchResultTrack.isFavorite).
 * Simple primary key by text name (artist), same pattern as
 * FavoriteAlbum -- an artist isn't its own entity in Room either, it's
 * identified by name just like everywhere else in the app (H08 Radio,
 * etc.).
 */
@Entity(
    tableName = "favorite_artists",
    primaryKeys = ["artist"],
)
data class FavoriteArtist(
    val artist: String,
)

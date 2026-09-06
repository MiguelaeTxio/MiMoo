package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * S059 -- caché permanente de la foto real de un artista, resuelta vía
 * Deezer (ver ArtistImageRepository) -- petición explícita de Miguel
 * Ángel: "¿podemos sacar la imagen de los artistas de algún sitio?".
 *
 * `imageUrl` es nullable A PROPÓSITO como valor real (no como "todavía
 * sin resolver"): la FILA EXISTE en cuanto se ha intentado la
 * búsqueda, con `imageUrl = null` si Deezer no tenía ningún artista
 * con ese nombre -- así no se reintenta la red en cada recomposición
 * para un artista que ya se sabe que no tiene foto (mismo patrón de
 * "búsqueda ya hecha, sin resultado" que `coverArtUrl` en
 * SearchResultTrack, pero aquí con una tabla propia porque el artista
 * no es una fila con clave primaria propia en ningún otro sitio de la
 * app).
 * ---
 * S059 -- permanent cache of an artist's real photo, resolved via
 * Deezer (see ArtistImageRepository) -- explicit request from Miguel
 * Ángel: "can we get artist images from somewhere?".
 *
 * `imageUrl` is nullable ON PURPOSE as a real value (not as "not yet
 * resolved"): the ROW EXISTS as soon as the lookup has been attempted,
 * with `imageUrl = null` if Deezer had no artist under that name --
 * this way the network isn't retried on every recomposition for an
 * artist already known to have no photo (same "already searched, no
 * result" pattern as `coverArtUrl` on SearchResultTrack, but with its
 * own table here because the artist has no primary-key row of its own
 * anywhere else in the app).
 */
@Entity(tableName = "artist_images")
data class ArtistImage(
    @PrimaryKey val artist: String,
    val imageUrl: String?,
    val resolvedAt: Long = System.currentTimeMillis(),
)

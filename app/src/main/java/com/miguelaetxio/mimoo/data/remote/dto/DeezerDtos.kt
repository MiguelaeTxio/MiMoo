package com.miguelaetxio.mimoo.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * S059 -- respuesta de la búsqueda de artistas de Deezer, usada solo
 * para sacar una foto real de artista (ver ArtistImageRepository).
 * Deezer no exige clave ni autenticación para este endpoint público.
 */
data class DeezerArtistSearchResponse(
    val data: List<DeezerArtist>,
)

data class DeezerArtist(
    val name: String,
    // Deezer sirve varios tamaños del mismo recorte cuadrado --
    // "xl" es el más grande disponible (~1000x1000).
    @SerializedName("picture_xl") val pictureXl: String?,
)

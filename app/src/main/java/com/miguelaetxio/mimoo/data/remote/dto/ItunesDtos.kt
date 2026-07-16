package com.miguelaetxio.mimoo.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Respuesta de la API de búsqueda de iTunes (H03, S011 -- fallback de
 * carátula cuando MusicBrainz/Cover Art Archive no tiene coincidencia,
 * petición explícita de Miguel Ángel: "falta descargar carátula
 * cuando no existe"). Verificado 2026-07-16 contra
 * performance-partners.apple.com/search-api -- API pública, sin clave
 * ni autenticación, pensada para promocionar contenido de la tienda,
 * uso razonable para carátulas de una biblioteca personal.
 */
data class ItunesSearchResponse(
    @SerializedName("results") val results: List<ItunesAlbumResult> = emptyList(),
)

data class ItunesAlbumResult(
    /**
     * Miniatura de 100x100 por defecto -- ver
     * `CoverArtRepository.upscaleItunesArtworkUrl()` para cómo se
     * pide una resolución mayor cambiando el propio string de la URL
     * (técnica documentada y de uso común, no hay parámetro de
     * consulta para elegir tamaño).
     */
    @SerializedName("artworkUrl100") val artworkUrl100: String? = null,
)

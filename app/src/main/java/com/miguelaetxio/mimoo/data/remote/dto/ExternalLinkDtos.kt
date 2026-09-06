package com.miguelaetxio.mimoo.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Result of resolving a pasted YouTube/YouTube Music link (PASO 6f,
 * H05) — mirrors the JSON shape returned by
 * link_resolver.resolve_youtube_link() in Python, parsed on the
 * Kotlin side with Gson rather than walking a raw Chaquopy PyObject.
 * ---
 * Resultado de resolver un enlace de YouTube/YouTube Music pegado
 * (PASO 6f, H05) — refleja la forma JSON que devuelve
 * link_resolver.resolve_youtube_link() en Python, parseada en el lado
 * Kotlin con Gson en vez de recorrer un PyObject de Chaquopy en
 * crudo.
 */
data class ExternalLinkResult(
    val title: String,
    val tracks: List<ExternalLinkTrack>,
    // S055 -- true solo cuando el enlace resuelto es un álbum OFICIAL
    // de YouTube Music (id de playlist autogenerado con prefijo
    // "OLAK5uy", ver _is_official_album() en link_resolver.py). false
    // para una playlist normal, aunque tenga varias pistas -- ver
    // ImportLinkViewModel.importSelected(), que ya no agrupa esas
    // pistas bajo un álbum falso con el título de la lista.
    @SerializedName("is_album") val isAlbum: Boolean = false,
)

data class ExternalLinkTrack(
    @SerializedName("youtube_id") val youtubeId: String,
    val title: String,
    @SerializedName("duration_seconds") val durationSeconds: Int,
    @SerializedName("channel_title") val channelTitle: String,
    @SerializedName("thumbnail_url") val thumbnailUrl: String?,
)

/**
 * Wraps the JSON returned by link_resolver.search_by_type() (H08
 * PARTE 1, S009) -- a playlist or channel found by free-text search,
 * not yet resolved into its individual tracks. Opening one (url)
 * reuses resolve_youtube_link()/ImportLinkScreen exactly like a
 * pasted link.
 * ---
 * Envuelve el JSON que devuelve link_resolver.search_by_type() (H08
 * PARTE 1, S009) -- una lista o canal encontrado por búsqueda de texto
 * libre, todavía sin resolver a sus pistas individuales. Abrir uno
 * (url) reutiliza resolve_youtube_link()/ImportLinkScreen exactamente
 * igual que un enlace pegado.
 */
data class SearchTypeResultsWrapper(
    val results: List<SearchTypeResult>,
)

data class SearchTypeResult(
    val id: String,
    val title: String,
    val url: String,
    val subtitle: String,
    @SerializedName("thumbnail_url") val thumbnailUrl: String?,
)

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
)

data class ExternalLinkTrack(
    @SerializedName("youtube_id") val youtubeId: String,
    val title: String,
    @SerializedName("duration_seconds") val durationSeconds: Int,
    @SerializedName("channel_title") val channelTitle: String,
    @SerializedName("thumbnail_url") val thumbnailUrl: String?,
)

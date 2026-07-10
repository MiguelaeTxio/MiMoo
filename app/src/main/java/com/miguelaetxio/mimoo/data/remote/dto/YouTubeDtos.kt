package com.miguelaetxio.mimoo.data.remote.dto

/**
 * A YouTube video, however it was found -- yt-dlp free-text search
 * (ExternalLinkResolver.searchYoutube) or a resolved pasted link
 * (ExternalLinkResolver.resolveLink). No longer tied to the YouTube
 * Data API response shape (removed S007, 2026-07-09 -- see
 * AlbumMatchRepository header for why).
 * ---
 * Un vídeo de YouTube, sea cual sea la forma en que se encontró --
 * búsqueda libre de yt-dlp (ExternalLinkResolver.searchYoutube) o un
 * enlace pegado resuelto (ExternalLinkResolver.resolveLink). Ya no
 * está ligado a la forma de respuesta de la YouTube Data API
 * (eliminada en S007, 2026-07-09 -- ver la cabecera de
 * AlbumMatchRepository para el motivo).
 */
data class TrackDto(
    val youtubeId: String,
    val title: String,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val channelTitle: String,
)

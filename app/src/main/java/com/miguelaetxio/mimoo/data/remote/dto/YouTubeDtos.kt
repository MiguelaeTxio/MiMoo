package com.miguelaetxio.mimoo.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTOs for YouTube Data API v3 responses.
 * ---
 * DTOs para las respuestas de la YouTube Data API v3.
 */

data class SearchListResponse(
    @SerializedName("items") val items: List<SearchItem> = emptyList(),
    @SerializedName("nextPageToken") val nextPageToken: String? = null,
)

data class SearchItem(
    @SerializedName("id") val id: SearchItemId,
    @SerializedName("snippet") val snippet: SearchItemSnippet,
)

data class SearchItemId(
    @SerializedName("videoId") val videoId: String? = null,
    @SerializedName("playlistId") val playlistId: String? = null,
)

data class SearchItemSnippet(
    @SerializedName("title") val title: String,
    @SerializedName("channelTitle") val channelTitle: String = "",
    @SerializedName("thumbnails") val thumbnails: Thumbnails? = null,
)

data class Thumbnails(
    @SerializedName("medium") val medium: ThumbnailItem? = null,
    @SerializedName("high") val high: ThumbnailItem? = null,
)

data class ThumbnailItem(
    @SerializedName("url") val url: String,
)

/**
 * playlistItems.list response — order of `items` already reflects
 * the real position of each video in the playlist (position field
 * confirms it explicitly). No `contentDetails.duration` here —
 * durations still require a separate videos.list batch call, same as
 * for a plain video search.
 * ---
 * Respuesta de playlistItems.list — el orden de `items` ya refleja la
 * posición real de cada vídeo en la playlist (el campo position lo
 * confirma explícitamente). No trae `contentDetails.duration` — las
 * duraciones siguen necesitando una llamada batch aparte a
 * videos.list, igual que en una búsqueda de vídeo normal.
 */
data class PlaylistItemsResponse(
    @SerializedName("items") val items: List<PlaylistItem> = emptyList(),
)

data class PlaylistItem(
    @SerializedName("snippet") val snippet: PlaylistItemSnippet,
)

data class PlaylistItemSnippet(
    @SerializedName("title") val title: String,
    @SerializedName("position") val position: Int,
    @SerializedName("channelTitle") val channelTitle: String = "",
    @SerializedName("thumbnails") val thumbnails: Thumbnails? = null,
    @SerializedName("resourceId") val resourceId: PlaylistItemResourceId,
)

data class PlaylistItemResourceId(
    @SerializedName("videoId") val videoId: String? = null,
)

data class VideoListResponse(
    @SerializedName("items") val items: List<VideoItem> = emptyList(),
)

data class VideoItem(
    @SerializedName("id") val id: String,
    @SerializedName("contentDetails") val contentDetails: ContentDetails,
)

data class ContentDetails(
    @SerializedName("duration") val duration: String,
)

/**
 * Aggregated DTO returned by YouTubeRepository.search().
 * ---
 * DTO agregado devuelto por YouTubeRepository.search().
 */
data class TrackDto(
    val youtubeId: String,
    val title: String,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val channelTitle: String,
)

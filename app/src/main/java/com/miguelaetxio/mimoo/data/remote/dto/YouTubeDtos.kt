package com.miguelaetxio.mimoo.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTOs for YouTube Data API v3 responses.
 * ---
 * DTOs para las respuestas de la YouTube Data API v3.
 */

data class PlaylistItemsResponse(
    @SerializedName("items") val items: List<PlaylistItem> = emptyList(),
    @SerializedName("nextPageToken") val nextPageToken: String? = null,
)

data class PlaylistItem(
    @SerializedName("snippet") val snippet: PlaylistItemSnippet,
)

data class PlaylistItemSnippet(
    @SerializedName("title") val title: String,
    @SerializedName("resourceId") val resourceId: ResourceId,
    @SerializedName("thumbnails") val thumbnails: Thumbnails? = null,
)

data class ResourceId(
    @SerializedName("videoId") val videoId: String,
)

data class Thumbnails(
    @SerializedName("medium") val medium: ThumbnailItem? = null,
    @SerializedName("high") val high: ThumbnailItem? = null,
)

data class ThumbnailItem(
    @SerializedName("url") val url: String,
)

data class VideoListResponse(
    @SerializedName("items") val items: List<VideoItem> = emptyList(),
)

data class VideoItem(
    @SerializedName("id") val id: String,
    @SerializedName("contentDetails") val contentDetails: ContentDetails,
    @SerializedName("snippet") val snippet: VideoSnippet? = null,
)

data class ContentDetails(
    @SerializedName("duration") val duration: String,
)

data class VideoSnippet(
    @SerializedName("channelTitle") val channelTitle: String = "",
)

/**
 * Aggregated DTO returned by YouTubeRepository.
 * ---
 * DTO agregado devuelto por YouTubeRepository.
 */
data class TrackDto(
    val youtubeId: String,
    val title: String,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val channelTitle: String,
)

package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Playback status of a search result that the user has chosen to
 * download for offline listening.
 * ---
 * Estado de descarga de un resultado de búsqueda que el usuario ha
 * elegido descargar para escucha offline.
 */
enum class DownloadStatus { PENDING, DOWNLOADING, DONE, ERROR }

/**
 * A single audio track resolved from a YouTube search result.
 * Unlike the previous Artist/Album/Track CRUD model, this entity is
 * never filled in by hand: every field is populated from YouTube
 * metadata at search time. artistName/albumName are free-text labels
 * taken from the video's channel title, not foreign keys to a manual
 * catalog.
 * ---
 * Una pista de audio resuelta a partir de un resultado de búsqueda de
 * YouTube. A diferencia del modelo CRUD anterior de Artist/Album/Track,
 * esta entidad nunca se rellena a mano: todos los campos proceden de
 * los metadatos de YouTube en el momento de la búsqueda. artistName
 * es una etiqueta de texto libre tomada del nombre del canal, no una
 * clave foránea a un catálogo manual.
 */
@Entity(tableName = "search_result_tracks")
data class SearchResultTrack(
    @PrimaryKey val youtubeId: String,  // 11-char YouTube video ID
    val title: String,
    val channelTitle: String,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val filePath: String? = null,       // local .opus path once downloaded
    val downloadStatus: DownloadStatus = DownloadStatus.PENDING,
    val lastSearchedAt: Long = System.currentTimeMillis(),
) {
    val youtubeUrl: String get() = "https://youtu.be/$youtubeId"
}

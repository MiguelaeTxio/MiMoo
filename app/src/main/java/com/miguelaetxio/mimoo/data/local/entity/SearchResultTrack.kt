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
 * metadata at search time. artist is a structured, queryable field
 * (added in Hito 03 PASO 2) used for storage foldering and Biblioteca
 * grouping/sorting; it starts as a copy of channelTitle but is
 * decoupled so a future manual edit (PASO 7) can diverge from it.
 * album stays null until the MusicBrainz milestone or a manual edit
 * exists. isFavorite (PASO 4) is a plain user toggle, independent of
 * downloadStatus — a track can be favorited whether or not it has
 * been downloaded yet.
 * ---
 * Una pista de audio resuelta a partir de un resultado de búsqueda de
 * YouTube. A diferencia del modelo CRUD anterior de Artist/Album/Track,
 * esta entidad nunca se rellena a mano: todos los campos proceden de
 * los metadatos de YouTube en el momento de la búsqueda. artist es un
 * campo estructurado y consultable (añadido en el Hito 03 PASO 2) que
 * se usa para la organización de carpetas y para agrupar/ordenar en
 * la Biblioteca; empieza como copia de channelTitle pero está
 * desacoplado para que una futura edición manual (PASO 7) pueda
 * divergir de él. album permanece null hasta que exista el hito de
 * MusicBrainz o una edición manual. isFavorite (PASO 4) es un simple
 * marcador del usuario, independiente de downloadStatus — una pista
 * puede marcarse como favorita se haya descargado o no.
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
    val artist: String? = null,         // structured artist, PASO 2 H03
    val album: String? = null,          // null until MusicBrainz/manual edit
    val isFavorite: Boolean = false,    // PASO 4 H03
) {
    val youtubeUrl: String get() = "https://youtu.be/$youtubeId"
}

package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DownloadStatus { PENDING, DOWNLOADING, DONE, ERROR }

@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = Artist::class,
            parentColumns = ["id"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Album::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("artistId"), Index("albumId")],
)
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val artistId: Long,
    val albumId: Long? = null,
    val title: String,
    val youtubeId: String,             // 11 caracteres del ID de video YT
    val durationSeconds: Int? = null,
    val filePath: String? = null,      // ruta local del .opus descargado
    val downloadStatus: DownloadStatus = DownloadStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val youtubeUrl: String get() = "https://youtu.be/$youtubeId"
}

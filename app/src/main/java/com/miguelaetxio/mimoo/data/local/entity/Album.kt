package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    foreignKeys = [
        ForeignKey(
            entity = Artist::class,
            parentColumns = ["id"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("artistId")],
)
data class Album(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val artistId: Long,
    val title: String,
    val mbid: String? = null,
    val year: Int? = null,
    val genres: String? = null,
    val coverUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

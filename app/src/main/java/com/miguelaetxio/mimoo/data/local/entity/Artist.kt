package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artists")
data class Artist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mbid: String? = null,
    val lastfmUrl: String? = null,
    val bio: String? = null,
    val genres: String? = null,       // tags separados por coma
    val coverUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

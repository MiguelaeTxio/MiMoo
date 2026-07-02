package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-created local playlist (Hito 04). Purely a name and creation
 * timestamp — track membership and order live in
 * PlaylistTrackCrossRef, not here.
 * ---
 * Una lista de reproducción local creada por el usuario (Hito 04).
 * Solo un nombre y una marca de tiempo de creación — la pertenencia y
 * el orden de las pistas viven en PlaylistTrackCrossRef, no aquí.
 */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

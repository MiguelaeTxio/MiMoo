package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * Marcador de playlist LOCAL propia como favorita -- petición
 * explícita de Miguel Ángel (sesión de diseño de Favoritos,
 * 2026-08-02): con muchas playlists creadas, poder señalar cuáles son
 * las favoritas para encontrarlas y ejecutarlas rápido. No es un
 * concepto nuevo de playlist ni cambia Playlist/PlaylistTrackCrossRef
 * en absoluto -- solo une un playlistId con "está marcada".
 *
 * onDelete = CASCADE, mismo criterio de diseño que
 * PlaylistTrackCrossRef: borrar la playlist no debe dejar un marcador
 * de favorito huérfano.
 * ---
 * Marker for a user's own LOCAL playlist as favorite -- explicit
 * request from Miguel Ángel (Favorites design session, 2026-08-02):
 * with many playlists created, being able to flag which ones are
 * favorites to find and run them quickly. Not a new playlist concept
 * and doesn't change Playlist/PlaylistTrackCrossRef at all -- just
 * links a playlistId to "is marked".
 *
 * onDelete = CASCADE, same design criterion as PlaylistTrackCrossRef:
 * deleting the playlist shouldn't leave an orphaned favorite marker.
 */
@Entity(
    tableName = "favorite_playlists",
    primaryKeys = ["playlistId"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FavoritePlaylist(
    val playlistId: Long,
    val addedAt: Long = System.currentTimeMillis(),
)

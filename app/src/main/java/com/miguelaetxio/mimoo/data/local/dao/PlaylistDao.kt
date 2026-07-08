package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.miguelaetxio.mimoo.data.local.entity.Playlist
import com.miguelaetxio.mimoo.data.local.entity.PlaylistTrackCrossRef
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import kotlinx.coroutines.flow.Flow

/**
 * DAO for playlists and their track membership/order (Hito 04).
 * ---
 * DAO para las playlists y la pertenencia/orden de sus pistas
 * (Hito 04).
 */
@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    /** Variante de una sola lectura de getAllPlaylists() -- ver SearchResultTrackDao.getAllOnce, H06 PASO 1. */
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getAllPlaylistsOnce(): List<Playlist>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    /**
     * Deleting the Playlist row cascades to
     * playlist_track_cross_refs via the foreign key (ON DELETE
     * CASCADE, see PlaylistTrackCrossRef) — no manual cleanup query
     * needed here.
     * ---
     * Borrar la fila Playlist cascada a playlist_track_cross_refs vía
     * la clave foránea (ON DELETE CASCADE, ver
     * PlaylistTrackCrossRef) — no hace falta ninguna consulta manual
     * de limpieza aquí.
     */
    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query(
        "SELECT MAX(position) FROM playlist_track_cross_refs " +
        "WHERE playlistId = :playlistId"
    )
    suspend fun getMaxPosition(playlistId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(crossRef: PlaylistTrackCrossRef)

    @Query(
        "DELETE FROM playlist_track_cross_refs " +
        "WHERE playlistId = :playlistId AND youtubeId = :youtubeId"
    )
    suspend fun removeTrackFromPlaylist(playlistId: Long, youtubeId: String)

    @Query(
        "UPDATE playlist_track_cross_refs SET position = :position " +
        "WHERE playlistId = :playlistId AND youtubeId = :youtubeId"
    )
    suspend fun updatePosition(
        playlistId: Long,
        youtubeId: String,
        position: Int,
    )

    /**
     * Tracks of one playlist, in saved order. Inner JOIN: if a
     * cross-ref's track row no longer exists this returns nothing for
     * it, but that state is unreachable in practice because the
     * foreign key's ON DELETE CASCADE already removes the cross-ref
     * the moment the track row is deleted.
     * ---
     * Pistas de una playlist, en el orden guardado. INNER JOIN: si la
     * fila de pista de una referencia ya no existe esto no devuelve
     * nada para ella, pero ese estado es inalcanzable en la práctica
     * porque el ON DELETE CASCADE de la clave foránea ya elimina la
     * referencia en el momento en que se borra la fila de la pista.
     */
    @Query(
        "SELECT t.* FROM search_result_tracks t " +
        "INNER JOIN playlist_track_cross_refs x ON t.youtubeId = x.youtubeId " +
        "WHERE x.playlistId = :playlistId " +
        "ORDER BY x.position ASC"
    )
    fun getTracksForPlaylist(playlistId: Long): Flow<List<SearchResultTrack>>

    /** Variante de una sola lectura de getTracksForPlaylist() -- ver SearchResultTrackDao.getAllOnce, H06 PASO 1. */
    @Query(
        "SELECT t.* FROM search_result_tracks t " +
        "INNER JOIN playlist_track_cross_refs x ON t.youtubeId = x.youtubeId " +
        "WHERE x.playlistId = :playlistId " +
        "ORDER BY x.position ASC"
    )
    suspend fun getTracksForPlaylistOnce(playlistId: Long): List<SearchResultTrack>

    @Query(
        "SELECT COUNT(*) FROM playlist_track_cross_refs " +
        "WHERE playlistId = :playlistId"
    )
    fun getTrackCountForPlaylist(playlistId: Long): Flow<Int>
}

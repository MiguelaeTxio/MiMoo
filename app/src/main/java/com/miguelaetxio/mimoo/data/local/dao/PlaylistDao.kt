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

    /**
     * Borra TODAS las playlists -- cascada automática a
     * playlist_track_cross_refs vía FK (mismo comentario de arriba).
     * Solo para la importación destructiva de H06 PASO 4.
     */
    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    /**
     * Borra TODAS las referencias playlist-pista de forma explícita.
     * Redundante con la cascada de deleteAllPlaylists() (y con la
     * cascada del lado search_result_tracks), pero se llama primero
     * de todas formas en H06 PASO 4 para que la transacción sea
     * explícita y no dependa implícitamente de cascada -- ver
     * ANNEX_H06.md.
     */
    @Query("DELETE FROM playlist_track_cross_refs")
    suspend fun deleteAllCrossRefs()

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

    /**
     * Todas las referencias de playlist que apuntan a una pista dada,
     * en cualquier lista de reproducción. Usado por
     * TrackAlternativeRepository.replaceFailedTrackSource() (fix real,
     * 2026-07-24): antes de borrar la fila vieja (que arrastraría
     * estas referencias por ON DELETE CASCADE), hay que capturarlas
     * para poder recrearlas con el youtubeId nuevo -- si no, sustituir
     * la fuente de una pista fallida la sacaría en silencio de
     * cualquier playlist en la que estuviera.
     * ---
     * All playlist cross-refs pointing at a given track, across any
     * playlist. Used by
     * TrackAlternativeRepository.replaceFailedTrackSource() (real fix,
     * 2026-07-24): before deleting the old row (which would drag these
     * refs away via ON DELETE CASCADE), they need to be captured so
     * they can be recreated with the new youtubeId -- otherwise
     * replacing a failed track's source would silently drop it from
     * any playlist it was in.
     */
    @Query("SELECT * FROM playlist_track_cross_refs WHERE youtubeId = :youtubeId")
    suspend fun getCrossRefsForTrack(youtubeId: String): List<PlaylistTrackCrossRef>

    /**
     * S057 -- petición explícita de Miguel Ángel: "Listas se quedan sin
     * carátula! O meter una o la carátula por defecto." Devuelve la
     * carátula (o miniatura de YouTube) del PRIMER tema de cada lista
     * (posición mínima), una fila por playlist -- usado en
     * PlaylistsScreen para mostrar algo más que un icono genérico.
     * `MIN(x.position)` acotado por playlistId vía la subconsulta
     * correlacionada -- Room no tiene una forma más directa de
     * "primera fila de cada grupo" en SQLite sin ventanas.
     * ---
     * S057 -- explicit request from Miguel Ángel: "Playlists are left
     * without a cover! Either add one or a default cover." Returns the
     * cover art (or YouTube thumbnail) of the FIRST track of each
     * playlist (lowest position), one row per playlist -- used in
     * PlaylistsScreen to show more than a generic icon. `MIN(x.position)`
     * scoped by playlistId via the correlated subquery -- Room has no
     * more direct way to express "first row of each group" in SQLite
     * without window functions.
     */
    @Query(
        "SELECT x.playlistId AS playlistId, t.coverArtUrl AS coverArtUrl, " +
        "t.thumbnailUrl AS thumbnailUrl " +
        "FROM playlist_track_cross_refs x " +
        "INNER JOIN search_result_tracks t ON t.youtubeId = x.youtubeId " +
        "WHERE x.position = (" +
        "  SELECT MIN(x2.position) FROM playlist_track_cross_refs x2 " +
        "  WHERE x2.playlistId = x.playlistId" +
        ")"
    )
    fun getFirstTrackCoverArtPerPlaylist(): Flow<List<PlaylistCoverArt>>
}

/** S057 -- ver getFirstTrackCoverArtPerPlaylist(). */
data class PlaylistCoverArt(
    val playlistId: Long,
    val coverArtUrl: String?,
    val thumbnailUrl: String?,
)

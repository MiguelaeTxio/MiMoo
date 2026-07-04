package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.PlaylistDao
import com.miguelaetxio.mimoo.data.local.entity.Playlist
import com.miguelaetxio.mimoo.data.local.entity.PlaylistTrackCrossRef
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for playlists and their track membership/order
 * (Hito 04). Thin wrapper over PlaylistDao, same pattern as
 * SearchResultTrackRepository.
 * ---
 * Repositorio de playlists y la pertenencia/orden de sus pistas
 * (Hito 04). Envoltorio fino sobre PlaylistDao, mismo patrón que
 * SearchResultTrackRepository.
 */
@Singleton
class PlaylistRepository @Inject constructor(
    private val dao: PlaylistDao,
) {
    fun getAllPlaylists(): Flow<List<Playlist>> = dao.getAllPlaylists()

    suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(Playlist(name = name))

    suspend fun renamePlaylist(playlistId: Long, name: String) =
        dao.renamePlaylist(playlistId, name)

    suspend fun deletePlaylist(playlistId: Long) =
        dao.deletePlaylist(playlistId)

    fun getTracksForPlaylist(playlistId: Long): Flow<List<SearchResultTrack>> =
        dao.getTracksForPlaylist(playlistId)

    fun getTrackCountForPlaylist(playlistId: Long): Flow<Int> =
        dao.getTrackCountForPlaylist(playlistId)

    /**
     * Appends a track at the end of the playlist (max position + 1,
     * or 0 for the first track).
     * ---
     * Añade una pista al final de la playlist (posición máxima + 1, o
     * 0 para la primera pista).
     */
    suspend fun addTrackToPlaylist(playlistId: Long, youtubeId: String) {
        val nextPosition = (dao.getMaxPosition(playlistId) ?: -1) + 1
        dao.addTrackToPlaylist(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                youtubeId = youtubeId,
                position = nextPosition,
            )
        )
    }

    /**
     * Añade varias pistas de golpe, en el orden dado -- petición
     * explícita de Miguel Ángel (2026-07-04): poder añadir un álbum
     * entero a una lista de reproducción, no solo pista a pista.
     * Reutiliza addTrackToPlaylist() en bucle: cada llamada añade al
     * final, así que el orden del álbum se conserva en la lista.
     * ---
     * Adds several tracks at once, in the given order -- explicit
     * request from Miguel Ángel (2026-07-04): being able to add a
     * whole album to a playlist, not just track by track. Reuses
     * addTrackToPlaylist() in a loop: each call appends at the end, so
     * the album's order is preserved in the playlist.
     */
    suspend fun addTracksToPlaylist(playlistId: Long, youtubeIds: List<String>) {
        youtubeIds.forEach { youtubeId ->
            addTrackToPlaylist(playlistId, youtubeId)
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, youtubeId: String) =
        dao.removeTrackFromPlaylist(playlistId, youtubeId)

    suspend fun updatePosition(playlistId: Long, youtubeId: String, position: Int) =
        dao.updatePosition(playlistId, youtubeId, position)
}

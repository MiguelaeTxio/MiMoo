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

    suspend fun removeTrackFromPlaylist(playlistId: Long, youtubeId: String) =
        dao.removeTrackFromPlaylist(playlistId, youtubeId)

    suspend fun updatePosition(playlistId: Long, youtubeId: String, position: Int) =
        dao.updatePosition(playlistId, youtubeId, position)
}

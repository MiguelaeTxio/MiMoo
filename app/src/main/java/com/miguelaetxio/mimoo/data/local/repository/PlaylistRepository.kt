package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.PlaylistDao
import com.miguelaetxio.mimoo.data.local.entity.Playlist
import com.miguelaetxio.mimoo.data.local.entity.PlaylistTrack
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(private val dao: PlaylistDao) {

    fun getAll(): Flow<List<Playlist>> = dao.getAll()

    suspend fun getById(id: Long): Playlist? = dao.getById(id)

    fun getTracksForPlaylist(playlistId: Long): Flow<List<PlaylistTrack>> =
        dao.getTracksForPlaylist(playlistId)

    suspend fun save(playlist: Playlist): Long =
        if (playlist.id == 0L) dao.insert(playlist)
        else { dao.update(playlist.copy(updatedAt = System.currentTimeMillis())); playlist.id }

    suspend fun delete(playlist: Playlist) = dao.delete(playlist)

    suspend fun addTrack(playlistTrack: PlaylistTrack) = dao.insertTrack(playlistTrack)

    suspend fun removeTrack(playlistTrack: PlaylistTrack) = dao.removeTrack(playlistTrack)
}

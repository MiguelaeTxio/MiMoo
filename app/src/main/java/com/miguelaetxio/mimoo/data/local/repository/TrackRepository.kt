package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.TrackDao
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.Track
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepository @Inject constructor(private val dao: TrackDao) {

    fun getAll(): Flow<List<Track>> = dao.getAll()

    fun getByArtist(artistId: Long): Flow<List<Track>> = dao.getByArtist(artistId)

    fun getByAlbum(albumId: Long): Flow<List<Track>> = dao.getByAlbum(albumId)

    fun getByStatus(status: DownloadStatus): Flow<List<Track>> = dao.getByStatus(status)

    suspend fun getById(id: Long): Track? = dao.getById(id)

    suspend fun save(track: Track): Long =
        if (track.id == 0L) dao.insert(track)
        else { dao.update(track.copy(updatedAt = System.currentTimeMillis())); track.id }

    suspend fun delete(track: Track) = dao.delete(track)
}

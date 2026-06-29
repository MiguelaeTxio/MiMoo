package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.AlbumDao
import com.miguelaetxio.mimoo.data.local.entity.Album
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepository @Inject constructor(private val dao: AlbumDao) {

    fun getAll(): Flow<List<Album>> = dao.getAll()

    fun getByArtist(artistId: Long): Flow<List<Album>> = dao.getByArtist(artistId)

    suspend fun getById(id: Long): Album? = dao.getById(id)

    suspend fun save(album: Album): Long =
        if (album.id == 0L) dao.insert(album)
        else { dao.update(album.copy(updatedAt = System.currentTimeMillis())); album.id }

    suspend fun delete(album: Album) = dao.delete(album)
}

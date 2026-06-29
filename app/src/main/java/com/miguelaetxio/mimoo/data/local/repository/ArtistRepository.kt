package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.ArtistDao
import com.miguelaetxio.mimoo.data.local.entity.Artist
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistRepository @Inject constructor(private val dao: ArtistDao) {

    fun getAll(): Flow<List<Artist>> = dao.getAll()

    suspend fun getById(id: Long): Artist? = dao.getById(id)

    suspend fun save(artist: Artist): Long =
        if (artist.id == 0L) dao.insert(artist)
        else { dao.update(artist.copy(updatedAt = System.currentTimeMillis())); artist.id }

    suspend fun delete(artist: Artist) = dao.delete(artist)
}

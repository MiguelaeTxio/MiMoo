package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.FavoriteAlbumDao
import com.miguelaetxio.mimoo.data.local.entity.FavoriteAlbum
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Favoritos a nivel de ÁLBUM -- petición explícita de Miguel Ángel
 * (2026-07-05), concepto separado del favorito por pista
 * (SearchResultTrackRepository.updateFavorite()).
 * ---
 * ALBUM-level favorites -- explicit request from Miguel Ángel
 * (2026-07-05), a separate concept from the per-track favorite
 * (SearchResultTrackRepository.updateFavorite()).
 */
@Singleton
class FavoriteAlbumRepository @Inject constructor(
    private val dao: FavoriteAlbumDao,
) {
    fun getAll(): Flow<List<FavoriteAlbum>> = dao.getAll()

    suspend fun toggle(artist: String, album: String) {
        if (dao.isFavorite(artist, album)) {
            dao.delete(FavoriteAlbum(artist, album))
        } else {
            dao.insert(FavoriteAlbum(artist, album))
        }
    }
}

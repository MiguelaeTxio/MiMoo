package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.FavoriteArtistDao
import com.miguelaetxio.mimoo.data.local.entity.FavoriteArtist
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Favoritos a nivel de ARTISTA -- H12, concepto separado del favorito
 * de álbum (FavoriteAlbumRepository) y del favorito por pista
 * (SearchResultTrackRepository.updateFavorite()). Independiente de
 * si hay algo descargado de ese artista.
 * ---
 * ARTIST-level favorites -- H12, a concept separate from album
 * favorites (FavoriteAlbumRepository) and track favorites
 * (SearchResultTrackRepository.updateFavorite()). Independent of
 * whether anything from that artist is downloaded.
 */
@Singleton
class FavoriteArtistRepository @Inject constructor(
    private val dao: FavoriteArtistDao,
) {
    fun getAll(): Flow<List<FavoriteArtist>> = dao.getAll()

    suspend fun toggle(artist: String) {
        if (dao.isFavorite(artist)) {
            dao.delete(FavoriteArtist(artist))
        } else {
            dao.insert(FavoriteArtist(artist))
        }
    }

    suspend fun isFavorite(artist: String): Boolean = dao.isFavorite(artist)
}

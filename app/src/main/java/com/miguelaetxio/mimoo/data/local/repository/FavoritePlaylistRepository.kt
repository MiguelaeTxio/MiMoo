package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.FavoritePlaylistDao
import com.miguelaetxio.mimoo.data.local.entity.FavoritePlaylist
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Marcador de PLAYLIST propia favorita -- sesión de diseño de
 * Favoritos (2026-08-02). Ver comentario de la entidad
 * FavoritePlaylist.
 * ---
 * Marker for a favorite own PLAYLIST -- Favorites design session
 * (2026-08-02). See the FavoritePlaylist entity's comment.
 */
@Singleton
class FavoritePlaylistRepository @Inject constructor(
    private val dao: FavoritePlaylistDao,
) {
    fun getAll(): Flow<List<FavoritePlaylist>> = dao.getAll()

    suspend fun isFavorite(playlistId: Long): Boolean = dao.isFavorite(playlistId)

    suspend fun toggle(playlistId: Long) {
        if (dao.isFavorite(playlistId)) {
            dao.deleteById(playlistId)
        } else {
            dao.insert(FavoritePlaylist(playlistId))
        }
    }
}

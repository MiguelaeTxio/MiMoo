package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.FavoriteTrackDao
import com.miguelaetxio.mimoo.data.local.entity.FavoriteTrack
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Favoritos de SENCILLO en streaming -- sesión de diseño de Favoritos
 * (2026-08-02), concepto separado del favorito por pista ya
 * descargada (SearchResultTrackRepository.updateFavorite()). Ver
 * comentario de la entidad FavoriteTrack.
 * ---
 * STREAMING single-track favorites -- Favorites design session
 * (2026-08-02), a separate concept from the already-downloaded
 * per-track favorite (SearchResultTrackRepository.updateFavorite()).
 * See the FavoriteTrack entity's comment.
 */
@Singleton
class FavoriteTrackRepository @Inject constructor(
    private val dao: FavoriteTrackDao,
) {
    fun getAll(): Flow<List<FavoriteTrack>> = dao.getAll()

    suspend fun isFavorite(youtubeId: String): Boolean = dao.isFavorite(youtubeId)

    suspend fun toggle(track: FavoriteTrack) {
        if (dao.isFavorite(track.youtubeId)) {
            dao.deleteById(track.youtubeId)
        } else {
            dao.insert(track)
        }
    }
}

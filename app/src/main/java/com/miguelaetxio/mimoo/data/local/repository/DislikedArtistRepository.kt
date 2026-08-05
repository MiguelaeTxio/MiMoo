package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.DislikedArtistDao
import com.miguelaetxio.mimoo.data.local.entity.DislikedArtist
import com.miguelaetxio.mimoo.util.SearchNormalizer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "No me gusta" a nivel de ARTISTA -- H16. La mutación (añadir/borrar)
 * pasa siempre por AutoSyncPusher.executeIfConnected() en el
 * ViewModel que llama a este repositorio, nunca aquí directamente --
 * mismo patrón obligatorio que FavoriteArtistRepository (regla de
 * negocio H07 PARTE 1).
 * ---
 * ARTIST-level "dislike" -- H16. The mutation (add/remove) always goes
 * through AutoSyncPusher.executeIfConnected() in the calling
 * ViewModel, never here directly -- same mandatory pattern as
 * FavoriteArtistRepository (H07 PART 1 business rule).
 */
@Singleton
class DislikedArtistRepository @Inject constructor(
    private val dao: DislikedArtistDao,
) {
    fun getAll(): Flow<List<DislikedArtist>> = dao.getAll()

    suspend fun add(artist: String) {
        dao.insert(DislikedArtist(artist = artist, dislikedAt = System.currentTimeMillis()))
    }

    suspend fun remove(artist: String) {
        dao.getAllOnce().firstOrNull { it.artist == artist }?.let { dao.delete(it) }
    }

    suspend fun isDisliked(artist: String): Boolean = dao.isDisliked(artist)

    /**
     * Conjunto de claves normalizadas (SearchNormalizer.normalizeArtistName()),
     * para comprobación en memoria O(1) desde RadioRepository/PopurriRepository
     * en vez de una consulta SQL por candidato evaluado.
     * ---
     * Set of normalized keys (SearchNormalizer.normalizeArtistName()),
     * for O(1) in-memory checks from RadioRepository/PopurriRepository
     * instead of one SQL query per evaluated candidate.
     */
    suspend fun normalizedKeysSnapshot(): Set<String> =
        dao.getAllOnce().map { SearchNormalizer.normalizeArtistName(it.artist) }.toSet()
}

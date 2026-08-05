package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.DislikedTrackDao
import com.miguelaetxio.mimoo.data.local.entity.DislikedTrack
import com.miguelaetxio.mimoo.util.SearchNormalizer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "No me gusta" a nivel de TEMA -- H16. Mismo patrón obligatorio que
 * DislikedArtistRepository/FavoriteArtistRepository: la mutación pasa
 * siempre por AutoSyncPusher.executeIfConnected() en el ViewModel que
 * llama, nunca aquí directamente.
 * ---
 * TRACK-level "dislike" -- H16. Same mandatory pattern as
 * DislikedArtistRepository/FavoriteArtistRepository: the mutation
 * always goes through AutoSyncPusher.executeIfConnected() in the
 * calling ViewModel, never here directly.
 */
@Singleton
class DislikedTrackRepository @Inject constructor(
    private val dao: DislikedTrackDao,
) {
    fun getAll(): Flow<List<DislikedTrack>> = dao.getAll()

    suspend fun add(artist: String, title: String) {
        dao.insert(DislikedTrack(artist = artist, title = title, dislikedAt = System.currentTimeMillis()))
    }

    suspend fun remove(artist: String, title: String) {
        dao.getAllOnce().firstOrNull { it.artist == artist && it.title == title }?.let { dao.delete(it) }
    }

    suspend fun isDisliked(artist: String, title: String): Boolean = dao.isDisliked(artist, title)

    /**
     * Conjunto de claves normalizadas ("artistKey|titleKey", vía
     * SearchNormalizer.normalizeArtistName() + songTitleKey()), para
     * comprobación en memoria O(1) desde RadioRepository/PopurriRepository
     * -- mismo criterio que DislikedArtistRepository.normalizedKeysSnapshot().
     * songTitleKey() ya colapsa versiones distintas del mismo tema
     * (directo/remasterizado/estudio) a la misma clave -- ver su kdoc.
     * ---
     * Set of normalized keys ("artistKey|titleKey", via
     * SearchNormalizer.normalizeArtistName() + songTitleKey()), for
     * O(1) in-memory checks from RadioRepository/PopurriRepository --
     * same criterion as DislikedArtistRepository.normalizedKeysSnapshot().
     * songTitleKey() already collapses different versions of the same
     * song (live/remastered/studio) into the same key -- see its kdoc.
     */
    suspend fun normalizedKeysSnapshot(): Set<String> =
        dao.getAllOnce().map { key(it.artist, it.title) }.toSet()

    companion object {
        /** Misma forma de clave usada en normalizedKeysSnapshot() y en el punto de consulta del candidato. */
        fun key(artist: String, title: String): String =
            SearchNormalizer.normalizeArtistName(artist) + "|" + SearchNormalizer.songTitleKey(title, artist)
    }
}

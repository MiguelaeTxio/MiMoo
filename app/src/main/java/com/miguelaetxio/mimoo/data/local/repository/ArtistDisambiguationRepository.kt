package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.ArtistDisambiguationDao
import com.miguelaetxio.mimoo.data.local.entity.ArtistDisambiguation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistencia de la elección de MBID cuando dos artistas distintos
 * comparten el mismo nombre normalizado (H12). Ver comentario de la
 * entidad ArtistDisambiguation para la distinción frente a
 * normalizeArtistName().
 * ---
 * Persistence of the MBID choice when two distinct artists share the
 * same normalized name (H12). See the ArtistDisambiguation entity's
 * comment for the distinction from normalizeArtistName().
 */
@Singleton
class ArtistDisambiguationRepository @Inject constructor(
    private val dao: ArtistDisambiguationDao,
) {
    suspend fun getChoice(normalizedNameKey: String): ArtistDisambiguation? =
        dao.getChoice(normalizedNameKey)

    suspend fun saveChoice(normalizedNameKey: String, chosenMbid: String) {
        dao.insert(ArtistDisambiguation(normalizedNameKey, chosenMbid))
    }
}

package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.local.dao.ArtistImageDao
import com.miguelaetxio.mimoo.data.local.entity.ArtistImage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S059 -- petición explícita de Miguel Ángel: "¿podemos sacar la
 * imagen de los artistas de algún sitio?", tras ver el avatar
 * genérico (icono de persona) que se añadió en la vista de artistas de
 * Biblioteca. Ni MusicBrainz ni Cover Art Archive tienen fotos de
 * artista (solo carátulas de álbum) -- se usa Deezer, cuya API pública
 * de búsqueda de artistas no exige clave ni autenticación, mismo
 * espíritu que el fallback de iTunes de CoverArtRepository.
 *
 * Caché permanente en Room (`artist_images`, ver la entidad
 * ArtistImage) -- una vez resuelto (con foto o sin ella), no se vuelve
 * a preguntar a Deezer para ese artista. Sin caché de sesión aparte
 * (a diferencia de CoverArtRepository): aquí Room ya puede guardar
 * `null` sin problema (no hace falta el centinela NO_MATCH que exigía
 * ConcurrentHashMap), así que la fila de Room hace las dos veces de
 * caché.
 * ---
 * S059 -- explicit request from Miguel Ángel: "can we get artist
 * images from somewhere?", after seeing the generic avatar (person
 * icon) added to Biblioteca's artist view. Neither MusicBrainz nor
 * Cover Art Archive have artist photos (only album covers) -- Deezer
 * is used instead, whose public artist-search API needs no key or
 * authentication, same spirit as CoverArtRepository's iTunes fallback.
 *
 * Permanent cache in Room (`artist_images`, see the ArtistImage
 * entity) -- once resolved (with or without a photo), Deezer isn't
 * asked again for that artist. No separate session cache (unlike
 * CoverArtRepository): here Room can already store `null` just fine
 * (no need for the NO_MATCH sentinel ConcurrentHashMap required), so
 * the Room row alone serves as the cache.
 */
@Singleton
class ArtistImageRepository @Inject constructor(
    private val deezerApiService: DeezerApiService,
    private val artistImageDao: ArtistImageDao,
) {
    /**
     * Returns the artist's photo URL, or null if Deezer has no artist
     * under that name. Never throws — any network or parsing failure
     * is treated the same as "no match found" and cached as such, so
     * a broken artist name doesn't retry against the network forever.
     * ---
     * Devuelve la URL de la foto del artista, o null si Deezer no
     * tiene ningún artista con ese nombre. Nunca lanza excepción --
     * cualquier fallo de red o de parseo se trata igual que "sin
     * coincidencia" y se cachea como tal, para que un nombre de
     * artista roto no reintente contra la red para siempre.
     */
    suspend fun resolveArtistImageUrl(artist: String): String? {
        artistImageDao.getByArtist(artist)?.let { cached -> return cached.imageUrl }

        val imageUrl = try {
            deezerApiService.searchArtists(query = artist).data.firstOrNull()?.pictureXl
        } catch (e: Exception) {
            null
        }
        artistImageDao.insert(ArtistImage(artist = artist, imageUrl = imageUrl))
        return imageUrl
    }
}

package com.miguelaetxio.mimoo.data.favorites

import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.FavoriteAlbum
import com.miguelaetxio.mimoo.data.local.entity.FavoriteArtist
import com.miguelaetxio.mimoo.data.local.entity.FavoritePlaylist
import com.miguelaetxio.mimoo.data.local.entity.FavoriteTrack
import com.miguelaetxio.mimoo.data.local.entity.Playlist
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.FavoriteAlbumRepository
import com.miguelaetxio.mimoo.data.local.repository.FavoriteArtistRepository
import com.miguelaetxio.mimoo.data.local.repository.FavoritePlaylistRepository
import com.miguelaetxio.mimoo.data.local.repository.FavoriteTrackRepository
import com.miguelaetxio.mimoo.data.local.repository.PlaylistRepository
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Una playlist propia junto a si está marcada como favorita -- para
 * pintar la pestaña "Listas de reproducción" de Favoritos sin que la
 * pantalla tenga que cruzar dos flujos ella misma.
 * ---
 * A user's own playlist alongside whether it's marked favorite -- so
 * the Favorites screen's "Playlists" tab doesn't have to cross-
 * reference two flows itself.
 */
data class FavoritePlaylistRow(val playlist: Playlist, val isFavorite: Boolean)

/**
 * Un sencillo favorito unificado, sea cual sea la tabla de origen
 * (favorito en streaming, favorito ya descargado, o ambas a la vez) --
 * ver comentario de la entidad FavoriteTrack.
 * ---
 * A unified favorite single, whatever the source table (streaming
 * favorite, already-downloaded favorite, or both at once) -- see the
 * FavoriteTrack entity's comment.
 */
data class FavoriteTrackRow(
    val youtubeId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val isDownloaded: Boolean,
)

/**
 * Agrega las cuatro fuentes de favoritos (artistas, álbumes,
 * sencillos, playlists) para la pantalla unificada de Favoritos --
 * sesión de diseño de Favoritos (2026-08-02). No introduce ningún
 * concepto nuevo de almacenamiento: solo combina los repositorios ya
 * existentes.
 * ---
 * Aggregates the four favorites sources (artists, albums, singles,
 * playlists) for the unified Favorites screen -- Favorites design
 * session (2026-08-02). Introduces no new storage concept: just
 * combines the already-existing repositories.
 */
@Singleton
class FavoritesRepository @Inject constructor(
    private val favoriteArtistRepository: FavoriteArtistRepository,
    private val favoriteAlbumRepository: FavoriteAlbumRepository,
    private val favoriteTrackRepository: FavoriteTrackRepository,
    private val favoritePlaylistRepository: FavoritePlaylistRepository,
    private val playlistRepository: PlaylistRepository,
    private val searchResultTrackRepository: SearchResultTrackRepository,
) {
    fun getFavoriteArtists(): Flow<List<FavoriteArtist>> = favoriteArtistRepository.getAll()

    fun getFavoriteAlbums(): Flow<List<FavoriteAlbum>> = favoriteAlbumRepository.getAll()

    /**
     * Une favorite_tracks (streaming) con los sencillos descargados
     * marcados isFavorite=true -- un mismo youtubeId puede estar en
     * las dos, se deduplica por youtubeId (favorito en streaming
     * NUNCA se fusiona ni se migra, ver comentario de la entidad
     * FavoriteTrack, pero en la pantalla se muestra una sola fila).
     * ---
     * Merges favorite_tracks (streaming) with downloaded singles
     * marked isFavorite=true -- the same youtubeId can be in both,
     * deduplicated by youtubeId (the streaming favorite is NEVER
     * merged or migrated, see the FavoriteTrack entity's comment, but
     * the screen shows a single row).
     */
    fun getFavoriteTracks(): Flow<List<FavoriteTrackRow>> =
        combine(
            favoriteTrackRepository.getAll(),
            searchResultTrackRepository.getFavorites(),
        ) { streamingFavorites, downloadedFavorites ->
            val rows = LinkedHashMap<String, FavoriteTrackRow>()
            for (track in downloadedFavorites) {
                rows[track.youtubeId] = FavoriteTrackRow(
                    youtubeId = track.youtubeId,
                    title = track.title,
                    artist = track.artist ?: track.channelTitle,
                    thumbnailUrl = track.thumbnailUrl,
                    isDownloaded = true,
                )
            }
            for (track in streamingFavorites) {
                if (rows.containsKey(track.youtubeId)) continue
                rows[track.youtubeId] = FavoriteTrackRow(
                    youtubeId = track.youtubeId,
                    title = track.title,
                    artist = track.artist,
                    thumbnailUrl = track.thumbnailUrl,
                    isDownloaded = false,
                )
            }
            rows.values.toList()
        }

    /** Solo las filas locales, para pasar a PopurriRepository.buildFromFavoriteTracks() sin volver a consultar. */
    fun getFavoriteLocalTracks(): Flow<List<SearchResultTrack>> = searchResultTrackRepository.getFavorites()

    fun getFavoritePlaylists(): Flow<List<FavoritePlaylistRow>> =
        combine(
            playlistRepository.getAllPlaylists(),
            favoritePlaylistRepository.getAll(),
        ) { playlists, favorites ->
            val favoriteIds = favorites.map { it.playlistId }.toSet()
            playlists
                .filter { it.id in favoriteIds }
                .map { FavoritePlaylistRow(it, isFavorite = true) }
        }

    suspend fun toggleArtist(artist: String) = favoriteArtistRepository.toggle(artist)

    suspend fun toggleAlbum(artist: String, album: String) = favoriteAlbumRepository.toggle(artist, album)

    suspend fun toggleTrack(track: FavoriteTrack) = favoriteTrackRepository.toggle(track)

    suspend fun togglePlaylist(playlistId: Long) = favoritePlaylistRepository.toggle(playlistId)
}

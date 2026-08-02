package com.miguelaetxio.mimoo.data.favorites

import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.FavoriteAlbum
import com.miguelaetxio.mimoo.data.local.entity.FavoriteArtist
import com.miguelaetxio.mimoo.data.local.entity.FavoriteTrack
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.QueueItem
import com.miguelaetxio.mimoo.data.playback.StreamResolver
import com.miguelaetxio.mimoo.data.remote.AlbumMatchRepository
import com.miguelaetxio.mimoo.data.remote.ArtistDirectoryRepository
import com.miguelaetxio.mimoo.data.remote.ArtistResolution
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzReleaseGroup
import com.miguelaetxio.mimoo.util.SearchNormalizer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Genera colas de reproducción efímeras ("popurrís") a partir de una
 * selección de favoritos -- sesión de diseño de Favoritos
 * (2026-08-02). Decisiones cerradas explícitamente con Miguel Ángel:
 *
 * - Cola EFÍMERA, nunca se guarda como playlist (se reproduce y
 *   desaparece al terminar, "son fáciles de crear").
 * - Tope de 100 pistas SIN REPETIR (deduplicación por artista+título
 *   normalizados).
 * - Streaming siempre; si una pista coincide exactamente con una fila
 *   local ya descargada, se reproduce desde ahí (barato: el cruce por
 *   nombre normalizado ya existe en el resto de la app, no añade
 *   complejidad real).
 * - Reparto POR TURNOS entre los artistas/álbumes elegidos (una tanda
 *   -- un álbum entero -- de cada uno por ronda, redistribuyendo
 *   cuando uno se agota): ningún artista con catálogo enorme (Rolling
 *   Stones) domina el popurrí solo por tener más discografía que otro
 *   (Presuntos Implicados). Esto TAMBIÉN resuelve el coste: solo se
 *   resuelve contra MusicBrainz+YouTube el álbum que hace falta en
 *   cada ronda, nunca la discografía entera de golpe -- el tope de 100
 *   acota cuántos álbumes se llegan a tocar.
 * - Nunca se mezcla el modo "artistas" con el modo "álbumes" en la
 *   misma tanda -- son dos flujos separados, tal como se seleccionan
 *   en la pantalla de Favoritos.
 * ---
 * Builds ephemeral playback queues ("popurrís") from a favorites
 * selection -- Favorites design session (2026-08-02). Decisions
 * explicitly closed with Miguel Ángel:
 *
 * - EPHEMERAL queue, never saved as a playlist (plays and disappears
 *   when it ends, "they're easy to create").
 * - Cap of 100 tracks, NO REPEATS (dedup by normalized artist+title).
 * - Always streaming; if a track exactly matches an already-downloaded
 *   local row, it plays from there (cheap: the normalized-name
 *   cross-reference already exists elsewhere in the app, adds no real
 *   complexity).
 * - ROUND-ROBIN distribution across the chosen artists/albums (one
 *   whole album's worth from each per round, redistributing once one
 *   runs out): no artist with a huge catalog (Rolling Stones) can
 *   dominate the popurrí just by having more discography than another
 *   (Presuntos Implicados). This ALSO solves the cost problem: only
 *   the album actually needed for that round gets resolved against
 *   MusicBrainz+YouTube, never a whole discography at once -- the cap
 *   of 100 bounds how many albums ever get touched.
 * - "Artists" mode and "albums" mode are never mixed in the same
 *   batch -- two separate flows, as selected on the Favorites screen.
 */
@Singleton
class PopurriRepository @Inject constructor(
    private val albumMatchRepository: AlbumMatchRepository,
    private val artistDirectoryRepository: ArtistDirectoryRepository,
    private val searchResultTrackRepository: SearchResultTrackRepository,
    private val streamResolver: StreamResolver,
) {
    companion object {
        const val TRACK_CAP = 100

        /** Resoluciones de stream en paralelo por tanda -- no lanzar las 100 a la vez contra yt-dlp. */
        private const val RESOLVE_CONCURRENCY = 6
    }

    /** Una pista ya emparejada a MusicBrainz+YouTube, todavía sin decidir local/streaming. */
    private data class PendingTrack(
        val artist: String,
        val album: String,
        val title: String,
        val youtubeId: String,
        val position: Int,
    )

    /** Una unidad de trabajo por ronda del reparto por turnos -- resolverla cuesta una llamada de red real. */
    private sealed class AlbumUnit {
        data class ReleaseGroupUnit(val artist: String, val releaseGroup: MusicBrainzReleaseGroup) : AlbumUnit()
        data class FavoriteAlbumUnit(val favorite: FavoriteAlbum) : AlbumUnit()
    }

    suspend fun buildFromArtists(artists: List<FavoriteArtist>): List<QueueItem> {
        if (artists.isEmpty()) return emptyList()
        val localIndex = buildLocalIndex()
        val queues = artists.mapNotNull { fav ->
            val resolution = artistDirectoryRepository.resolveArtist(fav.artist)
            val resolved = resolution as? ArtistResolution.Resolved ?: return@mapNotNull null
            val albums = artistDirectoryRepository.getAlbums(resolved.mbid)
            val singles = artistDirectoryRepository.getSingles(resolved.mbid)
            val units = (albums + singles).map { rg ->
                AlbumUnit.ReleaseGroupUnit(resolved.canonicalName, rg) as AlbumUnit
            }
            ArrayDeque(units)
        }
        val pending = roundRobinAndCollect(queues, localIndex)
        return finish(pending, localIndex)
    }

    suspend fun buildFromAlbums(albums: List<FavoriteAlbum>): List<QueueItem> {
        if (albums.isEmpty()) return emptyList()
        val localIndex = buildLocalIndex()
        val queues = albums.map { fav ->
            ArrayDeque(listOf(AlbumUnit.FavoriteAlbumUnit(fav) as AlbumUnit))
        }
        val pending = roundRobinAndCollect(queues, localIndex)
        return finish(pending, localIndex)
    }

    /**
     * Popurrí de sencillos favoritos -- sin selección ni reparto por
     * turnos, se reproducen TODOS los favoritos de golpe (petición
     * explícita: "le doy a reproducir... popurrí de todos los
     * sencillos que tenga elegidos en favoritos"). Prioriza los ya
     * descargados (coste cero) y completa con los de streaming
     * (FavoriteTrack) hasta el mismo tope de 100.
     * ---
     * Favorite singles popurrí -- no selection or round-robin, ALL
     * favorites play at once (explicit request: "I tap play... it
     * plays a mix of all the singles I have marked as favorites").
     * Prioritizes already-downloaded ones (zero cost) and fills in
     * with streaming ones (FavoriteTrack) up to the same cap of 100.
     */
    suspend fun buildFromFavoriteTracks(
        favoriteTracks: List<FavoriteTrack>,
        favoriteLocalTracks: List<SearchResultTrack>,
    ): List<QueueItem> {
        val seen = mutableSetOf<String>()
        val items = mutableListOf<QueueItem>()
        for (local in favoriteLocalTracks) {
            if (local.downloadStatus != DownloadStatus.DONE || local.filePath == null) continue
            if (!seen.add(local.youtubeId)) continue
            items += QueueItem(
                uri = local.filePath,
                title = local.title,
                isLocal = true,
                artist = local.artist ?: local.channelTitle,
                youtubeId = local.youtubeId,
            )
            if (items.size >= TRACK_CAP) break
        }
        if (items.size < TRACK_CAP) {
            for (fav in favoriteTracks) {
                if (!seen.add(fav.youtubeId)) continue
                items += QueueItem(
                    uri = "https://youtu.be/${fav.youtubeId}",
                    title = fav.title,
                    isLocal = false,
                    artist = fav.artist,
                    youtubeId = fav.youtubeId,
                )
                if (items.size >= TRACK_CAP) break
            }
        }
        return resolveStreamUrlsConcurrently(items)
    }

    /**
     * Núcleo del reparto por turnos: en cada ronda, resuelve UN álbum
     * de cada cola que todavía tenga álbumes pendientes (nunca la
     * discografía entera de golpe), añade sus pistas (con
     * deduplicación global por artista+título normalizados) y para en
     * cuanto se llega al tope o se agotan todas las colas.
     * ---
     * Round-robin core: each round resolves ONE album from every queue
     * that still has albums pending (never a whole discography at
     * once), adds its tracks (with global dedup by normalized
     * artist+title), and stops once the cap is hit or every queue is
     * exhausted.
     */
    private suspend fun roundRobinAndCollect(
        queues: List<ArrayDeque<AlbumUnit>>,
        localIndex: Map<Pair<String, String>, Map<Int, SearchResultTrack>>,
    ): List<PendingTrack> {
        val collected = mutableListOf<PendingTrack>()
        val seenKeys = mutableSetOf<Pair<String, String>>()
        var anyLeft = true
        while (collected.size < TRACK_CAP && anyLeft) {
            anyLeft = false
            for (queue in queues) {
                if (collected.size >= TRACK_CAP) break
                val unit = queue.removeFirstOrNull() ?: continue
                anyLeft = true
                val tracks = try {
                    resolveUnit(unit, localIndex)
                } catch (e: Exception) {
                    emptyList()
                }
                for (track in tracks) {
                    if (collected.size >= TRACK_CAP) break
                    val key = SearchNormalizer.normalizeArtistName(track.artist) to
                        SearchNormalizer.normalize(track.title)
                    if (!seenKeys.add(key)) continue
                    collected += track
                }
            }
        }
        return collected
    }

    /**
     * Antes de tocar la red: si el álbum ya está completo en local
     * (bug real reportado por Miguel Ángel, 2026-08-02: reproducir un
     * popurrí de 3 álbumes ya descargados y favoritos tardaba
     * demasiado en arrancar), sus pistas se construyen directamente
     * desde search_result_tracks y NUNCA se llama a
     * searchAlbumCandidates()/matchAlbumTracks() para ese álbum -- el
     * check local anterior en finish() llegaba DESPUÉS de ya haber
     * pagado el coste de red completo, que es justo lo que causaba el
     * retraso. Solo se cae a resolveTracksForReleaseGroup()/
     * resolveFavoriteAlbumTracks() cuando no hay ninguna pista local
     * para ese álbum.
     * ---
     * Before touching the network: if the album is already complete
     * locally (real bug reported by Miguel Ángel, 2026-08-02: playing
     * a popurrí of 3 already-downloaded favorite albums took too long
     * to start), its tracks are built directly from
     * search_result_tracks and searchAlbumCandidates()/
     * matchAlbumTracks() are NEVER called for that album -- the
     * previous local check in finish() ran AFTER already paying the
     * full network cost, which is exactly what caused the delay. Only
     * falls back to resolveTracksForReleaseGroup()/
     * resolveFavoriteAlbumTracks() when there's no local track at all
     * for that album.
     */
    private suspend fun resolveUnit(
        unit: AlbumUnit,
        localIndex: Map<Pair<String, String>, Map<Int, SearchResultTrack>>,
    ): List<PendingTrack> {
        val (artist, album) = when (unit) {
            is AlbumUnit.ReleaseGroupUnit -> unit.artist to unit.releaseGroup.title
            is AlbumUnit.FavoriteAlbumUnit -> unit.favorite.artist to unit.favorite.album
        }
        val key = SearchNormalizer.normalizeArtistName(artist) to SearchNormalizer.normalize(album)
        val localTracks = localIndex[key]
        if (!localTracks.isNullOrEmpty()) {
            return localTracks.map { (position, track) ->
                PendingTrack(
                    artist = artist,
                    album = album,
                    title = track.title,
                    youtubeId = track.youtubeId,
                    position = position,
                )
            }
        }
        return when (unit) {
            is AlbumUnit.ReleaseGroupUnit ->
                resolveTracksForReleaseGroup(unit.releaseGroup.id, unit.artist, unit.releaseGroup.title)
            is AlbumUnit.FavoriteAlbumUnit ->
                resolveFavoriteAlbumTracks(unit.favorite)
        }
    }

    private suspend fun resolveTracksForReleaseGroup(
        mbid: String,
        artist: String,
        albumTitle: String,
    ): List<PendingTrack> {
        val matches = albumMatchRepository.matchAlbumTracks(mbid = mbid, artist = artist, album = albumTitle)
        return matches.mapNotNull { match ->
            val track = match.matchedTrack ?: return@mapNotNull null
            PendingTrack(
                artist = artist,
                album = albumTitle,
                title = track.title,
                youtubeId = track.youtubeId,
                position = match.position - 1,
            )
        }
    }

    /** Álbum favorito sin mbid conocido (FavoriteAlbum solo guarda texto) -- primero hay que resolverlo, igual que AlbumViewModel.resolve(). */
    private suspend fun resolveFavoriteAlbumTracks(favorite: FavoriteAlbum): List<PendingTrack> {
        val candidates = albumMatchRepository.searchAlbumCandidates(
            artist = favorite.artist,
            album = favorite.album,
        )
        val normalizedAlbum = SearchNormalizer.normalize(favorite.album)
        val candidate = candidates.firstOrNull { SearchNormalizer.normalize(it.title) == normalizedAlbum }
            ?: candidates.firstOrNull()
            ?: return emptyList()
        return resolveTracksForReleaseGroup(
            candidate.mbid,
            candidate.artist ?: favorite.artist,
            candidate.title,
        )
    }

    /**
     * Cruce local por (artista, álbum, posición) -- MISMO criterio que
     * AlbumViewModel.refreshLocalTracks() (nunca por youtubeId recién
     * emparejado, que puede no coincidir con el que se descargó en su
     * día). Se construye UNA VEZ para todo el popurrí, no por pista.
     * ---
     * Local cross-reference by (artist, album, position) -- SAME
     * criterion as AlbumViewModel.refreshLocalTracks() (never by the
     * freshly-matched youtubeId, which may not match what was
     * downloaded back when). Built ONCE for the whole popurrí, not per
     * track.
     */
    private suspend fun buildLocalIndex(): Map<Pair<String, String>, Map<Int, SearchResultTrack>> {
        val all = searchResultTrackRepository.getAllOnce()
        return all
            .filter { it.trackPosition != null && it.album != null && it.downloadStatus == DownloadStatus.DONE }
            .groupBy { track ->
                SearchNormalizer.normalizeArtistName(track.artist ?: track.channelTitle) to
                    SearchNormalizer.normalize(track.album!!)
            }
            .mapValues { (_, tracks) -> tracks.associateBy { it.trackPosition!! } }
    }

    private suspend fun finish(
        pending: List<PendingTrack>,
        localIndex: Map<Pair<String, String>, Map<Int, SearchResultTrack>>,
    ): List<QueueItem> {
        if (pending.isEmpty()) return emptyList()
        val items = pending.map { track ->
            val key = SearchNormalizer.normalizeArtistName(track.artist) to
                SearchNormalizer.normalize(track.album)
            val local = localIndex[key]?.get(track.position)
            if (local != null && local.filePath != null) {
                QueueItem(
                    uri = local.filePath,
                    title = local.title,
                    isLocal = true,
                    artist = track.artist,
                    youtubeId = local.youtubeId,
                )
            } else {
                QueueItem(
                    uri = "https://youtu.be/${track.youtubeId}",
                    title = track.title,
                    isLocal = false,
                    artist = track.artist,
                    youtubeId = track.youtubeId,
                )
            }
        }
        return resolveStreamUrlsConcurrently(items)
    }

    /**
     * Resuelve la URL de stream real de cada QueueItem no local, en
     * tandas concurrentes (RESOLVE_CONCURRENCY a la vez) en vez de una
     * por una en secuencia -- con hasta 100 pistas, resolver de una en
     * una tardaría demasiado antes de poder arrancar la reproducción.
     * Una pista que falle al resolver se descarta en silencio (mismo
     * criterio tolerante que el resto de RadioRepository/AlbumViewModel:
     * "no encontrado" no detiene el conjunto).
     * ---
     * Resolves the real stream URL for each non-local QueueItem, in
     * concurrent batches (RESOLVE_CONCURRENCY at a time) instead of one
     * at a time in sequence -- with up to 100 tracks, resolving one by
     * one would take too long before playback could start. A track
     * that fails to resolve is silently dropped (same tolerant
     * criterion as the rest of RadioRepository/AlbumViewModel: "not
     * found" doesn't stop the whole batch).
     */
    private suspend fun resolveStreamUrlsConcurrently(items: List<QueueItem>): List<QueueItem> {
        val resolved = mutableListOf<QueueItem>()
        for (chunk in items.chunked(RESOLVE_CONCURRENCY)) {
            coroutineScope {
                val deferred = chunk.map { item ->
                    async {
                        if (item.isLocal) return@async item
                        try {
                            item.copy(uri = streamResolver.resolveAudioStreamUrl(item.uri))
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                resolved += deferred.mapNotNull { it.await() }
            }
        }
        return resolved
    }
}

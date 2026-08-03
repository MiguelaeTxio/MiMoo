package com.miguelaetxio.mimoo.data.favorites

import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.FavoriteAlbum
import com.miguelaetxio.mimoo.data.local.entity.FavoriteArtist
import com.miguelaetxio.mimoo.data.local.entity.FavoriteTrack
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import com.miguelaetxio.mimoo.data.playback.StreamResolver
import com.miguelaetxio.mimoo.data.remote.AlbumMatchRepository
import com.miguelaetxio.mimoo.data.remote.ArtistDirectoryRepository
import com.miguelaetxio.mimoo.data.remote.ArtistResolution
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzReleaseGroup
import com.miguelaetxio.mimoo.util.SearchNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        /**
         * Cuántas pistas se resuelven ANTES de arrancar la
         * reproducción -- petición explícita de Miguel Ángel
         * (2026-08-02): "tarda mucho en iniciar la reproducción
         * cuando no están descargados". Con 1 sola pista resuelta ya
         * se puede arrancar; el resto se resuelve en segundo plano
         * (ver playProgressively()) mientras suena, así que 1 es
         * suficiente sin dejar de sonar de fondo casi de inmediato.
         */
        private const val INITIAL_BATCH_SIZE = 1
    }

    /**
     * Ámbito propio para resolver el resto del popurrí EN SEGUNDO
     * PLANO, una vez ya arrancada la reproducción -- no puede ser el
     * `viewModelScope` de FavoritesViewModel: si el usuario navega
     * fuera de la pantalla de Favoritos mientras el popurrí sigue
     * sonando, la resolución del resto no debe cancelarse con la
     * pantalla. Mismo criterio que AutoSyncPusher.pushScope.
     * ---
     * Own scope to resolve the rest of the popurrí IN THE BACKGROUND,
     * once playback has already started -- can't be
     * FavoritesViewModel's `viewModelScope`: if the user navigates
     * away from the Favorites screen while the popurrí keeps playing,
     * resolving the rest shouldn't get cancelled along with the
     * screen. Same criterion as AutoSyncPusher.pushScope.
     */
    private val resolveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    /**
     * Bug real reportado por Miguel Ángel (2026-08-02): seleccionó
     * varios artistas favoritos y pulsó aleatorio a las 7:34; a las
     * 7:42 (8 minutos) no había sonado nada todavía. Causa: el
     * arreglo anterior de esta misma sesión (`playProgressively()`)
     * solo hacía progresiva la RESOLUCIÓN de streaming -- pero
     * construir el PLAN en sí (recorrer discografías completas,
     * álbum a álbum, con `resolveUnit()` -> red real contra
     * MusicBrainz/YouTube por cada álbum) seguía siendo bloqueante
     * ANTES de poder arrancar nada. Con MusicBrainz degradado (ver
     * los fallos de Radio reportados en el mismo mensaje), cada álbum
     * podía tardar varios segundos con reintentos, multiplicado por
     * decenas de álbumes antes de reunir 100 pistas.
     *
     * Ahora el reparto por turnos en sí es progresivo: en cuanto el
     * PRIMER álbum de CUALQUIER cola produce al menos una pista
     * resoluble, arranca la reproducción con ella -- el resto del
     * reparto (más álbumes, más artistas, hasta el tope de 100)
     * continúa en `resolveScope`, en segundo plano, exactamente igual
     * que ya hacía la resolución de streaming.
     * ---
     * Real bug reported by Miguel Ángel (2026-08-02): selected several
     * favorite artists and hit shuffle at 7:34; by 7:42 (8 minutes)
     * nothing had played yet. Cause: this same session's earlier fix
     * (`playProgressively()`) only made stream RESOLUTION progressive
     * -- but building the PLAN itself (walking full discographies,
     * album by album, with `resolveUnit()` -> real network against
     * MusicBrainz/YouTube per album) was still blocking BEFORE
     * anything could start. With MusicBrainz degraded (see the Radio
     * failures reported in the same message), each album could take
     * several seconds with retries, multiplied by dozens of albums
     * before gathering 100 tracks.
     *
     * Now the round-robin distribution itself is progressive: as soon
     * as the FIRST album from ANY queue produces at least one
     * resolvable track, playback starts with it -- the rest of the
     * distribution (more albums, more artists, up to the cap of 100)
     * continues on `resolveScope`, in the background, exactly like
     * stream resolution already did.
     */
    suspend fun playArtistsProgressively(
        playerManager: PlayerManager,
        artists: List<FavoriteArtist>,
        shuffle: Boolean,
    ): Boolean {
        if (artists.isEmpty()) return false
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
        return playRoundRobinProgressively(playerManager, queues, localIndex, shuffle)
    }

    /** Mismo arreglo que playArtistsProgressively() -- ver su comentario. */
    suspend fun playAlbumsProgressively(
        playerManager: PlayerManager,
        albums: List<FavoriteAlbum>,
        shuffle: Boolean,
    ): Boolean {
        if (albums.isEmpty()) return false
        val localIndex = buildLocalIndex()
        val queues = albums.map { fav ->
            ArrayDeque(listOf(AlbumUnit.FavoriteAlbumUnit(fav) as AlbumUnit))
        }
        return playRoundRobinProgressively(playerManager, queues, localIndex, shuffle)
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
        return items
    }

    /**
     * Fase 1: busca el PRIMER álbum, de cualquier cola, que produzca
     * al menos una pista resoluble -- arranca la reproducción con
     * ella y delega el resto del reparto (Fase 2, `continueCollecting()`)
     * en `resolveScope`. Nunca bloquea más allá de lo que tarda un
     * único álbum en resolverse.
     * ---
     * Phase 1: looks for the FIRST album, from any queue, that
     * produces at least one resolvable track -- starts playback with
     * it and hands off the rest of the distribution (Phase 2,
     * `continueCollecting()`) to `resolveScope`. Never blocks longer
     * than it takes to resolve a single album.
     */
    private suspend fun playRoundRobinProgressively(
        playerManager: PlayerManager,
        queues: List<ArrayDeque<AlbumUnit>>,
        localIndex: Map<Pair<String, String>, Map<Int, SearchResultTrack>>,
        shuffle: Boolean,
    ): Boolean {
        if (queues.isEmpty()) return false
        val seenKeys = mutableSetOf<Pair<String, String>>()
        val totalCollected = intArrayOf(0)
        var anyLeft = true
        while (totalCollected[0] < TRACK_CAP && anyLeft) {
            anyLeft = false
            for (queue in queues) {
                if (totalCollected[0] >= TRACK_CAP) break
                val unit = queue.removeFirstOrNull() ?: continue
                anyLeft = true
                val tracks = try {
                    resolveUnit(unit, localIndex)
                } catch (e: Exception) {
                    emptyList()
                }
                val fresh = collectFresh(tracks, seenKeys, totalCollected)
                if (fresh.isEmpty()) continue
                val items = fresh.map { toQueueItem(it, localIndex) }
                val resolvedFirst = resolveStreamUrlsConcurrently(items.take(1))
                // Este álbum en concreto no dio ninguna pista
                // reproducible (p.ej. todas sus URLs fallaron al
                // resolver) -- se prueba con el siguiente álbum de la
                // ronda en vez de rendirse.
                if (resolvedFirst.isEmpty()) continue
                if (shuffle) playerManager.playQueueShuffled(resolvedFirst) else playerManager.playQueue(resolvedFirst)
                val restOfBatch = items.drop(1)
                resolveScope.launch {
                    if (restOfBatch.isNotEmpty()) {
                        val resolvedRest = resolveStreamUrlsConcurrently(restOfBatch)
                        if (resolvedRest.isNotEmpty()) {
                            withContext(Dispatchers.Main) { playerManager.addToQueue(resolvedRest) }
                        }
                    }
                    continueCollecting(playerManager, queues, localIndex, seenKeys, totalCollected)
                }
                return true
            }
        }
        return false
    }

    /** Fase 2, en segundo plano (`resolveScope`): sigue el reparto por turnos donde lo dejó la Fase 1. */
    private suspend fun continueCollecting(
        playerManager: PlayerManager,
        queues: List<ArrayDeque<AlbumUnit>>,
        localIndex: Map<Pair<String, String>, Map<Int, SearchResultTrack>>,
        seenKeys: MutableSet<Pair<String, String>>,
        totalCollected: IntArray,
    ) {
        var anyLeft = true
        while (totalCollected[0] < TRACK_CAP && anyLeft) {
            anyLeft = false
            for (queue in queues) {
                if (totalCollected[0] >= TRACK_CAP) break
                val unit = queue.removeFirstOrNull() ?: continue
                anyLeft = true
                val tracks = try {
                    resolveUnit(unit, localIndex)
                } catch (e: Exception) {
                    emptyList()
                }
                val fresh = collectFresh(tracks, seenKeys, totalCollected)
                if (fresh.isEmpty()) continue
                val items = fresh.map { toQueueItem(it, localIndex) }
                val resolved = resolveStreamUrlsConcurrently(items)
                if (resolved.isNotEmpty()) {
                    withContext(Dispatchers.Main) { playerManager.addToQueue(resolved) }
                }
            }
        }
    }

    /** Filtra por deduplicación global (artista+título normalizados) y respeta el tope de 100, compartido por las dos fases. */
    private fun collectFresh(
        tracks: List<PendingTrack>,
        seenKeys: MutableSet<Pair<String, String>>,
        totalCollected: IntArray,
    ): List<PendingTrack> {
        val fresh = mutableListOf<PendingTrack>()
        for (track in tracks) {
            if (totalCollected[0] >= TRACK_CAP) break
            val key = SearchNormalizer.normalizeArtistName(track.artist) to
                SearchNormalizer.normalize(track.title)
            if (!seenKeys.add(key)) continue
            fresh += track
            totalCollected[0]++
        }
        return fresh
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

    /**
     * Decide local-vs-streaming para UNA pista -- misma lógica que
     * antes tenía `finish()`, ahora por pista suelta porque el
     * reparto por turnos ya no construye la lista entera de golpe
     * antes de reproducir nada (ver playRoundRobinProgressively()).
     * ---
     * Decides local-vs-streaming for ONE track -- same logic
     * `finish()` used to have, now per single track because the
     * round-robin distribution no longer builds the whole list at
     * once before playing anything (see playRoundRobinProgressively()).
     */
    private fun toQueueItem(
        track: PendingTrack,
        localIndex: Map<Pair<String, String>, Map<Int, SearchResultTrack>>,
    ): QueueItem {
        val key = SearchNormalizer.normalizeArtistName(track.artist) to
            SearchNormalizer.normalize(track.album)
        val local = localIndex[key]?.get(track.position)
        return if (local != null && local.filePath != null) {
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

    /**
     * Arranca la reproducción del plan devuelto por
     * buildFromFavoriteTracks() SIN esperar a resolver todas sus
     * pistas -- bug real reportado por Miguel Ángel (2026-08-02):
     * "tarda mucho en iniciar la reproducción cuando no están
     * descargados". Solo resuelve INITIAL_BATCH_SIZE pista(s) (barato:
     * si es local no hay red de por medio) y arranca con eso; el
     * resto se resuelve en `resolveScope`, en segundo plano, en tandas
     * de RESOLVE_CONCURRENCY, añadiéndose a la cola ya sonando via
     * `PlayerManager.addToQueue()` según van llegando -- nunca bloquea
     * la reproducción esperando al resto.
     *
     * (Artistas/Álbumes usan playArtistsProgressively()/
     * playAlbumsProgressively() en su lugar, que además hacen
     * progresiva la CONSTRUCCIÓN del plan en sí -- ver su comentario.
     * Sencillos favoritos no lo necesitan: buildFromFavoriteTracks()
     * no toca la red en absoluto, solo la base de datos local, así
     * que construir su plan ya es instantáneo.)
     *
     * Devuelve `true` si arrancó a sonar algo, `false` si no se pudo
     * resolver ni la primera pista (plan vacío o todo el lote inicial
     * falló).
     * ---
     * Starts playing the plan returned by buildFromFavoriteTracks()
     * WITHOUT waiting to resolve all its tracks -- real bug reported
     * by Miguel Ángel (2026-08-02): "takes a long time to start
     * playback when tracks aren't downloaded". Only resolves
     * INITIAL_BATCH_SIZE track(s) (cheap: if it's local there's no
     * network involved) and starts with that; the rest gets resolved
     * on `resolveScope`, in the background, in RESOLVE_CONCURRENCY
     * batches, getting appended to the already-playing queue via
     * `PlayerManager.addToQueue()` as they come in -- never blocks
     * playback waiting for the rest.
     *
     * (Artists/Albums use playArtistsProgressively()/
     * playAlbumsProgressively() instead, which also make building the
     * plan itself progressive -- see their comment. Favorite singles
     * don't need that: buildFromFavoriteTracks() never touches the
     * network, only the local database, so building its plan is
     * already instant.)
     *
     * Returns `true` if something started playing, `false` if not
     * even the first track could be resolved (empty plan or the whole
     * initial batch failed).
     */
    suspend fun playProgressively(
        playerManager: PlayerManager,
        plan: List<QueueItem>,
        shuffle: Boolean,
    ): Boolean {
        if (plan.isEmpty()) return false
        val firstBatch = resolveStreamUrlsConcurrently(plan.take(INITIAL_BATCH_SIZE))
        if (firstBatch.isEmpty()) return false
        if (shuffle) {
            playerManager.playQueueShuffled(firstBatch)
        } else {
            playerManager.playQueue(firstBatch)
        }
        val rest = plan.drop(INITIAL_BATCH_SIZE)
        if (rest.isNotEmpty()) {
            resolveScope.launch {
                for (chunk in rest.chunked(RESOLVE_CONCURRENCY)) {
                    val resolvedChunk = resolveStreamUrlsConcurrently(chunk)
                    if (resolvedChunk.isNotEmpty()) {
                        // PlayerManager envuelve ExoPlayer, que exige
                        // que sus llamadas lleguen desde el hilo
                        // principal -- resolveScope corre en
                        // Dispatchers.IO.
                        withContext(Dispatchers.Main) {
                            playerManager.addToQueue(resolvedChunk)
                        }
                    }
                }
            }
        }
        return true
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

package com.miguelaetxio.mimoo.data.favorites

import android.content.Context
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.FavoriteAlbum
import com.miguelaetxio.mimoo.data.local.entity.FavoriteArtist
import com.miguelaetxio.mimoo.data.local.entity.FavoriteTrack
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.DislikedArtistRepository
import com.miguelaetxio.mimoo.data.local.repository.DislikedTrackRepository
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import com.miguelaetxio.mimoo.data.playback.StreamResolver
import com.miguelaetxio.mimoo.data.remote.AlbumMatchRepository
import com.miguelaetxio.mimoo.data.remote.ArtistDirectoryRepository
import com.miguelaetxio.mimoo.data.remote.ArtistResolution
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzReleaseGroup
import com.miguelaetxio.mimoo.util.SearchNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    @ApplicationContext private val appContext: Context,
    private val storageManager: StorageManager,
    // H16 -- Lista Negra ("no me gusta"): mismo filtro DURO que en
    // RadioRepository/PlayerManager, aplicado aquí antes de encolar
    // cualquier tema del popurrí -- streaming o ya descargado. Ver
    // ANNEX_H16.md, "Hoja de Ruta para la Siguiente Sesión que retome
    // H16", punto 3.
    private val dislikedArtistRepository: DislikedArtistRepository,
    private val dislikedTrackRepository: DislikedTrackRepository,
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

        /**
         * S050 -- bug real reportado por Miguel Ángel: con varios
         * álbumes favoritos "Various Artists" seleccionados, uno de
         * ellos (una recopilación de cerca de 100 pistas por sí solo)
         * agotaba el tope de TRACK_CAP entero antes de que los demás
         * álbumes de la ronda tuvieran su turno -- el "reparto por
         * turnos" no era tal, era vaciar el primer álbum, luego el
         * segundo, luego el tercero. Causa real: `resolveUnit()`
         * devuelve el álbum COMPLETO de golpe, y se consumía sin
         * límite por ronda. Se corrige tomando como mucho
         * ROUND_ROBIN_BATCH_SIZE pistas de cada álbum POR RONDA,
         * guardando el resto en un búfer por álbum para la ronda
         * siguiente -- así ningún álbum, por grande que sea, puede
         * acaparar el tope antes de que los demás tengan su turno.
         */
        private const val ROUND_ROBIN_BATCH_SIZE = 5
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

    /**
     * Petición explícita de Miguel Ángel (2026-08-23): la generación en
     * segundo plano de un popurrí (resto de artistas/álbumes/sencillos
     * por resolver tras el arranque) no tenía manera de pararse -- solo
     * existía el botón de limpiar la cola de reproducción, que no es lo
     * mismo. `activeGenerationJob` es el ÚNICO job de generación en
     * marcha en cada momento (uno nuevo cancela y sustituye al
     * anterior, nunca coexisten dos); `isBackgroundGenerating` refleja
     * su estado para que FavoritesScreen pueda mostrar un botón de
     * parar mientras dura. Cancelarlo NUNCA toca `playerManager` --
     * solo deja de añadir más pistas a la cola que ya está sonando.
     * ---
     * Explicit request from Miguel Ángel (2026-08-23): a popurrí's
     * background generation (remaining artists/albums/singles still to
     * resolve after playback started) had no way to be stopped -- only
     * the "clear playback queue" button existed, which is not the same
     * thing. `activeGenerationJob` is the ONLY generation job running
     * at any time (a new one cancels and replaces the previous one,
     * never two at once); `isBackgroundGenerating` reflects its state
     * so FavoritesScreen can show a stop button while it runs.
     * Cancelling it NEVER touches `playerManager` -- it only stops
     * adding more tracks to the queue that's already playing.
     */
    private var activeGenerationJob: Job? = null

    private val _isBackgroundGenerating = MutableStateFlow(false)
    val isBackgroundGenerating: StateFlow<Boolean> = _isBackgroundGenerating.asStateFlow()

    private fun launchBackgroundGeneration(block: suspend () -> Unit) {
        activeGenerationJob?.cancel()
        _isBackgroundGenerating.value = true
        activeGenerationJob = resolveScope.launch {
            try {
                block()
            } finally {
                _isBackgroundGenerating.value = false
            }
        }
    }

    /** Para la generación en curso (si hay alguna) sin tocar la cola ya sonando/encolada. */
    fun cancelBackgroundGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _isBackgroundGenerating.value = false
    }

    /**
     * H16 -- snapshot de la Lista Negra, releída al arrancar CADA
     * popurrí (playArtistsProgressively()/playAlbumsProgressively()/
     * buildFromFavoriteTracks()) -- un popurrí es una cola efímera de
     * construcción rápida, no una sesión larga como la Radio, así que
     * una única lectura al principio basta (a diferencia de
     * PlayerManager.fetchRoundCandidate(), que la relee en cada
     * vuelta). Mismas claves normalizadas que expone
     * DislikedArtistRepository/DislikedTrackRepository -- pensadas
     * explícitamente para este uso, ver sus kdocs.
     */
    private var dislikedArtistKeysSnapshot: Set<String> = emptySet()
    private var dislikedTrackKeysSnapshot: Set<String> = emptySet()

    private suspend fun refreshDislikedSnapshots() {
        dislikedArtistKeysSnapshot = dislikedArtistRepository.normalizedKeysSnapshot()
        dislikedTrackKeysSnapshot = dislikedTrackRepository.normalizedKeysSnapshot()
    }

    private fun isArtistDisliked(artist: String?): Boolean =
        !artist.isNullOrBlank() && SearchNormalizer.normalizeArtistName(artist) in dislikedArtistKeysSnapshot

    private fun isTrackDisliked(artist: String?, title: String?): Boolean =
        !artist.isNullOrBlank() && !title.isNullOrBlank() &&
            DislikedTrackRepository.key(artist, title) in dislikedTrackKeysSnapshot

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
    /**
     * Bug real reportado por Miguel Ángel (2026-08-23): seleccionar
     * varios artistas favoritos tardaba MINUTOS en arrancar, en vez de
     * los 5-7 segundos esperados. Causa real, distinta del bug de
     * 2026-08-02 de más abajo (ese arreglaba que la CONSTRUCCIÓN del
     * plan bloquease tras tener ya las colas por artista): el bloque
     * que CONSTRUÍA esas colas resolvía TODOS los artistas
     * seleccionados de forma síncrona y secuencial -- `resolveArtist()`
     * + `getAlbums()` + `getSingles()`, hasta 3 llamadas de red por
     * artista -- ANTES de que la Fase 1 progresiva pudiera arrancar
     * nada. Con el interceptor de MusicBrainz limitando a 1 petición
     * cada 1,1s de forma GLOBAL (ver `NetworkModule`,
     * `MusicBrainzRateLimitInterceptor`) y `readTimeout`/
     * `connectTimeout` de 10s cada uno, seleccionar 7 artistas podía
     * significar hasta 21 llamadas encadenadas -- minutos reales si
     * alguna se degradaba, nunca menos de ~20s incluso en el caso
     * perfecto.
     *
     * Ahora se resuelve un artista a la vez: se prueba el primero: si
     * da una pista reproducible, arranca YA (coste real: un solo
     * artista, ~3 llamadas). Si no da nada, se prueba el siguiente,
     * sin volver atrás. El resto de la selección se resuelve EN
     * SEGUNDO PLANO, también uno a uno, sumándose al reparto por turnos
     * ya en marcha (`continueArtistsInBackground()`).
     *
     * Compromiso consciente, documentado para quien retome esto: el
     * reparto por turnos entre artistas (que ningún catálogo enorme
     * domine sobre otro) sigue aplicando DENTRO de las colas ya
     * incorporadas en cada momento, pero un artista que se suma tarde
     * a la generación en segundo plano ya no compite en pie de
     * igualdad con uno que llegó antes -- cada `continueCollecting()`
     * agota lo que tiene delante antes de que se sume el siguiente
     * artista. Aceptable: ocurre solo en segundo plano, con la música
     * ya sonando, y resuelve el problema real reportado hoy (arranque
     * lento). Si en el futuro se nota que unos artistas dominan sobre
     * otros en el popurrí de fondo, hay que revisar este punto.
     * ---
     * Real bug reported by Miguel Ángel (2026-08-23): selecting several
     * favorite artists took MINUTES to start, instead of the expected
     * 5-7 seconds. Real cause, distinct from the 2026-08-02 bug further
     * below (that one fixed the PLAN CONSTRUCTION blocking once the
     * per-artist queues already existed): the block that BUILT those
     * queues resolved ALL selected artists synchronously and
     * sequentially -- `resolveArtist()` + `getAlbums()` +
     * `getSingles()`, up to 3 network calls per artist -- BEFORE the
     * progressive Phase 1 could start anything. With MusicBrainz's
     * interceptor limiting to 1 request every 1.1s GLOBALLY (see
     * `NetworkModule`, `MusicBrainzRateLimitInterceptor`) and 10s
     * `readTimeout`/`connectTimeout` each, selecting 7 artists could
     * mean up to 21 chained calls -- real minutes if any degraded,
     * never less than ~20s even in the perfect case.
     *
     * Now artists resolve one at a time: try the first one; if it
     * yields a playable track, start NOW (real cost: a single artist,
     * ~3 calls). If it yields nothing, try the next, never going back.
     * The rest of the selection resolves IN THE BACKGROUND, also one at
     * a time, joining the round-robin already under way
     * (`continueArtistsInBackground()`).
     *
     * Conscious trade-off, documented for whoever picks this up next:
     * round-robin fairness among artists (no huge catalog dominating
     * another) still applies WITHIN the queues already incorporated at
     * any given moment, but an artist that joins background generation
     * late no longer competes on equal footing with one that arrived
     * earlier -- each `continueCollecting()` call drains what's ahead
     * of it before the next artist joins. Acceptable: it only happens
     * in the background, with music already playing, and it fixes
     * today's real complaint (slow start). If skewed distribution
     * toward earlier artists is ever noticed, revisit this.
     */
    suspend fun playArtistsProgressively(
        playerManager: PlayerManager,
        artists: List<FavoriteArtist>,
        shuffle: Boolean,
    ): Boolean {
        if (artists.isEmpty()) return false
        refreshDislikedSnapshots()
        log("playArtistsProgressively() -- ${artists.size} artista(s) seleccionados: ${artists.joinToString { it.artist }}")
        val localIndex = buildLocalIndex()
        // H16 -- Favoritos y Lista Negra se excluyen mutuamente (marcar
        // "no me gusta" quita el favorito, y viceversa -- ver PlayerBar),
        // así que en teoría esta lista nunca debería traer un artista
        // disliked; se filtra igualmente por seguridad defensiva, sin
        // depender de que ese otro punto nunca falle.
        val candidates = artists.filterNot { isArtistDisliked(it.artist) }
        if (candidates.isEmpty()) {
            log("playArtistsProgressively() -- todos los artistas seleccionados están en la Lista Negra")
            return false
        }

        val seenKeys = mutableSetOf<Pair<String, String>>()
        val totalCollected = intArrayOf(0)
        val queues = mutableListOf<ArrayDeque<AlbumUnit>>()

        var index = 0
        var started = false
        while (!started && index < candidates.size) {
            val queue = resolveArtistQueue(candidates[index])
            index++
            if (queue != null) queues += queue
            started = playOneRoundOverQueues(playerManager, queues, localIndex, seenKeys, totalCollected, shuffle)
        }
        if (!started) {
            log("playArtistsProgressively() -- NINGÚN artista dio ni una sola pista reproducible, popurrí vacío")
            return false
        }

        val remaining = candidates.drop(index)
        launchBackgroundGeneration {
            continueArtistsInBackground(playerManager, queues, remaining, localIndex, seenKeys, totalCollected)
        }
        return true
    }

    /**
     * Resuelve UN artista a MBID + álbumes + sencillos (hasta 3
     * llamadas de red) y devuelve su cola de trabajo, o `null` si no se
     * pudo resolver -- extraído de `playArtistsProgressively()` para
     * poder llamarse un artista cada vez, tanto en la Fase 1 síncrona
     * como en la continuación en segundo plano.
     */
    private suspend fun resolveArtistQueue(fav: FavoriteArtist): ArrayDeque<AlbumUnit>? {
        val resolution = try {
            artistDirectoryRepository.resolveArtist(fav.artist)
        } catch (e: Exception) {
            log("resolveArtistQueue() -- resolveArtist('${fav.artist}') lanzó excepción: ${e.javaClass.simpleName}: ${e.message}")
            return null
        }
        val resolved = resolution as? ArtistResolution.Resolved
        if (resolved == null) {
            log("resolveArtistQueue() -- '${fav.artist}' no se pudo resolver a un MBID ($resolution), se descarta de este popurrí")
            return null
        }
        val albums = try {
            artistDirectoryRepository.getAlbums(resolved.mbid)
        } catch (e: Exception) {
            log("resolveArtistQueue() -- getAlbums('${resolved.canonicalName}') lanzó excepción: ${e.javaClass.simpleName}: ${e.message}")
            return null
        }
        val singles = try {
            artistDirectoryRepository.getSingles(resolved.mbid)
        } catch (e: Exception) {
            log("resolveArtistQueue() -- getSingles('${resolved.canonicalName}') lanzó excepción: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
        log("resolveArtistQueue() -- '${fav.artist}' -> '${resolved.canonicalName}' (${albums.size} álbumes, ${singles.size} sencillos)")
        val units = (albums + singles).map { rg ->
            AlbumUnit.ReleaseGroupUnit(resolved.canonicalName, rg) as AlbumUnit
        }
        return ArrayDeque(units)
    }

    /**
     * Recorre UNA ronda de las colas actuales (una unidad por cola)
     * buscando la primera pista reproducible; si la encuentra, arranca
     * la reproducción con ella y añade el resto de esa unidad a la cola
     * ya sonando -- nunca bloquea más de lo que tarda resolver una
     * única unidad por cola. Usado solo por `playArtistsProgressively()`
     * en su Fase 1 -- `playAlbumsProgressively()` sigue usando
     * `playRoundRobinProgressively()` sin cambios, porque no tiene el
     * problema de resolución por artista que motiva esta función.
     */
    private suspend fun playOneRoundOverQueues(
        playerManager: PlayerManager,
        queues: MutableList<ArrayDeque<AlbumUnit>>,
        localIndex: Map<Pair<String, String>, Map<Int, SearchResultTrack>>,
        seenKeys: MutableSet<Pair<String, String>>,
        totalCollected: IntArray,
        shuffle: Boolean,
    ): Boolean {
        for (queue in queues) {
            if (totalCollected[0] >= TRACK_CAP) break
            val unit = queue.removeFirstOrNull() ?: continue
            val tracks = try {
                resolveUnit(unit, localIndex)
            } catch (e: Exception) {
                log("playOneRoundOverQueues() -- resolveUnit() lanzó excepción para '${unitLabel(unit)}': ${e.javaClass.simpleName}: ${e.message}")
                emptyList()
            }
            val fresh = collectFresh(tracks, seenKeys, totalCollected)
            if (fresh.isEmpty()) continue
            val items = fresh.map { toQueueItem(it, localIndex) }
            val resolvedFirst = resolveStreamUrlsConcurrently(items.take(1))
            if (resolvedFirst.isEmpty()) continue
            if (shuffle) playerManager.playQueueShuffled(resolvedFirst) else playerManager.playQueue(resolvedFirst)
            val restOfBatch = items.drop(1)
            if (restOfBatch.isNotEmpty()) {
                val resolvedRest = resolveStreamUrlsConcurrently(restOfBatch)
                if (resolvedRest.isNotEmpty()) {
                    withContext(Dispatchers.Main) { playerManager.addToQueue(resolvedRest) }
                }
            }
            return true
        }
        return false
    }

    /**
     * Fase 2 de `playArtistsProgressively()`, en segundo plano: agota
     * primero lo que quede en las colas ya incorporadas (mismo
     * mecanismo que `continueCollecting()`), y cuando se acaban,
     * resuelve el SIGUIENTE artista pendiente de la selección (uno a
     * uno, nunca todos de golpe) y lo suma al reparto -- hasta agotar
     * la selección completa o llegar al tope de 100.
     */
    private suspend fun continueArtistsInBackground(
        playerManager: PlayerManager,
        queues: MutableList<ArrayDeque<AlbumUnit>>,
        remainingCandidates: List<FavoriteArtist>,
        localIndex: Map<Pair<String, String>, Map<Int, SearchResultTrack>>,
        seenKeys: MutableSet<Pair<String, String>>,
        totalCollected: IntArray,
    ) {
        // S050 -- mismo búfer de reparto por turnos para las DOS
        // llamadas a continueCollecting() de aquí abajo -- si cada una
        // creara el suyo (el parámetro tiene valor por defecto), se
        // perdería lo que quedó a medias del álbum/sencillo de la
        // primera llamada al procesar el siguiente artista. Ver el
        // comentario de ROUND_ROBIN_BATCH_SIZE.
        val carryover = mutableMapOf<ArrayDeque<AlbumUnit>, ArrayDeque<PendingTrack>>()
        continueCollecting(playerManager, queues, localIndex, seenKeys, totalCollected, carryover)
        for (fav in remainingCandidates) {
            if (totalCollected[0] >= TRACK_CAP) break
            val queue = resolveArtistQueue(fav) ?: continue
            queues += queue
            continueCollecting(playerManager, queues, localIndex, seenKeys, totalCollected, carryover)
        }
        log("continueArtistsInBackground() -- terminado, ${totalCollected[0]}/$TRACK_CAP pistas reunidas en total")
    }

    /** Mismo arreglo que playArtistsProgressively() -- ver su comentario. */
    suspend fun playAlbumsProgressively(
        playerManager: PlayerManager,
        albums: List<FavoriteAlbum>,
        shuffle: Boolean,
    ): Boolean {
        if (albums.isEmpty()) return false
        refreshDislikedSnapshots()
        log("playAlbumsProgressively() -- ${albums.size} álbum(es) seleccionados: ${albums.joinToString { "${it.artist} - ${it.album}" }}")
        val localIndex = buildLocalIndex()
        // H16 -- ver el comentario equivalente en playArtistsProgressively().
        val queues = albums.filterNot { isArtistDisliked(it.artist) }.map { fav ->
            ArrayDeque(listOf(AlbumUnit.FavoriteAlbumUnit(fav) as AlbumUnit))
        }
        val started = playRoundRobinProgressively(playerManager, queues, localIndex, shuffle)
        if (!started) {
            log("playAlbumsProgressively() -- NINGÚN álbum dio ni una sola pista reproducible, popurrí vacío")
        }
        return started
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
        refreshDislikedSnapshots()
        val seen = mutableSetOf<String>()
        val items = mutableListOf<QueueItem>()
        for (local in favoriteLocalTracks) {
            if (local.downloadStatus != DownloadStatus.DONE || local.filePath == null) continue
            if (!seen.add(local.youtubeId)) continue
            // H16 -- ya descargado no significa exento: un tema
            // marcado "no me gusta" deja de sonar en cualquier
            // contexto automático (Radio, popurrí), aunque siga en el
            // disco/Biblioteca local -- ver ANNEX_H16.md, "Decisiones
            // ya cerradas con Miguel Ángel en S029", punto 3.
            val artistName = local.artist ?: local.channelTitle
            if (isArtistDisliked(artistName) || isTrackDisliked(artistName, local.title)) continue
            items += QueueItem(
                uri = local.filePath,
                title = local.title,
                isLocal = true,
                artist = artistName,
                youtubeId = local.youtubeId,
            )
            if (items.size >= TRACK_CAP) break
        }
        if (items.size < TRACK_CAP) {
            for (fav in favoriteTracks) {
                if (!seen.add(fav.youtubeId)) continue
                if (isArtistDisliked(fav.artist) || isTrackDisliked(fav.artist, fav.title)) continue
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
        // S050 -- búfer por álbum, ver el comentario de
        // ROUND_ROBIN_BATCH_SIZE. Se crea aquí y se le pasa a
        // continueCollecting() para que el reparto por turnos siga
        // siendo real en la Fase 2 (segundo plano), no solo en esta
        // primera ronda síncrona.
        val carryover = mutableMapOf<ArrayDeque<AlbumUnit>, ArrayDeque<PendingTrack>>()
        var anyLeft = true
        while (totalCollected[0] < TRACK_CAP && anyLeft) {
            anyLeft = false
            for (queue in queues) {
                if (totalCollected[0] >= TRACK_CAP) break
                val buffer = carryover.getOrPut(queue) { ArrayDeque() }
                if (buffer.isEmpty()) {
                    val unit = queue.removeFirstOrNull() ?: continue
                    anyLeft = true
                    val resolved = try {
                        resolveUnit(unit, localIndex)
                    } catch (e: Exception) {
                        log("playRoundRobinProgressively() -- resolveUnit() lanzó excepción para '${unitLabel(unit)}': ${e.javaClass.simpleName}: ${e.message}")
                        emptyList()
                    }
                    buffer.addAll(resolved)
                    if (buffer.isEmpty()) continue
                } else {
                    anyLeft = true
                }
                val batch = mutableListOf<PendingTrack>()
                repeat(ROUND_ROBIN_BATCH_SIZE) { buffer.removeFirstOrNull()?.let { batch += it } }
                val fresh = collectFresh(batch, seenKeys, totalCollected)
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
                launchBackgroundGeneration {
                    if (restOfBatch.isNotEmpty()) {
                        val resolvedRest = resolveStreamUrlsConcurrently(restOfBatch)
                        if (resolvedRest.isNotEmpty()) {
                            withContext(Dispatchers.Main) { playerManager.addToQueue(resolvedRest) }
                        }
                    }
                    continueCollecting(playerManager, queues, localIndex, seenKeys, totalCollected, carryover)
                }
                return true
            }
        }
        log(
            "playRoundRobinProgressively() -- Fase 1 agotada sin encontrar ni una sola pista " +
                "reproducible en ${queues.size} cola(s) (tope=$TRACK_CAP, recogidas=${totalCollected[0]})",
        )
        return false
    }

    /** Fase 2, en segundo plano (`resolveScope`): sigue el reparto por turnos donde lo dejó la Fase 1. */
    private suspend fun continueCollecting(
        playerManager: PlayerManager,
        queues: List<ArrayDeque<AlbumUnit>>,
        localIndex: Map<Pair<String, String>, Map<Int, SearchResultTrack>>,
        seenKeys: MutableSet<Pair<String, String>>,
        totalCollected: IntArray,
        carryover: MutableMap<ArrayDeque<AlbumUnit>, ArrayDeque<PendingTrack>> = mutableMapOf(),
    ) {
        var anyLeft = true
        while (totalCollected[0] < TRACK_CAP && anyLeft) {
            anyLeft = false
            for (queue in queues) {
                if (totalCollected[0] >= TRACK_CAP) break
                val buffer = carryover.getOrPut(queue) { ArrayDeque() }
                if (buffer.isEmpty()) {
                    val unit = queue.removeFirstOrNull() ?: continue
                    anyLeft = true
                    val resolved = try {
                        resolveUnit(unit, localIndex)
                    } catch (e: Exception) {
                        log("continueCollecting() -- resolveUnit() lanzó excepción para '${unitLabel(unit)}': ${e.javaClass.simpleName}: ${e.message}")
                        emptyList()
                    }
                    buffer.addAll(resolved)
                    if (buffer.isEmpty()) continue
                } else {
                    anyLeft = true
                }
                val batch = mutableListOf<PendingTrack>()
                repeat(ROUND_ROBIN_BATCH_SIZE) { buffer.removeFirstOrNull()?.let { batch += it } }
                val fresh = collectFresh(batch, seenKeys, totalCollected)
                if (fresh.isEmpty()) continue
                val items = fresh.map { toQueueItem(it, localIndex) }
                val resolved = resolveStreamUrlsConcurrently(items)
                if (resolved.isNotEmpty()) {
                    withContext(Dispatchers.Main) { playerManager.addToQueue(resolved) }
                }
            }
        }
        log("continueCollecting() -- terminado, ${totalCollected[0]}/$TRACK_CAP pistas reunidas en total")
    }

    /** Nombre corto de una unidad de trabajo, solo para los mensajes de depuración. */
    private fun unitLabel(unit: AlbumUnit): String = when (unit) {
        is AlbumUnit.ReleaseGroupUnit -> "${unit.artist} - ${unit.releaseGroup.title}"
        is AlbumUnit.FavoriteAlbumUnit -> "${unit.favorite.artist} - ${unit.favorite.album}"
    }

    private fun log(line: String) = PopurriDebugLogger.log(appContext, storageManager, line)

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
            // H16 -- exclusión dura de la Lista Negra, mismo punto
            // único de paso que ya usan las dos fases del reparto por
            // turnos (Fase 1 en playRoundRobinProgressively(), Fase 2
            // en continueCollecting()) para deduplicar. No cuenta para
            // el tope de 100 -- simplemente no se ofrece.
            if (isArtistDisliked(track.artist) || isTrackDisliked(track.artist, track.title)) continue
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
            is AlbumUnit.ReleaseGroupUnit -> {
                // Bug real (2026-08-03, log real como prueba: 100% de
                // los álbumes de este flujo fallaban con HTTP 404) --
                // `unit.releaseGroup.id` es un id de RELEASE-GROUP
                // (getAlbums()/getSingles()), pero
                // resolveTracksForReleaseGroup()/matchAlbumTracks()
                // exige el id de una RELEASE concreta. Se resuelve
                // aquí antes de pedir el tracklist -- mismo mecanismo
                // que ya usaba ArtistDirectoryRepository.getTrackCount(),
                // ahora compartido vía resolveRepresentativeReleaseId().
                val releaseId = artistDirectoryRepository
                    .resolveRepresentativeReleaseId(unit.releaseGroup.id)
                if (releaseId == null) {
                    log(
                        "resolveUnit() -- '${unit.artist} - ${unit.releaseGroup.title}' sin ninguna " +
                            "release resoluble para su release-group (${unit.releaseGroup.id})",
                    )
                    emptyList()
                } else {
                    resolveTracksForReleaseGroup(releaseId, unit.artist, unit.releaseGroup.title)
                }
            }
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
        if (plan.isEmpty()) {
            log("playProgressively() -- plan vacío, no hay ningún sencillo favorito resoluble")
            return false
        }
        val firstBatch = resolveStreamUrlsConcurrently(plan.take(INITIAL_BATCH_SIZE))
        if (firstBatch.isEmpty()) {
            log("playProgressively() -- no se pudo resolver la URL de streaming de la primera pista del plan (${plan.size} en total)")
            return false
        }
        if (shuffle) {
            playerManager.playQueueShuffled(firstBatch)
        } else {
            playerManager.playQueue(firstBatch)
        }
        val rest = plan.drop(INITIAL_BATCH_SIZE)
        if (rest.isNotEmpty()) {
            launchBackgroundGeneration {
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

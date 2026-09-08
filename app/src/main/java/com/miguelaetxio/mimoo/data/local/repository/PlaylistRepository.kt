package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.PlaylistDao
import com.miguelaetxio.mimoo.data.local.entity.Playlist
import com.miguelaetxio.mimoo.data.local.entity.PlaylistTrackCrossRef
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import com.miguelaetxio.mimoo.data.playback.StreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S053 -- datos mínimos para poder crear la fila de
 * `search_result_tracks` si hace falta al añadir a una lista (ver
 * `addTrackToPlaylist()`). `artist` puede ser null -- pistas
 * streaming-only a veces no tienen artista estructurado resuelto.
 */
data class PlaylistTrackInput(val youtubeId: String, val title: String, val artist: String?)

/**
 * Resultado de intentar reproducir una playlist completa por id (H18,
 * S032) -- ver playPlaylistById(), extraída de
 * PlaylistDetailViewModel.playAll() para poder reutilizarla también
 * desde el botón individual de play/aleatorio de la pestaña "Listas"
 * de Favoritos, sin duplicar la lógica de resolución de streaming.
 * `started = false` cubre tanto la playlist vacía como el caso en que
 * ninguna pista se pudo resolver.
 */
data class PlaylistPlayResult(
    val started: Boolean,
    val resolutionFailures: Int,
)

/**
 * Repository for playlists and their track membership/order
 * (Hito 04). Thin wrapper over PlaylistDao, same pattern as
 * SearchResultTrackRepository.
 * ---
 * Repositorio de playlists y la pertenencia/orden de sus pistas
 * (Hito 04). Envoltorio fino sobre PlaylistDao, mismo patrón que
 * SearchResultTrackRepository.
 */
@Singleton
class PlaylistRepository @Inject constructor(
    private val dao: PlaylistDao,
    // S045 -- petición explícita de Miguel Ángel: "obvio un
    // recopilatorio, marco un tema, vuelvo a poner el recopilatorio y
    // suena el tema marcado." Causa real: esta clase nunca comprobaba
    // "no me gusta" a nivel de TEMA en absoluto -- ver el kdoc real
    // junto a playPlaylistById().
    private val dislikedTrackRepository: DislikedTrackRepository,
    // S053 -- petición explícita de Miguel Ángel tras un crash real
    // (FOREIGN KEY constraint failed): la fila de search_result_tracks
    // tiene que existir ANTES de insertar el cross-ref (la clave
    // foránea de PlaylistTrackCrossRef.youtubeId apunta ahí) -- ver
    // addTrackToPlaylist() más abajo.
    private val searchResultTrackRepository: SearchResultTrackRepository,
) {
    fun getAllPlaylists(): Flow<List<Playlist>> = dao.getAllPlaylists()

    /** S057 -- ver el kdoc real en PlaylistDao.getFirstTrackCoverArtPerPlaylist(). */
    fun getFirstTrackCoverArtPerPlaylist(): Flow<List<com.miguelaetxio.mimoo.data.local.dao.PlaylistCoverArt>> =
        dao.getFirstTrackCoverArtPerPlaylist()

    suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(Playlist(name = name))

    suspend fun renamePlaylist(playlistId: Long, name: String) =
        dao.renamePlaylist(playlistId, name)

    suspend fun deletePlaylist(playlistId: Long) =
        dao.deletePlaylist(playlistId)

    fun getTracksForPlaylist(playlistId: Long): Flow<List<SearchResultTrack>> =
        dao.getTracksForPlaylist(playlistId)

    /**
     * S051 -- variante de una sola lectura, usada para comprobar
     * duplicados ANTES de añadir (ver AddToPlaylistDialogViewModel):
     * hace falta un valor puntual, no un Flow que siga observando.
     */
    suspend fun getTracksForPlaylistOnce(playlistId: Long): List<SearchResultTrack> =
        dao.getTracksForPlaylistOnce(playlistId)

    fun getTrackCountForPlaylist(playlistId: Long): Flow<Int> =
        dao.getTrackCountForPlaylist(playlistId)

    /**
     * Appends a track at the end of the playlist (max position + 1,
     * or 0 for the first track).
     * ---
     * Añade una pista al final de la playlist (posición máxima + 1, o
     * 0 para la primera pista).
     */
    /**
     * S053 -- crash real reportado por Miguel Ángel:
     * SQLiteConstraintException FOREIGN KEY constraint failed al
     * añadir a una lista. Causa real: `PlaylistTrackCrossRef` tiene
     * una FOREIGN KEY real sobre `youtubeId` hacia
     * `search_result_tracks` (a diferencia de `updateFavorite()`, que
     * es un UPDATE silencioso -- ver `setFavoriteEnsuringRow()`, S010,
     * mismo problema de fondo). Una pista de un popurrí de favoritos
     * (streaming puro, nunca buscada ni descargada) no tiene fila en
     * `search_result_tracks` -- el INSERT del cross-ref petaba en vez
     * de fallar en silencio. Se asegura la fila ANTES de insertar el
     * cross-ref, mismo patrón que `setFavoriteEnsuringRow()`.
     */
    suspend fun addTrackToPlaylist(playlistId: Long, track: PlaylistTrackInput) {
        searchResultTrackRepository.ensureRowExists(
            youtubeId = track.youtubeId,
            title = track.title,
            channelTitle = track.artist ?: track.title,
            artist = track.artist,
        )
        val nextPosition = (dao.getMaxPosition(playlistId) ?: -1) + 1
        dao.addTrackToPlaylist(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                youtubeId = track.youtubeId,
                position = nextPosition,
            )
        )
    }

    /**
     * Añade varias pistas de golpe, en el orden dado -- petición
     * explícita de Miguel Ángel (2026-07-04): poder añadir un álbum
     * entero a una lista de reproducción, no solo pista a pista.
     * Reutiliza addTrackToPlaylist() en bucle: cada llamada añade al
     * final, así que el orden del álbum se conserva en la lista.
     * ---
     * Adds several tracks at once, in the given order -- explicit
     * request from Miguel Ángel (2026-07-04): being able to add a
     * whole album to a playlist, not just track by track. Reuses
     * addTrackToPlaylist() in a loop: each call appends at the end, so
     * the album's order is preserved in the playlist.
     */
    suspend fun addTracksToPlaylist(playlistId: Long, tracks: List<PlaylistTrackInput>) {
        tracks.forEach { track ->
            addTrackToPlaylist(playlistId, track)
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, youtubeId: String) =
        dao.removeTrackFromPlaylist(playlistId, youtubeId)

    suspend fun updatePosition(playlistId: Long, youtubeId: String, position: Int) =
        dao.updatePosition(playlistId, youtubeId, position)

    /**
     * Reproduce la playlist completa en el orden guardado (H18, S032,
     * lógica extraída de PlaylistDetailViewModel.playAll() para
     * reutilizarla desde el botón individual de play/aleatorio de la
     * pestaña "Listas" de Favoritos -- mismo comportamiento exacto:
     * pistas descargadas en local, el resto resuelve streaming vía
     * StreamResolver, una pista cuya resolución falla se omite sin
     * abortar el resto). `shuffle = true` activa el modo aleatorio del
     * reproductor tras encolar, mismo criterio que el resto de
     * popurrís de la app (SelectionHeader/playAllFavoriteTracks).
     * ---
     * Plays the whole playlist in its saved order (H18, S032, logic
     * extracted from PlaylistDetailViewModel.playAll() to reuse from
     * the individual play/shuffle button in the Favorites "Listas"
     * tab -- exact same behavior: downloaded tracks play locally, the
     * rest resolve streaming via StreamResolver, a track whose
     * resolution fails is skipped without aborting the rest).
     * `shuffle = true` turns on the player's shuffle mode after
     * queueing, same criterion as the rest of the app's popurrís
     * (SelectionHeader/playAllFavoriteTracks).
     */
    /**
     * S045 -- bug real reportado por Miguel Ángel con texto exacto:
     * *"obvio un recopilatorio, marco un tema. Vuelvo a poner el
     * recopilatorio y suena el tema marcado... el mismo tema
     * exactamente el mismo, suena lo pongas en la lista negra o en la
     * verde."* No era un problema de coincidencia de texto (el fix de
     * S039/S042 para el veto de ARTISTA no aplica aquí) -- era que
     * esta función, al reproducir una playlist/recopilatorio propio,
     * nunca comprobaba en absoluto el "no me gusta" a nivel de TEMA
     * (`DislikedTrackRepository`) -- se limitaba a poner en cola todas
     * las pistas guardadas, sin filtrar nada.
     */
    // S062 -- petición explícita de Miguel Ángel: mismo criterio que
    // PopurriRepository.resolveScope -- si el usuario navega fuera de
    // la pantalla mientras se resuelve el resto de la lista, esa
    // resolución no debe cancelarse junto con la pantalla.
    private val resolveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * S062 -- bug real reportado por Miguel Ángel: "le das al play y
     * se pega un montón de rato el spinner dando vueltas antes de que
     * empiece a sonar la música." Causa real: esta función resolvía
     * la URL de streaming de TODAS las pistas de la lista, una a una y
     * en orden, ANTES de reproducir nada -- para una lista con muchas
     * pistas sin descargar (el caso normal desde S056, que importa
     * playlists como sencillos sueltos) eso podía ser media lista de
     * llamadas de red seguidas antes de que sonara el primer segundo.
     *
     * Arreglo: mismo patrón progresivo que ya usa
     * PopurriRepository -- se prueba pista a pista hasta encontrar la
     * PRIMERA reproducible, se arranca la reproducción con ella YA, y
     * el resto se resuelve en segundo plano (`resolveScope`),
     * añadiéndose a la cola ya sonando en cuanto está listo.
     *
     * S062 -- petición explícita de Miguel Ángel, en el mismo mensaje:
     * "no tenemos posibilidad de reproducir en aleatorio". Con el
     * arranque progresivo no basta con pedirle a ExoPlayer que mezcle
     * la cola (`playQueueShuffled()`) -- solo tendríamos un elemento
     * cuando arranca. Se mezcla el ORDEN de las pistas aquí mismo,
     * antes de resolver nada (`tracks.shuffled()`), y se aplica el
     * mismo arranque progresivo sobre ese orden ya mezclado -- el
     * resultado es una reproducción realmente aleatoria, no solo el
     * primer tema al azar seguido del resto en orden de lista.
     * ---
     * S062 -- real bug reported by Miguel Ángel: "you hit play and the
     * spinner spins for a long while before the music starts." Real
     * cause: this function resolved the streaming URL of EVERY track
     * in the playlist, one by one in order, BEFORE playing anything --
     * for a playlist with many non-downloaded tracks (the normal case
     * since S056, which imports playlists as loose singles) that could
     * mean half the playlist's worth of network calls in a row before
     * a single second of audio played.
     *
     * Fix: same progressive pattern PopurriRepository already uses --
     * try tracks one by one until the FIRST playable one is found,
     * start playback with it RIGHT AWAY, and resolve the rest in the
     * background (`resolveScope`), appending to the already-playing
     * queue as each one becomes ready.
     *
     * S062 -- explicit request from Miguel Ángel, same message: "we
     * don't have the option to play in shuffle." With progressive
     * startup, just asking ExoPlayer to shuffle the queue
     * (`playQueueShuffled()`) isn't enough -- there'd only be one
     * element when it starts. The track ORDER is shuffled right here,
     * before resolving anything (`tracks.shuffled()`), and the same
     * progressive startup runs on that already-shuffled order -- the
     * result is genuinely random playback, not just a random first
     * track followed by the rest in playlist order.
     */
    suspend fun playPlaylistById(
        playlistId: Long,
        shuffle: Boolean,
        playerManager: PlayerManager,
        streamResolver: StreamResolver,
    ): PlaylistPlayResult {
        val dislikedKeys = dislikedTrackRepository.normalizedKeysSnapshot()
        val filteredTracks = dao.getTracksForPlaylistOnce(playlistId).filterNot { track ->
            DislikedTrackRepository.key(track.artist ?: track.channelTitle ?: "", track.title) in dislikedKeys
        }
        if (filteredTracks.isEmpty()) return PlaylistPlayResult(started = false, resolutionFailures = 0)
        val orderedTracks = if (shuffle) filteredTracks.shuffled() else filteredTracks

        var resolutionFailures = 0
        var firstItem: QueueItem? = null
        var firstIndex = -1
        for ((index, track) in orderedTracks.withIndex()) {
            val item = resolveTrackToQueueItem(track, playerManager, streamResolver)
            if (item != null) {
                firstItem = item
                firstIndex = index
                break
            }
            resolutionFailures++
        }
        if (firstItem == null) {
            return PlaylistPlayResult(started = false, resolutionFailures = resolutionFailures)
        }

        playerManager.playQueue(listOf(firstItem))

        val remaining = orderedTracks.subList(firstIndex + 1, orderedTracks.size)
        if (remaining.isNotEmpty()) {
            resolveScope.launch {
                val items = remaining.mapNotNull { track ->
                    resolveTrackToQueueItem(track, playerManager, streamResolver)
                }
                if (items.isNotEmpty()) {
                    withContext(Dispatchers.Main) { playerManager.addToQueue(items) }
                }
            }
        }
        // Nota: resolutionFailures aquí solo cuenta los fallos ANTES
        // de la primera pista reproducible -- los que pudieran ocurrir
        // después, ya en segundo plano, no llegan a reflejarse en la
        // UI en el momento (mismo límite aceptado que la generación
        // progresiva de PopurriRepository).
        return PlaylistPlayResult(started = true, resolutionFailures = resolutionFailures)
    }

    /** S062 -- decide local-vs-streaming para UNA pista; extraído de playPlaylistById() para poder resolver pista a pista en vez de la lista entera de golpe. */
    private suspend fun resolveTrackToQueueItem(
        track: SearchResultTrack,
        playerManager: PlayerManager,
        streamResolver: StreamResolver,
    ): QueueItem? {
        val localPath = track.filePath
        val remoteUrl = track.youtubeUrl
        return if (localPath != null) {
            QueueItem(
                uri = localPath,
                title = track.title,
                isLocal = true,
                artist = track.artist ?: track.channelTitle,
                youtubeId = track.youtubeId,
                channelTitle = track.channelTitle,
                artworkUri = track.coverArtUrl ?: track.thumbnailUrl,
            )
        } else if (remoteUrl == null) {
            null
        } else {
            try {
                val streamUrl = streamResolver.resolveAudioStreamUrl(remoteUrl)
                playerManager.resolveStreamItem(
                    streamUrl = streamUrl,
                    videoTitle = track.title,
                    structuredArtist = track.artist,
                    youtubeId = track.youtubeId,
                    artworkUri = track.coverArtUrl ?: track.thumbnailUrl,
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

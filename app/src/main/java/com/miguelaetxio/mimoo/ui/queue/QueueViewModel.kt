package com.miguelaetxio.mimoo.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlaybackState
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs QueueScreen -- la cola de reproducción de SESIÓN, en memoria,
 * distinta de las Playlists guardadas (PlaylistDetailScreen/Room).
 * Petición explícita de Miguel Ángel (2026-07-05). Envoltorio fino
 * sobre PlayerManager, mismo patrón que PlayerBarViewModel.
 * ---
 * Sostiene QueueScreen -- la cola de reproducción de SESIÓN, en
 * memoria, distinta de las Playlists guardadas
 * (PlaylistDetailScreen/Room). Explicit request from Miguel Ángel
 * (2026-07-05). Thin wrapper over PlayerManager, same pattern as
 * PlayerBarViewModel.
 */
@HiltViewModel
class QueueViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val searchResultTrackRepository: SearchResultTrackRepository,
) : ViewModel() {

    val queue: StateFlow<List<QueueItem>> = playerManager.queue
    val playbackState: StateFlow<PlaybackState> = playerManager.state

    /**
     * Favoritos por youtubeId (S010, favoritos desde la cola --
     * petición explícita de Miguel Ángel: "que aparezca en todos
     * sitios"). Un Set en vez de una consulta por pista: la cola ya
     * puede tener hasta 10 pistas de Radio a la vez, un Set evita
     * repetir la consulta a Room fila por fila en cada recomposición.
     * ---
     * Favorites by youtubeId (S010, favorites from the queue). A Set
     * instead of a per-track query: the queue can already have up to
     * 10 Radio tracks at once, a Set avoids repeating the Room query
     * row by row on every recomposition.
     */
    val favoriteYoutubeIds: StateFlow<Set<String>> = searchResultTrackRepository.getAll()
        .map { tracks -> tracks.filter { it.isFavorite }.map { it.youtubeId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /**
     * Recibe el QueueItem completo, no solo el youtubeId (S010,
     * reportado por Miguel Ángel: "añadir a favoritos solo funciona
     * con algunas canciones de la cola") -- las pistas que añade la
     * Radio (H08) son transitorias, nunca llegan a tener fila en Room,
     * así que un UPDATE simple por youtubeId no tocaba ninguna fila y
     * el favorito se perdía en silencio. setFavoriteEnsuringRow() crea
     * la fila si hace falta, con los datos que ya trae el propio
     * QueueItem.
     * ---
     * Takes the full QueueItem, not just the youtubeId (S010,
     * reported by Miguel Ángel: favoriting only worked for some queue
     * tracks) -- tracks Radio (H08) adds are transient, never get a
     * Room row, so a plain UPDATE by youtubeId touched nothing and the
     * favorite silently vanished. setFavoriteEnsuringRow() creates the
     * row if needed, using the data the QueueItem already carries.
     */
    fun toggleFavorite(item: QueueItem) {
        val youtubeId = item.youtubeId ?: return
        viewModelScope.launch {
            val isFavorite = youtubeId in favoriteYoutubeIds.value
            searchResultTrackRepository.setFavoriteEnsuringRow(
                youtubeId = youtubeId,
                isFavorite = !isFavorite,
                title = item.title,
                channelTitle = item.channelTitle ?: item.title,
                artist = item.artist,
            )
        }
    }

    fun playAtIndex(index: Int) = playerManager.playAtIndex(index)

    fun removeFromQueue(index: Int) = playerManager.removeFromQueue(index)

    /**
     * Mueve una pista de `fromIndex` a `toIndex` -- usado por el
     * drag-and-drop de QueueScreen (petición explícita de Miguel
     * Ángel, 2026-07-05: "una vista de la lista donde se pueda hacer
     * drag and drop de los temas para reorganizarlos").
     * ---
     * Moves a track from `fromIndex` to `toIndex` -- used by
     * QueueScreen's drag-and-drop (explicit request from Miguel Ángel,
     * 2026-07-05: "a list view where you can drag and drop tracks to
     * reorganize them").
     */
    fun moveTo(fromIndex: Int, toIndex: Int) = playerManager.moveInQueue(fromIndex, toIndex)

    fun togglePlayPause() {
        if (playbackState.value.isPlaying) {
            playerManager.pause()
        } else {
            playerManager.resume()
        }
    }

    fun clearQueue() = playerManager.clearQueue()
}

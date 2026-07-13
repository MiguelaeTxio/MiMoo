package com.miguelaetxio.mimoo.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlaybackState
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerBarViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val searchResultTrackRepository: SearchResultTrackRepository,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = playerManager.state

    /**
     * H08 -- ExoPlayer no dispara ningún evento continuo de "la
     * posición ha cambiado" mientras suena (solo eventos puntuales:
     * cambio de pista, play/pausa...). Para la barra de progreso hace
     * falta sondear currentPositionMs() a intervalos cortos -- patrón
     * estándar en apps de reproducción con ExoPlayer/Media3, no hay
     * alternativa "reactiva" razonable para esto. 500ms es
     * suficientemente fino para que la barra se vea fluida sin
     * sondear en exceso.
     * ---
     * H08 -- ExoPlayer fires no continuous "position changed" event
     * while playing (only discrete events: track change, play/pause...).
     * The progress bar needs to poll currentPositionMs() at short
     * intervals -- standard pattern in ExoPlayer/Media3 playback apps,
     * there's no reasonable "reactive" alternative for this. 500ms is
     * fine-grained enough for the bar to look smooth without
     * over-polling.
     */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    /**
     * Favorito de la pista que está sonando ahora mismo (S010,
     * favoritos desde el reproductor -- petición explícita de Miguel
     * Ángel: "que aparezca en todos sitios"). Reutiliza
     * SearchResultTrackRepository.updateFavorite() (H03) por
     * PlaybackState.currentYoutubeId -- null si la pista actual no
     * tiene equivalente real en la biblioteca (p.ej. una emisora de
     * Radio-Browser.info, que no es un vídeo de YouTube), en cuyo caso
     * isCurrentFavorite se queda en false y la UI no debería ni
     * mostrar el botón.
     * ---
     * Favorite state of the currently playing track (S010, favorites
     * from the player). Reuses SearchResultTrackRepository (H03) by
     * PlaybackState.currentYoutubeId -- null when the current track
     * has no real library equivalent, in which case isCurrentFavorite
     * stays false and the UI shouldn't even show the button.
     */
    private val _isCurrentFavorite = MutableStateFlow(false)
    val isCurrentFavorite: StateFlow<Boolean> = _isCurrentFavorite.asStateFlow()

    /**
     * S010 -- carátula del "reproductor expandido" (petición explícita
     * de Miguel Ángel: "carátula cuadrada a la izquierda, metadatos a
     * la derecha", sin tener que tocar nada -- directamente en la
     * barra persistente, no en una pantalla aparte). Misma consulta
     * que isCurrentFavorite (SearchResultTrackRepository.getById()),
     * fusionadas en un único refresh para no duplicar la lectura de
     * Room en cada cambio de pista. Null para pistas transitorias de
     * Radio sin fila en la base de datos todavía (sin favoritar) -- la
     * UI muestra un icono genérico en ese caso, no un hueco vacío.
     * ---
     * S010 -- "expanded player" cover art. Same query as
     * isCurrentFavorite, merged into a single refresh to avoid
     * duplicating the Room read on every track change. Null for
     * transient Radio tracks with no database row yet.
     */
    private val _coverArtUrl = MutableStateFlow<String?>(null)
    val coverArtUrl: StateFlow<String?> = _coverArtUrl.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                _positionMs.value = playerManager.currentPositionMs()
                delay(500)
            }
        }
        // S010 -- flatMapLatest en vez de un simple collect+lookup de
        // un solo disparo: si la Biblioteca resuelve la carátula EN
        // SEGUNDO PLANO después de que esta pista ya haya empezado a
        // sonar (requestCoverArtIfMissing() es asíncrono), esta pista
        // sigue observando su propia fila y se entera del cambio en
        // cuanto llega -- no se queda con el valor null que tenía al
        // arrancar.
        // ---
        // S010 -- flatMapLatest instead of a simple one-shot
        // collect+lookup: if the Library resolves the cover art IN THE
        // BACKGROUND after this track has already started playing,
        // this keeps observing that row and finds out as soon as it
        // arrives -- doesn't stay stuck with the null value it had at
        // start.
        viewModelScope.launch {
            state.map { it.currentYoutubeId }
                .distinctUntilChanged()
                .flatMapLatest { youtubeId ->
                    if (youtubeId == null) {
                        flowOf(null)
                    } else {
                        searchResultTrackRepository.getByIdFlow(youtubeId)
                    }
                }
                .collect { track ->
                    _isCurrentFavorite.value = track?.isFavorite == true
                    _coverArtUrl.value = track?.coverArtUrl
                }
        }
    }

    private suspend fun refreshFavoriteState(youtubeId: String?) {
        val track = youtubeId?.let { searchResultTrackRepository.getById(it) }
        _isCurrentFavorite.value = track?.isFavorite == true
        _coverArtUrl.value = track?.coverArtUrl
    }

    fun toggleCurrentFavorite() {
        val current = state.value
        val youtubeId = current.currentYoutubeId ?: return
        val title = current.currentTitle ?: return
        viewModelScope.launch {
            searchResultTrackRepository.setFavoriteEnsuringRow(
                youtubeId = youtubeId,
                isFavorite = !_isCurrentFavorite.value,
                title = title,
                channelTitle = current.currentChannelTitle ?: title,
                artist = current.currentArtist,
            )
            refreshFavoriteState(youtubeId)
        }
    }

    fun togglePlayPause() {
        if (state.value.isPlaying) playerManager.pause() else playerManager.resume()
    }

    fun playNext() = playerManager.playNext()

    fun playPrevious() = playerManager.playPrevious()

    /** H07 PARTE 3. */
    fun toggleRepeat() = playerManager.toggleRepeatMode()

    /** H07 PARTE 3. */
    fun toggleShuffle() = playerManager.toggleShuffleMode()

    /** H08 -- arrastrar la barra de progreso. */
    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
        _positionMs.value = positionMs
    }
}

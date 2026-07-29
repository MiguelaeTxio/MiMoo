package com.miguelaetxio.mimoo.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlaybackState
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.remote.CoverArtRepository
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
import java.util.Collections
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerBarViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val searchResultTrackRepository: SearchResultTrackRepository,
    private val coverArtRepository: CoverArtRepository,
    private val downloadQueueManager: com.miguelaetxio.mimoo.data.download.DownloadQueueManager,
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

    /**
     * S011 -- botón de descarga en el reproductor expandido (petición
     * explícita de Miguel Ángel, junto con el mismo botón en la
     * notificación -- ver `MiMooPlaybackService` para el límite real
     * de huecos que tiene la notificación del sistema, que este
     * reproductor propio no sufre). `null` cuando la pista actual no
     * tiene equivalente real en la biblioteca (Radio Online, H09) --
     * la UI no debería mostrar el botón en ese caso, igual que ya hace
     * con favoritos.
     */
    private val _downloadStatus =
        MutableStateFlow<com.miguelaetxio.mimoo.data.local.entity.DownloadStatus?>(null)
    val downloadStatus: StateFlow<com.miguelaetxio.mimoo.data.local.entity.DownloadStatus?> =
        _downloadStatus.asStateFlow()

    /**
     * H12 (S018) -- artista/álbum resueltos para el menú de tres
     * puntos del reproductor ("Ver álbum"/"Ver artista", roadmap punto
     * 6). `_menuArtist`: el artista ESTRUCTURADO de la fila local si
     * existe (`track.artist`, H05) tiene prioridad; si la pista no
     * tiene fila local o esa fila no trae artista, cae al
     * `currentArtist` del propio PlaybackState (fiable para pistas
     * reproducidas desde AlbumScreen/SongScreen, que siempre lo
     * pasan); si tampoco hay nada, se intenta
     * `PlayerManager.parseArtistFromTitle()` (mismo patrón que Radio,
     * H08) sobre el título. `null` en los tres casos = sin artista
     * resoluble = la UI oculta el menú entero (no tiene sentido
     * mostrar un menú con cero opciones). `_menuAlbum`: solo viene de
     * la fila local (`track.album`) -- nunca se infiere, un álbum
     * inventado navegaría a un sitio incorrecto.
     * ---
     * H12 (S018) -- resolved artist/album for the player's three-dot
     * menu ("View album"/"View artist", roadmap point 6).
     * `_menuArtist`: the local row's STRUCTURED artist (`track.artist`,
     * H05) takes priority if it exists; if there's no local row or it
     * has no artist, falls back to PlaybackState's own `currentArtist`
     * (reliable for tracks played from AlbumScreen/SongScreen, which
     * always pass it); if there's still nothing,
     * `PlayerManager.parseArtistFromTitle()` is tried (same pattern as
     * Radio, H08) against the title. `null` in all three cases = no
     * resolvable artist = the UI hides the whole menu (no point
     * showing a menu with zero options). `_menuAlbum`: only ever comes
     * from the local row (`track.album`) -- never inferred, a made-up
     * album would navigate somewhere wrong.
     */
    private val _menuArtist = MutableStateFlow<String?>(null)
    val menuArtist: StateFlow<String?> = _menuArtist.asStateFlow()
    private val _menuAlbum = MutableStateFlow<String?>(null)
    val menuAlbum: StateFlow<String?> = _menuAlbum.asStateFlow()

    private fun resolveMenuArtist(track: com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack?): String? {
        val current = state.value
        return track?.artist?.takeIf { it.isNotBlank() }
            ?: current.currentArtist?.takeIf { it.isNotBlank() }
            ?: playerManager.parseArtistFromTitle(current.currentTitle)
    }

    /**
     * Fallo real diagnosticado leyendo el código (Miguel Ángel,
     * 2026-07-15: "a veces tiene carátula un disco pero no se ve en
     * el exoplayer"): hasta ahora este ViewModel solo LEÍA
     * `coverArtUrl` de Room de forma pasiva -- la resolución real
     * contra MusicBrainz/Cover Art Archive
     * (`CoverArtRepository.resolveCoverArtUrl()` +
     * `SearchResultTrackRepository.updateCoverArtForAlbum()`) solo se
     * disparaba desde `LibraryViewModel.requestCoverArtIfMissing()` --
     * ver LibraryScreen. Si Miguel Ángel reproducía un tema de un
     * álbum que SÍ tiene carátula real en MusicBrainz pero nunca había
     * visitado Biblioteca para ese álbum, `coverArtUrl` seguía `null`
     * en Room para siempre, y el reproductor mostraba el marcador
     * genérico aunque la carátula existiera de verdad.
     *
     * Mismo mecanismo de deduplicación que Biblioteca
     * (`coverArtRequested`, un `Set` sincronizado por artist+album,
     * vida de proceso) para no repetir la búsqueda en MusicBrainz cada
     * vez que cambia de pista dentro del mismo álbum. No hace falta
     * escribir `_coverArtUrl.value` a mano tras resolver -- el
     * `flatMapLatest` de más abajo sigue observando la misma fila de
     * Room y se entera solo en cuanto `updateCoverArtForAlbum()`
     * escribe el resultado.
     * ---
     * Real bug diagnosed by reading the code (Miguel Ángel, 2026-07-15:
     * "sometimes an album has cover art but it doesn't show in the
     * ExoPlayer"): until now this ViewModel only passively READ
     * `coverArtUrl` from Room -- the actual resolution against
     * MusicBrainz/Cover Art Archive only ever fired from
     * `LibraryViewModel.requestCoverArtIfMissing()`. If Miguel Ángel
     * played a track from an album that genuinely has real cover art
     * in MusicBrainz but had never visited the Library for that album,
     * `coverArtUrl` stayed `null` in Room forever, and the player kept
     * showing the generic placeholder even though the art really
     * existed.
     */
    private val coverArtRequested = Collections.synchronizedSet(mutableSetOf<String>())

    private fun requestCoverArtIfMissing(artist: String?, album: String?) {
        if (artist.isNullOrBlank() || album.isNullOrBlank()) return
        val key = "$artist|$album"
        if (!coverArtRequested.add(key)) return
        viewModelScope.launch {
            val url = coverArtRepository.resolveCoverArtUrl(artist, album)
            if (url != null) {
                searchResultTrackRepository.updateCoverArtForAlbum(artist, album, url)
            }
        }
    }

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
                    _downloadStatus.value = track?.downloadStatus
                    _menuArtist.value = resolveMenuArtist(track)
                    _menuAlbum.value = track?.album
                    if (track != null && track.coverArtUrl == null) {
                        requestCoverArtIfMissing(track.artist, track.album)
                    }
                }
        }
    }

    private suspend fun refreshFavoriteState(youtubeId: String?) {
        val track = youtubeId?.let { searchResultTrackRepository.getById(it) }
        _isCurrentFavorite.value = track?.isFavorite == true
        _coverArtUrl.value = track?.coverArtUrl
        _downloadStatus.value = track?.downloadStatus
        _menuArtist.value = resolveMenuArtist(track)
        _menuAlbum.value = track?.album
        if (track != null && track.coverArtUrl == null) {
            requestCoverArtIfMissing(track.artist, track.album)
        }
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

    /** S011 -- botón de descarga del reproductor expandido, mismos campos que toggleCurrentFavorite(). */
    fun downloadCurrentTrack() {
        val current = state.value
        val youtubeId = current.currentYoutubeId ?: return
        val title = current.currentTitle ?: return
        viewModelScope.launch {
            val track = searchResultTrackRepository.getById(youtubeId)
            downloadQueueManager.enqueue(
                youtubeId = youtubeId,
                title = title,
                // S025 -- el nombre del canal NO es el artista. Nunca.
                // Antes caia a `currentChannelTitle` y por ahi entraban
                // en la biblioteca carpetas como "Deep Purple Official".
                // Si no hay artista real se parte del titulo
                // ("Artista - Tema"); si tampoco, se deja el titulo y ya
                // lo corregira la reconciliacion del boton de Ajustes.
                artist = track?.artist
                    ?: com.miguelaetxio.mimoo.util.SearchNormalizer.artistFromTitle(title)
                    ?: title,
                album = track?.album,
                trackPosition = track?.trackPosition,
            )
            refreshFavoriteState(youtubeId)
        }
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

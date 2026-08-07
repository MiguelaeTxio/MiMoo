package com.miguelaetxio.mimoo.ui.favorites

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.favorites.FavoritePlaylistRow
import com.miguelaetxio.mimoo.data.favorites.FavoritesRepository
import com.miguelaetxio.mimoo.data.favorites.FavoriteTrackRow
import com.miguelaetxio.mimoo.data.favorites.PopurriDebugLogger
import com.miguelaetxio.mimoo.data.favorites.PopurriRepository
import com.miguelaetxio.mimoo.data.local.entity.FavoriteAlbum
import com.miguelaetxio.mimoo.data.local.entity.FavoriteArtist
import com.miguelaetxio.mimoo.data.local.entity.FavoriteTrack
import com.miguelaetxio.mimoo.data.local.repository.PlaylistRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.StreamResolver
import com.miguelaetxio.mimoo.ui.common.SortCriterion
import com.miguelaetxio.mimoo.ui.common.SortDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FavoritesTab { ARTISTS, ALBUMS, TRACKS, PLAYLISTS }

/** Clave de selección de álbum -- FavoriteAlbum no es Parcelable/no hace falta, basta el par (artist, album). */
data class AlbumKey(val artist: String, val album: String)

data class FavoritesUiState(
    val tab: FavoritesTab = FavoritesTab.ARTISTS,
    val artists: List<FavoriteArtist> = emptyList(),
    val albums: List<FavoriteAlbum> = emptyList(),
    val tracks: List<FavoriteTrackRow> = emptyList(),
    val playlists: List<FavoritePlaylistRow> = emptyList(),
    val selectedArtists: Set<String> = emptySet(),
    val selectedAlbums: Set<AlbumKey> = emptySet(),
    val isGeneratingPopurri: Boolean = false,
    val errorMessage: String? = null,
    val sortCriterion: SortCriterion = SortCriterion.ALPHABETICAL,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
)

/**
 * ViewModel de la pantalla unificada de Favoritos (sesión de diseño,
 * 2026-08-02): cuatro pestañas (Artistas/Álbumes/Sencillos/Listas),
 * selección múltiple con casillas para Artistas/Álbumes (generan
 * popurrí a partir de la selección), reproducción directa de TODOS
 * los sencillos favoritos sin selección, y apertura de las listas de
 * reproducción propias marcadas como favoritas.
 * ---
 * ViewModel for the unified Favorites screen (design session,
 * 2026-08-02): four tabs (Artists/Albums/Singles/Playlists), checkbox
 * multi-select for Artists/Albums (generates a popurrí from the
 * selection), direct playback of ALL favorite singles with no
 * selection needed, and opening the user's own playlists marked
 * favorite.
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val popurriRepository: PopurriRepository,
    private val playerManager: PlayerManager,
    private val autoSyncPusher: AutoSyncPusher,
    @ApplicationContext private val appContext: Context,
    private val storageManager: StorageManager,
    private val playlistRepository: PlaylistRepository,
    private val streamResolver: StreamResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            favoritesRepository.getFavoriteArtists().collect { artists ->
                _uiState.value = _uiState.value.copy(
                    artists = artists,
                    // Al desaparecer un favorito de la lista (se desmarcó desde ArtistScreen), su selección deja de tener sentido.
                    selectedArtists = _uiState.value.selectedArtists
                        .filter { name -> artists.any { it.artist == name } }
                        .toSet(),
                )
            }
        }
        viewModelScope.launch {
            favoritesRepository.getFavoriteAlbums().collect { albums ->
                _uiState.value = _uiState.value.copy(
                    albums = albums,
                    selectedAlbums = _uiState.value.selectedAlbums
                        .filter { key -> albums.any { it.artist == key.artist && it.album == key.album } }
                        .toSet(),
                )
            }
        }
        viewModelScope.launch {
            favoritesRepository.getFavoriteTracks().collect { tracks ->
                _uiState.value = _uiState.value.copy(tracks = tracks)
            }
        }
        viewModelScope.launch {
            favoritesRepository.getFavoritePlaylists().collect { playlists ->
                _uiState.value = _uiState.value.copy(playlists = playlists)
            }
        }
    }

    fun selectTab(tab: FavoritesTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    // --- Ordenación (H18, S032) -- criterio y dirección compartidos por las cuatro pestañas ---

    fun setSortCriterion(criterion: SortCriterion) {
        _uiState.value = _uiState.value.copy(sortCriterion = criterion)
    }

    fun toggleSortDirection() {
        val next = if (_uiState.value.sortDirection == SortDirection.ASCENDING) {
            SortDirection.DESCENDING
        } else {
            SortDirection.ASCENDING
        }
        _uiState.value = _uiState.value.copy(sortDirection = next)
    }

    // --- Selección (Artistas) ---

    fun toggleArtistSelection(artist: String) {
        val current = _uiState.value.selectedArtists
        _uiState.value = _uiState.value.copy(
            selectedArtists = if (artist in current) current - artist else current + artist,
        )
    }

    fun toggleSelectAllArtists() {
        val state = _uiState.value
        val allNames = state.artists.map { it.artist }.toSet()
        _uiState.value = state.copy(
            selectedArtists = if (state.selectedArtists.size == allNames.size) emptySet() else allNames,
        )
    }

    // --- Selección (Álbumes) ---

    fun toggleAlbumSelection(key: AlbumKey) {
        val current = _uiState.value.selectedAlbums
        _uiState.value = _uiState.value.copy(
            selectedAlbums = if (key in current) current - key else current + key,
        )
    }

    fun toggleSelectAllAlbums() {
        val state = _uiState.value
        val allKeys = state.albums.map { AlbumKey(it.artist, it.album) }.toSet()
        _uiState.value = state.copy(
            selectedAlbums = if (state.selectedAlbums.size == allKeys.size) emptySet() else allKeys,
        )
    }

    // --- Marcar/desmarcar favorito ---
    // Bug real (2026-08-02, ver comentario de
    // ArtistViewModel.toggleFavorite()): estas cuatro mutaciones
    // tampoco pasaban por AutoSyncPusher.

    fun removeArtistFavorite(artist: String) {
        viewModelScope.launch {
            autoSyncPusher.executeIfConnected(appContext) { favoritesRepository.toggleArtist(artist) }
        }
    }

    fun removeAlbumFavorite(artist: String, album: String) {
        viewModelScope.launch {
            autoSyncPusher.executeIfConnected(appContext) { favoritesRepository.toggleAlbum(artist, album) }
        }
    }

    fun removeTrackFavorite(row: FavoriteTrackRow) {
        viewModelScope.launch {
            autoSyncPusher.executeIfConnected(appContext) {
                favoritesRepository.toggleTrack(
                    FavoriteTrack(
                        youtubeId = row.youtubeId,
                        title = row.title,
                        artist = row.artist,
                        thumbnailUrl = row.thumbnailUrl,
                        durationSeconds = 0,
                    ),
                )
            }
        }
    }

    fun removePlaylistFavorite(playlistId: Long) {
        viewModelScope.launch {
            autoSyncPusher.executeIfConnected(appContext) { favoritesRepository.togglePlaylist(playlistId) }
        }
    }

    // --- Generación y reproducción de popurrís ---

    fun playSelectedArtists(shuffle: Boolean) {
        playArtists(_uiState.value.selectedArtists, shuffle)
    }

    /** Play/aleatorio individual de una fila de Artistas (H18, S032) -- mix de ESE artista concreto, sin mezclar con otros favoritos. */
    fun playArtist(artist: String, shuffle: Boolean) {
        playArtists(setOf(artist), shuffle)
    }

    private fun playArtists(names: Set<String>, shuffle: Boolean) {
        val selected = _uiState.value.artists.filter { it.artist in names }
        if (selected.isEmpty()) return
        generateAndPlay { popurriRepository.playArtistsProgressively(playerManager, selected, shuffle) }
    }

    fun playSelectedAlbums(shuffle: Boolean) {
        playAlbums(_uiState.value.selectedAlbums, shuffle)
    }

    /** Play/aleatorio individual de una fila de Álbumes (H18, S032) -- mismo criterio que playArtist(). */
    fun playAlbum(artist: String, album: String, shuffle: Boolean) {
        playAlbums(setOf(AlbumKey(artist, album)), shuffle)
    }

    private fun playAlbums(keys: Set<AlbumKey>, shuffle: Boolean) {
        val selected = _uiState.value.albums.filter { AlbumKey(it.artist, it.album) in keys }
        if (selected.isEmpty()) return
        generateAndPlay { popurriRepository.playAlbumsProgressively(playerManager, selected, shuffle) }
    }

    fun playAllFavoriteTracks(shuffle: Boolean) {
        generateAndPlay {
            val plan = popurriRepository.buildFromFavoriteTracks(
                favoriteTracks = pendingStreamingFavorites(),
                favoriteLocalTracks = pendingLocalFavorites(),
            )
            popurriRepository.playProgressively(playerManager, plan, shuffle)
        }
    }

    /**
     * Play individual de un sencillo (H18, S032) -- SOLO play, sin
     * aleatorio (una única pista no tiene nada que barajar, precisión
     * cerrada con Miguel Ángel). Reutiliza el mismo pipeline que
     * playAllFavoriteTracks(), acotado a esta única fila -- prioriza
     * la copia local si existe, igual que buildFromFavoriteTracks()
     * hace para el conjunto completo.
     */
    fun playTrack(row: FavoriteTrackRow) {
        generateAndPlay {
            val plan = if (row.isDownloaded) {
                popurriRepository.buildFromFavoriteTracks(
                    favoriteTracks = emptyList(),
                    favoriteLocalTracks = favoritesRepository.getFavoriteLocalTracks().first()
                        .filter { it.youtubeId == row.youtubeId },
                )
            } else {
                popurriRepository.buildFromFavoriteTracks(
                    favoriteTracks = listOf(
                        FavoriteTrack(row.youtubeId, row.title, row.artist, row.thumbnailUrl, durationSeconds = 0)
                    ),
                    favoriteLocalTracks = emptyList(),
                )
            }
            popurriRepository.playProgressively(playerManager, plan, shuffle = false)
        }
    }

    /**
     * Play/aleatorio individual de una fila de Listas (H18, S032) --
     * reproduce la playlist entera en su orden guardado, vía
     * PlaylistRepository.playPlaylistById() (misma lógica exacta que
     * PlaylistDetailViewModel.playAll(), extraída para reutilizarla
     * aquí sin duplicarla).
     */
    fun playPlaylist(playlistId: Long, shuffle: Boolean) {
        generateAndPlay {
            playlistRepository.playPlaylistById(
                playlistId = playlistId,
                shuffle = shuffle,
                playerManager = playerManager,
                streamResolver = streamResolver,
            ).started
        }
    }

    /** Los sencillos en streaming (sin fila local) de entre los favoritos unificados actuales. */
    private suspend fun pendingStreamingFavorites(): List<FavoriteTrack> =
        _uiState.value.tracks
            .filter { !it.isDownloaded }
            .map { FavoriteTrack(it.youtubeId, it.title, it.artist, it.thumbnailUrl, durationSeconds = 0) }

    private suspend fun pendingLocalFavorites() =
        favoritesRepository.getFavoriteLocalTracks().first()

    /**
     * Bug real reportado por Miguel Ángel (2026-08-02): "tarda mucho
     * en iniciar la reproducción cuando no están descargados". Y
     * segunda vuelta el mismo día: seleccionó varios artistas y no
     * sonó nada en 8 minutos -- porque `playProgressively()` solo
     * hacía progresiva la resolución de streaming, no la construcción
     * del plan en sí (ver PopurriRepository.playArtistsProgressively()).
     * Ahora `action` ya devuelve directamente si algo empezó a sonar
     * -- toda la lógica de "arrancar rápido, seguir en segundo plano"
     * vive en PopurriRepository, este ViewModel solo refleja el
     * resultado en la UI.
     * ---
     * Real bug reported by Miguel Ángel (2026-08-02): "takes a long
     * time to start playback when tracks aren't downloaded". And a
     * second round the same day: selected several artists and nothing
     * played in 8 minutes -- because `playProgressively()` only made
     * stream resolution progressive, not building the plan itself
     * (see PopurriRepository.playArtistsProgressively()). Now `action`
     * directly returns whether something started playing -- all the
     * "start fast, continue in the background" logic lives in
     * PopurriRepository, this ViewModel just reflects the result in
     * the UI.
     */
    /**
     * Bug real reportado por Miguel Ángel (2026-08-03), con captura de
     * pantalla: al generar un popurrí de 7 artistas favoritos, salió
     * en pantalla el texto pelado "timeout" -- el mensaje crudo de una
     * excepción de red (probablemente de
     * `ArtistDirectoryRepository.getAlbums()`, que no estaba envuelta
     * en un `try/catch` y tumbaba toda la construcción de colas sin
     * ningún control, ver `PopurriRepository.playArtistsProgressively()`)
     * mostrado tal cual, sin contexto ninguno.
     *
     * Ahora el mensaje que ve el usuario es SIEMPRE uno claro y
     * accionable -- el detalle real de la excepción (clase + mensaje)
     * va al registro de depuración propio de Favoritos
     * (`PopurriDebugLogger`, nuevo hoy mismo), no a la pantalla.
     * ---
     * Real bug reported by Miguel Ángel (2026-08-03), with a
     * screenshot: generating a popurrí from 7 favorite artists showed
     * the bare text "timeout" on screen -- a raw network exception
     * message (likely from `ArtistDirectoryRepository.getAlbums()`,
     * which wasn't wrapped in a `try/catch` and brought down the whole
     * queue-building process with no handling at all, see
     * `PopurriRepository.playArtistsProgressively()`) shown as-is, with
     * no context.
     *
     * Now the message the user sees is ALWAYS a clear, actionable one
     * -- the real exception detail (class + message) goes to
     * Favorites' own debug log (`PopurriDebugLogger`, new today), not
     * to the screen.
     */
    private fun generateAndPlay(action: suspend () -> Boolean) {
        // Blindaje adicional al de FavoritesScreen (botones
        // deshabilitados con isGeneratingPopurri) -- un toque muy
        // rápido puede llegar antes de que Compose repinte el botón
        // como deshabilitado. Ver el comentario de SelectionHeader().
        if (_uiState.value.isGeneratingPopurri) return
        // Petición explícita de Miguel Ángel (2026-08-03): rellenar la
        // espera real (~30s) con un sonido de apertura en vez de
        // silencio -- ver el comentario de
        // PlayerManager.playOpeningLoopIfAvailable(). No hace nada si
        // el archivo de audio todavía no existe.
        playerManager.playOpeningLoopIfAvailable(appContext)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingPopurri = true, errorMessage = null)
            try {
                val started = action()
                if (!started) {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingPopurri = false,
                        errorMessage = "No se ha podido resolver ninguna pista para este popurrí.",
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(isGeneratingPopurri = false)
            } catch (e: Exception) {
                PopurriDebugLogger.log(
                    appContext, storageManager,
                    "FavoritesViewModel.generateAndPlay() -- excepción sin capturar: " +
                        "${e.javaClass.simpleName}: ${e.message}",
                )
                _uiState.value = _uiState.value.copy(
                    isGeneratingPopurri = false,
                    errorMessage = "No se ha podido generar el popurrí. Vuelve a intentarlo.",
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

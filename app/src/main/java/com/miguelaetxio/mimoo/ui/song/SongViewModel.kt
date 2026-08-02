package com.miguelaetxio.mimoo.ui.song

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.backup.MutationOutcome
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.FavoriteTrack
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.FavoriteTrackRepository
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.StreamResolver
import com.miguelaetxio.mimoo.data.remote.ExternalLinkResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SongUiState(
    val artistName: String = "",
    val songTitle: String = "",
    val isLoading: Boolean = true,
    // Pista resuelta en YouTube (H01, coste cero) -- puede haber
    // ninguna si la búsqueda no devuelve nada usable.
    val youtubeId: String? = null,
    val youtubeChannelTitle: String? = null,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val notFound: Boolean = false,
    val errorMessage: String? = null,
    // No nulo solo si la pista ya está descargada -- el favorito de
    // pista suelta (SearchResultTrack.isFavorite) es siempre de algo
    // ya descargado (ver memoria de sesión), y el álbum solo se puede
    // ofrecer como enlace cuando se conoce localmente.
    val localAlbum: String? = null,
    val isFavorite: Boolean = false,
    val downloadStatus: DownloadStatus? = null,
    val isResolvingPlayback: Boolean = false,
)

/**
 * ViewModel de SongScreen (H12, S018): página de una pista suelta,
 * alcanzable desde un sencillo de ArtistScreen, desde una pista de
 * AlbumScreen, o desde la búsqueda unificada. A diferencia de
 * AlbumScreen (resuelto vía MusicBrainz + AlbumMatchRepository), aquí
 * la resolución es directamente contra YouTube (mismo mecanismo de
 * coste cero que SearchScreen/H01) -- una "canción" no tiene un
 * MBID de recording que resolver de forma fiable con lo que expone
 * hoy MusicBrainzApiService, y H01 ya demostró que la búsqueda libre
 * de yt-dlp es suficiente para encontrarla.
 * ---
 * SongScreen's ViewModel (H12, S018): a loose track page, reachable
 * from an ArtistScreen single, from an AlbumScreen track, or from
 * unified search. Unlike AlbumScreen (resolved via MusicBrainz +
 * AlbumMatchRepository), resolution here is directly against YouTube
 * (same zero-cost mechanism as SearchScreen/H01) -- a "song" has no
 * recording MBID that can be reliably resolved with what
 * MusicBrainzApiService exposes today, and H01 already proved free
 * yt-dlp search is enough to find it.
 */
@HiltViewModel
class SongViewModel @Inject constructor(
    private val externalLinkResolver: ExternalLinkResolver,
    private val searchResultTrackRepository: SearchResultTrackRepository,
    private val favoriteTrackRepository: FavoriteTrackRepository,
    private val downloadQueueManager: DownloadQueueManager,
    private val streamResolver: StreamResolver,
    private val playerManager: PlayerManager,
    private val autoSyncPusher: AutoSyncPusher,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val artistName: String =
        checkNotNull(savedStateHandle.get<String>("artistName")) {
            "SongViewModel requires an artistName nav argument"
        }
    private val songTitle: String =
        checkNotNull(savedStateHandle.get<String>("songTitle")) {
            "SongViewModel requires a songTitle nav argument"
        }

    private val _uiState = MutableStateFlow(
        SongUiState(artistName = artistName, songTitle = songTitle),
    )
    val uiState: StateFlow<SongUiState> = _uiState.asStateFlow()

    init {
        resolve()
    }

    private fun resolve() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val results = externalLinkResolver
                    .searchYoutube("$artistName $songTitle", limit = 5)
                    .tracks
                val best = results.firstOrNull()
                if (best == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, notFound = true)
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    youtubeId = best.youtubeId,
                    youtubeChannelTitle = best.channelTitle,
                    durationSeconds = best.durationSeconds,
                    thumbnailUrl = best.thumbnailUrl,
                )
                refreshLocalState(best.youtubeId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Error al buscar la pista",
                )
            }
        }
    }

    /**
     * Cruza la pista resuelta con search_result_tracks -- si ya está
     * descargada, expone su álbum (para el enlace "Ver álbum") y su
     * favorito ya existentes; si no, ambos quedan sin valor (null/
     * false), nunca se inventa un álbum ni se ofrece favorito para
     * algo que no existe todavía localmente.
     * ---
     * Cross-references the resolved track with search_result_tracks --
     * if it's already downloaded, exposes its existing album (for the
     * "View album" link) and favorite; if not, both stay unset (null/
     * false), never inventing an album or offering favorite for
     * something that doesn't exist locally yet.
     */
    /**
     * Cruza la pista resuelta con search_result_tracks -- si ya está
     * descargada, expone su álbum (para el enlace "Ver álbum") y su
     * favorito local ya existentes. Si NO está descargada, el
     * favorito se consulta en FavoriteTrackRepository (streaming,
     * sesión de diseño de Favoritos 2026-08-02) en vez de quedar
     * siempre en false -- localAlbum sigue sin valor porque solo se
     * conoce una vez descargada.
     * ---
     * Cross-references the resolved track with search_result_tracks --
     * if it's already downloaded, exposes its existing album (for the
     * "View album" link) and its existing local favorite. If it's NOT
     * downloaded, favorite is looked up in FavoriteTrackRepository
     * (streaming, Favorites design session 2026-08-02) instead of
     * always staying false -- localAlbum stays unset either way since
     * it's only known once downloaded.
     */
    private suspend fun refreshLocalState(youtubeId: String) {
        val local = searchResultTrackRepository.getById(youtubeId)
        val isFavorite = local?.isFavorite ?: favoriteTrackRepository.isFavorite(youtubeId)
        _uiState.value = _uiState.value.copy(
            localAlbum = local?.album,
            isFavorite = isFavorite,
            downloadStatus = local?.downloadStatus,
        )
    }

    /**
     * Favorito en streaming (sesión de diseño de Favoritos,
     * 2026-08-02): si ya hay fila local, sigue usando
     * SearchResultTrackRepository (sin cambios); si no, alterna en
     * FavoriteTrackRepository -- botón ya no se oculta cuando la
     * pista no está descargada, ver SongScreen.
     * ---
     * Streaming favorite (Favorites design session, 2026-08-02): if a
     * local row already exists, still uses SearchResultTrackRepository
     * (unchanged); otherwise toggles FavoriteTrackRepository -- the
     * button no longer hides when the track isn't downloaded, see
     * SongScreen.
     */
    /** Bug real (2026-08-02, ver comentario de ArtistViewModel.toggleFavorite()): pasa ahora por AutoSyncPusher. */
    fun toggleFavorite() {
        val youtubeId = _uiState.value.youtubeId ?: return
        val state = _uiState.value
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(appContext) {
                if (state.downloadStatus != null) {
                    searchResultTrackRepository.updateFavorite(youtubeId, !state.isFavorite)
                } else {
                    favoriteTrackRepository.toggle(
                        FavoriteTrack(
                            youtubeId = youtubeId,
                            title = songTitle,
                            artist = artistName,
                            thumbnailUrl = state.thumbnailUrl,
                            durationSeconds = state.durationSeconds ?: 0,
                        ),
                    )
                }
            }
            if (outcome is MutationOutcome.Success) {
                _uiState.value = _uiState.value.copy(isFavorite = !_uiState.value.isFavorite)
            }
        }
    }

    /** Reproduce -- local si ya está descargada, streaming si no, mismo criterio que SearchViewModel.playTrack(). */
    fun play() {
        val state = _uiState.value
        val youtubeId = state.youtubeId ?: return

        viewModelScope.launch {
            val local = searchResultTrackRepository.getById(youtubeId)
            if (local?.downloadStatus == DownloadStatus.DONE && local.filePath != null) {
                playerManager.play(
                    local.filePath,
                    local.title,
                    isLocal = true,
                    artist = local.artist ?: local.channelTitle,
                    youtubeId = youtubeId,
                    channelTitle = local.channelTitle,
                    artworkUri = local.coverArtUrl ?: local.thumbnailUrl,
                )
                return@launch
            }

            _uiState.value = state.copy(isResolvingPlayback = true, errorMessage = null)
            try {
                val streamUrl = streamResolver.resolveAudioStreamUrl("https://youtu.be/$youtubeId")
                playerManager.play(
                    streamUrl,
                    songTitle,
                    isLocal = false,
                    artist = artistName,
                    youtubeId = youtubeId,
                    channelTitle = state.youtubeChannelTitle,
                    artworkUri = state.thumbnailUrl,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Error al resolver el stream",
                )
            } finally {
                _uiState.value = _uiState.value.copy(isResolvingPlayback = false)
            }
        }
    }

    /**
     * Descarga como sencillo suelto (álbum = null, igual que
     * "Importar enlace" y SearchScreen para pistas sin álbum
     * conocido) -- mismo mecanismo de dos pasos que
     * AlbumViewModel.cacheAndEnqueue().
     * ---
     * Downloads as a loose single (album = null, same as "Importar
     * enlace" and SearchScreen for tracks with no known album) -- same
     * two-step mechanism as AlbumViewModel.cacheAndEnqueue().
     */
    fun download() {
        val state = _uiState.value
        val youtubeId = state.youtubeId ?: return

        viewModelScope.launch {
            searchResultTrackRepository.cacheSearchResults(
                listOf(
                    SearchResultTrack(
                        youtubeId = youtubeId,
                        title = songTitle,
                        channelTitle = state.youtubeChannelTitle ?: artistName,
                        durationSeconds = state.durationSeconds ?: 0,
                        thumbnailUrl = state.thumbnailUrl,
                        artist = artistName,
                        album = null,
                    ),
                ),
            )
            downloadQueueManager.enqueue(
                youtubeId = youtubeId,
                title = songTitle,
                artist = artistName,
            )
            refreshLocalState(youtubeId)
        }
    }
}

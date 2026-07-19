package com.miguelaetxio.mimoo.ui.unifiedsearch

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.backup.MutationOutcome
import com.miguelaetxio.mimoo.data.local.repository.ChannelSubscriptionRepository
import com.miguelaetxio.mimoo.data.remote.AlbumCandidate
import com.miguelaetxio.mimoo.data.remote.AlbumMatchRepository
import com.miguelaetxio.mimoo.data.remote.ArtistDirectoryRepository
import com.miguelaetxio.mimoo.data.remote.ExternalLinkResolver
import com.miguelaetxio.mimoo.data.remote.SearchResultType
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzArtistSummary
import com.miguelaetxio.mimoo.data.remote.dto.SearchTypeResult
import com.miguelaetxio.mimoo.data.remote.dto.TrackDto
import com.miguelaetxio.mimoo.util.YoutubeTitleCleaner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tipo de resultado para restringir la VISTA (S018, rediseño de la
 * búsqueda unificada): "lanza la búsqueda, presenta unos resultados,
 * toco álbum, restringe la búsqueda a los álbumes" -- las cinco
 * fuentes siguen buscando TODAS en paralelo en search() (sin
 * cambios), esto solo decide qué secciones se pintan en pantalla.
 * ---
 * Result type to restrict the VIEW (S018, unified search redesign):
 * "run the search, show results, tap album, restrict the view to
 * albums" -- all five sources still search in parallel in search()
 * (unchanged), this only decides which sections get painted on
 * screen.
 */
enum class SearchResultKind { SONG, ALBUM, ARTIST, PLAYLIST, CHANNEL }

data class UnifiedSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
    val songs: List<TrackDto> = emptyList(),
    val albums: List<AlbumCandidate> = emptyList(),
    val artists: List<MusicBrainzArtistSummary> = emptyList(),
    val playlists: List<SearchTypeResult> = emptyList(),
    val channels: List<SearchTypeResult> = emptyList(),
    val syncBlockedMessage: String? = null,
    // null = sin restringir, se ven las cinco secciones (S018).
    val activeFilter: SearchResultKind? = null,
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() &&
            playlists.isEmpty() && channels.isEmpty()
}

/**
 * ViewModel de la búsqueda unificada (H12, bloque 5, S018): sustituye
 * a SearchScreen (H01) y AlbumSearchScreen (H05) como único punto de
 * entrada de búsqueda del catálogo online. Una sola query se reparte
 * a las fuentes que ya existían -- YouTube vía ExternalLinkResolver
 * (sencillos, listas, canales) y MusicBrainz vía AlbumMatchRepository/
 * ArtistDirectoryRepository (álbumes, artistas) -- en paralelo, cada
 * una con su propio try/catch para que el fallo de una fuente no
 * tumbe las demás (una búsqueda filtrada de listas/canales es zona
 * menos estable de yt-dlp, ver ExternalLinkResolver.searchByType()).
 *
 * Cambio deliberado respecto al SearchViewModel antiguo: los
 * resultados de tipo canción ya NO se cachean en search_result_tracks
 * en el momento de buscar -- solo se muestran como TrackDto. La
 * pantalla nueva (SongScreen) resuelve la pista otra vez al entrar,
 * así que cachear aquí sería un efecto secundario innecesario en cada
 * tecleo de búsqueda (a diferencia de H01 original, donde el propio
 * resultado de búsqueda YA era la fila reproducible/descargable).
 * ---
 * Unified search's ViewModel (H12, block 5, S018): replaces
 * SearchScreen (H01) and AlbumSearchScreen (H05) as the sole entry
 * point for catalog search. One query is distributed to the sources
 * that already existed -- YouTube via ExternalLinkResolver (singles,
 * playlists, channels) and MusicBrainz via AlbumMatchRepository/
 * ArtistDirectoryRepository (albums, artists) -- in parallel, each
 * with its own try/catch so one source's failure doesn't take down
 * the rest (type-filtered playlist/channel search is a less stable
 * area of yt-dlp, see ExternalLinkResolver.searchByType()).
 *
 * Deliberate change from the old SearchViewModel: song results are no
 * longer cached into search_result_tracks at search time -- they're
 * only shown as TrackDto. The new screen (SongScreen) resolves the
 * track again on entry, so caching here would be an unneeded side
 * effect on every search keystroke (unlike original H01, where the
 * search result itself WAS the playable/downloadable row).
 */
@HiltViewModel
class UnifiedSearchViewModel @Inject constructor(
    private val externalLinkResolver: ExternalLinkResolver,
    private val albumMatchRepository: AlbumMatchRepository,
    private val artistDirectoryRepository: ArtistDirectoryRepository,
    private val channelSubscriptionRepository: ChannelSubscriptionRepository,
    private val autoSyncPusher: AutoSyncPusher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnifiedSearchUiState())
    val uiState: StateFlow<UnifiedSearchUiState> = _uiState.asStateFlow()

    /** Mismo patrón que SearchViewModel.subscribedChannelIds (H11, S011). */
    val subscribedChannelIds: StateFlow<List<String>> =
        channelSubscriptionRepository.getAllChannelIds()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    /** Toggle: tocar el chip ya activo lo desactiva (vuelve a ver las cinco secciones). */
    fun setFilter(kind: SearchResultKind) {
        val current = _uiState.value.activeFilter
        _uiState.value = _uiState.value.copy(activeFilter = if (current == kind) null else kind)
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                hasSearched = true,
                errorMessage = null,
            )
            coroutineScope {
                val songsDeferred = async { searchSongs(query) }
                val albumsDeferred = async { searchAlbums(query) }
                val artistsDeferred = async { searchArtists(query) }
                val playlistsDeferred = async { searchType(query, SearchResultType.PLAYLIST) }
                val channelsDeferred = async { searchType(query, SearchResultType.CHANNEL) }

                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    songs = songsDeferred.await(),
                    albums = albumsDeferred.await(),
                    artists = artistsDeferred.await(),
                    playlists = playlistsDeferred.await(),
                    channels = channelsDeferred.await(),
                )
            }
        }
    }

    private suspend fun searchSongs(query: String): List<TrackDto> =
        try {
            externalLinkResolver.searchYoutube(query, limit = 10).tracks.map { entry ->
                TrackDto(
                    youtubeId = entry.youtubeId,
                    title = YoutubeTitleCleaner.clean(entry.title),
                    durationSeconds = entry.durationSeconds,
                    thumbnailUrl = entry.thumbnailUrl,
                    channelTitle = entry.channelTitle,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }

    private suspend fun searchAlbums(query: String): List<AlbumCandidate> =
        try {
            albumMatchRepository.searchAlbumCandidates(artist = null, album = query).take(10)
        } catch (e: Exception) {
            emptyList()
        }

    private suspend fun searchArtists(query: String): List<MusicBrainzArtistSummary> =
        try {
            artistDirectoryRepository.searchArtistsByQuery(query)
        } catch (e: Exception) {
            emptyList()
        }

    private suspend fun searchType(query: String, type: SearchResultType): List<SearchTypeResult> =
        try {
            externalLinkResolver.searchByType(query, type)
        } catch (e: Exception) {
            emptyList()
        }

    /** Mismo patrón que SearchViewModel.toggleChannelSubscription() (H07 PARTE 1, S015). */
    fun toggleChannelSubscription(activity: Activity, result: SearchTypeResult) {
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(activity) {
                channelSubscriptionRepository.toggle(result)
            }
            if (outcome is MutationOutcome.NoConnection) {
                _uiState.value = _uiState.value.copy(
                    syncBlockedMessage = "Sin conexión: no se puede cambiar la suscripción ahora mismo.",
                )
            }
        }
    }

    fun dismissSyncBlockedMessage() {
        _uiState.value = _uiState.value.copy(syncBlockedMessage = null)
    }
}

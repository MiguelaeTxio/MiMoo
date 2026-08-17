package com.miguelaetxio.mimoo.ui.explorer

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.backup.MutationOutcome
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.repository.ChannelSubscriptionRepository
import com.miguelaetxio.mimoo.data.local.repository.DislikedArtistRepository
import com.miguelaetxio.mimoo.data.local.repository.FavoriteArtistRepository
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.remote.AlbumCandidate
import com.miguelaetxio.mimoo.data.remote.AlbumMatchRepository
import com.miguelaetxio.mimoo.data.remote.ArtistDirectoryRepository
import com.miguelaetxio.mimoo.data.remote.ExternalLinkResolver
import com.miguelaetxio.mimoo.data.remote.SearchResultType
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzArtistSummary
import com.miguelaetxio.mimoo.data.remote.dto.SearchTypeResult
import com.miguelaetxio.mimoo.data.remote.dto.TrackDto
import com.miguelaetxio.mimoo.ui.library.sortLetterFor
import com.miguelaetxio.mimoo.ui.unifiedsearch.SearchResultKind
import com.miguelaetxio.mimoo.util.SearchNormalizer
import com.miguelaetxio.mimoo.util.YoutubeTitleCleaner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * REDISEÑO S018 (feedback en dispositivo de Miguel Ángel, 2026-07-19):
 * la primera versión de Explorador (solo FavoriteArtist) estaba mal
 * planteada -- "el explorador es el total... debe aparecer todo".
 * Explorador ya NO tiene nivel de "artistas favoritos" propio; cada
 * letra muestra DOS bloques: lo que ya tienes descargado (local,
 * completo, sin paginar) y una muestra paginada de MusicBrainz
 * (scroll infinito), sin repetir lo que ya salió en el bloque local.
 * ---
 * S018 REDESIGN (Miguel Ángel's on-device feedback, 2026-07-19): the
 * first Explorer version (favorite-artists-only) was the wrong shape
 * -- "Explorer is the total... everything should show up". Explorer
 * no longer has its own "favorite artists" level; each letter shows
 * TWO blocks: what you already have downloaded (local, complete, not
 * paginated) and a paginated MusicBrainz sample (infinite scroll),
 * without repeating what already showed up in the local block.
 */
sealed class ExplorerDrillLevel {
    object Letters : ExplorerDrillLevel()
    data class Artists(val letter: Char) : ExplorerDrillLevel()
}

data class ExplorerUiState(
    val drill: ExplorerDrillLevel = ExplorerDrillLevel.Letters,
    // Fijas A-Z -- a diferencia de Biblioteca, que solo muestra letras
    // con contenido local, MusicBrainz "tiene de todo" así que las 26
    // letras son siempre explorables.
    val letters: List<Char> = ('A'..'Z').toList(),
    val localArtistsForLetter: List<String> = emptyList(),
    val onlineArtists: List<MusicBrainzArtistSummary> = emptyList(),
    val isLoadingOnline: Boolean = false,
    val hasMoreOnline: Boolean = true,
    val errorMessage: String? = null,
    /** H16 -- claves normalizadas (SearchNormalizer.normalizeArtistName()) de artistas en la Lista Negra, para pintar el icono de cada fila. */
    val dislikedArtistKeys: Set<String> = emptySet(),
    // --- Búsqueda unificada embebida (S034, incidencia real de
    // S033: "el explorador carece de campo búsqueda para buscar en
    // musicbrainz"). Mismos campos y misma lógica que
    // UnifiedSearchViewModel (H12, S018) -- se dispara la búsqueda
    // sin salir del Explorador, en vez de navegar a la pantalla
    // "Búsqueda" aparte. Cuando hasSearched es true y la query no
    // está vacía, la pantalla sustituye el contenido de letras/
    // artistas por los resultados de búsqueda; al vaciar la query
    // vuelve al drill normal (ver ExplorerScreen).
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val searchErrorMessage: String? = null,
    val searchSongs: List<TrackDto> = emptyList(),
    val searchAlbums: List<AlbumCandidate> = emptyList(),
    val searchArtists: List<MusicBrainzArtistSummary> = emptyList(),
    val searchPlaylists: List<SearchTypeResult> = emptyList(),
    val searchChannels: List<SearchTypeResult> = emptyList(),
    val searchSyncBlockedMessage: String? = null,
    val searchActiveFilter: SearchResultKind? = null,
) {
    val isSearchActive: Boolean get() = hasSearched && searchQuery.isNotBlank()
    val isSearchEmpty: Boolean
        get() = searchSongs.isEmpty() && searchAlbums.isEmpty() && searchArtists.isEmpty() &&
            searchPlaylists.isEmpty() && searchChannels.isEmpty()
}

/**
 * ViewModel de Explorador (H12, S018 rediseño): "Biblioteca pero de
 * MusicBrainz", ahora de verdad -- letra -> lo que ya tienes de esa
 * letra (local) + muestra paginada de MusicBrainz de esa letra
 * (online, scroll infinito). Sigue sin reutilizar LibraryViewModel
 * (SAF, descarga, edición, fusión de carpetas no aplican aquí); lo
 * único compartido con Biblioteca son `sortLetterFor()` y
 * `LetterGrid` (composable), como ya se acordó.
 * ---
 * Explorer's ViewModel (H12, S018 redesign): "Library but for
 * MusicBrainz", for real this time -- letter -> what you already have
 * for that letter (local) + a paginated MusicBrainz sample for that
 * letter (online, infinite scroll). Still doesn't reuse
 * LibraryViewModel (SAF, download, editing, folder merging don't
 * apply here); the only things shared with Biblioteca are
 * `sortLetterFor()` and the `LetterGrid` composable, as already
 * agreed.
 */
@HiltViewModel
class ExplorerViewModel @Inject constructor(
    private val artistDirectoryRepository: ArtistDirectoryRepository,
    private val searchResultTrackRepository: SearchResultTrackRepository,
    // H16 -- acción "no me gusta" en cada fila de artista (roadmap
    // punto 5). Ver ANNEX_H16.md, "Contexto técnico", punto
    // "ExplorerScreen.kt/ExplorerViewModel.kt".
    private val dislikedArtistRepository: DislikedArtistRepository,
    private val favoriteArtistRepository: FavoriteArtistRepository,
    private val autoSyncPusher: AutoSyncPusher,
    // --- Búsqueda unificada embebida (S034) -- mismos repositorios
    // que UnifiedSearchViewModel, artistDirectoryRepository ya estaba
    // inyectado arriba y se reutiliza también aquí.
    private val externalLinkResolver: ExternalLinkResolver,
    private val albumMatchRepository: AlbumMatchRepository,
    private val channelSubscriptionRepository: ChannelSubscriptionRepository,
    @ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 20
    }

    /** Mismo patrón que UnifiedSearchViewModel.subscribedChannelIds (H11, S011). */
    val subscribedChannelIds: StateFlow<List<String>> =
        channelSubscriptionRepository.getAllChannelIds()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Todos los artistas con algo DESCARGADO (DownloadStatus.DONE) -- mismo filtro que Biblioteca. */
    private var allLocalArtists: List<String> = emptyList()
    private var onlineOffset = 0

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            searchResultTrackRepository.getByStatus(DownloadStatus.DONE).collect { tracks ->
                allLocalArtists = tracks
                    .map { it.artist ?: it.channelTitle }
                    .distinct()
                    .sorted()
                val currentDrill = _uiState.value.drill
                if (currentDrill is ExplorerDrillLevel.Artists) {
                    _uiState.value = _uiState.value.copy(
                        localArtistsForLetter = localArtistsFor(currentDrill.letter),
                    )
                }
            }
        }
        // H16 -- observado en vivo (no un snapshot puntual): si se
        // marca/desmarca desde otro sitio (ExoPlayer, pantalla CRUD)
        // mientras el Explorador está abierto, los iconos de fila se
        // actualizan solos.
        viewModelScope.launch {
            dislikedArtistRepository.getAll().collect { disliked ->
                _uiState.value = _uiState.value.copy(
                    dislikedArtistKeys = disliked.map { SearchNormalizer.normalizeArtistName(it.artist) }.toSet(),
                )
            }
        }
    }

    private fun isArtistDisliked(artist: String): Boolean =
        SearchNormalizer.normalizeArtistName(artist) in _uiState.value.dislikedArtistKeys

    /**
     * H16 -- acción "no me gusta" de una fila de artista del
     * Explorador. Alterna: añade si no estaba, quita si ya estaba
     * (mismo criterio de icono ON/OFF que el resto de la app).
     * Exclusión mutua con Favoritos, mismo criterio que el botón del
     * ExoPlayer -- ver ANNEX_H16.md, "Puntos de diseño -- CERRADOS",
     * punto 2.
     */
    fun toggleArtistDisliked(artist: String) {
        viewModelScope.launch {
            autoSyncPusher.executeIfConnected(appContext) {
                if (isArtistDisliked(artist)) {
                    dislikedArtistRepository.remove(artist)
                } else {
                    dislikedArtistRepository.add(artist)
                    if (favoriteArtistRepository.isFavorite(artist)) {
                        favoriteArtistRepository.toggle(artist)
                    }
                }
            }
        }
    }

    private fun localArtistsFor(letter: Char): List<String> =
        allLocalArtists.filter { sortLetterFor(it) == letter }

    fun selectLetter(letter: Char) {
        onlineOffset = 0
        _uiState.value = _uiState.value.copy(
            drill = ExplorerDrillLevel.Artists(letter),
            localArtistsForLetter = localArtistsFor(letter),
            onlineArtists = emptyList(),
            hasMoreOnline = true,
            errorMessage = null,
        )
        loadMoreOnline()
    }

    fun backToLetters() {
        _uiState.value = _uiState.value.copy(
            drill = ExplorerDrillLevel.Letters,
            localArtistsForLetter = emptyList(),
            onlineArtists = emptyList(),
        )
    }

    /**
     * Pide la siguiente página de MusicBrainz para la letra activa --
     * llamado tanto al entrar en la letra como al acercarse al final
     * del scroll (ver ExplorerScreen). Descarta duplicados con el
     * bloque local (mismo criterio de nombre normalizado que el resto
     * de H12) para no mostrar dos veces el mismo artista.
     * ---
     * Requests the next MusicBrainz page for the active letter --
     * called both on entering the letter and when nearing the end of
     * the scroll (see ExplorerScreen). Discards duplicates against the
     * local block (same normalized-name criterion as the rest of H12)
     * to avoid showing the same artist twice.
     */
    fun loadMoreOnline() {
        val drill = _uiState.value.drill
        if (drill !is ExplorerDrillLevel.Artists) return
        if (_uiState.value.isLoadingOnline || !_uiState.value.hasMoreOnline) return

        val letter = drill.letter
        val requestedOffset = onlineOffset
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingOnline = true, errorMessage = null)
            try {
                val page = artistDirectoryRepository.browseArtistsByLetter(
                    letter = letter,
                    offset = requestedOffset,
                )
                // La letra pudo haber cambiado mientras esta llamada
                // estaba en vuelo (usuario tocó otra letra rápido) --
                // si ya no coincide, se descarta el resultado.
                if ((_uiState.value.drill as? ExplorerDrillLevel.Artists)?.letter != letter) return@launch

                val existingKeys = (_uiState.value.localArtistsForLetter + _uiState.value.onlineArtists.map { it.name })
                    .map { SearchNormalizer.normalizeArtistName(it) }
                    .toSet()
                val newArtists = page.filter {
                    SearchNormalizer.normalizeArtistName(it.name) !in existingKeys
                }

                onlineOffset = requestedOffset + PAGE_SIZE
                _uiState.value = _uiState.value.copy(
                    isLoadingOnline = false,
                    onlineArtists = _uiState.value.onlineArtists + newArtists,
                    hasMoreOnline = page.size == PAGE_SIZE,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingOnline = false,
                    errorMessage = e.message ?: "Error al cargar más artistas",
                )
            }
        }
    }

    // ============================================================
    // BÚSQUEDA UNIFICADA EMBEBIDA (S034, MiMoo-S34H12)
    // Mismo motor y mismo comportamiento que UnifiedSearchViewModel
    // (H12, S018) -- cinco fuentes en paralelo, cada una con su
    // propio try/catch, filtro de vista por tipo. Duplicado a
    // propósito en vez de compartir clase: mismo criterio que el
    // resto del proyecto para pantallas que reutilizan un motor ya
    // construido (ANNEX_H12.md, "reutiliza el motor de H08 ya
    // construido", mismo patrón para Explorador reutilizando el
    // motor de búsqueda de H12).
    // ============================================================

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    /** Vacía la búsqueda y vuelve al drill normal (letras/artistas). */
    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            hasSearched = false,
            searchErrorMessage = null,
            searchSongs = emptyList(),
            searchAlbums = emptyList(),
            searchArtists = emptyList(),
            searchPlaylists = emptyList(),
            searchChannels = emptyList(),
            searchActiveFilter = null,
        )
    }

    fun setSearchFilter(kind: SearchResultKind) {
        val current = _uiState.value.searchActiveFilter
        _uiState.value = _uiState.value.copy(searchActiveFilter = if (current == kind) null else kind)
    }

    fun search() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                hasSearched = true,
                searchErrorMessage = null,
            )
            coroutineScope {
                val songsDeferred = async { searchSongs(query) }
                val albumsDeferred = async { searchAlbums(query) }
                val artistsDeferred = async { searchArtists(query) }
                val playlistsDeferred = async { searchType(query, SearchResultType.PLAYLIST) }
                val channelsDeferred = async { searchType(query, SearchResultType.CHANNEL) }

                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchSongs = songsDeferred.await(),
                    searchAlbums = albumsDeferred.await(),
                    searchArtists = artistsDeferred.await(),
                    searchPlaylists = playlistsDeferred.await(),
                    searchChannels = channelsDeferred.await(),
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

    /** Mismo patrón que UnifiedSearchViewModel.toggleChannelSubscription() (H07 PARTE 1, S015). */
    fun toggleSearchChannelSubscription(activity: Activity, result: SearchTypeResult) {
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(activity) {
                channelSubscriptionRepository.toggle(result)
            }
            if (outcome is MutationOutcome.NoConnection) {
                _uiState.value = _uiState.value.copy(
                    searchSyncBlockedMessage = "Sin conexión: no se puede cambiar la suscripción ahora mismo.",
                )
            }
        }
    }

    fun dismissSearchSyncBlockedMessage() {
        _uiState.value = _uiState.value.copy(searchSyncBlockedMessage = null)
    }
}

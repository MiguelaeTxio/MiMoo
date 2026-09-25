package com.miguelaetxio.mimoo.ui.playlist

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.backup.MutationOutcome
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.PlaylistRepository
import com.miguelaetxio.mimoo.data.local.repository.PlaylistTrackInput
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.StreamResolver
import com.miguelaetxio.mimoo.util.SearchNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val tracks: List<SearchResultTrack> = emptyList(),
    val isResolving: Boolean = false,
    val resolveError: String? = null,
    // H07 PARTE 1 -- aviso cuando quitar una pista se rechaza por falta de conexión.
    val syncBlockedMessage: String? = null,
    // S037 (H04) -- edit mode, always active on entering the list.
    // ---
    // S037 (H04) -- edición, activa siempre al entrar en la lista.
    val filterQuery: String = "",
    val visibleTracks: List<SearchResultTrack> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val playError: String? = null,
)

/**
 * ViewModel for a single playlist's detail screen (Hito 04): ordered
 * track list, remove, reorder (up/down), play all.
 *
 * playlistId comes from navigation arguments rather than the
 * constructor directly — SavedStateHandle is the standard Hilt+Nav
 * Compose pattern for that, but is intentionally left for the actual
 * NavGraph wiring step rather than guessed here; this constructor
 * signature may need adjusting once Screen.PlaylistDetail's argument
 * name is fixed in NavGraph.kt.
 * ---
 * ViewModel de la pantalla de detalle de una playlist (Hito 04):
 * lista de pistas en orden, quitar, reordenar (subir/bajar),
 * reproducir todo.
 *
 * playlistId llega de los argumentos de navegación en vez de ir
 * directo al constructor — SavedStateHandle es el patrón estándar de
 * Hilt+Nav Compose para eso, pero se deja intencionadamente para el
 * propio paso de conexión de NavGraph en vez de adivinarlo aquí; esta
 * firma de constructor puede necesitar ajuste en cuanto se fije el
 * nombre del argumento de Screen.PlaylistDetail en NavGraph.kt.
 */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val playerManager: PlayerManager,
    private val streamResolver: StreamResolver,
    private val autoSyncPusher: AutoSyncPusher,
    private val shareCodeRepository: com.miguelaetxio.mimoo.data.share.ShareCodeRepository,
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    private val playlistId: Long =
        checkNotNull(savedStateHandle.get<Long>("playlistId")) {
            "PlaylistDetailViewModel requires a playlistId nav argument"
        }

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    /** H10 (S011, niveles 7/8) -- Uri del archivo .txt de esta lista, mismo patrón que Ajustes/Biblioteca. */
    private val _generatedShareFileUri = MutableStateFlow<android.net.Uri?>(null)
    val generatedShareFileUri: StateFlow<android.net.Uri?> = _generatedShareFileUri.asStateFlow()

    fun shareReplica() {
        viewModelScope.launch {
            _generatedShareFileUri.value = shareCodeRepository.buildPlaylistShareFile(playlistId)
        }
    }

    fun consumeGeneratedShareFileUri() {
        _generatedShareFileUri.value = null
    }

    init {
        viewModelScope.launch {
            repository.getTracksForPlaylist(playlistId).collect { tracks ->
                val current = _uiState.value
                val ids = tracks.map { it.youtubeId }.toSet()
                _uiState.value = current.copy(
                    tracks = tracks,
                    visibleTracks = applyFilter(tracks, current.filterQuery),
                    selectedIds = current.selectedIds.intersect(ids),
                )
            }
        }
    }

    fun removeTrack(activity: Activity, youtubeId: String) {
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(activity) {
                repository.removeTrackFromPlaylist(playlistId, youtubeId)
            }
            if (outcome is MutationOutcome.NoConnection) {
                _uiState.value = _uiState.value.copy(
                    syncBlockedMessage = "Sin conexión: no se puede quitar la pista ahora mismo."
                )
            }
        }
    }

    /**
     * S037 (H04) -- text filter over the list. Matches when the query
     * appears in the file name or in any metadata of the track (title,
     * artist, album), accent/case/punctuation-insensitive via
     * SearchNormalizer. Channel names are never used (binding rule:
     * `channelTitle` is ignored everywhere). Explicit request from
     * Miguel Ángel: "si busco loquillo me presenta una lista con todos
     * los temas donde aparece loquillo en el nombre del archivo o en
     * cualquier metadato".
     * ---
     * S037 (H04) -- filtro de texto sobre la lista. Coincide si el texto
     * aparece en el nombre del archivo o en cualquier metadato de la
     * pista (título, artista, álbum), sin distinguir acentos,
     * mayúsculas ni puntuación (SearchNormalizer). Nunca usa el nombre
     * del canal (regla vinculante: `channelTitle` se ignora en todo).
     */
    fun onFilterChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            filterQuery = query,
            visibleTracks = applyFilter(_uiState.value.tracks, query),
        )
    }

    private fun applyFilter(tracks: List<SearchResultTrack>, query: String): List<SearchResultTrack> {
        val needle = SearchNormalizer.normalize(query)
        if (needle.isEmpty()) return tracks
        return tracks.filter { track ->
            val fileName = track.filePath?.let { Uri.decode(it).substringAfterLast('/') }
            listOfNotNull(track.title, track.artist, track.album, fileName)
                .any { SearchNormalizer.normalize(it).contains(needle) }
        }
    }

    /**
     * S037 (H04) -- checkbox selection of one row.
     * ---
     * S037 (H04) -- selección por casillero de una fila.
     */
    fun toggleSelection(youtubeId: String) {
        val current = _uiState.value.selectedIds
        _uiState.value = _uiState.value.copy(
            selectedIds = if (youtubeId in current) current - youtubeId else current + youtubeId,
        )
    }

    /**
     * S037 (H04) -- removes every selected track from THIS list only
     * (the downloaded file stays in the library), in one connectivity
     * check, same H07 rule as the single remove.
     * ---
     * S037 (H04) -- quita todas las pistas seleccionadas SOLO de esta
     * lista (el archivo descargado sigue en la biblioteca), con una
     * única comprobación de conexión, misma regla H07 que el borrado
     * individual.
     */
    fun removeSelected(activity: Activity) {
        val ids = _uiState.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(activity) {
                ids.forEach { repository.removeTrackFromPlaylist(playlistId, it) }
            }
            if (outcome is MutationOutcome.NoConnection) {
                _uiState.value = _uiState.value.copy(
                    syncBlockedMessage = "Sin conexión: no se pueden quitar las pistas ahora mismo."
                )
            } else {
                _uiState.value = _uiState.value.copy(selectedIds = emptySet())
            }
        }
    }

    /**
     * S037 (H04) -- selected tracks in list order, as input for the
     * shared AddToPlaylistDialog ("Copiar en").
     * ---
     * S037 (H04) -- pistas seleccionadas en el orden de la lista, como
     * entrada del diálogo compartido AddToPlaylistDialog ("Copiar en").
     */
    fun selectedTrackInputs(): List<PlaylistTrackInput> {
        val ids = _uiState.value.selectedIds
        return _uiState.value.tracks
            .filter { it.youtubeId in ids }
            .map { PlaylistTrackInput(youtubeId = it.youtubeId, title = it.title, artist = it.artist) }
    }

    /**
     * S037 (H04) -- per-row play: plays that track on its own.
     * ---
     * S037 (H04) -- play por fila: reproduce ese tema por sí solo.
     */
    fun playTrack(track: SearchResultTrack) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResolving = true, playError = null)
            val started = repository.playSingleTrack(track, playerManager, streamResolver)
            _uiState.value = _uiState.value.copy(
                isResolving = false,
                playError = if (started) null else "No se pudo reproducir \"${track.title}\".",
            )
        }
    }

    fun dismissPlayError() {
        _uiState.value = _uiState.value.copy(playError = null)
    }

    /** Descarta el aviso de mutación bloqueada por falta de conexión (H07 PARTE 1). */
    fun dismissSyncBlockedMessage() {
        _uiState.value = _uiState.value.copy(syncBlockedMessage = null)
    }

    /**
     * Swaps a track with its neighbor and persists both positions.
     * Minimum-viable reordering per mimoo-annex-v04 PASO 3 — drag and
     * drop is explicitly out of scope for this hito.
     * ---
     * Intercambia una pista con su vecina y persiste ambas
     * posiciones. Reordenación mínima viable según mimoo-annex-v04
     * PASO 3 — el drag and drop queda explícitamente fuera de alcance
     * de este hito.
     */
    fun moveTrack(fromIndex: Int, direction: Int) {
        val tracks = _uiState.value.tracks
        val toIndex = fromIndex + direction
        if (toIndex !in tracks.indices || fromIndex !in tracks.indices) return

        val a = tracks[fromIndex]
        val b = tracks[toIndex]
        viewModelScope.launch {
            repository.updatePosition(playlistId, a.youtubeId, toIndex)
            repository.updatePosition(playlistId, b.youtubeId, fromIndex)
        }
    }

    /**
     * Plays the whole playlist in saved order -- delegates to
     * PlaylistRepository.playPlaylistById() (H18, S032), which now
     * holds this exact logic so it can also be reused from the
     * individual play/shuffle button in the Favorites "Listas" tab.
     * This ViewModel only reflects isResolving/resolveError in the UI.
     * ---
     * Reproduce la playlist completa en el orden guardado -- delega en
     * PlaylistRepository.playPlaylistById() (H18, S032), que ahora
     * contiene esta misma lógica exacta para poder reutilizarla
     * también desde el botón individual de play/aleatorio de la
     * pestaña "Listas" de Favoritos. Este ViewModel solo refleja
     * isResolving/resolveError en la UI.
     */
    fun playAll() {
        playInternal(shuffle = false)
    }

    /** S062 -- petición explícita de Miguel Ángel: "no tenemos posibilidad de reproducir en aleatorio". */
    fun playAllShuffled() {
        playInternal(shuffle = true)
    }

    private fun playInternal(shuffle: Boolean) {
        val tracks = _uiState.value.tracks
        if (tracks.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isResolving = true,
                resolveError = null,
            )
            val result = repository.playPlaylistById(
                playlistId = playlistId,
                shuffle = shuffle,
                playerManager = playerManager,
                streamResolver = streamResolver,
            )
            _uiState.value = _uiState.value.copy(
                isResolving = false,
                resolveError = if (result.resolutionFailures > 0) {
                    "No se pudieron resolver ${result.resolutionFailures} pista(s); " +
                        "se reproduce el resto."
                } else {
                    null
                },
            )
        }
    }

    /** Dismisses a previously shown resolve-error banner. */
    fun dismissResolveError() {
        _uiState.value = _uiState.value.copy(resolveError = null)
    }
}

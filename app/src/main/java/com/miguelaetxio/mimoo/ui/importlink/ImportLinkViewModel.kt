package com.miguelaetxio.mimoo.ui.importlink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.remote.ExternalLinkResolver
import com.miguelaetxio.mimoo.data.remote.dto.ExternalLinkTrack
import com.miguelaetxio.mimoo.ui.library.VARIOUS_ARTISTS_CREDIT
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportLinkUiState(
    val url: String = "",
    val isResolving: Boolean = false,
    val errorMessage: String? = null,
    val resolvedTitle: String? = null,
    // Si el enlace resuelto trae mas de una pista, se trata como
    // album/playlist (album = resolvedTitle al importar); un enlace
    // de un solo video se trata como sencillo (album = null) -- ver
    // importSelected().
    val isPlaylist: Boolean = false,
    val tracks: List<ExternalLinkTrack> = emptyList(),
    val selectedYoutubeIds: Set<String> = emptySet(),
    val importedCount: Int? = null,
)

/**
 * ViewModel for "Importar enlace" (PASO 6f, H05) — pastes a
 * YouTube/YouTube Music URL (single video or playlist/album) and
 * previews its tracks via yt-dlp (ExternalLinkResolver), letting the
 * user pick which ones to import before downloading. "Aquí la
 * búsqueda es externa" (Miguel Ángel, 2026-07-02): the user already
 * found the content on YouTube themselves, so this flow never touches
 * the YouTube Data API — zero quota cost regardless of how often it's
 * used, which also means it works even while the Data API quota is
 * fully exhausted (see AlbumSearchViewModel / PASO 6e).
 * ---
 * ViewModel de "Importar enlace" (PASO 6f, H05) — pega una URL de
 * YouTube/YouTube Music (vídeo suelto o playlist/álbum) y previsualiza
 * sus pistas vía yt-dlp (ExternalLinkResolver), dejando elegir cuáles
 * importar antes de descargar. "Aquí la búsqueda es externa" (Miguel
 * Ángel, 2026-07-02): el usuario ya encontró el contenido él mismo en
 * YouTube, así que este flujo nunca toca la YouTube Data API — coste
 * de cuota cero sin importar cuántas veces se use, lo que además
 * significa que funciona incluso con la cuota de la Data API agotada
 * del todo (ver AlbumSearchViewModel / PASO 6e).
 */
@HiltViewModel
class ImportLinkViewModel @Inject constructor(
    private val externalLinkResolver: ExternalLinkResolver,
    private val searchResultTrackRepository: SearchResultTrackRepository,
    private val downloadQueueManager: DownloadQueueManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportLinkUiState())
    val uiState: StateFlow<ImportLinkUiState> = _uiState.asStateFlow()

    fun onUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(url = url)
    }

    fun resolveLink() {
        val url = _uiState.value.url.trim()
        if (url.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isResolving = true,
                errorMessage = null,
                resolvedTitle = null,
                tracks = emptyList(),
                selectedYoutubeIds = emptySet(),
                importedCount = null,
            )
            try {
                val result = externalLinkResolver.resolveLink(url)
                _uiState.value = _uiState.value.copy(
                    isResolving = false,
                    resolvedTitle = result.title,
                    isPlaylist = result.tracks.size > 1,
                    tracks = result.tracks,
                    // Todas seleccionadas por defecto -- el usuario
                    // desmarca las que no quiere en vez de tener que
                    // marcar una a una las que sí (caso común: quiere
                    // el album entero).
                    selectedYoutubeIds = result.tracks.map { it.youtubeId }.toSet(),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isResolving = false,
                    errorMessage = e.message ?: "No se pudo resolver el enlace",
                )
            }
        }
    }

    fun toggleTrackSelected(youtubeId: String) {
        val current = _uiState.value.selectedYoutubeIds
        _uiState.value = _uiState.value.copy(
            selectedYoutubeIds = if (youtubeId in current) {
                current - youtubeId
            } else {
                current + youtubeId
            },
        )
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedYoutubeIds = _uiState.value.tracks.map { it.youtubeId }.toSet(),
        )
    }

    fun selectNone() {
        _uiState.value = _uiState.value.copy(selectedYoutubeIds = emptySet())
    }

    /**
     * Imports the selected tracks and auto-enqueues their download —
     * same convention as AlbumSearchViewModel.importAlbum() (PASO 6b
     * Parte 1). Artist assignment: if every selected track shares the
     * same channel, that channel becomes the artist for all of them
     * (a curated single-channel playlist); if they come from
     * different channels, every track gets VARIOUS_ARTISTS_CREDIT so
     * they group together under "Varios" in Biblioteca (PASO 6d/H05 +
     * reorganización de Biblioteca), instead of scattering across
     * unrelated per-channel artist buckets. album is only set when
     * the resolved link was a playlist (isPlaylist) — a single-video
     * link is a sencillo, not a one-track "album".
     * ---
     * Importa las pistas seleccionadas y encola su descarga
     * automáticamente — misma convención que
     * AlbumSearchViewModel.importAlbum() (PASO 6b Parte 1). Asignación
     * de artista: si todas las pistas seleccionadas comparten el mismo
     * canal, ese canal se convierte en el artista de todas (una
     * playlist curada de un solo canal); si vienen de canales
     * distintos, cada pista recibe VARIOUS_ARTISTS_CREDIT para que se
     * agrupen juntas bajo "Varios" en Biblioteca (PASO 6d/H05 +
     * reorganización de Biblioteca), en vez de repartirse en cubos de
     * artista por canal sin relación entre sí. album solo se fija
     * cuando el enlace resuelto era una playlist (isPlaylist) — un
     * enlace de un solo vídeo es un sencillo, no un "álbum" de una
     * pista.
     */
    fun importSelected() {
        val state = _uiState.value
        val selected = state.tracks.filter { it.youtubeId in state.selectedYoutubeIds }
        if (selected.isEmpty()) return

        val distinctChannels = selected.map { it.channelTitle }.distinct()
        val artist = if (distinctChannels.size == 1) {
            distinctChannels.first()
        } else {
            VARIOUS_ARTISTS_CREDIT
        }
        val album = if (state.isPlaylist) state.resolvedTitle else null

        viewModelScope.launch {
            val tracks = selected.map { track ->
                SearchResultTrack(
                    youtubeId = track.youtubeId,
                    title = track.title,
                    channelTitle = track.channelTitle,
                    durationSeconds = track.durationSeconds,
                    thumbnailUrl = track.thumbnailUrl,
                    artist = artist,
                    album = album,
                )
            }
            searchResultTrackRepository.cacheSearchResults(tracks)
            tracks.forEach { track ->
                downloadQueueManager.enqueue(
                    youtubeId = track.youtubeId,
                    title = track.title,
                    artist = track.artist ?: track.channelTitle,
                )
            }
            _uiState.value = _uiState.value.copy(importedCount = tracks.size)
        }
    }

    fun dismissImportedDialog() {
        _uiState.value = _uiState.value.copy(importedCount = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

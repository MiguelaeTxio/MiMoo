package com.miguelaetxio.mimoo.ui.importlink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import com.miguelaetxio.mimoo.data.playback.StreamResolver
import com.miguelaetxio.mimoo.data.remote.CoverArtRepository
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
    // Carátula real (MusicBrainz + Cover Art Archive) para el
    // álbum/playlist resuelto -- se resuelve en segundo plano tras
    // resolveLink(), sin bloquear la lista de pistas. Null hasta que
    // se resuelve o si MusicBrainz no tiene coincidencia (entonces la
    // UI cae a la miniatura de YouTube de la primera pista, igual que
    // AlbumSearchScreen/LibraryScreen).
    val coverArtUrl: String? = null,
    // Resolviendo streams para reproducir sin descargar (playSelected) --
    // separado de isResolving (que es la resolucion inicial del enlace).
    val isResolvingQueue: Boolean = false,
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
    private val streamResolver: StreamResolver,
    private val playerManager: PlayerManager,
    private val coverArtRepository: CoverArtRepository,
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
                if (result.tracks.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isResolving = false,
                        errorMessage = "El enlace no tiene ninguna pista " +
                            "reproducible (puede ser contenido bloqueado " +
                            "o no disponible).",
                    )
                    return@launch
                }
                val isPlaylist = result.tracks.size > 1
                _uiState.value = _uiState.value.copy(
                    isResolving = false,
                    resolvedTitle = result.title,
                    isPlaylist = isPlaylist,
                    tracks = result.tracks,
                    // Todas seleccionadas por defecto -- el usuario
                    // desmarca las que no quiere en vez de tener que
                    // marcar una a una las que sí (caso común: quiere
                    // el album entero).
                    selectedYoutubeIds = result.tracks.map { it.youtubeId }.toSet(),
                )
                if (isPlaylist) {
                    resolveCoverArt(result.title, result.tracks.map { it.channelTitle })
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isResolving = false,
                    errorMessage = e.message ?: "No se pudo resolver el enlace",
                )
            }
        }
    }

    /**
     * Resolves cover art for the whole resolved playlist/album via
     * CoverArtRepository (MusicBrainz + Cover Art Archive, same
     * mechanism as Biblioteca/AlbumSearchScreen) — peticion de Miguel
     * Ángel tras probar el enlace. Only attempted when every track
     * shares a single channel: CoverArtRepository.resolveCoverArtUrl()
     * requires both artist and album for its MusicBrainz query, and a
     * mixed-channel compilation has no single real artist to search
     * with (it will get VARIOUS_ARTISTS_CREDIT on import, which
     * MusicBrainz will not match to a specific release's cover).
     * Never blocks the track list from showing — runs after it's
     * already in the UI state.
     * ---
     * Resuelve la carátula de toda la playlist/álbum resuelto vía
     * CoverArtRepository (MusicBrainz + Cover Art Archive, mismo
     * mecanismo que Biblioteca/AlbumSearchScreen) — petición de Miguel
     * Ángel tras probar el enlace. Solo se intenta cuando todas las
     * pistas comparten un único canal:
     * CoverArtRepository.resolveCoverArtUrl() necesita artista y álbum
     * para su consulta a MusicBrainz, y una compilación de canales
     * mixtos no tiene un artista real único con el que buscar (recibe
     * VARIOUS_ARTISTS_CREDIT al importar, que MusicBrainz no va a
     * emparejar con la carátula de un release concreto). Nunca bloquea
     * que se muestre la lista de pistas — se ejecuta después de que ya
     * está en el estado de la UI.
     */
    private fun resolveCoverArt(album: String, channelTitles: List<String>) {
        val artist = dominantArtist(channelTitles) ?: return
        viewModelScope.launch {
            val url = coverArtRepository.resolveCoverArtUrl(artist, album)
            if (url != null) {
                _uiState.value = _uiState.value.copy(coverArtUrl = url)
            }
        }
    }

    /** Returns the single shared channel title, or null if there is more than one. */
    private fun dominantArtist(channelTitles: List<String>): String? =
        channelTitles.distinct().singleOrNull()

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

    /**
     * Resolves a live streaming URL for every selected track (same
     * mechanism as SearchViewModel.playTrack(), one
     * StreamResolver.resolveAudioStreamUrl() call per track) and
     * plays them as a queue — no download involved. Petición de
     * Miguel Ángel tras probar el enlace: hacía falta poder escuchar
     * la lista sin tener que descargarla primero.
     *
     * Sequential resolution, not parallel: yt-dlp's extraction is
     * already an external network call per track, and PlayerManager
     * only starts playing the first queue item immediately — the
     * remaining resolves happen while that first track is already
     * sonando, so playback does not wait for all of them the way it
     * feels here (the whole list resolves before playQueue() is
     * called, but the first track is normally quick since it's
     * always resolved first in this same loop).
     * ---
     * Resuelve una URL de streaming en vivo para cada pista
     * seleccionada (mismo mecanismo que
     * SearchViewModel.playTrack(), una llamada a
     * StreamResolver.resolveAudioStreamUrl() por pista) y las
     * reproduce como cola — sin descarga de por medio. Petición de
     * Miguel Ángel tras probar el enlace: hacía falta poder escuchar
     * la lista sin tener que descargarla primero.
     */
    fun playSelected() {
        val state = _uiState.value
        val selected = state.tracks.filter { it.youtubeId in state.selectedYoutubeIds }
        if (selected.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isResolvingQueue = true,
                errorMessage = null,
            )
            try {
                val items = selected.map { track ->
                    val streamUrl = streamResolver.resolveAudioStreamUrl(
                        "https://youtu.be/${track.youtubeId}",
                    )
                    QueueItem(uri = streamUrl, title = track.title, isLocal = false)
                }
                playerManager.playQueue(items)
                _uiState.value = _uiState.value.copy(isResolvingQueue = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isResolvingQueue = false,
                    errorMessage = e.message ?: "Error al resolver el stream",
                )
            }
        }
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

        val artist = dominantArtist(selected.map { it.channelTitle })
            ?: VARIOUS_ARTISTS_CREDIT
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
            // Ya resuelta en resolveCoverArt() -- se persiste aqui
            // para que Biblioteca no tenga que volver a preguntarle a
            // MusicBrainz por el mismo album (misma logica de cache
            // permanente que ya usa LibraryViewModel.requestCoverArtIfMissing).
            if (album != null) {
                state.coverArtUrl?.let { url ->
                    searchResultTrackRepository.updateCoverArtForAlbum(artist, album, url)
                }
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

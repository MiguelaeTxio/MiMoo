package com.miguelaetxio.mimoo.ui.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.library.LibraryReconciler
import com.miguelaetxio.mimoo.data.library.TrackFileRelocator
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import com.miguelaetxio.mimoo.data.remote.CoverArtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

enum class LibraryTab { ALBUMS, SINGLES, FAVORITES }

/**
 * Displayed label for the MusicBrainz "Various Artists" credit — solo
 * a nivel de presentación, la clave de agrupación real sigue siendo
 * el artist-credit tal cual vino de MusicBrainz (PASO 6d, H05), para
 * no romper el filtro de texto ni la reubicación de archivos si
 * alguna vez se edita a mano.
 * ---
 * Etiqueta mostrada para el artist-credit "Various Artists" de
 * MusicBrainz — solo a nivel de presentación, la clave de agrupación
 * real sigue siendo el artist-credit tal cual vino de MusicBrainz
 * (PASO 6d, H05), para no romper el filtro de texto ni la reubicación
 * de archivos si alguna vez se edita a mano.
 */
const val VARIOUS_ARTISTS_CREDIT = "Various Artists"
const val VARIOUS_ARTISTS_DISPLAY_LABEL = "Varios"

/**
 * Fallback credit for cuando YouTube no da ningún nombre de canal
 * real utilizable (p.ej. uploader "-" en playlists auto-generadas de
 * YouTube Music, normalizado a blanco en link_resolver.py) — a
 * diferencia de VARIOUS_ARTISTS_CREDIT, que es para compilaciones con
 * varios artistas reales distintos, esto es para un único álbum/pista
 * real cuyo nombre de artista simplemente no se pudo determinar. Se
 * guarda y se muestra igual, sin mapeo — no es una convención de
 * MusicBrainz como VARIOUS_ARTISTS_CREDIT, así que no hace falta
 * traducirlo en displayArtistName().
 * ---
 * Fallback credit for when YouTube gives no usable real channel name
 * at all (e.g. uploader "-" on auto-generated YouTube Music
 * playlists, normalized to blank in link_resolver.py) — unlike
 * VARIOUS_ARTISTS_CREDIT, which is for compilations with several
 * distinct real artists, this is for a single real album/track whose
 * artist name simply couldn't be determined. Stored and displayed
 * as-is, no mapping needed — it isn't a MusicBrainz convention like
 * VARIOUS_ARTISTS_CREDIT, so displayArtistName() doesn't need to
 * translate it.
 */
const val UNKNOWN_ARTIST_CREDIT = "Artista desconocido"

fun displayArtistName(artist: String): String =
    if (artist.equals(VARIOUS_ARTISTS_CREDIT, ignoreCase = true)) {
        VARIOUS_ARTISTS_DISPLAY_LABEL
    } else {
        artist
    }

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.ALBUMS,
    val filterQuery: String = "",
    // PASO: reorganizacion de Biblioteca a peticion de Miguel Angel --
    // tres estructuras separadas en vez de un unico grouped+viewMode
    // ambiguo. albumsByArtist solo contiene pistas con album real
    // (compilaciones de un artista incluidas -- "Various Artists"
    // cae aqui igual que cualquier otro artista, agrupando de facto
    // los recopilatorios multi-artista bajo un unico "artista").
    // singlesByArtist solo pistas sin album. favorites es plana,
    // ordenada por titulo, sin importar si son album o sencillo.
    val albumsByArtist: Map<String, Map<String, List<SearchResultTrack>>> = emptyMap(),
    val singlesByArtist: Map<String, List<SearchResultTrack>> = emptyMap(),
    val favorites: List<SearchResultTrack> = emptyList(),
    val isRefreshing: Boolean = false,
    val editMetadataError: String? = null,
    // Resumen de mergeDuplicateFolders() para mostrar como Snackbar en
    // LibraryScreen; null cuando no hay nada pendiente de mostrar.
    // ---
    // Summary from mergeDuplicateFolders() to show as a Snackbar in
    // LibraryScreen; null when there's nothing pending to show.
    val mergeResultMessage: String? = null,
)

/**
 * ViewModel for the Biblioteca (local library) screen. Reads only
 * tracks with downloadStatus == DONE — this screen is about what has
 * actually been downloaded, not search results.
 *
 * Does NOT auto-reconcile the SAF folder on creation — that only
 * happens once when the storage folder is first chosen (in
 * MainActivity) or on demand via refreshLibrary(), called from an
 * explicit refresh button. A full SAF tree walk on every screen open
 * would not scale to a large library.
 * ---
 * ViewModel de la pantalla de Biblioteca (biblioteca local). Lee
 * solo pistas con downloadStatus == DONE — esta pantalla trata sobre
 * lo que realmente se ha descargado, no resultados de búsqueda.
 *
 * NO reconcilia la carpeta SAF automáticamente al crearse — eso solo
 * ocurre una vez al elegir la carpeta por primera vez (en
 * MainActivity) o bajo demanda vía refreshLibrary(), llamado desde un
 * botón de refresco explícito. Un recorrido completo del árbol SAF en
 * cada apertura de pantalla no escalaría con una biblioteca grande.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: SearchResultTrackRepository,
    private val playerManager: PlayerManager,
    private val storageManager: StorageManager,
    private val libraryReconciler: LibraryReconciler,
    private val coverArtRepository: CoverArtRepository,
    private val trackFileRelocator: TrackFileRelocator,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var allDownloaded: List<SearchResultTrack> = emptyList()

    // Tracks which artist+album pairs already have a cover art lookup
    // in flight or resolved this process run, so LibraryScreen can
    // call requestCoverArtIfMissing() on every recomposition without
    // spamming MusicBrainz — only the first call per album per app
    // run actually triggers a network request.
    private val coverArtRequested = Collections.synchronizedSet(mutableSetOf<String>())

    init {
        viewModelScope.launch {
            repository.getByStatus(DownloadStatus.DONE).collect { tracks ->
                allDownloaded = tracks
                recompute()
            }
        }
    }

    /**
     * Manually triggers a SAF↔Room reconciliation, called only from
     * the refresh button in LibraryScreen.
     * ---
     * Dispara manualmente una reconciliación SAF↔Room, llamado solo
     * desde el botón de refresco de LibraryScreen.
     */
    fun refreshLibrary() {
        val rootUri = storageManager.getRootUri() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            libraryReconciler.rescan(rootUri)
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    /**
     * Fusiona carpetas de artista duplicadas por la condición de
     * carrera de DownloadDirManager (2026-07-03, ver
     * LibraryReconciler.mergeDuplicateArtistFolders()), y a
     * continuación limpia filas sintéticas muertas y vuelve a
     * escanear para que Biblioteca refleje las rutas nuevas. Acción
     * explícita del usuario, igual que refreshLibrary() -- nunca
     * automática.
     * ---
     * Merges duplicate artist folders caused by DownloadDirManager's
     * race condition (2026-07-03, see LibraryReconciler.
     * mergeDuplicateArtistFolders()), then prunes dead synthetic rows
     * and rescans so Biblioteca reflects the new paths. Explicit user
     * action, same as refreshLibrary() -- never automatic.
     */
    fun mergeDuplicateFolders() {
        val rootUri = storageManager.getRootUri() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val result = libraryReconciler.mergeDuplicateArtistFolders(rootUri)
            libraryReconciler.pruneDeadSyntheticRows()
            libraryReconciler.rescan(rootUri)
            val message = if (result.foldersMerged == 0 && result.filesMoved == 0) {
                "No se encontraron carpetas duplicadas."
            } else {
                "Fusionadas ${result.foldersMerged} carpetas duplicadas, " +
                    "${result.filesMoved} pistas movidas" +
                    if (result.conflicts > 0) {
                        ", ${result.conflicts} con conflicto (no se tocaron)."
                    } else {
                        "."
                    }
            }
            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                mergeResultMessage = message,
            )
        }
    }

    /** Descarta el mensaje de resumen de mergeDuplicateFolders() tras mostrarlo. */
    fun dismissMergeResultMessage() {
        _uiState.value = _uiState.value.copy(mergeResultMessage = null)
    }

    fun setTab(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    fun onFilterQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(filterQuery = query)
        recompute()
    }

    /** Toggles the favorite flag for a track from the library. */
    fun toggleFavorite(track: SearchResultTrack) {
        viewModelScope.launch {
            repository.updateFavorite(track.youtubeId, !track.isFavorite)
        }
    }

    /**
     * Resolves and persists the cover art for one artist+album if it
     * hasn't been requested yet this process run (PASO 6, H03).
     * Called from LibraryScreen once per rendered AlbumHeaderRow —
     * only real albums reach this function, since singles now live in
     * their own tab (albumsByArtist never contains a synthetic
     * "Sencillos" grouping).
     * ---
     * Resuelve y persiste la carátula de un artista+álbum si no se ha
     * pedido ya en esta ejecución del proceso (PASO 6, H03). Se llama
     * desde LibraryScreen una vez por cada AlbumHeaderRow renderizado
     * — solo llegan aquí álbumes reales, ya que los sencillos ahora
     * viven en su propia pestaña (albumsByArtist nunca contiene una
     * agrupación sintética "Sencillos").
     */
    fun requestCoverArtIfMissing(artist: String, album: String) {
        val key = "$artist|$album"
        if (!coverArtRequested.add(key)) return

        val alreadyHasCover = _uiState.value.albumsByArtist[artist]?.get(album)
            ?.any { it.coverArtUrl != null } == true
        if (alreadyHasCover) return

        viewModelScope.launch {
            val url = coverArtRepository.resolveCoverArtUrl(artist, album)
            if (url != null) {
                repository.updateCoverArtForAlbum(artist, album, url)
            }
        }
    }

    /**
     * Applies a manual metadata edit to a track (PASO 7, H03). Title
     * always updates in place with no file move. Artist/album changes
     * additionally relocate the physical .opus file to the new
     * {artist}/{album}/ folder via TrackFileRelocator — applies
     * equally to real, search-originated rows and to synthetic rows
     * from LibraryReconciler, since both carry a real filePath once
     * downloaded. If relocation is needed but fails (no root Uri, or
     * the copy itself fails), the whole edit is aborted and neither
     * Room nor the filesystem changes — surfaced via
     * editMetadataError rather than silently keeping a stale filePath
     * that playback would then fail to open.
     * ---
     * Aplica una edición manual de metadatos a una pista (PASO 7,
     * H03). El título siempre se actualiza en el sitio sin mover el
     * archivo. Los cambios de artista/álbum además reubican el
     * archivo .opus físico a la nueva carpeta {artista}/{álbum}/ vía
     * TrackFileRelocator — aplica igual a filas reales originadas de
     * una búsqueda y a filas sintéticas de LibraryReconciler, ya que
     * ambas llevan un filePath real una vez descargadas. Si hace
     * falta reubicar pero falla (sin Uri raíz, o la copia en sí
     * falla), toda la edición se aborta y ni Room ni el sistema de
     * archivos cambian — se muestra vía editMetadataError en lugar de
     * dejar en silencio un filePath obsoleto que la reproducción
     * fallaría al abrir.
     */
    fun editMetadata(track: SearchResultTrack, newTitle: String, newArtist: String, newAlbumRaw: String) {
        val trimmedTitle = newTitle.trim().ifBlank { track.title }
        val trimmedArtist = newArtist.trim()
            .ifBlank { track.artist ?: track.channelTitle }
        val newAlbum = newAlbumRaw.trim().ifBlank { null }

        val currentArtist = track.artist ?: track.channelTitle
        val needsRelocation =
            (trimmedArtist != currentArtist || newAlbum != track.album) &&
                track.filePath != null

        viewModelScope.launch {
            var updatedFilePath = track.filePath

            if (needsRelocation) {
                val rootUri = storageManager.getRootUri()
                if (rootUri == null) {
                    _uiState.value = _uiState.value.copy(
                        editMetadataError = "No se puede mover el archivo: " +
                            "no hay carpeta de almacenamiento elegida.",
                    )
                    return@launch
                }
                val relocated = trackFileRelocator.relocate(
                    context = context,
                    sourceFilePath = track.filePath!!,
                    rootUri = rootUri,
                    newArtist = trimmedArtist,
                    newAlbum = newAlbum,
                    title = trimmedTitle,
                )
                if (relocated == null) {
                    _uiState.value = _uiState.value.copy(
                        editMetadataError = "No se pudo mover el archivo a la " +
                            "nueva carpeta. La edición no se ha guardado.",
                    )
                    return@launch
                }
                updatedFilePath = relocated
            }

            repository.update(
                track.copy(
                    title = trimmedTitle,
                    artist = trimmedArtist,
                    album = newAlbum,
                    filePath = updatedFilePath,
                )
            )
        }
    }

    /** Dismisses a previously shown editMetadata error banner/dialog. */
    fun dismissEditMetadataError() {
        _uiState.value = _uiState.value.copy(editMetadataError = null)
    }

    /**
     * Deletes a download (PASO 5, H03): removes the physical .opus
     * file via SAF, then either deletes the row entirely (synthetic
     * rows from LibraryReconciler, which have no real youtubeId to
     * fall back to) or resets it to PENDING with a null filePath
     * (real, search-originated rows, which can be re-downloaded
     * later from SearchScreen).
     * ---
     * Elimina una descarga (PASO 5, H03): borra el archivo .opus
     * físico vía SAF, y después o bien elimina la fila entera (filas
     * sintéticas de LibraryReconciler, que no tienen un youtubeId
     * real al que volver) o la resetea a PENDING con filePath null
     * (filas reales originadas de una búsqueda, que pueden volver a
     * descargarse más adelante desde SearchScreen).
     */
    fun deleteDownload(track: SearchResultTrack) {
        viewModelScope.launch {
            track.filePath?.let { path ->
                DocumentFile.fromSingleUri(context, Uri.parse(path))?.delete()
            }
            if (track.youtubeId.startsWith(LibraryReconciler.LOCAL_ID_PREFIX)) {
                repository.delete(track)
            } else {
                repository.clearDownload(track.youtubeId)
            }
        }
    }

    private fun recompute() {
        val query = _uiState.value.filterQuery.trim().lowercase()

        val filtered = if (query.isEmpty()) {
            allDownloaded
        } else {
            allDownloaded.filter { track ->
                track.title.lowercase().contains(query) ||
                    (track.artist ?: track.channelTitle).lowercase()
                        .contains(query) ||
                    (track.album ?: "").lowercase().contains(query)
            }
        }

        // PASO: reorganizacion de Biblioteca -- separacion real por
        // album != null en vez de la etiqueta sintetica UNKNOWN_ALBUM_LABEL
        // de antes. LibraryReconciler ya mapea las carpetas "Sencillos"/
        // legacy a album = null, asi que esta particion es identica
        // para pistas de busqueda y pistas reconciliadas desde disco.
        val albumsByArtist = filtered
            .filter { it.album != null }
            .groupBy { it.artist ?: it.channelTitle }
            .toSortedMap()
            .mapValues { (_, tracks) ->
                tracks
                    .groupBy { it.album!! }
                    .toSortedMap()
                    .mapValues { (_, albumTracks) ->
                        // trackPosition real primero (orden de disco);
                        // las pistas sin posición conocida (sueltas de
                        // SearchScreen, o reconciliadas desde disco)
                        // caen al final, ordenadas entre ellas por
                        // título -- mismo comportamiento que antes de
                        // este fix para esas filas en concreto.
                        albumTracks.sortedWith(
                            compareBy<SearchResultTrack> {
                                it.trackPosition ?: Int.MAX_VALUE
                            }.thenBy { it.title }
                        )
                    }
            }

        val singlesByArtist = filtered
            .filter { it.album == null }
            .groupBy { it.artist ?: it.channelTitle }
            .toSortedMap()
            .mapValues { (_, tracks) -> tracks.sortedBy { it.title } }

        val favorites = filtered
            .filter { it.isFavorite }
            .sortedBy { it.title }

        _uiState.value = _uiState.value.copy(
            albumsByArtist = albumsByArtist,
            singlesByArtist = singlesByArtist,
            favorites = favorites,
        )
    }

    /** Plays a single track from the library (always local). */
    fun playTrack(track: SearchResultTrack) {
        val filePath = track.filePath ?: return
        playerManager.play(filePath, track.title, isLocal = true)
    }

    /** Plays every track of one album, in title order, as a queue. */
    fun playAlbum(artist: String, album: String) {
        val tracks = _uiState.value.albumsByArtist[artist]?.get(album) ?: return
        playerManager.playQueue(tracks.toQueueItems())
    }

    /**
     * Plays every album track of one artist, album order then title
     * order within each album, as a queue (pestaña Álbumes).
     * ---
     * Reproduce todas las pistas de álbum de un artista, en orden de
     * álbum y luego de título dentro de cada álbum, como cola
     * (pestaña Álbumes).
     */
    fun playArtistAlbums(artist: String) {
        val albums = _uiState.value.albumsByArtist[artist] ?: return
        playerManager.playQueue(albums.values.flatten().toQueueItems())
    }

    /** Plays every album track of one artist in random order (pestaña Álbumes). */
    fun playArtistAlbumsShuffled(artist: String) {
        val albums = _uiState.value.albumsByArtist[artist] ?: return
        playerManager.playQueue(albums.values.flatten().shuffled().toQueueItems())
    }

    /** Plays every single of one artist, title order, as a queue (pestaña Sencillos). */
    fun playArtistSingles(artist: String) {
        val tracks = _uiState.value.singlesByArtist[artist] ?: return
        playerManager.playQueue(tracks.toQueueItems())
    }

    /** Plays every single of one artist in random order (pestaña Sencillos). */
    fun playArtistSinglesShuffled(artist: String) {
        val tracks = _uiState.value.singlesByArtist[artist] ?: return
        playerManager.playQueue(tracks.shuffled().toQueueItems())
    }

    /** Plays every favorite, title order, as a queue (pestaña Favoritos). */
    fun playFavorites() {
        playerManager.playQueue(_uiState.value.favorites.toQueueItems())
    }

    /** Plays every favorite in random order (pestaña Favoritos). */
    fun playFavoritesShuffled() {
        playerManager.playQueue(_uiState.value.favorites.shuffled().toQueueItems())
    }

    private fun List<SearchResultTrack>.toQueueItems(): List<QueueItem> =
        mapNotNull { track ->
            track.filePath?.let { path ->
                QueueItem(uri = path, title = track.title, isLocal = true)
            }
        }
}

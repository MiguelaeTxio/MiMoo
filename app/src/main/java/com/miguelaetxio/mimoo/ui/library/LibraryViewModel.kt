package com.miguelaetxio.mimoo.ui.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.download.DownloadDirManager
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.library.LibraryReconciler
import com.miguelaetxio.mimoo.data.library.StartupNotices
import com.miguelaetxio.mimoo.data.library.TrackFileRelocator
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import com.miguelaetxio.mimoo.data.remote.CoverArtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
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
 * real cuyo nombre de artista simplemente no se pudo determinar.
 * ---
 * Fallback credit for when YouTube gives no usable real channel name
 * at all (e.g. uploader "-" on auto-generated YouTube Music
 * playlists, normalized to blank in link_resolver.py) — unlike
 * VARIOUS_ARTISTS_CREDIT, which is for compilations with several
 * distinct real artists, this is for a single real album/track whose
 * artist name simply couldn't be determined.
 */
const val UNKNOWN_ARTIST_CREDIT = "Artista desconocido"

fun displayArtistName(artist: String): String =
    if (artist.equals(VARIOUS_ARTISTS_CREDIT, ignoreCase = true)) {
        VARIOUS_ARTISTS_DISPLAY_LABEL
    } else {
        artist
    }

/**
 * Letra de agrupación de un artista para la primera capa de
 * Biblioteca ("Artistas por letra"), pedida por Miguel Ángel
 * (2026-07-04): se agrupa por el nombre TAL CUAL está guardado en
 * Room (el "nombre conocido" -- Beethoven, Mozart, Alejandro Sanz --
 * ya es lo que viene de MusicBrainz/YouTube, no el nombre de pila
 * completo), sin ninguna heurística de apellido. Solo se le quitan
 * los acentos/diacríticos para que "Ángel" caiga en "A" y no en un
 * cubo aparte para "Á". Cualquier nombre que no empiece por una letra
 * A-Z cae en "#".
 * ---
 * Grouping letter for an artist for Biblioteca's first layer
 * ("Artistas por letra"), requested by Miguel Ángel (2026-07-04):
 * grouped by the name AS STORED in Room (the "known name" --
 * Beethoven, Mozart, Alejandro Sanz -- is already what comes from
 * MusicBrainz/YouTube, not the full birth name), no surname
 * heuristic. Only diacritics are stripped so "Ángel" falls under "A"
 * instead of a separate "Á" bucket. Anything not starting with an
 * A-Z letter falls under "#".
 */
fun sortLetterFor(artist: String): Char {
    val trimmed = displayArtistName(artist).trim()
    if (trimmed.isEmpty()) return '#'
    val normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
    val first = normalized.firstOrNull()?.uppercaseChar() ?: '#'
    return if (first in 'A'..'Z') first else '#'
}

/**
 * Niveles de navegación por capas de la pestaña Álbumes, pedidos por
 * Miguel Ángel (2026-07-04): Letras -> Artistas (de esa letra) ->
 * Álbumes (de ese artista) -> Pistas (de ese álbum) -- nunca todo
 * junto en una sola pantalla.
 * ---
 * Drill-down navigation levels for the Álbumes tab, requested by
 * Miguel Ángel (2026-07-04): Letters -> Artists (for that letter) ->
 * Albums (for that artist) -> Tracks (for that album) -- never
 * everything on one screen at once.
 */
sealed class AlbumsDrillLevel {
    object Letters : AlbumsDrillLevel()
    data class Artists(val letter: Char) : AlbumsDrillLevel()
    data class Albums(val artist: String) : AlbumsDrillLevel()
    data class Tracks(val artist: String, val album: String) : AlbumsDrillLevel()
}

/**
 * Igual que AlbumsDrillLevel pero para la pestaña Sencillos, que no
 * tiene capa de álbum: Letras -> Artistas -> Pistas directamente.
 * ---
 * Same as AlbumsDrillLevel but for the Sencillos tab, which has no
 * album layer: Letters -> Artists -> Tracks directly.
 */
sealed class SinglesDrillLevel {
    object Letters : SinglesDrillLevel()
    data class Artists(val letter: Char) : SinglesDrillLevel()
    data class Tracks(val artist: String) : SinglesDrillLevel()
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
    // Letras disponibles (con al menos un artista) para la primera
    // capa de cada pestaña, ya ordenadas.
    // ---
    // Available letters (with at least one artist) for the first
    // layer of each tab, already sorted.
    val albumLetters: List<Char> = emptyList(),
    val singleLetters: List<Char> = emptyList(),
    val albumsDrill: AlbumsDrillLevel = AlbumsDrillLevel.Letters,
    val singlesDrill: SinglesDrillLevel = SinglesDrillLevel.Letters,
    val isRefreshing: Boolean = false,
    val editMetadataError: String? = null,
    // Resumen de mergeDuplicateFolders() para mostrar como Snackbar en
    // LibraryScreen; null cuando no hay nada pendiente de mostrar.
    // ---
    // Summary from mergeDuplicateFolders() to show as a Snackbar in
    // LibraryScreen; null when there's nothing pending to show.
    val mergeResultMessage: String? = null,
    // Aviso de la limpieza automática de arranque (MainActivity ->
    // StartupNotices), mostrado una sola vez como Snackbar.
    // ---
    // Notice from the automatic startup cleanup (MainActivity ->
    // StartupNotices), shown exactly once as a Snackbar.
    val startupMessage: String? = null,
)

/**
 * ViewModel for the Biblioteca (local library) screen. Reads only
 * tracks with downloadStatus == DONE — this screen is about what has
 * actually been downloaded, not search results.
 *
 * La reconciliación SAF↔Room automática al ARRANCAR la app vive en
 * MainActivity (cada vez que hay una carpeta ya elegida, no solo la
 * primera vez -- petición explícita de Miguel Ángel, 2026-07-04).
 * refreshLibrary() sigue existiendo aquí como acción manual adicional
 * desde el botón de refresco.
 * ---
 * ViewModel de la pantalla de Biblioteca (biblioteca local). Lee
 * solo pistas con downloadStatus == DONE — esta pantalla trata sobre
 * lo que realmente se ha descargado, no resultados de búsqueda.
 *
 * La reconciliación automática SAF↔Room al arrancar vive en
 * MainActivity (cada vez que ya hay carpeta elegida, no solo la
 * primera vez -- petición explícita de Miguel Ángel, 2026-07-04).
 * refreshLibrary() sigue existiendo aquí como acción manual adicional
 * desde el botón de refresco.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: SearchResultTrackRepository,
    private val playerManager: PlayerManager,
    private val storageManager: StorageManager,
    private val libraryReconciler: LibraryReconciler,
    private val startupNotices: StartupNotices,
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
        viewModelScope.launch {
            startupNotices.message.collect { message ->
                if (message != null) {
                    _uiState.value = _uiState.value.copy(startupMessage = message)
                }
            }
        }
    }

    /** Descarta el aviso de limpieza de arranque tras mostrarlo. */
    fun dismissStartupMessage() {
        _uiState.value = _uiState.value.copy(startupMessage = null)
        startupNotices.consume()
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
     * escanear para que Biblioteca refleje las rutas nuevas.
     * ---
     * Merges duplicate artist folders caused by DownloadDirManager's
     * race condition (2026-07-03, see LibraryReconciler.
     * mergeDuplicateArtistFolders()), then prunes dead synthetic rows
     * and rescans so Biblioteca reflects the new paths.
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

    // --- Navegación por capas, pestaña Álbumes -----------------------

    fun selectAlbumsLetter(letter: Char) {
        _uiState.value = _uiState.value.copy(
            albumsDrill = AlbumsDrillLevel.Artists(letter),
        )
    }

    fun selectAlbumsArtist(artist: String) {
        _uiState.value = _uiState.value.copy(
            albumsDrill = AlbumsDrillLevel.Albums(artist),
        )
    }

    fun selectAlbumsAlbum(artist: String, album: String) {
        _uiState.value = _uiState.value.copy(
            albumsDrill = AlbumsDrillLevel.Tracks(artist, album),
        )
    }

    /** Sube un nivel en la navegación de Álbumes; en Letras no hace nada. */
    fun backAlbumsDrill(): Boolean {
        val current = _uiState.value.albumsDrill
        val newLevel = when (current) {
            is AlbumsDrillLevel.Letters -> return false
            is AlbumsDrillLevel.Artists -> AlbumsDrillLevel.Letters
            is AlbumsDrillLevel.Albums -> AlbumsDrillLevel.Artists(
                sortLetterFor(current.artist),
            )
            is AlbumsDrillLevel.Tracks -> AlbumsDrillLevel.Albums(current.artist)
        }
        _uiState.value = _uiState.value.copy(albumsDrill = newLevel)
        return true
    }

    // --- Navegación por capas, pestaña Sencillos ---------------------

    fun selectSinglesLetter(letter: Char) {
        _uiState.value = _uiState.value.copy(
            singlesDrill = SinglesDrillLevel.Artists(letter),
        )
    }

    fun selectSinglesArtist(artist: String) {
        _uiState.value = _uiState.value.copy(
            singlesDrill = SinglesDrillLevel.Tracks(artist),
        )
    }

    /** Sube un nivel en la navegación de Sencillos; en Letras no hace nada. */
    fun backSinglesDrill(): Boolean {
        val current = _uiState.value.singlesDrill
        val newLevel = when (current) {
            is SinglesDrillLevel.Letters -> return false
            is SinglesDrillLevel.Artists -> SinglesDrillLevel.Letters
            is SinglesDrillLevel.Tracks -> SinglesDrillLevel.Artists(
                sortLetterFor(current.artist),
            )
        }
        _uiState.value = _uiState.value.copy(singlesDrill = newLevel)
        return true
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
     * ---
     * Resuelve y persiste la carátula de un artista+álbum si no se ha
     * pedido ya en esta ejecución del proceso (PASO 6, H03).
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
     * {artist}/{album}/ folder via TrackFileRelocator.
     * ---
     * Aplica una edición manual de metadatos a una pista (PASO 7,
     * H03). El título siempre se actualiza en el sitio sin mover el
     * archivo. Los cambios de artista/álbum además reubican el
     * archivo físico a la nueva carpeta {artista}/{álbum}/ vía
     * TrackFileRelocator.
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
                val relocated = withContext(Dispatchers.IO) {
                    trackFileRelocator.relocate(
                        context = context,
                        sourceFilePath = track.filePath!!,
                        rootUri = rootUri,
                        newArtist = trimmedArtist,
                        newAlbum = newAlbum,
                        title = trimmedTitle,
                        trackPosition = track.trackPosition,
                    )
                }
                if (relocated == null) {
                    _uiState.value = _uiState.value.copy(
                        editMetadataError = "No se pudo mover el archivo a la " +
                            "nueva carpeta. La edición no se ha guardado.",
                    )
                    return@launch
                }
                updatedFilePath = relocated

                // El archivo salió de {currentArtist}/{track.album}/ --
                // si quedó vacía, la limpiamos igual que en un borrado.
                // ---
                // The file left {currentArtist}/{track.album}/ -- if it
                // ended up empty, clean it up just like on a delete.
                withContext(Dispatchers.IO) {
                    cleanupEmptyFolders(currentArtist, track.album)
                }
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
     * Deletes a download: removes the physical file via SAF, borra la
     * carpeta de álbum si quedó vacía y la de artista si también
     * quedó vacía (petición de Miguel Ángel, 2026-07-04 -- nunca se
     * borra la carpeta raíz), y después o bien elimina la fila entera
     * (filas sintéticas de LibraryReconciler) o la resetea a PENDING
     * (filas reales, que pueden volver a descargarse).
     * ---
     * Elimina una descarga: borra el archivo físico vía SAF, borra la
     * carpeta de álbum si quedó vacía y la de artista si también
     * quedó vacía (requested by Miguel Ángel, 2026-07-04 -- the root
     * folder is never deleted), then either deletes the row entirely
     * (synthetic rows from LibraryReconciler) or resets it to PENDING
     * (real rows, which can be re-downloaded).
     */
    fun deleteDownload(track: SearchResultTrack) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                track.filePath?.let { path ->
                    DocumentFile.fromSingleUri(context, Uri.parse(path))?.delete()
                }
                cleanupEmptyFolders(track.artist ?: track.channelTitle, track.album)
                if (track.youtubeId.startsWith(LibraryReconciler.LOCAL_ID_PREFIX)) {
                    repository.delete(track)
                } else {
                    repository.clearDownload(track.youtubeId)
                }
            }
        }
    }

    /**
     * Borra un álbum entero de un artista: todas sus pistas (archivo
     * físico + fila Room) y, al final, la carpeta de álbum entera y la
     * de artista si quedó vacía. Acción explícita pedida por Miguel
     * Ángel (2026-07-04) para gestionar la biblioteca desde dentro de
     * un artista.
     * ---
     * Deletes an entire album from an artist: every track (physical
     * file + Room row) and, at the end, the whole album folder and the
     * artist folder if it ended up empty. Explicit action requested by
     * Miguel Ángel (2026-07-04) to manage the library from within an
     * artist.
     */
    fun deleteAlbum(artist: String, album: String) {
        viewModelScope.launch {
            val tracks = _uiState.value.albumsByArtist[artist]?.get(album)
                ?: return@launch
            withContext(Dispatchers.IO) {
                tracks.forEach { track ->
                    track.filePath?.let { path ->
                        DocumentFile.fromSingleUri(context, Uri.parse(path))?.delete()
                    }
                    if (track.youtubeId.startsWith(LibraryReconciler.LOCAL_ID_PREFIX)) {
                        repository.delete(track)
                    } else {
                        repository.clearDownload(track.youtubeId)
                    }
                }
                deleteFolderIfExists(artist, album)
                cleanupEmptyFolders(artist, album)
            }

            if (_uiState.value.albumsDrill is AlbumsDrillLevel.Tracks) {
                _uiState.value = _uiState.value.copy(
                    albumsDrill = AlbumsDrillLevel.Albums(artist),
                )
            }
        }
    }

    /**
     * Borra un artista entero: todas sus pistas de álbum y sencillos
     * (archivo físico + fila Room) y la carpeta de artista completa.
     * Acción explícita pedida por Miguel Ángel (2026-07-04).
     * ---
     * Deletes an entire artist: every album and single track (physical
     * file + Room row) and the whole artist folder. Explicit action
     * requested by Miguel Ángel (2026-07-04).
     */
    fun deleteArtist(artist: String) {
        viewModelScope.launch {
            val albumTracks = _uiState.value.albumsByArtist[artist]
                ?.values?.flatten() ?: emptyList()
            val singleTracks = _uiState.value.singlesByArtist[artist] ?: emptyList()

            withContext(Dispatchers.IO) {
                (albumTracks + singleTracks).forEach { track ->
                    track.filePath?.let { path ->
                        DocumentFile.fromSingleUri(context, Uri.parse(path))?.delete()
                    }
                    if (track.youtubeId.startsWith(LibraryReconciler.LOCAL_ID_PREFIX)) {
                        repository.delete(track)
                    } else {
                        repository.clearDownload(track.youtubeId)
                    }
                }

                val rootUri = storageManager.getRootUri()
                if (rootUri != null) {
                    DocumentFile.fromTreeUri(context, rootUri)
                        ?.findFile(DownloadDirManager.sanitize(artist))
                        ?.delete()
                }
            }

            if (_uiState.value.albumsDrill !is AlbumsDrillLevel.Letters) {
                _uiState.value = _uiState.value.copy(
                    albumsDrill = AlbumsDrillLevel.Letters,
                )
            }
            if (_uiState.value.singlesDrill !is SinglesDrillLevel.Letters) {
                _uiState.value = _uiState.value.copy(
                    singlesDrill = SinglesDrillLevel.Letters,
                )
            }
        }
    }

    /**
     * Borra físicamente la carpeta de álbum {artist}/{album}/ entera
     * (llamado desde deleteAlbum(), donde ya se han borrado todas las
     * pistas conocidas pero podría quedar algún archivo suelto no
     * indexado -- p.ej. una carátula .jpg). No falla si no existe.
     * ---
     * Physically deletes the whole {artist}/{album}/ folder (called
     * from deleteAlbum(), where every known track has already been
     * removed but a stray, non-indexed file might remain -- e.g. a
     * .jpg cover). Does not fail if it doesn't exist.
     */
    private fun deleteFolderIfExists(artist: String, album: String?) {
        val rootUri = storageManager.getRootUri() ?: return
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return
        val artistDir = root.findFile(DownloadDirManager.sanitize(artist)) ?: return
        val albumDirName = album?.let { DownloadDirManager.sanitize(it) } ?: "Sencillos"
        artistDir.findFile(albumDirName)?.delete()
    }

    /**
     * Borra la carpeta de álbum si quedó vacía tras un borrado, y la
     * de artista si también quedó vacía -- nunca la carpeta raíz.
     * Petición explícita de Miguel Ángel (2026-07-04): "cuando
     * borremos el último integrante de la carpeta debe borrar la
     * carpeta excepto la carpeta raíz".
     * ---
     * Deletes the album folder if it ended up empty after a deletion,
     * and the artist folder if it also ended up empty -- never the
     * root folder. Explicit request from Miguel Ángel (2026-07-04):
     * "when we delete the last member of the folder, the folder
     * should be deleted, except the root folder".
     */
    private fun cleanupEmptyFolders(artist: String, album: String?) {
        val rootUri = storageManager.getRootUri() ?: return
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return
        val artistDir = root.findFile(DownloadDirManager.sanitize(artist)) ?: return

        val albumDirName = album?.let { DownloadDirManager.sanitize(it) } ?: "Sencillos"
        val albumDir = artistDir.findFile(albumDirName)
        if (albumDir != null && albumDir.isDirectory && albumDir.listFiles().isEmpty()) {
            albumDir.delete()
        }

        if (artistDir.isDirectory && artistDir.listFiles().isEmpty()) {
            artistDir.delete()
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
                        // las pistas sin posición conocida caen al
                        // final, ordenadas entre ellas por título.
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

        val albumLetters = albumsByArtist.keys
            .map { sortLetterFor(it) }
            .toSortedSet()
            .toList()
        val singleLetters = singlesByArtist.keys
            .map { sortLetterFor(it) }
            .toSortedSet()
            .toList()

        _uiState.value = _uiState.value.copy(
            albumsByArtist = albumsByArtist,
            singlesByArtist = singlesByArtist,
            favorites = favorites,
            albumLetters = albumLetters,
            singleLetters = singleLetters,
        )
    }

    /** Plays a single track from the library (always local). */
    fun playTrack(track: SearchResultTrack) {
        val filePath = track.filePath ?: return
        playerManager.play(filePath, track.title, isLocal = true)
    }

    /**
     * Añade una pista suelta al FINAL de la cola de reproducción de
     * sesión, sin interrumpir lo que suena ahora -- petición explícita
     * de Miguel Ángel (2026-07-05): "añado una canción a la lista de
     * reproducción... en la lista de reproducción actual estoy
     * añadiendo una canción". Distinto de playTrack(), que sí
     * interrumpe y reproduce ya.
     * ---
     * Adds a single track to the END of the session playback queue,
     * without interrupting what's currently playing -- explicit
     * request from Miguel Ángel (2026-07-05): "I add a song to the
     * playback queue... I'm adding a song to the CURRENT queue".
     * Distinct from playTrack(), which does interrupt and play right
     * away.
     */
    fun addTrackToQueue(track: SearchResultTrack) {
        val filePath = track.filePath ?: return
        playerManager.addToQueue(
            listOf(QueueItem(uri = filePath, title = track.title, isLocal = true))
        )
    }

    /**
     * "Reproducir a continuación" -- pista suelta. Ver
     * PlayerManager.insertNext() para la semántica exacta.
     * ---
     * "Play next" -- single track. See PlayerManager.insertNext() for
     * the exact semantics.
     */
    fun insertTrackNext(track: SearchResultTrack) {
        val filePath = track.filePath ?: return
        playerManager.insertNext(
            listOf(QueueItem(uri = filePath, title = track.title, isLocal = true))
        )
    }

    /**
     * Añade un álbum entero al final de la cola de sesión, sin
     * interrumpir lo que suena -- mismo criterio que
     * addTrackToQueue(), a nivel de álbum.
     * ---
     * Adds a whole album to the end of the session queue, without
     * interrupting what's playing -- same criterion as
     * addTrackToQueue(), at the album level.
     */
    fun addAlbumToQueue(artist: String, album: String) {
        val tracks = _uiState.value.albumsByArtist[artist]?.get(album) ?: return
        playerManager.addToQueue(tracks.toQueueItems())
    }

    /**
     * "Reproducir a continuación" -- álbum entero. Ver
     * PlayerManager.insertNext() para la semántica exacta.
     * ---
     * "Play next" -- whole album. See PlayerManager.insertNext() for
     * the exact semantics.
     */
    fun insertAlbumNext(artist: String, album: String) {
        val tracks = _uiState.value.albumsByArtist[artist]?.get(album) ?: return
        playerManager.insertNext(tracks.toQueueItems())
    }

    /** Plays every track of one album, in title order, as a queue. */
    fun playAlbum(artist: String, album: String) {
        val tracks = _uiState.value.albumsByArtist[artist]?.get(album) ?: return
        playerManager.playQueue(tracks.toQueueItems())
    }

    /**
     * Plays every album track of one artist, album order then title
     * order within each album, as a queue (pestaña Álbumes).
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

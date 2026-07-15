package com.miguelaetxio.mimoo.ui.library

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.download.DownloadDirManager
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.library.LibraryReconciler
import com.miguelaetxio.mimoo.data.library.StartupNotices
import com.miguelaetxio.mimoo.data.library.TrackFileRelocator
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.FavoriteAlbumRepository
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import com.miguelaetxio.mimoo.data.playback.StreamResolver
import com.miguelaetxio.mimoo.data.remote.CoverArtRepository
import com.miguelaetxio.mimoo.util.SearchNormalizer
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
    /**
     * Vista plana alternativa a Letters -- toggle pedido por Miguel
     * Ángel (2026-07-07): todos los artistas ordenados
     * alfabéticamente, sin la capa de letras intermedia. Es raíz
     * igual que Letters (no tiene "atrás"); AlbumsViewMode decide
     * cuál de las dos está activa. De aquí para abajo (Albums,
     * Tracks) no cambia nada.
     * ---
     * Flat alternative view to Letters -- toggle requested by Miguel
     * Ángel (2026-07-07): all artists sorted alphabetically, without
     * the intermediate letters layer. It's a root level just like
     * Letters (no "back"); AlbumsViewMode decides which of the two is
     * active. Nothing changes below this (Albums, Tracks).
     */
    object ArtistsFlat : AlbumsDrillLevel()
    /**
     * Entrada "Favoritos" -- petición explícita de Miguel Ángel
     * (2026-07-05): lista plana (no por letra/artista, "todo
     * exactamente igual" en orden alfabético) de álbumes marcados como
     * favoritos, cruzando artistas. Aparece antes que las letras en la
     * pantalla de Letters.
     * ---
     * "Favoritos" entry -- explicit request from Miguel Ángel
     * (2026-07-05): a flat (not by letter/artist, "everything exactly
     * the same" in alphabetical order) list of albums marked as
     * favorite, across artists. Shown before the letters on the
     * Letters screen.
     */
    object FavoriteAlbums : AlbumsDrillLevel()
    data class Artists(val letter: Char) : AlbumsDrillLevel()
    data class Albums(val artist: String) : AlbumsDrillLevel()
    /**
     * fromFavorites indica si se llegó aquí desde FavoriteAlbums (para
     * que el botón atrás vuelva ahí en vez de a Albums(artist), que
     * asume siempre el mismo artista).
     * ---
     * fromFavorites tracks whether we got here from FavoriteAlbums (so
     * the back button returns there instead of to Albums(artist),
     * which always assumes the same artist).
     */
    data class Tracks(
        val artist: String,
        val album: String,
        val fromFavorites: Boolean = false,
    ) : AlbumsDrillLevel()
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
    /** Vista plana equivalente a AlbumsDrillLevel.ArtistsFlat, misma petición (2026-07-07). */
    object ArtistsFlat : SinglesDrillLevel()
    data class Artists(val letter: Char) : SinglesDrillLevel()
    data class Tracks(val artist: String) : SinglesDrillLevel()
}

/** Equivalente a AlbumsViewMode pero para la pestaña Sencillos. */
enum class SinglesViewMode { BY_LETTER, FLAT }

/**
 * Qué vista raíz de la pestaña Álbumes está activa -- toggle pedido
 * por Miguel Ángel (2026-07-07). Solo afecta a qué nivel raíz se
 * usa (Letters vs ArtistsFlat); Albums y Tracks son idénticos en
 * ambos modos.
 * ---
 * Which root view of the Álbumes tab is active -- toggle requested
 * by Miguel Ángel (2026-07-07). Only affects which root level is
 * used (Letters vs ArtistsFlat); Albums and Tracks are identical in
 * both modes.
 */
enum class AlbumsViewMode { BY_LETTER, FLAT }

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
    val albumsViewMode: AlbumsViewMode = AlbumsViewMode.BY_LETTER,
    val singlesDrill: SinglesDrillLevel = SinglesDrillLevel.Letters,
    val singlesViewMode: SinglesViewMode = SinglesViewMode.BY_LETTER,
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
    // Favoritos a nivel de álbum -- petición explícita de Miguel Ángel
    // (2026-07-05). favoriteAlbumKeys para saber si un álbum concreto
    // está marcado (icono relleno/vacío en AlbumHeaderRow);
    // favoriteAlbumsFlat ya filtrado a álbumes que siguen existiendo en
    // la biblioteca y ordenado alfabéticamente por título de álbum,
    // para la lista plana de AlbumsDrillLevel.FavoriteAlbums.
    // ---
    // Album-level favorites -- explicit request from Miguel Ángel
    // (2026-07-05). favoriteAlbumKeys to know if a specific album is
    // marked (filled/empty icon in AlbumHeaderRow); favoriteAlbumsFlat
    // already filtered to albums that still exist in the library and
    // sorted alphabetically by album title, for
    // AlbumsDrillLevel.FavoriteAlbums's flat list.
    val favoriteAlbumKeys: Set<Pair<String, String>> = emptySet(),
    val favoriteAlbumsFlat: List<Pair<String, String>> = emptyList(),
    // H07 PARTE 1 -- aviso cuando una acción de añadir/borrar se
    // rechaza por falta de conexión (regla de negocio de Miguel
    // Ángel, S008: sin red, ni siquiera se aplica en local).
    // ---
    // H07 PART 1 -- notice when an add/remove action gets rejected
    // for lack of connection (Miguel Ángel's business rule, S008: no
    // network, doesn't even apply locally).
    val syncBlockedMessage: String? = null,
    /**
     * S010 -- "Reproducir todo"/"Aleatorio" de Favoritos ahora resuelve
     * en streaming las que no están descargadas antes de reproducir la
     * cola completa (mismo patrón que PlaylistDetailViewModel). Mientras
     * resuelve, la UI puede mostrar un indicador; si alguna falla, el
     * aviso aparece aquí en vez de desaparecer en silencio.
     * ---
     * S010 -- Favorites "Play all"/"Shuffle" now resolves streaming for
     * non-downloaded tracks before playing the full queue. While
     * resolving, the UI can show an indicator; if any fail, the warning
     * shows up here instead of silently vanishing.
     */
    val isResolvingFavorites: Boolean = false,
    val favoritesResolveError: String? = null,
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
    private val favoriteAlbumRepository: FavoriteAlbumRepository,
    private val autoSyncPusher: AutoSyncPusher,
    private val streamResolver: StreamResolver,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var allDownloaded: List<SearchResultTrack> = emptyList()
    private var allFavorites: List<SearchResultTrack> = emptyList()
    // Set en memoria de (artist, album) marcados favoritos -- ver
    // comentario de LibraryUiState.favoriteAlbumKeys.
    // ---
    // In-memory set of (artist, album) marked favorite -- see
    // LibraryUiState.favoriteAlbumKeys' comment.
    private var favoriteAlbumKeysSet: Set<Pair<String, String>> = emptySet()

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
        // S010 -- independiente de allDownloaded a propósito: una
        // pista favoritada desde el reproductor/cola puede no estar
        // descargada, y aun así debe verse (y poder reproducirse en
        // streaming) en la sección "Favoritos" de la Biblioteca. Ver
        // SearchResultTrackDao.getFavorites().
        // ---
        // S010 -- deliberately independent of allDownloaded: a track
        // favorited from the player/queue may not be downloaded, and
        // should still show up (and be playable via streaming) in the
        // Library's "Favorites" section.
        viewModelScope.launch {
            repository.getFavorites().collect { tracks ->
                allFavorites = tracks
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
        viewModelScope.launch {
            favoriteAlbumRepository.getAll().collect { favorites ->
                favoriteAlbumKeysSet = favorites.map { it.artist to it.album }.toSet()
                recompute()
            }
        }
    }

    /**
     * Marca/desmarca un álbum entero como favorito -- concepto NUEVO,
     * separado de toggleFavorite() (que es por pista, para sencillos).
     * Petición explícita de Miguel Ángel (2026-07-05).
     * ---
     * Marks/unmarks a whole album as favorite -- a NEW concept,
     * separate from toggleFavorite() (which is per-track, for
     * singles). Explicit request from Miguel Ángel (2026-07-05).
     */
    fun toggleFavoriteAlbum(activity: Activity, artist: String, album: String) {
        viewModelScope.launch {
            // H07 PARTE 1 -- la mutación NO se aplica en absoluto si
            // no hay conexión (regla de negocio de Miguel Ángel,
            // S008), en vez de aplicarla en local y empujar aparte
            // como en el primer diseño.
            // ---
            // H07 PART 1 -- the mutation is NOT applied at all if
            // there's no connection (Miguel Ángel's business rule,
            // S008), instead of applying it locally and pushing
            // separately like in the first design.
            val outcome = autoSyncPusher.executeIfConnected(activity) {
                favoriteAlbumRepository.toggle(artist, album)
            }
            if (outcome is com.miguelaetxio.mimoo.data.backup.MutationOutcome.NoConnection) {
                _uiState.value = _uiState.value.copy(
                    syncBlockedMessage = "Sin conexión: no se puede cambiar favoritos ahora mismo."
                )
            }
        }
    }

    /** Descarta el aviso de mutación bloqueada por falta de conexión (H07 PARTE 1). */
    fun dismissSyncBlockedMessage() {
        _uiState.value = _uiState.value.copy(syncBlockedMessage = null)
    }

    fun dismissFavoritesResolveError() {
        _uiState.value = _uiState.value.copy(favoritesResolveError = null)
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

    /**
     * Al pulsar una pestaña, vuelve siempre a su lista raíz --
     * bug real reportado por Miguel Ángel: antes solo cambiaba qué
     * pestaña estaba activa, dejando el nivel de navegación (`drill`)
     * de esa pestaña tal cual estaba (p.ej. dentro de un álbum
     * concreto), así que pulsar "Álbumes"/"Sencillos" desde dentro de
     * un artista/álbum no llevaba a ningún sitio nuevo -- había que
     * salir a mano con la flecha atrás repetidas veces.
     * ---
     * Tapping a tab always goes back to its root list -- real bug
     * reported by Miguel Ángel: before, this only changed which tab
     * was active, leaving that tab's navigation level (`drill`) as it
     * was (e.g. inside a specific album), so tapping
     * "Álbumes"/"Sencillos" from inside an artist/album didn't go
     * anywhere new -- you had to back out by hand with the back arrow
     * repeatedly.
     */
    fun setTab(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(
            tab = tab,
            albumsDrill = if (tab == LibraryTab.ALBUMS) rootAlbumsLevel() else _uiState.value.albumsDrill,
            singlesDrill = if (tab == LibraryTab.SINGLES) rootSinglesLevel() else _uiState.value.singlesDrill,
        )
    }

    fun onFilterQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(filterQuery = query)
        recompute()
    }

    // --- Navegación por capas, pestaña Álbumes -----------------------

    /** Nivel raíz de la pestaña Álbumes según el modo de vista activo. */
    private fun rootAlbumsLevel(): AlbumsDrillLevel = when (_uiState.value.albumsViewMode) {
        AlbumsViewMode.BY_LETTER -> AlbumsDrillLevel.Letters
        AlbumsViewMode.FLAT -> AlbumsDrillLevel.ArtistsFlat
    }

    fun selectAlbumsLetter(letter: Char) {
        _uiState.value = _uiState.value.copy(
            albumsDrill = AlbumsDrillLevel.Artists(letter),
        )
    }

    /**
     * Alterna la vista raíz de Álbumes entre Letters y ArtistsFlat.
     * Solo tiene efecto visible si el usuario está en uno de esos dos
     * niveles raíz en ese momento; si está más adentro (Artists,
     * Albums, Tracks) igualmente cambia el modo memorizado, para que
     * el siguiente "atrás" hasta la raíz respete el nuevo modo.
     * ---
     * Toggles the Álbumes root view between Letters and ArtistsFlat.
     * Only has a visible effect if the user is currently at one of
     * those two root levels; if they're deeper in (Artists, Albums,
     * Tracks) it still changes the remembered mode, so the next
     * "back" up to the root respects the new mode.
     */
    fun toggleAlbumsViewMode() {
        val newMode = when (_uiState.value.albumsViewMode) {
            AlbumsViewMode.BY_LETTER -> AlbumsViewMode.FLAT
            AlbumsViewMode.FLAT -> AlbumsViewMode.BY_LETTER
        }
        val current = _uiState.value.albumsDrill
        val newDrill = when (current) {
            is AlbumsDrillLevel.Letters, is AlbumsDrillLevel.ArtistsFlat ->
                if (newMode == AlbumsViewMode.FLAT) {
                    AlbumsDrillLevel.ArtistsFlat
                } else {
                    AlbumsDrillLevel.Letters
                }
            else -> current
        }
        _uiState.value = _uiState.value.copy(
            albumsViewMode = newMode,
            albumsDrill = newDrill,
        )
    }

    /** Entra en la lista plana de álbumes favoritos -- ver AlbumsDrillLevel.FavoriteAlbums. */
    fun selectFavoriteAlbums() {
        _uiState.value = _uiState.value.copy(
            albumsDrill = AlbumsDrillLevel.FavoriteAlbums,
        )
    }

    fun selectAlbumsArtist(artist: String) {
        _uiState.value = _uiState.value.copy(
            albumsDrill = AlbumsDrillLevel.Albums(artist),
        )
    }

    fun selectAlbumsAlbum(artist: String, album: String, fromFavorites: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            albumsDrill = AlbumsDrillLevel.Tracks(artist, album, fromFavorites),
        )
    }

    /** Sube un nivel en la navegación de Álbumes; en Letras no hace nada. */
    fun backAlbumsDrill(): Boolean {
        val current = _uiState.value.albumsDrill
        val newLevel = when (current) {
            is AlbumsDrillLevel.Letters -> return false
            is AlbumsDrillLevel.ArtistsFlat -> return false
            is AlbumsDrillLevel.FavoriteAlbums -> AlbumsDrillLevel.Letters
            is AlbumsDrillLevel.Artists -> AlbumsDrillLevel.Letters
            is AlbumsDrillLevel.Albums -> if (_uiState.value.albumsViewMode == AlbumsViewMode.FLAT) {
                AlbumsDrillLevel.ArtistsFlat
            } else {
                AlbumsDrillLevel.Artists(sortLetterFor(current.artist))
            }
            is AlbumsDrillLevel.Tracks -> if (current.fromFavorites) {
                AlbumsDrillLevel.FavoriteAlbums
            } else {
                AlbumsDrillLevel.Albums(current.artist)
            }
        }
        _uiState.value = _uiState.value.copy(albumsDrill = newLevel)
        return true
    }

    // --- Navegación por capas, pestaña Sencillos ---------------------

    /** Nivel raíz de Sencillos según el modo de vista activo. */
    private fun rootSinglesLevel(): SinglesDrillLevel = when (_uiState.value.singlesViewMode) {
        SinglesViewMode.BY_LETTER -> SinglesDrillLevel.Letters
        SinglesViewMode.FLAT -> SinglesDrillLevel.ArtistsFlat
    }

    fun selectSinglesLetter(letter: Char) {
        _uiState.value = _uiState.value.copy(
            singlesDrill = SinglesDrillLevel.Artists(letter),
        )
    }

    /** Alterna la vista raíz de Sencillos entre Letters y ArtistsFlat -- ver toggleAlbumsViewMode. */
    fun toggleSinglesViewMode() {
        val newMode = when (_uiState.value.singlesViewMode) {
            SinglesViewMode.BY_LETTER -> SinglesViewMode.FLAT
            SinglesViewMode.FLAT -> SinglesViewMode.BY_LETTER
        }
        val current = _uiState.value.singlesDrill
        val newDrill = when (current) {
            is SinglesDrillLevel.Letters, is SinglesDrillLevel.ArtistsFlat ->
                if (newMode == SinglesViewMode.FLAT) {
                    SinglesDrillLevel.ArtistsFlat
                } else {
                    SinglesDrillLevel.Letters
                }
            else -> current
        }
        _uiState.value = _uiState.value.copy(
            singlesViewMode = newMode,
            singlesDrill = newDrill,
        )
    }

    fun selectSinglesArtist(artist: String) {
        _uiState.value = _uiState.value.copy(
            singlesDrill = SinglesDrillLevel.Tracks(artist),
        )
    }

    /** Sube un nivel en la navegación de Sencillos; en la raíz no hace nada. */
    fun backSinglesDrill(): Boolean {
        val current = _uiState.value.singlesDrill
        val newLevel = when (current) {
            is SinglesDrillLevel.Letters -> return false
            is SinglesDrillLevel.ArtistsFlat -> return false
            is SinglesDrillLevel.Artists -> SinglesDrillLevel.Letters
            is SinglesDrillLevel.Tracks -> if (_uiState.value.singlesViewMode == SinglesViewMode.FLAT) {
                SinglesDrillLevel.ArtistsFlat
            } else {
                SinglesDrillLevel.Artists(sortLetterFor(current.artist))
            }
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

    /**
     * Aplica una edición manual de artista/álbum a TODAS las pistas de
     * un álbum de golpe (petición de Miguel Ángel, 2026-07-08 --
     * corregir una errata de artista/álbum pista por pista era
     * tedioso y podía dejar el álbum roto a medias si se paraba antes
     * de terminar). El título de cada pista no se toca. Reutiliza
     * TrackFileRelocator pista a pista, igual que editMetadata(); si
     * una reubicación falla a mitad, se detiene ahí y se informa del
     * error -- las pistas ya movidas se quedan movidas, no hay
     * rollback.
     * ---
     * Applies a manual artist/album edit to ALL tracks of an album at
     * once (requested by Miguel Ángel, 2026-07-08 -- fixing an
     * artist/album typo track by track was tedious and could leave
     * the album half-broken if stopped partway through). Each track's
     * title is left untouched. Reuses TrackFileRelocator track by
     * track, same as editMetadata(); if a relocation fails partway,
     * it stops there and reports the error -- tracks already moved
     * stay moved, there's no rollback.
     */
    fun editAlbumMetadata(artist: String, album: String, newArtistRaw: String, newAlbumRaw: String) {
        val tracks = _uiState.value.albumsByArtist[artist]?.get(album) ?: return
        val trimmedArtist = newArtistRaw.trim().ifBlank { artist }
        val newAlbum = newAlbumRaw.trim().ifBlank { album }
        val needsRelocation = trimmedArtist != artist || newAlbum != album

        if (!needsRelocation) return

        viewModelScope.launch {
            val rootUri = storageManager.getRootUri()
            if (tracks.any { it.filePath != null } && rootUri == null) {
                _uiState.value = _uiState.value.copy(
                    editMetadataError = "No se puede mover el álbum: " +
                        "no hay carpeta de almacenamiento elegida.",
                )
                return@launch
            }

            for (track in tracks) {
                var updatedFilePath = track.filePath
                if (track.filePath != null && rootUri != null) {
                    val relocated = withContext(Dispatchers.IO) {
                        trackFileRelocator.relocate(
                            context = context,
                            sourceFilePath = track.filePath!!,
                            rootUri = rootUri,
                            newArtist = trimmedArtist,
                            newAlbum = newAlbum,
                            title = track.title,
                            trackPosition = track.trackPosition,
                        )
                    }
                    if (relocated == null) {
                        _uiState.value = _uiState.value.copy(
                            editMetadataError = "No se pudo mover \"${track.title}\" a la " +
                                "nueva carpeta. El resto del álbum se ha quedado sin editar.",
                        )
                        return@launch
                    }
                    updatedFilePath = relocated
                }
                repository.update(
                    track.copy(
                        artist = trimmedArtist,
                        album = newAlbum,
                        filePath = updatedFilePath,
                    )
                )
            }

            withContext(Dispatchers.IO) {
                cleanupEmptyFolders(artist, album)
            }
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
    fun deleteDownload(activity: Activity, track: SearchResultTrack) {
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(activity) {
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
            if (outcome is com.miguelaetxio.mimoo.data.backup.MutationOutcome.NoConnection) {
                _uiState.value = _uiState.value.copy(
                    syncBlockedMessage = "Sin conexión: no se puede borrar ahora mismo."
                )
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
    fun deleteAlbum(activity: Activity, artist: String, album: String) {
        viewModelScope.launch {
            val tracks = _uiState.value.albumsByArtist[artist]?.get(album)
                ?: return@launch
            val outcome = autoSyncPusher.executeIfConnected(activity) {
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
            }
            if (outcome is com.miguelaetxio.mimoo.data.backup.MutationOutcome.NoConnection) {
                _uiState.value = _uiState.value.copy(
                    syncBlockedMessage = "Sin conexión: no se puede borrar ahora mismo."
                )
                return@launch
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
    fun deleteArtist(activity: Activity, artist: String) {
        viewModelScope.launch {
            val albumTracks = _uiState.value.albumsByArtist[artist]
                ?.values?.flatten() ?: emptyList()
            val singleTracks = _uiState.value.singlesByArtist[artist] ?: emptyList()

            val outcome = autoSyncPusher.executeIfConnected(activity) {
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
            }
            if (outcome is com.miguelaetxio.mimoo.data.backup.MutationOutcome.NoConnection) {
                _uiState.value = _uiState.value.copy(
                    syncBlockedMessage = "Sin conexión: no se puede borrar ahora mismo."
                )
                return@launch
            }

            if (_uiState.value.albumsDrill !is AlbumsDrillLevel.Letters &&
                _uiState.value.albumsDrill !is AlbumsDrillLevel.ArtistsFlat
            ) {
                _uiState.value = _uiState.value.copy(
                    albumsDrill = rootAlbumsLevel(),
                )
            }
            if (_uiState.value.singlesDrill !is SinglesDrillLevel.Letters &&
                _uiState.value.singlesDrill !is SinglesDrillLevel.ArtistsFlat
            ) {
                _uiState.value = _uiState.value.copy(
                    singlesDrill = rootSinglesLevel(),
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

    /**
     * H08 -- fixed to use `SearchNormalizer` (accent-insensitive),
     * same fix applied to the equivalent Playlists filter. Previously
     * only did `trim().lowercase()`, so an accented query like
     * "canción" never matched a track titled "Cancion" (or the other
     * way around) -- a real usability gap, not a design choice worth
     * keeping just because it predates this session.
     * ---
     * H08 -- corregido para usar `SearchNormalizer` (insensible a
     * acentos), mismo arreglo aplicado al filtro equivalente de
     * Playlists. Antes solo hacía `trim().lowercase()`, así que una
     * búsqueda con tilde como "canción" nunca encontraba una pista
     * titulada "Cancion" (o al revés) -- un hueco de usabilidad real,
     * no una decisión de diseño que mereciera conservarse solo por
     * ser anterior a esta sesión.
     */
    private fun recompute() {
        val query = SearchNormalizer.normalize(_uiState.value.filterQuery)

        val filtered = if (query.isEmpty()) {
            allDownloaded
        } else {
            allDownloaded.filter { track ->
                SearchNormalizer.normalize(track.title).contains(query) ||
                    SearchNormalizer.normalize(track.artist ?: track.channelTitle)
                        .contains(query) ||
                    SearchNormalizer.normalize(track.album ?: "").contains(query)
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

        // S010 -- allFavorites, no "filtered" -- filtered viene de
        // allDownloaded (solo lo descargado), y una pista favoritada
        // desde el reproductor/cola puede no estarlo. El filtro de
        // búsqueda (si hay texto en la barra) se aplica igual, sobre
        // el conjunto correcto.
        // ---
        // S010 -- allFavorites, not "filtered" -- filtered comes from
        // allDownloaded (downloaded-only), and a track favorited from
        // the player/queue might not be. The search filter (if any
        // text in the bar) still applies, just over the right set.
        val favorites = (
            if (query.isEmpty()) {
                allFavorites
            } else {
                allFavorites.filter { track ->
                    SearchNormalizer.normalize(track.title).contains(query) ||
                        SearchNormalizer.normalize(track.artist ?: track.channelTitle)
                            .contains(query)
                }
            }
        ).sortedBy { it.title }

        val albumLetters = albumsByArtist.keys
            .map { sortLetterFor(it) }
            .toSortedSet()
            .toList()
        val singleLetters = singlesByArtist.keys
            .map { sortLetterFor(it) }
            .toSortedSet()
            .toList()

        // Solo álbumes marcados favoritos que SIGUEN existiendo en la
        // biblioteca (si se borró un álbum favorito, desaparece de
        // aquí solo -- la fila de favorite_albums queda huérfana pero
        // inofensiva, nunca se muestra). Orden alfabético por título
        // de álbum, cruzando artistas -- "todo exactamente igual"
        // pedido por Miguel Ángel, pero en una lista plana.
        // ---
        // Only albums marked favorite that STILL exist in the library
        // (if a favorite album gets deleted, it just disappears from
        // here -- the favorite_albums row is left orphaned but
        // harmless, never shown). Alphabetical order by album title,
        // across artists -- "everything exactly the same" requested by
        // Miguel Ángel, but in a flat list.
        val favoriteAlbumsFlat = favoriteAlbumKeysSet
            .filter { (artist, album) -> albumsByArtist[artist]?.containsKey(album) == true }
            .sortedBy { (_, album) -> album }

        _uiState.value = _uiState.value.copy(
            albumsByArtist = albumsByArtist,
            singlesByArtist = singlesByArtist,
            favorites = favorites,
            albumLetters = albumLetters,
            singleLetters = singleLetters,
            favoriteAlbumKeys = favoriteAlbumKeysSet,
            favoriteAlbumsFlat = favoriteAlbumsFlat,
        )
    }

    /** Plays a single track from the library (always local). */
    /**
     * S010 -- gana la rama de streaming (reutiliza el mismo patrón que
     * SearchViewModel.playTrack()): una pista favoritada desde el
     * reproductor/cola puede no estar descargada, y la sección
     * "Favoritos" de la Biblioteca ahora la muestra igualmente -- tiene
     * que poder sonar en streaming, no solo aparecer en la lista.
     * ---
     * S010 -- gains the streaming branch (reuses the same pattern as
     * SearchViewModel.playTrack()): a track favorited from the
     * player/queue might not be downloaded, and the Library's
     * "Favorites" section now shows it anyway -- it has to actually
     * play via streaming, not just appear in the list.
     */
    fun playTrack(track: SearchResultTrack) {
        val filePath = track.filePath
        if (filePath != null) {
            playerManager.play(
                filePath,
                track.title,
                isLocal = true,
                artist = track.artist ?: track.channelTitle,
                youtubeId = track.youtubeId,
                channelTitle = track.channelTitle,
                artworkUri = track.coverArtUrl ?: track.thumbnailUrl,
            )
            return
        }

        val remoteUrl = track.youtubeUrl ?: return
        viewModelScope.launch {
            try {
                val streamUrl = streamResolver.resolveAudioStreamUrl(remoteUrl)
                playerManager.play(
                    streamUrl,
                    track.title,
                    isLocal = false,
                    artist = track.artist ?: track.channelTitle,
                    youtubeId = track.youtubeId,
                    channelTitle = track.channelTitle,
                    artworkUri = track.coverArtUrl ?: track.thumbnailUrl,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    mergeResultMessage = "No se pudo reproducir en streaming: ${e.message}",
                )
            }
        }
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
            listOf(
                QueueItem(
                    uri = filePath,
                    title = track.title,
                    isLocal = true,
                    artist = track.artist ?: track.channelTitle,
                    youtubeId = track.youtubeId,
                    channelTitle = track.channelTitle,
                    artworkUri = track.coverArtUrl ?: track.thumbnailUrl,
                )
            )
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
            listOf(
                QueueItem(
                    uri = filePath,
                    title = track.title,
                    isLocal = true,
                    artist = track.artist ?: track.channelTitle,
                    youtubeId = track.youtubeId,
                    channelTitle = track.channelTitle,
                    artworkUri = track.coverArtUrl ?: track.thumbnailUrl,
                )
            )
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
    /**
     * "Reproducir todo" de la pestaña Favoritos (S010) -- ya no se
     * salta en silencio las favoritas sin descargar. Mismo patrón
     * exacto que PlaylistDetailViewModel.playPlaylist(): resuelve el
     * stream de cada pista sin filePath antes de reproducir la cola
     * completa de una vez, con aviso si alguna falla en vez de
     * desaparecer sin más.
     * ---
     * "Play all" for the Favorites tab (S010) -- no longer silently
     * skips non-downloaded favorites. Exact same pattern as
     * PlaylistDetailViewModel.playPlaylist(): resolves each
     * non-downloaded track's stream before playing the full queue at
     * once, with a warning if any fail instead of just vanishing.
     */
    fun playFavorites() {
        playResolvedFavorites(_uiState.value.favorites)
    }

    /** Plays every favorite in random order (pestaña Favoritos). */
    fun playFavoritesShuffled() {
        playResolvedFavorites(_uiState.value.favorites.shuffled())
    }

    private fun playResolvedFavorites(tracks: List<SearchResultTrack>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isResolvingFavorites = true,
                favoritesResolveError = null,
            )
            var resolutionFailures = 0
            val items = tracks.mapNotNull { track ->
                val localPath = track.filePath
                val remoteUrl = track.youtubeUrl
                if (localPath != null) {
                    QueueItem(
                        uri = localPath,
                        title = track.title,
                        isLocal = true,
                        artist = track.artist ?: track.channelTitle,
                        youtubeId = track.youtubeId,
                        channelTitle = track.channelTitle,
                        artworkUri = track.coverArtUrl ?: track.thumbnailUrl,
                    )
                } else if (remoteUrl == null) {
                    resolutionFailures++
                    null
                } else {
                    try {
                        val streamUrl = streamResolver.resolveAudioStreamUrl(remoteUrl)
                        QueueItem(
                            uri = streamUrl,
                            title = track.title,
                            isLocal = false,
                            artist = track.artist ?: track.channelTitle,
                            youtubeId = track.youtubeId,
                            channelTitle = track.channelTitle,
                            artworkUri = track.coverArtUrl ?: track.thumbnailUrl,
                        )
                    } catch (e: Exception) {
                        resolutionFailures++
                        null
                    }
                }
            }
            _uiState.value = _uiState.value.copy(
                isResolvingFavorites = false,
                favoritesResolveError = if (resolutionFailures > 0) {
                    "No se pudieron resolver $resolutionFailures pista(s); se reproduce el resto."
                } else {
                    null
                },
            )
            if (items.isNotEmpty()) {
                playerManager.playQueue(items)
            }
        }
    }

    private fun List<SearchResultTrack>.toQueueItems(): List<QueueItem> =
        mapNotNull { track ->
            track.filePath?.let { path ->
                QueueItem(
                    uri = path,
                    title = track.title,
                    isLocal = true,
                    artist = track.artist ?: track.channelTitle,
                    youtubeId = track.youtubeId,
                    channelTitle = track.channelTitle,
                    artworkUri = track.coverArtUrl ?: track.thumbnailUrl,
                )
            }
        }
}

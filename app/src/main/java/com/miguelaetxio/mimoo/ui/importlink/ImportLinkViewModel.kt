package com.miguelaetxio.mimoo.ui.importlink

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.local.repository.PlaylistRepository
import com.miguelaetxio.mimoo.data.local.repository.PlaylistTrackInput
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.StreamResolver
import com.miguelaetxio.mimoo.data.remote.CoverArtRepository
import com.miguelaetxio.mimoo.data.remote.ExternalLinkResolver
import com.miguelaetxio.mimoo.data.remote.dto.ExternalLinkTrack
import com.miguelaetxio.mimoo.ui.library.UNKNOWN_ARTIST_CREDIT
import com.miguelaetxio.mimoo.ui.library.VARIOUS_ARTISTS_CREDIT
import com.miguelaetxio.mimoo.util.YoutubeTitleCleaner
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
    // álbum/playlist a efectos de UI (multi-selección, carátula) --
    // ver resolveLink(). NO decide por sí solo si se agrupan bajo un
    // álbum al importar -- eso lo decide isOfficialAlbum (S055).
    val isPlaylist: Boolean = false,
    // S055 -- bug real reportado por Miguel Ángel: una PLAYLIST normal
    // de YouTube Music (personal/curada, puede mezclar artistas y
    // álbumes distintos) se trataba igual que un álbum real -- todas
    // sus pistas quedaban agrupadas bajo un álbum falso con el título
    // de la lista. true solo cuando el enlace resuelto es un álbum
    // OFICIAL (ver ExternalLinkResult.isAlbum / _is_official_album()
    // en link_resolver.py) -- solo entonces importSelected() agrupa
    // las pistas bajo `album = resolvedTitle`. Para una playlist
    // normal (aunque tenga varias pistas y aunque todas compartan un
    // único artista real) las pistas se importan como sencillos
    // sueltos, salvo que el usuario fije un álbum a mano en el diálogo
    // de confirmación.
    val isOfficialAlbum: Boolean = false,
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
    savedStateHandle: SavedStateHandle,
    private val externalLinkResolver: ExternalLinkResolver,
    private val searchResultTrackRepository: SearchResultTrackRepository,
    private val downloadQueueManager: DownloadQueueManager,
    private val streamResolver: StreamResolver,
    private val playerManager: PlayerManager,
    private val coverArtRepository: CoverArtRepository,
    // S056 -- petición explícita de Miguel Ángel tras S055: al
    // importar una playlist NORMAL (no un álbum oficial), sus pistas
    // ya no se agrupan bajo un álbum falso -- pero tampoco deben
    // quedar sueltas sin ninguna relación entre sí. Se crea una lista
    // de MiMoo de verdad con el título de la playlist, ver
    // importSelected().
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportLinkUiState())
    val uiState: StateFlow<ImportLinkUiState> = _uiState.asStateFlow()

    init {
        // H08 PARTE 1 (S009) -- si se llega aquí desde un resultado de
        // búsqueda de playlist/canal (SearchScreen), la url ya viene
        // resuelta por navegación: se prellena y se resuelve sola, sin
        // que el usuario tenga que pegarla ni pulsar "Ver contenido
        // del enlace" -- el flujo de pegar a mano sigue igual cuando
        // no llega ninguna url (navegación normal desde el drawer).
        // ---
        // H08 PARTE 1 (S009) -- if reached from a playlist/channel
        // search result (SearchScreen), the url already arrives via
        // navigation: it's pre-filled and auto-resolved, without the
        // user having to paste it or tap "Ver contenido del enlace" --
        // the paste-by-hand flow stays identical when no url arrives
        // (normal navigation from the drawer).
        val initialUrl: String? = savedStateHandle["url"]
        if (!initialUrl.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(url = initialUrl)
            resolveLink()
        }
    }

    fun onUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(url = url)
    }

    /**
     * S049 -- petición explícita de Miguel Ángel: falta un aspa para
     * borrar el enlace anterior y pegar uno nuevo. Resetea TODO el
     * estado a `ImportLinkUiState()` en blanco -- no solo la url, sino
     * también las pistas/selección/carátula ya resueltas del enlace
     * anterior, para que la pantalla quede exactamente como al entrar
     * por primera vez.
     */
    fun clearUrl() {
        _uiState.value = ImportLinkUiState()
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
                val cleanedTitle = YoutubeTitleCleaner.clean(result.title)
                _uiState.value = _uiState.value.copy(
                    isResolving = false,
                    resolvedTitle = cleanedTitle,
                    isPlaylist = isPlaylist,
                    // S055 -- solo tiene sentido cuando isPlaylist ya
                    // es true (un vídeo suelto siempre trae isAlbum en
                    // false desde el propio resolver).
                    isOfficialAlbum = result.isAlbum,
                    tracks = result.tracks,
                    // Todas seleccionadas por defecto -- el usuario
                    // desmarca las que no quiere en vez de tener que
                    // marcar una a una las que sí (caso común: quiere
                    // el album entero).
                    selectedYoutubeIds = result.tracks.map { it.youtubeId }.toSet(),
                )
                if (isPlaylist) {
                    resolveCoverArt(cleanedTitle, result.tracks.map { it.channelTitle })
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
            // CoverArtRepository.resolveCoverArtUrl() documenta que
            // nunca lanza -- pero esta es una corrutina "dispara y
            // olvida" (nadie hace join/await de ella), y una
            // excepcion no capturada aqui se lleva la app entera por
            // delante (viewModelScope no instala ningun
            // CoroutineExceptionHandler). La caratula es un detalle
            // cosmetico, nunca vale la pena cerrar la app por ella --
            // blindaje explicito tras el reporte de cierre de Miguel
            // Angel (2026-07-02) que coincide exactamente con "es
            // lista" (unico caso en que esta funcion se llama).
            try {
                val url = coverArtRepository.resolveCoverArtUrl(artist, album)
                if (url != null) {
                    _uiState.value = _uiState.value.copy(coverArtUrl = url)
                }
            } catch (e: Exception) {
                // Sin caratula, sin romper nada -- no es un error que
                // el usuario necesite ver.
            }
        }
    }

    /**
     * Returns the single shared, non-blank channel title, or null if
     * there is more than one distinct real channel, or if none of
     * them carry real channel info (link_resolver.py ya normaliza un
     * uploader "-" de YouTube a "" — ver PASO de esa función — así
     * que un canal en blanco aquí siempre significa "YouTube no dio
     * esa información", nunca un nombre real).
     * ---
     * Devuelve el único canal compartido y no vacío, o null si hay
     * más de un canal real distinto, o si ninguno trae información
     * real de canal (link_resolver.py ya normaliza un uploader "-" de
     * YouTube a "" — ver esa función — así que un canal en blanco
     * aquí siempre significa "YouTube no dio esa información", nunca
     * un nombre real).
     */
    private fun dominantArtist(channelTitles: List<String>): String? =
        channelTitles.filter { it.isNotBlank() }.distinct().singleOrNull()

    /**
     * Called only when dominantArtist() returned null — decide cuál
     * de los dos fallbacks aplica. Si ningún canal trae info real
     * (todos en blanco), esto no es una compilación, es simplemente
     * que YouTube no dio el dato — UNKNOWN_ARTIST_CREDIT. Si hay
     * varios canales reales y distintos, sí es una compilación
     * genuina — VARIOUS_ARTISTS_CREDIT, como antes.
     * ---
     * Called only when dominantArtist() returned null — decides which
     * of the two fallbacks applies. If no channel carries real info
     * (all blank), this isn't a compilation, YouTube just didn't give
     * the data — UNKNOWN_ARTIST_CREDIT. If there are several distinct
     * real channels, it is a genuine compilation — VARIOUS_ARTISTS_CREDIT,
     * as before.
     */
    private fun fallbackArtistCredit(channelTitles: List<String>): String =
        if (channelTitles.all { it.isBlank() }) {
            UNKNOWN_ARTIST_CREDIT
        } else {
            VARIOUS_ARTISTS_CREDIT
        }

    /**
     * True cuando la selección actual no tiene un artista real y
     * único determinable — es decir, cuando importSelected() caería a
     * fallbackArtistCredit(). Usado por ImportLinkScreen para
     * preguntar Artista/Álbum antes de descargar en vez de guardar
     * "Artista desconocido"/"Various Artists" en silencio (Miguel
     * Ángel, 2026-07-03). Solo mira el artista, nunca el álbum — un
     * álbum sin asignar en un vídeo suelto es el comportamiento
     * normal (sencillo), no un dato desconocido que preguntar.
     * ---
     * True when the current selection has no determinable single real
     * artist — i.e. when importSelected() would fall back to
     * fallbackArtistCredit(). Used by ImportLinkScreen to ask for
     * Artista/Álbum before downloading instead of silently storing
     * "Artista desconocido"/"Various Artists" (Miguel Ángel,
     * 2026-07-03). Only looks at the artist, never the album — a
     * missing album on a single video is normal behaviour (sencillo),
     * not unknown data worth asking about.
     */
    fun needsArtistConfirmation(): Boolean {
        val state = _uiState.value
        val selected = state.tracks.filter { it.youtubeId in state.selectedYoutubeIds }
        return dominantArtist(selected.map { it.channelTitle }) == null
    }

    /**
     * Best-guess artist/álbum to pre-fill the confirmation dialog with
     * — same values importSelected() would use automatically, so
     * confirming without editing behaves identically to not asking at
     * all.
     * ---
     * Mejor estimación de artista/álbum para prellenar el diálogo de
     * confirmación — los mismos valores que usaría importSelected()
     * automáticamente, así que confirmar sin editar se comporta igual
     * que no preguntar en absoluto.
     */
    fun defaultArtistAndAlbum(): Pair<String, String> {
        val state = _uiState.value
        val selected = state.tracks.filter { it.youtubeId in state.selectedYoutubeIds }
        val artist = dominantArtist(selected.map { it.channelTitle })
            ?: fallbackArtistCredit(selected.map { it.channelTitle })
        // S055 -- solo se prellena un álbum cuando el enlace es un
        // álbum OFICIAL de YouTube Music, no cualquier playlist
        // multi-pista -- ver el comentario de isOfficialAlbum.
        val album = if (state.isPlaylist && state.isOfficialAlbum) state.resolvedTitle.orEmpty() else ""
        return artist to album
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
                val items = selected.mapNotNull { track ->
                    val streamUrl = streamResolver.resolveAudioStreamUrl(
                        "https://youtu.be/${track.youtubeId}",
                    )
                    // S027 -- antes era `artist = track.channelTitle`
                    // directo, sin condición ninguna -- el canal se
                    // reproducía tal cual como si fuera el artista.
                    // Ahora resolución verificada (artista
                    // estructurado si lo hay, si no
                    // identifyFromTitleWords contra MusicBrainz); si
                    // no identifica nada, la pista se excluye del
                    // streaming en vez de inventar un artista.
                    playerManager.resolveStreamItem(
                        streamUrl = streamUrl,
                        videoTitle = track.title,
                        // ExternalLinkTrack no tiene artista
                        // estructurado (es el resultado crudo de
                        // resolver el enlace, antes de cualquier
                        // emparejamiento) -- resolveStreamItem cae
                        // directo a identifyFromTitleWords().
                        structuredArtist = null,
                        youtubeId = track.youtubeId,
                        artworkUri = track.thumbnailUrl,
                    )
                }
                playerManager.playQueue(items)
                _uiState.value = _uiState.value.copy(
                    isResolvingQueue = false,
                    errorMessage = if (items.size < selected.size) {
                        "No se pudo identificar el artista de " +
                            "${selected.size - items.size} pista(s); se reproduce el resto."
                    } else {
                        null
                    },
                )
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
     * the resolved link is an OFFICIAL YouTube Music album
     * (isOfficialAlbum, S055) — a single-video link is a sencillo, and
     * a regular multi-track playlist (even a curated single-channel
     * one) is NOT an album just because it has several tracks.
     *
     * S056 -- a regular playlist's tracks are NOT left as unrelated
     * loose singles either: a real MiMoo Playlist gets created (named
     * after the resolved title) and every imported track is added to
     * it, unless the user explicitly typed an album name
     * (albumOverride) — in that case they chose to treat it as an
     * album instead, so no playlist is created.
     *
     * artistOverride/albumOverride (Miguel Ángel, 2026-07-03): when
     * the screen detected no real artist could be determined
     * (needsArtistConfirmation()) and prompted the user, these carry
     * what they typed — applied to every selected track at once, same
     * as the automatic assignment would have been.
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
     * cuando el enlace resuelto es un álbum OFICIAL de YouTube Music
     * (isOfficialAlbum, S055) — un enlace de un solo vídeo es un
     * sencillo, y una playlist normal con varias pistas (aunque sea
     * curada de un solo canal) NO es un álbum solo por tener varias
     * pistas.
     *
     * S056 -- las pistas de una playlist normal TAMPOCO se dejan
     * sueltas sin ninguna relación entre sí: se crea una Lista de
     * MiMoo de verdad (con el título resuelto) y se añaden a ella
     * todas las pistas importadas, salvo que el usuario haya escrito
     * a mano un nombre de álbum (albumOverride) -- en ese caso eligió
     * tratarlo como álbum en su lugar, y no se crea ninguna lista.
     *
     * artistOverride/albumOverride (Miguel Ángel, 2026-07-03): cuando
     * la pantalla detecta que no se pudo determinar un artista real
     * (needsArtistConfirmation()) y se le pregunta al usuario, aquí
     * llega lo que escribió — se aplica a todas las pistas
     * seleccionadas de golpe, igual que haría la asignación
     * automática.
     */
    fun importSelected(artistOverride: String? = null, albumOverride: String? = null) {
        val state = _uiState.value
        val selected = state.tracks.filter { it.youtubeId in state.selectedYoutubeIds }
        if (selected.isEmpty()) return

        val artist = artistOverride?.trim()?.takeIf { it.isNotBlank() }
            ?: dominantArtist(selected.map { it.channelTitle })
            ?: fallbackArtistCredit(selected.map { it.channelTitle })
        val album = albumOverride?.trim()?.takeIf { it.isNotBlank() }
            // S055 -- solo se agrupa bajo un álbum cuando el enlace es
            // un álbum OFICIAL de YouTube Music, no cualquier playlist
            // multi-pista -- ver el comentario de isOfficialAlbum. Una
            // playlist normal (aunque comparta un único canal real)
            // importa sus pistas como sencillos sueltos, salvo
            // albumOverride explícito del usuario.
            ?: if (state.isPlaylist && state.isOfficialAlbum) state.resolvedTitle else null
        // Enlace pegado por el usuario, guardado tal cual para poder
        // compartirlo después por WhatsApp -- petición explícita de
        // Miguel Ángel (2026-07-04). Mismo enlace para todas las
        // pistas de este import (sea un vídeo suelto o una playlist
        // entera), ya que todas vinieron de la misma URL.
        // ---
        // Link pasted by the user, saved as-is so it can be shared
        // later via WhatsApp -- explicit request from Miguel Ángel
        // (2026-07-04). Same link for every track in this import
        // (whether a single video or a whole playlist), since they all
        // came from the same URL.
        val sourceUrl = state.url.trim().takeIf { it.isNotBlank() }

        viewModelScope.launch {
            val tracks = selected.map { track ->
                SearchResultTrack(
                    youtubeId = track.youtubeId,
                    title = YoutubeTitleCleaner.clean(track.title),
                    channelTitle = track.channelTitle,
                    durationSeconds = track.durationSeconds,
                    thumbnailUrl = track.thumbnailUrl,
                    artist = artist,
                    album = album,
                    sourceUrl = sourceUrl,
                    // Posición real dentro de la lista RESUELTA
                    // (state.tracks), no del subconjunto seleccionado
                    // -- así, si el usuario deselecciona una pista, el
                    // resto conserva su posición real de disco en vez
                    // de recomprimirse.
                    trackPosition = state.tracks.indexOfFirst {
                        it.youtubeId == track.youtubeId
                    }.takeIf { it >= 0 },
                )
            }
            searchResultTrackRepository.cacheSearchResults(tracks)
            tracks.forEach { track ->
                downloadQueueManager.enqueue(
                    youtubeId = track.youtubeId,
                    title = track.title,
                    artist = track.artist ?: track.channelTitle,
                    album = track.album,
                    trackPosition = track.trackPosition,
                )
            }
            // S056 -- petición explícita de Miguel Ángel tras S055: una
            // playlist normal (no álbum oficial) no debe quedar como
            // pistas sueltas sin relación entre sí solo porque ya no se
            // agrupan bajo un álbum falso. Se crea una Lista de MiMoo
            // de verdad con el título resuelto, y se añaden las pistas
            // importadas en el mismo orden en que llegaron. No se crea
            // si el usuario escribió a mano un álbum (albumOverride) --
            // eso significa que prefirió tratarlo como álbum.
            val userChoseAlbumOverride = !albumOverride?.trim().isNullOrBlank()
            if (state.isPlaylist && !state.isOfficialAlbum && !userChoseAlbumOverride) {
                val playlistName = state.resolvedTitle?.takeIf { it.isNotBlank() }
                    ?: "Lista importada"
                val playlistId = playlistRepository.createPlaylist(playlistName)
                playlistRepository.addTracksToPlaylist(
                    playlistId,
                    tracks.map { track ->
                        PlaylistTrackInput(
                            youtubeId = track.youtubeId,
                            title = track.title,
                            artist = track.artist,
                        )
                    },
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
            // S050 -- petición explícita de Miguel Ángel: tras
            // importar, quitar de la vista lo descargado y vaciar el
            // campo de enlace, para evitar descargar dos veces lo
            // mismo por despiste. Se resetea TODO el estado a un
            // ImportLinkUiState() en blanco salvo importedCount (así
            // el diálogo "Importado" se sigue mostrando encima de una
            // pantalla ya limpia, no de la lista recién importada).
            _uiState.value = ImportLinkUiState(importedCount = tracks.size)
        }
    }

    fun dismissImportedDialog() {
        _uiState.value = _uiState.value.copy(importedCount = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

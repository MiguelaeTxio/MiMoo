package com.miguelaetxio.mimoo.ui.downloads

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.local.repository.TrackAlternativeRepository
import com.miguelaetxio.mimoo.data.remote.ExternalLinkResolver
import com.miguelaetxio.mimoo.data.remote.dto.ExternalLinkTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Cuánto de "completadas recientes" mostrar — sección de referencia
 * rápida, no un sustituto de Biblioteca (que sigue siendo la fuente
 * completa de lo descargado).
 * ---
 * How many "recently completed" downloads to show — a quick-reference
 * section, not a substitute for Biblioteca (still the full source of
 * truth for what's downloaded).
 */
private const val RECENTLY_COMPLETED_LIMIT = 15

data class DownloadsUiState(
    // QUEUED primero, DOWNLOADING después -- dentro de cada grupo,
    // más reciente primero (mismo orden que getActiveDownloads()).
    val queued: List<SearchResultTrack> = emptyList(),
    val downloading: List<SearchResultTrack> = emptyList(),
    val recentlyCompleted: List<SearchResultTrack> = emptyList(),
    // Hueco real detectado por Miguel Ángel (2026-07-03): antes de
    // esto, una descarga con ERROR era invisible en TODA la app --
    // ni aquí ni en ImportLinkScreen/AlbumSearchScreen había forma de
    // saber que algo había fallado en silencio.
    val failed: List<SearchResultTrack> = emptyList(),
)

/**
 * Fix real (2026-07-24, petición explícita de Miguel Ángel, motivada
 * por "River Euphrates" de Pixies -- ver TrackAlternativeRepository
 * para el diseño completo): estado del diálogo "Buscar alternativa"
 * para una pista con ERROR permanente. `query` empieza como el título
 * exacto de la pista y es editable por el usuario (p.ej. quitar
 * "Remaster 2007" del texto) antes de lanzar la búsqueda.
 * `targetTrack` es la fila fallida que se sustituirá si el usuario
 * elige un resultado -- `null` significa que el diálogo está cerrado.
 */
data class AlternativeSearchUiState(
    val targetTrack: SearchResultTrack? = null,
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<ExternalLinkTrack> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * ViewModel for the "Descargas" screen — the single place to see
 * everything with a download in flight (QUEUED/DOWNLOADING, with real
 * progress) plus a short list of what just finished. Exists because
 * neither Biblioteca (solo DONE) ni las pantallas de origen
 * (AlbumSearchScreen/ImportLinkScreen, solo mientras estás en ellas)
 * dan una vista persistente de "qué está pasando con mis descargas
 * ahora mismo" — bug real reportado por Miguel Ángel en verificación
 * de PASO 6c (H05): "descargar todo" no mostraba ni spinners ni
 * progreso, y salir de la pantalla lo hacía desaparecer del todo.
 * ---
 * ViewModel de la pantalla "Descargas" — el único sitio para ver todo
 * lo que tiene una descarga en marcha (QUEUED/DOWNLOADING, con
 * progreso real) más una lista corta de lo recién terminado. Existe
 * porque ni Biblioteca (solo DONE) ni las pantallas de origen
 * (AlbumSearchScreen/ImportLinkScreen, solo mientras estás en ellas)
 * dan una vista persistente de "qué está pasando con mis descargas
 * ahora mismo" — bug real reportado por Miguel Ángel en verificación
 * de PASO 6c (H05): "descargar todo" no mostraba ni spinners ni
 * progreso, y salir de la pantalla lo hacía desaparecer del todo.
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: SearchResultTrackRepository,
    private val downloadQueueManager: DownloadQueueManager,
    private val externalLinkResolver: ExternalLinkResolver,
    private val trackAlternativeRepository: TrackAlternativeRepository,
    private val autoSyncPusher: AutoSyncPusher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val _alternativeSearchState = MutableStateFlow(AlternativeSearchUiState())
    val alternativeSearchState: StateFlow<AlternativeSearchUiState> = _alternativeSearchState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getActiveDownloads().collect { tracks ->
                _uiState.value = _uiState.value.copy(
                    queued = tracks.filter {
                        it.downloadStatus == DownloadStatus.QUEUED
                    },
                    downloading = tracks.filter {
                        it.downloadStatus == DownloadStatus.DOWNLOADING
                    },
                )
            }
        }
        viewModelScope.launch {
            repository.getByStatus(DownloadStatus.DONE).collect { tracks ->
                _uiState.value = _uiState.value.copy(
                    recentlyCompleted = tracks.take(RECENTLY_COMPLETED_LIMIT),
                )
            }
        }
        viewModelScope.launch {
            repository.getByStatus(DownloadStatus.ERROR).collect { tracks ->
                _uiState.value = _uiState.value.copy(failed = tracks)
            }
        }
    }

    /**
     * Reintenta una descarga fallida — mismo mecanismo que
     * SearchScreen.DownloadButton en ERROR (downloadQueueManager.enqueue()
     * marca QUEUED y vuelve a encolar el Worker).
     * ---
     * Retries a failed download — same mechanism as
     * SearchScreen.DownloadButton on ERROR (downloadQueueManager.enqueue()
     * marks QUEUED and re-enqueues the Worker).
     */
    fun retry(track: SearchResultTrack) {
        viewModelScope.launch {
            enqueueRetry(track)
        }
    }

    /**
     * Reintenta TODAS las descargas fallidas de golpe -- petición
     * explícita de Miguel Ángel (2026-07-06): "es un coñazo estar
     * reintentando una por una... 36 de 100 títulos han fallado".
     * ---
     * Retries ALL failed downloads at once -- explicit request from
     * Miguel Ángel (2026-07-06): "it's a pain having to retry one by
     * one... 36 out of 100 titles failed".
     */
    fun retryAll() {
        viewModelScope.launch {
            _uiState.value.failed.forEach { track ->
                enqueueRetry(track)
            }
        }
    }

    private suspend fun enqueueRetry(track: SearchResultTrack) {
        downloadQueueManager.enqueue(
            youtubeId = track.youtubeId,
            title = track.title,
            artist = track.artist ?: track.channelTitle,
            album = track.album,
            trackPosition = track.trackPosition,
        )
    }

    /**
     * Borra una descarga fallida de forma DEFINITIVA -- para las que
     * fallan siempre, sin importar cuánto se espere o se reintente.
     * Petición explícita de Miguel Ángel (2026-07-06): "hay algunas
     * que fallan siempre... sería conveniente poder borrar esas
     * descargas fallidas para no reintentarlas más". A diferencia de
     * retry(), esto borra la fila entera de Room -- no queda ningún
     * rastro que pueda reintentarse por error más adelante. Si
     * Miguel Ángel quiere esa pista en el futuro, tendrá que buscarla/
     * importarla de nuevo desde cero.
     * ---
     * Permanently deletes a failed download -- for the ones that
     * always fail, no matter how long you wait or how many times you
     * retry. Explicit request from Miguel Ángel (2026-07-06): "some
     * always fail... it would help to be able to delete those failed
     * downloads so they don't get retried anymore". Unlike retry(),
     * this deletes the whole Room row -- no trace is left that could
     * accidentally get retried later. If Miguel Ángel wants that track
     * in the future, he'll have to search/import it again from
     * scratch.
     */
    fun deleteFailed(track: SearchResultTrack) {
        viewModelScope.launch {
            repository.delete(track)
        }
    }

    /**
     * Abre el diálogo "Buscar alternativa" para una pista fallida --
     * query inicial = título exacto de la pista, editable por el
     * usuario antes de buscar (petición textual de Miguel Ángel:
     * poder quitar "Remaster 2007" del texto, por ejemplo).
     */
    fun openAlternativeSearch(track: SearchResultTrack) {
        _alternativeSearchState.value = AlternativeSearchUiState(
            targetTrack = track,
            query = track.title,
        )
    }

    fun updateAlternativeQuery(query: String) {
        _alternativeSearchState.value = _alternativeSearchState.value.copy(query = query)
    }

    fun dismissAlternativeSearch() {
        _alternativeSearchState.value = AlternativeSearchUiState()
    }

    /**
     * Lanza la búsqueda en YouTube con el texto editado -- mismo
     * mecanismo que UnifiedSearchViewModel.searchSongs()
     * (ExternalLinkResolver.searchYoutube()), reutilizado tal cual.
     */
    fun searchAlternatives() {
        val query = _alternativeSearchState.value.query.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            _alternativeSearchState.value = _alternativeSearchState.value.copy(
                isSearching = true,
                errorMessage = null,
            )
            try {
                val results = externalLinkResolver.searchYoutube(query, limit = 15).tracks
                _alternativeSearchState.value = _alternativeSearchState.value.copy(
                    isSearching = false,
                    results = results,
                )
            } catch (e: Exception) {
                _alternativeSearchState.value = _alternativeSearchState.value.copy(
                    isSearching = false,
                    results = emptyList(),
                    errorMessage = e.message ?: "No se pudo buscar en YouTube.",
                )
            }
        }
    }

    /**
     * El usuario elige uno de los resultados: sustituye la fuente de
     * la pista fallida (TrackAlternativeRepository, preserva álbum/
     * artista/posición/favorito/playlists) y encola la descarga de
     * inmediato con el youtubeId nuevo -- mismo mecanismo de sync que
     * cualquier otra mutación (H07 PARTE 1, AutoSyncPusher).
     */
    fun chooseAlternative(activity: Activity, alternative: ExternalLinkTrack) {
        val target = _alternativeSearchState.value.targetTrack ?: return
        viewModelScope.launch {
            autoSyncPusher.executeIfConnected(activity) {
                val replacement = trackAlternativeRepository.replaceFailedTrackSource(
                    original = target,
                    alternative = alternative,
                )
                downloadQueueManager.enqueue(
                    youtubeId = replacement.youtubeId,
                    title = replacement.title,
                    artist = replacement.artist ?: replacement.channelTitle,
                    album = replacement.album,
                    trackPosition = replacement.trackPosition,
                )
            }
            dismissAlternativeSearch()
        }
    }
}

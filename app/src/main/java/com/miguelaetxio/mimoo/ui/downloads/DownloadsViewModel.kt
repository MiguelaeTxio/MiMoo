package com.miguelaetxio.mimoo.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

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
    }
}

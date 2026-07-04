package com.miguelaetxio.mimoo.data.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pequeño puente entre MainActivity (donde corre la reconciliación
 * automática de arranque) y Biblioteca (donde se muestra el aviso) --
 * petición explícita de Miguel Ángel (2026-07-04): "avisar
 * directamente al usuario, en el inicio: se han detectado carpetas
 * vacías... se han borrado y se ha dejado el sistema limpio".
 *
 * MainActivity llama a post() tras rescan(); LibraryViewModel observa
 * el StateFlow y lo copia a su uiState para que LibraryScreen lo
 * muestre como Snackbar una sola vez (consume() lo vacía).
 * ---
 * Small bridge between MainActivity (where the automatic startup
 * reconciliation runs) and Biblioteca (where the notice is shown) --
 * explicit request from Miguel Ángel (2026-07-04): "notify the user
 * directly, at startup: empty folders were detected... they were
 * deleted and the system was left clean".
 *
 * MainActivity calls post() after rescan(); LibraryViewModel observes
 * the StateFlow and copies it into its uiState so LibraryScreen shows
 * it as a Snackbar exactly once (consume() clears it).
 */
@Singleton
class StartupNotices @Inject constructor() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun post(text: String) {
        _message.value = text
    }

    fun consume() {
        _message.value = null
    }
}

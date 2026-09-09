package com.miguelaetxio.mimoo.data.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S075 -- petición explícita de Miguel Ángel: "debemos incluir un
 * botón para pausar las descargas por si necesitamos ancho de banda,
 * concretamente he tenido que cerrar la APP y descargar desde el
 * servidor la nueva versión pq desde ajustes/actualizaciones era
 * imposible ya que no daba comienzo la descarga siquiera pq está
 * totalmente ocupado el ancho de banda por las descargas."
 *
 * `@Singleton` compartido por todo el proceso -- todos los
 * `DownloadWorker` (potencialmente varios a la vez, ver
 * `DownloadConcurrencyLimiter`) consultan el mismo estado. Pausar no
 * cancela nada en curso a media transferencia (`yt-dlp` corre
 * bloqueante vía Chaquopy, no hay forma limpia de interrumpirlo a
 * mitad sin arriesgar un archivo a medias) -- impide que se ARRANQUEN
 * nuevas transferencias mientras está pausado. Con el tope de 3
 * simultáneas de `DownloadConcurrencyLimiter`, como mucho hay 3
 * transferencias reales terminando de su propia cuenta tras pausar,
 * nunca más -- el ancho de banda se libera en cuestión de segundos,
 * no de minutos.
 */
@Singleton
class DownloadPauseController @Inject constructor() {
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    fun setPaused(paused: Boolean) {
        _isPaused.value = paused
    }

    /**
     * Se queda suspendido aquí, sin coste, mientras esté pausado --
     * ver el punto de uso en `DownloadWorker.doWork()`, justo antes de
     * arrancar la transferencia real.
     */
    suspend fun awaitUnpaused() {
        isPaused.first { !it }
    }
}

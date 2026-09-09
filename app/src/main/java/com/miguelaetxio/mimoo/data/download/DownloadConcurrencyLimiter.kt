package com.miguelaetxio.mimoo.data.download

import kotlinx.coroutines.sync.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S074 -- petición explícita de Miguel Ángel: "las descargas masivas
 * tardan mucho... si me descargo una lista con 400 temas, se ponen
 * demasiados descargando a la vez y enlentece mucho, es mejor
 * descargar en tandas de X archivos, lo que juzguemos más eficiente
 * para racionalizar el consumo de ancho de banda."
 *
 * Causa real: `DownloadWorker.doWork()` corre en `Dispatchers.IO`
 * (S049, pool de 64 hilos, pensado justo para E/S bloqueante) SIN
 * ningún límite de concurrencia propio -- WorkManager tampoco impone
 * ninguno (ver `MiMooApp.workManagerConfiguration`, sin
 * `setExecutor()` ni límite de tandas). Con una importación grande,
 * decenas de descargas reales (red, yt-dlp) podían arrancar a la vez,
 * todas compitiendo por el mismo ancho de banda -- cada una más lenta
 * cuantas más hubiera a la vez, en vez de repartirse en tandas.
 *
 * Semáforo compartido -- un único `@Singleton` para todo el proceso,
 * ya que todos los `DownloadWorker` viven en el mismo proceso de la
 * app. Como mucho `MAX_CONCURRENT_DOWNLOADS` descargas reales corren
 * a la vez; el resto espera su turno en cola, SUSPENDIDAS sin
 * consumir ancho de banda ni CPU mientras esperan (no es un bucle de
 * sondeo -- `Semaphore.acquire()`/`withPermit()` es una espera real de
 * coroutine, sin coste mientras no le toca).
 * ---
 * S074 -- explicit request from Miguel Ángel: "bulk downloads take a
 * long time... if I download a 400-track playlist, too many start
 * downloading at once and it slows way down, it's better to download
 * in batches of X files, whatever we judge most efficient to
 * rationalize bandwidth usage."
 *
 * Real cause: `DownloadWorker.doWork()` runs on `Dispatchers.IO`
 * (S049, a 64-thread pool meant precisely for blocking I/O) with NO
 * concurrency limit of its own -- WorkManager doesn't impose one
 * either (see `MiMooApp.workManagerConfiguration`, no `setExecutor()`
 * or batch limit). With a large import, dozens of real downloads
 * (network, yt-dlp) could start at once, all competing for the same
 * bandwidth -- each one slower the more there were at once, instead
 * of being spread into batches.
 *
 * Shared semaphore -- a single process-wide `@Singleton`, since every
 * `DownloadWorker` lives in the same app process. At most
 * `MAX_CONCURRENT_DOWNLOADS` real downloads run at once; the rest
 * wait their turn in a queue, SUSPENDED without consuming bandwidth
 * or CPU while they wait (not a polling loop --
 * `Semaphore.acquire()`/`withPermit()` is a real coroutine suspension,
 * costing nothing while it isn't its turn).
 */
@Singleton
class DownloadConcurrencyLimiter @Inject constructor() {
    companion object {
        /**
         * Descargas reales simultáneas -- 3 como término medio
         * razonable entre "aprovechar la conexión" y "no saturarla
         * con decenas a la vez". Ajustable aquí si Miguel Ángel quiere
         * otro número tras probarlo en uso real.
         */
        const val MAX_CONCURRENT_DOWNLOADS = 3
    }

    val semaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)
}

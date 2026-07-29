package com.miguelaetxio.mimoo.data.remote

import com.miguelaetxio.mimoo.data.local.dao.SearchResultTrackDao
import com.miguelaetxio.mimoo.util.SearchNormalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * S025 -- CONSTRUCTOR DE LA BASE DE DATOS DEL ANCLA.
 *
 * Orden de Miguel Ángel: *"el botón de ajustes, que es fundamental, el
 * de creación de base de datos. Que hacemos una aplicación para hacer
 * el diccionario, que se dedique única y exclusivamente a hacer el
 * diccionario. Que dentro de ajustes un lanzador que diga 'hacer
 * diccionario' y empezar grupo por grupo, canción por canción, hasta
 * que tengamos los dos mil millones de canciones metidas en una base de
 * datos."*
 *
 * **Por qué hace falta.** Sin esto el diccionario solo crece cuando la
 * Radio tropieza con un artista, de tres en tres por vuelta. Eso llena
 * la tarjeta al ritmo al que se escucha música, que es lentísimo. Este
 * recorrido lo hace de golpe y sin depender de que suene nada.
 *
 * **Qué recorre, y en este orden:**
 *   1. El cajón de sin red -- lo que ya se sabe que falta.
 *   2. Los artistas de la biblioteca local descargada. Son los que más
 *      probabilidades tienen de arrancar una Radio, porque son la
 *      música que Miguel Ángel escucha de verdad.
 *   3. Los artistas de la semilla a los que les falta el país.
 *
 * Se salta lo que ya está resuelto: preguntar de nuevo por algo que ya
 * está en la tarjeta es gastar una petición para nada.
 *
 * **Ritmo.** Una petición por segundo es el límite de MusicBrainz, y el
 * interceptor de red ya lo impone. Aquí no se añade espera artificial:
 * el propio cliente HTTP marca el paso. Un recorrido de mil artistas
 * son unos veinte minutos, y se puede parar en cualquier momento --
 * lo hecho queda guardado.
 * ---
 * S025 -- builds the anchor dictionary in bulk instead of waiting for
 * Radio to stumble across artists three per round. Walks the pending
 * drawer, then the downloaded local library, then seed entries missing
 * a country. Skips anything already resolved, and can be stopped at any
 * point: whatever was resolved is already on the card.
 */
@Singleton
class AnchorDictionaryBuilder @Inject constructor(
    private val anchorDictionary: AnchorDictionary,
    private val radioRepository: RadioRepository,
    private val trackDao: SearchResultTrackDao,
) {

    /** Avance del recorrido, para pintarlo en Ajustes. */
    data class Progress(
        val done: Int,
        val total: Int,
        val resolved: Int,
        val notFound: Int,
        val currentArtist: String,
    )

    /** Cómo terminó el recorrido. */
    sealed interface Result {
        data class Finished(val resolved: Int, val notFound: Int, val skipped: Int) : Result
        data class Stopped(val resolved: Int, val notFound: Int) : Result
        data class NetworkDown(val resolved: Int, val notFound: Int) : Result
    }

    /**
     * Recorre y resuelve. Es `suspend` y cooperativa: cancelar la
     * corrutina la para en el siguiente artista, y lo ya resuelto está
     * escrito en la tarjeta desde el momento en que se resolvió.
     */
    suspend fun build(onProgress: (Progress) -> Unit): Result {
        val queue = buildQueue()
        var resolved = 0
        var notFound = 0
        var skipped = 0

        for ((index, name) in queue.withIndex()) {
            if (!currentCoroutineContext().isActive) {
                return Result.Stopped(resolved, notFound)
            }
            onProgress(Progress(index, queue.size, resolved, notFound, name))

            if (anchorDictionary.artist(name) != null) {
                skipped++
                continue
            }
            val outcome = try {
                radioRepository.resolveArtistFactsForDictionary(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return Result.NetworkDown(resolved, notFound)
            }
            when (outcome) {
                RadioRepository.DictionaryOutcome.RESOLVED -> resolved++
                RadioRepository.DictionaryOutcome.NOT_FOUND -> notFound++
                RadioRepository.DictionaryOutcome.NETWORK_DOWN ->
                    return Result.NetworkDown(resolved, notFound)
            }
        }
        onProgress(Progress(queue.size, queue.size, resolved, notFound, ""))
        return Result.Finished(resolved, notFound, skipped)
    }

    /**
     * Cuántos artistas quedarían por recorrer ahora mismo. Se muestra
     * en Ajustes antes de arrancar, para que el botón no sea un salto
     * al vacío.
     */
    suspend fun pendingWork(): Int = buildQueue().size

    private suspend fun buildQueue(): List<String> {
        val seen = mutableSetOf<String>()
        val queue = mutableListOf<String>()

        fun add(name: String?) {
            val clean = name?.trim().orEmpty()
            if (clean.isBlank()) return
            val k = SearchNormalizer.tight(SearchNormalizer.normalizeArtistName(clean))
            if (k.isBlank() || !seen.add(k)) return
            if (anchorDictionary.artist(clean) != null) return
            queue += clean
        }

        // 1. El cajón de sin red, que es lo que ya se sabe que falta.
        anchorDictionary.takePendingArtists(Int.MAX_VALUE).forEach { add(it) }

        // 2. La biblioteca local. Son los artistas que de verdad se
        //    escuchan, así que son los que más veces van a anclar.
        trackDao.getAllOnce()
            .mapNotNull { it.artist }
            .forEach { add(it) }

        return queue
    }
}

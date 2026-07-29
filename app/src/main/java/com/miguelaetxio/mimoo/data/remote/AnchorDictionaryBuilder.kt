package com.miguelaetxio.mimoo.data.remote

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
    private val folderReconciler: LibraryFolderReconciler,
) {

    private companion object {
        /** Artistas por petición. MusicBrainz admite hasta 100. */
        const val PAGE_SIZE = 100

        /**
         * Páginas por género. Cinco son quinientos artistas por
         * género, de sobra para cubrir lo que la Radio va a pedir, y
         * mantiene el recorrido completo en torno a la media hora.
         */
        const val MAX_PAGES_PER_GENRE = 5
    }

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
        data class Finished(
            val resolved: Int,
            val notFound: Int,
            val skipped: Int,
            val renamedFolders: Int = 0,
        ) : Result
        data class Stopped(val resolved: Int, val notFound: Int) : Result
        data class NetworkDown(val resolved: Int, val notFound: Int) : Result
    }

    /**
     * Recorre y resuelve. Es `suspend` y cooperativa: cancelar la
     * corrutina la para en el siguiente artista, y lo ya resuelto está
     * escrito en la tarjeta desde el momento en que se resolvió.
     */
    /**
     * S025 -- RECORRIDO POR GÉNEROS CONTRA MUSICBRAINZ.
     *
     * Miguel Ángel, tras ver que la primera versión solo sacaba 280
     * artistas: *"eso sale de mi disco"*. Tenía razón. Un botón que
     * completa lo que ya tienes bajado no construye una base de datos;
     * si en disco hay un disco de Joselito, esa es la base de datos.
     *
     * Ahora la fuente no es la tarjeta: es MusicBrainz. Se recorren los
     * géneros que de verdad anclan sesiones -- los que aparecen en la
     * semilla -- y de cada uno se piden artistas de cien en cien. Cada
     * resultado trae ya nombre y país, y el género lo da la propia
     * consulta, así que una sola petición deja hasta cien artistas
     * completos para el ancla.
     *
     * Con unos trescientos géneros y cinco páginas por género salen
     * unas mil quinientas peticiones: media hora larga a una por
     * segundo, y decenas de miles de artistas. Eso ya es una base de
     * datos.
     *
     * **Reanudable.** Cada género terminado se anota en la tarjeta, así
     * que parar y volver a pulsar sigue donde se dejó en vez de empezar
     * de cero.
     */
    suspend fun build(onProgress: (Progress) -> Unit): Result {
        val done = anchorDictionary.doneGenres()
        val genres = anchorDictionary.seedGenres().filterNot { it in done }
        var resolved = 0
        var notFound = 0

        for ((index, genre) in genres.withIndex()) {
            if (!currentCoroutineContext().isActive) {
                anchorDictionary.flush()
                return Result.Stopped(resolved, notFound)
            }
            onProgress(Progress(index, genres.size, resolved, notFound, genre))

            var pagesWithResults = 0
            for (page in 0 until MAX_PAGES_PER_GENRE) {
                if (!currentCoroutineContext().isActive) {
                    anchorDictionary.flush()
                    return Result.Stopped(resolved, notFound)
                }
                val artists = try {
                    radioRepository.browseArtistsByGenre(genre, offset = page * PAGE_SIZE)
                } catch (e: CancellationException) {
                    anchorDictionary.flush()
                    throw e
                } catch (e: Exception) {
                    anchorDictionary.flush()
                    return Result.NetworkDown(resolved, notFound)
                }
                if (artists.isEmpty()) break
                pagesWithResults++
                for (a in artists) {
                    if (anchorDictionary.looksLikeChannelName(a.name)) {
                        notFound++
                        continue
                    }
                    anchorDictionary.learnArtistFromCrawl(a.name, a.country, genre)
                    resolved++
                }
                onProgress(Progress(index, genres.size, resolved, notFound, genre))
            }
            if (pagesWithResults > 0) anchorDictionary.flush()
            anchorDictionary.markGenreDone(genre)
        }

        anchorDictionary.flush()

        // Segunda fase: arreglar el directorio en disco. Orden de
        // Miguel Ángel: *"en el botón de generar la base de datos debes
        // incluir reconciliar los nombres de las carpetas y poner los
        // nombres de los artistas y no los nombres de canales."*
        onProgress(Progress(genres.size, genres.size, resolved, notFound, "Ordenando carpetas..."))
        val renamed = folderReconciler.reconcile()
        onProgress(Progress(genres.size, genres.size, resolved, notFound, ""))
        return Result.Finished(resolved, notFound, 0, renamed)
    }

    suspend fun pendingWork(): Int {
        val done = anchorDictionary.doneGenres()
        return anchorDictionary.seedGenres().count { it !in done }
    }

}

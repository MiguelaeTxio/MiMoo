package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.util.SearchNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S025 -- DICCIONARIO DEL ANCLA, PERSISTIDO EN LA TARJETA.
 *
 * Orden de Miguel Ángel, literal: *"si vamos a tener de forma dinámica
 * en el teléfono toda la información, esa información debe quedar
 * guardada en la carpeta donde se guarda todo. Normalmente se va a
 * elegir la tarjeta SD externa para grabar las descargas. Ahí mismo es
 * donde tenemos que grabar el diccionario, y todo lo referente al
 * ancla."*
 *
 * No en Room, no en `SharedPreferences`, no en el almacenamiento
 * interno de la app: bajo la raíz SAF que el usuario eligió, la misma
 * donde van las descargas. `RadioDebugLogger` ya escribe ahí desde
 * S010, así que el mecanismo está probado.
 *
 * Tres consecuencias buscadas, y son las tres razones de la orden:
 *   - Sobrevive a la reinstalación: borrar datos de la app no borra lo
 *     aprendido.
 *   - Viaja con la tarjeta: si la tarjeta cambia de teléfono, el
 *     diccionario va con ella.
 *   - Entra solo en la sincronización de H07, que ya sincroniza esa
 *     carpeta contra Drive: lo que aprende un teléfono lo hereda el
 *     otro sin trabajo extra.
 *
 * ESTRUCTURA, bajo la raíz elegida:
 *
 *     MiMoo/diccionario/artistas.json    artista -> país, géneros, actividad
 *     MiMoo/diccionario/temas.json       artista|tema -> año original
 *     MiMoo/diccionario/pendientes.json  lo que no se pudo resolver sin red
 *
 * JSON y no base de datos, a propósito: legible, editable a mano por
 * Miguel Ángel si ve un dato mal, y sincronizable como un fichero
 * cualquiera.
 *
 * SEMILLA. El índice `anchor_artists.json` de assets (1.161 artistas
 * con país y géneros, construido en S025 a partir del diccionario de
 * éxitos, del repertorio clásico y de una tanda de grandes que
 * faltaban) NO es un techo: es el punto de partida. Todo lo que se
 * resuelva por red se escribe en la tarjeta, así que el diccionario
 * crece solo con el uso y cada vez hace falta menos red.
 *
 * PENDIENTES. Orden de Miguel Ángel: *"cuando un título nos dé que no
 * tenemos red, se guarda el artista y se guarda el título para que
 * cuando haya red se busque, aunque no sea para ponerlo, pero para
 * tenerlo guardado en el diccionario."* Eso es `pendientes.json`, y
 * `takePending()` es lo que lo vacía cuando la red vuelve.
 * ---
 * S025 -- anchor dictionary persisted on the storage card the user
 * picked for downloads, not in Room or app-internal storage. It
 * survives reinstalls, travels with the card, and is already covered
 * by H07's folder sync. The bundled index is a seed, not a ceiling:
 * everything resolved over the network is written back, so the
 * dictionary grows by itself and needs the network less over time.
 * Lookups that fail for lack of network are queued in `pendientes.json`
 * and resolved later, purely to enrich the dictionary.
 */
@Singleton
class AnchorDictionary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: StorageManager,
    // H15 (miMooutCast), S032 -- señal compartida para enrutar el log
    // de depuración entre Radio y miMooutCast. Ver el kdoc de
    // MiMooutcastSessionFlag.
    private val mimooutcastSessionFlag: com.miguelaetxio.mimoo.data.playback.MiMooutcastSessionFlag,
) {

    /** Lo que se sabe de un ARTISTA: país, géneros y años de actividad. */
    data class ArtistFacts(
        val artist: String = "",
        val country: String? = null,
        val genres: List<String> = emptyList(),
        /** De dónde salió: `semilla`, `musicbrainz`, `discogs`... */
        val source: String = "semilla",
    )

    /** Lo que se sabe de un TEMA: el año de su edición original. */
    data class TrackFacts(
        val key: String = "",
        val artist: String = "",
        val title: String = "",
        val year: Int = 0,
        val source: String = "",
    )

    /** Un tema que no se pudo resolver por falta de red. */
    data class Pending(
        val artist: String = "",
        val title: String = "",
    )

    /**
     * S025 -- un ARTISTA que no está en el diccionario y que no se pudo
     * preguntar por falta de red.
     *
     * Orden de Miguel Ángel: *"cuando no tengamos en nuestra base de
     * datos a un artista y no tengamos red, guardarlo para cuando
     * tengamos red y estemos realizando otra búsqueda, reconciliar ese
     * artista, ese y todos los que haya en el cajón de sin red."*
     *
     * Hasta aquí solo se apuntaban TEMAS sin año. Faltaba la mitad
     * importante: el artista desconocido, que es el que deja la Radio
     * sin arrancar.
     */
    data class PendingArtist(
        val artist: String = "",
    )

    private val gson = Gson()

    // ---------------------------------------------------------------
    // Semilla empaquetada
    // ---------------------------------------------------------------

    private val seed: Map<String, ArtistFacts> by lazy {
        try {
            val json = context.assets.open(SEED_ASSET)
                .bufferedReader()
                .use { it.readText() }
            val type = object : TypeToken<List<ArtistFacts>>() {}.type
            val list: List<ArtistFacts> = gson.fromJson(json, type)
            list.associateBy { key(it.artist) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ---------------------------------------------------------------
    // Aprendido, en tarjeta
    // ---------------------------------------------------------------

    private val learnedArtists = mutableMapOf<String, ArtistFacts>()
    private val learnedTracks = mutableMapOf<String, TrackFacts>()
    private val pending = linkedSetOf<String>()
    private val pendingItems = mutableMapOf<String, Pending>()
    private val pendingArtists = linkedSetOf<String>()
    private val pendingArtistItems = mutableMapOf<String, PendingArtist>()

    @Volatile
    private var loaded = false

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        // S025 -- antes de leer nada, barrer los duplicados que dejó el
        // fallo del MIME. Si no, `findDoc()` seguiría eligiendo entre
        // cientos de ficheros en cada lectura.
        runCatching { dictionaryDir()?.let { cleanupStrayFiles(it) } }
        readArtists()?.let { list -> list.forEach { learnedArtists[key(it.artist)] = it } }
        readTracks()?.let { list -> list.forEach { learnedTracks[it.key] = it } }
        readPending()?.let { list ->
            list.forEach {
                val k = trackKey(it.artist, it.title)
                pending += k
                pendingItems[k] = it
            }
        }
        readPendingArtists()?.let { list ->
            list.forEach {
                val k = key(it.artist)
                if (k.isNotBlank()) {
                    pendingArtists += k
                    pendingArtistItems[k] = it
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Consulta
    // ---------------------------------------------------------------

    /**
     * Lo que se sabe del artista, mirando primero lo aprendido y luego
     * la semilla. Devuelve `null` solo si no hay nada en ninguno de los
     * dos, que es la señal de que toca preguntar a la red.
     */
    fun artist(name: String?): ArtistFacts? {
        if (name.isNullOrBlank()) return null
        ensureLoaded()
        val k = key(name)
        if (k.isBlank()) return null
        learnedArtists[k]?.let { return it }
        seed[k]?.let { return it }
        // S027 -- fallback por palabras. Caso real reportado por
        // Miguel Ángel: el título de un vídeo trae "Beethoven" a
        // secas, pero la semilla guarda "Ludwig van Beethoven" -- la
        // clave exacta normalizada nunca coincide, así que un artista
        // que SÍ está en el diccionario (con géneros reales:
        // classical, symphony...) se daba por no encontrado y la
        // resolución caía a la red, donde "Beethoven" suelto
        // coincidía por nombre exacto con una entidad de MusicBrainz
        // totalmente distinta, sin géneros -- y sin ancla.
        //
        // Solo se acepta si el candidato es ÚNICO en todo el
        // diccionario (semilla + aprendido): mismo principio que el
        // resto del sistema -- mejor sin ancla que con una mala.
        return artistByWordSubset(name)
    }

    /**
     * S027 -- busca por conjunto de palabras contenido (en cualquier
     * dirección) entre el nombre buscado y el nombre de cada entrada
     * del diccionario. "beethoven" está contenido en
     * "ludwig van beethoven"; "bach" en "johann sebastian bach".
     * Comparación sobre `SearchNormalizer.normalizeArtistName()`
     * (conserva espacios) -- NO sobre `key()`, que los quita y
     * convertiría "ludwig van beethoven" en un único token pegado
     * imposible de partir en palabras. Solo se devuelve si hay
     * EXACTAMENTE UN candidato -- con más de uno es ambiguo y no se
     * adivina.
     */
    private fun artistByWordSubset(name: String): ArtistFacts? {
        val wantedWords = wordsOf(SearchNormalizer.normalizeArtistName(name))
        if (wantedWords.isEmpty()) return null
        val allFacts = seed.values.asSequence() + learnedArtists.values.asSequence()
        val matches = allFacts
            .filter { facts ->
                val candidateWords = wordsOf(SearchNormalizer.normalizeArtistName(facts.artist))
                candidateWords.isNotEmpty() &&
                    (wantedWords.all { it in candidateWords } || candidateWords.all { it in wantedWords })
            }
            .distinct()
            .toList()
        return matches.singleOrNull()
    }

    private fun wordsOf(normalizedName: String): Set<String> =
        normalizedName.split(" ").filter { it.isNotBlank() }.toSet()

    /**
     * Año de edición original del tema, si ya se sabe.
     *
     * S027 -- pregunta de seguimiento de Miguel Ángel tras construir
     * la caché de discografía completa (`ensureDiscographyCached()`):
     * de poco sirve tener la discografía entera guardada si la
     * búsqueda LOCAL sigue exigiendo coincidencia EXACTA de título. Un
     * vídeo de YouTube con ruido ("PIGNOISE 'Te entiendo' Videoclip
     * oficial") no coincide carácter a carácter con el título limpio
     * que guarda MusicBrainz ("Te Entiendo"), y el acierto local se
     * perdía -- se seguía cayendo a preguntar por red para un tema que
     * ya estaba guardado. Mismo mecanismo ya usado para nombres de
     * artista cortos (Beethoven) y títulos de canción con ruido
     * (`firstReleaseYearFromMusicBrainz` en RadioRepository): si la
     * clave exacta falla, se compara por conjunto de palabras contra
     * los temas YA guardados de ESE MISMO artista -- solo se acepta si
     * es el ÚNICO que coincide, para no adivinar con ambigüedad.
     *
     * SEGUNDA VUELTA (2026-08-03), bug real: ese mecanismo seguía sin
     * bastar. "PIGNOISE 'Te entiendo' Videoclip oficial" (wantedWords
     * incluye pignoise/te/entiendo/videoclip/oficial) NO encontraba
     * "Te Entiendo" (gotWords = te/entiendo) porque el subconjunto
     * EXIGE que TODAS las palabras de uno estén en el otro -- y aquí
     * ninguno de los dos lo es del otro completo (sobran palabras en
     * ambos sentidos si el título guardado trae aunque sea una palabra
     * de más, o el del vídeo trae ruido de más). Con la discografía ya
     * descargada de golpe (barata, local, sin red), tiene sentido ser
     * más permisivo aquí -- probar de nuevo por SOLAPAMIENTO de
     * palabras significativas (4+ letras, para no dejar que "de", "el"
     * decidan un acierto) en vez de subconjunto completo, aceptando
     * solo si el resultado sigue siendo único entre los temas
     * guardados de ese artista -- misma norma de seguridad que antes,
     * solo el criterio de coincidencia es más laxo.
     * ---
     * Original release year of the track, if already known.
     *
     * S027 -- Miguel Ángel's follow-up question after building the
     * full discography cache (`ensureDiscographyCached()`): having the
     * whole discography saved does little good if the LOCAL lookup
     * still requires an EXACT title match. A noisy YouTube video title
     * ("PIGNOISE 'Te entiendo' Videoclip oficial") doesn't match
     * character-for-character against the clean title MusicBrainz
     * stores ("Te Entiendo"), and the local hit was lost -- it still
     * fell back to asking over the network for a track that was
     * already saved. Same mechanism already used for short artist
     * names (Beethoven) and noisy song titles
     * (`firstReleaseYearFromMusicBrainz` in RadioRepository): if the
     * exact key fails, compare by word set against the tracks ALREADY
     * saved for THAT SAME artist -- only accepted if it's the ONLY one
     * that matches, to avoid guessing with ambiguity.
     *
     * SECOND ROUND (2026-08-03), real bug: that mechanism still wasn't
     * enough. "PIGNOISE 'Te entiendo' Videoclip oficial" (wantedWords
     * includes pignoise/te/entiendo/videoclip/oficial) didn't find
     * "Te Entiendo" (gotWords = te/entiendo) because the subset check
     * REQUIRES ALL words of one to be in the other -- and neither is a
     * full subset of the other here (extra words on either side break
     * it if the saved title has even one extra word, or the video's
     * has extra noise). With the discography already bulk-downloaded
     * (cheap, local, no network), it makes sense to be more permissive
     * here -- try again by OVERLAP of significant words (4+ letters,
     * so "de"/"el" don't decide a match) instead of full subset,
     * accepting only if the result is still unique among that artist's
     * saved tracks -- same safety rule as before, just a looser match
     * criterion.
     */
    fun trackYear(artist: String?, title: String?): Int? {
        if (artist.isNullOrBlank() || title.isNullOrBlank()) return null
        ensureLoaded()
        learnedTracks[trackKey(artist, title)]?.year?.let { return it }

        val artistKey = key(artist)
        val wantedWords = wordsOf(SearchNormalizer.normalize(title))
        if (wantedWords.isEmpty()) return null
        val sameArtistFacts = learnedTracks.values
            .asSequence()
            .filter { key(it.artist) == artistKey }
            .toList()

        val strictMatches = sameArtistFacts
            .filter { fact ->
                val gotWords = wordsOf(SearchNormalizer.normalize(fact.title))
                gotWords.isNotEmpty() &&
                    (wantedWords.all { it in gotWords } || gotWords.all { it in wantedWords })
            }
            .map { it.year }
            .distinct()
        if (strictMatches.size == 1) return strictMatches.single()
        // Ambiguo en la comprobación estricta (más de un año distinto
        // encaja) -- no se cae a la comprobación laxa, sería adivinar
        // con menos información todavía.
        if (strictMatches.size > 1) return null

        val significantWanted = wantedWords.filter { it.length >= 4 }.toSet()
        if (significantWanted.isEmpty()) return null
        val looseMatches = sameArtistFacts
            .mapNotNull { fact ->
                val gotWords = wordsOf(SearchNormalizer.normalize(fact.title))
                    .filter { it.length >= 4 }
                    .toSet()
                if (gotWords.isEmpty()) return@mapNotNull null
                val overlap = significantWanted.intersect(gotWords).size
                if (overlap == 0) return@mapNotNull null
                val ratio = overlap.toDouble() / minOf(significantWanted.size, gotWords.size)
                if (ratio >= 0.6) fact.year else null
            }
            .distinct()
        return looseMatches.singleOrNull()
    }

    // ---------------------------------------------------------------
    // Aprendizaje
    // ---------------------------------------------------------------

    /**
     * Guarda lo aprendido de un artista. No pisa un dato de la semilla
     * con uno peor: si lo nuevo no trae país ni géneros, se ignora.
     */
    fun learnArtist(facts: ArtistFacts) {
        if (facts.artist.isBlank()) return
        if (facts.country == null && facts.genres.isEmpty()) return
        ensureLoaded()
        val k = key(facts.artist)
        if (k.isBlank()) return
        val previous = learnedArtists[k]
        if (previous != null && previous == facts) return
        learnedArtists[k] = facts
        writeArtists()
        if (pendingArtists.remove(k)) {
            pendingArtistItems.remove(k)
            writePendingArtists()
        }
    }

    /**
     * Guarda el año original de un tema. Si el tema estaba en la cola
     * de pendientes, sale de ella: ya se sabe.
     */
    fun learnTrackYear(artist: String, title: String, year: Int, source: String) {
        if (artist.isBlank() || title.isBlank() || year <= 0) return
        ensureLoaded()
        val k = trackKey(artist, title)
        if (learnedTracks[k]?.year == year) return
        learnedTracks[k] = TrackFacts(k, artist, title, year, source)
        writeTracks()
        if (pending.remove(k)) {
            pendingItems.remove(k)
            writePending()
        }
    }

    /**
     * S027 -- variante en bloque de [learnTrackYear] para cuando se
     * aprende la discografía ENTERA de un artista de golpe (ver
     * `RadioRepository.ensureDiscographyCached()`). `learnTrackYear()`
     * reescribe el fichero completo en CADA llamada -- para una
     * discografía de 60-100 temas eso son 60-100 reescrituras del
     * mismo fichero seguidas. Aquí se añaden todas las entradas al
     * mapa primero y se escribe UNA sola vez al final.
     */
    fun learnTrackYearsBulk(artist: String, entries: List<Pair<String, Int>>, source: String) {
        if (artist.isBlank() || entries.isEmpty()) return
        ensureLoaded()
        var changed = false
        for ((title, year) in entries) {
            if (title.isBlank() || year <= 0) continue
            val k = trackKey(artist, title)
            if (learnedTracks[k]?.year == year) continue
            learnedTracks[k] = TrackFacts(k, artist, title, year, source)
            changed = true
            if (pending.remove(k)) pendingItems.remove(k)
        }
        if (changed) {
            writeTracks()
            writePending()
        }
    }

    // ---------------------------------------------------------------
    // Cola de pendientes por falta de red
    // ---------------------------------------------------------------

    /** Apunta un artista+tema que no se pudo resolver por falta de red. */
    fun rememberPending(artist: String?, title: String?) {
        if (artist.isNullOrBlank() || title.isNullOrBlank()) return
        ensureLoaded()
        val k = trackKey(artist, title)
        if (k in pending || k in learnedTracks) return
        if (pending.size >= MAX_PENDING) {
            val oldest = pending.firstOrNull() ?: return
            pending.remove(oldest)
            pendingItems.remove(oldest)
        }
        pending += k
        pendingItems[k] = Pending(artist, title)
        writePending()
    }

    /**
     * Saca hasta [limit] pendientes para intentar resolverlos ahora que
     * hay red. No los borra: los borra `learnTrackYear()` cuando de
     * verdad se resuelven, o `dropPending()` si resulta que no existen.
     */
    fun takePending(limit: Int): List<Pending> {
        ensureLoaded()
        return pending.take(limit).mapNotNull { pendingItems[it] }
    }

    /** Descarta un pendiente que no se ha podido resolver ni con red. */
    fun dropPending(artist: String, title: String) {
        ensureLoaded()
        val k = trackKey(artist, title)
        if (pending.remove(k)) {
            pendingItems.remove(k)
            writePending()
        }
    }

    /**
     * Apunta un artista que no está en el diccionario y que no se pudo
     * preguntar. Si ya se conoce no se apunta: `artist()` lo resolvería
     * sin red.
     */
    fun rememberPendingArtist(name: String?) {
        if (name.isNullOrBlank()) return
        ensureLoaded()
        val k = key(name)
        if (k.isBlank() || k in pendingArtists) return
        if (learnedArtists.containsKey(k) || seed.containsKey(k)) return
        if (pendingArtists.size >= MAX_PENDING) {
            val oldest = pendingArtists.firstOrNull() ?: return
            pendingArtists.remove(oldest)
            pendingArtistItems.remove(oldest)
        }
        pendingArtists += k
        pendingArtistItems[k] = PendingArtist(name)
        writePendingArtists()
    }

    fun takePendingArtists(limit: Int): List<String> {
        ensureLoaded()
        return pendingArtists.take(limit).mapNotNull { pendingArtistItems[it]?.artist }
    }

    /** Saca un artista del cajón: o se resolvió, o no existe. */
    fun dropPendingArtist(name: String) {
        ensureLoaded()
        val k = key(name)
        if (pendingArtists.remove(k)) {
            pendingArtistItems.remove(k)
            writePendingArtists()
        }
    }

    fun pendingArtistCount(): Int {
        ensureLoaded()
        return pendingArtists.size
    }

    fun pendingCount(): Int {
        ensureLoaded()
        return pending.size
    }

    /**
     * S025 -- todos los géneros que aparecen en la semilla, ordenados.
     *
     * Son el universo que recorre el constructor: los géneros que de
     * verdad anclan sesiones. Recorrer las 2.176 etiquetas del árbol
     * completo traería ópera china y gamelán balinés, que no le sirven
     * de nada a esta biblioteca.
     */
    fun seedGenres(): List<String> {
        ensureLoaded()
        return seed.values
            .flatMap { it.genres }
            .map { it.lowercase().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    /**
     * S025 -- guarda un artista del recorrido masivo SUMANDO géneros.
     *
     * El recorrido pregunta género a género, así que el mismo artista
     * aparece en varios. `learnArtist()` reemplazaría y cada pasada
     * borraría la anterior; aquí se acumulan, que es lo que hace útil
     * al ancla: cuantos más géneros tenga un artista, mejor cruza.
     */
    fun learnArtistFromCrawl(name: String, country: String?, genre: String) {
        if (name.isBlank()) return
        ensureLoaded()
        val k = key(name)
        if (k.isBlank()) return
        val previous = learnedArtists[k]
        val genres = (previous?.genres.orEmpty() + genre)
            .map { it.lowercase().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val merged = ArtistFacts(
            artist = previous?.artist ?: name,
            country = country ?: previous?.country,
            genres = genres,
            source = "musicbrainz",
        )
        if (merged == previous) return
        learnedArtists[k] = merged
        dirtyArtists = true
    }

    /**
     * S025 -- el recorrido masivo escribe miles de entradas; guardar en
     * cada una castigaría la tarjeta sin motivo. Se acumula en memoria
     * y se vuelca por lotes con `flush()`.
     */
    @Volatile
    private var dirtyArtists = false

    fun flush() {
        if (!dirtyArtists) return
        ensureLoaded()
        // S025 -- solo se da por guardado si la escritura fue bien. Si
        // la tarjeta falla, `dirtyArtists` sigue en pie y el siguiente
        // `flush()` lo reintenta, en vez de perder el lote en silencio.
        if (writeArtists()) dirtyArtists = false
    }

    /** Géneros ya recorridos, para poder reanudar donde se dejó. */
    fun doneGenres(): Set<String> {
        ensureLoaded()
        return readText(FILE_DONE_GENRES)
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
    }

    fun markGenreDone(genre: String) {
        ensureLoaded()
        val all = doneGenres() + genre
        writeText(FILE_DONE_GENRES, all.joinToString("\n"))
    }

    /**
     * S025 -- BORRA TODO LO APRENDIDO.
     *
     * La primera versión del constructor sembró el diccionario con los
     * artistas de la biblioteca local, y ese campo puede contener el
     * nombre del canal de YouTube. Resultado: entradas como "Deep Purple
     * Official" con país y géneros propios, exactamente lo que llevaba
     * toda la sesión ordenándose sacar del sistema.
     *
     * Esa base de datos no se puede corregir entrada por entrada porque
     * no hay forma de distinguir lo bueno de lo malo: se tira entera y
     * se vuelve a construir con una fuente limpia. Se ejecuta una sola
     * vez al actualizar, marcada con `FILE_WIPED`.
     *
     * La semilla del APK no se toca: no está contaminada.
     */
    fun wipeLearnedOnce() {
        ensureLoaded()
        if (!readText(FILE_WIPED).isNullOrBlank()) return
        // S025 -- LA MARCA SE ESCRIBE ANTES DE BORRAR NADA.
        //
        // Al revés era una mina, y estalló: se borraba primero y se
        // marcaba al final, con un `writeText` que se tragaba los
        // errores. Si esa escritura fallaba -- o la app moría por el
        // ANR de Ajustes antes de llegar --, el borrado se repetía en
        // CADA entrada a Ajustes y pulverizaba el trabajo una y otra
        // vez. Reportado por Miguel Ángel: *"el trabajo anterior en la
        // base de datos ha quedado pulverizado y no ha persistido en
        // absoluto."*
        //
        // Si no se puede dejar constancia de que se ha borrado, no se
        // borra. Es preferible arrastrar entradas sucias a perderlo
        // todo en bucle.
        if (!writeText(FILE_WIPED, "S025")) return
        learnedArtists.clear()
        learnedTracks.clear()
        pending.clear()
        pendingItems.clear()
        pendingArtists.clear()
        pendingArtistItems.clear()
        writeArtists()
        writeTracks()
        writePending()
        writePendingArtists()
        writeText(FILE_DONE_GENRES, "")
    }

    /**
     * S025 -- ¿este nombre huele a canal de YouTube y no a artista?
     *
     * No hay forma infalible de saberlo, pero sí hay marcas que un
     * artista real prácticamente nunca lleva y un canal lleva casi
     * siempre. Ante la duda se descarta: dejar fuera a un artista
     * legítimo cuesta una entrada; meter un canal ensucia el
     * diccionario y la Radio.
     */
    fun looksLikeChannelName(name: String): Boolean {
        val n = " ${SearchNormalizer.normalizeArtistName(name)} "
        if (n.isBlank()) return true
        return CHANNEL_MARKERS.any { n.contains(" $it ") } ||
            n.trim().split(" ").size > 6 ||
            n.trim().length > 45
    }

    // ---------------------------------------------------------------
    // Copia entre dispositivos de la MISMA cuenta (H07)
    // ---------------------------------------------------------------

    /**
     * S025 -- lo APRENDIDO, para que viaje en la copia de Drive.
     *
     * Orden de Miguel Ángel: *"debe guardarse en Drive cuando se haga
     * la copia para persistir esa base de datos entre dispositivos. Lo
     * mismo que persistimos las grabaciones, los links, los favoritos."*
     *
     * Solo lo aprendido, nunca la semilla: la semilla va dentro del APK
     * y es idéntica en los dos teléfonos, así que meterla en la copia
     * sería duplicar por nada varios cientos de kB en cada respaldo.
     */
    /**
     * S025 -- CONSULTA DIRECTA DE LA BASE DE DATOS CONSTRUIDA.
     *
     * Orden de Miguel Ángel, y con toda la razón: *"quiero que la base
     * de datos que he construido se use de una vez."* Llevaba razón
     * también en el fondo: se construyó una base de miles de artistas
     * y la búsqueda de relacionados seguía yendo en vivo a MusicBrainz
     * como si no existiera. `RadioRepository.findCandidates()` no la
     * consultaba en ningún punto.
     *
     * Devuelve los artistas de la semilla y de lo aprendido cuyo país
     * coincide y que comparten AL MENOS UN género con lo pedido. Es
     * instantáneo -- sin red, sin límite de una petición por segundo --
     * y es justo lo que media hora de recorrido debería servir para
     * evitar.
     *
     * S026 -- filtra por GRUPO de origen (Hispanoamérica/Anglosajona/
     * Europea/Mundial, ver `OriginGroup`) en vez de país exacto,
     * sustituyendo el filtro de S025. Decisión explícita de Miguel
     * Ángel: *"prefiero que Led Zeppelin me traiga a Van Halen o AC/DC
     * antes que rebuscar en GB"* -- dentro del grupo, el país exacto
     * ya no manda nada, se abre a todo el grupo desde el principio.
     * `originGroup == null` (dato desconocido) no filtra por origen en
     * absoluto -- mismo principio de degradación elegante que el resto
     * de la Radio.
     *
     * Devuelve también el país exacto de cada candidato (antes solo
     * nombre+géneros), para que `RadioRepository.suggestRelatedArtist()`
     * pueda aplicar la salvaguarda "conocido en España" a los
     * candidatos hispanoamericanos que no son de España -- ver ese
     * método.
     */
    data class MatchedArtist(val name: String, val genres: Set<String>, val country: String?)

    fun artistsMatching(genres: Set<String>, originGroup: OriginGroup?): List<MatchedArtist> {
        ensureLoaded()
        val wanted = genres.map { it.lowercase().trim() }.filter { it.isNotBlank() }.toSet()
        if (wanted.isEmpty()) return emptyList()
        val pool = seed.values.asSequence() + learnedArtists.values.asSequence()
        return pool
            .filter { originGroup == null || OriginGroup.of(it.country) == originGroup }
            .filter { it.genres.any { g -> g in wanted } }
            .distinctBy { it.artist.lowercase().trim() }
            .map { MatchedArtist(it.artist, it.genres.toSet(), it.country) }
            .toList()
    }

    fun learnedArtistsSnapshot(): List<ArtistFacts> {
        ensureLoaded()
        return learnedArtists.values.toList()
    }

    fun learnedTracksSnapshot(): List<TrackFacts> {
        ensureLoaded()
        return learnedTracks.values.toList()
    }

    /**
     * S025 -- FUSIÓN, no reemplazo.
     *
     * La copia entra sumando: si este dispositivo ya sabía algo que la
     * copia no trae, se queda. Lo contrario sería perder lo que el otro
     * teléfono no llegó a aprender, y el diccionario existe justamente
     * para acumular.
     *
     * Ante el mismo artista o el mismo tema en ambos lados manda lo que
     * ya hay en este dispositivo: es al menos tan reciente como la
     * copia y puede venir de una fuente mejor.
     *
     * Ámbito, decidido por Miguel Ángel: esto solo ocurre entre
     * dispositivos de la MISMA cuenta -- *"ya cuando es la cuenta del
     * móvil de mi mujer, ya ella tiene que hacer la búsqueda"*. No hace
     * falta código para eso: la copia vive en la carpeta de Drive de
     * cada cuenta, así que el ámbito lo da el propio mecanismo de H07.
     */
    fun mergeFromBackup(artists: List<ArtistFacts>, tracks: List<TrackFacts>) {
        ensureLoaded()
        var changedArtists = false
        for (facts in artists) {
            if (facts.artist.isBlank()) continue
            if (facts.country == null && facts.genres.isEmpty()) continue
            val k = key(facts.artist)
            if (k.isBlank() || learnedArtists.containsKey(k)) continue
            learnedArtists[k] = facts
            changedArtists = true
            if (pendingArtists.remove(k)) pendingArtistItems.remove(k)
        }
        var changedTracks = false
        for (t in tracks) {
            if (t.artist.isBlank() || t.title.isBlank() || t.year <= 0) continue
            val k = trackKey(t.artist, t.title)
            if (learnedTracks.containsKey(k)) continue
            learnedTracks[k] = t.copy(key = k)
            changedTracks = true
            if (pending.remove(k)) pendingItems.remove(k)
        }
        if (changedArtists) {
            writeArtists()
            writePendingArtists()
        }
        if (changedTracks) {
            writeTracks()
            writePending()
        }
    }

    fun learnedArtistCount(): Int {
        ensureLoaded()
        return learnedArtists.size
    }

    // ---------------------------------------------------------------
    // Entrada/salida sobre la tarjeta
    // ---------------------------------------------------------------

    private fun readArtists(): List<ArtistFacts>? = try {
        val text = readText(FILE_ARTISTS)
        if (text.isNullOrBlank()) {
            null
        } else {
            val type = object : TypeToken<List<ArtistFacts>>() {}.type
            gson.fromJson<List<ArtistFacts>>(text, type)
        }
    } catch (e: Exception) {
        null
    }

    private fun readTracks(): List<TrackFacts>? = try {
        val text = readText(FILE_TRACKS)
        if (text.isNullOrBlank()) {
            null
        } else {
            val type = object : TypeToken<List<TrackFacts>>() {}.type
            gson.fromJson<List<TrackFacts>>(text, type)
        }
    } catch (e: Exception) {
        null
    }

    private fun readPending(): List<Pending>? = try {
        val text = readText(FILE_PENDING)
        if (text.isNullOrBlank()) {
            null
        } else {
            val type = object : TypeToken<List<Pending>>() {}.type
            gson.fromJson<List<Pending>>(text, type)
        }
    } catch (e: Exception) {
        null
    }

    private fun readPendingArtists(): List<PendingArtist>? = try {
        val text = readText(FILE_PENDING_ARTISTS)
        if (text.isNullOrBlank()) {
            null
        } else {
            val type = object : TypeToken<List<PendingArtist>>() {}.type
            gson.fromJson<List<PendingArtist>>(text, type)
        }
    } catch (e: Exception) {
        null
    }

    private fun writePendingArtists() =
        writeText(FILE_PENDING_ARTISTS, gson.toJson(pendingArtistItems.values.toList()))

    private fun writeArtists(): Boolean =
        writeText(FILE_ARTISTS, gson.toJson(learnedArtists.values.toList()))
    private fun writeTracks() = writeText(FILE_TRACKS, gson.toJson(learnedTracks.values.toList()))
    private fun writePending() = writeText(FILE_PENDING, gson.toJson(pendingItems.values.toList()))

    /**
     * Carpeta `MiMoo/diccionario/` bajo la raíz elegida, creándola si
     * hace falta. Si el usuario no ha elegido raíz todavía se cae al
     * almacenamiento interno, que es lo único disponible: mejor eso que
     * perder lo aprendido, y en cuanto elija tarjeta se empieza a
     * escribir ahí.
     */
    /**
     * S025 -- la carpeta y los ficheros se resuelven UNA VEZ.
     *
     * Fallo reportado por Miguel Ángel: *"de nuevo se pega dos horas
     * para entrar en la sidebar"*. La causa era esto: `dictionaryDir()`
     * se llamaba en cada lectura, en cada escritura y otra vez en la
     * línea de log, y CADA llamada hace `root.findFile("MiMoo")`, que
     * lista la raíz entera de la tarjeta -- todas las carpetas de
     * artista, una por una. Con una biblioteca grande eso son segundos
     * por operación, y el diccionario hace decenas.
     *
     * Ahora la carpeta se resuelve una vez por sesión y cada fichero
     * también, así que a partir de la primera vez todo va directo al
     * `Uri` que ya se conoce.
     */
    @Volatile
    private var cachedDir: DocumentFile? = null

    @Volatile
    private var dirResolved = false

    private val docCache = mutableMapOf<String, DocumentFile>()

    private fun dictionaryDir(): DocumentFile? {
        if (dirResolved) return cachedDir
        val resolved = resolveDictionaryDir()
        cachedDir = resolved
        dirResolved = true
        return resolved
    }

    private fun resolveDictionaryDir(): DocumentFile? {
        val rootUri = storageManager.getRootUri() ?: return null
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        val base = root.findFile(DIR_BASE)?.takeIf { it.isDirectory }
            ?: root.createDirectory(DIR_BASE)
            ?: return null
        return base.findFile(DIR_DICT)?.takeIf { it.isDirectory }
            ?: base.createDirectory(DIR_DICT)
    }

    /**
     * S025 -- busca el fichero tolerando que el proveedor SAF le haya
     * cambiado el nombre.
     *
     * Este es el fallo que hacía que NO PERSISTIERA NADA. Los ficheros
     * se creaban con MIME `application/json`, y los proveedores SAF de
     * tarjeta externa añaden extensión según el MIME: `artistas.json`
     * acababa en disco como `artistas.json.json`. Al volver,
     * `findFile("artistas.json")` no encontraba nada, así que cada
     * escritura creaba un fichero nuevo y cada lectura devolvía vacío.
     *
     * Miguel Ángel lo vio en pantalla: guardaba 1.534 artistas y al
     * volver a entrar en Ajustes ponía "Ya guardados: 0", y el
     * recorrido empezaba otra vez por el género 0.
     *
     * Se crean ahora con `WRITE_MIME_TYPE` (S026:
     * `application/octet-stream`, sin extensión canónica -- ver esa
     * constante), que no toca el nombre, y esta búsqueda acepta además
     * cualquier variante que empiece por el nombre pedido, para
     * recuperar lo que ya se escribió mal con MIME anteriores.
     */
    private fun findDoc(dir: DocumentFile, name: String): DocumentFile? =
        docCache[name]
            ?: dir.findFile(name)
            // De los renombrados, el MÁS GRANDE: cada escritura creaba
            // uno nuevo, así que hay varias generaciones y la buena es
            // la que más entradas tiene. Coger el primero que apareciera
            // podía resucitar un fichero viejo o vacío.
            ?: dir.listFiles()
                .filter { it.isFile && it.name?.startsWith(name) == true }
                .maxByOrNull { it.length() }
                    .also { found -> if (found != null) docCache[name] = found }

    /**
     * S025 -- LIMPIEZA DE LOS FICHEROS QUE DEJÓ EL FALLO DEL MIME.
     *
     * Mientras los ficheros se creaban con `application/json`, el
     * proveedor SAF les cambiaba el nombre y `findFile` no los volvía a
     * encontrar, así que CADA escritura creaba uno nuevo. Durante un
     * recorrido completo eso son cientos de duplicados
     * -- `artistas.json.json`, `artistas.json (1).json`... -- ocupando
     * sitio en la tarjeta y sin servir para nada.
     *
     * Pregunta de Miguel Ángel: *"¿y lo que se ha grabado con
     * .json.json? ¿lo dejamos para la posteridad?"*. No.
     *
     * De cada grupo se conserva el más grande, que es el que más
     * entradas tiene; su contenido se reescribe bajo el nombre
     * correcto y todos los demás se borran. Se ejecuta una sola vez,
     * al cargar.
     */
    private fun cleanupStrayFiles(dir: DocumentFile) {
        for (name in ALL_FILES) {
            val matches = dir.listFiles()
                .filter { it.isFile && it.name?.startsWith(name) == true }
            if (matches.size <= 1 && matches.firstOrNull()?.name == name) continue

            val best = matches.maxByOrNull { it.length() }
            val content = best?.let { doc ->
                runCatching {
                    context.contentResolver.openInputStream(doc.uri)
                        ?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }

            // S025 -- PRIMERO SE ASEGURA EL BUENO, DESPUÉS SE BORRA.
            //
            // Al revés era destructivo: si la lectura del mejor fallaba
            // o la reescritura no salía, se habían borrado ya todos y
            // el trabajo desaparecía. Miguel Ángel lo sufrió: *"marcaba
            // que había trabajo hecho y al darle mandó a tomar por culo
            // todo lo hecho"*. Si no se puede garantizar el fichero
            // bueno, no se borra nada.
            if (content.isNullOrBlank()) continue

            val canonical = dir.findFile(name)
                ?: dir.createFile(WRITE_MIME_TYPE, name)
                ?: continue
            val written = runCatching {
                context.contentResolver.openOutputStream(canonical.uri, "wt")?.use { out ->
                    out.write(content.toByteArray())
                } != null
            }.getOrDefault(false)
            if (!written) continue
            docCache[name] = canonical

            var deleted = 0
            for (doc in matches) {
                if (doc.uri == canonical.uri) continue
                if (runCatching { doc.delete() }.getOrDefault(false)) deleted++
            }
            if (deleted > 0) {
                log(
                    "AnchorDictionary.cleanupStrayFiles('$name') -- $deleted duplicado(s) " +
                        "del fallo del MIME borrados; conservado el mayor " +
                        "(${content?.length ?: 0} caracteres)",
                )
            }
        }
    }

    // H15 (miMooutCast), S032 -- enruta según `mimooutcastSessionFlag`
    // (única fuente de verdad, ver su kdoc). Orden explícita y
    // repetida de Miguel Ángel: el debug de Radio no debe tocarse en
    // absoluto mientras se usa miMooutCast.
    private fun log(line: String) {
        if (mimooutcastSessionFlag.active) {
            MimooutcastDebugLogger.log(context, storageManager, line)
        } else {
            RadioDebugLogger.log(context, storageManager, line)
        }
    }

    private fun readText(name: String): String? = try {
        val dir = dictionaryDir()
        if (dir != null) {
            findDoc(dir, name)?.let { doc ->
                context.contentResolver.openInputStream(doc.uri)
                    ?.bufferedReader()?.use { it.readText() }
            }
        } else {
            File(context.filesDir, name).takeIf { it.exists() }?.readText()
        }
    } catch (e: Exception) {
        null
    }

    /**
     * S026 -- aparte para que el reintento de `writeText()` pueda
     * atrapar el fallo sin que se escape como excepción -- si dejara
     * subir la excepción, el `try` de `writeText()` la atraparía
     * primero y nunca llegaría a intentar la resolución fresca.
     */
    private fun writeToDoc(doc: DocumentFile, content: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
            out.write(content.toByteArray())
        } != null
    }.getOrDefault(false)

    private fun writeText(name: String, content: String): Boolean {
        return try {
            val dir = dictionaryDir()
            if (dir != null) {
                // S026 -- SEGUNDA VUELTA DEL MISMO FALLO DE S025, esta vez
                // con `text/plain`. Captura real de Miguel Ángel: el
                // fichero en la tarjeta se llamaba `temas.json.txt`, no
                // `temas.json` -- el proveedor SAF de su tarjeta también
                // añade la extensión canónica del MIME (`.txt` para
                // `text/plain`) cuando el nombre pedido no termina ya en
                // ella, exactamente el mismo mecanismo que renombraba a
                // `artistas.json.json` con `application/json`.
                //
                // `application/octet-stream` no tiene extensión canónica
                // en el mapa de MIME de Android (es el tipo "binario
                // genérico"), así que ningún proveedor SAF conocido le
                // añade nada -- ver WRITE_MIME_TYPE.
                val doc = findDoc(dir, name)
                    ?: dir.createFile(WRITE_MIME_TYPE, name)?.also { docCache[name] = it }
                    ?: return false
                writeToDoc(doc, content) || run {
                    // S026 -- TERCERA VUELTA: `docCache` cacheado apuntando
                    // a un `DocumentFile` que ya no existe de verdad --
                    // visto en log real ("Failed to determine if ... is
                    // child of ...: Missing file"), horas después de que
                    // `cleanupStrayFiles()` hubiera consolidado y borrado
                    // el fichero renombrado de la vuelta anterior en OTRA
                    // instancia de esta clase (la app se reinició de por
                    // medio -- el `docCache` es solo de memoria, no
                    // sobrevive a un reinicio de proceso, y una escritura
                    // que llega antes de que `ensureLoaded()` vuelva a
                    // resolverlo puede quedarse con un handle obsoleto).
                    // Se invalida la caché para este nombre y se reintenta
                    // UNA vez con una resolución fresca -- si el fichero
                    // canónico tampoco existe ya, se crea de cero.
                    docCache.remove(name)
                    val freshDoc = dir.findFile(name)
                        ?: dir.createFile(WRITE_MIME_TYPE, name)
                        ?: return false
                    docCache[name] = freshDoc
                    writeToDoc(freshDoc, content)
                }
            } else {
                File(context.filesDir, name).writeText(content)
                true
            }.also { ok ->
                // S025 -- rastro en el log de la Radio. Cuando esto
                // fallaba en silencio, la unica pista era que el
                // contador volvia a cero, y hubo que adivinar. Ya no.
                log(
                    "AnchorDictionary.writeText('$name') -> ${if (ok) "OK" else "FALLO"}, " +
                        "${content.length} caracteres, " +
                        "destino=${if (cachedDir != null) "tarjeta" else "interno"}",
                )
            }
        } catch (e: Exception) {
            // Nunca romper la Radio por no poder escribir el
            // diccionario. Lo aprendido se queda en memoria para esta
            // sesión y se reintentará al siguiente aprendizaje. Pero se
            // informa del fallo: `flush()` necesita saberlo para no dar
            // por guardado lo que no lo está.
            log(
                "AnchorDictionary.writeText('$name') -> EXCEPCIÓN: " +
                    "${e::class.java.simpleName}: ${e.message}",
            )
            false
        }
    }

    // ---------------------------------------------------------------

    private fun key(artist: String): String =
        SearchNormalizer.tight(SearchNormalizer.normalizeArtistName(artist))

    private fun trackKey(artist: String, title: String): String =
        key(artist) + "|" + SearchNormalizer.songTitleKey(title, artist)

    private companion object {
        const val SEED_ASSET = "anchor_artists.json"
        const val DIR_BASE = "MiMoo"
        const val DIR_DICT = "diccionario"
        const val FILE_ARTISTS = "artistas.json"
        const val FILE_TRACKS = "temas.json"
        const val FILE_PENDING = "pendientes.json"
        const val FILE_PENDING_ARTISTS = "pendientes_artistas.json"
        const val FILE_WIPED = "borrado_s025.txt"
        const val FILE_DONE_GENRES = "generos_recorridos.txt"

        /**
         * S026 -- MIME para crear los ficheros del diccionario en SAF.
         * Ni `application/json` (S025: renombraba a `.json.json`) ni
         * `text/plain` (S026: renombraba a `.json.txt`, visto en captura
         * real de la tarjeta de Miguel Ángel) sirven -- ambos tienen
         * extensión canónica en el mapa de MIME de Android, y al menos
         * un proveedor SAF conocido la añade cuando el nombre pedido no
         * termina ya en ella. `application/octet-stream` es el tipo
         * "binario genérico", sin extensión canónica asociada, así que
         * no hay nada que un proveedor pueda añadir.
         */
        const val WRITE_MIME_TYPE = "application/octet-stream"

        /** Todos los ficheros del diccionario, para la limpieza. */
        val ALL_FILES = listOf(
            "artistas.json",
            "temas.json",
            "pendientes.json",
            "pendientes_artistas.json",
            "generos_recorridos.txt",
        )

        /**
         * S025 -- palabras que delatan un canal de YouTube y no un
         * artista. Salen de casos reales del log de Miguel Ángel:
         * "Deep Purple Official", "OlvidadasCanciones",
         * "Valentina Lisitsa QOR Records Official channel".
         */
        val CHANNEL_MARKERS = setOf(
            "official", "oficial", "channel", "canal", "vevo", "topic",
            "records", "music", "musica", "tv", "hd", "hq", "video",
            "videos", "oldies", "clasicos", "recopilacion", "recopilaciones",
            "mix", "playlist", "radio", "fm", "productions", "media",
            "entertainment", "studios", "label", "discos", "subs", "fan",
        )

        /**
         * Tope de la cola de pendientes. No es por espacio -- un JSON
         * de diez mil pares artista/tema son unos cientos de kB -- sino
         * para que una racha larga sin red no deje una cola que tarde
         * horas en drenarse a una petición por segundo, que es el
         * ritmo que impone MusicBrainz.
         */
        const val MAX_PENDING = 2000
    }
}

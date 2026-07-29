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
) {

    /** Lo que se sabe de un ARTISTA: país, géneros y años de actividad. */
    data class ArtistFacts(
        val artist: String = "",
        val country: String? = null,
        val genres: List<String> = emptyList(),
        /**
         * Años de actividad, para la regla de coherencia. Un tema no
         * puede ser anterior a `activeFrom` ni posterior a `activeTo`:
         * es lo que habría tumbado el "Black Dog -> 1983" del log de
         * Miguel Ángel, con Led Zeppelin disueltos en 1980.
         */
        val activeFrom: Int? = null,
        val activeTo: Int? = null,
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

    @Volatile
    private var loaded = false

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        readArtists()?.let { list -> list.forEach { learnedArtists[key(it.artist)] = it } }
        readTracks()?.let { list -> list.forEach { learnedTracks[it.key] = it } }
        readPending()?.let { list ->
            list.forEach {
                val k = trackKey(it.artist, it.title)
                pending += k
                pendingItems[k] = it
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
        return learnedArtists[k] ?: seed[k]
    }

    /** Año de edición original del tema, si ya se sabe. */
    fun trackYear(artist: String?, title: String?): Int? {
        if (artist.isNullOrBlank() || title.isNullOrBlank()) return null
        ensureLoaded()
        return learnedTracks[trackKey(artist, title)]?.year
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

    fun pendingCount(): Int {
        ensureLoaded()
        return pending.size
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

    private fun writeArtists() = writeText(FILE_ARTISTS, gson.toJson(learnedArtists.values.toList()))
    private fun writeTracks() = writeText(FILE_TRACKS, gson.toJson(learnedTracks.values.toList()))
    private fun writePending() = writeText(FILE_PENDING, gson.toJson(pendingItems.values.toList()))

    /**
     * Carpeta `MiMoo/diccionario/` bajo la raíz elegida, creándola si
     * hace falta. Si el usuario no ha elegido raíz todavía se cae al
     * almacenamiento interno, que es lo único disponible: mejor eso que
     * perder lo aprendido, y en cuanto elija tarjeta se empieza a
     * escribir ahí.
     */
    private fun dictionaryDir(): DocumentFile? {
        val rootUri = storageManager.getRootUri() ?: return null
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        val base = root.findFile(DIR_BASE)?.takeIf { it.isDirectory }
            ?: root.createDirectory(DIR_BASE)
            ?: return null
        return base.findFile(DIR_DICT)?.takeIf { it.isDirectory }
            ?: base.createDirectory(DIR_DICT)
    }

    private fun readText(name: String): String? = try {
        val dir = dictionaryDir()
        if (dir != null) {
            dir.findFile(name)?.let { doc ->
                context.contentResolver.openInputStream(doc.uri)
                    ?.bufferedReader()?.use { it.readText() }
            }
        } else {
            File(context.filesDir, name).takeIf { it.exists() }?.readText()
        }
    } catch (e: Exception) {
        null
    }

    private fun writeText(name: String, content: String) {
        try {
            val dir = dictionaryDir()
            if (dir != null) {
                val doc = dir.findFile(name) ?: dir.createFile("application/json", name)
                doc?.let {
                    context.contentResolver.openOutputStream(it.uri, "wt")?.use { out ->
                        out.write(content.toByteArray())
                    }
                }
            } else {
                File(context.filesDir, name).writeText(content)
            }
        } catch (e: Exception) {
            // Nunca romper la Radio por no poder escribir el
            // diccionario. Lo aprendido se queda en memoria para esta
            // sesión y se reintentará al siguiente aprendizaje.
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

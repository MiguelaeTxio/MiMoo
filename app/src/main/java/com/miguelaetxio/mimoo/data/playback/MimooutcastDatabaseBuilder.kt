package com.miguelaetxio.mimoo.data.playback

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.miguelaetxio.mimoo.data.remote.MimooutcastCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * H15 (miMooutCast), S032 -- genera, DESDE EL PROPIO DISPOSITIVO, un
 * recopilatorio de enlaces de YouTube ya validados para TODOS los
 * géneros del catálogo de miMooutCast. Orden explícita de Miguel
 * Ángel, tras el fallo del script de GitHub Actions (bloqueado por
 * IP de centro de datos, sin las rutinas ni las cookies reales del
 * teléfono): *"Vas a montar el script en la propia aplicación...
 * desde ahí lo lanzo yo, desde la propia aplicación, utilizando las
 * mismas rutinas de la propia aplicación. Y esto lo vamos a hacer con
 * todos y cada uno de los géneros."*
 *
 * Reutiliza `PlayerManager.findValidatedTrackForGenre()` -- la MISMA
 * lógica que ya usa una sesión real de miMooutCast en vivo
 * (`suggestWorkForGenre()` + `resolveYoutubeCandidate()`), no ningún
 * mecanismo nuevo -- así que hereda directamente todo lo ya
 * verificado en esta sesión: cookies reales del dispositivo (si las
 * hay), IP real, misma tasa de acierto que se ha visto funcionando en
 * vivo durante horas de pruebas.
 *
 * Escribe el resultado de forma INCREMENTAL (tras cada género
 * completo, no solo al final) -- un proceso que recorre 24 géneros a
 * varios segundos por tema (Miguel Ángel, aceptado: *"se va a pegar
 * un buen rato haciendo eso, pero es necesario"*) puede tardar horas,
 * y perder todo el progreso por una interrupción a mitad camino sería
 * absurdo.
 *
 * El fichero resultante (`mimooutcast_database.json`, en el
 * almacenamiento privado de la app) es un PASO INTERMEDIO -- Miguel
 * Ángel tiene que exportarlo (ver el botón "Compartir" de Ajustes) y
 * dárselo a Claude para que lo añada a `app/src/main/assets/` del
 * repositorio, donde el compilador de Gradle ya lo empaqueta dentro
 * del APK sin ningún paso extra -- las siguientes instalaciones ya lo
 * llevarán de fábrica, sin tener que generarlo nunca más.
 */
@Singleton
class MimooutcastDatabaseBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerManager: PlayerManager,
    // H15, S032 -- misma fuente de subgéneros que ya usa la pantalla
    // (`MimooutcastViewModel.subgenresOf()`), para recorrer también
    // los subgéneros reales, no solo los 24 raíz. Orden de Miguel
    // Ángel: *"3, evidentemente 3"* -- ampliar el generador para que
    // también los recorra, aceptando que tarde mucho más.
    private val genreTree: com.miguelaetxio.mimoo.data.remote.GenreTree,
    // H15 -- fix real, S033: SIN esto, `suggestWorkForGenre()` /
    // `suggestRelatedArtist()` (compartidas con Radio) enrutan su log
    // por defecto a `radio_relacionados_debug.txt`, porque no hay
    // ninguna sesión real de miMooutCast activa mientras corre el
    // generador -- exactamente la contaminación del log de Radio que
    // Miguel Ángel prohibió explícita y repetidamente en S032 (ver
    // kdoc de `MiMooutcastSessionFlag`). Se activa al entrar en
    // `build()` y se restaura siempre en el `finally`, nunca dos
    // banderas que puedan desincronizarse -- misma única fuente de
    // verdad que ya usa `PlayerManager.manualAnchorActive`.
    private val mimooutcastSessionFlag: MiMooutcastSessionFlag,
) {
    data class BuildProgress(
        val isRunning: Boolean = false,
        val currentGenreIndex: Int = 0,
        val totalGenres: Int = MimooutcastCatalog.genres.size,
        val currentGenreLabel: String = "",
        val tracksFoundThisGenre: Int = 0,
        val totalTracksFound: Int = 0,
        val lastError: String? = null,
        val finished: Boolean = false,
    )

    private data class StoredTrack(val genre: String, val artist: String, val title: String, val youtube_id: String)

    // H15 -- fix real, S033: envoltorio del fichero persistido. Antes solo
    // se guardaba la lista plana de temas -- sin ningún rastro de qué
    // géneros ya se habían dado por agotados (objetivo alcanzado O
    // válvula de seguridad de 15 fallos disparada), cada "Parar" +
    // reanudar volvía a intentar TODOS los géneros incompletos desde
    // cero, gastando hasta 15 búsquedas reales por género ya agotado sin
    // avanzar de verdad. Confirmado por Miguel Ángel con datos reales:
    // "anoche llevaba casi 150 géneros... paras, empieza con 3, paras son
    // 12" -- y el total (3274) solo cuadra si la mayoría de esos 150
    // géneros son nicho y se agotaron con un puñado de temas, no con 100.
    private data class DatabaseFile(
        val tracks: List<StoredTrack> = emptyList(),
        val doneGenres: List<String> = emptyList(),
    )

    private val _progress = MutableStateFlow(BuildProgress())
    val progress: StateFlow<BuildProgress> = _progress.asStateFlow()

    @Volatile
    private var cancelled = false

    private val outputFile: File
        get() {
            // H15, S032 -- subcarpeta propia, no filesDir a secas --
            // tiene que coincidir exactamente con la ruta declarada en
            // file_provider_paths.xml o compartir() falla con
            // IllegalArgumentException en tiempo de ejecución.
            val dir = File(context.filesDir, "mimooutcast_export")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "mimooutcast_database.json")
        }

    /** Ruta absoluta del fichero generado, para el botón "Compartir" de Ajustes. */
    fun outputFilePath(): String = outputFile.absolutePath

    /**
     * H15 (miMooutCast), S032 -- URI compartible por el selector del
     * sistema, mismo mecanismo (`FileProvider`, misma autoridad) que
     * ya usa `ShareCodeRepository.buildLibraryShareFile()`. `null` si
     * todavía no se ha generado nada.
     */
    fun shareableUri(): android.net.Uri? {
        if (!outputFile.exists()) return null
        return androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", outputFile,
        )
    }

    fun cancel() {
        cancelled = true
    }

    /**
     * H15 (miMooutCast), S032 -- objetivo por género. 100 (mismo
     * tamaño que el recopilatorio curado de clásica) es el valor de
     * partida -- ajustable si Miguel Ángel pide más o menos una vez
     * visto cuánto tarda de verdad.
     */
    suspend fun build(targetPerGenre: Int = 100) {
        cancelled = false
        // H15 -- fix real, S033: activar la bandera de sesión ANTES de
        // la primera llamada compartida con Radio, para que TODO el log
        // de este recorrido caiga en mimooutcast_debug.txt. Se guarda el
        // valor previo (por si alguna vez esto se llamara mientras ya
        // hay una sesión real de miMooutCast en marcha, caso hoy
        // inexistente pero no imposible) y se restaura siempre, incluso
        // si build() se cancela o lanza.
        val previousFlag = mimooutcastSessionFlag.active
        mimooutcastSessionFlag.active = true
        try {
            buildInternal(targetPerGenre)
        } finally {
            mimooutcastSessionFlag.active = previousFlag
        }
    }

    private suspend fun buildInternal(targetPerGenre: Int) {
        val existing = loadExisting()
        val alreadyStored = existing.tracks.toMutableList()
        val storedByGenre = alreadyStored.groupBy { it.genre }.mapValues { it.value.toMutableList() }.toMutableMap()
        val doneGenres = existing.doneGenres.toMutableSet()

        // H15, S032 -- orden explícita de Miguel Ángel: "3, evidentemente
        // 3" -- recorrer también los subgéneros, no solo los géneros
        // raíz. Misma fuente que usa la pantalla
        // (`MimooutcastCatalog.subgenresOf()`), para que la base de
        // datos cubra exactamente lo que se puede elegir, ni más ni
        // menos. Clásica queda FUERA de este recorrido de subgéneros
        // -- ya tiene su propio recopilatorio fijo curado a mano (el
        // punto 38-41 del anexo), y la propia pantalla tampoco le
        // muestra ningún desplegable de subgéneros (`tapGenre()`, la
        // orden de "buscamos classical y punto").
        val allGenres: List<com.miguelaetxio.mimoo.data.remote.MimooutcastGenre> =
            MimooutcastCatalog.genres.flatMap { root ->
                if (root.mbGenre == "classical") {
                    listOf(root)
                } else {
                    listOf(root) + MimooutcastCatalog.subgenresOf(root, genreTree)
                }
            }

        _progress.value = BuildProgress(
            isRunning = true,
            totalGenres = allGenres.size,
            totalTracksFound = alreadyStored.size,
        )

        for ((genreIndex, genreEntry) in allGenres.withIndex()) {
            if (cancelled) break
            val genre = genreEntry.mbGenre
            val bucket = storedByGenre.getOrPut(genre) { mutableListOf() }
            var offset = 0
            // H15 -- fix real, S033: si este género ya venía completo de
            // una tanda anterior (retomar tras "Parar"), el `while` de
            // abajo no entra ni una vez y el progreso se queda clavado
            // con el género/etiqueta de la iteración anterior mientras
            // se salta de largo por todos los géneros ya hechos --
            // Miguel Ángel lo vio en vivo: "el total va bien pero el
            // género que se muestra no tiene nada que ver". Se actualiza
            // aquí, al ENTRAR en cada género, para que la pantalla
            // siempre refleje cuál se está mirando ahora mismo, esté
            // completo o no.
            _progress.value = _progress.value.copy(
                currentGenreIndex = genreIndex,
                currentGenreLabel = genreEntry.label,
                tracksFoundThisGenre = bucket.size,
                totalTracksFound = storedByGenre.values.sumOf { it.size },
            )
            // H15 -- fix real, S033: este género ya se dio por agotado en
            // una tanda anterior (objetivo alcanzado O válvula de
            // seguridad ya disparada) -- se salta SIN gastar ni una sola
            // búsqueda real. Sin esto, un "Parar" + reanudar reintentaba
            // ENTERO cada género nicho ya agotado (la inmensa mayoría),
            // que es justo lo que hacía que el índice se arrastrara desde
            // cero en cada reinicio en vez de avanzar de verdad.
            if (genre in doneGenres) continue

            // H15, S032 -- válvula de seguridad REAL, no un "ríndete
            // pronto": si un género concreto tiene de verdad menos de
            // `targetPerGenre` artistas verificables, no tiene sentido
            // reintentar para siempre SOLO en ese género -- se pasa al
            // siguiente tras varias rondas seguidas sin encontrar nada
            // nuevo. No es el mismo error que el punto 42 del anexo
            // (aquí SÍ hace falta un tope, porque el proceso tiene que
            // terminar en algún momento y avanzar por TODOS los
            // géneros y subgéneros, no quedarse atascado en uno solo).
            var consecutiveFailures = 0
            while (bucket.size < targetPerGenre && consecutiveFailures < 15 && !cancelled) {
                val excludeLower = bucket.map { it.artist.lowercase() }.toSet()
                val found = try {
                    playerManager.findValidatedTrackForGenre(genre, excludeLower, offset)
                } catch (e: Exception) {
                    _progress.value = _progress.value.copy(lastError = "${e::class.java.simpleName}: ${e.message}")
                    null
                }
                offset += 25
                if (found == null) {
                    consecutiveFailures++
                } else {
                    consecutiveFailures = 0
                    bucket.add(StoredTrack(genre, found.artist, found.title, found.youtubeId))
                }
                _progress.value = _progress.value.copy(
                    currentGenreIndex = genreIndex,
                    currentGenreLabel = genreEntry.label,
                    tracksFoundThisGenre = bucket.size,
                    totalTracksFound = storedByGenre.values.sumOf { it.size },
                )
            }
            // H15 -- fix real, S033: solo se marca agotado si el while
            // terminó por SUS PROPIAS condiciones (objetivo alcanzado o
            // válvula de seguridad) -- nunca por una cancelación a mitad
            // de búsqueda, que debe poder reintentar ese mismo género la
            // próxima vez en vez de darlo por bueno a medias.
            if (!cancelled) {
                doneGenres += genre
            }
            // Incremental -- se guarda al terminar CADA género, no solo al final.
            writeAll(storedByGenre.values.flatten(), doneGenres)
        }

        _progress.value = _progress.value.copy(isRunning = false, finished = !cancelled)
    }

    /**
     * H15 -- fix real, S033: el formato viejo (anterior a este fix) era
     * un array JSON plano de temas, sin ningún envoltorio ni
     * `doneGenres`. Si el fichero en disco todavía tiene ese formato
     * (el caso real de Miguel Ángel, con 3274 temas ya acumulados), se
     * cae a leerlo como array plano -- los temas ya encontrados NO se
     * pierden, solo se pierde el rastro de qué géneros ya se habían
     * agotado en esa tanda antigua, que se reintentan una única vez más
     * y a partir de ahí quedan ya marcados para siempre.
     */
    private fun loadExisting(): DatabaseFile {
        if (!outputFile.exists()) return DatabaseFile()
        val json = try {
            outputFile.readText()
        } catch (e: Exception) {
            return DatabaseFile()
        }
        return try {
            val type = object : TypeToken<DatabaseFile>() {}.type
            Gson().fromJson(json, type) ?: DatabaseFile(tracks = loadLegacyTracks(json))
        } catch (e: Exception) {
            DatabaseFile(tracks = loadLegacyTracks(json))
        }
    }

    private fun loadLegacyTracks(json: String): List<StoredTrack> {
        return try {
            val type = object : TypeToken<List<StoredTrack>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeAll(tracks: List<StoredTrack>, doneGenres: Set<String>) {
        val json = GsonBuilder().setPrettyPrinting().create()
            .toJson(DatabaseFile(tracks, doneGenres.toList()))
        outputFile.writeText(json)
    }
}

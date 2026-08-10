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
        val alreadyStored = loadExisting().toMutableList()
        val storedByGenre = alreadyStored.groupBy { it.genre }.mapValues { it.value.toMutableList() }.toMutableMap()

        _progress.value = BuildProgress(isRunning = true, totalTracksFound = alreadyStored.size)

        for ((genreIndex, genreEntry) in MimooutcastCatalog.genres.withIndex()) {
            if (cancelled) break
            val genre = genreEntry.mbGenre
            val bucket = storedByGenre.getOrPut(genre) { mutableListOf() }
            var offset = 0
            // H15, S032 -- válvula de seguridad REAL, no un "ríndete
            // pronto": si un género concreto tiene de verdad menos de
            // `targetPerGenre` artistas verificables, no tiene sentido
            // reintentar para siempre SOLO en ese género -- se pasa al
            // siguiente tras varias rondas seguidas sin encontrar nada
            // nuevo. No es el mismo error que el punto 42 del anexo
            // (aquí SÍ hace falta un tope, porque el proceso tiene que
            // terminar en algún momento y avanzar por los 24 géneros,
            // no quedarse atascado indefinidamente en uno solo).
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
            // Incremental -- se guarda al terminar CADA género, no solo al final.
            writeAll(storedByGenre.values.flatten())
        }

        _progress.value = _progress.value.copy(isRunning = false, finished = !cancelled)
    }

    private fun loadExisting(): List<StoredTrack> {
        return try {
            if (!outputFile.exists()) return emptyList()
            val json = outputFile.readText()
            val type = object : TypeToken<List<StoredTrack>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeAll(tracks: List<StoredTrack>) {
        val json = GsonBuilder().setPrettyPrinting().create().toJson(tracks)
        outputFile.writeText(json)
    }
}

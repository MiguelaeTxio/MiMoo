package com.miguelaetxio.mimoo.data.playback

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S037 -- gemelo exacto de `MimooutcastDatabaseBuilder` (que ya
 * genera, DESDE EL PROPIO DISPOSITIVO, la semilla de género), esta
 * vez para el diccionario de éxitos por década. Mismo motivo real: un
 * primer intento vía GitHub Actions falló por completo (3.188 de
 * 3.193 con error real "Sign in to confirm you're not a bot" --
 * YouTube bloquea las IPs de centro de datos de GitHub, exactamente
 * el mismo motivo por el que la semilla de género tuvo que
 * construirse en el propio teléfono, con sus cookies y su IP reales).
 *
 * A diferencia del generador de género, aquí NO hace falta
 * "descubrir" ningún artista -- el diccionario de éxitos ya trae
 * artista+canción exactos para cada década (`KnownHitsRepository`).
 * Solo hay que validar cada entrada contra YouTube una vez
 * (`PlayerManager.findValidatedTrackForDecadeHit()`), guardando el
 * `youtubeId` real para siempre.
 *
 * Igual que género: el fichero resultante
 * (`mimooutcast_decade_seed.json`, almacenamiento privado de la app)
 * es un paso intermedio -- hay que exportarlo (botón "Compartir" de
 * Ajustes) y dárselo a Claude para que lo añada a
 * `app/src/main/assets/`, donde ya lo lee
 * `MimooutcastDecadeSeedRepository` -- las siguientes instalaciones lo
 * llevarán de fábrica.
 */
@Singleton
class MimooutcastDecadeDatabaseBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerManager: PlayerManager,
    private val knownHitsRepository: com.miguelaetxio.mimoo.data.remote.KnownHitsRepository,
    // H15 -- mismo motivo exacto que en MimooutcastDatabaseBuilder: sin
    // esto, el log de este recorrido se mezclaría con el de Radio
    // automática, contaminación prohibida explícita y repetidamente
    // por Miguel Ángel.
    private val mimooutcastSessionFlag: MiMooutcastSessionFlag,
) {
    data class BuildProgress(
        val isRunning: Boolean = false,
        val currentDecadeIndex: Int = 0,
        val totalDecades: Int = 0,
        val currentDecadeLabel: String = "",
        val entriesFoundThisDecade: Int = 0,
        val totalEntriesFound: Int = 0,
        val decadesCompleted: Int = 0,
        val lastError: String? = null,
        val finished: Boolean = false,
    )

    private data class StoredEntry(val artist: String, val song: String, val youtube_id: String)
    private data class DatabaseFile(
        val entries: List<StoredEntryWithDecade> = emptyList(),
        val doneDecades: List<Int> = emptyList(),
    )
    private data class StoredEntryWithDecade(val decade: Int, val artist: String, val song: String, val youtube_id: String)

    private val _progress = MutableStateFlow(BuildProgress())
    val progress: StateFlow<BuildProgress> = _progress.asStateFlow()

    @Volatile
    private var cancelled = false

    private val outputFile: File
        get() {
            val dir = File(context.filesDir, "mimooutcast_export")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "mimooutcast_decade_database.json")
        }

    /** Ruta absoluta del fichero generado, para el botón "Compartir" de Ajustes. */
    fun outputFilePath(): String = outputFile.absolutePath

    /** URI compartible por el selector del sistema -- mismo mecanismo que MimooutcastDatabaseBuilder. `null` si todavía no se ha generado nada. */
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
     * Recorre las 8 décadas del diccionario de éxitos (1950-2020),
     * validando cada entrada (es + intl) contra YouTube real. Sin
     * objetivo por década -- se recorre el diccionario ENTERO, no una
     * muestra (a diferencia de género, aquí no hace falta parar en
     * 100: el diccionario ya es una selección curada de éxitos
     * reales, todas sus entradas merecen quedar validadas).
     */
    suspend fun build() {
        cancelled = false
        val previousFlag = mimooutcastSessionFlag.active
        mimooutcastSessionFlag.active = true
        try {
            val existing = loadExisting()
            val storedByDecade = existing.entries.groupBy { it.decade }
                .mapValues { (_, v) -> v.map { StoredEntry(it.artist, it.song, it.youtube_id) }.toMutableList() }
                .toMutableMap()
            val doneDecades = existing.doneDecades.toMutableSet()

            val decades = (1950..2020 step 10).toList()
            _progress.value = BuildProgress(
                isRunning = true,
                totalDecades = decades.size,
                totalEntriesFound = storedByDecade.values.sumOf { it.size },
                decadesCompleted = doneDecades.size,
            )

            for ((decadeIndex, decade) in decades.withIndex()) {
                if (cancelled) break
                val bucket = storedByDecade.getOrPut(decade) { mutableListOf() }
                _progress.value = _progress.value.copy(
                    currentDecadeIndex = decadeIndex,
                    currentDecadeLabel = decade.toString(),
                    entriesFoundThisDecade = bucket.size,
                    totalEntriesFound = storedByDecade.values.sumOf { it.size },
                    decadesCompleted = doneDecades.size,
                )
                if (decade in doneDecades) continue

                val alreadyDone = bucket.map { "${it.artist}|||${it.song}".lowercase() }.toMutableSet()
                val allHits = knownHitsRepository.allHitsForDecade(decade)
                for (hit in allHits) {
                    if (cancelled) break
                    val key = "${hit.artist}|||${hit.song}".lowercase()
                    if (key in alreadyDone) continue
                    val found = try {
                        playerManager.findValidatedTrackForDecadeHit(hit.artist, hit.song)
                    } catch (e: Exception) {
                        _progress.value = _progress.value.copy(lastError = "${e::class.java.simpleName}: ${e.message}")
                        null
                    }
                    if (found != null) {
                        bucket.add(StoredEntry(found.artist, found.title, found.youtubeId))
                        alreadyDone.add(key)
                    }
                    _progress.value = _progress.value.copy(
                        currentDecadeIndex = decadeIndex,
                        currentDecadeLabel = decade.toString(),
                        entriesFoundThisDecade = bucket.size,
                        totalEntriesFound = storedByDecade.values.sumOf { it.size },
                        decadesCompleted = doneDecades.size,
                    )
                    // Incremental de verdad -- se guarda tras CADA entrada, no solo al terminar la década.
                    writeAll(storedByDecade, doneDecades)
                }
                if (!cancelled) {
                    doneDecades += decade
                }
                writeAll(storedByDecade, doneDecades)
            }

            _progress.value = _progress.value.copy(isRunning = false, finished = !cancelled)
        } finally {
            mimooutcastSessionFlag.active = previousFlag
        }
    }

    private fun loadExisting(): DatabaseFile {
        if (!outputFile.exists()) return DatabaseFile()
        val json = try {
            outputFile.readText()
        } catch (e: Exception) {
            return DatabaseFile()
        }
        return try {
            val type = object : TypeToken<DatabaseFile>() {}.type
            Gson().fromJson(json, type) ?: DatabaseFile()
        } catch (e: Exception) {
            DatabaseFile()
        }
    }

    private fun writeAll(storedByDecade: Map<Int, List<StoredEntry>>, doneDecades: Set<Int>) {
        val entries = storedByDecade.flatMap { (decade, list) ->
            list.map { StoredEntryWithDecade(decade, it.artist, it.song, it.youtube_id) }
        }
        val json = GsonBuilder().setPrettyPrinting().create()
            .toJson(DatabaseFile(entries, doneDecades.toList()))
        outputFile.writeText(json)
    }
}

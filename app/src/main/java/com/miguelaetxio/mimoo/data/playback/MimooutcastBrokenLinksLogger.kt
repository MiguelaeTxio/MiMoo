package com.miguelaetxio.mimoo.data.playback

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S034 -- registra, por género, los enlaces de `mimooutcast_seed.json`
 * que dejan de funcionar en uso real (vídeo caído/retirado) y su
 * sustituto encontrado en vivo, para que en el siguiente ciclo de
 * generación+bundleo Miguel Ángel pueda exportar este fichero, dármelo,
 * y yo sustituya en `mimooutcast_seed.json` el roto por el sustituto --
 * "curándola a medida que los links vayan cayendo con el paso del
 * tiempo", en sus propias palabras.
 *
 * Método completo, palabras de Miguel Ángel: *"cuando un link falle,
 * pq está caído, se repone con los nuevos que estamos buscando, se
 * anota el link roto y el sustituto y en la próxima build se sustituye
 * el roto con el nuevo... se pone un contador de aviso de que se
 * necesita restaurar la instalación cuando el contador de links rotos
 * llegue a 10 en un género."*
 *
 * El "contador" es simplemente el tamaño de la lista de un género --
 * no hay ningún campo aparte que pueda desincronizarse de la lista
 * real.
 */
@Singleton
class MimooutcastBrokenLinksLogger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class BrokenEntry(
        val brokenYoutubeId: String,
        val brokenArtist: String,
        val brokenTitle: String,
        /** `null` hasta que se encuentra un sustituto en vivo para este mismo género. */
        val replacementYoutubeId: String? = null,
        val replacementArtist: String? = null,
        val replacementTitle: String? = null,
    )

    private data class BrokenLinksFile(val byGenre: Map<String, List<BrokenEntry>> = emptyMap())

    private val outputFile: File
        get() {
            // S034 -- misma subcarpeta que mimooutcast_database.json
            // (MimooutcastDatabaseBuilder.outputFile), ya declarada en
            // file_provider_paths.xml -- compartir() la necesita
            // exacta o falla con IllegalArgumentException.
            val dir = File(context.filesDir, "mimooutcast_export")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "mimooutcast_broken_links.json")
        }

    fun outputFilePath(): String = outputFile.absolutePath

    fun shareableUri(): android.net.Uri? {
        if (!outputFile.exists()) return null
        return androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", outputFile,
        )
    }

    @Synchronized
    private fun load(): MutableMap<String, MutableList<BrokenEntry>> {
        if (!outputFile.exists()) return mutableMapOf()
        return try {
            val json = outputFile.readText()
            val type = object : TypeToken<BrokenLinksFile>() {}.type
            val raw: BrokenLinksFile = Gson().fromJson(json, type) ?: BrokenLinksFile()
            raw.byGenre.mapValues { it.value.toMutableList() }.toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    @Synchronized
    private fun save(data: Map<String, List<BrokenEntry>>) {
        val json = GsonBuilder().setPrettyPrinting().create().toJson(BrokenLinksFile(data))
        outputFile.writeText(json)
    }

    /** Anota un enlace roto de la semilla, sin sustituto todavía. */
    @Synchronized
    fun recordBroken(genre: String, youtubeId: String, artist: String, title: String) {
        val key = genre.lowercase().trim()
        val data = load()
        val list = data.getOrPut(key) { mutableListOf() }
        list.add(BrokenEntry(brokenYoutubeId = youtubeId, brokenArtist = artist, brokenTitle = title))
        save(data)
    }

    /**
     * Rellena el sustituto de la última entrada rota SIN sustituto
     * todavía de ese género -- llamado cuando la siguiente pista que
     * suena de verdad para esa misma sesión es una encontrada en vivo
     * (no otra de la semilla, no viene con sustituto ya puesto).
     */
    @Synchronized
    fun recordReplacement(genre: String, youtubeId: String, artist: String, title: String) {
        val key = genre.lowercase().trim()
        val data = load()
        val list = data[key] ?: return
        val index = list.indexOfLast { it.replacementYoutubeId == null }
        if (index == -1) return
        list[index] = list[index].copy(
            replacementYoutubeId = youtubeId,
            replacementArtist = artist,
            replacementTitle = title,
        )
        save(data)
    }

    /** Nº de enlaces rotos anotados para un género -- el "contador" del aviso de restaurar instalación. */
    fun brokenCount(genre: String): Int = load()[genre.lowercase().trim()]?.size ?: 0

    /** ¿Este género ya llegó al umbral (10) que pide restaurar la instalación? */
    fun needsReinstall(genre: String): Boolean = brokenCount(genre) >= REINSTALL_THRESHOLD

    /** Géneros que ya llegaron al umbral, para el aviso general de Ajustes. */
    fun genresNeedingReinstall(): List<String> = load().filter { it.value.size >= REINSTALL_THRESHOLD }.keys.toList()

    private companion object {
        const val REINSTALL_THRESHOLD = 10
    }
}

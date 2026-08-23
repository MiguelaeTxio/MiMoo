package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S034 -- carga `mimooutcast_seed.json`, la semilla bundleada en el
 * APK con la base de datos real de miMooutCast (exportada por Miguel
 * Ángel tras semanas de generación en dispositivo, `mimooutcast_
 * database.json` renombrado). Mismo patrón exacto que
 * `ClassicalValidatedLinksRepository` (clásica ya tenía su propio
 * recopilatorio de enlaces validados, S032) generalizado a TODOS los
 * géneros, no solo clásica.
 *
 * Objetivo de fondo, palabras de Miguel Ángel: *"el objetivo de toda
 * esta mierda de estar un mes sacando la lista de temas es para que
 * esa lista vaya incluida en la aplicación y no se tenga que generar
 * más, que lo único que haya que hacer es ir curándola a medida que
 * los links vayan cayendo con el paso del tiempo."* Y el método
 * concreto, también suyo: *"se elige el género... y ya tenemos links
 * para encolar, 100 en concreto, al azar, pero nada más empezar...
 * comenzamos a buscar más temas y los vamos encolando a continuación
 * del tema que está sonando."*
 *
 * Cada entrada ya trae un `youtubeId` verificado en su día por el
 * propio motor de miMooutCast (mismo pipeline que la búsqueda en
 * vivo) -- no hace falta volver a buscar ni verificar, solo resolver
 * la URL de streaming (que sí caduca) del vídeo ya conocido. Igual
 * que clásica: mucho más rápido que una búsqueda completa.
 *
 * Si el asset no existe o está corrupto, `emptyList()` -- miMooutCast
 * cae directo a la búsqueda dinámica de género, como si no hubiera
 * semilla, sin romper nada.
 */
@Singleton
class MimooutcastSeedRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class SeedTrack(
        val artist: String,
        val title: String,
        val youtubeId: String,
    )

    private data class RawTrack(val genre: String, val artist: String, val title: String, val youtube_id: String)
    private data class RawSeedFile(val tracks: List<RawTrack> = emptyList(), val doneGenres: List<String> = emptyList())

    /** `lazy` -- se lee y parsea el asset una sola vez, la primera vez que se necesita. */
    private val byGenre: Map<String, List<SeedTrack>> by lazy {
        try {
            val json = context.assets.open("mimooutcast_seed.json")
                .bufferedReader()
                .use { it.readText() }
            val type = object : TypeToken<RawSeedFile>() {}.type
            val raw: RawSeedFile = Gson().fromJson(json, type)
            raw.tracks
                .groupBy { it.genre.lowercase().trim() }
                .mapValues { (_, tracks) -> tracks.map { SeedTrack(it.artist, it.title, it.youtube_id) } }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Pistas de la semilla para un género exacto (case-insensitive). Vacío si no hay semilla para él. */
    fun tracksForGenre(genre: String): List<SeedTrack> = byGenre[genre.lowercase().trim()].orEmpty()
}

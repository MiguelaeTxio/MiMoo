package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * H15 (miMooutCast), S032 -- carga `classical_validated_links.json`,
 * la salida de `tools/validate_classical_links.py` (ejecutado por
 * `.github/workflows/validate-classical-links.yml`). Orden de Miguel
 * Ángel, método completo: *"Como si haces un script aparte y lo
 * ejecutas desde GitHub para validar los links y se guardan en base
 * de datos. Una vez que tengas esos links se resuelven todos sin
 * principio... cuando se encolan estos 100 links funcionales y
 * conocidos de forma aleatoria se van buscando los otros cien
 * intercalándose a medida que se van encontrando."*
 *
 * Cada entrada ya trae un `youtubeId` comprobado -- a diferencia del
 * resto de fuentes de miMooutCast, aquí NO hace falta buscar ni
 * verificar nada en el momento (eso ya lo hizo el script offline),
 * solo resolver la URL de streaming del vídeo YA CONOCIDO -- mucho
 * más rápido que una búsqueda completa.
 *
 * Antes de que el workflow se ejecute por primera vez, el asset no
 * existe -- `emptyList()` en ese caso, sin romper nada: miMooutCast
 * simplemente cae directo a la búsqueda dinámica de género para
 * clásica, como cualquier otro género, hasta que haya un recopilatorio
 * validado que usar primero.
 */
@Singleton
class ClassicalValidatedLinksRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class ValidatedWork(
        val artist: String,
        val song: String,
        val youtubeId: String,
        val title: String,
    )

    private data class RawEntry(
        val artist: String,
        val song: String,
        val youtube_id: String,
        val title: String,
        val duration_seconds: Int = 0,
    )

    /** `lazy` -- se lee y parsea el asset una sola vez, la primera vez que se necesita. */
    val works: List<ValidatedWork> by lazy {
        try {
            val json = context.assets.open("classical_validated_links.json")
                .bufferedReader()
                .use { it.readText() }
            val type = object : TypeToken<List<RawEntry>>() {}.type
            val raw: List<RawEntry> = Gson().fromJson(json, type)
            raw.map { ValidatedWork(it.artist, it.song, it.youtube_id, it.title) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

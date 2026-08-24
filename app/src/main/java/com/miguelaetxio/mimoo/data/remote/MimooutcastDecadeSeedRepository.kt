package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S037 -- carga `mimooutcast_decade_seed.json`, gemelo exacto de
 * `MimooutcastSeedRepository` pero por DÉCADA en vez de por género.
 * Orden explícita y final de Miguel Ángel, palabras textuales: *"cuando
 * yo le dé al botón de década de los 90... la canción que haya sonando
 * sigue sonando y a continuación... empieza a sonar lo que he puesto...
 * ni 10 segundos ni nada. Llevamos un mes metiendo una base de datos de
 * dos pares de cojones para no tener que esperar."*
 *
 * Causa real diagnosticada con logs reales de la sesión: la semilla de
 * género SÍ trae el `youtubeId` ya validado de antemano -- arranca al
 * instante. El diccionario de éxitos (`KnownHitsRepository`, usado por
 * década sola y "Conocido en España") nunca se validó contra YouTube --
 * cada elección exigía buscar en vivo y a menudo fallaba ("0 de 6
 * resultados pasaron el filtro"). `tools/validate_decade_hits.py`
 * (GitHub Actions) hace esa validación UNA VEZ contra el diccionario
 * entero, con el MISMO criterio de filtro que
 * `PlayerManager.resolveYoutubeCandidate()` -- este repositorio solo
 * lee el resultado ya validado.
 *
 * Si el asset no existe o está corrupto, `emptyList()` -- miMooutCast
 * cae directo al diccionario sin validar / búsqueda en vivo, como si
 * no hubiera semilla, sin romper nada.
 */
@Singleton
class MimooutcastDecadeSeedRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class SeedTrack(
        val artist: String,
        val title: String,
        val youtubeId: String,
    )

    private data class RawTrack(val artist: String, val song: String, val youtube_id: String)

    /** `lazy` -- se lee y parsea el asset una sola vez, la primera vez que se necesita. */
    private val byDecade: Map<Int, List<SeedTrack>> by lazy {
        try {
            val json = context.assets.open("mimooutcast_decade_seed.json")
                .bufferedReader()
                .use { it.readText() }
            val type = object : TypeToken<Map<String, Map<String, RawTrack>>>() {}.type
            val raw: Map<String, Map<String, RawTrack>> = Gson().fromJson(json, type)
            raw.mapNotNull { (decadeKey, tracks) ->
                val decade = decadeKey.toIntOrNull() ?: return@mapNotNull null
                decade to tracks.values.map { SeedTrack(it.artist, it.song, it.youtube_id) }
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Pistas validadas de la semilla para una década exacta. Vacío si no hay semilla para ella todavía. */
    fun tracksForDecade(decadeBegin: Int): List<SeedTrack> = byDecade[decadeBegin].orEmpty()
}

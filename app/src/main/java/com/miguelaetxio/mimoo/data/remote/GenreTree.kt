package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Taxonomía real de géneros y subgéneros, leída de
 * `genre_tree.json` (S023).
 *
 * **Qué sustituye.** Hasta S023 la pertenencia de una entrada del
 * diccionario al género del ancla se decidía con `GENRE_FAMILIES`:
 * sacos de géneros escritos a mano por el modelo. No era comparación
 * por subcadena, pero el efecto era peor, porque el dato era opinión y
 * no dato. En uno de esos sacos convivían `new wave` y `post-punk`, y
 * por esa arista Tears for Fears entraba en una radio de Dead Can
 * Dance.
 *
 * Miguel Ángel lo cerró con una analogía que da la regla entera: un
 * oso hormiguero y un oso polar comparten ancestro -- mamífero -- y
 * eso no los hace parientes. Compartir un antepasado lejano no
 * significa nada; lo que importa es la posición en el árbol.
 *
 * **Dos tipos de arista, y solo uno se recorre.** MusicBrainz ya
 * distinguía lo que los sacos confundían. En la ficha de `dark wave`:
 *
 *     subgéneros:         ethereal wave, neoclassical dark wave,
 *                         neue deutsche todeskunst
 *     influenciado por:   new wave, synth-pop
 *
 * `new wave` NO es la carpeta padre de `dark wave`: solo la influyó, y
 * en la misma casilla que `synth-pop`. Aquí se cargan únicamente
 * `parents`/`children` -- el parentesco. Las aristas de influencia
 * están en el asset a propósito, para poder revisar la decisión sin
 * volver a rastrear, pero NO se recorren.
 *
 * **Por qué no se desciende desde cualquier género.** Medido sobre el
 * árbol real: el 83% de los 2176 géneros no tiene descendientes y el
 * percentil 99 está en 19. Las carpetas raíz se separan solas:
 *
 *     dark wave 3    post-punk 4     flamenco 3    alternative rock 22
 *     folk 80        hip hop 96      pop 102       rock 129
 *     classical 209  edm 278         electronic 350
 *
 * Bajar desde `dark wave` es seguro; bajar desde `rock` admite medio
 * catálogo y sería tan malo como subir al padre. De ahí
 * [MAX_DESCENDANTS_TO_DESCEND].
 */
@Singleton
class GenreTree @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private data class RawNode(
        val parents: List<String> = emptyList(),
        val children: List<String> = emptyList(),
    )

    private data class RawTree(val genres: Map<String, RawNode> = emptyMap())

    private val nodes: Map<String, RawNode> by lazy {
        try {
            val json = context.assets.open("genre_tree.json")
                .bufferedReader()
                .use { it.readText() }
            val type = object : TypeToken<RawTree>() {}.type
            val raw: RawTree = Gson().fromJson(json, type)
            raw.genres.mapKeys { it.key.lowercase().trim() }
        } catch (e: Exception) {
            // Sin árbol se sigue funcionando: la pertenencia se decide
            // solo por intersección exacta, que es estricta pero nunca
            // falsa. Preferimos eso a reinventar las familias a mano.
            emptyMap()
        }
    }

    /** Todos los descendientes de un género, sin recorrer influencias. */
    private val descendantsCache = mutableMapOf<String, Set<String>>()

    private fun descendants(genre: String): Set<String> {
        val key = genre.lowercase().trim()
        descendantsCache[key]?.let { return it }

        val found = mutableSetOf<String>()
        val pending = ArrayDeque<String>()
        pending += key
        // El grafo de MusicBrainz tiene ciclos (`gothic rock` aparece
        // colgando de dos ramas distintas), así que se lleva conjunto
        // de visitados en vez de asumir un árbol limpio.
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            for (child in nodes[current]?.children.orEmpty()) {
                val normalized = child.lowercase().trim()
                if (found.add(normalized)) pending += normalized
            }
        }
        descendantsCache[key] = found
        return found
    }

    /** ¿Existe este género en la taxonomía? */
    fun isKnown(genre: String): Boolean = genre.lowercase().trim() in nodes

    /**
     * S034 -- géneros que Miguel Ángel descartó EN TODAS PARTES, no
     * solo en miMooutCast: *"que no deben molestar en ningún sitio, ni
     * en la radio."* Estos 41 nunca dan un tema real -- confirmado con
     * dos pasadas completas del generador de miMooutCast y un log de
     * 67 minutos (S034) mostrando tres causas distintas (escasez
     * genuina de datos en MusicBrainz, red intermitente, o verificación
     * final que rechaza siempre a los pocos candidatos que aparecen) --
     * y como el ancla de la Radio automática se calcula del artista
     * real que se está escuchando (no de un catálogo fijo), cualquiera
     * de estos 41 puede colarse como género ancla igual que en
     * miMooutCast si MusicBrainz lo etiqueta así. Único punto de
     * verdad: se consulta desde `RadioRepository.findCandidates()` y
     * `RadioRepository.suggestWorkForGenre()` (los dos puntos de
     * entrada reales a MusicBrainz compartidos por Radio y
     * miMooutCast) para cortar ANTES de gastar ninguna llamada de red,
     * y desde `MimooutcastCatalog.subgenresOf()` para que tampoco se
     * ofrezcan en el desplegable de subgéneros de la pantalla. NO
     * afecta a `directChildren()`/`isDescendantOf()`/`isSpecific()` --
     * la clasificación de qué género pertenece a qué carpeta sigue
     * intacta; esto solo bloquea que se use como TÉRMINO DE BÚSQUEDA
     * en vivo.
     */
    fun isBarren(genre: String): Boolean = genre.lowercase().trim() in BARREN_GENRES

    /**
     * H15 (miMooutCast) -- hijos DIRECTOS (un solo nivel, no todos los
     * descendientes) de un género, para desplegar un segundo nivel de
     * subgéneros al pinchar uno de los géneros raíz de
     * `MimooutcastCatalog` -- petición explícita de Miguel Ángel
     * (2026-08-06): *"pinchar electrónica y mostrar un nivel de
     * géneros dentro de electrónica... siempre que se pueda"*. Vacío
     * cuando el género no tiene hijos catalogados (p.ej. una hoja) --
     * la pantalla lo trata entonces como género final, sin segundo
     * nivel.
     */
    fun directChildren(genre: String): List<String> =
        nodes[genre.lowercase().trim()]?.children.orEmpty()
            .map { it.lowercase().trim() }
            .distinct()
            .sorted()

    /**
     * ¿Es [candidate] un descendiente de [ancestor]?
     *
     * Solo hacia ABAJO. Que `dark wave` sea descendiente de algo no
     * autoriza a subir a ese algo y volver a bajar por otra rama: ese
     * es exactamente el camino oso polar -> mamífero -> oso hormiguero.
     */
    fun isDescendantOf(candidate: String, ancestor: String): Boolean =
        candidate.lowercase().trim() in descendants(ancestor)

    /**
     * ¿Es [genre] una carpeta concreta y no una raíz?
     *
     * Esto gobierna también la INTERSECCIÓN, no solo el descenso, y es
     * la pieza que faltaba. Medido sobre la radio real de P!nk: Creed y
     * Café Tacvba entraban compartiendo únicamente `rock`, que tiene
     * 129 descendientes. Compartir la carpeta raíz no es parentesco --
     * es el oso hormiguero otra vez. Christina Aguilera, en cambio,
     * comparte `electropop`, `dance-pop` y `contemporary r&b`: tres
     * carpetas concretas, y eso sí significa algo.
     */
    fun isSpecific(genre: String): Boolean =
        descendants(genre).size <= MAX_DESCENDANTS_TO_DESCEND

    /**
     * ¿Es [genre] una carpeta lo bastante contenida como para que
     * descender por ella signifique algo? Como [isSpecific], pero
     * además tiene que tener hijos: descender de una hoja no da nada.
     */
    fun isSpecificEnoughToDescend(genre: String): Boolean =
        descendants(genre).size in 1..MAX_DESCENDANTS_TO_DESCEND

    /**
     * ¿Comparten padre inmediato? Es el último peldaño que Miguel Ángel
     * aceptó ("habrá géneros que son hermanos, muy parecidos, que
     * varían solo en algo -- eso a última hora es comible"), y se
     * aplica con la misma cautela: si el padre común es una carpeta
     * raíz, ser hermanos no significa nada. `new wave` y `post-punk`
     * cuelgan los dos de `rock`, y eso no los hace intercambiables.
     */
    fun shareImmediateParent(a: String, b: String): Boolean {
        val parentsA = nodes[a.lowercase().trim()]?.parents.orEmpty()
            .map { it.lowercase().trim() }
        if (parentsA.isEmpty()) return false
        val parentsB = nodes[b.lowercase().trim()]?.parents.orEmpty()
            .map { it.lowercase().trim() }.toSet()
        return parentsA.any { it in parentsB && isSpecificEnoughToDescend(it) }
    }

    private companion object {
        /**
         * Tope de descendientes para considerar que un género es una
         * carpeta concreta y no una raíz.
         *
         * 25 sale de la medición del árbol real, no de la intuición:
         * el percentil 99 está en 19 descendientes, y por encima solo
         * quedan las raíces (`folk` 80, `pop` 102, `rock` 129,
         * `electronic` 350). Deja dentro `alternative rock` (22), que
         * es una escena reconocible, y fuera todo lo que ya no
         * significa nada como criterio musical.
         */
        const val MAX_DESCENDANTS_TO_DESCEND = 25

        /** Ver el kdoc de `isBarren()`. */
        val BARREN_GENRES: Set<String> = setOf(
            "arabesk rap", "bagad", "baguala", "balani show", "budots",
            "bérite club", "doble paso", "falak", "fijiri", "genge",
            "graphical sound", "hipco", "indo jazz", "isa", "jersey sound",
            "kréyol djaz", "ländlermusik", "miejski folk", "mulatós",
            "neo-bop", "nepali lok geet", "ori deck", "paramaribop",
            "pop kreatif", "pop minang", "rabbit song", "rock urbano mexicano",
            "rom kbach", "samba rap", "scrumpy and western", "seguidilla",
            "stornello", "sufi rock", "sutartinės", "sweet jazz", "tajaraste",
            "tonada asturiana", "trikitixa", "waulking song", "wong shadow",
            "xuc",
        )
    }
}

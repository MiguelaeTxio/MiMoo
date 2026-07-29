package com.miguelaetxio.mimoo.data.remote

/**
 * S026 -- CALIDAD DE LA COINCIDENCIA DE GÉNERO, compartida por las
 * tres porciones de la Radio (Conocidos, Disco y Exploración).
 *
 * Orden de Miguel Ángel tras revisar un log real donde Elton John y
 * Emerson, Lake & Palmer aparecían en una radio de Led Zeppelin: *"si
 * tenemos artistas que coinciden con dos o tres géneros, son
 * preferibles... si Led Zeppelin tiene 'rock progresivo' entre siete
 * géneros más, y encontramos uno que ÚNICAMENTE tiene rock progresivo,
 * ese no lo ponemos. Ponemos el que tenga rock progresivo, rock,
 * classic rock... y que tenga tres o cuatro géneros que coincidan."*
 *
 * **Primer intento (este mismo commit, corregido más abajo): exigir
 * 2+ géneros ESPECÍFICOS compartidos, sin lista negra.** Falló contra
 * un caso real, verificado con captura de pantalla de dispositivo:
 * Supertramp entró en la radio de Led Zeppelin porque comparten DOS
 * etiquetas -- `classic rock` Y `progressive rock` -- que
 * `GenreTree.isSpecific()` trata como carpetas concretas (0 y 6
 * descendientes respectivamente) pero que en la práctica son
 * etiquetas de FORMATO/ÉPOCA de MusicBrainz, no de sonido: se cuelgan
 * de casi cualquier artista de rock de los 60-80 con tal de que suene
 * "clásico" o "elaborado". Dos etiquetas de formato compartidas siguen
 * siendo cero información real, así que el conteo por sí solo no
 * basta -- exactamente lo que Miguel Ángel describió: *"el único que
 * se parece es que son personas que forman un grupo y que hacen
 * música... la música que hacen no se parece absolutamente en
 * nada."*
 *
 * **Corrección: lista corta de etiquetas de FORMATO/ÉPOCA que nunca
 * cuentan**, ni para FUERTE ni para DÉBIL -- se descartan antes de
 * cualquier conteo. No es una lista de "géneros que no me gustan": son
 * las etiquetas que la propia documentación de MusicBrainz describe
 * como agrupación de época o de emisora de radio, no de sonido
 * (`classic rock`, `rock and roll`, `mainstream rock`). `progressive
 * rock` NO entra en esta lista -- es un género real y específico
 * (Yes, King Crimson, Genesis); el problema no es la etiqueta en sí,
 * es que la propia semilla de Led Zeppelin la incluye entre sus ocho
 * géneros de forma cuestionable. Quitando `classic rock` de la cuenta,
 * Supertramp y Led Zeppelin comparten solo `progressive rock` -- un
 * género específico, uno solo -- así que Supertramp cae a DÉBIL/
 * último recurso en vez de FUERTE, que es justo donde debía estar.
 *
 * Con la etiqueta de formato descartada, la regla de fondo que pidió
 * Miguel Ángel sigue intacta: DOS O MÁS géneros específicos REALES
 * compartidos es la categoría preferente; exactamente uno es último
 * recurso, admitido solo cuando no queda nadie con dos o más: *"que lo
 * admita... porque no haya una definición, pero que luego, en
 * general, compare dos o más géneros."*
 */
object GenreMatchQuality {
    /**
     * Etiquetas de FORMATO o ÉPOCA de MusicBrainz que nunca cuentan
     * como género para la coincidencia, en ningún peldaño: no
     * describen un sonido, describen una emisora de radio o una
     * década. Lista corta y con motivo escrito para cada una --
     * ampliable si aparece un caso real nuevo, nunca a base de
     * intuición sin verificar en un log o una captura real.
     */
    private val FORMAT_TAGS = setOf(
        // Categoría de emisora de radio en EE.UU. ("clásicos del
        // rock"), no un sonido -- caso real que motivó esta lista:
        // Supertramp entraba en la radio de Led Zeppelin por esto.
        "classic rock",
        // Descriptor de ÉPOCA (rock de los 50), se cuelga
        // retroactivamente de casi cualquier cosa con guitarra y
        // batería de esa década en adelante.
        "rock and roll",
        // Categoría de lista de éxitos de Billboard (radio
        // "Mainstream Rock"), no un sonido.
        "mainstream rock",
    )

    private fun stripFormatTags(genres: Set<String>): Set<String> = genres - FORMAT_TAGS

    /**
     * Calcula la calidad de la coincidencia entre los géneros de un
     * candidato y los del ancla. Reutiliza exactamente los mismos
     * peldaños que ya validó `KnownHitsRepository.matchesGenre()`
     * (intersección específica -> descenso -> hermanos -> ancla
     * genérica de rescate), añadiendo el conteo que decide FUERTE vs
     * DÉBIL en el primer peldaño, sobre géneros ya limpios de
     * etiquetas de formato.
     */
    fun of(candidateGenres: Set<String>, anchorGenres: Set<String>, genreTree: GenreTree): Result {
        val candidates = stripFormatTags(
            candidateGenres.map { it.lowercase().trim() }.filter { it.isNotBlank() }.toSet(),
        )
        val anchors = stripFormatTags(
            anchorGenres.map { it.lowercase().trim() }.filter { it.isNotBlank() }.toSet(),
        )
        if (candidates.isEmpty() || anchors.isEmpty()) return Result(Level.NONE, emptySet())

        // 1 -- intersección directa de géneros ESPECÍFICOS (no carpetas
        // raíz). El conteo de esta intersección es lo que decide FUERTE
        // (2+) vs DÉBIL (exactamente 1).
        val specificShared = candidates.filter { it in anchors && genreTree.isSpecific(it) }.toSet()
        if (specificShared.size >= 2) return Result(Level.STRONG, specificShared)
        if (specificShared.size == 1) return Result(Level.WEAK, specificShared)

        // 2 -- descenso desde el ancla, nunca ascenso. Señal más débil
        // que una intersección directa -- nunca FUERTE aunque coincidan
        // varias ramas, porque no son el mismo género, solo parentesco.
        val descendable = anchors.filter { genreTree.isSpecificEnoughToDescend(it) }
        if (descendable.any { anchor -> candidates.any { genreTree.isDescendantOf(it, anchor) } }) {
            return Result(Level.WEAK, emptySet())
        }

        // 3 -- hermanos, último peldaño de parentesco real.
        if (anchors.any { anchor -> candidates.any { genreTree.shareImmediateParent(it, anchor) } }) {
            return Result(Level.WEAK, emptySet())
        }

        // 4 -- ancla enteramente genérica (sin ninguna carpeta
        // concreta): lo ancho es lo único que hay y tiene que contar,
        // igual que en `matchesGenre()` original (S023, caso Radio
        // Futura).
        if (anchors.none { genreTree.isSpecific(it) } && candidates.any { it in anchors }) {
            return Result(Level.WEAK, emptySet())
        }

        return Result(Level.NONE, emptySet())
    }

    enum class Level { NONE, WEAK, STRONG }

    data class Result(val level: Level, val specificSharedGenres: Set<String>) {
        val matches: Boolean get() = level != Level.NONE
        val isStrong: Boolean get() = level == Level.STRONG
    }
}

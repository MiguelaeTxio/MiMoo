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
 * Causa raíz encontrada en el log: `classic rock` y `progressive rock`
 * tienen CERO descendientes en `genre_tree.json`, así que
 * `GenreTree.isSpecific()` los trata igual que una carpeta concreta de
 * verdad (`dark wave`, `post-punk`...). En la práctica son etiquetas
 * de formato de radio que MusicBrainz cuelga de casi cualquier artista
 * de rock/pop de los 60-80 -- 63 de las 1.682 entradas del diccionario
 * de éxitos y 46 de los 1.161 artistas de la semilla de ancla llevan
 * `classic rock`. Compartir solo esa etiqueta con el ancla no dice
 * nada real sobre el artista.
 *
 * En vez de mantener una lista negra de géneros genéricos (fragil,
 * exige mantenimiento a mano cada vez que aparezca uno nuevo), la
 * solución que pidió Miguel Ángel resuelve el problema por diseño:
 * exigir DOS O MÁS géneros específicos compartidos para la categoría
 * preferente. Un único género específico compartido -- sea `classic
 * rock` o cualquier otro -- nunca basta por sí solo para la categoría
 * preferente, así que la etiqueta genérica deja de ser un puente por
 * su cuenta. Solo en el peor de los casos (nadie del pool comparte dos
 * o más) se admite como último recurso, tal como él lo pidió
 * explícitamente: *"que lo admita... porque no haya una definición,
 * pero que luego, en general, compare dos o más géneros."*
 */
object GenreMatchQuality {
    /**
     * Calcula la calidad de la coincidencia entre los géneros de un
     * candidato y los del ancla. Reutiliza exactamente los mismos
     * peldaños que ya validó `KnownHitsRepository.matchesGenre()`
     * (intersección específica -> descenso -> hermanos -> ancla
     * genérica de rescate), añadiendo el conteo que decide FUERTE vs
     * DÉBIL en el primer peldaño.
     */
    fun of(candidateGenres: Set<String>, anchorGenres: Set<String>, genreTree: GenreTree): Result {
        val candidates = candidateGenres.map { it.lowercase().trim() }.filter { it.isNotBlank() }.toSet()
        val anchors = anchorGenres.map { it.lowercase().trim() }.filter { it.isNotBlank() }.toSet()
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

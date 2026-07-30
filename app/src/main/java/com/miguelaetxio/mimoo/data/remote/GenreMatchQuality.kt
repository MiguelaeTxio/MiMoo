package com.miguelaetxio.mimoo.data.remote

/**
 * S026 -- CALIDAD DE LA COINCIDENCIA DE GÉNERO, compartida por las
 * tres porciones de la Radio (Conocidos, Disco y Exploración).
 *
 * HISTORIA DEL DISEÑO, con motivo real en cada paso:
 *
 * 1. Primer intento: exigir 2+ géneros ESPECÍFICOS compartidos.
 *    Falló contra un caso real (captura de dispositivo): Supertramp
 *    entró en la radio de Led Zeppelin compartiendo DOS etiquetas de
 *    FORMATO (`classic rock`, `progressive rock`), no de sonido.
 * 2. Corrección: lista corta de etiquetas de formato/época que nunca
 *    cuentan (`classic rock`, `rock and roll`, `mainstream rock`).
 *    Arregló Supertramp, pero el corte "2+/1" seguía dejando pasar
 *    como último recurso a artistas con MUCHOS géneros catalogados
 *    (Pink Floyd: 13, Fleetwood Mac: 8) de los que solo UNO --
 *    `blues rock`, una etiqueta ancha -- coincidía con Led Zeppelin.
 *    Petición de Miguel Ángel con log real: *"Pink Floyd... hay un
 *    mundo enorme... Fleetwood Mac más en una lista con Neil Young,
 *    Creedence Clearwater Revival."*
 * 3. **Diseño actual: PORCENTAJE, no conteo.** Miguel Ángel, textual:
 *    *"si tiene diez géneros, y el otro tiene diez géneros, y tienen
 *    ocho que son iguales, coincide... un 30% o un 40%... configurable
 *    en ajustes, con escalones de diez."* Verificado contra los datos
 *    reales antes de implementar (intersección/unión de géneros
 *    ESPECÍFICOS, formato ya descartado):
 *
 *      Pink Floyd      1/17 =  6%   Fleetwood Mac   1/10 = 10%
 *      Queen           1/12 =  8%   Jethro Tull     2/6  = 33%
 *      Motörhead       2/5  = 40%   Black Sabbath   3/7  = 43%
 *      Deep Purple     3/6  = 50%
 *
 *    Con el 40% por defecto, la línea separa exactamente donde Miguel
 *    Ángel la puso a ojo -- Jethro Tull queda justo fuera (33%), como
 *    él mismo dijo que "en un momento dado podría pasar" pero sin
 *    insistir en que tuviera que hacerlo.
 *
 * El umbral es el porcentaje MÍNIMO de intersección/unión para admitir
 * un candidato -- configurable en Ajustes
 * (`UiPreferencesManager.radioGenreMatchThresholdPercent`, escalones
 * de 10, por defecto 40). Sustituye por completo el sistema previo de
 * dos niveles (FUERTE/DÉBIL): ya no hay "último recurso" aparte --
 * un candidato o llega al porcentaje, o no entra esa vuelta, igual que
 * pasaba antes cuando ninguno llegaba al conteo mínimo.
 *
 * Los peldaños de descenso/hermanos/ancla-genérica del diseño anterior
 * (S022-S023) se retiran con este cambio: eran parte del sistema
 * booleano "¿coincide o no?", y no tienen un equivalente limpio en un
 * modelo de porcentaje sin inventar una equivalencia arbitraria. La
 * intersección directa de géneros específicos ya cubre bien la
 * inmensa mayoría de los casos reales vistos hasta ahora.
 */
object GenreMatchQuality {
    /**
     * Etiquetas de FORMATO o ÉPOCA de MusicBrainz que nunca cuentan
     * como género para la coincidencia: no describen un sonido,
     * describen una emisora de radio o una década. Lista corta y con
     * motivo escrito para cada una -- ampliable si aparece un caso
     * real nuevo, nunca a base de intuición sin verificar en un log o
     * una captura real.
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

    private fun specificGenres(genres: Set<String>, genreTree: GenreTree): Set<String> {
        val clean = genres.map { it.lowercase().trim() }.filter { it.isNotBlank() }.toSet() - FORMAT_TAGS
        return clean.filter { genreTree.isSpecific(it) }.toSet()
    }

    /**
     * @param thresholdPercent mínimo de intersección/unión (0-100)
     * para admitir el candidato -- viene de
     * `UiPreferencesManager.radioGenreMatchThresholdPercent`.
     */
    fun of(
        candidateGenres: Set<String>,
        anchorGenres: Set<String>,
        genreTree: GenreTree,
        thresholdPercent: Int,
    ): Result {
        val candidates = specificGenres(candidateGenres, genreTree)
        val anchors = specificGenres(anchorGenres, genreTree)
        val union = candidates + anchors
        if (union.isEmpty()) return Result(matches = false, overlapPercent = 0, sharedGenres = emptySet())

        val shared = candidates intersect anchors
        val percent = (shared.size * 100) / union.size
        return Result(
            matches = percent >= thresholdPercent,
            overlapPercent = percent,
            sharedGenres = shared,
        )
    }

    data class Result(val matches: Boolean, val overlapPercent: Int, val sharedGenres: Set<String>)
}

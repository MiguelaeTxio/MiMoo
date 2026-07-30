package com.miguelaetxio.mimoo.data.remote

/**
 * S026 -- LOS CUATRO GRANDES GRUPOS DE ORIGEN, sustituyendo al binario
 * España/resto que cerró S020.
 *
 * Decisión completa de Miguel Ángel, cerrada a lo largo de varios
 * mensajes -- y CORREGIDA una vez probada en dispositivo real:
 * - **Cuatro grupos**: Hispanoamérica, Anglosajona, Europea, Mundial.
 * - **Pared TOTAL entre grupos**, igual que el binario anterior --
 *   *"el origen en estos grupos grandes es una pared."* Cero
 *   excepciones, cero mezcla entre grupos vecinos.
 * - **Dentro de un grupo, el país exacto no manda nada** -- *"prefiero
 *   que Led Zeppelin me traiga a Van Halen o AC/DC antes que rebuscar
 *   en GB."* Con Led Zeppelin (GB, Anglosajona) se abre igual a US,
 *   AU, IE, NZ, CA desde el principio, sin preferencia por el país
 *   exacto del ancla.
 * - **Puerto Rico** entra en Hispanoamérica, NO en Anglosajona --
 *   verificado que MusicBrainz ya lo distingue de EEUU con su propio
 *   código de país (`PR`), así que no hay riesgo real de que Bad Bunny
 *   se cuele en una radio anglosajona por error.
 * - **CORRECCIÓN, probada en dispositivo real: Brasil y Portugal van a
 *   EUROPEA, no a Hispanoamérica.** Primer diseño: iban con España por
 *   idioma/decisión explícita ("Portugal va con nosotros siempre").
 *   Probado con un ancla española real (Ilegales, rock and roll/ES),
 *   la Exploración metía muchísimos artistas portugueses (Mão Morta,
 *   Heróis do Mar, GNR, Salvador Sobral...) desconocidos en España, y
 *   la experiencia de escucha no era agradable. Cita textual: *"me he
 *   equivocado totalmente... mete muchos temas de Portugal... no es
 *   una radio agradable... dejamos Portugal y Brasil, lo englobamos en
 *   Europa."* El grupo se renombra de Iberoamericana a
 *   **Hispanoamérica** porque, sin Brasil ni Portugal, ya describe
 *   mejor lo que contiene: España + el mundo hispanohablante (Luis
 *   Miguel, Bad Bunny, etc., "tienen que estar").
 * - **Canadá** entra en Anglosajona (decisión explícita), pese a tener
 *   una parte francófona real.
 * - **Mundial** es un cajón de lo que no encaja en los otros tres
 *   (Asia, África, Oriente Medio, Oceanía no anglosajona) -- no
 *   pretende ser un grupo culturalmente coherente, solo "lo que
 *   queda".
 *
 * `of(null)` devuelve `null` a propósito -- un país desconocido (a
 * veces MusicBrainz no lo da) no se trata como "Mundial", porque eso
 * lo amurallaría contra todo lo demás sin motivo real. `null` significa
 * "no se sabe", y el código que llama debe decidir cómo degradar
 * (mismo principio que el resto de la Radio: no saber no es lo mismo
 * que "no hay").
 */
enum class OriginGroup {
    HISPANOAMERICA,
    ANGLOSAJONA,
    EUROPEA,
    MUNDIAL;

    companion object {
        private val HISPANOAMERICA_COUNTRIES = setOf(
            "ES",
            "MX", "AR", "CO", "PR", "DO", "PE", "CL", "VE", "EC", "UY", "PY", "BO",
            "CR", "PA", "GT", "HN", "SV", "NI", "CU",
        )

        private val ANGLOSAJONA_COUNTRIES = setOf("GB", "US", "AU", "IE", "NZ", "CA")

        /**
         * S026 -- incluye `PT` y `BR` (Portugal y Brasil), movidos
         * aquí desde Hispanoamérica tras la corrección con dispositivo
         * real -- ver el comentario de la clase.
         */
        private val EUROPEA_COUNTRIES = setOf(
            "PT", "BR",
            "FR", "DE", "IT", "NL", "BE", "SE", "NO", "DK", "FI", "CH", "AT", "PL",
            "GR", "RU", "IS", "HU", "CZ", "SK", "RO", "BG", "HR", "SI", "EE", "LV",
            "LT", "UA", "RS", "AL", "MK", "MT", "CY", "LU", "MC", "AD", "SM", "VA",
            "BA", "ME", "XK",
        )

        /**
         * Países del grupo, para construir cláusulas de MusicBrainz
         * tipo `country:GB OR country:US OR ...` -- ver
         * `RadioRepository.buildGenreQuery()`.
         */
        fun countriesOf(group: OriginGroup): Set<String> = when (group) {
            HISPANOAMERICA -> HISPANOAMERICA_COUNTRIES
            ANGLOSAJONA -> ANGLOSAJONA_COUNTRIES
            EUROPEA -> EUROPEA_COUNTRIES
            MUNDIAL -> emptySet() // cajón de descarte, no una lista cerrada -- ver buildGenreQuery()
        }

        /**
         * `null` si `countryCode` es `null` (dato desconocido -- no
         * confundir con Mundial, que es un grupo real). Cualquier
         * país no listado explícitamente en los otros tres grupos cae
         * en Mundial por descarte -- es el cajón de lo que queda.
         */
        fun of(countryCode: String?): OriginGroup? {
            val c = countryCode?.trim()?.uppercase()?.ifBlank { null } ?: return null
            return when (c) {
                in HISPANOAMERICA_COUNTRIES -> HISPANOAMERICA
                in ANGLOSAJONA_COUNTRIES -> ANGLOSAJONA
                in EUROPEA_COUNTRIES -> EUROPEA
                else -> MUNDIAL
            }
        }
    }
}

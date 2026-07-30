package com.miguelaetxio.mimoo.data.remote

/**
 * S026 -- LOS CUATRO GRANDES GRUPOS DE ORIGEN, sustituyendo al binario
 * España/resto que cerró S020.
 *
 * Decisión completa de Miguel Ángel, cerrada a lo largo de varios
 * mensajes:
 * - **Cuatro grupos**: Iberoamericana, Anglosajona, Europea, Mundial.
 * - **Pared TOTAL entre grupos**, igual que el binario anterior --
 *   *"el origen en estos grupos grandes es una pared."* Cero
 *   excepciones, cero mezcla entre grupos vecinos.
 * - **Dentro de un grupo, el país exacto no manda nada** -- *"prefiero
 *   que Led Zeppelin me traiga a Van Halen o AC/DC antes que rebuscar
 *   en GB."* Con Led Zeppelin (GB, Anglosajona) se abre igual a US,
 *   AU, IE, NZ, CA desde el principio, sin preferencia por el país
 *   exacto del ancla.
 * - **Puerto Rico** entra en Iberoamericana, NO en Anglosajona --
 *   verificado que MusicBrainz ya lo distingue de EEUU con su propio
 *   código de país (`PR`), así que no hay riesgo real de que Bad Bunny
 *   se cuele en una radio anglosajona por error.
 * - **Brasil y Portugal** entran en Iberoamericana por idioma/decisión
 *   explícita ("Portugal va con nosotros siempre"), pese a que
 *   Portugal es geográficamente Europa.
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
    IBEROAMERICANA,
    ANGLOSAJONA,
    EUROPEA,
    MUNDIAL;

    companion object {
        private val IBEROAMERICANA_COUNTRIES = setOf(
            "ES", "PT", "BR",
            "MX", "AR", "CO", "PR", "DO", "PE", "CL", "VE", "EC", "UY", "PY", "BO",
            "CR", "PA", "GT", "HN", "SV", "NI", "CU",
        )

        private val ANGLOSAJONA_COUNTRIES = setOf("GB", "US", "AU", "IE", "NZ", "CA")

        private val EUROPEA_COUNTRIES = setOf(
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
            IBEROAMERICANA -> IBEROAMERICANA_COUNTRIES
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
                in IBEROAMERICANA_COUNTRIES -> IBEROAMERICANA
                in ANGLOSAJONA_COUNTRIES -> ANGLOSAJONA
                in EUROPEA_COUNTRIES -> EUROPEA
                else -> MUNDIAL
            }
        }
    }
}

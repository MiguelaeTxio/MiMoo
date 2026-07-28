package com.miguelaetxio.mimoo.util

import java.text.Normalizer

/**
 * Normalizes a string for accent-insensitive, case-insensitive text
 * search/filtering, shared by every filter field in the app
 * (Biblioteca, Playlists) and by every name-based cross-reference in
 * H12 (ArtistScreen/AlbumScreen matching against local downloads).
 * Strips diacritics (NFD decomposition + removal of combining marks,
 * same technique already used by `sortLetterFor` in
 * `LibraryViewModel` for the artist-letter grouping) so that typing
 * "angel" matches "Ángel" and vice versa -- a real usability gap in
 * the original filter, which only did `trim().lowercase()` and
 * silently missed accented queries.
 *
 * S018 -- fallo real reportado por Miguel Ángel (ArtistScreen, AC/DC):
 * el álbum local "The Razor's Edge" no se reconocía como el mismo que
 * el release-group de MusicBrainz "The Razors Edge" (sin apóstrofo),
 * dando "0 álbumes completos" pese a tener el álbum entero descargado.
 * Ampliado para quitar también puntuación (apóstrofos rectos/curvos,
 * paréntesis, exclamaciones, dos puntos...), no solo acentos --
 * mismo principio que el fix de acentos: dos formas de escribir el
 * mismo título no deberían fallar el match por un carácter que ni
 * siquiera se pronuncia.
 * ---
 * Normaliza una cadena para búsqueda/filtrado insensible a acentos y
 * mayúsculas, compartida por todos los campos de filtro de la app
 * (Biblioteca, Playlists) y por todo cruce por nombre de H12
 * (ArtistScreen/AlbumScreen contra lo descargado localmente). Elimina
 * diacríticos (descomposición NFD + borrado de marcas combinantes, la
 * misma técnica que ya usa `sortLetterFor` en `LibraryViewModel` para
 * agrupar artistas por letra) para que escribir "angel" encuentre
 * "Ángel" y viceversa -- un hueco de usabilidad real en el filtro
 * original, que solo hacía `trim().lowercase()` y no encontraba
 * búsquedas con acento.
 *
 * S018 -- fallo real reportado por Miguel Ángel (ArtistScreen, AC/DC):
 * el álbum local "The Razor's Edge" no se reconocía como el mismo que
 * el release-group de MusicBrainz "The Razors Edge" (sin apóstrofo),
 * dando "0 álbumes completos" pese a tener el álbum entero descargado.
 * Ampliado para quitar también puntuación (apóstrofos rectos/curvos,
 * paréntesis, exclamaciones, dos puntos...), no solo acentos -- mismo
 * principio que el fix de acentos: dos formas de escribir el mismo
 * título no deberían fallar el match por un carácter que ni siquiera
 * se pronuncia.
 */
object SearchNormalizer {

    fun normalize(raw: String): String {
        val trimmedLower = raw.trim().lowercase()
        val withoutAccents = Normalizer.normalize(trimmedLower, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        // \p{L} = letras (cualquier alfabeto), \p{Nd} = dígitos decimales
        // -- todo lo que no sea eso ni espacio se quita (apóstrofos,
        // paréntesis, exclamaciones, dos puntos, guiones...). Colapsa
        // los espacios sobrantes que deja huecos como "(Live)" -> " Live".
        return withoutAccents
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Normalizes an artist name for H12 matching/routing (directory
     * pages, favorites, disambiguation cache key) -- NEVER for display,
     * where the canonical MusicBrainz name is always shown. Strips a
     * leading "The " (case-insensitive) so variants of the same artist
     * ("The Chemical Brothers" / "Chemical Brothers") collapse to one
     * page, then reuses `normalize()` for the existing accent/case
     * folding instead of duplicating that logic. Explicit, confirmed
     * scope: only the English article "The" -- Spanish articles ("Los",
     * "La", etc.) are intentionally left untouched, no real case found
     * yet (ver DOCS/ANNEX_H12.md, S017).
     * ---
     * Normaliza un nombre de artista para matching/routing de H12
     * (páginas de directorio, favoritos, clave de caché de
     * desambiguación) -- NUNCA para mostrar en pantalla, donde siempre
     * se usa el nombre canónico de MusicBrainz. Quita un "The " inicial
     * (case-insensitive) para que variantes del mismo artista colapsen
     * a una sola página, y reutiliza `normalize()` para el plegado de
     * acentos/mayúsculas ya existente en vez de duplicar esa lógica.
     * Alcance explícito y confirmado: solo el artículo inglés "The" --
     * los artículos en español ("Los", "La", etc.) se dejan
     * intencionadamente sin tocar, sin caso real detectado todavía (ver
     * DOCS/ANNEX_H12.md, S017).
     */
    /**
     * S025 -- "Apellido, Nombre" -> "Nombre Apellido".
     *
     * El catálogo de H05 guarda a los compositores como los guarda una
     * ficha de disco: `Beethoven, Ludwig van`. MusicBrainz los tiene al
     * derecho, `Ludwig van Beethoven`, así que la Radio preguntaba por
     * un nombre que no existe. En el log de S025 sale cuatro veces:
     *
     *   resolveAnchor('Beethoven, Ludwig van') -- MusicBrainz no
     *     encontró NINGÚN artista con ese nombre (searchArtists vacío)
     *
     * La vuelta solo se da cuando el nombre tiene UNA coma y lo que va
     * detrás parece un nombre de pila: de una a tres palabras, sin
     * `&` ni ` and `, y sin empezar por `the `. Esas tres exclusiones
     * no son teóricas, salen del propio diccionario y de casos reales:
     * `Earth, Wind & Fire`, `Peter, Paul and Mary` y `Tyler, The
     * Creator` tienen coma y NO deben voltearse.
     * ---
     * S025 -- turns "Surname, Forename" into "Forename Surname", which
     * is how MusicBrainz stores people. Only applies with a single
     * comma and a forename-shaped tail, so band names with commas
     * (Earth, Wind & Fire) are left alone.
     */
    fun reorderCommaName(name: String): String {
        val parts = name.split(",")
        if (parts.size != 2) return name
        val surname = parts[0].trim()
        val forename = parts[1].trim()
        if (surname.isBlank() || forename.isBlank()) return name
        val lower = forename.lowercase()
        if (lower.startsWith("the ")) return name
        if (forename.contains("&") || lower.contains(" and ")) return name
        if (forename.split(" ").filter { it.isNotBlank() }.size > 3) return name
        return "$forename $surname"
    }

    fun normalizeArtistName(artist: String): String {
        val trimmed = reorderCommaName(artist.trim()).trim()
        val withoutLeadingThe = if (trimmed.startsWith("the ", ignoreCase = true)) {
            trimmed.substring(4)
        } else {
            trimmed
        }
        return normalize(withoutLeadingThe)
    }

    /**
     * S025 -- quita TODOS los espacios de una cadena ya normalizada.
     *
     * `normalize()` BORRA la puntuación en vez de sustituirla por
     * espacio, así que 'Lobo-Hombre' queda "lobohombre" y 'Lobo Hombre'
     * queda "lobo hombre": la misma canción, dos cadenas distintas. Es
     * el mismo agujero que S023 encontró con 'M-Clan' / 'M Clan' y
     * resolvió localmente en `RadioRepository.pickAnchorArtist()` con
     * una función `tight()` privada. Aquí se sube al normalizador
     * compartido porque la clave de canción lo necesita igual.
     * ---
     * S025 -- strips every space from an already-normalized string, so
     * that 'Lobo-Hombre' and 'Lobo Hombre' collapse to one key.
     */
    fun tight(value: String): String = value.replace(" ", "")

    /**
     * S025 -- clave estable de una CANCIÓN, pensada para deduplicar.
     *
     * Fallo reportado por Miguel Ángel: *"un tema JAMÁS debe volver a
     * escucharse"*, y sonaba tres veces en la misma sesión. Del log:
     *
     *   10:31  resolveYoutubeCandidate(query='La Unión')
     *            -> añadido: 'LA UNIÓN - Lobo Hombre en París (1984)'
     *   11:16  resolveFinalFallback -> tema sin estrenar:
     *            'La Unión' - 'Lobo-Hombre en París'
     *   11:26  resolveFinalFallback -> tema sin estrenar:
     *            'La Unión' - 'Lobo hombre en París'
     *
     * Las tres veces el sistema la daba por "sin estrenar", y tenía
     * razón según su propia clave: el tema se REGISTRA con el título
     * del vídeo de YouTube y se COMPRUEBA con el título del
     * diccionario, que nunca son la misma cadena. Además `normalize()`
     * borra el guion sin dejar espacio, así que 'Lobo-Hombre' y 'Lobo
     * Hombre' tampoco casaban entre sí.
     *
     * Se reduce el título a su esqueleto, en este orden:
     *   1. Fuera lo que va entre paréntesis o corchetes -- ahí viven
     *      los años y las coletillas: "(1984)", "(con letra)", "[HD]".
     *   2. Se parte por " - ". Si el primer trozo ES el artista, se
     *      tira: el título de YouTube casi siempre repite el nombre
     *      del grupo delante ("LA UNIÓN - Lobo Hombre en París").
     *   3. Se tiran los trozos finales que sean solo coletilla
     *      ("Clip Oficial Alta Calidad HQ", "Videoclip Remasterizado").
     *   4. Fuera los años sueltos que hayan quedado.
     *   5. `normalize()` + `tight()`: sin acentos, sin puntuación, sin
     *      mayúsculas y sin espacios.
     *
     * Los tres títulos de arriba dan "lobohombreenparis". El mismo
     * tema en otro vídeo, con otro subtítulo o con el guion puesto de
     * otra manera, también.
     *
     * ALCANCE DELIBERADO: una versión en directo, una remasterizada y
     * la de estudio colapsan a la misma clave. Es lo correcto bajo la
     * regla de Miguel Ángel -- es la misma canción, y no quiere oírla
     * dos veces. Si algún día se quisiera distinguirlas, hay que
     * hacerlo aquí y no en las llamadas.
     * ---
     * S025 -- stable key for a SONG, for deduplication. Strips
     * bracketed segments, a leading artist prefix, trailing decoration
     * segments and stray years, then folds accents, punctuation, case
     * and spaces. Live/remastered/studio versions deliberately collapse
     * to the same key: it's the same song.
     */
    fun songTitleKey(title: String, artist: String? = null): String {
        val withoutBrackets = title
            .replace(Regex("\\([^()]*\\)"), " ")
            .replace(Regex("\\[[^\\[\\]]*\\]"), " ")
        val parts = withoutBrackets
            .split(" - ", " – ", " — ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()
        val artistKey = artist?.let { tight(normalizeArtistName(it)) }.orEmpty()
        if (artistKey.isNotEmpty()) {
            // El nombre del grupo puede ir delante ("LA UNIÓN - Lobo
            // Hombre en París") o detrás ("santa lucia - miguel rios").
            // Los dos casos salen del log real de Miguel Ángel.
            if (parts.size > 1 && tight(normalizeArtistName(parts.first())) == artistKey) {
                parts.removeAt(0)
            }
            if (parts.size > 1 && tight(normalizeArtistName(parts.last())) == artistKey) {
                parts.removeAt(parts.size - 1)
            }
        }
        while (parts.size > 1 && isOnlyDecoration(parts.last())) {
            parts.removeAt(parts.size - 1)
        }
        val skeleton = parts.joinToString(" ")
            .replace(Regex("\\b(19|20)\\d{2}\\b"), " ")
        // Coletillas que van PEGADAS al título, sin separador propio:
        // "ESTAMOS DESESPERADOS HQ", "...de la carretera HD". Se podan
        // palabra a palabra desde el final, y solo las inequívocas
        // (`STRONG_DECORATION`): las de relleno como "de" o "la" no se
        // tocan aquí, o "Devuélveme a mi Chica" perdería el final.
        val words = normalize(skeleton).split(" ").filter { it.isNotBlank() }.toMutableList()
        while (words.size > 1 && words.last() in STRONG_DECORATION) {
            words.removeAt(words.size - 1)
        }
        val key = words.joinToString("")
        // Red de seguridad: si de tanto podar no queda nada (un título
        // que fuera SOLO coletillas y año), vale más una clave sucia
        // que una clave vacía -- dos temas distintos con clave vacía
        // colapsarían en uno y se perdería un tema para siempre.
        return key.ifBlank { tight(normalize(title)) }
    }

    private fun isOnlyDecoration(segment: String): Boolean {
        val words = normalize(segment).split(" ").filter { it.isNotBlank() }
        return words.isNotEmpty() && words.all { it in STRONG_DECORATION || it in FILLER_WORDS }
    }

    /**
     * S025 -- coletillas inequívocas. Se podan también sueltas al final
     * del título, porque en YouTube van pegadas sin separador propio.
     */
    private val STRONG_DECORATION = setOf(
        "hq", "hd", "uhd", "4k", "8k", "1080p", "720p", "hifi", "full",
        "video", "videoclip", "clip", "audio", "oficial", "official", "officiel",
        "remastered", "remasterizado", "remasterizada", "remaster",
        "karaoke", "instrumental", "cover", "live", "directo", "vivo",
        "lyrics", "lyric", "subtitulado", "subtitulos",
    )

    /**
     * S025 -- palabras de relleno. NUNCA se podan sueltas: solo sirven
     * para decidir que un trozo ENTERO del título ("Clip Oficial Alta
     * Calidad HQ") es coletilla y se puede tirar completo. Así una
     * canción que se llame de verdad "Directo al corazón" conserva su
     * nombre.
     */
    private val FILLER_WORDS = setOf(
        "alta", "calidad", "sonido", "musica", "music", "version",
        "original", "originale", "letra", "letras", "concierto",
        "con", "en", "el", "la", "los", "las", "y", "de", "del", "al",
    )
}

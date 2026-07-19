package com.miguelaetxio.mimoo.util

import java.text.Normalizer

/**
 * Normalizes a string for accent-insensitive, case-insensitive text
 * search/filtering, shared by every filter field in the app
 * (Biblioteca, Playlists). Strips diacritics (NFD decomposition +
 * removal of combining marks, same technique already used by
 * `sortLetterFor` in `LibraryViewModel` for the artist-letter
 * grouping) so that typing "angel" matches "Ángel" and vice versa --
 * a real usability gap in the original filter, which only did
 * `trim().lowercase()` and silently missed accented queries.
 * ---
 * Normaliza una cadena para búsqueda/filtrado insensible a acentos y
 * mayúsculas, compartida por todos los campos de filtro de la app
 * (Biblioteca, Playlists). Elimina diacríticos (descomposición NFD +
 * borrado de marcas combinantes, la misma técnica que ya usa
 * `sortLetterFor` en `LibraryViewModel` para agrupar artistas por
 * letra) para que escribir "angel" encuentre "Ángel" y viceversa --
 * un hueco de usabilidad real en el filtro original, que solo hacía
 * `trim().lowercase()` y no encontraba búsquedas con acento.
 */
object SearchNormalizer {

    fun normalize(raw: String): String {
        val trimmedLower = raw.trim().lowercase()
        return Normalizer.normalize(trimmedLower, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
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
    fun normalizeArtistName(artist: String): String {
        val trimmed = artist.trim()
        val withoutLeadingThe = if (trimmed.startsWith("the ", ignoreCase = true)) {
            trimmed.substring(4)
        } else {
            trimmed
        }
        return normalize(withoutLeadingThe)
    }
}

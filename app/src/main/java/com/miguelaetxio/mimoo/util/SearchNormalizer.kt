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
}

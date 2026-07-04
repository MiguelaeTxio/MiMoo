package com.miguelaetxio.mimoo.util

/**
 * Limpia etiquetas que los subidores de YouTube añaden al título de
 * un vídeo/playlist pero que NUNCA forman parte del nombre real del
 * álbum o la canción -- p.ej. "Moon Safari [Full Album]" en YouTube
 * no significa que el álbum se llame así, "Full Album" es una
 * etiqueta añadida por quien subió el vídeo. Petición explícita de
 * Miguel Ángel (2026-07-04): Biblioteca solo debe mostrar metadatos
 * reales, nunca el nombre de archivo/título de YouTube tal cual venía.
 *
 * Enfoque deliberadamente conservador (lista cerrada de frases
 * conocidas, no un regex genérico "quita cualquier paréntesis"):
 * paréntesis como "(Remastered 2011)" SÍ forman parte de un título
 * real y no deben tocarse. Solo se elimina lo que coincide, ignorando
 * mayúsculas, con una frase de la lista.
 * ---
 * Cleans up tags that YouTube uploaders add to a video/playlist title
 * but that are NEVER part of the real album or song name -- e.g.
 * "Moon Safari [Full Album]" on YouTube doesn't mean the album is
 * actually named that, "Full Album" is a tag added by whoever
 * uploaded the video. Explicit request from Miguel Ángel (2026-07-04):
 * Biblioteca should only show real metadata, never the YouTube
 * title/filename as-is.
 *
 * Deliberately conservative approach (a closed list of known phrases,
 * not a generic "strip any parentheses" regex): parentheses like
 * "(Remastered 2011)" ARE part of a real title and must not be
 * touched. Only text that case-insensitively matches a phrase in the
 * list gets removed.
 */
object YoutubeTitleCleaner {

    private val JUNK_PHRASES = listOf(
        "full album", "álbum completo", "album completo",
        "official music video", "official lyric video", "official video",
        "official audio", "official visualizer", "lyric video", "lyrics",
        "video oficial", "audio oficial", "video lírico",
        "hq audio", "hd audio",
    )

    /**
     * Cleans one title/album string. Removes (...) / [...] groups
     * whose inner text matches a known junk phrase exactly, then
     * removes a trailing " - <junk phrase>" style suffix without
     * brackets, then trims stray leftover dashes/whitespace.
     * ---
     * Limpia una cadena de título/álbum. Elimina grupos (...) / [...]
     * cuyo texto interior coincide exactamente con una frase conocida,
     * luego elimina un sufijo final " - <frase>" sin corchetes, y por
     * último recorta guiones/espacios sueltos que hayan quedado.
     */
    fun clean(raw: String): String {
        var cleaned = raw

        cleaned = Regex("[\\(\\[]([^\\)\\]]*)[\\)\\]]").replace(cleaned) { match ->
            val inner = match.groupValues[1].trim().lowercase()
            if (inner in JUNK_PHRASES) "" else match.value
        }

        for (phrase in JUNK_PHRASES) {
            cleaned = Regex("(?i)[-–—]\\s*${Regex.escape(phrase)}\\s*$")
                .replace(cleaned, "")
        }

        return cleaned
            .trim()
            .trim('-', '–', '—', ' ')
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .ifBlank { raw.trim() }
    }
}

package com.miguelaetxio.mimoo.util

/** Una línea de letra sincronizada, con su instante de inicio en milisegundos. */
data class LrcLine(val timeMs: Long, val text: String)

/**
 * Parser de formato LRC (H17, S031, bloque 2) -- `syncedLyrics` de
 * lrclib.net llega como una única cadena con saltos de línea, cada
 * línea con la forma `[mm:ss.xx] texto` (a veces `[mm:ss.xxx]` con
 * tres decimales, o varias marcas de tiempo seguidas para la misma
 * línea -- ambos casos cubiertos por el regex). Líneas sin marca de
 * tiempo reconocible (metadatos LRC como `[ar:...]`/`[ti:...]`, o
 * líneas vacías) se descartan. El resultado se ordena por tiempo --
 * lrclib.net ya lo entrega ordenado, pero no hay garantía documentada
 * de que sea así en el 100% de las contribuciones de la comunidad.
 * ---
 * LRC format parser (H17, S031). `syncedLyrics` from lrclib.net comes
 * as a single string with newlines, each line shaped like
 * `[mm:ss.xx] text`. Lines with no recognizable timestamp are
 * dropped. Result is sorted by time.
 */
object LrcParser {
    private val LINE_REGEX = Regex("""\[(\d+):(\d+)(?:\.(\d+))?]""")

    fun parse(lrc: String): List<LrcLine> {
        val result = mutableListOf<LrcLine>()
        for (rawLine in lrc.lineSequence()) {
            val matches = LINE_REGEX.findAll(rawLine).toList()
            if (matches.isEmpty()) continue
            val text = rawLine.substring(matches.last().range.last + 1).trim()
            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                val fraction = match.groupValues[3]
                val fractionMs = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                result.add(LrcLine(timeMs = (minutes * 60 + seconds) * 1000 + fractionMs, text = text))
            }
        }
        return result.sortedBy { it.timeMs }
    }
}

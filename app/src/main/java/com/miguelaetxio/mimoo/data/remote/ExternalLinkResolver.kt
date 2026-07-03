package com.miguelaetxio.mimoo.data.remote

import com.chaquo.python.Python
import com.google.gson.Gson
import com.miguelaetxio.mimoo.data.remote.dto.ExternalLinkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a pasted YouTube/YouTube Music link (single video or
 * playlist/album) via the embedded yt-dlp Python module (PASO 6f,
 * H05) — same Chaquopy calling pattern as StreamResolver. Runs on
 * Dispatchers.IO since yt-dlp's extraction is blocking and
 * network-bound.
 *
 * Deliberately bypasses the YouTube Data API entirely: the user
 * already found the content themselves on YouTube and pastes the
 * link ("aquí la búsqueda es externa" — Miguel Ángel, 2026-07-02),
 * so there is nothing to search — yt-dlp reads the link directly,
 * at zero quota cost.
 * ---
 * Resuelve un enlace de YouTube/YouTube Music pegado (vídeo suelto o
 * playlist/álbum) vía el módulo Python embebido yt-dlp (PASO 6f,
 * H05) — mismo patrón de invocación Chaquopy que StreamResolver. Se
 * ejecuta en Dispatchers.IO porque la extracción de yt-dlp es
 * bloqueante y depende de red.
 *
 * Deliberadamente evita la YouTube Data API por completo: el usuario
 * ya encontró el contenido él mismo en YouTube y pega el enlace
 * ("aquí la búsqueda es externa" — Miguel Ángel, 2026-07-02), así que
 * no hay nada que buscar — yt-dlp lee el enlace directamente, con
 * coste de cuota cero.
 */
@Singleton
class ExternalLinkResolver @Inject constructor() {

    private val gson = Gson()

    /**
     * Raises RuntimeException (wrapping the Python-side RuntimeError
     * message via Chaquopy) if the link cannot be resolved at all —
     * private/deleted playlist, malformed URL, etc.
     * ---
     * Lanza RuntimeException (envolviendo el mensaje de RuntimeError
     * del lado Python vía Chaquopy) si el enlace no se puede resolver
     * en absoluto — playlist privada/borrada, URL mal formada, etc.
     */
    suspend fun resolveLink(url: String): ExternalLinkResult =
        withContext(Dispatchers.IO) {
            val py = Python.getInstance()
            val module = py.getModule("link_resolver")
            val json = module.callAttr("resolve_youtube_link", url).toString()
            gson.fromJson(json, ExternalLinkResult::class.java)
        }
}

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

    /**
     * Búsqueda por texto libre gratuita, a coste de cuota CERO --
     * petición explícita de Miguel Ángel (2026-07-04): "quiero poder
     * realizar las búsquedas de forma gratuita... prefiero tener que
     * escribir los nombres a gastar mi cuota". Usa la sintaxis
     * pseudo-URL "ytsearchN:query" de yt-dlp, que resolve_youtube_link()
     * ya sabe tratar exactamente igual que una playlist real (mismos
     * "entries", mismo extract_flat) -- no hace falta ninguna función
     * Python nueva. Sustituye a YouTubeApiService.search() (search.list,
     * 100 unidades/llamada sobre un pool diario de 10.000) en la
     * pantalla de Búsqueda.
     * ---
     * Free-text search at ZERO quota cost -- explicit request from
     * Miguel Ángel (2026-07-04): "I want to be able to search for
     * free... I'd rather type the names than spend my quota". Uses
     * yt-dlp's "ytsearchN:query" pseudo-URL syntax, which
     * resolve_youtube_link() already knows how to handle exactly like
     * a real playlist (same "entries", same extract_flat) -- no new
     * Python function needed. Replaces YouTubeApiService.search()
     * (search.list, 100 units/call over a 10,000/day pool) in the
     * Search screen.
     */
    suspend fun searchYoutube(query: String, limit: Int = 15): ExternalLinkResult =
        resolveLink("ytsearch$limit:$query")
}

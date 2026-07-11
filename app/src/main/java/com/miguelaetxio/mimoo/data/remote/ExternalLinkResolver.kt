package com.miguelaetxio.mimoo.data.remote

import com.chaquo.python.Python
import com.google.gson.Gson
import com.miguelaetxio.mimoo.data.remote.dto.ExternalLinkResult
import com.miguelaetxio.mimoo.data.remote.dto.SearchTypeResult
import com.miguelaetxio.mimoo.data.remote.dto.SearchTypeResultsWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * H08 PARTE 1 (S009) -- tipo de resultado por el que filtrar una
 * búsqueda de YouTube, usando el mismo token "sp" que el propio
 * selector "Filtros de búsqueda" de la app de YouTube. Valores
 * verificados contra una captura real de esa UI y contra
 * documentación independiente de terceros (no una única fuente) --
 * YouTube no tiene un tipo "Podcast"/"Audiolibro" dedicado, así que
 * no existe un tercer valor para eso.
 * ---
 * H08 PARTE 1 (S009) -- result type to filter a YouTube search by,
 * using the same "sp" token as YouTube's own "Filtros de búsqueda"
 * selector. Values verified against a real screenshot of that UI and
 * against independent third-party documentation (not a single
 * source) -- YouTube has no dedicated "Podcast"/"Audiobook" type, so
 * there is no third value for that.
 */
enum class SearchResultType(val spFilter: String) {
    PLAYLIST("EgIQAw%3D%3D"),
    CHANNEL("EgIQAg%3D%3D"),
}

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

    /**
     * H08 PARTE 1 (S009) -- busca listas o canales por texto libre,
     * a coste de cuota CERO (mismo mecanismo de scraping que
     * searchYoutube(), requisito explícito de Miguel Ángel). Cada
     * resultado trae su propia url resoluble, que se abre después
     * con resolveLink() exactamente igual que un enlace pegado a
     * mano en "Importar enlace" -- no hace falta ninguna lógica de
     * apertura nueva.
     *
     * Aviso de riesgo, no oculto: la búsqueda filtrada por tipo es
     * una zona menos estable de yt-dlp que la búsqueda normal de
     * vídeos (historial de roturas documentado en su propio
     * tracker); una lista vacía de resultados es un desenlace
     * esperable a mostrar con gracia en la UI, no necesariamente un
     * error.
     * ---
     * H08 PARTE 1 (S009) -- searches playlists or channels by free
     * text, at ZERO quota cost (same scraping mechanism as
     * searchYoutube(), Miguel Ángel's explicit requirement). Each
     * result carries its own resolvable url, later opened with
     * resolveLink() exactly like a link pasted by hand in "Importar
     * enlace" -- no new opening logic needed.
     *
     * Risk disclosure, not hidden: type-filtered search is a less
     * stable area of yt-dlp than plain video search (documented
     * history of breakage in its own tracker); an empty result list
     * is an expected outcome to show gracefully in the UI, not
     * necessarily an error.
     */
    suspend fun searchByType(
        query: String,
        type: SearchResultType,
        limit: Int = 15,
    ): List<SearchTypeResult> =
        withContext(Dispatchers.IO) {
            val py = Python.getInstance()
            val module = py.getModule("link_resolver")
            val json = module.callAttr(
                "search_by_type", query, type.spFilter, limit,
            ).toString()
            gson.fromJson(json, SearchTypeResultsWrapper::class.java).results
        }
}

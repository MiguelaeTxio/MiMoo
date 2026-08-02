package com.miguelaetxio.mimoo.data.playback

import com.chaquo.python.Python
import com.miguelaetxio.mimoo.data.download.CookiesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the direct audio stream URL of a YouTube video using the
 * embedded yt-dlp Python module (via Chaquopy), without downloading
 * the file. Runs on Dispatchers.IO since yt-dlp's extraction call is
 * blocking and network-bound.
 * ---
 * Resuelve la URL directa de stream de audio de un video de YouTube
 * usando el módulo Python embebido yt-dlp (vía Chaquopy), sin
 * descargar el archivo. Se ejecuta en Dispatchers.IO porque la
 * llamada de extracción de yt-dlp es bloqueante y depende de red.
 */
@Singleton
class StreamResolver @Inject constructor(
    private val cookiesManager: CookiesManager,
) {

    /**
     * S027 -- bug real reportado por Miguel Ángel con captura: "Sign
     * in to confirm you're not a bot" al darle a Reproducir. Esta
     * función no llevaba cabecera User-Agent ni cookies, a diferencia
     * de la descarga (`downloader.py`), que las tiene desde el
     * 2026-07-24 por exactamente este motivo -- mismo bloqueo de
     * YouTube, sin el mismo arreglo aplicado en streaming.
     */
    suspend fun resolveAudioStreamUrl(youtubeUrl: String): String =
        withContext(Dispatchers.IO) {
            val py = Python.getInstance()
            val resolverModule = py.getModule("resolver")
            resolverModule
                .callAttr("resolve_audio_stream_url", youtubeUrl, cookiesManager.cookiesFilePathOrNull())
                .toString()
        }
}

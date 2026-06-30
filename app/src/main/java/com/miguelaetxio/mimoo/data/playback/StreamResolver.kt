package com.miguelaetxio.mimoo.data.playback

import com.chaquo.python.Python
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
class StreamResolver @Inject constructor() {

    suspend fun resolveAudioStreamUrl(youtubeUrl: String): String =
        withContext(Dispatchers.IO) {
            val py = Python.getInstance()
            val resolverModule = py.getModule("resolver")
            resolverModule
                .callAttr("resolve_audio_stream_url", youtubeUrl)
                .toString()
        }
}

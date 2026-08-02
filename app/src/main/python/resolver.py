# resolver.py
# Modulo Python embebido via Chaquopy, invocado desde Kotlin para
# resolver la URL de streaming de audio de un video de YouTube sin
# descargarlo a disco.
# ---
# Python module embedded via Chaquopy, called from Kotlin to resolve
# the audio streaming URL of a YouTube video without downloading it.

import os

import yt_dlp


def resolve_audio_stream_url(youtube_url: str, cookies_path: str = None) -> str:
    """
    Returns the direct HTTP URL of the best audio-only stream for a
    given YouTube video, without downloading any file to disk.
    Equivalent to: yt-dlp -f bestaudio -g {youtube_url}

    S027 -- antes esta funcion no llevaba ni cabecera User-Agent ni
    cookies, a diferencia de downloader.py (las descargas), que las
    tiene desde el 2026-07-24 por exactamente este motivo. Reportado
    por Miguel Angel con captura real: "Sign in to confirm you're not
    a bot" al darle a Reproducir (streaming), no al descargar -- el
    mismo tipo de bloqueo de YouTube que ya se resolvio para
    descargas, sin aplicar aqui. `cookies_path` lo gestiona
    CookiesManager.kt -- nunca se genera en este modulo.
    ---
    Devuelve la URL HTTP directa del mejor stream de solo-audio para
    un video de YouTube dado, sin descargar ningun archivo a disco.
    Equivalente a: yt-dlp -f bestaudio -g {youtube_url}
    """
    options = {
        "format": "bestaudio",
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        # Mismo motivo que en downloader.py: YouTube rechaza
        # peticiones sin un User-Agent de navegador real.
        "http_headers": {
            "User-Agent": (
                "Mozilla/5.0 (Linux; Android 10; K) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/124.0.0.0 Mobile Safari/537.36"
            ),
        },
    }
    if cookies_path and os.path.isfile(cookies_path):
        options["cookiefile"] = cookies_path
    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(youtube_url, download=False)
        url = info.get("url")
        if not url:
            raise RuntimeError(
                "yt-dlp no devolvio una URL de stream para: " + youtube_url
            )
        return url

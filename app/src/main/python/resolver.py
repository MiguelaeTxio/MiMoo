# resolver.py
# Modulo Python embebido via Chaquopy, invocado desde Kotlin para
# resolver la URL de streaming de audio de un video de YouTube sin
# descargarlo a disco.
# ---
# Python module embedded via Chaquopy, called from Kotlin to resolve
# the audio streaming URL of a YouTube video without downloading it.

import yt_dlp


def resolve_audio_stream_url(youtube_url: str) -> str:
    """
    Returns the direct HTTP URL of the best audio-only stream for a
    given YouTube video, without downloading any file to disk.
    Equivalent to: yt-dlp -f bestaudio -g {youtube_url}
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
    }
    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(youtube_url, download=False)
        url = info.get("url")
        if not url:
            raise RuntimeError(
                "yt-dlp no devolvio una URL de stream para: " + youtube_url
            )
        return url

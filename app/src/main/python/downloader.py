"""
downloader.py — yt-dlp audio download module for MiMoo (Hito 02).

Invoked from DownloadWorker.kt via Chaquopy. Downloads the best
audio stream from a YouTube URL and converts it to Opus using
yt-dlp's built-in FFmpeg post-processor.

---
Módulo de descarga de audio yt-dlp para MiMoo (Hito 02).

Invocado desde DownloadWorker.kt vía Chaquopy. Descarga el mejor
stream de audio de una URL de YouTube y lo convierte a Opus usando
el post-procesador FFmpeg integrado de yt-dlp.
"""

import yt_dlp


def download_audio(youtube_url: str, output_path: str) -> bool:
    """
    Download the best audio stream from youtube_url and write it to
    output_path as an Opus file. Returns True on success.
    Raises yt_dlp.utils.DownloadError on failure.
    ---
    Descarga el mejor stream de audio de youtube_url y lo escribe en
    output_path como archivo Opus. Devuelve True si tiene éxito.
    Lanza yt_dlp.utils.DownloadError en caso de fallo.

    Args:
        youtube_url: Full YouTube URL (e.g. https://youtu.be/XXXXXXXXXXX).
        output_path: Absolute path for the output file (without extension;
                     yt-dlp appends the container extension automatically).

    Returns:
        True if download and conversion succeeded.
    """
    ydl_opts = {
        "format": "bestaudio/best",
        "outtmpl": output_path,
        "postprocessors": [
            {
                "key": "FFmpegExtractAudio",
                "preferredcodec": "opus",
                "preferredquality": "0",
            }
        ],
        "quiet": True,
        "no_warnings": True,
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        ydl.download([youtube_url])
    return True

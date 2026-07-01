"""
downloader.py — yt-dlp audio download module for MiMoo (Hito 02).

Downloads the best audio stream from a YouTube URL to a temporary
local path as an Opus file. The caller (DownloadWorker.kt) is
responsible for copying the result to the final SAF destination and
deleting the temporary file.

yt-dlp cannot write directly to Android SAF Uris since they are not
filesystem paths. The two-step pattern (temp file -> SAF copy) avoids
any storage permission requirement.

---
Descarga el mejor stream de audio de una URL de YouTube a una ruta
temporal local como archivo Opus. El llamante (DownloadWorker.kt) es
responsable de copiar el resultado al destino SAF final y borrar el
archivo temporal.

yt-dlp no puede escribir directamente en Uris SAF de Android ya que
no son rutas del sistema de archivos. El patron de dos pasos
(archivo temporal -> copia SAF) evita cualquier requisito de permiso
de almacenamiento.
"""

import yt_dlp


def download_audio(youtube_url: str, output_path: str) -> bool:
    """
    Download the best audio stream and convert it to Opus at output_path.
    output_path must be a writable filesystem path (e.g. app cache dir).
    Returns True on success; raises yt_dlp.utils.DownloadError on failure.
    ---
    Descarga el mejor stream de audio y lo convierte a Opus en output_path.
    output_path debe ser una ruta de sistema de archivos con permiso de
    escritura (p.ej. directorio de cache de la app).
    Devuelve True en exito; lanza yt_dlp.utils.DownloadError en fallo.

    Args:
        youtube_url: Full YouTube URL (e.g. https://youtu.be/XXXXXXXXXXX).
        output_path: Absolute filesystem path for the output file
                     (without .opus extension; yt-dlp appends it).

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


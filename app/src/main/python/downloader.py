"""
downloader.py — yt-dlp audio download module for MiMoo (Hito 02).

Downloads the best audio stream from a YouTube URL to a temporary
local path as an Opus file, using the bundled ffmpeg binary so no
external ffmpeg installation is required.

---
Descarga el mejor stream de audio de una URL de YouTube a una ruta
temporal local como archivo Opus, usando el binario ffmpeg incluido
en el APK para no necesitar instalacion externa de ffmpeg.
"""

import yt_dlp


def download_audio(
    youtube_url: str,
    output_path: str,
    ffmpeg_location: str,
) -> bool:
    """
    Download the best audio stream and convert it to Opus at output_path.
    output_path must be a writable filesystem path (e.g. app cache dir).
    ffmpeg_location must be the directory containing the ffmpeg binary,
    or the full path to the ffmpeg binary itself.
    Returns True on success; raises on failure.
    ---
    Descarga el mejor stream de audio y lo convierte a Opus en output_path.
    output_path debe ser una ruta de sistema de archivos con permiso de
    escritura (p.ej. directorio de cache de la app).
    ffmpeg_location debe ser el directorio que contiene el binario ffmpeg,
    o la ruta completa al binario ffmpeg.
    Devuelve True en exito; lanza excepcion en fallo.

    Args:
        youtube_url:     Full YouTube URL (e.g. https://youtu.be/XXXXXXXXXXX).
        output_path:     Absolute filesystem path WITHOUT .opus extension
                         (yt-dlp appends it automatically).
        ffmpeg_location: Path to ffmpeg binary or its parent directory.

    Returns:
        True if download and conversion succeeded.
    """
    import os
    # yt-dlp accepts either the binary path or its parent directory.
    # If we receive the full path, extract the directory.
    # yt-dlp acepta tanto la ruta al binario como su directorio padre.
    # Si recibimos la ruta completa, extraemos el directorio.
    if os.path.isfile(ffmpeg_location):
        ffmpeg_dir = os.path.dirname(ffmpeg_location)
        ffmpeg_bin = ffmpeg_location
    else:
        ffmpeg_dir = ffmpeg_location
        ffmpeg_bin = os.path.join(ffmpeg_location, "ffmpeg")

    # Ensure the binary is executable.
    # Asegurarse de que el binario tiene permisos de ejecucion.
    if os.path.isfile(ffmpeg_bin) and not os.access(ffmpeg_bin, os.X_OK):
        os.chmod(ffmpeg_bin, 0o755)

    ydl_opts = {
        "format": "bestaudio/best",
        "outtmpl": output_path,
        "ffmpeg_location": ffmpeg_dir,
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


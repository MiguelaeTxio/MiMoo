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
    progress_listener=None,
) -> bool:
    """
    Download the best audio stream and convert it to Opus at output_path.
    output_path must be a writable filesystem path (e.g. app cache dir).
    ffmpeg_location must be the directory containing the ffmpeg binary,
    or the full path to the ffmpeg binary itself.
    Returns True on success; raises on failure.

    progress_listener, if given, is a Kotlin/Java object (Chaquopy proxy)
    exposing onProgress(percent: Int) — see DownloadWorker.
    DownloadProgressListener. Called from yt-dlp's own progress_hooks,
    which only cover the raw audio download step, not the ffmpeg Opus
    postprocessing/mux that follows — percent is capped at 99 while
    status == "downloading" for that reason, real 100 is set by
    DownloadWorker itself once the whole doWork() (download + SAF copy)
    succeeds. Throttled here (min 2-point delta) so a slow connection
    doesn't flood Room with a write per callback — yt-dlp fires this
    hook many times per second.
    ---
    Descarga el mejor stream de audio y lo convierte a Opus en output_path.
    output_path debe ser una ruta de sistema de archivos con permiso de
    escritura (p.ej. directorio de cache de la app).
    ffmpeg_location debe ser el directorio que contiene el binario ffmpeg,
    o la ruta completa al binario ffmpeg.
    Devuelve True en exito; lanza excepcion en fallo.

    progress_listener, si se pasa, es un objeto Kotlin/Java (proxy de
    Chaquopy) que expone onProgress(percent: Int) — ver
    DownloadWorker.DownloadProgressListener. Se llama desde el propio
    progress_hooks de yt-dlp, que solo cubre el paso de descarga de
    audio en crudo, no el postproceso/mux a Opus de ffmpeg que viene
    despues — por eso el porcentaje se limita a 99 mientras
    status == "downloading"; el 100 real lo fija el propio
    DownloadWorker cuando todo doWork() (descarga + copia SAF) termina
    con exito. Con throttling aqui (delta minimo de 2 puntos) para que
    una conexion lenta no sature Room con una escritura por callback —
    yt-dlp dispara este hook muchas veces por segundo.

    Args:
        youtube_url:     Full YouTube URL (e.g. https://youtu.be/XXXXXXXXXXX).
        output_path:     Absolute filesystem path WITHOUT .opus extension
                         (yt-dlp appends it automatically).
        ffmpeg_location: Path to ffmpeg binary or its parent directory.
        progress_listener: Optional Kotlin DownloadProgressListener proxy.

    Returns:
        True if download and conversion succeeded.
    """
    import os

    last_reported = -1

    def _progress_hook(d):
        nonlocal last_reported
        if progress_listener is None or d.get("status") != "downloading":
            return
        total = d.get("total_bytes") or d.get("total_bytes_estimate")
        downloaded = d.get("downloaded_bytes")
        if not total or downloaded is None:
            return
        percent = min(99, int(downloaded * 100 / total))
        if percent - last_reported >= 2:
            last_reported = percent
            progress_listener.onProgress(percent)
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
        # YouTube rejects download requests without a real browser
        # User-Agent. Without this header, yt-dlp gets HTTP 403 when
        # trying to fetch the audio stream data on Android/Chaquopy.
        # ---
        # YouTube rechaza peticiones de descarga sin un User-Agent de
        # navegador real. Sin esta cabecera, yt-dlp recibe HTTP 403 al
        # intentar descargar el stream de audio en Android/Chaquopy.
        "http_headers": {
            "User-Agent": (
                "Mozilla/5.0 (Linux; Android 10; K) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/124.0.0.0 Mobile Safari/537.36"
            ),
        },
        "quiet": True,
        "no_warnings": True,
        "progress_hooks": [_progress_hook],
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        ydl.download([youtube_url])
    return True


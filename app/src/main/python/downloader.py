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

import os
import subprocess

import yt_dlp

# Clave de metadato Vorbis Comment propia de MiMoo, embebida en cada
# .opus descargado (H07 PARTE 0) para que el youtubeId sobreviva a
# una reinstalacion -- antes de este fix, el id solo vivia en la fila
# de Room, y LibraryReconciler no tenia forma de recuperarlo tras una
# perdida de la base de datos. Ver LibraryReconciler.kt (lectura) y
# DOCS/ANNEX_H07.md PARTE 0 para el diseno completo.
# ---
# MiMoo's own Vorbis Comment metadata key, embedded in every
# downloaded .opus (H07 PART 0) so the youtubeId survives a
# reinstall -- before this fix, the id only lived in the Room row,
# and LibraryReconciler had no way to recover it after a database
# loss. See LibraryReconciler.kt (read side) and DOCS/ANNEX_H07.md
# PART 0 for the full design.
MIMOO_YOUTUBE_ID_TAG = "MIMOO_YOUTUBE_ID"


def get_ytdlp_version() -> str:
    """
    Version real de yt-dlp empaquetada en este build (resuelta por pip
    en tiempo de compilacion, sin version fijada -- ver
    app/build.gradle.kts). Usado por DownloadWorker.kt para incluirla
    en debug_error.txt -- yt-dlp cambia con mucha frecuencia como
    responde YouTube a cada cliente, asi que saber la version exacta
    del fallo es diagnostico real, no solo curiosidad.
    ---
    Real yt-dlp version bundled in this build (resolved by pip at
    compile time, no pinned version -- see app/build.gradle.kts). Used
    by DownloadWorker.kt to include it in debug_error.txt -- yt-dlp
    changes very frequently how YouTube responds to each client, so
    knowing the exact version at the time of a failure is real
    diagnostic, not just curiosity.
    """
    return yt_dlp.version.__version__


def download_audio(
    youtube_url: str,
    output_path: str,
    ffmpeg_location: str,
    progress_listener=None,
    youtube_id: str = None,
    cookies_path: str = None,
) -> bool:
    """
    Download the best audio stream and convert it to Opus at output_path.
    output_path must be a writable filesystem path (e.g. app cache dir).
    ffmpeg_location must be the directory containing the ffmpeg binary,
    or the full path to the ffmpeg binary itself.
    Returns True on success; raises on failure.

    youtube_id, if given, is embedded as a custom Vorbis Comment tag
    (MIMOO_YOUTUBE_ID) in the resulting .opus file via a second,
    lossless remux pass (-c copy, no re-encode) after yt-dlp's own
    conversion. This is what lets LibraryReconciler recover the real
    id from disk alone if Room is ever lost (H07 PARTE 0) -- without
    it, the id only ever lived in the Room row. If omitted, no tag is
    written (behavior identical to before this fix).
    ---
    youtube_id, si se pasa, se embebe como un tag Vorbis Comment
    propio (MIMOO_YOUTUBE_ID) en el .opus resultante, via un segundo
    paso de remux sin perdidas (-c copy, sin recodificar) tras la
    conversion propia de yt-dlp. Esto es lo que permite a
    LibraryReconciler recuperar el id real solo desde disco si Room
    se pierde alguna vez (H07 PARTE 0) -- sin esto, el id solo vivia
    en la fila de Room. Si se omite, no se escribe ningun tag
    (comportamiento identico al de antes de este fix).

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
        youtube_id: Optional 11-char YouTube video id, embedded as a
                    custom metadata tag in the output file (see above).
        cookies_path: Optional absolute path to a Netscape-format
                    cookies.txt (see CookiesManager.kt), passed to
                    yt-dlp as ydl_opts["cookiefile"]. Required for
                    videos YouTube marks as age-restricted -- as of
                    2026, no player_client workaround bypasses that
                    without an authenticated, age-verified account.
                    If omitted or the file doesn't exist, no cookies
                    are used (behavior identical to before this fix;
                    non-restricted videos are unaffected either way).

    Returns:
        True if download and conversion succeeded.
    """
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

    # Fix real (2026-07-24, debug_error.txt de Miguel Angel): sin esto,
    # yt-dlp rechaza cualquier video marcado por YouTube como
    # restringido por edad con "Sign in to confirm your age". El
    # archivo lo gestiona CookiesManager.kt -- nunca se genera aqui.
    # ---
    # Real fix (2026-07-24, Miguel Angel's debug_error.txt): without
    # this, yt-dlp rejects any video YouTube marks as age-restricted
    # with "Sign in to confirm your age". The file is managed by
    # CookiesManager.kt -- never generated here.
    if cookies_path and os.path.isfile(cookies_path):
        ydl_opts["cookiefile"] = cookies_path

    # Fix real de REGRESION (2026-07-24, tercera vuelta): la version
    # anterior forzaba el cliente "web_creator" para CUALQUIER descarga
    # con cookies, no solo la restringida por edad -- reportado por
    # Miguel Angel con dos "alternativas" DISTINTAS (youtubeId
    # ynZZwmSAnHg y kz7S66l-Dbc) fallando con un error nuevo, "Please
    # sign in" generico, que nunca habia aparecido antes de ese fix.
    # web_creator exige un contexto de cuenta distinto (pensado para
    # YouTube Studio) que rompe la autenticacion normal para videos
    # SIN ninguna restriccion real. Ahora: primer intento SIEMPRE con
    # el comportamiento por defecto de yt-dlp (tv_downgraded,web con
    # cookies) -- identico al de antes de tocar nada por este tema.
    # Solo si ese intento falla con el mensaje EXACTO de restriccion
    # de edad, se reintenta UNA vez forzando web_creator -- nunca para
    # ningun otro tipo de error, para no repetir esta regresion.
    # ---
    # Real REGRESSION fix (2026-07-24, third round): the previous
    # version forced the "web_creator" client for ANY download with
    # cookies, not just the age-restricted one -- reported by Miguel
    # Angel with two DIFFERENT "alternatives" (youtubeId ynZZwmSAnHg
    # and kz7S66l-Dbc) failing with a new, generic "Please sign in"
    # error that had never appeared before that fix. web_creator
    # requires a different account context (meant for YouTube Studio)
    # that breaks normal authentication for videos with NO real
    # restriction. Now: first attempt ALWAYS uses yt-dlp's default
    # behavior (tv_downgraded,web with cookies) -- identical to before
    # this whole topic was touched. Only if that attempt fails with the
    # EXACT age-restriction message is it retried ONCE forcing
    # web_creator -- never for any other kind of error, to avoid
    # repeating this regression.
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([youtube_url])
    except yt_dlp.DownloadError as e:
        if "Sign in to confirm your age" not in str(e):
            raise
        ydl_opts_age_gate = dict(ydl_opts)
        ydl_opts_age_gate["extractor_args"] = {
            "youtube": {
                "player_client": ["web_creator", "tv_downgraded", "web"],
            },
        }
        with yt_dlp.YoutubeDL(ydl_opts_age_gate) as ydl:
            ydl.download([youtube_url])

    if youtube_id:
        _embed_youtube_id_tag(f"{output_path}.opus", ffmpeg_bin, youtube_id)

    return True


def _embed_youtube_id_tag(opus_path: str, ffmpeg_bin: str, youtube_id: str) -> None:
    """
    Remuxes opus_path in place, adding the MIMOO_YOUTUBE_ID Vorbis
    Comment tag. Uses -c copy (stream copy, no re-encode) so this is
    fast and lossless -- only the container's comment header changes.
    Writes to a sibling temp file first and replaces the original only
    on success, so a failure here never corrupts or loses the already-
    downloaded audio.
    ---
    Remuxea opus_path in situ, anadiendo el tag Vorbis Comment
    MIMOO_YOUTUBE_ID. Usa -c copy (copia de stream, sin recodificar),
    asi que es rapido y sin perdidas -- solo cambia la cabecera de
    comentario del contenedor. Escribe primero en un archivo temporal
    hermano y solo reemplaza el original si tuvo exito, para que un
    fallo aqui nunca corrompa ni pierda el audio ya descargado.
    """
    tagged_path = f"{opus_path}.tagged.opus"
    result = subprocess.run(
        [
            ffmpeg_bin,
            "-y",
            "-i", opus_path,
            "-c", "copy",
            "-metadata", f"{MIMOO_YOUTUBE_ID_TAG}={youtube_id}",
            tagged_path,
        ],
        capture_output=True,
    )
    if result.returncode != 0 or not os.path.exists(tagged_path):
        # No se aborta la descarga por esto -- el audio ya esta bien
        # descargado, solo falta el tag. Se deja el archivo sin tag
        # (LibraryReconciler caera a local: si algun dia hace falta
        # reconciliarlo, igual que antes de este fix).
        # ---
        # Download isn't aborted over this -- the audio is already
        # downloaded fine, only the tag is missing. The file is left
        # untagged (LibraryReconciler will fall back to local: if it
        # ever needs reconciling, same as before this fix).
        if os.path.exists(tagged_path):
            os.remove(tagged_path)
        return

    os.replace(tagged_path, opus_path)


"""
link_resolver.py — resuelve un enlace de YouTube/YouTube Music pegado
por el usuario (vídeo suelto o playlist/álbum) directamente con
yt-dlp, sin pasar por la YouTube Data API (Hito 05, PASO 6f).

Peticion explicita de Miguel Angel: "aqui la busqueda es externa" --
el usuario ya encontro el contenido el mismo en YouTube y solo pega
el enlace; la app no necesita buscar nada, solo listar lo que hay en
ese enlace. yt-dlp usa la web/API interna de YouTube para esto, no la
Data API con cuota -- coste de cuota CERO sin importar cuantas veces
se use.

Soporta:
  - Enlaces de playlist:      youtube.com/playlist?list=...
  - Enlaces de video suelto:  youtube.com/watch?v=... o youtu.be/...
  - Enlaces de YouTube Music: music.youtube.com/watch?v=...
    (yt-dlp los resuelve con el mismo extractor que youtube.com, sin
    necesitar codigo especial)

---
link_resolver.py — resolves a YouTube/YouTube Music link pasted by
the user (single video or playlist/album) directly with yt-dlp,
without going through the YouTube Data API (Hito 05, PASO 6f).

Explicit request from Miguel Angel: "the search here is external" --
the user already found the content themselves on YouTube and just
pastes the link; the app does not need to search anything, only list
what is in that link. yt-dlp uses YouTube's own web/internal API for
this, not the quota-limited Data API -- zero quota cost no matter how
many times it is used.
"""

import yt_dlp


def resolve_youtube_link(url: str) -> str:
    """
    Resolves a pasted YouTube/YouTube Music URL into a lightweight
    track list, as a JSON string (Chaquopy interop is simplest and
    most robust via a JSON string parsed on the Kotlin side with
    Gson, rather than walking a raw Python dict/list from Kotlin).

    extract_flat="in_playlist" is used so playlist entries are read
    from the playlist page itself (fast, one HTTP round-trip) instead
    of yt-dlp visiting every single video page one by one (which
    would be correct but painfully slow for a 20-track album). This
    still includes duration for YouTube, since it comes from the
    playlist page's own per-item metadata, not from the video page.

    Returns a JSON string:
      {
        "title": str,            # playlist title, or the single video's title
        "tracks": [
          {
            "youtube_id": str,
            "title": str,
            "duration_seconds": int,   # 0 if unknown
            "channel_title": str,
            "thumbnail_url": str | null,
          },
          ...
        ]
      }

    Raises RuntimeError if yt-dlp cannot resolve anything from the URL
    (private/deleted playlist, malformed link, etc.) — the caller
    (Kotlin) surfaces this message directly, no silent empty result.
    ---
    Resuelve una URL de YouTube/YouTube Music pegada en una lista de
    pistas ligera, como cadena JSON (la interoperabilidad con Chaquopy
    es más simple y robusta vía una cadena JSON parseada en el lado
    Kotlin con Gson, en vez de recorrer un dict/list de Python en
    crudo desde Kotlin).

    Se usa extract_flat="in_playlist" para que las entradas de la
    playlist se lean de la propia página de la playlist (rápido, una
    sola ida y vuelta HTTP) en vez de que yt-dlp visite cada página de
    vídeo una a una (correcto, pero dolorosamente lento para un álbum
    de 20 pistas). Esto sigue incluyendo la duración para YouTube, ya
    que viene de los metadatos propios de cada item en la página de la
    playlist, no de la página del vídeo.

    Lanza RuntimeError si yt-dlp no puede resolver nada de la URL
    (playlist privada/borrada, enlace mal formado, etc.) — quien llama
    (Kotlin) muestra este mensaje directamente, sin resultado vacío en
    silencio.
    """
    import json

    options = {
        "quiet": True,
        "no_warnings": True,
        "extract_flat": "in_playlist",
        "skip_download": True,
    }
    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=False)

    if info is None:
        raise RuntimeError("yt-dlp no pudo resolver el enlace: " + url)

    entries = info.get("entries")
    if entries is not None:
        tracks = [
            _entry_to_track(entry) for entry in entries if entry is not None
        ]
        result = {
            "title": info.get("title") or "(lista sin título)",
            "tracks": tracks,
        }
    else:
        result = {
            "title": info.get("title") or "(sin título)",
            "tracks": [_entry_to_track(info)],
        }

    return json.dumps(result)


def _entry_to_track(entry: dict) -> dict:
    return {
        "youtube_id": entry.get("id"),
        "title": entry.get("title") or "(sin título)",
        "duration_seconds": int(entry.get("duration") or 0),
        "channel_title": entry.get("uploader") or entry.get("channel") or "",
        "thumbnail_url": _best_thumbnail(entry.get("thumbnails")),
    }


def _best_thumbnail(thumbnails):
    # yt-dlp devuelve las miniaturas ordenadas de menor a mayor
    # resolucion habitualmente -- la ultima es la de mayor calidad
    # disponible.
    # ---
    # yt-dlp usually returns thumbnails ordered from lowest to highest
    # resolution -- the last one is the highest quality available.
    if not thumbnails:
        return None
    return thumbnails[-1].get("url")

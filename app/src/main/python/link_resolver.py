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

import re
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
            track for entry in entries
            if entry is not None
            for track in [_entry_to_track(entry)]
            if track is not None
        ]
        result = {
            "title": info.get("title") or "(lista sin título)",
            "tracks": tracks,
            "is_album": _is_official_album(info),
        }
    else:
        track = _entry_to_track(info)
        if track is None:
            raise RuntimeError(
                "El enlace no resolvió a ningún vídeo reproducible: " + url
            )
        result = {
            "title": info.get("title") or "(sin título)",
            "tracks": [track],
            "is_album": False,
        }

    return json.dumps(result)


def _is_official_album(info: dict) -> bool:
    """
    S055 -- bug real reportado por Miguel Ángel: al importar una
    PLAYLIST normal de YouTube Music (una lista personal/curada, que
    puede mezclar artistas y álbumes distintos), la app la trataba
    igual que un álbum real -- todas sus pistas quedaban agrupadas
    bajo un único "álbum" falso con el título de la lista.

    Señal real para distinguirlos: cuando se comparte un álbum oficial
    de YouTube Music (music.youtube.com/browse/MPREb_... o el enlace
    equivalente de youtube.com/playlist), yt-dlp lo resuelve con un id
    de playlist AUTOGENERADO por YouTube con el prefijo "OLAK5uy" --
    nunca elegido por ningún usuario, reservado específicamente para
    representar un álbum real como si fuera una playlist internamente.
    Las playlists normales (creadas a mano por cualquier usuario,
    curadas o de "Mix"/radio automática) tienen ids con otros prefijos
    ("PL...", "RD...", etc.), nunca ese.

    Solo importa cuando `entries` no es None (esta función solo se
    llama desde esa rama) -- un vídeo suelto nunca es un álbum.
    ---
    S055 -- real bug reported by Miguel Ángel: importing a regular
    YouTube Music PLAYLIST (a personal/curated list, which can mix
    different artists and albums) was treated the same as a real
    album -- all its tracks ended up grouped under one fake "album"
    named after the playlist's title.

    Real signal to tell them apart: when an official YouTube Music
    album is shared (music.youtube.com/browse/MPREb_... or the
    equivalent youtube.com/playlist link), yt-dlp resolves it with a
    playlist id AUTO-GENERATED by YouTube with the "OLAK5uy" prefix --
    never chosen by any user, reserved specifically to represent a
    real album as if it were a playlist internally. Regular playlists
    (hand-created by any user, curated or an automatic "Mix"/radio)
    have ids with other prefixes ("PL...", "RD...", etc.), never that
    one.

    Only matters when `entries` is not None (this is only called from
    that branch) -- a single video is never an album.
    """
    playlist_id = info.get("id") or ""
    return playlist_id.startswith("OLAK5uy")


def search_by_type(query: str, sp: str, limit: int = 15) -> str:
    """
    Searches YouTube filtered by result type (playlist or channel) via
    the "sp" filter token of YouTube's own search results page --
    same free scraping mechanism as ytsearchN: video search
    (ExternalLinkResolver.searchYoutube()), never the quota-limited
    Data API. H08 PARTE 1 (S009): unlike a pasted link, the caller
    doesn't know the playlist/channel URL yet -- this finds
    candidates by free text so one can then be opened exactly like a
    pasted link, via resolve_youtube_link().

    sp is the caller's responsibility (ExternalLinkResolver.kt), not
    hardcoded here, so the two known filter values (playlists,
    channels -- verified against YouTube's own "Filtros de búsqueda"
    UI and independent documentation, S009) live in one place instead
    of duplicated between Kotlin and Python.

    Returns a JSON string:
      {
        "results": [
          {
            "id": str,
            "title": str,
            "url": str,
            "subtitle": str,          # best-effort, "" if unknown
            "thumbnail_url": str | null,
          },
          ...
        ]
      }

    Never raises for "no matches" (returns an empty list) -- only
    raises if yt-dlp itself cannot reach the search page at all.
    Filtered-type search is a less stable area of yt-dlp than plain
    video search (documented instability in the yt-dlp tracker); a
    caller getting zero results back is expected behavior to handle
    gracefully, not necessarily a bug.
    ---
    Busca en YouTube filtrado por tipo de resultado (lista o canal) vía
    el token de filtro "sp" de la propia página de resultados de
    YouTube -- mismo mecanismo gratuito de scraping que la búsqueda de
    vídeos ytsearchN: (ExternalLinkResolver.searchYoutube()), nunca la
    Data API de cuota. H08 PARTE 1 (S009): a diferencia de un enlace
    pegado, quien llama todavía no conoce la URL de la lista/canal --
    esto encuentra candidatos por texto libre para que luego se pueda
    abrir uno exactamente igual que un enlace pegado, vía
    resolve_youtube_link().

    sp es responsabilidad de quien llama (ExternalLinkResolver.kt), no
    va fijo aquí, para que los dos valores de filtro conocidos
    (listas, canales -- verificados contra la propia UI de "Filtros de
    búsqueda" de YouTube y documentación independiente, S009) vivan en
    un solo sitio en vez de duplicados entre Kotlin y Python.

    Devuelve una cadena JSON:
      {
        "results": [
          {
            "id": str,
            "title": str,
            "url": str,
            "subtitle": str,          # best-effort, "" si se desconoce
            "thumbnail_url": str | null,
          },
          ...
        ]
      }

    Nunca lanza excepción por "sin resultados" (devuelve una lista
    vacía) -- solo lanza si yt-dlp no puede llegar en absoluto a la
    página de resultados. La búsqueda filtrada por tipo es una zona
    menos estable de yt-dlp que la búsqueda normal de vídeos
    (inestabilidad documentada en el propio tracker de yt-dlp); que
    quien llama reciba cero resultados es un comportamiento esperado a
    manejar con gracia, no necesariamente un fallo.
    """
    import json
    import urllib.parse

    encoded_query = urllib.parse.quote_plus(query)
    search_url = (
        f"https://www.youtube.com/results?search_query={encoded_query}&sp={sp}"
    )

    options = {
        "quiet": True,
        "no_warnings": True,
        "extract_flat": True,
        "skip_download": True,
        "playlistend": limit,
    }
    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(search_url, download=False)

    entries = (info or {}).get("entries") or []
    results = [
        item for entry in entries
        if entry is not None
        for item in [_entry_to_search_type_result(entry)]
        if item is not None
    ]
    return json.dumps({"results": results[:limit]})


def _entry_to_search_type_result(entry: dict):
    # Descarta entradas sin id -- igual criterio defensivo que
    # _entry_to_track() para playlists resueltas por enlace: sin id no
    # hay nada que abrir despues.
    # ---
    # Discards entries without an id -- same defensive criterion as
    # _entry_to_track() for link-resolved playlists: no id means
    # nothing to open afterwards.
    entry_id = entry.get("id")
    if not entry_id:
        return None

    entry_type = entry.get("_type") or ""
    url = entry.get("url") or entry.get("webpage_url")
    if not url:
        # extract_flat a veces solo da el id, no la URL completa --
        # se reconstruye a partir del id y el tipo declarado por
        # yt-dlp.
        # ---
        # extract_flat sometimes only gives the id, not the full URL
        # -- reconstructed from the id and yt-dlp's declared type.
        if entry_type == "playlist":
            url = f"https://www.youtube.com/playlist?list={entry_id}"
        else:
            url = f"https://www.youtube.com/channel/{entry_id}"

    subtitle_parts = []
    item_count = entry.get("playlist_count") or entry.get("entry_count")
    if item_count:
        subtitle_parts.append(f"{item_count} vídeos")
    uploader = entry.get("uploader") or entry.get("channel")
    if uploader:
        subtitle_parts.append(uploader)
    subscriber_count = entry.get("channel_follower_count")
    if subscriber_count:
        subtitle_parts.append(f"{subscriber_count} suscriptores")

    return {
        "id": entry_id,
        "title": entry.get("title") or "(sin título)",
        "url": url,
        "subtitle": " · ".join(subtitle_parts),
        "thumbnail_url": _best_thumbnail(entry.get("thumbnails")),
    }


def _entry_to_track(entry: dict):
    # Algunas entradas de álbumes/playlists (visto con enlaces de
    # YouTube Music) no traen id de vídeo real -- pistas bonus no
    # disponibles, marcadores de seccion, contenido regional
    # bloqueado, etc. Sin id no hay nada que reproducir ni descargar,
    # así que se descartan aquí en vez de dejar pasar un "youtube_id"
    # nulo que rompería un campo Kotlin no-nullable en el lado
    # Android (causa real del cierre de la app reportado por Miguel
    # Ángel, 2026-07-02, al importar un álbum de YouTube Music).
    # ---
    # Some entries in albums/playlists (seen with YouTube Music links)
    # don't carry a real video id -- unavailable bonus tracks, section
    # markers, region-blocked content, etc. With no id there is
    # nothing to play or download, so they're discarded here instead
    # of letting a null "youtube_id" through, which would break a
    # non-nullable Kotlin field on the Android side (real cause of the
    # app closing reported by Miguel Ángel, 2026-07-02, when importing
    # a YouTube Music album).
    video_id = entry.get("id")
    if not video_id:
        return None
    # YouTube devuelve a veces literalmente "-" como uploader en
    # playlists auto-generadas de YouTube Music (álbumes) -- no es un
    # nombre de canal real, así que se normaliza a "" igual que cuando
    # el campo falta del todo. Sin esto, "-" se colaba como si fuera
    # un artista válido (reportado por Miguel Ángel, 2026-07-03, con
    # el álbum "Moon Safari" de Air).
    # ---
    # YouTube sometimes literally returns "-" as the uploader on
    # auto-generated YouTube Music playlists (albums) -- not a real
    # channel name, so it's normalized to "" just like a missing
    # field. Without this, "-" leaked through as if it were a valid
    # artist name (reported by Miguel Ángel, 2026-07-03, with Air's
    # "Moon Safari" album).
    channel_title = entry.get("uploader") or entry.get("channel") or ""
    if channel_title.strip() == "-":
        channel_title = ""
    # YouTube nombra automáticamente los canales "Topic" (subida
    # automática de audio, sin vídeo real) como "<Artista> - Topic" --
    # NO es parte del nombre del artista. Sin esto se colaba tal cual
    # como artista/canal en toda la app (Biblioteca, Búsqueda,
    # Playlists...) y, en particular, rompía por completo la búsqueda
    # de "relacionados" de la Radio (H08): MusicBrainz no tiene ningún
    # artista llamado "Jeff Mills - Topic" (reportado por Miguel Ángel,
    # S010, con log real de radio_relacionados_debug.txt). El sufijo es
    # siempre literal " - Topic" en inglés, independientemente del
    # idioma de la interfaz de YouTube -- verificado contra ejemplos
    # reales de canales "Topic" de distintos artistas/idiomas.
    # ---
    # YouTube auto-names "Topic" channels (auto-uploaded audio, no real
    # video) as "<Artist> - Topic" -- NOT part of the artist's actual
    # name. Without this it leaked through as-is as the artist/channel
    # everywhere in the app (Library, Search, Playlists...) and, in
    # particular, completely broke Radio's (H08) "related artist"
    # lookup: MusicBrainz has no artist named "Jeff Mills - Topic"
    # (reported by Miguel Ángel, S010, with a real log from
    # radio_relacionados_debug.txt). The suffix is always the literal
    # English " - Topic" regardless of YouTube's UI language --
    # verified against real "Topic" channel examples from different
    # artists/languages.
    if channel_title.endswith(" - Topic"):
        channel_title = channel_title[: -len(" - Topic")].strip()

    # S010 (continuación) -- dos sufijos más de canal que rompen la
    # búsqueda de "relacionados" de la Radio exactamente igual que
    # " - Topic", encontrados con datos reales en la misma sesión
    # (radio_relacionados_debug.txt): "PISTONES Oficial" y
    # "PistonesVEVO" -- MusicBrainz tampoco tiene ningún artista con
    # esos nombres. A diferencia de "- Topic" (sufijo literal que
    # YouTube pone él solo), "VEVO" y "Oficial" los elige el propio
    # dueño del canal al nombrarlo, así que el separador varía
    # (" VEVO", " - VEVO", "VEVO" pegado sin espacio...) -- de ahí la
    # regex en vez de un endswith() literal como con Topic.
    # ---
    # S010 (continued) -- two more channel suffixes that break Radio's
    # "related artist" lookup exactly like " - Topic" did, found with
    # real data in the same session: "PISTONES Oficial" and
    # "PistonesVEVO" -- MusicBrainz has no artist under those names
    # either. Unlike "- Topic" (a literal suffix YouTube adds by
    # itself), "VEVO"/"Oficial" are chosen by the channel owner, so the
    # separator varies -- hence the regex instead of a literal
    # endswith() like with Topic.
    for suffix_pattern in (r"\s*-?\s*VEVO$", r"\s*-?\s*Oficial$"):
        channel_title = re.sub(suffix_pattern, "", channel_title, flags=re.IGNORECASE).strip()

    return {
        "youtube_id": video_id,
        "title": entry.get("title") or "(sin título)",
        "duration_seconds": int(entry.get("duration") or 0),
        "channel_title": channel_title,
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

#!/usr/bin/env python3
"""
VALIDACION REAL de known_hit_artists.json contra YouTube -- para que
seleccionar una decada sola (o "Conocido en Espana") en miMooutCast
arranque al instante, exactamente igual que ya arranca al instante un
genero con semilla (mimooutcast_seed.json).

MOTIVACION, S037 (orden explicita y final de Miguel Angel, 2026-08-24,
tras un mes construyendo esta base de datos justo para esto): *"cuando
yo le de al boton de decada de los 90... la cancion que haya sonando
sigue sonando y a continuacion... empieza a sonar lo que he puesto...
ni 10 segundos ni nada."*

Causa real diagnosticada con logs reales de la sesion: la semilla de
genero (mimooutcast_seed.json) SI trae el youtube_id ya validado de
antemano -- por eso arranca al instante (solo hay que resolver la URL
de streaming, que caduca, nunca buscar ni verificar). El diccionario
de exitos (known_hit_artists.json, usado por decada sola y "Conocido
en Espana") NUNCA se valido contra YouTube -- cada eleccion exige
buscar en vivo, filtrar por duracion/titulo, y a menudo falla ("0 de 6
resultados pasaron el filtro"), tal como se vio repetidamente en
mimooutcast_debug.txt.

Este script hace exactamente lo que ya hace
PlayerManager.resolveYoutubeCandidate() en el propio dispositivo (con
songTitle conocido): busca "artista cancion" en YouTube (hasta 6
resultados), descarta por duracion > 15 min, por titulo que delate que
NO es la cancion suelta (mismas dos listas de pistas,
COMPILATION_TITLE_HINTS + NOT_MUSIC_TITLE_HINTS, copiadas literalmente
de PlayerManager.kt), y por que el titulo no contenga el nombre del
artista -- y se queda con el primero que pase. La diferencia: esto
corre UNA VEZ, en GitHub Actions, contra el diccionario ENTERO, y el
resultado (youtube_id real y validado) se guarda para siempre en el
propio APK -- no en cada sesion del usuario.

Incremental de verdad (mismo patron que MimooutcastDatabaseBuilder):
guarda progreso tras CADA entrada, para poder parar y retomar entre
ejecuciones de GitHub Actions sin perder lo ya validado.
"""

import json
import re
import sys
import time
import unicodedata

try:
    import yt_dlp
except ImportError:
    print("ERROR: falta la libreria 'yt-dlp' (pip install yt-dlp).")
    sys.exit(1)

DICT_PATH = "app/src/main/assets/known_hit_artists.json"
OUT_SEED_PATH = "app/src/main/assets/mimooutcast_decade_seed.json"
OUT_REPORT_PATH = "tools/decade_seed_report.json"

SEARCH_LIMIT = 6
MAX_TRACK_SECONDS = 15 * 60
SLEEP_BETWEEN_SEARCHES = 0.3

# Copiadas literalmente de PlayerManager.kt -- COMPILATION_TITLE_HINTS + NOT_MUSIC_TITLE_HINTS.
COMPILATION_TITLE_HINTS = [
    "full album", "greatest hits", "playlist", "compilation", "best songs of",
    "best of", "all songs", "complete works", "full concert", "megamix",
    "top 10", "top 20", "top 50", "top 100",
    "album completo", "disco completo", "grandes exitos", "sus mejores",
    "lo mejor de", "los mejores", "recopilacion", "recopilatorio",
    "concierto completo", "exitos",
]
NOT_MUSIC_TITLE_HINTS = [
    "interview", "entrevista", "chapter", "capitulo", "episode", "episodio",
    "podcast", "documentary", "documental", "audiobook", "audiolibro",
    "full movie", "pelicula completa", "tutorial", "how to play",
    "como tocar", "cómo tocar", "lesson", "leccion", "lección",
    "explained", "explicado",
]
ALL_HINTS = COMPILATION_TITLE_HINTS + NOT_MUSIC_TITLE_HINTS


def normalize(value):
    stripped = unicodedata.normalize("NFKD", value)
    stripped = "".join(c for c in stripped if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", stripped).strip().lower()


def looks_like_non_song(title):
    normalized = normalize(title)
    if not normalized:
        return False
    return any(
        re.search(r"(^|\s)" + re.escape(normalize(hint)) + r"($|\s)", normalized)
        for hint in ALL_HINTS
    )


def matches_artist(artist, title):
    needle = normalize(artist)
    if not needle:
        return True
    return needle in normalize(title)


def search_and_validate(ydl, artist, song):
    query = f"ytsearch{SEARCH_LIMIT}:{artist} {song}"
    try:
        info = ydl.extract_info(query, download=False)
    except Exception as error:
        return None, "%s: %s" % (type(error).__name__, error)
    entries = (info or {}).get("entries") or []
    for entry in entries:
        if entry is None:
            continue
        title = entry.get("title") or ""
        duration = entry.get("duration")
        video_id = entry.get("id")
        if not video_id or duration is None:
            continue
        if not (0 < duration <= MAX_TRACK_SECONDS):
            continue
        if looks_like_non_song(title):
            continue
        if not matches_artist(artist, title):
            continue
        return video_id, None
    return None, None


def main():
    with open(DICT_PATH, encoding="utf-8") as handle:
        dictionary = json.load(handle)

    try:
        with open(OUT_SEED_PATH, encoding="utf-8") as handle:
            seed = json.load(handle)
    except FileNotFoundError:
        seed = {}

    ydl_opts = {"quiet": True, "no_warnings": True, "skip_download": True, "extract_flat": False}
    total = 0
    validated = 0
    rejected = 0
    failed = 0

    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        for decade in sorted(dictionary.keys()):
            decade_seed = seed.setdefault(decade, {})
            for origin in ("es", "intl"):
                for entry in dictionary[decade].get(origin) or []:
                    artist = entry["artist"]
                    song = entry.get("song") or ""
                    if not song:
                        continue
                    key = "%s|||%s" % (artist, song)
                    total += 1
                    if key in decade_seed:
                        continue
                    video_id, error = search_and_validate(ydl, artist, song)
                    if error:
                        failed += 1
                        print("[%s] ERROR '%s' - '%s': %s" % (decade, artist, song, error), flush=True)
                    elif video_id:
                        decade_seed[key] = {"artist": artist, "song": song, "youtube_id": video_id}
                        validated += 1
                        print("[%s] OK '%s' - '%s' -> %s" % (decade, artist, song, video_id), flush=True)
                    else:
                        rejected += 1
                        print("[%s] SIN MATCH '%s' - '%s'" % (decade, artist, song), flush=True)
                    # Guardado incremental TRAS CADA ENTRADA -- si el job
                    # se corta (timeout de GitHub Actions), no se pierde
                    # nada de lo ya validado.
                    with open(OUT_SEED_PATH, "w", encoding="utf-8") as handle:
                        json.dump(seed, handle, ensure_ascii=False, indent=1, sort_keys=True)
                        handle.write("\n")
                    time.sleep(SLEEP_BETWEEN_SEARCHES)

    with open(OUT_REPORT_PATH, "w", encoding="utf-8") as handle:
        json.dump({
            "total_diccionario": total,
            "validadas_esta_ejecucion": validated,
            "rechazadas_esta_ejecucion": rejected,
            "fallos_esta_ejecucion": failed,
            "total_validadas_acumuladas": sum(len(v) for v in seed.values()),
        }, handle, ensure_ascii=False, indent=1, sort_keys=True)
        handle.write("\n")

    print("\n--- RESUMEN ---")
    print("Total en diccionario:", total)
    print("Validadas esta ejecucion:", validated)
    print("Rechazadas (sin match valido):", rejected)
    print("Fallos de busqueda:", failed)
    print("Total validadas acumuladas:", sum(len(v) for v in seed.values()))
    return 0


if __name__ == "__main__":
    sys.exit(main())

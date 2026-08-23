#!/usr/bin/env python3
"""
COSECHA: "LAS 100 CANCIONES DEL AÑO EN ESPAÑA" (Spotify, Jose Julian
Marti Santiago) -- fuente gemela de harvest_los40_charts.py.

Motivacion (H08, S035). Los 40 solo da el numero UNO semanal; esta
serie da las 100 canciones representativas de CADA ANO en Espana, con
cobertura desde 1960 (Los 40 arranca en 1966).

CAMBIO DE DISENO REAL (S035, segunda vuelta): la primera version de
este script usaba `client.get_user(CREATOR_USER_ID)` para descubrir
TODAS las playlists del perfil de una vez -- parecia la via mas
robusta segun la documentacion de la libreria ("Public user profiles
— get_user()"), pero en la practica esa llamada exige autenticacion
(`AuthenticationError: ... build SpotifyClient(cookies=...)`),
confirmado con una sonda de aislamiento real contra el entorno de
GitHub Actions. Sin cuenta de Spotify que autenticar, se descarta.

En su lugar: se busca ANO A ANO con `client.search()` (documentada
como anonima, confirmada en la misma sonda), filtrando los resultados
por el nombre exacto del creador y el patron del titulo -- mismo
resultado final (descubrir todas las playlists de la serie), sin
depender de un endpoint que exige login. Un ano sin resultado
simplemente no aporta nada a la cosecha (la serie puede no cubrir
todos los anos) -- no es un fallo del script.

Este script NO toca known_hit_artists.json -- deja la cosecha cruda y
un informe/diagnostico. La fusion es un paso aparte
(enrich_chart_artists.py + merge_charts_into_dictionary.py +
merge_intl_charts_into_dictionary.py, reutilizados via variables de
entorno, sin tocar su codigo).
"""

import json
import re
import sys
import time
import traceback
import unicodedata

OUT_RAW = "tools/chart_spotify100_raw.json"
OUT_REPORT = "tools/chart_spotify100_report.json"
OUT_DEBUG = "tools/chart_spotify100_debug.json"
DICT_PATH = "app/src/main/assets/known_hit_artists.json"

CREATOR_NAME = "Jose Julian Marti Santiago"
TITLE_PATTERN = re.compile(r"^\s*(\d{4})\s*-\s*LAS\s+100\s+CANCIONES\s+DEL\s+A[NÑ]O\s+EN\s+ESPA[NÑ]A", re.IGNORECASE)
FIRST_YEAR = 1955
LAST_YEAR = 2026
DELAY_SECONDS = 0.4
MAX_RETRIES = 3


def fold(value):
    stripped = unicodedata.normalize("NFKD", value)
    stripped = "".join(c for c in stripped if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", stripped).strip().lower()


def with_retries(label, fn):
    last_error = None
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            return fn(), None
        except BaseException as error:
            last_error = error
            time.sleep(DELAY_SECONDS * (2 ** attempt))
    return None, last_error


def find_playlist_id(client, year):
    query = "%d LAS 100 CANCIONES DEL ANO EN ESPANA" % year
    results, error = with_retries("search:%d" % year, lambda: client.search(
        query, types=("playlist",), limit=10,
    ))
    if results is None:
        return None, str(error) if error else "sin resultado"
    for pl in getattr(results, "playlists", None) or []:
        title = getattr(pl, "name", None) or ""
        owner = getattr(pl, "owner", None)
        owner_name = getattr(owner, "name", None) or ""
        match = TITLE_PATTERN.match(title)
        if not match:
            continue
        if int(match.group(1)) != year:
            continue
        if fold(owner_name) != fold(CREATOR_NAME):
            continue
        return getattr(pl, "id", None), None
    return None, None


def track_rows(playlist, year):
    rows = []
    for pt in getattr(playlist, "tracks", None) or []:
        track = getattr(pt, "track", None)
        if track is None:
            continue
        name = getattr(track, "name", None)
        artists = getattr(track, "artists", None) or []
        artist_name = getattr(artists[0], "name", None) if artists else None
        if name and artist_name:
            rows.append({"song": str(name).strip(), "artist": str(artist_name).strip(), "year": year})
    return rows


def write_debug(debug):
    with open(OUT_DEBUG, "w", encoding="utf-8") as handle:
        json.dump(debug, handle, ensure_ascii=False, indent=1, sort_keys=True, default=str)
        handle.write("\n")


def run():
    debug = {"ok": False, "stage": "arranque", "por_ano": {}}
    try:
        debug["stage"] = "import spotify_scraper"
        from spotify_scraper import SpotifyClient

        debug["stage"] = "cargar diccionario actual"
        with open(DICT_PATH, encoding="utf-8") as handle:
            dictionary = json.load(handle)
        known = {
            fold(entry["artist"])
            for block in dictionary.values()
            for origin in ("es", "intl")
            for entry in block.get(origin) or []
        }
        print("Diccionario actual: %d artistas distintos.\n" % len(known), flush=True)

        harvest = []
        found_years = {}
        not_found_years = []
        error_years = {}

        debug["stage"] = "buscar y cosechar por ano"
        client = SpotifyClient()
        for year in range(FIRST_YEAR, LAST_YEAR + 1):
            playlist_id, search_error = find_playlist_id(client, year)
            if search_error:
                error_years[year] = search_error
                print("[%d] error de busqueda: %s" % (year, search_error), flush=True)
                time.sleep(DELAY_SECONDS)
                continue
            if playlist_id is None:
                not_found_years.append(year)
                time.sleep(DELAY_SECONDS)
                continue

            playlist, error = with_retries("get_playlist:%d" % year, lambda pid=playlist_id: client.get_playlist(pid))
            if playlist is None:
                error_years[year] = str(error) if error else "get_playlist devolvio None"
                print("[%d] error al leer la playlist: %s" % (year, error_years[year]), flush=True)
                time.sleep(DELAY_SECONDS)
                continue

            rows = track_rows(playlist, year)
            seen = set()
            unique = []
            for row in rows:
                key = (fold(row["artist"]), fold(row["song"]))
                if key in seen:
                    continue
                seen.add(key)
                unique.append(row)
            harvest += unique
            found_years[year] = len(unique)
            debug["por_ano"][year] = len(unique)
            print("[%d] %3d canciones" % (year, len(unique)), flush=True)
            time.sleep(DELAY_SECONDS)

        debug["stage"] = "consolidar cosecha"
        seen = set()
        unique = []
        for row in sorted(harvest, key=lambda r: r["year"]):
            key = (fold(row["artist"]), fold(row["song"]))
            if key in seen:
                continue
            seen.add(key)
            unique.append(row)

        artists = {fold(r["artist"]) for r in unique}
        nuevos = artists - known

        print("\n--- COSECHA SPOTIFY100 ---\n", flush=True)
        print("Anos con playlist encontrada:  %d %s" % (len(found_years), sorted(found_years)))
        print("Anos sin playlist:              %d" % len(not_found_years))
        print("Anos con error:                 %d %s" % (len(error_years), error_years))
        print("Canciones distintas:            %d" % len(unique))
        print("Artistas distintos:             %d" % len(artists))
        print("  ...NUEVOS:                    %d" % len(nuevos))

        with open(OUT_RAW, "w", encoding="utf-8") as handle:
            json.dump(unique, handle, ensure_ascii=False, indent=1)
            handle.write("\n")
        with open(OUT_REPORT, "w", encoding="utf-8") as handle:
            json.dump({
                "canciones": len(unique),
                "artistas": len(artists),
                "artistas_nuevos": sorted(nuevos),
                "anos_encontrados": found_years,
                "anos_sin_playlist": not_found_years,
                "anos_con_error": error_years,
            }, handle, ensure_ascii=False, indent=1, sort_keys=True)
            handle.write("\n")

        debug["ok"] = True
        debug["canciones"] = len(unique)
        write_debug(debug)

        if len(unique) < 200:
            print("\nERROR: cosecha anormalmente corta -- revisar antes de usar nada de esto.")
            return 1
        return 0
    except BaseException as error:
        debug["error"] = "%s: %s" % (type(error).__name__, error)
        debug["traceback"] = traceback.format_exc()
        write_debug(debug)
        print("EXCEPCION NO CONTROLADA en stage='%s': %s" % (debug["stage"], error), flush=True)
        print(debug["traceback"], flush=True)
        return 1


if __name__ == "__main__":
    sys.exit(run())

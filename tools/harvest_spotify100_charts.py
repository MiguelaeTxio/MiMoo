#!/usr/bin/env python3
"""
COSECHA: "LAS 100 CANCIONES DEL AÑO EN ESPAÑA" (Spotify, Jose Julian
Marti Santiago) -- fuente gemela de harvest_los40_charts.py.

Motivacion (H08, S035). Miguel Angel senalo un hueco real: la fusion
espanola (Fase 3, S024) y la internacional (S035) salen de los numeros
UNO de LOS40 -- profundidad de una sola cancion por semana. Estas
listas de Spotify dan, en cambio, las 100 canciones representativas de
CADA ANO en Espana (exito, no solo el numero uno), y la serie llega
hasta 1960 -- cubre la decada de 1960 con detalle que LOS40 (que
arranca en 1966) no tiene, y es una fuente totalmente distinta e
independiente para contrastar/completar.

FUENTE. Perfil publico de Spotify de Jose Julian Marti Santiago
(https://open.spotify.com/user/115935096) -- una playlist publica por
ano, titulada "{ANO} - LAS 100 CANCIONES DEL ANO EN ESPANA". Se
descubren TODAS las playlists del perfil (sin asumir un rango de anos
fijo) y se filtran por el patron del titulo.

MECANISMO DE LECTURA. La pagina estatica de una playlist solo renderiza
~30 canciones (limite de las metaetiquetas de vista previa social de
Spotify, no del contenido real) -- verificado en la propia sesion con
web_fetch. El Top 100 completo exige los mismos endpoints JSON que usa
el reproductor web. Se usa la libreria `spotifyscraper` (PyPI, activa,
con test diario contra Spotify real -- ver
https://github.com/AliAkhtari78/SpotifyScraper), que ya resuelve el
token anonimo del reproductor web y expone `get_user()`/`get_playlist()`
tipados, en vez de reimplementar esa logica a mano.

Este script NO toca known_hit_artists.json -- deja la cosecha cruda y
un informe (mismo patron de dos fases que harvest_los40_charts.py:
cosechar y medir primero, enriquecer/fusionar despues con
merge_charts_into_dictionary.py / merge_intl_charts_into_dictionary.py,
que ya saben leer cualquier fichero con forma
[{"song":..., "artist":..., "year":...}, ...] -- se les pasa este
RAW_PATH nuevo sin tocar su codigo).
"""

import json
import re
import sys
import time
import unicodedata

OUT_RAW = "tools/chart_spotify100_raw.json"
OUT_REPORT = "tools/chart_spotify100_report.json"
DICT_PATH = "app/src/main/assets/known_hit_artists.json"

CREATOR_USER_ID = "115935096"
TITLE_PATTERN = re.compile(r"^\s*(\d{4})\s*-\s*LAS\s+100\s+CANCIONES\s+DEL\s+A[ÑN]O\s+EN\s+ESPA[ÑN]A", re.IGNORECASE)
DELAY_SECONDS = 0.5
MAX_RETRIES = 4


def fold(value):
    stripped = unicodedata.normalize("NFKD", value)
    stripped = "".join(c for c in stripped if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", stripped).strip().lower()


def with_retries(label, fn):
    last_error = None
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            return fn()
        except Exception as error:  # SpotifyScraper's own exception hierarchy varies by call
            last_error = error
            print("    [%s] intento %d/%d fallo: %s" % (label, attempt, MAX_RETRIES, error), flush=True)
            time.sleep(DELAY_SECONDS * (2 ** attempt))
    print("    [%s] agotados los reintentos: %s" % (label, last_error), flush=True)
    return None


def track_rows(playlist, year):
    """Extrae (cancion, artista) de cada pista de la playlist -- acceso
    defensivo (getattr) porque el modelo tipado de spotifyscraper puede
    variar de forma entre versiones; nunca se asume una forma exacta
    sin comprobarla."""
    rows = []
    tracks = getattr(playlist, "tracks", None) or []
    for track in tracks:
        name = getattr(track, "name", None)
        artists = getattr(track, "artists", None) or []
        artist_name = None
        if artists:
            first = artists[0]
            artist_name = getattr(first, "name", None) or (first if isinstance(first, str) else None)
        if name and artist_name:
            rows.append({"song": str(name).strip(), "artist": str(artist_name).strip(), "year": year})
    return rows


def main():
    try:
        from spotify_scraper import SpotifyClient
    except ImportError:
        print("ERROR: falta la libreria 'spotifyscraper' (pip install spotifyscraper).")
        return 1

    with open(DICT_PATH, encoding="utf-8") as handle:
        dictionary = json.load(handle)
    known = {
        fold(entry["artist"])
        for block in dictionary.values()
        for origin in ("es", "intl")
        for entry in block.get(origin) or []
    }
    print("Diccionario actual: %d artistas distintos.\n" % len(known), flush=True)

    with SpotifyClient() as client:
        user = with_retries("perfil", lambda: client.get_user(CREATOR_USER_ID))
        if user is None:
            print("ERROR: no se pudo leer el perfil publico -- revisar el ID de usuario o la libreria.")
            return 1

        playlists = getattr(user, "playlists", None) or []
        print("Playlists publicas del perfil: %d\n" % len(playlists), flush=True)

        year_playlists = {}
        for pl in playlists:
            title = getattr(pl, "name", None) or ""
            match = TITLE_PATTERN.match(title)
            if not match:
                continue
            year_playlists[int(match.group(1))] = getattr(pl, "id", None) or getattr(pl, "url", None)

        if not year_playlists:
            print("ERROR: ninguna playlist del perfil encaja con el patron de titulo esperado -- "
                  "revisar TITLE_PATTERN o si el perfil cambio.")
            return 1

        print("Anos encontrados: %d (%s)\n" % (len(year_playlists), sorted(year_playlists)), flush=True)

        harvest = []
        per_year = {}
        failed_years = []
        for year in sorted(year_playlists):
            playlist_id = year_playlists[year]
            playlist = with_retries(str(year), lambda pid=playlist_id: client.get_playlist(pid))
            if playlist is None:
                failed_years.append(year)
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
            per_year[year] = len(unique)
            print("[%d] %3d canciones" % (year, len(unique)), flush=True)
            time.sleep(DELAY_SECONDS)

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
    print("Canciones distintas:  %d" % len(unique))
    print("Artistas distintos:   %d" % len(artists))
    print("  ...NUEVOS:          %d" % len(nuevos))
    print("Anos fallidos:        %s" % failed_years)

    with open(OUT_RAW, "w", encoding="utf-8") as handle:
        json.dump(unique, handle, ensure_ascii=False, indent=1)
        handle.write("\n")
    with open(OUT_REPORT, "w", encoding="utf-8") as handle:
        json.dump({
            "canciones": len(unique),
            "artistas": len(artists),
            "artistas_nuevos": sorted(nuevos),
            "por_ano": per_year,
            "anos_fallidos": failed_years,
        }, handle, ensure_ascii=False, indent=1, sort_keys=True)
        handle.write("\n")

    if len(unique) < 200:
        print("\nERROR: cosecha anormalmente corta -- revisar antes de usar nada de esto.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

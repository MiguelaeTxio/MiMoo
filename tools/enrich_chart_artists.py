#!/usr/bin/env python3
"""
Enriquece con PAIS y GENERO los artistas cosechados de Los 40.

Fase 2 de la ampliacion del diccionario (H08, S024). La fase 1
(harvest_los40_charts.py) trajo 2121 canciones y 946 artistas, pero una
lista de exitos da artista, tema y ano -- no da genero ni pais. Y sin
esos dos la cosecha no sirve para nada:

  * sin GENERO, `matchesGenre()` descarta la entrada: su conjunto de
    generos esta vacio y nunca casa con ningun ancla. Meter 2121
    canciones sin enriquecer no aportaria ni un candidato.
  * sin PAIS no se puede repartir entre el bloque `es` y el `intl`, que
    el diccionario separa con reglas distintas (S020).

ORDEN DE FUENTES, y el porque de cada una:

  1. MusicBrainz -- da pais y generos en la MISMA consulta, y su
     vocabulario ES el de genre_tree.json, asi que aterriza al 100%.
     Es la fuente barata y exacta; se agota primero.
  2. Discogs -- solo para los que MusicBrainz deja sin generos. Es lo
     que midio el sondeo de esta sesion: 86% de cobertura con
     vocabulario que aterriza al 90%, muy por encima de Wikipedia
     (39%) y Wikidata (36%).

Los filtros anti-homonimo del sondeo se conservan tal cual, porque
costaron cuatro pasadas aprenderlos: solo discos de papel 'Main', fuera
recopilaciones, fuera discos con mas de tres artistas acreditados, y
contraste de epoca contra el ano en que el artista fue numero uno.

NO escribe en known_hit_artists.json. Deja el enriquecido en su propio
archivo; la fusion es un paso aparte y revisable.
"""

import json
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter

RAW_PATH = "tools/chart_los40_raw.json"
TREE_PATH = "app/src/main/assets/genre_tree.json"
DICT_PATH = "app/src/main/assets/known_hit_artists.json"
OUT_PATH = "tools/chart_los40_enriched.json"

MB_API = "https://musicbrainz.org/ws/2"
DISCOGS_API = "https://api.discogs.com"
USER_AGENT = "MiMoo-ChartEnrich/1.0 ( https://github.com/MiguelaeTxio/MiMoo )"
MB_DELAY = 1.1          # MusicBrainz pide 1 peticion por segundo.
DISCOGS_DELAY = 0.4
MAX_RETRIES = 4
MAX_DESCENDANTS = 25

_TREE = {}
_BY_KEY = {}
_DESC = {}


def fold(value):
    for fancy, plain in (("\u2019", "'"), ("\u2018", "'"), ("\u02bc", "'")):
        value = value.replace(fancy, plain)
    stripped = unicodedata.normalize("NFKD", value)
    stripped = "".join(c for c in stripped if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", stripped).strip().lower()


def tree_key(value):
    folded = fold(value).replace("&", " and ").replace("-", " ").replace("/", " ")
    folded = re.sub(r"\s+", " ", folded).strip()
    return folded[:-6].strip() if folded.endswith(" music") else folded


def descendants(genre):
    key = genre.lower().strip()
    if key in _DESC:
        return _DESC[key]
    found, pending = set(), [key]
    while pending:
        current = pending.pop()
        for child in (_TREE.get(current) or {}).get("children") or []:
            child = child.lower().strip()
            if child not in found:
                found.add(child)
                pending.append(child)
    _DESC[key] = found
    return found


def is_specific(key):
    real = _BY_KEY.get(key)
    return bool(real) and len(descendants(real)) <= MAX_DESCENDANTS


def land(labels):
    """Deja solo las etiquetas que existen en el arbol, sin duplicar."""
    out = []
    for label in labels:
        real = _BY_KEY.get(tree_key(label))
        if real and real not in out:
            out.append(real)
    return out


def fetch_json(url, headers=None):
    last_error = None
    for attempt in range(1, MAX_RETRIES + 1):
        merged = {"User-Agent": USER_AGENT}
        merged.update(headers or {})
        try:
            request = urllib.request.Request(url, headers=merged)
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.loads(response.read().decode("utf-8", errors="replace"))
        except urllib.error.HTTPError as error:
            last_error = error
            if error.code in (429, 500, 502, 503, 504):
                time.sleep(MB_DELAY * (2 ** attempt))
                continue
            return None
        except (urllib.error.URLError, TimeoutError, OSError, ValueError) as error:
            last_error = error
            time.sleep(MB_DELAY * (2 ** attempt))
    print("    agotados los reintentos: %s" % last_error, flush=True)
    return None


def musicbrainz(name):
    """Pais y generos del artista. Devuelve (pais, generos, encontrado)."""
    query = 'artist:"%s"' % name.replace('"', "")
    data = fetch_json("%s/artist?%s" % (MB_API, urllib.parse.urlencode({
        "query": query, "limit": 5, "fmt": "json",
    })))
    if not data:
        return None, [], False
    wanted = fold(name)
    for artist in data.get("artists") or []:
        got = fold(artist.get("name") or "")
        # Misma cautela que pickAnchorArtist() en la app: igualdad de
        # nombre, o de conjunto de palabras (S024). Antes ningun dato
        # que el de otro artista.
        if got != wanted and set(got.split()) != set(wanted.split()):
            continue
        country = artist.get("country")
        area = ((artist.get("area") or {}).get("name") or "")
        if not country and area:
            country = "ES" if fold(area) in ("spain", "espana") else None
        genres = [
            g.get("name") for g in (artist.get("genres") or [])
            if g.get("name")
        ]
        if not genres:
            genres = [
                t.get("name") for t in (artist.get("tags") or [])
                if t.get("name") and (t.get("count") or 0) > 0
            ]
        return country, land(genres), True
    return None, [], False


def discogs(name, year, token):
    """Estilos del artista, con los filtros anti-homonimo del sondeo."""
    if not token:
        return []
    headers = {"Authorization": "Discogs token=%s" % token}
    search = fetch_json("%s/database/search?%s" % (DISCOGS_API, urllib.parse.urlencode({
        "q": name, "type": "artist", "per_page": 5,
    })), headers)
    if not search:
        return []
    wanted = fold(name)
    artist_id = None
    for hit in search.get("results") or []:
        if fold(hit.get("title") or "") == wanted:
            artist_id = hit.get("id")
            break
    if not artist_id:
        return []
    time.sleep(DISCOGS_DELAY)
    releases = fetch_json("%s/artists/%s/releases?%s" % (
        DISCOGS_API, artist_id, urllib.parse.urlencode({"per_page": 50, "sort": "year"})
    ), headers)
    if not releases:
        return []
    own = [r for r in (releases.get("releases") or []) if (r.get("role") or "Main") == "Main"]
    years = [
        int(r["year"]) for r in own
        if str(r.get("year") or "").isdigit() and int(r["year"]) > 1900
    ]
    if years and not any(year - 15 <= y <= year + 15 for y in years):
        return []
    styles = Counter()
    examined = 0
    for release in own:
        if examined >= 8:
            break
        main_id = release.get("main_release") or release.get("id")
        if not main_id:
            continue
        time.sleep(DISCOGS_DELAY)
        detail = fetch_json("%s/releases/%s" % (DISCOGS_API, main_id), headers)
        if not detail:
            continue
        formats = " ".join(
            "%s %s" % (f.get("name") or "", " ".join(f.get("descriptions") or []))
            for f in detail.get("formats") or []
        ).lower()
        if "compilation" in formats:
            continue
        credited = {(a.get("name") or "").strip() for a in detail.get("artists") or []}
        if len(credited) > 3:
            continue
        examined += 1
        for style in detail.get("styles") or []:
            styles[style] += 1
    return land([s for s, _ in styles.most_common()])


def main():
    global _TREE, _BY_KEY
    with open(TREE_PATH, encoding="utf-8") as handle:
        _TREE = {
            k.lower().strip(): v
            for k, v in (json.load(handle).get("genres") or {}).items()
        }
    _BY_KEY = {tree_key(k): k for k in _TREE}

    with open(RAW_PATH, encoding="utf-8") as handle:
        harvest = json.load(handle)

    # Un artista aparece en muchas canciones; se resuelve UNA vez. El
    # ano que se usa para el contraste de epoca es el primero en que
    # fue numero uno.
    first_year = {}
    display = {}
    for row in harvest:
        key = fold(row["artist"])
        if key not in first_year or row["year"] < first_year[key]:
            first_year[key] = row["year"]
        display.setdefault(key, row["artist"])
    print("Artistas a resolver: %d\n" % len(first_year), flush=True)

    # Lo que ya sabemos del diccionario actual no se vuelve a pedir.
    with open(DICT_PATH, encoding="utf-8") as handle:
        dictionary = json.load(handle)
    already = {}
    for decade, block in dictionary.items():
        for origin in ("es", "intl"):
            for entry in block.get(origin) or []:
                genres = entry.get("genres") or []
                if genres:
                    already.setdefault(fold(entry["artist"]), (origin, genres))
    print("Reutilizables del diccionario actual: %d\n" % len(already), flush=True)

    token = (os.environ.get("DISCOGS_TOKEN") or "").strip()
    if not token:
        print("AVISO: sin DISCOGS_TOKEN. Solo MusicBrainz.\n", flush=True)

    out = {}
    stats = Counter()
    for position, (key, year) in enumerate(sorted(first_year.items(), key=lambda x: x[1]), 1):
        name = display[key]
        if key in already:
            origin, genres = already[key]
            out[name] = {"country": "ES" if origin == "es" else None,
                         "genres": genres, "via": "diccionario"}
            stats["diccionario"] += 1
            continue

        country, genres, found = musicbrainz(name)
        time.sleep(MB_DELAY)
        via = "musicbrainz" if genres else None
        if not genres:
            genres = discogs(name, year, token)
            if genres:
                via = "discogs"
        if genres:
            stats[via] += 1
        elif found:
            stats["sin_genero"] += 1
        else:
            stats["sin_artista"] += 1
        out[name] = {"country": country, "genres": genres, "via": via or "nada"}

        if position % 25 == 0:
            print("[%d/%d] %s -> pais=%s generos=%d (%s)"
                  % (position, len(first_year), name[:28], country, len(genres), via),
                  flush=True)

    with open(OUT_PATH, "w", encoding="utf-8") as handle:
        json.dump(out, handle, ensure_ascii=False, indent=1, sort_keys=True)
        handle.write("\n")

    con_genero = sum(1 for v in out.values() if v["genres"])
    concretos = sum(
        1 for v in out.values()
        if any(is_specific(tree_key(g)) for g in v["genres"])
    )
    espanoles = sum(1 for v in out.values() if v["country"] == "ES")
    print("\n--- ENRIQUECIDO ---\n", flush=True)
    print("Artistas resueltos:        %d" % len(out))
    print("  con algun genero:        %d" % con_genero)
    print("  con genero CONCRETO:     %d" % concretos)
    print("  identificados como ES:   %d" % espanoles)
    print()
    print("Via:", dict(stats))
    if con_genero < len(out) // 4:
        print("\nERROR: cobertura anormalmente baja, revisar antes de fusionar.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

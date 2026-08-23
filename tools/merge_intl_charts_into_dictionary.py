#!/usr/bin/env python3
"""
Fusiona en el diccionario las canciones INTERNACIONALES cosechadas de
Los 40 -- gemelo de merge_charts_into_dictionary.py (que solo fusiona
espanolas), construido a peticion explicita de Miguel Angel (2026-08-23)
para el problema real de HOY: seleccion de decada sola en miMooutCast
demasiado dominada por temas espanoles, echando en falta clasicos
internacionales de la decada (Nirvana, Chemical Brothers, Rage Against
the Machine...) que ya estan en la cosecha pero nunca se fusionaron.

  fase 1  harvest_los40_charts.py   2121 canciones, 946 artistas
  fase 2  enrich_chart_artists.py   pais y genero de esos artistas
  fase 3  merge_charts_into_dictionary.py       -> bloque ES (ya ejecutada)
  fase 3b esto                                   -> bloque INTL

CRITERIOS DE ADMISION, mismos que la Fase 3 espanola salvo el pais:

  pais       el artista NO es espanol (country != "ES"), y el pais es
             CONOCIDO -- un artista sin pais identificado (country ==
             None) se descarta, igual de estricto que exigir "ES" en
             la fusion espanola: no se adivina.
  genero     la entrada tiene que llevar al menos un genero CONCRETO,
             mismo criterio exacto que la Fase 3 (matchesGenre() la
             descartaria sin eso).
  duplicado  ni el mismo artista+cancion que ya este, ni dos veces la
             misma cancion de la cosecha.

La decada sale del ANO en que la cancion fue numero uno, misma regla
de S023 que la Fase 3.
"""

import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict

DICT_PATH = "app/src/main/assets/known_hit_artists.json"
TREE_PATH = "app/src/main/assets/genre_tree.json"
RAW_PATH = "tools/chart_los40_raw.json"
ENRICHED_PATH = "tools/chart_los40_enriched.json"
REJECTED_PATH = "tools/chart_los40_intl_rejected.json"

MAX_DESCENDANTS = 25
FIRST_DECADE = 1960
LAST_DECADE = 2020


def fold(value):
    stripped = unicodedata.normalize("NFKD", value)
    stripped = "".join(c for c in stripped if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", stripped).strip().lower()


def tree_key(value):
    folded = fold(value).replace("&", " and ").replace("-", " ").replace("/", " ")
    folded = re.sub(r"\s+", " ", folded).strip()
    return folded[:-6].strip() if folded.endswith(" music") else folded


def load_tree():
    with open(TREE_PATH, encoding="utf-8") as handle:
        nodes = {
            k.lower().strip(): v
            for k, v in (json.load(handle).get("genres") or {}).items()
        }
    cache = {}

    def descendants(genre):
        key = genre.lower().strip()
        if key in cache:
            return cache[key]
        found, pending = set(), [key]
        while pending:
            current = pending.pop()
            for child in (nodes.get(current) or {}).get("children") or []:
                child = child.lower().strip()
                if child not in found:
                    found.add(child)
                    pending.append(child)
        cache[key] = found
        return found

    return nodes, {tree_key(k): k for k in nodes}, descendants


def main():
    nodes, by_key, descendants = load_tree()

    def is_specific(key):
        real = by_key.get(key)
        return bool(real) and len(descendants(real)) <= MAX_DESCENDANTS

    try:
        with open(RAW_PATH, encoding="utf-8") as handle:
            harvest = json.load(handle)
        with open(ENRICHED_PATH, encoding="utf-8") as handle:
            enriched = json.load(handle)
    except FileNotFoundError as error:
        print("ERROR: falta %s. Hay que lanzar antes las fases 1 y 2." % error.filename)
        return 1

    by_fold = {fold(name): data for name, data in enriched.items()}
    print("Cosecha: %d canciones. Enriquecidos: %d artistas.\n"
          % (len(harvest), len(enriched)), flush=True)

    with open(DICT_PATH, encoding="utf-8") as handle:
        dictionary = json.load(handle)

    existing_songs = set()
    for decade, block in dictionary.items():
        for origin in ("es", "intl"):
            for entry in block.get(origin) or []:
                existing_songs.add((fold(entry["artist"]), fold(entry.get("song", ""))))

    added = 0
    seen = set()
    rejected = Counter()
    rejected_names = defaultdict(set)
    per_decade = Counter()
    for row in harvest:
        key = fold(row["artist"])
        data = by_fold.get(key)
        if not data:
            rejected["sin enriquecer"] += 1
            rejected_names["sin enriquecer"].add(row["artist"])
            continue
        country = data.get("country")
        if country is None or country == "ES":
            rejected["espanol o pais desconocido"] += 1
            continue
        genres = [g for g in (data.get("genres") or []) if tree_key(g) in by_key]
        if not any(is_specific(tree_key(g)) for g in genres):
            rejected["sin genero concreto"] += 1
            rejected_names["sin genero concreto"].add(row["artist"])
            continue
        song_key = (key, fold(row["song"]))
        if song_key in existing_songs or song_key in seen:
            rejected["ya estaba"] += 1
            continue
        decade = max(FIRST_DECADE, min(LAST_DECADE, (row["year"] // 10) * 10))
        seen.add(song_key)
        primary = next(g for g in genres if is_specific(tree_key(g)))
        dictionary.setdefault(str(decade), {"es": [], "intl": []})
        dictionary[str(decade)].setdefault("intl", []).append({
            "artist": row["artist"],
            "genre": primary,
            "genres": genres,
            "country": country,
            "song": row["song"],
        })
        added += 1
        per_decade[decade] += 1

    for block in dictionary.values():
        for origin in ("es", "intl"):
            if block.get(origin):
                block[origin].sort(key=lambda e: (fold(e["artist"]), fold(e.get("song", ""))))

    with open(DICT_PATH, "w", encoding="utf-8") as handle:
        json.dump(dictionary, handle, ensure_ascii=False, indent=1, sort_keys=True)
        handle.write("\n")
    with open(REJECTED_PATH, "w", encoding="utf-8") as handle:
        json.dump({k: sorted(v) for k, v in rejected_names.items()},
                  handle, ensure_ascii=False, indent=1, sort_keys=True)
        handle.write("\n")

    total_intl = sum(len(b.get("intl") or []) for b in dictionary.values())

    print("--- FUSION INTL ---\n", flush=True)
    print("Canciones internacionales anadidas: %d" % added)
    print("Bloque INTL: %d entradas (antes %d)" % (total_intl, total_intl - added))
    print()
    print("%-8s %10s" % ("decada", "anadidas"))
    for decade in sorted(per_decade):
        block_size = len(dictionary[str(decade)]["intl"])
        print("%-8d %10d   -> bloque queda en %d" % (decade, per_decade[decade], block_size))
    print()
    print("Descartadas:", dict(rejected))

    if added == 0:
        print("\nERROR: no se anadio ninguna cancion.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

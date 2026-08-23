#!/usr/bin/env python3
"""
Fusiona en el diccionario las canciones ESPANOLAS cosechadas de Los 40.

Fase 3 y ultima de la ampliacion (H08, S024).

  fase 1  harvest_los40_charts.py   2121 canciones, 946 artistas
  fase 2  enrich_chart_artists.py   pais y genero de esos artistas
  fase 3  esto                      fusion en known_hit_artists.json

DECISION DE MIGUEL ANGEL: solo espanoles. Los 40 mezcla los dos
mundos, pero el bloque internacional ya va holgado (mediana 26 frente a
12 del espanol) y meter ahi 1200 canciones mas seria trabajo sin
retorno. Tamano del asset: preguntado y descartado como problema.

POR QUE ESTO ARREGLA LO QUE NO ARREGLABA EL MOTOR. Medido
analiticamente sobre los pools reales, escalando el bloque ES con la
misma calidad de generos que ya tiene:

    bloque ES                        mediana   con <5 candidatos
    hoy: 352 entradas, ~50/decada        12           9%
    x4:  ~200/decada                     43           4%
    x8:  ~400/decada                     87           0%

El objetivo de S023 -- mediana ~15, menos del 5% -- no lo alcanzo el
enriquecimiento de generos (movio la mediana de 7 a 12 tras cuatro
pasadas de Discogs). Lo alcanza el volumen, sin tocar el motor.

CRITERIOS DE ADMISION, y ninguno es negociable:

  pais       el artista tiene que ser espanol. Sale de MusicBrainz, o
             de que ya estuviera en el bloque `es` del diccionario.
  genero     la entrada tiene que llevar al menos un genero CONCRETO.
             Una entrada sin generos la descarta matchesGenre() y no
             aporta ni un candidato; una con solo carpetas raiz no
             sirve para anclar -- es la leccion de Radio Futura, que
             tenia `['rock']` y estrangulaba su propia radio.
  duplicado  ni el mismo artista+cancion que ya este, ni dos veces la
             misma cancion de la cosecha.

La decada sale del ANO en que la cancion fue numero uno, no de la vida
del artista. Es la regla de Miguel Angel de S023 -- la decada la marca
el tema -- aplicada aqui de forma natural, porque la lista da
exactamente eso.

El campo `genre` se rellena con el primer genero concreto, para que las
entradas nuevas tengan la misma forma que las viejas: RadioRepository
lo usa como termino unico de busqueda en MusicBrainz.
"""

import json
import os
import re
import sys
import unicodedata
from collections import Counter, defaultdict

DICT_PATH = "app/src/main/assets/known_hit_artists.json"
TREE_PATH = "app/src/main/assets/genre_tree.json"
RAW_PATH = os.environ.get("CHART_RAW_PATH", "tools/chart_los40_raw.json")
ENRICHED_PATH = os.environ.get("CHART_ENRICHED_PATH", "tools/chart_los40_enriched.json")
REJECTED_PATH = os.environ.get("CHART_REJECTED_PATH", "tools/chart_los40_rejected.json")

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


def simulate(dictionary, nodes, descendants):
    """Cada entrada como ancla contra su pool de decada+origen.

    Replica matchesGenre() de KnownHitsRepository -- mismo metodo que
    produjo las cifras de S023 y de todas las medidas de esta sesion,
    para que el antes y el despues sean comparables.
    """
    def spec(genre):
        return len(descendants(genre)) <= MAX_DESCENDANTS

    def share_parent(a, b):
        pa = [p.lower().strip() for p in (nodes.get(a) or {}).get("parents") or []]
        pb = {p.lower().strip() for p in (nodes.get(b) or {}).get("parents") or []}
        return any(p in pb and 1 <= len(descendants(p)) <= MAX_DESCENDANTS for p in pa)

    def matches(hits, anchors):
        if not hits or not anchors:
            return False
        if any(h in anchors and spec(h) for h in hits):
            return True
        descendable = [a for a in anchors if 1 <= len(descendants(a)) <= MAX_DESCENDANTS]
        if any(h in descendants(a) for a in descendable for h in hits):
            return True
        if any(share_parent(a, h) for a in anchors for h in hits):
            return True
        if not any(spec(a) for a in anchors):
            return any(h in anchors for h in hits)
        return False

    by_bucket = defaultdict(list)
    for decade, block in dictionary.items():
        for origin in ("es", "intl"):
            for entry in block.get(origin) or []:
                genres = {
                    g.lower().strip()
                    for g in (entry.get("genres") or [entry.get("genre")])
                    if g
                }
                by_bucket[(int(decade), origin)].append((entry["artist"], genres))

    counts = {"es": [], "intl": []}
    for (decade, origin), rows in by_bucket.items():
        for artist, genres in rows:
            counts[origin].append(
                sum(1 for other, hits in rows if other != artist and matches(hits, genres))
            )

    def median(values):
        ordered = sorted(values)
        return ordered[len(ordered) // 2] if ordered else 0

    every = counts["es"] + counts["intl"]
    return {
        "es": median(counts["es"]),
        "intl": median(counts["intl"]),
        "under5": sum(1 for n in every if n < 5),
        "total": len(every),
    }


def show(label, stats):
    print("  %-10s es_mediana=%4d  intl_mediana=%4d  con<5=%4d (%2.0f%%)" % (
        label, stats["es"], stats["intl"],
        stats["under5"], 100.0 * stats["under5"] / max(stats["total"], 1),
    ), flush=True)


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
    before = simulate(dictionary, nodes, descendants)

    # Lo que ya esta, para no duplicar. Se compara plegado.
    existing_songs = set()
    spanish_already = set()
    for decade, block in dictionary.items():
        for entry in block.get("es") or []:
            spanish_already.add(fold(entry["artist"]))
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
        # Espanol: por MusicBrainz, o porque ya estaba en el bloque es.
        if data.get("country") != "ES" and key not in spanish_already:
            rejected["no espanol"] += 1
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
        dictionary[str(decade)].setdefault("es", []).append({
            "artist": row["artist"],
            "genre": primary,
            "genres": genres,
            "song": row["song"],
        })
        added += 1
        per_decade[decade] += 1

    # Orden estable dentro de cada bloque, para que el diff sea legible
    # en las fusiones siguientes.
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

    after = simulate(dictionary, nodes, descendants)
    total_es = sum(len(b.get("es") or []) for b in dictionary.values())

    print("--- FUSION ---\n", flush=True)
    print("Canciones espanolas anadidas: %d" % added)
    print("Bloque ES: %d entradas (antes %d)" % (total_es, total_es - added))
    print()
    print("%-8s %10s" % ("decada", "anadidas"))
    for decade in sorted(per_decade):
        block_size = len(dictionary[str(decade)]["es"])
        print("%-8d %10d   -> bloque queda en %d" % (decade, per_decade[decade], block_size))
    print()
    print("Descartadas:", dict(rejected))
    print()
    print("Simulacion sobre pools reales:", flush=True)
    show("ANTES", before)
    show("DESPUES", after)
    print()
    print("Objetivo de S023: mediana ES ~15, menos del 5% con <5 candidatos.")

    if added == 0:
        print("\nERROR: no se anadio ninguna cancion.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

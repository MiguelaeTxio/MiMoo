#!/usr/bin/env python3
"""
Rellena el conjunto de generos de las entradas ESPANOLAS que no lo
tienen, con los estilos de Discogs medidos por probe_genre_sources.py.

Motivacion (H08, S024). Medido al cierre de S023: 151 de las 352
entradas espanolas (43%) no tienen conjunto de generos porque
MusicBrainz apenas cataloga a esos artistas -- en el lado internacional
solo pasa en 5 de 425. Consecuencia: una radio anclada en un espanol se
agota, con mediana de 7 candidatos frente a 26 en el internacional.

FUENTE ELEGIDA POR MIGUEL ANGEL tras el sondeo de las tres candidatas
sobre esos mismos 125 artistas:

    fuente      cobertura   riqueza   CONCRETOS
    discogs      108/125       5.1     107/125
    wikipedia     65/125       2.6      49/125
    wikidata      71/125       1.7      45/125

'Concretos' es la cifra que decide: artistas que reciben al menos una
etiqueta que el arbol reconoce y que NO es carpeta raiz. Wikidata
cubria 71 artistas pero casi todo con 'pop music' y 'rock music', que
caen en las raices y no discriminan nada -- cubrir a alguien con 'pop'
lo deja igual que estaba.

DEL CAMPO 'style', NUNCA 'genre'. El 'genre' de Discogs es demasiado
grueso (Latin, Rock, Pop, Folk); el que sirve es 'style', que da
Flamenco, Rumba, Synth-pop, Indie Pop.

QUE NO HACE. No inventa ningun genero. Solo escribe etiquetas que
vienen de Discogs Y que existen en genre_tree.json -- lo que no
aterriza en el arbol se descarta, porque matchesGenre() no sabria
cruzarlo. Restriccion de S023: el genero no lo inventa el modelo.

QUE CONSERVA. El campo 'genre' original de cada entrada se mantiene
intacto, igual que hizo enrich_dictionary_genres.py en S023:
RadioRepository lo necesita como termino unico de busqueda en
MusicBrainz y romperlo no aporta nada.

SEPARACION DE RESPONSABILIDADES. Este script NO consulta Discogs: lee
tools/genre_sources_probe.json, que es la salida ya medida y
commiteada del sondeo. Para refrescar el dato se relanza el sondeo, no
esto. Asi el relleno es determinista y revisable en el diff.
"""

import json
import re
import sys
import unicodedata
from collections import Counter

DICT_PATH = "app/src/main/assets/known_hit_artists.json"
TREE_PATH = "app/src/main/assets/genre_tree.json"
PROBE_PATH = "tools/genre_sources_probe.json"
MANUAL_PATH = "tools/spanish_genres_manual.json"
LEFTOVER_PATH = "tools/spanish_genres_leftover.json"

MAX_DESCENDANTS = 25


def fold(value):
    for fancy, plain in (
        ("\u2019", "'"), ("\u2018", "'"), ("\u02bc", "'"),
        ("\u2010", "-"), ("\u2011", "-"), ("\u2012", "-"),
        ("\u2013", "-"), ("\u2014", "-"),
    ):
        value = value.replace(fancy, plain)
    stripped = unicodedata.normalize("NFKD", value)
    stripped = "".join(c for c in stripped if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", stripped).strip().lower()


def tree_key(value):
    """Misma normalizacion que probe_genre_sources.py, para que lo que
    el sondeo conto como 'aterriza' sea exactamente lo que aqui se
    escribe. Si las dos difieren, las cifras del sondeo dejan de
    describir el resultado."""
    folded = fold(value).replace("&", " and ")
    folded = folded.replace("-", " ").replace("/", " ")
    folded = re.sub(r"\s+", " ", folded).strip()
    if folded.endswith(" music"):
        folded = folded[:-len(" music")].strip()
    return folded


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
        found = set()
        pending = [key]
        while pending:
            current = pending.pop()
            for child in (nodes.get(current) or {}).get("children") or []:
                child = child.lower().strip()
                if child not in found:
                    found.add(child)
                    pending.append(child)
        cache[key] = found
        return found

    by_key = {tree_key(k): k for k in nodes}
    return nodes, by_key, descendants


def simulate(dictionary, nodes, descendants):
    """Cada entrada como ancla contra su pool de decada+origen.

    Replica matchesGenre() de KnownHitsRepository. Es el mismo metodo
    que produjo las cifras de S023, para que el antes y el despues sean
    comparables.
    """
    def spec(g):
        return len(descendants(g)) <= MAX_DESCENDANTS

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

    entries = []
    for decade, block in dictionary.items():
        for origin in ("es", "intl"):
            for entry in block.get(origin) or []:
                genres = {
                    g.lower().strip()
                    for g in (entry.get("genres") or [entry.get("genre")])
                    if g
                }
                entries.append((int(decade), origin, entry["artist"], genres))

    counts = {"es": [], "intl": []}
    for decade, origin, artist, genres in entries:
        pool = [
            e for e in entries
            if e[0] == decade and e[1] == origin and e[2] != artist
        ]
        counts[origin].append(sum(1 for e in pool if matches(e[3], genres)))

    def median(values):
        ordered = sorted(values)
        return ordered[len(ordered) // 2] if ordered else 0

    every = counts["es"] + counts["intl"]
    return {
        "es_median": median(counts["es"]),
        "intl_median": median(counts["intl"]),
        "under5": sum(1 for n in every if n < 5),
        "total": len(every),
    }


def show(label, stats):
    print("  %-14s es_mediana=%3d  intl_mediana=%3d  con<5=%3d (%2.0f%%)" % (
        label, stats["es_median"], stats["intl_median"],
        stats["under5"], 100.0 * stats["under5"] / max(stats["total"], 1),
    ), flush=True)


def compatible(styles, coarse, by_key, nodes, descendants):
    """¿Los estilos de Discogs pegan con el genero grueso de la entrada?

    S024 -- ultimo colador contra homonimos, y no cuesta red. El
    contraste de epoca del sondeo caza al homonimo de OTRA epoca
    (Formula V, Taburete, Los Canarios, Rayden quedaron vacios), pero
    no al que publica a la vez:

        Chanel    entrada 'pop'  -> Discogs [house, garage house, electro]
        Los Pecos entrada 'pop'  -> Discogs [cumbia, guaracha, psychedelic]

    Chanel Terrero canta pop y Los Pecos eran un duo de baladas. Los
    que ha encontrado Discogs son otros.

    El campo `genre` de cada entrada esta escrito a mano y es grueso,
    pero acierta en la FAMILIA, que es justo lo que hace falta aqui. Se
    exige que al menos un estilo de Discogs sea ese genero, cuelgue de
    el, lo contenga, o sea hermano suyo. 'house' no cuelga de 'pop' por
    ningun lado; 'garage house' tampoco.

    Si la entrada no trae genero grueso utilizable, no se bloquea nada:
    grueso antes que falso, pero tampoco se inventa una sospecha.
    """
    coarse_real = by_key.get(tree_key(coarse or ""))
    if not coarse_real:
        return True
    def parents(genre):
        return [p.lower().strip() for p in (nodes.get(genre) or {}).get("parents") or []]
    coarse_parents = set(parents(coarse_real))
    for style in styles:
        real = by_key.get(tree_key(style))
        if not real:
            continue
        if real == coarse_real:
            return True
        if real in descendants(coarse_real) or coarse_real in descendants(real):
            return True
        if coarse_parents & set(parents(real)):
            return True
    return False


def merge(existing, landed):
    """Une lo que ya habia con lo nuevo, sin duplicar y sin perder nada.

    S024 -- las entradas del tipo `['rock']` ya traian una etiqueta
    correcta, solo que inservible por si sola. No se sustituye: se
    conserva y se le anaden las concretas, que es lo que le faltaba.
    """
    out = list(existing)
    for genre in landed:
        if genre not in out:
            out.append(genre)
    return out


def main():
    nodes, by_key, descendants = load_tree()

    def is_specific(key):
        """La etiqueta existe en el arbol Y no es carpeta raiz.

        Mismo tope de 25 descendientes que GenreTree en la app. Es lo
        que separa un genero que sirve para anclar de uno que no:
        'rock' existe, pero con 129 descendientes no discrimina nada.
        """
        real = by_key.get(key)
        return bool(real) and len(descendants(real)) <= MAX_DESCENDANTS
    print("Arbol de generos: %d etiquetas.\n" % len(nodes), flush=True)

    try:
        with open(PROBE_PATH, encoding="utf-8") as handle:
            probe = json.load(handle).get("artists") or {}
    except FileNotFoundError:
        print("ERROR: falta %s. Hay que lanzar antes el sondeo "
              "(workflow 'Sondear fuentes de generos')." % PROBE_PATH)
        return 1
    if not probe:
        print("ERROR: el informe del sondeo esta vacio.")
        return 1
    with_styles = sum(1 for r in probe.values() if r.get("discogs"))
    print("Sondeo: %d artistas, %d con estilos de Discogs.\n"
          % (len(probe), with_styles), flush=True)
    if not with_styles:
        print("ERROR: el sondeo no trae ningun estilo de Discogs. "
              "Se relanza el sondeo con DISCOGS_TOKEN presente.")
        return 1

    # S024 -- los cinco que Discogs resolvia como homonimo y que ni el
    # contraste de epoca ni el de pais lograron descartar. Sus generos
    # NO los inventa el modelo: cada uno viene con su fuente citada en
    # el propio archivo. Tienen precedencia sobre Discogs.
    try:
        with open(MANUAL_PATH, encoding="utf-8") as handle:
            manual = {
                k: v for k, v in json.load(handle).items()
                if not k.startswith("_")
            }
    except FileNotFoundError:
        manual = {}
    print("Resueltos a mano con fuente citada: %d\n" % len(manual), flush=True)

    with open(DICT_PATH, encoding="utf-8") as handle:
        dictionary = json.load(handle)

    before = simulate(dictionary, nodes, descendants)

    filled = 0
    filled_artists = set()
    leftover = {}
    suspicious = {}
    manual_used = set()
    dropped = Counter()
    for decade, block in sorted(dictionary.items()):
        for entry in block.get("es") or []:
            existing = entry.get("genres") or []
            # S024 -- mismo criterio que probe_genre_sources.py: lo que
            # descalifica a una entrada como ancla no es no tener
            # generos, es no tener ninguno CONCRETO. `['rock']` es un
            # conjunto, pero como ancla no sirve para nada.
            if existing and any(is_specific(tree_key(g)) for g in existing):
                continue
            artist = entry["artist"]
            override = manual.get(artist)
            if override:
                landed = [
                    by_key[tree_key(g)] for g in override["generos"]
                    if tree_key(g) in by_key
                ]
                if landed:
                    entry["genres"] = merge(existing, landed)
                    filled += 1
                    filled_artists.add(artist)
                    manual_used.add(artist)
                    continue
            styles = (probe.get(artist) or {}).get("discogs") or []
            landed = []
            for style in styles:
                real = by_key.get(tree_key(style))
                if real:
                    if real not in landed:
                        landed.append(real)
                else:
                    dropped[style] += 1
            if not landed:
                leftover.setdefault(artist, {
                    "decade": int(decade),
                    "genre": entry.get("genre", ""),
                    "discogsRaw": styles,
                    "generosActuales": existing,
                    "motivo": "Discogs no da ningun estilo que exista en el arbol",
                })
                continue
            entry["genres"] = merge(existing, landed)
            filled += 1
            filled_artists.add(artist)

    with open(DICT_PATH, "w", encoding="utf-8") as handle:
        json.dump(dictionary, handle, ensure_ascii=False, indent=1, sort_keys=True)
        handle.write("\n")

    with open(LEFTOVER_PATH, "w", encoding="utf-8") as handle:
        json.dump({"sin_dato": leftover, "sospechosos": suspicious},
                  handle, ensure_ascii=False, indent=1, sort_keys=True)
        handle.write("\n")

    after = simulate(dictionary, nodes, descendants)

    still_empty = sum(
        1 for block in dictionary.values()
        for entry in block.get("es") or []
        if not entry.get("genres")
    )
    total_es = sum(len(block.get("es") or []) for block in dictionary.values())

    print("--- RESULTADO ---\n", flush=True)
    print("Entradas ES rellenadas:        %d" % filled)
    print("  ...de Discogs:               %d" % (len(filled_artists) - len(manual_used)))
    print("  ...resueltos a mano:         %d (%s)"
          % (len(manual_used), ", ".join(sorted(manual_used)) or "-"))
    print("Artistas distintos:            %d" % len(filled_artists))
    print("Entradas ES aun sin conjunto:  %d de %d" % (still_empty, total_es))
    print("Artistas sin dato aprovechable: %d" % len(leftover))
    print("Apartados por sospecha de homonimo: %d" % len(suspicious))
    print("  (los dos listados en %s)" % LEFTOVER_PATH)
    if suspicious:
        print()
        print("Apartados -- NO se les ha escrito nada:")
        for name, info in sorted(suspicious.items()):
            print("   %-24s entrada='%s'  Discogs=%s"
                  % (name, info["genre"], ", ".join(info["discogsLanded"])))
    print()
    print("Simulacion sobre pools reales:", flush=True)
    show("ANTES", before)
    show("DESPUES", after)
    print()
    if dropped:
        print("Estilos de Discogs descartados por no estar en el arbol:")
        print("  ", ", ".join("%s(%d)" % (s, n) for s, n in dropped.most_common(12)))

    if filled == 0:
        print("\nERROR: no se relleno ninguna entrada.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

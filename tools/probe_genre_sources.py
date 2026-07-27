#!/usr/bin/env python3
"""
SONDEO: que fuente de generos cubre mejor el bloque espanol.

Motivacion (H08, S024). Medido al cierre de S023: de las 352 entradas
espanolas del diccionario, 151 (43%) no tienen conjunto de generos
porque MusicBrainz apenas cataloga a esos artistas. En el lado
internacional solo pasa en 5 de 425 (1%). Consecuencia directa: una
radio anclada en un espanol se queda sin candidatos y se agota --
mediana de 6-11 candidatos frente a 21-40 en el internacional.

No falta volumen de diccionario: falta DATO DE GENERO justo donde mas
se necesita. Anadir mas entradas espanolas sin resolver esto solo
anadiria mas 'pop'.

RESTRICCION HEREDADA DE S023, NO NEGOCIABLE: el genero no lo inventa
el modelo. Si el dato no esta en una fuente, se pregunta; no se rellena
a ojo. Fue el origen de GENRE_FAMILIES y de todo el trabajo de S023.
De ahi que esto sea un SONDEO y no un relleno: mide fuentes, no
escribe nada en el diccionario.

QUE MIDE, POR FUENTE

  cobertura   cuantos de los 151 reciben al menos un genero
  riqueza     media de generos por artista resuelto
  aterrizaje  que porcentaje de las etiquetas devueltas existe en
              genre_tree.json

El tercero es el que decide, y es el que no se ve a simple vista.
matchesGenre() cruza contra el arbol de MusicBrainz: una etiqueta que
no este en el arbol no sirve para nada, por muy correcta que sea
musicalmente. Una fuente con mucha cobertura y vocabulario propio
puede valer menos que otra con menos cobertura y vocabulario
compatible.

FUENTES SONDEADAS

  wikidata    propiedad P136 (genre). Publica, sin clave.
  wikipedia   campo 'genero'/'genre' de la infobox, es y en.
              Publica, sin clave.
  discogs     campo 'style' agregado sobre los lanzamientos del
              artista. REQUIERE token (variable DISCOGS_TOKEN). Si no
              hay token se salta y se avisa, en vez de fallar.

POR QUE DESDE ACTIONS. Igual que build_genre_tree.py y
enrich_dictionary_genres.py: el runner tiene salida a Internet sin
restricciones. No hay ningun paso local.
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

DICT_PATH = "app/src/main/assets/known_hit_artists.json"
TREE_PATH = "app/src/main/assets/genre_tree.json"
REPORT_PATH = "tools/genre_sources_probe.json"

USER_AGENT = "MiMoo-GenreProbe/1.0 ( https://github.com/MiguelaeTxio/MiMoo )"
DELAY_SECONDS = 0.4
MAX_RETRIES = 4

WIKIDATA_API = "https://www.wikidata.org/w/api.php"
DISCOGS_API = "https://api.discogs.com"


def fold(value):
    """Normaliza para comparar nombres: sin tildes, sin mayusculas.

    Misma intencion que fold() de enrich_dictionary_genres.py -- las
    fuentes escriben con tipografia fina y sin unificarla se descartan
    artistas correctos.
    """
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
    """Clave con la que se comprueba si una etiqueta esta en el arbol.

    Se pliegan acentos, guiones y '&' porque las fuentes escriben
    'Pop-rock' o 'Rock & Roll' donde MusicBrainz escribe 'pop rock' y
    'rock and roll'. Sin esto el aterrizaje se subestima por
    tipografia, no por vocabulario, y la comparacion entre fuentes
    dejaria de ser justa.
    """
    folded = fold(value).replace("&", " and ")
    folded = folded.replace("-", " ").replace("/", " ")
    return re.sub(r"\s+", " ", folded).strip()


def fetch_json(url, headers=None):
    """Descarga JSON reintentando ante fallo transitorio.

    Un 503 no es 'este artista no tiene generos', es 'ahora no'.
    Confundir ambas cosas es lo que costo la sesion S022.
    """
    last_error = None
    for attempt in range(1, MAX_RETRIES + 1):
        merged = {"User-Agent": USER_AGENT}
        merged.update(headers or {})
        request = urllib.request.Request(url, headers=merged)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.loads(response.read().decode("utf-8", errors="replace"))
        except urllib.error.HTTPError as error:
            last_error = error
            if error.code in (429, 500, 502, 503, 504):
                backoff = DELAY_SECONDS * (2 ** attempt)
                print("    HTTP %d, reintento %d/%d en %.1fs"
                      % (error.code, attempt, MAX_RETRIES, backoff), flush=True)
                time.sleep(backoff)
                continue
            return None
        except (urllib.error.URLError, TimeoutError, OSError, ValueError) as error:
            last_error = error
            backoff = DELAY_SECONDS * (2 ** attempt)
            time.sleep(backoff)
    print("    agotados los reintentos: %s" % last_error, flush=True)
    return None


# --------------------------------------------------------------- wikidata

def wikidata_genres(name):
    """Generos via P136 del item de Wikidata que corresponda al artista.

    Se acepta el item cuya etiqueta case con el nombre buscado -- misma
    cautela que pickAnchorArtist() en la app: antes ningun dato que el
    de otro artista.
    """
    search = fetch_json("%s?%s" % (WIKIDATA_API, urllib.parse.urlencode({
        "action": "wbsearchentities", "search": name, "language": "es",
        "uselang": "es", "type": "item", "limit": 10, "format": "json",
    })))
    if not search:
        return None, []
    wanted = fold(name)
    qid = None
    for hit in search.get("search") or []:
        if fold(hit.get("label") or "") == wanted:
            qid = hit.get("id")
            break
    if not qid:
        return None, []

    entity = fetch_json("%s?%s" % (WIKIDATA_API, urllib.parse.urlencode({
        "action": "wbgetentities", "ids": qid, "props": "claims", "format": "json",
    })))
    if not entity:
        return qid, []
    claims = ((entity.get("entities") or {}).get(qid) or {}).get("claims") or {}
    genre_qids = []
    for claim in claims.get("P136") or []:
        value = (((claim.get("mainsnak") or {}).get("datavalue") or {}).get("value") or {})
        if isinstance(value, dict) and value.get("id"):
            genre_qids.append(value["id"])
    if not genre_qids:
        return qid, []

    labels = fetch_json("%s?%s" % (WIKIDATA_API, urllib.parse.urlencode({
        "action": "wbgetentities", "ids": "|".join(genre_qids[:40]),
        "props": "labels", "languages": "en", "format": "json",
    })))
    if not labels:
        return qid, []
    out = []
    for qid_genre in genre_qids:
        entry = (labels.get("entities") or {}).get(qid_genre) or {}
        label = ((entry.get("labels") or {}).get("en") or {}).get("value")
        if label:
            out.append(label)
    return qid, out


# -------------------------------------------------------------- wikipedia

INFOBOX_GENRE = re.compile(
    r"^\s*\|\s*(?:g[eé]nero|genre)s?\s*=\s*(.+)$", re.IGNORECASE | re.MULTILINE
)
WIKILINK = re.compile(r"\[\[([^\]|]+)(?:\|[^\]]*)?\]\]")


def wikipedia_genres(name, lang):
    """Campo 'genero'/'genre' de la infobox, leyendo el wikitexto.

    Se extraen solo los enlaces internos del campo: el texto suelto que
    los acompana ('influencias de', notas entre parentesis) no es
    vocabulario, es prosa.
    """
    api = "https://%s.wikipedia.org/w/api.php" % lang
    data = fetch_json("%s?%s" % (api, urllib.parse.urlencode({
        "action": "query", "prop": "revisions", "rvprop": "content",
        "rvslots": "main", "titles": name, "redirects": 1, "format": "json",
    })))
    if not data:
        return []
    pages = ((data.get("query") or {}).get("pages") or {})
    for page in pages.values():
        if "missing" in page:
            continue
        revisions = page.get("revisions") or []
        if not revisions:
            continue
        content = (((revisions[0].get("slots") or {}).get("main") or {}).get("*")) or ""
        match = INFOBOX_GENRE.search(content)
        if not match:
            continue
        field = match.group(1)
        found = [g.strip() for g in WIKILINK.findall(field) if g.strip()]
        if found:
            return found
        # Sin enlaces: se acepta texto plano corto separado por comas.
        plain = re.sub(r"\{\{[^}]*\}\}", " ", field)
        parts = [p.strip(" ,.;") for p in re.split(r"[,/]| y ", plain)]
        return [p for p in parts if p and len(p) < 40]
    return []


# ---------------------------------------------------------------- discogs

def discogs_styles(name, token):
    """Conjunto de 'style' agregado sobre los lanzamientos del artista.

    El campo 'genre' de Discogs es demasiado grueso (Latin, Rock, Pop);
    el que sirve es 'style'. Se agrega sobre varios lanzamientos porque
    en Discogs el estilo vive en el disco, no en el artista -- y esa
    agregacion es justo la forma de dato que consume matchesGenre().
    """
    headers = {"Authorization": "Discogs token=%s" % token}
    search = fetch_json("%s/database/search?%s" % (DISCOGS_API, urllib.parse.urlencode({
        "q": name, "type": "artist", "per_page": 5,
    })), headers)
    if not search:
        return None, []
    wanted = fold(name)
    artist_id = None
    for hit in search.get("results") or []:
        if fold(hit.get("title") or "") == wanted:
            artist_id = hit.get("id")
            break
    if not artist_id:
        return None, []

    time.sleep(DELAY_SECONDS)
    releases = fetch_json("%s/artists/%s/releases?%s" % (
        DISCOGS_API, artist_id, urllib.parse.urlencode({"per_page": 25, "sort": "year"})
    ), headers)
    if not releases:
        return artist_id, []

    styles = Counter()
    for release in (releases.get("releases") or [])[:12]:
        main_id = release.get("main_release") or release.get("id")
        if not main_id or release.get("type") not in (None, "master", "release"):
            continue
        time.sleep(DELAY_SECONDS)
        detail = fetch_json("%s/releases/%s" % (DISCOGS_API, main_id), headers)
        if not detail:
            continue
        for style in detail.get("styles") or []:
            styles[style] += 1
    return artist_id, [s for s, _ in styles.most_common()]


# ------------------------------------------------------------------- main

def preflight(tree, token):
    """Comprueba cada fuente con un caso conocido antes de las 151."""
    ok = True
    print("Comprobacion previa (Los Chichos)...", flush=True)

    _, wd = wikidata_genres("Los Chichos")
    print("  wikidata  -> %s" % (wd or "sin generos"), flush=True)

    wp = wikipedia_genres("Los Chichos", "es") or wikipedia_genres("Los Chichos", "en")
    print("  wikipedia -> %s" % (wp or "sin generos"), flush=True)
    if not wd and not wp:
        print("  ERROR: ninguna fuente publica respondio. Revisar endpoints.")
        ok = False

    if token:
        _, dg = discogs_styles("Los Chichos", token)
        print("  discogs   -> %s" % (dg or "sin estilos"), flush=True)
    else:
        print("  discogs   -> SALTADO (falta el secret DISCOGS_TOKEN)", flush=True)
    print(flush=True)
    return ok


def main():
    with open(TREE_PATH, encoding="utf-8") as handle:
        tree = {tree_key(k) for k in (json.load(handle).get("genres") or {})}
    print("Arbol de generos: %d etiquetas.\n" % len(tree), flush=True)

    with open(DICT_PATH, encoding="utf-8") as handle:
        dictionary = json.load(handle)

    targets = []
    for decade, block in sorted(dictionary.items()):
        for entry in block.get("es") or []:
            if not entry.get("genres"):
                targets.append((int(decade), entry["artist"]))
    # Un artista puede aparecer en varias decadas: se sondea una vez.
    seen = set()
    unique = []
    for decade, artist in targets:
        if artist in seen:
            continue
        seen.add(artist)
        unique.append((decade, artist))
    print("%d entradas ES sin conjunto de generos, %d artistas distintos.\n"
          % (len(targets), len(unique)), flush=True)

    token = (os.environ.get("DISCOGS_TOKEN") or "").strip()
    if not preflight(tree, token):
        return 1

    sources = ["wikidata", "wikipedia", "discogs"]
    results = {}
    for position, (decade, artist) in enumerate(unique, 1):
        print("[%d/%d] %s (%d)" % (position, len(unique), artist, decade), flush=True)
        row = {"decade": decade}

        _, wd = wikidata_genres(artist)
        row["wikidata"] = wd
        time.sleep(DELAY_SECONDS)

        wp = wikipedia_genres(artist, "es")
        if not wp:
            time.sleep(DELAY_SECONDS)
            wp = wikipedia_genres(artist, "en")
        row["wikipedia"] = wp
        time.sleep(DELAY_SECONDS)

        if token:
            _, dg = discogs_styles(artist, token)
            row["discogs"] = dg
            time.sleep(DELAY_SECONDS)
        else:
            row["discogs"] = []

        results[artist] = row
        print("    wikidata=%-2d wikipedia=%-2d discogs=%-2d"
              % (len(row["wikidata"]), len(row["wikipedia"]), len(row["discogs"])),
              flush=True)

    print("\n--- RESUMEN DEL SONDEO ---\n", flush=True)
    print("%-11s %10s %9s %12s %10s" %
          ("fuente", "cobertura", "riqueza", "aterrizaje", "utiles"), flush=True)
    summary = {}
    for source in sources:
        if source == "discogs" and not token:
            print("%-11s %10s" % (source, "SALTADO -- sin DISCOGS_TOKEN"), flush=True)
            summary[source] = {"skipped": True}
            continue
        covered = [a for a, r in results.items() if r[source]]
        labels = [g for r in results.values() for g in r[source]]
        landed = [g for g in labels if tree_key(g) in tree]
        # 'utiles' = artistas que reciben al menos una etiqueta que el
        # arbol reconoce. Es la cifra que de verdad mueve la Radio:
        # cubrir a un artista con vocabulario ajeno no le sirve de nada.
        useful = [
            a for a, r in results.items()
            if any(tree_key(g) in tree for g in r[source])
        ]
        summary[source] = {
            "covered": len(covered), "total": len(unique),
            "labels": len(labels), "landed": len(landed), "useful": len(useful),
        }
        print("%-11s %6d/%-3d %9.1f %8d/%-4d %6d/%-3d" % (
            source, len(covered), len(unique),
            (len(labels) / len(covered)) if covered else 0.0,
            len(landed), len(labels), len(useful), len(unique),
        ), flush=True)

    print("\nEtiquetas mas frecuentes que NO estan en el arbol:", flush=True)
    for source in sources:
        if source == "discogs" and not token:
            continue
        missing = Counter(
            g for r in results.values() for g in r[source] if tree_key(g) not in tree
        )
        top = ", ".join("%s(%d)" % (g, n) for g, n in missing.most_common(8))
        print("  %-10s %s" % (source, top or "-- ninguna"), flush=True)

    with open(REPORT_PATH, "w", encoding="utf-8") as handle:
        json.dump({"summary": summary, "artists": results},
                  handle, ensure_ascii=False, indent=1, sort_keys=True)
        handle.write("\n")
    print("\nDetalle por artista en %s" % REPORT_PATH, flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())

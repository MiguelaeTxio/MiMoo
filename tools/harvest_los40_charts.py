#!/usr/bin/env python3
"""
COSECHA: los numeros uno historicos de Los 40 Principales (Espana).

Motivacion (H08, S024). Observacion de Miguel Angel al ver que cada
prueba destapaba un caso nuevo: *"esta forma de probar, caso no
contemplado, implementar caso, probar, caso no contemplado, implementar
caso, conlleva a una implementacion eterna"*. Y tenia razon. El
problema de fondo no es que falten casos en el filtro: es que el pool
es tan pequeno que CUALQUIER exigencia del filtro lo deja seco.

Medido analiticamente sobre los pools reales, escalando el bloque ES y
manteniendo la misma calidad de generos que hoy:

    bloque ES                        mediana   con <5 candidatos
    hoy: 352 entradas, ~50/decada        10          18%
    x2:  ~100/decada                     21           9%
    x4:  ~200/decada                     43           4%
    x8:  ~400/decada                     87           0%

El objetivo fijado al cerrar S023 era mediana ~15 y menos del 5% con
menos de 5 candidatos. Se alcanza SOLO con volumen, sin tocar el
motor ni una linea. Para comparar: todo el enriquecimiento de generos
via Discogs de esta sesion movio la mediana de 7 a 12.

FUENTE. Wikipedia ES publica una pagina por ano desde 1966:

    Anexo:Los numeros uno de Los 40 Principales (Espana) {ano}

La propia enciclopedia declara mas de dos mil canciones numero uno en
60 anos de lista. Cubre los dos bloques a la vez: en Los 40 conviven
Nirvana y Los Rodriguez, Bon Jovi y Sergio Dalma.

AVISO IMPORTANTE -- ESTO SOLO ES LA MITAD DEL TRABAJO. Una entrada de
lista trae artista, tema y ano, pero NO genero. Y `matchesGenre()`
descarta toda entrada cuyo conjunto de generos este vacio, asi que una
cancion sin enriquecer no aporta ni un candidato. La tabla de arriba
asume que las nuevas entradas llevan generos de calidad parecida a las
actuales. Por eso esto es la FASE 1: cosechar y medir. La fase 2 es
enriquecer los artistas nuevos con la misma maquinaria de Discogs que
ya existe, y solo entonces fusionar.

Este script NO toca known_hit_artists.json. Deja la cosecha cruda y un
informe, para decidir con cifras.
"""

import json
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict

OUT_RAW = "tools/chart_los40_raw.json"
OUT_REPORT = "tools/chart_los40_report.json"
DICT_PATH = "app/src/main/assets/known_hit_artists.json"

API = "https://es.wikipedia.org/w/api.php"
PAGE = "Anexo:Los números uno de Los 40 Principales (España) %d"
FIRST_YEAR = 1966
LAST_YEAR = 2025

USER_AGENT = "MiMoo-ChartHarvest/1.0 ( https://github.com/MiguelaeTxio/MiMoo )"
DELAY_SECONDS = 0.3
MAX_RETRIES = 4

# Enlaces que aparecen en las filas pero no son ni artista ni cancion.
NOISE = re.compile(
    r"^(?:\d{1,2}\s+de\s+\w+|\d{4}|categor[ií]a|anexo|los\s*40|espa[nñ]a|"
    r"n[uú]mero\s*uno|sencillo|[eé]xito)",
    re.IGNORECASE,
)


def fold(value):
    stripped = unicodedata.normalize("NFKD", value)
    stripped = "".join(c for c in stripped if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", stripped).strip().lower()


def fetch_json(url):
    last_error = None
    for attempt in range(1, MAX_RETRIES + 1):
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.loads(response.read().decode("utf-8", errors="replace"))
        except urllib.error.HTTPError as error:
            last_error = error
            if error.code in (429, 500, 502, 503, 504):
                time.sleep(DELAY_SECONDS * (2 ** attempt))
                continue
            return None
        except (urllib.error.URLError, TimeoutError, OSError, ValueError) as error:
            last_error = error
            time.sleep(DELAY_SECONDS * (2 ** attempt))
    print("    agotados los reintentos: %s" % last_error, flush=True)
    return None


def wikitext(title):
    data = fetch_json("%s?%s" % (API, urllib.parse.urlencode({
        "action": "query", "prop": "revisions", "rvprop": "content",
        "rvslots": "main", "titles": title, "redirects": 1, "format": "json",
    })))
    if not data:
        return None
    for page in ((data.get("query") or {}).get("pages") or {}).values():
        if "missing" in page:
            return None
        revisions = page.get("revisions") or []
        if revisions:
            return (((revisions[0].get("slots") or {}).get("main") or {}).get("*")) or ""
    return None


CELL_SPLIT = re.compile(r"\|\||\n\s*\|(?!\})")
LINK = re.compile(r"\[\[([^\]|]+)(?:\|([^\]]*))?\]\]")
QUOTED = re.compile(r"[«\"“'']([^»\"”'']{2,60})[»\"”'']")


def clean(value):
    value = re.sub(r"\{\{[^}]*\}\}", " ", value)
    value = re.sub(r"<[^>]*>", " ", value)
    value = re.sub(r"'{2,}", "", value)
    value = value.replace("[[", "").replace("]]", "")
    return re.sub(r"\s+", " ", value).strip(" |-—–\t")


def parse_year(text, year):
    """Saca (cancion, artista) de las filas de las tablas del ano.

    Las paginas no comparten un unico formato a lo largo de 60 anos, asi
    que en vez de asumir columnas fijas se recorre cada fila y se toman
    sus ENLACES: en estas tablas la cancion y el artista van enlazados
    casi siempre. Cuando la cancion no lleva enlace pero si comillas
    («Titulo»), se recoge de ahi.

    Se devuelve tambien el recuento de filas sin interpretar, para que
    el informe diga que se ha perdido en vez de fingir que no se pierde
    nada.
    """
    rows = []
    unparsed = 0
    for block in re.findall(r"\{\|.*?\n\|\}", text, re.DOTALL):
        if "wikitable" not in block[:200]:
            continue
        for row in block.split("|-"):
            if "||" not in row and "\n|" not in row:
                continue
            links = [
                (target.strip(), (label or target).strip())
                for target, label in LINK.findall(row)
            ]
            links = [
                (t, l) for t, l in links
                if t and not NOISE.match(t) and len(t) > 1
            ]
            song = artist = None
            if len(links) >= 2:
                song, artist = links[0][1], links[1][1]
            elif len(links) == 1:
                quoted = QUOTED.search(row)
                if quoted:
                    song, artist = clean(quoted.group(1)), links[0][1]
            if not song or not artist:
                if row.count("|") >= 2 and len(row.strip()) > 20:
                    unparsed += 1
                continue
            song, artist = clean(song), clean(artist)
            if song and artist and len(artist) < 60 and len(song) < 90:
                rows.append({"song": song, "artist": artist, "year": year})
    return rows, unparsed


def main():
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
    per_year = {}
    missing_pages = []
    unparsed_total = 0
    for year in range(FIRST_YEAR, LAST_YEAR + 1):
        text = wikitext(PAGE % year)
        if text is None:
            missing_pages.append(year)
            print("[%d] pagina no encontrada" % year, flush=True)
            time.sleep(DELAY_SECONDS)
            continue
        rows, unparsed = parse_year(text, year)
        # Un mismo tema puede figurar en varias semanas.
        seen = set()
        unique = []
        for row in rows:
            key = (fold(row["artist"]), fold(row["song"]))
            if key in seen:
                continue
            seen.add(key)
            unique.append(row)
        harvest += unique
        per_year[year] = {"canciones": len(unique), "filas_sin_interpretar": unparsed}
        unparsed_total += unparsed
        print("[%d] %3d canciones  (%d filas sin interpretar)"
              % (year, len(unique), unparsed), flush=True)
        time.sleep(DELAY_SECONDS)

    # Dedup global: la misma cancion puede repetir numero uno en dos anos.
    seen = set()
    unique = []
    for row in sorted(harvest, key=lambda r: r["year"]):
        key = (fold(row["artist"]), fold(row["song"]))
        if key in seen:
            continue
        seen.add(key)
        unique.append(row)

    by_decade = Counter((r["year"] // 10) * 10 for r in unique)
    artists = {fold(r["artist"]) for r in unique}
    nuevos = artists - known
    art_by_decade = defaultdict(set)
    for row in unique:
        art_by_decade[(row["year"] // 10) * 10].add(fold(row["artist"]))

    print("\n--- COSECHA ---\n", flush=True)
    print("Canciones distintas:            %d" % len(unique))
    print("Artistas distintos:             %d" % len(artists))
    print("  ...que YA estan en el dicc.:  %d" % (len(artists) - len(nuevos)))
    print("  ...NUEVOS a enriquecer:       %d" % len(nuevos))
    print("Paginas no encontradas:         %d %s"
          % (len(missing_pages), missing_pages[:12]))
    print("Filas sin interpretar:          %d" % unparsed_total)
    print()
    print("%-10s %12s %12s %14s" % ("decada", "canciones", "artistas", "dicc. ES hoy"))
    hoy = {
        int(decade): len(block.get("es") or [])
        for decade, block in dictionary.items()
    }
    for decade in sorted(by_decade):
        print("%-10d %12d %12d %14s"
              % (decade, by_decade[decade], len(art_by_decade[decade]),
                 hoy.get(decade, "-")))

    with open(OUT_RAW, "w", encoding="utf-8") as handle:
        json.dump(unique, handle, ensure_ascii=False, indent=1)
        handle.write("\n")
    with open(OUT_REPORT, "w", encoding="utf-8") as handle:
        json.dump({
            "canciones": len(unique),
            "artistas": len(artists),
            "artistas_nuevos": sorted(nuevos),
            "por_ano": per_year,
            "paginas_no_encontradas": missing_pages,
            "filas_sin_interpretar": unparsed_total,
        }, handle, ensure_ascii=False, indent=1, sort_keys=True)
        handle.write("\n")

    print("\nMuestra de lo cosechado, para revisar que el parseo no desvaria:")
    for row in unique[:8] + unique[len(unique) // 2:len(unique) // 2 + 8]:
        print("   %d  %-34s %s" % (row["year"], row["artist"][:34], row["song"][:44]))

    if len(unique) < 300:
        print("\nERROR: cosecha anormalmente corta. El parseo no encaja con el "
              "formato de las paginas; revisar antes de usar nada de esto.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

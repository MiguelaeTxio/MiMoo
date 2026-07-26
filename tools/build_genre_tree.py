#!/usr/bin/env python3
"""
Construye el arbol de generos y subgeneros a partir de MusicBrainz.

Motivacion (H08, S023). Hasta ahora la pertenencia de un tema al genero
del ancla se decidia con GENRE_FAMILIES: sacos de generos escritos a
mano en KnownHitsRepository.kt. Eso metia criterio propio del modelo en
el motor de la Radio, y producia cruces falsos -- 'new wave' acabo en
el mismo saco que 'post-punk', y por esa arista Tears for Fears entraba
en una radio de Dead Can Dance.

MusicBrainz ya distingue lo que ese saco confundia. En la ficha de cada
genero hay dos tipos de enlace radicalmente distintos:

  - 'subgenre of' / 'subgenres'  -> PARENTESCO. Es el arbol real.
  - 'influenced by' / 'influenced genres' -> INFLUENCIA. Se parecen,
    pero NO son de la misma rama.

Ejemplo real, ficha de 'dark wave':
    subgenres:          ethereal wave, neoclassical dark wave,
                        neue deutsche todeskunst
    influenced by:      new wave, synth-pop
    influenced genres:  new beat, steampunk, witch house

'new wave' NO es la carpeta padre de 'dark wave': solo la influyo, y en
la misma casilla que 'synth-pop'. Recorrer aristas de influencia es
justo el error que este archivo viene a eliminar.

Este script guarda LOS DOS tipos de arista. Quien decide cuales se
recorren es el codigo de la app, no este generador: el dato se captura
entero y la politica se aplica despues. Asi se puede cambiar el
criterio sin volver a rastrear.

IMPORTANTE -- por que se rastrea el HTML y no la API. La documentacion
oficial de MusicBrainz dice que las relaciones estan disponibles para
todos los tipos de entidad EXCEPTO los generos. El dato existe en la
base de datos y se ve en la web, pero /ws/2 no lo expone. De ahi que
haya que leer las fichas HTML una a una.

Se ejecuta a mano desde GitHub Actions (.github/workflows/genre-tree.yml),
no en cada push: son miles de peticiones a 1/segundo. El resultado se
commitea como asset de la app, de modo que el arbol viaja dentro del
APK y sigue funcionando con MusicBrainz caido -- que es justo cuando el
diccionario local es lo unico que sostiene la Radio.
"""

import html
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

BASE = "https://musicbrainz.org"
LIST_URL = BASE + "/genres"
OUT_PATH = "app/src/main/assets/genre_tree.json"

# MusicBrainz exige un User-Agent identificable y como maximo una
# peticion por segundo. 1.1 deja margen para no rozar el limite.
USER_AGENT = "MiMoo-GenreTree/1.0 ( https://github.com/MiguelaeTxio/MiMoo )"
DELAY_SECONDS = 1.1
MAX_RETRIES = 5

# Etiqueta de la fila en la ficha -> clave en el JSON de salida.
# Las dos primeras son parentesco; el resto NO lo son y quedan
# guardadas aparte precisamente para no confundirlas nunca mas.
RELATION_LABELS = {
    "subgenre of": "parents",
    "subgenres": "children",
    "fusion of": "fusionOf",
    "has fusion genres": "fusionInto",
    "influenced by": "influencedBy",
    "influenced genres": "influenced",
}

ROW_RE = re.compile(r"<th[^>]*>(.*?)</th>\s*<td[^>]*>(.*?)</td>", re.S | re.I)
GENRE_LINK_RE = re.compile(
    r'href="/genre/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"[^>]*>(.*?)</a>',
    re.S | re.I,
)
TAG_RE = re.compile(r"<[^>]+>")


def strip_tags(fragment):
    return html.unescape(TAG_RE.sub("", fragment)).strip()


def fetch(url):
    """Descarga una pagina reintentando ante fallo transitorio.

    Un 503 o un 429 de MusicBrainz no significa 'no hay dato', significa
    'ahora no'. Tratarlos como respuesta definitiva es exactamente el
    fallo que costo la sesion S022 en el motor de Radio; aqui se trata
    igual de en serio.
    """
    last_error = None
    for attempt in range(1, MAX_RETRIES + 1):
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return response.read().decode("utf-8", errors="replace")
        except urllib.error.HTTPError as error:
            last_error = error
            if error.code in (429, 500, 502, 503, 504):
                backoff = DELAY_SECONDS * (2 ** attempt)
                print(
                    "    HTTP %d, reintento %d/%d en %.1fs"
                    % (error.code, attempt, MAX_RETRIES, backoff),
                    flush=True,
                )
                time.sleep(backoff)
                continue
            raise
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            last_error = error
            backoff = DELAY_SECONDS * (2 ** attempt)
            print(
                "    red: %s, reintento %d/%d en %.1fs"
                % (error, attempt, MAX_RETRIES, backoff),
                flush=True,
            )
            time.sleep(backoff)
    raise RuntimeError("agotados los reintentos para %s: %s" % (url, last_error))


def collect_genre_index():
    """Lee /genres y devuelve {mbid: nombre} de todos los generos."""
    page = fetch(LIST_URL)
    index = {}
    for mbid, raw_name in GENRE_LINK_RE.findall(page):
        name = strip_tags(raw_name)
        if name:
            index[mbid] = name
    return index


def parse_relationships(page):
    """Extrae las relaciones genero-genero de una ficha."""
    found = {key: [] for key in RELATION_LABELS.values()}
    for raw_label, cell in ROW_RE.findall(page):
        label = strip_tags(raw_label).rstrip(":").strip().lower()
        key = RELATION_LABELS.get(label)
        if key is None:
            continue
        for _, raw_name in GENRE_LINK_RE.findall(cell):
            name = strip_tags(raw_name)
            if name and name not in found[key]:
                found[key].append(name)
    return found


# Ficha de control. Si el HTML de MusicBrainz cambia, el rastreo
# entero devolveria un arbol vacio tras una hora larga de peticiones.
# Se comprueba antes de empezar contra un caso cuyo contenido conocemos
# -- y no uno cualquiera: es exactamente el caso que motivo todo esto.
PREFLIGHT_MBID = "c4a72fcd-e291-43c8-a546-9ce1764ff31b"  # dark wave
PREFLIGHT_EXPECTED = {
    "children": "ethereal wave",
    "influencedBy": "new wave",
}


def preflight():
    print("Comprobacion previa sobre la ficha de 'dark wave'...", flush=True)
    page = fetch("%s/genre/%s" % (BASE, PREFLIGHT_MBID))
    found = parse_relationships(page)
    for key, expected in PREFLIGHT_EXPECTED.items():
        if expected not in found.get(key, []):
            print(
                "ERROR: se esperaba '%s' en '%s' y no aparece.\n"
                "El HTML de MusicBrainz ha cambiado: hay que revisar\n"
                "parse_relationships() antes de rastrear nada."
                % (expected, key)
            )
            return False
    print(
        "  OK -- parentesco y influencia se leen por separado "
        "(children=%s, influencedBy=%s).\n" % (found["children"], found["influencedBy"]),
        flush=True,
    )
    return True


def main():
    if not preflight():
        return 1
    time.sleep(DELAY_SECONDS)

    print("Leyendo el indice de generos de MusicBrainz...", flush=True)
    index = collect_genre_index()
    total = len(index)
    if total == 0:
        print("ERROR: el indice vino vacio, MusicBrainz cambio el HTML.")
        return 1
    print("%d generos en el indice.\n" % total, flush=True)

    genres = {}
    failures = []
    for position, (mbid, name) in enumerate(sorted(index.items(), key=lambda item: item[1]), 1):
        print("[%d/%d] %s" % (position, total, name), flush=True)
        try:
            page = fetch("%s/genre/%s" % (BASE, mbid))
        except RuntimeError as error:
            print("    FALLO DEFINITIVO: %s" % error, flush=True)
            failures.append(name)
            genres[name] = {"mbid": mbid, "incomplete": True}
            time.sleep(DELAY_SECONDS)
            continue

        node = {"mbid": mbid}
        node.update(
            {key: value for key, value in parse_relationships(page).items() if value}
        )
        genres[name] = node
        time.sleep(DELAY_SECONDS)

    with_parent = sum(1 for node in genres.values() if node.get("parents"))
    with_child = sum(1 for node in genres.values() if node.get("children"))
    isolated = sum(
        1
        for node in genres.values()
        if not node.get("parents") and not node.get("children")
    )
    influence_only = sum(
        1
        for node in genres.values()
        if not node.get("parents")
        and not node.get("children")
        and (node.get("influencedBy") or node.get("influenced"))
    )

    payload = {
        "source": "musicbrainz.org (fichas HTML de genero)",
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "note": (
            "parents/children son PARENTESCO (subgenre of / subgenres). "
            "influencedBy/influenced NO lo son y no deben recorrerse para "
            "decidir pertenencia al genero del ancla."
        ),
        "stats": {
            "total": total,
            "withParent": with_parent,
            "withChild": with_child,
            "isolated": isolated,
            "isolatedButInfluenceLinked": influence_only,
            "failed": len(failures),
        },
        "genres": genres,
    }

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=1, sort_keys=True)
        handle.write("\n")

    print("\n--- RESUMEN ---", flush=True)
    print("Generos totales:               %d" % total)
    print("Con carpeta padre:             %d" % with_parent)
    print("Con subgeneros:                %d" % with_child)
    print("Sin parentesco alguno:         %d" % isolated)
    print("  ...pero con influencias:     %d" % influence_only)
    print("Fichas fallidas:               %d" % len(failures))
    if failures:
        print("Fallidas: %s" % ", ".join(failures[:20]))
    print("\nEscrito en %s" % OUT_PATH)

    if len(failures) > total * 0.02:
        print("\nERROR: mas del 2%% de fichas fallidas, el arbol no es fiable.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

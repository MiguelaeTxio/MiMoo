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

    S024 -- se retira ademas el sufijo ' music', convencion de
    Wikidata ('pop music', 'rock music', 'folk music'). AVISO medido en
    la primera pasada: esto sube la cobertura bruta de Wikidata de 46 a
    66 artistas y los que reciben una etiqueta CONCRETA solo de 44 a
    45, porque casi todo lo que entra por aqui es 'pop' y 'rock', las
    dos carpetas raiz. Se normaliza por correccion, no porque ayude.
    """
    folded = fold(value).replace("&", " and ")
    folded = folded.replace("-", " ").replace("/", " ")
    folded = re.sub(r"\s+", " ", folded).strip()
    if folded.endswith(" music"):
        folded = folded[:-len(" music")].strip()
    return folded


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
    r"^\s*\|\s*(?:g[eé]nero|genre)s?\s*=\s*(.*?)(?=^\s*\|\s*\w|^\s*\}\})",
    re.IGNORECASE | re.MULTILINE | re.DOTALL,
)
WIKILINK = re.compile(r"\[\[([^\]|]+)(?:\|[^\]]*)?\]\]")


def wikipedia_genres(name, lang):
    """Campo 'genero'/'genre' de la infobox, leyendo el wikitexto.

    Se extraen solo los enlaces internos del campo: el texto suelto que
    los acompana ('influencias de', notas entre parentesis) no es
    vocabulario, es prosa.

    S024 -- dos correcciones tras la primera pasada del sondeo:

    1. El campo suele empezar por una plantilla de lista
       (`{{flatlist|`, `{{hlist|`) y continuar en las lineas
       siguientes. Capturando una sola linea, el 'genero' que salia era
       literalmente la cadena '{{flatlist|' -- 14 veces en 125
       artistas. Ahora se captura hasta el siguiente campo o el cierre
       de la infobox, y se retiran los nombres de plantilla.
    2. Las etiquetas de es.wikipedia vienen en castellano ('Rock
       alternativo', 'Pop latino') y el arbol esta en ingles. NO se
       traducen a mano: se resuelve el titulo ingles del propio
       articulo enlazado via langlinks, que es dato de Wikipedia y no
       criterio del modelo.
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
        if not found:
            # Sin enlaces: texto plano, ya sin plantillas ni marcas.
            plain = re.sub(r"\{\{[^}]*\}\}", " ", field)
            plain = re.sub(r"<[^>]*>", " ", plain).replace("*", " ")
            parts = [p.strip(" ,.;|") for p in re.split(r"[,/\n]| y ", plain)]
            found = [p for p in parts if p and 2 < len(p) < 40]
        found = [g for g in found if not g.lower().startswith(("flatlist", "hlist", "plainlist"))]
        if lang == "es":
            found = [translate_to_english(g) for g in found]
        return [g for g in found if g]
    return []


_LANGLINK_CACHE = {}


def translate_to_english(title):
    """Titulo ingles de un articulo de es.wikipedia, via langlinks.

    'Rock alternativo' -> 'Alternative rock'. Si no hay enlace de
    idioma se devuelve el titulo original: grueso antes que falso.
    """
    if title in _LANGLINK_CACHE:
        return _LANGLINK_CACHE[title]
    data = fetch_json("https://es.wikipedia.org/w/api.php?%s" % urllib.parse.urlencode({
        "action": "query", "prop": "langlinks", "lllang": "en", "titles": title,
        "redirects": 1, "format": "json",
    }))
    result = title
    if data:
        for page in ((data.get("query") or {}).get("pages") or {}).values():
            for link in page.get("langlinks") or []:
                if link.get("*"):
                    result = link["*"]
    _LANGLINK_CACHE[title] = result
    time.sleep(DELAY_SECONDS)
    return result


# ---------------------------------------------------------------- discogs

def discogs_styles(name, token, decade=None):
    """Conjunto de 'style' agregado sobre los lanzamientos del artista.

    El campo 'genre' de Discogs es demasiado grueso (Latin, Rock, Pop);
    el que sirve es 'style'. Se agrega sobre varios lanzamientos porque
    en Discogs el estilo vive en el disco, no en el artista -- y esa
    agregacion es justo la forma de dato que consume matchesGenre().

    S024 -- HOMONIMOS. Casar el nombre exacto no basta, igual que no
    bastaba en MusicBrainz (fue todo el trabajo de S023). Con solo el
    nombre salian:

        Chanel     -> house, garage house, grime  (es pop, Eurovision)
        Formula V  -> funk, disco, electro        (grupo beat de 1960)
        Los Pecos  -> cumbia, guaracha            (duo de baladas)

    Se exige ademas que el artista PUBLIQUE en la epoca de la entrada:
    al menos un disco propio dentro de [decada-15, decada+15]. No es
    criterio musical -- es la misma idea que la regla de Miguel Angel
    de que la decada la marca el tema, aplicada a verificar identidad.
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
        DISCOGS_API, artist_id, urllib.parse.urlencode({"per_page": 50, "sort": "year"})
    ), headers)
    if not releases:
        return artist_id, []

    # S024, fallo real detectado al revisar el primer relleno. Sin
    # filtrar aqui, el listado de un artista incluye TODO aquello donde
    # aparece, y de ahi salian disparates:
    #
    #   Taburete       -> merengue, guaguanco, calypso, candombe...
    #   Antonio Molina -> techno, makina, euro house...
    #
    # Taburete es pop rock y Antonio Molina cantaba coplas y murio en
    # 1992. Los estilos no eran suyos: venian de recopilatorios de
    # varios interpretes donde salia un tema suyo, y el disco entero
    # aporta sus estilos al Counter.
    #
    #   role  -- 'Main' es obra propia. 'Appearance', 'TrackAppearance'
    #            y 'Remix' son discos de otros.
    #   comp  -- ademas se descartan los formatos marcados como
    #            recopilacion, que agregan estilos de terceros aunque
    #            el papel sea 'Main' (los 'grandes exitos de varios').
    own = [
        r for r in (releases.get("releases") or [])
        if (r.get("role") or "Main") == "Main"
    ]

    # Contraste de epoca. Si el artista que ha casado por nombre no
    # publica nada cerca de la decada de la entrada, no es el nuestro.
    if decade is not None:
        years = [
            int(r["year"]) for r in own
            if str(r.get("year") or "").isdigit() and int(r["year"]) > 1900
        ]
        if years and not any(decade - 15 <= y <= decade + 15 for y in years):
            print("    discogs: descartado por epoca (publica %d-%d, entrada %d)"
                  % (min(years), max(years), decade), flush=True)
            return artist_id, []

    # Contraste de PAIS. Los 125 artistas sondeados son espanoles por
    # construccion -- salen del bloque `es` del diccionario. Un artista
    # que ha casado por nombre pero no edita NUNCA en Espana no es el
    # nuestro. Caza al homonimo contemporaneo, que el contraste de
    # epoca no ve:
    #
    #   Chanel        entrada 'pop'     -> house, garage house, electro
    #   Leiva         entrada 'rock'    -> tech house, deep house, techno
    #   Natos y Waor  entrada 'hip hop' -> tech house, electro
    #   Los Pecos     entrada 'pop'     -> cumbia, guaracha (grupo latinoamericano)
    #
    # OJO -- el pais NO viene en el listado `/artists/{id}/releases`,
    # solo en el detalle de cada disco. En la pasada anterior este
    # contraste se hizo sobre el listado y quedo en nada: mismas cifras,
    # mismos homonimos. Se recoge aqui, dentro del bucle que ya pide los
    # detalles para los estilos, y se decide al terminar.
    #
    # Si NINGUN disco declara pais, no se castiga: puede ser una ficha
    # pobre y no un homonimo.
    styles = Counter()
    countries = set()
    examined = 0
    for release in own:
        if examined >= 12:
            break
        main_id = release.get("main_release") or release.get("id")
        if not main_id or release.get("type") not in (None, "master", "release"):
            continue
        time.sleep(DELAY_SECONDS)
        detail = fetch_json("%s/releases/%s" % (DISCOGS_API, main_id), headers)
        if not detail:
            continue
        formats = " ".join(
            "%s %s" % (f.get("name") or "", " ".join(f.get("descriptions") or []))
            for f in detail.get("formats") or []
        ).lower()
        if "compilation" in formats:
            continue
        # Un disco con muchos artistas distintos en los creditos es un
        # recopilatorio aunque no venga marcado como tal.
        credited = {
            (a.get("name") or "").strip()
            for a in detail.get("artists") or []
        }
        if len(credited) > 3:
            continue
        examined += 1
        country = (detail.get("country") or "").strip()
        if country:
            countries.add(country)
        for style in detail.get("styles") or []:
            styles[style] += 1

    if countries and not any(c.lower() == "spain" for c in countries):
        print("    discogs: descartado por pais (edita en %s, y la entrada es espanola)"
              % ", ".join(sorted(countries)[:5]), flush=True)
        return artist_id, []
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


_TREE_NODES = {}
_DESCENDANTS = {}
# Mismo tope que GenreTree.MAX_DESCENDANTS_TO_DESCEND en la app: por
# encima de 25 descendientes un genero es carpeta raiz y no significa
# nada como criterio musical.
MAX_DESCENDANTS = 25


def descendants(genre):
    key = genre.lower().strip()
    if key in _DESCENDANTS:
        return _DESCENDANTS[key]
    found = set()
    pending = [key]
    while pending:
        current = pending.pop()
        for child in (_TREE_NODES.get(current) or {}).get("children") or []:
            child = child.lower().strip()
            if child not in found:
                found.add(child)
                pending.append(child)
    _DESCENDANTS[key] = found
    return found


def is_specific(key):
    """La etiqueta existe en el arbol Y no es carpeta raiz."""
    real = _BY_KEY.get(key)
    if not real:
        return False
    return len(descendants(real)) <= MAX_DESCENDANTS


_BY_KEY = {}


def main():
    global _TREE_NODES, _BY_KEY
    with open(TREE_PATH, encoding="utf-8") as handle:
        _TREE_NODES = {
            k.lower().strip(): v
            for k, v in (json.load(handle).get("genres") or {}).items()
        }
    _BY_KEY = {tree_key(k): k for k in _TREE_NODES}
    tree = set(_BY_KEY)
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
            _, dg = discogs_styles(artist, token, decade)
            row["discogs"] = dg
            time.sleep(DELAY_SECONDS)
        else:
            row["discogs"] = []

        results[artist] = row
        print("    wikidata=%-2d wikipedia=%-2d discogs=%-2d"
              % (len(row["wikidata"]), len(row["wikipedia"]), len(row["discogs"])),
              flush=True)

    print("\n--- RESUMEN DEL SONDEO ---\n", flush=True)
    print("'concretos' = artistas que reciben al menos una etiqueta que", flush=True)
    print("el arbol reconoce Y que no es carpeta raiz. Es la unica cifra", flush=True)
    print("que mueve la Radio: cubrir a alguien con 'pop' no sirve.\n", flush=True)
    print("%-11s %10s %9s %12s %10s %11s" %
          ("fuente", "cobertura", "riqueza", "aterrizaje", "utiles", "concretos"),
          flush=True)
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
        concrete = [
            a for a, r in results.items()
            if any(is_specific(tree_key(g)) for g in r[source])
        ]
        summary[source] = {
            "covered": len(covered), "total": len(unique),
            "labels": len(labels), "landed": len(landed),
            "useful": len(useful), "concrete": len(concrete),
        }
        print("%-11s %6d/%-3d %9.1f %8d/%-4d %6d/%-3d %7d/%-3d" % (
            source, len(covered), len(unique),
            (len(labels) / len(covered)) if covered else 0.0,
            len(landed), len(labels), len(useful), len(unique),
            len(concrete), len(unique),
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

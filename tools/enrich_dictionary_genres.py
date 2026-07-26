#!/usr/bin/env python3
"""
Anade a cada entrada de known_hit_artists.json su CONJUNTO de generos
real, tomado de MusicBrainz.

Motivacion (H08, S023). El diccionario tiene 777 entradas y solo 27
generos distintos, escritos a mano. El reparto es inservible:

    pop   258      hip hop   44      electronic  36
    rock  165      pop rock  40      ...

'pop' y 'rock' se llevan 423 de 777, el 54%. En una taxonomia real
esas dos son carpetas raiz: lo contienen casi todo. Con la regla
acordada -- desde el ancla se baja a los descendientes, nunca se sube
al padre -- una entrada etiquetada solo como 'pop' no puede entrar en
ninguna radio especifica. Y eso es correcto: no quieres que un ancla
de Alaska y Dinarama arrastre a cualquiera etiquetado 'pop'.

La consecuencia es que el arbol de generos, por si solo, no arregla
nada: deja al descubierto que las etiquetas del diccionario son
demasiado gruesas. Fangoria no es 'pop'; es synth-pop, electropop,
new wave. Escrito como 'pop', el motor no puede saberlo.

De ahi este script. Cada entrada pasa a llevar 'genres' con lo que
MusicBrainz dice de verdad de ese artista. El campo 'genre' original
se CONSERVA: RadioRepository lo necesita como termino unico de
busqueda, y romperlo no aporta nada.

CONSISTENCIA CON LO QUE HACE LA APP. Se replica exactamente el camino
de RadioRepository.resolveAnchor():

    1. GET /ws/2/artist/?query=artist:"NOMBRE"&fmt=json&limit=5
       -> se toma el PRIMER resultado
    2. GET /ws/2/artist/{mbid}?inc=genres&fmt=json
       -> se leen los generos con su recuento

Si el rastreo offline resolviera los artistas por otro criterio, el
ancla y el diccionario no hablarian el mismo idioma.

RESOLUCIONES DUDOSAS. Quedarse con el primer resultado a ciegas es lo
unico que se puede hacer en tiempo real, pero esto se genera una sola
vez y se revisa. Por eso cada artista guarda el nombre que MusicBrainz
devolvio y su puntuacion, y se marca 'review': true cuando el nombre
no coincide o la puntuacion baja de 90. Esas son las que hay que mirar
a mano en el diff -- homonimos tipo 'Los Angeles'.
"""

import json
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://musicbrainz.org/ws/2"
DICT_PATH = "app/src/main/assets/known_hit_artists.json"
DISAMBIGUATION_PATH = "app/src/main/assets/artist_disambiguation.json"

USER_AGENT = "MiMoo-ArtistGenres/1.0 ( https://github.com/MiguelaeTxio/MiMoo )"
DELAY_SECONDS = 1.1
MAX_RETRIES = 5
SCORE_THRESHOLD = 90


def fold(value):
    """Normaliza para comparar nombres: sin tildes, sin mayusculas.

    MusicBrainz escribe los nombres con tipografia fina -- apostrofo
    curvo en 'Guns N'Roses', guion no ASCII en 'a-ha', 'Wu-Tang Clan',
    'blink-182', 'Run-D.M.C.'. Sin unificar esos caracteres, diez
    artistas correctos salian marcados como dudosos y enterraban a los
    que de verdad estaban mal resueltos.
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


def fetch_json(url):
    """Descarga JSON reintentando ante fallo transitorio.

    Un 503 no es 'este artista no tiene generos', es 'ahora no'.
    Confundir ambas cosas es lo que costo la sesion S022.
    """
    last_error = None
    for attempt in range(1, MAX_RETRIES + 1):
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
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
            raise
        except (urllib.error.URLError, TimeoutError, OSError, ValueError) as error:
            last_error = error
            backoff = DELAY_SECONDS * (2 ** attempt)
            print("    red: %s, reintento %d/%d en %.1fs"
                  % (error, attempt, MAX_RETRIES, backoff), flush=True)
            time.sleep(backoff)
    raise RuntimeError("agotados los reintentos para %s: %s" % (url, last_error))


def search_artist(name):
    """Mismo camino que RadioRepository.buildArtistQuery() + searchArtists()."""
    query = 'artist:"%s"' % name.replace('"', "")
    url = "%s/artist/?%s" % (
        BASE,
        urllib.parse.urlencode({"query": query, "fmt": "json", "limit": 5}),
    )
    hits = fetch_json(url).get("artists") or []
    return hits[0] if hits else None


def lookup_genres(mbid):
    """Mismo camino que RadioRepository -> lookupArtist(inc=genres)."""
    url = "%s/artist/%s?%s" % (
        BASE, mbid, urllib.parse.urlencode({"inc": "genres", "fmt": "json"})
    )
    genres = fetch_json(url).get("genres") or []
    ordered = sorted(
        (g for g in genres if (g.get("name") or "").strip()),
        key=lambda g: (-(g.get("count") or 0), (g.get("name") or "").lower()),
    )
    return [g["name"] for g in ordered]


def preflight():
    """Comprueba contra un caso conocido antes de rastrear 658 artistas."""
    print("Comprobacion previa con 'Joy Division'...", flush=True)
    hit = search_artist("Joy Division")
    if not hit:
        print("ERROR: la busqueda no devolvio nada. Revisar el endpoint.")
        return False
    time.sleep(DELAY_SECONDS)
    genres = lookup_genres(hit["id"])
    if not genres:
        print("ERROR: el lookup no devolvio generos. Revisar 'inc=genres'.")
        return False
    print("  OK -- %s -> %s\n" % (hit.get("name"), genres[:5]), flush=True)
    return True


def main():
    if not preflight():
        return 1
    time.sleep(DELAY_SECONDS)

    with open(DICT_PATH, encoding="utf-8") as handle:
        dictionary = json.load(handle)

    try:
        with open(DISAMBIGUATION_PATH, encoding="utf-8") as handle:
            disambiguation = json.load(handle)
    except FileNotFoundError:
        disambiguation = {}
    wrong = disambiguation.get("incorrectos", {})
    confirmed = disambiguation.get("confirmados", {})
    print("Desambiguacion: %d incorrectos, %d confirmados.\n"
          % (len(wrong), len(confirmed)), flush=True)

    names = sorted({
        entry["artist"]
        for decade in dictionary.values()
        for entries in decade.values()
        for entry in entries
    })
    total = len(names)
    print("%d artistas distintos que resolver.\n" % total, flush=True)

    resolved = {}
    unresolved = []
    skipped = []
    for position, name in enumerate(names, 1):
        print("[%d/%d] %s" % (position, total, name), flush=True)

        # Artistas que la busqueda automatica resuelve mal. Sin MBID
        # fijado a mano se SALTAN: se quedan con su 'genre' original en
        # vez de recibir generos de otro artista. Grueso antes que falso.
        if name in wrong:
            forced = wrong[name].get("mbid")
            if not forced:
                print("    saltado: %s (se resolvia como '%s')"
                      % ("sin MBID fijado", wrong[name].get("devolvia", "?")), flush=True)
                skipped.append(name)
                continue
            try:
                genres = lookup_genres(forced)
                time.sleep(DELAY_SECONDS)
            except RuntimeError as error:
                print("    FALLO DEFINITIVO: %s" % error, flush=True)
                unresolved.append(name)
                continue
            resolved[name] = {
                "mbid": forced, "matchedName": name, "score": 100,
                "genres": genres, "review": False, "desambiguado": True,
            }
            print("    MBID fijado a mano -> %s" % (genres[:4] or "sin generos"), flush=True)
            continue

        try:
            hit = search_artist(name)
            time.sleep(DELAY_SECONDS)
            if not hit:
                print("    sin resultados en MusicBrainz", flush=True)
                unresolved.append(name)
                continue
            genres = lookup_genres(hit["id"])
            time.sleep(DELAY_SECONDS)
        except RuntimeError as error:
            print("    FALLO DEFINITIVO: %s" % error, flush=True)
            unresolved.append(name)
            continue

        matched = hit.get("name") or ""
        score = int(hit.get("score") or 0)
        # Los confirmados devuelven otro nombre pero son el mismo
        # artista: no vuelven a marcarse dudosos en cada pasada.
        if confirmed.get(name) == matched:
            suspicious = False
        else:
            suspicious = fold(matched) != fold(name) or score < SCORE_THRESHOLD
        resolved[name] = {
            "mbid": hit["id"],
            "matchedName": matched,
            "score": score,
            "genres": genres,
            "review": suspicious,
        }
        if not genres:
            print("    resuelto pero SIN generos propios", flush=True)
        if suspicious:
            print("    REVISAR: devolvio '%s' (score %d)" % (matched, score), flush=True)

    # Se escribe el conjunto en cada entrada, conservando 'genre'.
    enriched = 0
    for decade in dictionary.values():
        for entries in decade.values():
            for entry in entries:
                info = resolved.get(entry["artist"])
                if info and info["genres"]:
                    entry["genres"] = info["genres"]
                    enriched += 1

    with open(DICT_PATH, "w", encoding="utf-8") as handle:
        json.dump(dictionary, handle, ensure_ascii=False, indent=1, sort_keys=True)
        handle.write("\n")

    with open("app/src/main/assets/artist_genre_resolution.json", "w", encoding="utf-8") as handle:
        json.dump(
            {"resolved": resolved, "unresolved": sorted(unresolved),
         "skipped": sorted(skipped)},
            handle, ensure_ascii=False, indent=1, sort_keys=True,
        )
        handle.write("\n")

    entries_total = sum(
        len(entries) for decade in dictionary.values() for entries in decade.values()
    )
    to_review = sum(1 for info in resolved.values() if info["review"])
    no_genres = sum(1 for info in resolved.values() if not info["genres"])

    print("\n--- RESUMEN ---", flush=True)
    print("Artistas distintos:            %d" % total)
    print("Resueltos:                     %d" % len(resolved))
    print("  ...sin generos propios:      %d" % no_genres)
    print("  ...marcados para revisar:    %d" % to_review)
    print("Sin resolver:                  %d" % len(unresolved))
    print("Saltados por desambiguacion:   %d" % len(skipped))
    print("Entradas enriquecidas:         %d de %d" % (enriched, entries_total))
    if unresolved:
        print("\nSin resolver: %s" % ", ".join(unresolved[:25]))

    if len(unresolved) > total * 0.10:
        print("\nERROR: mas del 10%% de artistas sin resolver.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

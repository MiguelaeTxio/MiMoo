#!/usr/bin/env python3
"""
Lista los candidatos de MusicBrainz para los artistas que la busqueda
automatica resuelve mal.

Los diez artistas de la seccion 'incorrectos' de
artist_disambiguation.json estan hoy sin MBID y por tanto sin conjunto
de generos. Para darselo hay que fijar el MBID correcto a mano -- y
elegirlo a ciegas seria repetir exactamente el fallo que motivo el
archivo: 'Pink' devolvia Pink Floyd porque nadie miro el resultado.

Este script no decide nada. Consulta los primeros candidatos de cada
nombre y anota de cada uno lo que permite distinguirlos: el texto de
desambiguacion, el pais, el tipo (persona o grupo), los anos de
actividad y sus generos. Con eso delante, la eleccion se hace con
evidencia y la toma Miguel Angel, que conoce a estos artistas.

Cuesta alrededor de un minuto: 10 nombres, unos pocos candidatos cada
uno, a una peticion por segundo.
"""

import json
import sys
import time
import urllib.parse

sys.path.insert(0, "tools")
from enrich_dictionary_genres import (  # noqa: E402
    BASE, DELAY_SECONDS, fetch_json, lookup_genres,
)

DISAMBIGUATION_PATH = "app/src/main/assets/artist_disambiguation.json"
OUT_PATH = "app/src/main/assets/artist_disambiguation_candidates.json"
CANDIDATES_PER_NAME = 5


def search_candidates(name):
    query = 'artist:"%s"' % name.replace('"', "")
    url = "%s/artist/?%s" % (
        BASE,
        urllib.parse.urlencode(
            {"query": query, "fmt": "json", "limit": CANDIDATES_PER_NAME}
        ),
    )
    return fetch_json(url).get("artists") or []


def main():
    with open(DISAMBIGUATION_PATH, encoding="utf-8") as handle:
        disambiguation = json.load(handle)

    pending = [
        name for name, info in disambiguation.get("incorrectos", {}).items()
        if not info.get("mbid")
    ]
    print("%d artistas sin MBID fijado.\n" % len(pending), flush=True)

    proposals = {}
    for position, name in enumerate(sorted(pending), 1):
        print("[%d/%d] %s" % (position, len(pending), name), flush=True)
        candidates = search_candidates(name)
        time.sleep(DELAY_SECONDS)

        listed = []
        for candidate in candidates:
            area = candidate.get("area") or {}
            begin_area = candidate.get("begin-area") or {}
            life = candidate.get("life-span") or {}
            try:
                genres = lookup_genres(candidate["id"])
            except RuntimeError:
                genres = []
            time.sleep(DELAY_SECONDS)

            listed.append({
                "mbid": candidate["id"],
                "nombre": candidate.get("name") or "",
                "desambiguacion": candidate.get("disambiguation") or "",
                "tipo": candidate.get("type") or "",
                "pais": candidate.get("country") or area.get("name")
                or begin_area.get("name") or "",
                "actividad": "%s%s" % (
                    (life.get("begin") or "?"),
                    (" - " + life["end"]) if life.get("end") else "",
                ),
                "generos": genres,
            })
            print("    %-32s %-10s %-14s %s" % (
                listed[-1]["nombre"][:32],
                listed[-1]["pais"][:10],
                listed[-1]["actividad"][:14],
                ", ".join(genres[:3]) or "sin generos",
            ), flush=True)

        proposals[name] = {
            "resolucion_erronea": disambiguation["incorrectos"][name].get("devolvia"),
            "candidatos": listed,
        }

    with open(OUT_PATH, "w", encoding="utf-8") as handle:
        json.dump(
            {
                "_nota": [
                    "Candidatos para los artistas de la seccion 'incorrectos' de",
                    "artist_disambiguation.json que aun no tienen MBID fijado.",
                    "Este archivo NO decide nada: es material para elegir con",
                    "evidencia. Una vez elegido, el MBID se copia al campo 'mbid'",
                    "del artista en artist_disambiguation.json y se relanza el",
                    "enriquecimiento, que entonces le pedira sus generos reales.",
                ],
                "propuestas": proposals,
            },
            handle, ensure_ascii=False, indent=1, sort_keys=True,
        )
        handle.write("\n")

    print("\nEscrito en %s" % OUT_PATH)
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""
Escribe el conjunto de generos de los artistas cuyo MBID se fijo a mano.

Los diez artistas de la seccion 'incorrectos' de
artist_disambiguation.json quedaron sin conjunto de generos porque la
busqueda automatica los resolvia mal y se decidio saltarlos: grueso
antes que falso. Cinco de ellos tienen ya MBID confirmado por Miguel
Angel sobre candidatos con evidencia -- pais, anos de actividad y
texto de desambiguacion.

Este script existe para no volver a rastrear los 658 artistas (una hora
y veinte minutos) por cinco entradas. Consulta solo los que tienen MBID
fijado y parchea sus entradas del diccionario. Cuesta segundos.

No busca: va directo al MBID. Esa es justo la diferencia -- la busqueda
es lo que fallaba.
"""

import json
import sys
import time

sys.path.insert(0, "tools")
from enrich_dictionary_genres import (  # noqa: E402
    DELAY_SECONDS, DISAMBIGUATION_PATH, DICT_PATH, lookup_genres,
)

RESOLUTION_PATH = "app/src/main/assets/artist_genre_resolution.json"


def main():
    with open(DISAMBIGUATION_PATH, encoding="utf-8") as handle:
        disambiguation = json.load(handle)

    forced = {
        name: info["mbid"]
        for name, info in disambiguation.get("incorrectos", {}).items()
        if info.get("mbid")
    }
    if not forced:
        print("Ningun artista con MBID fijado. Nada que hacer.")
        return 0
    print("%d artistas con MBID fijado a mano.\n" % len(forced), flush=True)

    with open(DICT_PATH, encoding="utf-8") as handle:
        dictionary = json.load(handle)
    with open(RESOLUTION_PATH, encoding="utf-8") as handle:
        resolution = json.load(handle)

    resolved = {}
    for position, (name, mbid) in enumerate(sorted(forced.items()), 1):
        print("[%d/%d] %s (%s)" % (position, len(forced), name, mbid), flush=True)
        try:
            genres = lookup_genres(mbid)
        except RuntimeError as error:
            print("    FALLO DEFINITIVO: %s" % error, flush=True)
            time.sleep(DELAY_SECONDS)
            continue
        time.sleep(DELAY_SECONDS)
        resolved[name] = genres
        print("    -> %s" % (", ".join(genres) or "SIN GENEROS PROPIOS"), flush=True)

    patched = 0
    for decade in dictionary.values():
        for entries in decade.values():
            for entry in entries:
                genres = resolved.get(entry["artist"])
                if genres:
                    entry["genres"] = genres
                    patched += 1

    for name, genres in resolved.items():
        resolution.setdefault("resolved", {})[name] = {
            "mbid": forced[name],
            "matchedName": name,
            "score": 100,
            "genres": genres,
            "review": False,
            "desambiguado": "MBID fijado a mano y confirmado por Miguel Angel",
        }

    with open(DICT_PATH, "w", encoding="utf-8") as handle:
        json.dump(dictionary, handle, ensure_ascii=False, indent=1, sort_keys=True)
        handle.write("\n")
    with open(RESOLUTION_PATH, "w", encoding="utf-8") as handle:
        json.dump(resolution, handle, ensure_ascii=False, indent=1, sort_keys=True)
        handle.write("\n")

    entries_total = sum(
        len(entries) for decade in dictionary.values() for entries in decade.values()
    )
    with_set = sum(
        1
        for decade in dictionary.values()
        for entries in decade.values()
        for entry in entries
        if entry.get("genres")
    )
    print("\n--- RESUMEN ---")
    print("Artistas resueltos:     %d de %d" % (len(resolved), len(forced)))
    print("Entradas parcheadas:    %d" % patched)
    print("Diccionario:            %d de %d con conjunto (%.1f%%)"
          % (with_set, entries_total, 100.0 * with_set / entries_total))
    return 0


if __name__ == "__main__":
    sys.exit(main())

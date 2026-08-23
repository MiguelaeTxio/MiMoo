#!/usr/bin/env python3
"""
S035 -- segunda ronda de la sonda de aislamiento. get_user() exige
cookie de autenticacion (AuthenticationError real, confirmado en la
sonda anterior) pese a que la documentacion de spotifyscraper lo lista
como funcion publica -- se descarta ese camino. Esta version comprueba
en su lugar search() (documentada como anonima) y get_playlist() sobre
un ID ya validado a mano en esta misma sesion (web_fetch real,
1960 - LAS 100 CANCIONES DEL ANO EN ESPANA), antes de reescribir el
cosechador entero sobre una API que tampoco se ha probado en vivo.
"""
import json
import sys
import traceback

KNOWN_PLAYLIST_ID = "450tJfoEUYVS2r6yRGmxr0"  # 1960 - LAS 100 CANCIONES DEL ANO EN ESPANA

result = {}


def probe(label, fn):
    entry = {"stage": label}
    try:
        value = fn()
        entry["ok"] = True
        entry["repr"] = repr(value)[:2500]
        entry["type"] = str(type(value))
        entry["dir"] = [a for a in dir(value) if not a.startswith("_")]
    except BaseException as e:
        entry["ok"] = False
        entry["error"] = "%s: %s" % (type(e).__name__, e)
        entry["traceback"] = traceback.format_exc()
    result[label] = entry


try:
    from spotify_scraper import SpotifyClient
    client = SpotifyClient()

    probe("search_playlist", lambda: client.search(
        "LAS 100 CANCIONES DEL ANO EN ESPANA", types=("playlist",), limit=10,
    ))
    probe("get_playlist", lambda: client.get_playlist(KNOWN_PLAYLIST_ID))
except BaseException as e:
    result["import_or_client_error"] = "%s: %s" % (type(e).__name__, e)
    result["traceback"] = traceback.format_exc()

with open("tools/chart_spotify100_probe_debug.json", "w", encoding="utf-8") as f:
    json.dump(result, f, ensure_ascii=False, indent=1, default=str, sort_keys=True)
    f.write("\n")

sys.exit(0)

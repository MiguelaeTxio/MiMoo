#!/usr/bin/env python3
"""
S035 -- sonda minima de aislamiento. harvest_spotify100_charts.py
fallaba tres veces seguidas sin escribir NUNCA su fichero de
diagnostico, ni con `except Exception` ni con `except BaseException`
-- indicio real de que el proceso se esta terminando por una via que
ni siquiera pasa por una excepcion de Python (señal externa, timeout
duro de alguna dependencia, etc.). Esta sonda hace lo MINIMO posible
(una sola llamada, sin reintentos, sin logica alrededor) para aislar
si el problema esta de verdad en spotifyscraper.get_user(), y vuelca
CUALQUIER resultado, siempre, con exit(0) fijo para no depender de
como el step interprete el codigo de salida.
"""
import json
import sys
import traceback

result = {"stage": "arranque"}
try:
    result["stage"] = "import"
    from spotify_scraper import SpotifyClient
    import spotify_scraper
    result["spotify_scraper_version"] = getattr(spotify_scraper, "__version__", "desconocida")

    result["stage"] = "construir cliente"
    client = SpotifyClient()

    result["stage"] = "get_user"
    user = client.get_user("115935096")
    result["ok"] = True
    result["user_repr"] = repr(user)[:2000]
    result["user_type"] = str(type(user))
    result["user_dir"] = [a for a in dir(user) if not a.startswith("_")]
except BaseException as e:
    result["ok"] = False
    result["error"] = "%s: %s" % (type(e).__name__, e)
    result["traceback"] = traceback.format_exc()

with open("tools/chart_spotify100_probe_debug.json", "w", encoding="utf-8") as f:
    json.dump(result, f, ensure_ascii=False, indent=1, default=str, sort_keys=True)
    f.write("\n")

print(json.dumps(result, ensure_ascii=False, indent=1, default=str)[:3000])
sys.exit(0)

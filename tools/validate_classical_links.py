"""
tools/validate_classical_links.py

Orden de Miguel Ángel (H15, S032): *"Como si haces un script aparte y
lo ejecutas desde GitHub para validar los links y se guardan en base
de datos. Una vez que tengas esos links se resuelven todos sin
principio, a medida que pase el tiempo irán rompiéndose estos cien
links que se irán renovando a decreto de la ejecución de la
aplicación."*

Busca y valida un enlace de YouTube real para cada obra del
recopilatorio de clásica (`known_hit_classical.json`, ya existente en
el proyecto -- 104 obras con título completo, curadas a mano), y
guarda el resultado en `classical_validated_links.json`. Con esto la
app puede encolar el recopilatorio ENTERO de golpe al elegir Clásica
en miMooutCast, sin tener que buscar cada obra en vivo -- son enlaces
ya comprobados de antemano, no una búsqueda en el momento.

Se ejecuta en GitHub Actions (ver
`.github/workflows/validate-classical-links.yml`), nunca en el
entorno de Claude: sin acceso a YouTube desde ahí, este script no se
ha podido probar en vivo antes de commitear -- la primera ejecución
real del workflow es la primera prueba de verdad. Reutiliza yt-dlp,
la misma librería que usa la app (`link_resolver.py`,
`ExternalLinkResolver.kt`) -- mismo mecanismo `ytsearchN:` de
búsqueda gratuita, nunca la Data API de cuota.

Las mismas reglas de filtrado por título que ya usa
`PlayerManager.looksLikeNonSong()` en Kotlin (NOT_MUSIC_TITLE_HINTS)
se reproducen aquí en Python, a propósito -- un enlace que la app
descartaría en vivo no debería colarse por venir precalculado.
"""

import json
import re
import sys
import time
import unicodedata

import yt_dlp

SOURCE_PATH = "app/src/main/assets/known_hit_classical.json"
OUTPUT_PATH = "app/src/main/assets/classical_validated_links.json"

# H15, S032 -- número de resultados de YouTube que se miran por obra
# antes de rendirse. Ocho es suficiente margen para descartar
# documentales/portraits sin tardar una eternidad por obra (104 obras
# en el peor caso, cortesía de 1s entre peticiones = unos minutos).
SEARCH_LIMIT = 8

# H15, S032 -- ventana de duración razonable para un tema suelto de
# clásica. Ni tan corto que sea un fragmento roto, ni tan largo que
# sea la obra ENTERA en varios movimientos -- Miguel Ángel fue
# explícito: *"esto es un popurrí de temas, no de obras completas."*
MIN_DURATION_SECONDS = 30
MAX_DURATION_SECONDS = 20 * 60

# H15, S032 -- misma lista que NOT_MUSIC_TITLE_HINTS en PlayerManager.kt
# (la única de las dos listas que se aplica en clásica -- ver el kdoc
# de looksLikeNonSong()). Mantener las dos en sincronía a mano; no hay
# forma de compartir la lista literal entre Kotlin y Python en este
# proyecto.
NOT_MUSIC_HINTS = [
    "interview", "entrevista",
    "chapter", "capitulo",
    "episode", "episodio",
    "podcast",
    "documentary", "documental",
    "audiobook", "audiolibro",
    "full movie", "pelicula completa",
    "tutorial",
    "how to play", "como tocar",
    "lesson", "leccion",
    "explained", "explicado", "explicada",
    "analysis", "analisis",
    "breakdown",
    "masterclass",
    "reaction", "reacciona",
    "minutes on",
    "portrait", "retrato",
    "an introduction", "una introduccion",
    "full", "integral", "obra completa", "sinfonia completa",
]


def normalize(text: str) -> str:
    """Mismo criterio que SearchNormalizer.normalize() en Kotlin: minúsculas, sin acentos, solo letras/dígitos/espacios."""
    text = text.lower()
    decomposed = unicodedata.normalize("NFD", text)
    without_accents = "".join(c for c in decomposed if unicodedata.category(c) != "Mn")
    only_word_chars = re.sub(r"[^a-z0-9\s]", " ", without_accents)
    return re.sub(r"\s+", " ", only_word_chars).strip()


def contains_word(haystack_norm: str, needle_raw: str) -> bool:
    """Coincidencia por PALABRA COMPLETA, no por trozo de texto -- mismo motivo que ANNEX_H08.md documenta (Tracy CHAPman por 'chap')."""
    needle_norm = normalize(needle_raw)
    if not needle_norm:
        return False
    return re.search(r"(^|\s)" + re.escape(needle_norm) + r"($|\s)", haystack_norm) is not None


def looks_like_non_song(title: str) -> bool:
    norm = normalize(title)
    return any(contains_word(norm, hint) for hint in NOT_MUSIC_HINTS)


def search_youtube(query: str, limit: int):
    options = {
        "quiet": True,
        "no_warnings": True,
        "extract_flat": True,
        "skip_download": True,
        "default_search": f"ytsearch{limit}",
    }
    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(query, download=False)
    return (info or {}).get("entries") or []


def artist_appears_in_title(artist: str, title_norm: str) -> bool:
    """Nunca cae al nombre del canal -- exige que el ARTISTA aparezca en el título, mismo criterio que matchesArtist() en Kotlin. Comprueba el apellido/última palabra del artista, más tolerante que exigir el nombre completo exacto."""
    artist_norm = normalize(artist)
    if artist_norm in title_norm:
        return True
    words = artist_norm.split()
    return bool(words) and contains_word(title_norm, words[-1])


def pick_best_candidate(artist: str, song: str):
    query = f"{artist} {song}"
    try:
        entries = search_youtube(query, SEARCH_LIMIT)
    except Exception as e:
        print(f"  EXCEPCION buscando '{query}': {e}", file=sys.stderr)
        return None

    for entry in entries:
        if entry is None:
            continue
        video_id = entry.get("id")
        title = entry.get("title") or ""
        if not video_id or not title:
            continue
        title_norm = normalize(title)
        if not artist_appears_in_title(artist, title_norm):
            continue
        if looks_like_non_song(title):
            continue
        duration = entry.get("duration") or 0
        if duration and not (MIN_DURATION_SECONDS <= duration <= MAX_DURATION_SECONDS):
            continue
        return {
            "artist": artist,
            "song": song,
            "youtube_id": video_id,
            "title": title,
            "duration_seconds": duration,
        }
    return None


def main():
    with open(SOURCE_PATH, encoding="utf-8") as f:
        works = json.load(f)

    validated = []
    failed = []
    for i, work in enumerate(works):
        artist = work["artist"]
        song = work["song"]
        print(f"[{i + 1}/{len(works)}] {artist} - {song}")
        result = pick_best_candidate(artist, song)
        if result:
            validated.append(result)
            print(f"  -> OK: {result['title']} ({result['youtube_id']})")
        else:
            failed.append(f"{artist} - {song}")
            print("  -> SIN CANDIDATO VALIDO")
        # Cortesia con YouTube -- mismo espaciado que ya usa el resto
        # de tools/ del proyecto para no arriesgar un bloqueo por ritmo.
        time.sleep(1)

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(validated, f, ensure_ascii=False, indent=2)

    print(f"\n{len(validated)}/{len(works)} obras validadas -> {OUTPUT_PATH}")
    if failed:
        print(f"{len(failed)} sin validar esta vez (puede que la proxima ejecucion si encuentre algo):")
        for item in failed:
            print(f"  - {item}")


if __name__ == "__main__":
    main()

# PUNTO DE REANUDACIÓN — MiMoo

**Última sesión cerrada:** S024 (2026-07-28)
**Hito EN PROGRESO:** H13 — UX del Reproductor (`DOCS/ANNEX_H13.md`)

> El estado de los hitos vive **exclusivamente** en
> `DOCS/ANNEX_ROUTER.md`. Este archivo no lo declara ni lo duplica.

---

## Qué hacer en la siguiente sesión

La hoja de ruta completa está en `DOCS/ANNEX_H13.md`. En una línea:
**repaso sistemático de la UX del reproductor**, por encargo de Miguel
Ángel al cerrar S024.

Tres cosas, en este orden:

1. **Descargar no funciona** — único bug con reporte explícito. Miguel
   Ángel lo sitúa "en el ExoPlayer", que en la práctica es la barra de
   reproducción. Reproducirlo antes de tocar nada, y comprobar primero
   si es una regresión del bug de descargas de S019 (yt-dlp,
   restricción por edad) o un fallo distinto.
2. **Repasar TODOS los botones** de `PlayerBar.kt`, mirando el código y
   no solo el comportamiento visible: qué estado lee cada uno, qué
   dispara, y si puede quedar sin efecto en algún estado (cola vacía,
   local frente a streaming, Radio activa). **Anotar todos los fallos
   antes de arreglar ninguno** y presentarle la lista para que
   priorice.
3. **Aspecto de "algunos ítems"** — mencionado sin concretar.
   Preguntarle cuáles; no asumir que son las chapitas de S018, que ya
   están hechas y verificadas.

---

## Dónde se quedó S024 (H08 — Radio)

**El objetivo que abrió S023 y S024 está cumplido y medido.** Que la
Radio no se agote. Recorrido de la mediana de candidatos del bloque
español a lo largo de la sesión:

    partida                                 7    (18% con <5 candidatos)
    relleno con Discogs                    10    (11%)
    anclas que solo tenían carpetas raíz   12     (9%)
    ampliación con listas de Los 40        44     (5%)

    objetivo fijado al cerrar S023:  mediana ~15, menos del 5%

Verificado además en dispositivo: una sesión anclada en Radio Futura
sirvió catorce temas seguidos de la movida madrileña con **cero
repeticiones, cero violaciones de la ventana de diez artistas y cero
porciones agotadas**.

Lo que cambió el rumbo de la sesión fue una observación de Miguel
Ángel a mitad de camino: *"esta forma de probar, caso no contemplado,
implementar caso, probar, caso no contemplado, implementar caso,
conlleva a una implementación eterna"*. Medirlo le dio la razón — el
cuello de botella no era la calidad del filtro sino el volumen del
diccionario, que pasó de 352 a 1027 entradas españolas con los números
uno de Los 40 entre 1966 y 2025.

El detalle completo, con las doce áreas trabajadas y las cuatro
pasadas que costó el sondeo de fuentes, está en `COMPLETADAS EN S024`
dentro de `DOCS/ANNEX_H08.md`.

---

## Decisión pendiente de Miguel Ángel

**No tomarla por él.** `resolveFinalFallback()` todavía puede repetir
un tema cuando las tres porciones fallan en la misma vuelta. Se dejó
repitiendo *el más antiguo* en vez de al azar, y en la última prueba no
llegó a dispararse ni una vez — pero repite.

Miguel Ángel dijo que un tema no se repite NUNCA. Eso obliga a decidir
qué hace la Radio cuando de verdad no queda nada: **pararse o
repetir**. Está anotado como punto 1 de la hoja de ruta de
`ANNEX_H08.md`.

---

## Cabos sueltos de H08, ninguno bloqueante

- **Bloque internacional sin ampliar** — mediana 26 frente a 44 del
  español. Las 1338 canciones no españolas de la cosecha están ya
  descargadas y enriquecidas en `tools/chart_los40_raw.json` y
  `tools/chart_los40_enriched.json`; la fusión sería ejecutar
  `merge_charts_into_dictionary.py` con el criterio de país invertido.
  Miguel Ángel lo descartó para S024 por no aportar donde ya sobra.
- **Repertorio clásico sin volver a probar** — el país sin filtrar y el
  tope de 45 minutos entraron después de la última prueba de clásica.
- **766 filas de Wikipedia sin interpretar** por el parseo de
  `harvest_los40_charts.py`.
- Tres pendientes menores heredados de S023, detallados al final de la
  hoja de ruta de `ANNEX_H08.md`.

---

## Herramientas nuevas disponibles

Todas se ejecutan desde GitHub Actions con disparo manual, porque la
red del entorno del modelo va por lista blanca y Wikidata, Discogs y
MusicBrainz están bloqueadas.

| herramienta | workflow | qué hace |
|---|---|---|
| `probe_genre_sources.py` | `probe-genre-sources.yml` | compara cobertura de género de Discogs, Wikipedia y Wikidata |
| `harvest_los40_charts.py` | `harvest-los40.yml` | cosecha los números uno de Los 40, 1966–2025 |
| `enrich_chart_artists.py` | `enrich-chart-artists.yml` | país y género de los artistas cosechados |
| `fill_spanish_genres.py` | — (local) | rellena géneros del bloque español |
| `merge_charts_into_dictionary.py` | — (local) | fusiona la cosecha en el diccionario |

`enrich-chart-artists.yml` y `probe-genre-sources.yml` necesitan el
secret `DISCOGS_TOKEN`, ya dado de alta en el repositorio.

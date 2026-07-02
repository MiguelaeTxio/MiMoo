# MIMOO — ANEXO HITO 01
# Buscar y Escuchar: Streaming de Audio bajo Demanda

*Vive en `DOCS/ANNEX_H01.md` — flujo NewFlow Android. Ver estado en
`DOCS/MASTER_DOCUMENT.md`.*

---

## NOTA DE REFUNDACIÓN

Este anexo sustituye por completo al Hito 01 original (Fundación:
Modelos, API YouTube y CRUD de Colecciones), descartado por decisión
explícita de Miguel Ángel en S002-H02 (2026-06-30). Ver §0 de
`MASTER_DOCUMENT.md` para el contexto completo de la refundación.

---

## OBJETIVO DEL HITO

Implementar el flujo mínimo central de MiMoo: buscar un tema musical
por término en YouTube y escucharlo inmediatamente en streaming de
audio, sin necesidad de descargarlo primero.

---

## COMPLETADAS EN S001 (2026-06-30)

**Fecha:** 2026-06-30

**PASO 1 — Purga del código obsoleto:** eliminados del repositorio
27 archivos del Hito 01 original (`ui/artist/`, `ui/album/`,
`ui/playlist/`, `ui/track/`, entidades `Artist.kt`/`Album.kt`/
`Playlist.kt`/`PlaylistTrack.kt`, DAOs y repositorios
correspondientes) vía `git rm`, confirmado limpio (27/27, cero
errores).

**PASO 2 — Decisión técnica BinaryManager:** adelantada a este hito
de forma indirecta: en lugar del plan original de copiar binarios
yt-dlp/ffmpeg nativos desde `assets/` (`BinaryManager.kt`), se
adoptó **Chaquopy 17.0** (Python embebido vía Gradle plugin,
`pip { install("yt-dlp") }`) tras confirmar por búsqueda web que no
existe binario yt-dlp ARM64 standalone para Android — yt-dlp es un
script Python, y la única vía sin Termux es embeber un intérprete
Python completo. Decisión validada con éxito: `BinaryManager.kt`
queda obsoleto, ya no es necesario en ningún hito futuro.

**PASO 3 — Entidad de caché de búsqueda:** creada `SearchResultTrack`
(Room), sin relación a `Artist`/`Album` manuales — todos los campos
proceden de metadatos de YouTube.

**PASO 4 — Adaptación de YouTubeRepository a búsqueda:** sustituido
el flujo de import de playlist por `search.list` (coste real 1
unidad/llamada, límite propio de 100/día).

**PASO 5 — Pantalla de búsqueda:** `SearchScreen.kt` +
`SearchViewModel.kt` creados.

**PASO 6 — Resolución de stream + reproductor:** `resolver.py`
(módulo Python embebido) con `resolve_audio_stream_url()`.
`StreamResolver.kt` (wrapper Kotlin vía Chaquopy). `PlayerManager.kt`
(ExoPlayer/Media3). `PlayerBarViewModel.kt` + `PlayerBar.kt`.

**PASO 7 — Verificación funcional:** completada con éxito. Build #22
verde. App instalada, búsqueda de término real devuelve resultados de
YouTube, reproducción en streaming confirmada por Miguel Ángel
(prueba real: "Sonata n.º 14 de Beethoven", audio reproducido
correctamente).

**Incidencias técnicas resueltas durante la implementación:**

1. **Build #18 — Chaquopy no encuentra Python 3.10:** corregido
   fijando `version = "3.11"` explícito en `chaquopy { defaultConfig
   {} }` y añadiendo `actions/setup-python@v5` (`python-version:
   '3.11'`) al workflow.

2. **Build #19 — Redeclaration y Unresolved reference:**
   `Track.kt`/`TrackDao.kt` quedaron olvidados en el `git rm` del
   PASO 1. Corregido con `git rm` adicional. Segundo error en el
   mismo build: import erróneo de `weight()` como función suelta en
   `MainActivity.kt` — corregido eliminando el import. Build #20
   verde.

3. **HTTP 403 "unregistered caller" en runtime:** instrumentado
   `YouTubeRepository.search()` para capturar el cuerpo JSON real de
   error. Causa raíz: `project.findProperty("YOUTUBE_API_KEY")` en
   `app/build.gradle.kts` no resolvía la clave de `local.properties`
   — AGP solo auto-carga propiedades reservadas. Corregido cargando
   `local.properties` explícitamente con `java.util.Properties().load()`.
   Build #22 verde.

4. **PA_API_TOKEN desincronizado y directorio `apk/` inexistente** —
   ver `ANNEX_H02.md` para el detalle, afecta a infraestructura
   compartida.

**Decisión de arquitectura — yt-dlp como extractor único:** el mismo
módulo yt-dlp (embebido vía Chaquopy) sirve tanto para resolver
streaming como para descarga a Opus (Hito 02).

---

## Estado

**Hito 01 completado y verificado funcionalmente.** No hay pasos
bloqueantes pendientes. Mejoras opcionales sin prioridad confirmada:
manejo de errores más pulido en `SearchScreen`, indicador visual
diferenciado "resolviendo stream" vs "buscando", lectura de caché de
búsqueda al reabrir la app.

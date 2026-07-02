# MIMOO — ANEXO HITO 05
# Búsqueda de Álbumes Completos vía MusicBrainz

*Vive en `DOCS/ANNEX_H05.md` — flujo NewFlow Android. Ver estado en
`DOCS/MASTER_DOCUMENT.md`. EN PROGRESO.*

---

## NOTA DE APERTURA (S001, 2026-07-02)

Abierto tras cerrar H04. Siguiente y último punto con descripción
propia en `MASTER_DOCUMENT.md` §1 antes de los puntos genéricos
"música relacionada"/"backup Drive" (sin alcance definido todavía).

---

## OBJETIVO DEL HITO

Buscar un álbum completo por artista+título contra MusicBrainz,
obtener su tracklist real (título+duración por pista), emparejar cada
pista automáticamente con un vídeo de YouTube por cercanía de
duración, permitir corrección manual del emparejamiento, e importar
el álbum completo a la base de datos existente (`search_result_tracks`
con `artist`/`album` ya fijados) para que funcione con el resto de la
app (descarga, biblioteca, carátulas, playlists) sin ningún cambio en
esas partes.

---

## CONTEXTO TÉCNICO — QUÉ SE HEREDA SIN CAMBIOS

- `MusicBrainzApiService`/`NetworkModule` (Hito 03): cliente
  MusicBrainz ya configurado con User-Agent obligatorio y limitador de
  ~1 req/s.
- `YouTubeRepository.search(query, apiKey)`: devuelve `List<TrackDto>`
  con `durationSeconds` resuelto.
- `SearchResultTrackRepository.cacheSearchResults()`: reutilizada tal
  cual para "importar álbum".
- **Verificar siempre contra el archivo real antes de codificar**
  (directriz §4.1 `MASTER_DOCUMENT.md`).

---

## Estado de Tareas

| Paso | Descripción | Estado |
|---|---|---|
| 1 | MusicBrainzApiService: lookup de release con tracklist (DTOs + endpoint) | HECHO — S001 |
| 2 | AlbumMatchRepository: orquesta búsqueda MB + emparejamiento YouTube por duración | HECHO — S001 |
| 3 | Pantalla AlbumSearchScreen + AlbumSearchViewModel | HECHO — S001 |
| 4 | Corrección manual del emparejamiento por pista | HECHO — S001 |
| 5 | Importar álbum a search_result_tracks | HECHO — S001, con incidencias (ver COMPLETADAS EN S001) |
| 6 | Verificación funcional end-to-end (dispositivo) | EN CURSO — incidencias detectadas, ver Hoja de Ruta |

---

## COMPLETADAS EN S001 (2026-07-02)

**PASOS 1-5 — Implementación completa.** `MusicBrainzApiService.lookupRelease`
+ DTOs de tracklist, `AlbumMatchRepository.matchAlbum()` (tolerancia
de emparejamiento fijada en ±7s, elegida como punto de partida
razonable sin validar contra álbumes reales, ver comentario en el
propio archivo), `AlbumSearchScreen`/`AlbumSearchViewModel` con
corrección manual por pista, e importación vía
`SearchResultTrackRepository.cacheSearchResults()`. Build en verde en
GitHub Actions.

**PASO 6 — Verificación real por Miguel Ángel: dos incidencias
bloqueantes encontradas.**

1. **Búsqueda de álbum exige artista Y álbum, no permite uno solo.**
   Miguel Ángel intentó buscar "la Novena Sinfonía de Beethoven" y le
   resultó imposible — el botón "Buscar álbum" solo se habilita con
   ambos campos rellenos, y `searchAlbum()` aborta si cualquiera de
   los dos está vacío. Para música clásica en particular, buscar solo
   por título de obra es un caso de uso legítimo que la pantalla
   actual no contempla.

2. **Importación exitosa pero invisible — bug crítico.** Con "Lou
   Reed" / "Transformer" sí encontró y emparejó las 11 pistas, dio a
   "Importar álbum", y Miguel Ángel no vio ningún resultado en
   ninguna pantalla de la app. Causa raíz identificada (sin corregir
   aún): `AlbumSearchViewModel.importAlbum()` inserta las filas con
   `downloadStatus = PENDING` y `filePath = null`, pero:
   - `SearchScreen`/`SearchViewModel` solo refleja
     `_currentYoutubeIds` (la última búsqueda en vivo), no todo lo
     que hay en Room. Las pistas importadas nunca entran en ese
     conjunto.
   - `LibraryScreen`/`LibraryViewModel` solo muestra
     `downloadStatus == DONE`. Las importadas están en `PENDING`.
   - Resultado: las 11 pistas existen en la base de datos local,
     correctamente emparejadas, pero ninguna pantalla las muestra.

**Petición explícita de Miguel Ángel (decisión de producto, no bug):**
el álbum importado debe **descargarse automáticamente**, no quedarse
como metadato a la espera de descarga manual. Motivo: la YouTube Data
API tiene un tope de cuota (mencionado como "100 búsquedas"/día) y no
quiere depender de volver a buscar en YouTube cada vez.

---

## Hoja de Ruta para la Siguiente Sesión

**Los PASOS 1-5 ya están implementados — no repetirlos.** Esta
sección sustituye al antiguo PASO 6 "Verificación funcional", que ha
mutado en tres correcciones concretas surgidas de la prueba real.

### PASO 6a — Búsqueda de álbum: permitir artista solo, álbum solo, o ambos

- **Verificar contra el archivo real** `AlbumSearchScreen.kt`/
  `AlbumSearchViewModel.kt` antes de tocarlos.
- Quitar la exigencia de que ambos campos estén rellenos: el botón
  "Buscar álbum" se habilita con al menos uno de los dos no vacío.
- `AlbumMatchRepository.matchAlbum(artist: String?, album: String?, ...)`:
  construir la query de MusicBrainz condicionalmente — si falta
  `artist`, omitir `artist:"..."`; si falta `album`, omitir
  `release:"..."`.
- Con resultados ambiguos (típico en música clásica con múltiples
  grabaciones), decidir con Miguel Ángel si se toma la primera
  coincidencia o se muestra una lista de releases candidatas — no
  asumir.
- Ejemplo real de prueba a repetir: buscar "Novena Sinfonía" de
  Beethoven sin artista.

### PASO 6b — Bug crítico: pistas importadas invisibles

**Parte 1 — Descarga automática al importar.**
- `AlbumSearchViewModel.importAlbum()`: además de
  `cacheSearchResults()`, encolar la descarga real de cada pista vía
  `DownloadQueueManager.enqueue(youtubeId, title, artist)` — mismo
  mecanismo que `SearchViewModel.requestDownload()` (leer ese archivo
  real antes de replicar la llamada).
- Aclarar con Miguel Ángel si la autodescarga se extiende también a
  `SearchScreen` normal (no solo álbumes importados) y si quiere
  poder desactivarla — no generalizar sin confirmar el alcance.

**Parte 2 — Visibilidad, incluso sin depender de la Parte 1.**
- Feedback inmediato en `AlbumSearchScreen` tras importar (mostrar
  `downloadStatus` de cada pista importada en la misma lista), y/o
  botón "Ver en Biblioteca" en el diálogo de confirmación — decidir
  cuál(es) con Miguel Ángel.

### PASO 6c — Verificación funcional real (repetir)

- Build en verde (comprobar tras 6a/6b).
- Repetir la prueba: buscar "Novena Sinfonía" sin artista obligatorio,
  reimportar Lou Reed - Transformer (u otro álbum) confirmando que
  las pistas aparecen, se descargan solas, y son escuchables desde
  Biblioteca sin pasos adicionales.

---

## Pendiente heredado de otros hitos (no tocar aquí, solo constancia)

`ANNEX_H03.md` PASO 8 y `ANNEX_H04.md` PASO 6 (verificación funcional
en dispositivo) siguen pendientes — Miguel Ángel no llegó a probarlos
en S001, solo el flujo de búsqueda de álbum de H05.

---

## Fuera de Alcance de Este Hito (explícitamente pospuesto)

- Emparejamiento por huella acústica (fingerprinting) — solo por
  duración/minutaje.
- Importación masiva de varios álbumes a la vez.

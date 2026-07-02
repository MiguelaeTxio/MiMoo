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

**Los PASOS 1-5 ya están implementados — no repetirlos. PASO 6a y
PASO 6b (Partes 1 y 2) ya están implementados y pusheados en S002
(2026-07-02, sesión NewFlow) — ver detalle en "COMPLETADAS EN S002"
más abajo.** Queda 6c (verificación real) y un nuevo PASO 6d que se
intercala antes de 6c por petición explícita de Miguel Ángel.

### PASO 6d — Selector de álbumes candidatos (HECHO — S002)

- Petición explícita de Miguel Ángel: buscar por un solo término
  ("Beethoven", "Sinfonía") coincidía con demasiados releases
  distintos en MusicBrainz como para importar el primero sin
  mostrarlos — resolvía así la ambigüedad que 6a dejó pendiente de
  decidir.
- `AlbumMatchRepository.matchAlbum()` dividido en
  `searchAlbumCandidates(artist, album)` (hasta 20 releases,
  `AlbumCandidate` con mbid/title/artist/year/coverArtUrl) y
  `matchAlbumTracks(mbid, artist, apiKey)` (lookup + emparejamiento
  YouTube del release ya elegido).
- `AlbumSearchScreen`: lista de candidatos con carátula (Cover Art
  Archive vía Coil, fallback a icono genérico) hasta elegir uno;
  entonces cabecera del álbum elegido (con botón para volver a la
  lista) + tracklist + Importar, igual que antes.
- `DOCS/MASTER_DOCUMENT.md` §4.5 (actualizarse en línea sobre APIs
  externas) aplicado: campos `artist-credit`/`date` de MusicBrainz
  verificados contra la documentación oficial antes de mapearlos en
  el DTO.

### PASO 6c — Verificación funcional real (repetir, PENDIENTE)

- Build en verde (comprobar tras 6a/6b/6d).
- Repetir la prueba: buscar "Beethoven" o "Sinfonía" sueltos y
  confirmar que aparece una lista de álbumes candidatos con carátula;
  elegir uno y confirmar que ahí sí se pide tracklist + emparejamiento
  YouTube; reimportar Lou Reed - Transformer (u otro álbum) y
  confirmar que las pistas aparecen en la misma lista con su estado de
  descarga, se descargan solas, el botón "Ver en Biblioteca" lleva
  hasta allí, y son escuchables sin pasos adicionales.

---

## COMPLETADAS EN S002 (2026-07-02, sesión NewFlow)

- **PASO 6a** — búsqueda de álbum con artista solo, álbum solo, o
  ambos. `matchAlbum(artist: String?, album: String?, ...)` construye
  la query de MusicBrainz condicionalmente; botón "Buscar álbum"
  habilitado con al menos un campo relleno.
- **PASO 6b Parte 1** — autodescarga real al importar, vía
  `DownloadQueueManager.enqueue()` por cada pista, mismo mecanismo que
  `SearchViewModel.requestDownload()`. Alcance limitado al import de
  álbum, confirmado con Miguel Ángel que no hace falta extenderlo a
  `SearchScreen` (ya existe la misma paridad ahí vía el botón
  "Descargar" por pista).
- **PASO 6b Parte 2** — visibilidad tras importar, ambos enfoques
  decididos por Miguel Ángel: estado de descarga en vivo en la misma
  fila de la lista de matches (`importedStatus`, observado desde Room
  vía `flatMapLatest`) y botón "Ver en Biblioteca" en el diálogo de
  confirmación.
- **PASO 6d** — selector de álbumes candidatos, ver detalle arriba.
- **Corrección de diagnóstico** (no parte de H05, ver `ANNEX_H03.md`):
  la hipótesis de keystore antigua residual para el "conflicto con un
  paquete" al actualizar la APK quedó descartada — es sistemático en
  10-12 versiones seguidas, no un caso puntual. Pista real aportada
  por Miguel Ángel: revisar los changelogs de NewPipe, que tuvo el
  mismo problema y lo resolvió. Pospuesto hasta terminar H05.

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

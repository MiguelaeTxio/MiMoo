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
| 6 | Verificación funcional end-to-end (dispositivo) | EN CURSO — Prioridad 1 confirmada (S003), quedan Lou Reed/Beethoven/playlist normal |

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

## COMPLETADAS EN S003 (2026-07-03, sesión NewFlow)

**Contexto de arranque:** retomando el PASO 6c pendiente. Verificación
real de Miguel Ángel en dispositivo desenterró una cadena de bugs
reales encadenados, cada uno llevando al siguiente — no una sola
sesión de un solo bug.

**Prioridad 1 (cierre de app al reimportar el álbum de YouTube Music)
— CONFIRMADA RESUELTA**, pero la causa real no era ninguna de las dos
capas de arreglo de S002. Investigación completa vía `crash_log.txt`
(ver más abajo): `CoverArtRepository.resolveCoverArtUrl()` guardaba
`url` (nullable) directamente en un `ConcurrentHashMap`, que **no
admite valores `null`** — cualquier álbum sin match en MusicBrainz
crasheaba la app entera al cachear el resultado negativo. Corregido
con un centinela (cadena vacía) en vez de `null`. Verificado en
dispositivo tras el fix: Biblioteca abre sin cerrarse.

**Pantalla "Descargas" (nueva, menú lateral) — no estaba en el
alcance original de H05, se volvió necesaria al verificar "Importar
enlace":** mismo patrón de bug que "Importación exitosa pero
invisible" de S001 (PASO 6, incidencia 2), pero en `ImportLinkScreen`
en vez de `AlbumSearchScreen` — "descargar todo" no mostraba ningún
progreso ni spinner, y salir de la pantalla lo hacía desaparecer del
todo (no había ningún sitio persistente donde ver "qué se está
descargando ahora"). Cadena de cambios para resolverlo de raíz:

- `DownloadStatus.QUEUED` (nuevo): antes, `DownloadQueueManager.enqueue()`
  no tocaba Room en absoluto, así que una pista recién pedida era
  indistinguible de una nunca pedida (ambas `PENDING`). Ahora
  `enqueue()` marca `QUEUED` de inmediato.
- `downloadProgress` (0-100, nuevo campo): progreso real vía
  `progress_hooks` de yt-dlp (`downloader.py`), propagado a Kotlin
  como un proxy Chaquopy (`DownloadProgressListener`) que
  `DownloadWorker` persiste en Room con throttling de 2 puntos.
- `DownloadsScreen`/`DownloadsViewModel`: tres secciones — Descargando
  (barra determinada con % real), En cola (barra vacía), Completadas
  recientes (tope de 15, no sustituye a Biblioteca). Más tarde, tras
  investigar dos pistas que parecían "perdidas", se añadió una cuarta
  sección **Con error** (antes invisible en toda la app) con botón de
  reintentar.
- Migraciones Room: v5→v6 (`downloadProgress`), v6→v7 (`trackPosition`,
  ver más abajo).

**Bug del artista "-" (Moon Safari, Air):** YouTube devuelve
literalmente `"-"` como `uploader` en playlists auto-generadas de
YouTube Music (álbumes), y el código lo aceptaba como nombre de canal
válido, colándose como artista visible. `link_resolver.py` lo
normaliza ahora a cadena vacía (mismo tratamiento que canal ausente);
`ImportLinkViewModel.dominantArtist()` ignora canales en blanco. Nuevo
fallback `UNKNOWN_ARTIST_CREDIT` ("Artista desconocido"), distinto de
`VARIOUS_ARTISTS_CREDIT` ("Various Artists" → "Varios") — este último
se reserva para compilaciones genuinas con varios canales reales
distintos; usarlo cuando YouTube simplemente no dio el dato era
engañoso.

**Petición de producto de Miguel Ángel, implementada en la misma
sesión:** cuando `needsArtistConfirmation()` detecta que no hay
artista real determinable, "Importar enlace" pregunta Artista/Álbum
antes de descargar (diálogo prellenado con la mejor estimación) y
aplica la respuesta a todas las pistas seleccionadas de golpe, en vez
de guardar "Artista desconocido" en silencio.

**Orden real de disco en álbumes:** `LibraryViewModel` ordenaba
**siempre** alfabéticamente por título, nunca por posición real —
confirmado no relacionado con las pistas que parecían faltar, habría
pasado igual con el álbum completo. Nuevo campo `trackPosition`
(nullable, sin backfill), poblado por índice real en
`ImportLinkViewModel`/`AlbumSearchViewModel`; `recompute()` ordena por
posición cuando existe, cae a alfabético si no (sencillos,
reconciliadas desde disco).

**Bug estructural de carpetas ("un galimatías", según Miguel Ángel) —
el más importante encontrado en la sesión:** `DownloadWorker.doWork()`
llamaba a `DownloadDirManager.getOrCreateTrackDir()` con
`album = null` **hardcodeado**, sin importar el álbum real de la
pista. `DownloadDirManager` ya soportaba `{artista}/{álbum}/`
perfectamente — el bug era que nunca se le pasaba el dato. Resultado:
TODO lo descargado, incluso álbumes completos con su campo `album`
correcto en Room, acababa físicamente en `{artista}/Sencillos/` en
disco; Biblioteca lo mostraba bien agrupado porque lee de Room, pero
el disco nunca coincidía con lo que mostraba la app. Corregido de
extremo a extremo: `DownloadWorker` (nuevo `KEY_ALBUM`),
`DownloadQueueManager.enqueue()` (nuevo parámetro `album`), y los 4
llamantes (`SearchViewModel`, `AlbumSearchViewModel`,
`ImportLinkViewModel`, `DownloadsViewModel.retry`). **Solo afecta a
descargas nuevas** — los archivos ya en `Sencillos/` no se mueven
solos, hace falta borrar+redescargar.

**Herramienta de diagnóstico temporal:** sin acceso a `adb`/`logcat`
ni a un "Crashes & ANRs" funcional del sistema, se instaló un
`Thread.setDefaultUncaughtExceptionHandler` en `MiMooApp` que escribe
el stacktrace completo a `crash_log.txt` en la raíz de la carpeta SAF
(mismo patrón que `DownloadWorker.debug_error.txt`) — fue la única vía
para obtener el stacktrace real del NPE de `ConcurrentHashMap`.
Candidato a retirar en una sesión futura una vez asentado que no hacen
falta más diagnósticos de este tipo.

**Incidencia de higiene de repo:** un `__pycache__/*.pyc` generado por
la propia verificación de sintaxis local (`py_compile`) se coló en un
commit; retirado del repo y añadido a `.gitignore` en el commit
siguiente.

**Pregunta abierta de Miguel Ángel, sin resolver aún — búsqueda sin
cuota:** ¿existe alguna API gratuita de descarga/búsqueda de MP3
alternativa a la YouTube Data API? Respuesta dada en conversación, no
implementada: `yt-dlp` puede buscar directamente (`ytsearch:`, mismo
mecanismo que ya usa `link_resolver.py` para resolver enlaces) sin
tocar la Data API ni su cuota — migrar `SearchScreen`/`SearchViewModel`
a ese mecanismo eliminaría el problema de cuota de raíz. Pendiente de
decisión de Miguel Ángel sobre si se aborda como hito propio.

**Pendiente sin resolver del todo:** dos pistas de Moon Safari
("La femme d'argent", "Sexy Boy") parecían faltar tras la primera
importación completa; Miguel Ángel terminó borrando el resto de la
biblioteca antes de que se pudiera confirmar si era un fallo real de
descarga (ahora visible en la nueva sección "Con error" si vuelve a
pasar) o solo el límite de 15 de "Completadas recientemente" ocultando
entradas antiguas. Sin conclusión definitiva — vigilar si se repite.

---

## Hoja de Ruta para la Siguiente Sesión

**PASOS 1-5 y PASO 6a/6b/6d/6e/6f siguen sin repetirse — ya
implementados y verificados.** La Prioridad 1 de PASO 6c (cierre de
app al reimportar) queda CONFIRMADA RESUELTA — no repetir esa prueba
salvo que reaparezca. Queda el resto de PASO 6c, sin tocar en S003:

- **Reimportar Lou Reed - Transformer** (búsqueda por álbum, PASO 6e)
  y confirmar que empareja las 11 pistas de golpe vía playlist. Si
  falla, comprobar si el mensaje menciona cuota de YouTube agotada
  (proyecto `mimoo-501004`, resetea a medianoche hora del Pacífico).
- **Probar "Beethoven"/"Sinfonía" sueltos** (solo título, sin
  artista) en Buscar álbum — confirmar que se pide tracklist +
  emparejamiento, que las pistas se descargan solas, y que el orden
  de disco ahora es correcto (trackPosition, fix de S003).
- **"Importar enlace" con una playlist normal de YouTube** (no
  YouTube Music) y con un vídeo suelto — confirmar carátula cuando
  hay un único canal, y que la carpeta en disco ahora es
  `{artista}/{álbum}/` real (fix de S003), no `Sencillos/`.
- Si reaparecen pistas "perdidas" de un álbum, comprobar primero la
  nueva sección "Con error" de Descargas antes de investigar a
  ciegas.
- Decisión pendiente de Miguel Ángel: ¿se aborda ya la migración de
  `SearchScreen` a búsqueda vía `yt-dlp` (`ytsearch:`, sin cuota) como
  hito propio, o queda pospuesta?
- Recordatorio de housekeeping: el álbum Moon Safari ya descargado
  sigue físicamente en `Air/Sencillos/` (carpeta antigua) — borrar y
  redescargar cuando convenga para que se reubique en
  `Air/Air - Moon Safari [Full Album]/`.

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
- **PASO 6e** — emparejamiento vía playlist de YouTube + corrección de
  coste de cuota, ver detalle arriba.
- **PASO 6f** — "Importar enlace": nueva pantalla que resuelve un
  enlace de YouTube/YouTube Music (playlist/álbum o vídeo suelto)
  directamente con yt-dlp (`link_resolver.py`), sin tocar la YouTube
  Data API — coste de cuota cero. Petición explícita de Miguel Ángel:
  "aquí la búsqueda es externa". Preview con checkboxes, botones
  Reproducir (streaming, sin descargar) y Descargar (autodescarga,
  destacado visualmente a petición de Miguel Ángel). Asignación de
  artista: un solo canal → ese canal; canales distintos →
  `VARIOUS_ARTISTS_CREDIT`, agrupa bajo "Varios" en Biblioteca.
  Carátula real vía `CoverArtRepository` (MusicBrainz + Cover Art
  Archive) cuando hay un único canal detrás del álbum.
  **Bug real encontrado en pruebas de Miguel Ángel:** la app se
  cerraba al importar un álbum de YouTube Music (aceptaba el enlace,
  mostraba las pistas, y se cerraba). Dos capas de arreglo aplicadas,
  **ninguna verificada todavía en dispositivo** (ver PASO 6c): (a)
  yt-dlp descarta entradas sin `youtube_id` real (bonus tracks no
  disponibles, etc. — la hipótesis más probable, ya que un `null` en
  ese campo rompía un tipo no-nullable de Kotlin usado como `key` de
  `LazyColumn`), (b) `resolveCoverArt()` blindado con try/catch por
  ser una corrutina disparar-y-olvidar sin manejador de excepciones
  (coincide con que el cierre solo ocurría en listas, nunca en
  sencillos, ya que esa función solo se llama para listas).
- **Reorganización de Biblioteca** (no es parte de H05 en sentido
  estricto, pero ocurrió en esta misma sesión): pestañas
  Álbumes/Sencillos/Favoritos sustituyendo el confuso toggle
  Jerárquica/Plana + sort roto. Ver commit `ac85989`.
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

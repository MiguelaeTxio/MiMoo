# MIMOO — ANEXO HITO 06
# Exportar/Importar Repositorio de Música vía Google Drive

*Vive en `DOCS/ANNEX_H06.md` — flujo NewFlow Android. Ver estado en
`DOCS/MASTER_DOCUMENT.md`. EN PROGRESO.*

---

## NOTA DE APERTURA (S006, 2026-07-08)

Abierto a petición explícita de Miguel Ángel al incorporar una tablet
además del móvil como dispositivo de uso. Pausa H05 sin tocar su
anexo (PASO 6c sigue pendiente ahí). **Explícitamente independiente**
de cualquier futura función de "música relacionada" — son cosas
distintas, no confundir.

Decisiones de producto ya tomadas por Miguel Ángel en la conversación
de apertura:

1. **Alcance de la exportación: todo el repositorio.** No hay
   selección parcial de álbumes/artistas — se exporta la biblioteca
   completa siempre.
2. **Transporte: Google Drive.** No es un archivo que Miguel Ángel
   mueva a mano por cable/USB — la propia app sube y descarga el
   archivo de exportación contra Drive.
3. **Importación destructiva.** El repositorio del dispositivo
   destino **muere por completo** y se sustituye por la copia
   importada — no hay fusión ni resolución de conflictos pista a
   pista.
4. **Las pistas sintéticas (`local:...`) no se exportan.** No tienen
   vídeo real de YouTube detrás, así que no tiene sentido llevarlas a
   otro dispositivo donde no se podrán re-descargar — quedan excluidas
   por completo del `BackupBundle`, no solo del auto-encolado.

---

## OBJETIVO DEL HITO

Permitir exportar toda la biblioteca local (pistas, favoritos de
álbum, listas de reproducción — nunca los archivos de audio en sí) a
un archivo en Google Drive desde un dispositivo, e importarla en otro
dispositivo (p.ej. la tablet), sustituyendo por completo el
repositorio local destino y encolando automáticamente la descarga de
cada pista con los metadatos ya corregidos en origen — sin pasar por
el diálogo de edición de metadatos que hoy aparece en Búsqueda/Importar
enlace, porque ya no hace falta corregir nada.

---

## CONTEXTO TÉCNICO — ESQUEMA REAL A EXPORTAR (leído del clon, S006)

Cuatro entidades Room componen "todo el repositorio" (directriz §4.1
`MASTER_DOCUMENT.md` — nunca inferir de memoria, ya releídas en S006
desde `app/src/main/java/com/miguelaetxio/mimoo/data/local/entity/`):

- **`SearchResultTrack`** (tabla `search_result_tracks`, PK
  `youtubeId`) — el grueso del repositorio. Campos relevantes para
  exportar: `title`, `channelTitle`, `durationSeconds`, `thumbnailUrl`,
  `artist`, `album`, `isFavorite`, `coverArtUrl`, `trackPosition`,
  `sourceUrl`. **Campos que NUNCA se exportan tal cual, por ser
  específicos del dispositivo origen:** `filePath` (ruta SAF local),
  `downloadStatus` (se reimporta siempre como `PENDING`),
  `downloadProgress` (se reimporta siempre como `0`). `youtubeId` sí
  se exporta — es la clave real y estable, no depende del
  dispositivo. **Filas sintéticas excluidas del export:** las que
  tienen `youtubeId` con prefijo `local:` (generadas por
  `LibraryReconciler` para archivos pegados a mano sin fila real) se
  filtran antes de construir el `BackupBundle` — decisión de Miguel
  Ángel (S006), no viajan al backup en absoluto.
- **`FavoriteAlbum`** (tabla `favorite_albums`, PK compuesta
  `artist`+`album`) — se exporta/importa tal cual, sin transformación.
- **`Playlist`** (tabla `playlists`, PK `id` autogenerado) — `name` y
  `createdAt` se exportan; `id` **no** se reutiliza en el destino
  (autogenerado allí también), ver PASO 4 para el remapeo.
- **`PlaylistTrackCrossRef`** (tabla `playlist_track_cross_refs`, PK
  compuesta `playlistId`+`youtubeId`, ambas claves foráneas con
  `onDelete = CASCADE`) — se exporta con el `playlistId` de origen tal
  cual; en la importación debe traducirse al nuevo `id` autogenerado
  de la playlist correspondiente (ver PASO 4).

**Lo que NO se toca:** ningún archivo de audio viaja por Drive — solo
metadatos, igual que ya preveía `MASTER_DOCUMENT.md` §1 punto 6 antes
de este hito (respaldo opcional de metadatos, no de audio).

**Prerrequisito bloqueante, pendiente de Miguel Ángel antes de PASO
2:** integrar Google Drive requiere Google Sign-In (o Credential
Manager) + Drive REST API habilitada. Aún sin decidir/confirmar: (a)
si se reutiliza el proyecto de Google Cloud `mimoo-501004` (ya usado
para YouTube Data API v3) o se crea uno nuevo; (b) alta de un OAuth
Client ID tipo Android en la consola (requiere el SHA-1 del keystore
de firma real usado por el workflow de GitHub Actions, no el de un
`gradlew` local — ver `android-build`); (c) pantalla de consentimiento
OAuth (modo "Testing" con Miguel Ángel como usuario de prueba es
suficiente para uso personal, sin necesidad de verificación de
Google). **No se puede avanzar PASO 2 sin esto resuelto** — el modelo
no tiene acceso a la consola de Google Cloud.

**Directriz §4.5 aplica sin excepción:** antes de implementar la
integración con Drive REST API / Google Sign-In / Credential Manager,
actualizarse en línea obligatoriamente — son APIs que cambian de
forma de uso con frecuencia (Google ha migrado varias veces el
mecanismo de sign-in en Android en los últimos años).

---

## Estado de Tareas

| Paso | Descripción | Estado |
|---|---|---|
| 1 | Modelo de exportación — DTOs + serialización/deserialización JSON de las 4 tablas | PENDIENTE |
| 2 | Integración Google Drive — auth + subida/descarga de archivo | BLOQUEADO — prerrequisito de consola pendiente de Miguel Ángel |
| 3 | Pantalla Exportar | PENDIENTE |
| 4 | Pantalla Importar + lógica destructiva de sustitución | PENDIENTE |
| 5 | Auto-descarga tras importar, con metadatos ya fijados (sin diálogo de edición) | PENDIENTE |
| 6 | Verificación funcional end-to-end en dispositivo (móvil → tablet) | PENDIENTE |

---

## HOJA DE RUTA DETALLADA

### PASO 1 — Modelo de exportación

DTOs de serialización (paquete propio, p.ej.
`data/backup/BackupDto.kt`), separados de las entidades Room para no
acoplar el formato de archivo al esquema interno de la base de datos:

- `BackupBundle(version: Int, exportedAt: Long, tracks: List<TrackBackupDto>, favoriteAlbums: List<FavoriteAlbumBackupDto>, playlists: List<PlaylistBackupDto>)`
- `TrackBackupDto`: todos los campos de `SearchResultTrack` exportables
  listados arriba (sin `filePath`/`downloadStatus`/`downloadProgress`).
  **Filtrado obligatorio antes de mapear:** excluir toda fila cuyo
  `youtubeId` empiece por `local:` (filas sintéticas de
  `LibraryReconciler`) — no entran en `tracks`.
- `PlaylistBackupDto(originalId: Long, name: String, createdAt: Long, trackYoutubeIdsInOrder: List<String>)`
  — aplana `PlaylistTrackCrossRef` dentro de la propia playlist
  (lista ordenada por `position`) en vez de exportar la tabla de unión
  suelta; simplifica el remapeo de IDs en la importación (PASO 4).
  **Coherencia con el filtrado de pistas sintéticas:** si una playlist
  contenía una pista `local:...`, su `youtubeId` se omite de
  `trackYoutubeIdsInOrder` (esa pista no existe en `tracks`, así que
  una referencia a ella sería colgante en el destino) — el resto de
  la playlist se exporta igual, solo se acorta.
- Serialización con `kotlinx.serialization` o Gson (comprobar cuál ya
  está en `build.gradle.kts` antes de añadir una dependencia nueva —
  releer el archivo real, directriz §4.1).
- `version` empieza en `1`; cualquier cambio futuro de formato debe
  poder detectarse y, como mínimo, rechazarse con un mensaje claro en
  vez de fallar en silencio.

### PASO 2 — Integración Google Drive

Bloqueado por el prerrequisito de consola (ver arriba). Una vez
resuelto:

- Alcance OAuth mínimo: `drive.file` (la app solo ve/gestiona los
  archivos que ella misma crea, no todo el Drive del usuario).
- Carpeta fija en Drive, p.ej. `MiMoo Backups/`, creada si no existe.
- Nombre de archivo con timestamp:
  `mimoo_backup_{yyyyMMdd_HHmmss}.json`.
- Subida: `DriveRepository.uploadBackup(bundle: BackupBundle)`.
- Listado/descarga: `DriveRepository.listBackups(): List<DriveBackupFile>`
  (nombre + fecha) y `DriveRepository.downloadBackup(fileId): BackupBundle`.

### PASO 3 — Pantalla Exportar

Punto de entrada nuevo (menú principal o Ajustes — a decidir con
Miguel Ángel el sitio exacto en la sesión donde se implemente, no
inventar ubicación sin confirmar). Botón único "Exportar repositorio a
Drive": serializa las 4 tablas completas (PASO 1), sube (PASO 2),
confirma con fecha/hora del archivo subido. Sin selección parcial —
decisión ya tomada por Miguel Ángel.

### PASO 4 — Pantalla Importar + sustitución destructiva

- Lista los backups disponibles en la carpeta de Drive
  (`DriveRepository.listBackups()`), Miguel Ángel elige uno.
- Confirmación explícita antes de ejecutar — es destructivo e
  irreversible localmente: "Esto borrará todo tu repositorio actual y
  lo sustituirá por el importado. ¿Continuar?".
- Transacción Room (`@Transaction`) con, en este orden:
  1. Borrar todas las filas de `playlist_track_cross_refs`,
     `playlists`, `favorite_albums`, `search_result_tracks` (ese
     orden respeta las FK `CASCADE`, aunque borrar en cascada desde
     `search_result_tracks`/`playlists` ya arrastraría
     `playlist_track_cross_refs` — hacerlo explícito de todas formas
     para que la transacción sea clara y no dependa implícitamente de
     cascada).
  2. Insertar cada `TrackBackupDto` como `SearchResultTrack` con
     `filePath = null`, `downloadStatus = PENDING`,
     `downloadProgress = 0` — el resto de campos tal cual venían del
     backup.
  3. Insertar cada `FavoriteAlbumBackupDto` tal cual.
  4. Insertar cada `PlaylistBackupDto` como `Playlist` nueva
     (`id` autogenerado, **no** reutilizar `originalId`), guardando el
     mapeo `originalId → nuevoId` en memoria durante la transacción, y
     a partir de `trackYoutubeIdsInOrder` generar las filas de
     `PlaylistTrackCrossRef` con el `id` nuevo y `position` = índice en
     la lista.
- **Borrado físico de archivos de audio existentes en el destino:**
  dado que el repositorio "muere" por completo, los archivos `.opus`
  (u otros formatos reconciliados) que ya hubiera en la raíz SAF del
  dispositivo destino deben eliminarse también — si no, quedarían
  huérfanos en disco sin fila en Room, y una reconciliación futura
  (`LibraryReconciler`) los recrearía como filas sintéticas sin los
  metadatos importados, contradiciendo el propósito del hito. Recorrer
  la raíz SAF actual y borrar todo antes del PASO 5.

### PASO 5 — Auto-descarga tras importar

Reutiliza el mecanismo ya implementado en H05 PASO 6b Parte 1
(`DownloadQueueManager.enqueue()` por cada pista, mismo patrón que
`SearchViewModel.requestDownload()`) — sin pasar por ningún diálogo de
edición de metadatos, porque `artist`/`album`/`title` ya vienen
correctos del backup. Encolar automáticamente **todas** las pistas
importadas (no solo las que tenían `downloadStatus = DONE` en origen —
en destino todas empiezan en `PENDING`, es una redescarga completa por
diseño). Al quedar las filas `local:...` excluidas ya en el propio
export (ver CONTEXTO TÉCNICO y PASO 1), en el destino no puede
aparecer ninguna fila sin vídeo real detrás — no hace falta ningún
caso especial aquí.

### PASO 6 — Verificación funcional end-to-end

Con ambos dispositivos reales: exportar desde el móvil, importar en la
tablet, confirmar (a) repositorio previo de la tablet completamente
sustituido, (b) todas las pistas visibles en Biblioteca con
artista/álbum correctos sin haber tocado ningún diálogo de edición,
(c) descarga automática en curso/completada, (d) favoritos de álbum y
listas de reproducción presentes con el mismo contenido y orden que en
origen.

---

## Fuera de Alcance de Este Hito (explícitamente pospuesto)

- Exportación parcial (por álbum/artista/playlist) — decisión ya
  tomada por Miguel Ángel: siempre todo el repositorio.
- Fusión/merge de repositorios — la importación es siempre destructiva
  por decisión de producto, no una limitación técnica a resolver
  después.
- Transporte de archivos de audio en sí — solo metadatos, el audio se
  redescarga en destino.
- "Música relacionada" — hito futuro distinto, sin alcance definido,
  no tocar aquí.

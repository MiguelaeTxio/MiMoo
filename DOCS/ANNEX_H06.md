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

**Prerrequisito de Google Cloud — RESUELTO en S006 (2026-07-08):**
integrar Google Drive requiere Google Sign-In/Credential Manager +
Drive REST API habilitada. Decisiones y valores ya confirmados:

- Proyecto de Google Cloud reutilizado: `mimoo-501004` (el mismo que
  ya usa la YouTube Data API v3), sin crear uno nuevo.
- Google Drive API habilitada en ese proyecto.
- Google Auth Platform configurado: Audience en modo `Testing` con
  Miguel Ángel como Test user (evita verificación de Google, suficiente
  para uso personal); scope `drive.file` añadido en Data Access.
- OAuth Client ID tipo **Android** creado (`Clients` → package
  `com.miguelaetxio.mimoo` + SHA-1 real de la keystore de firma,
  obtenido del log del workflow de GitHub Actions — paso "Mostrar
  SHA-1 de firma", añadido en S006 junto a la verificación de
  SHA-256 ya existente):
  `652972961389-2cefjrssrhnd79hpo189f9bm99pvmcig.apps.googleusercontent.com`.
  Los clientes tipo Android no llevan client secret — el JSON que
  ofrece descargar la consola es solo un resumen de estos mismos
  datos, no hace falta guardarlo en el repositorio.

**Pendiente mecánico antes de PASO 2 (no bloquea código, es
infraestructura):** dar de alta el secret de GitHub Actions
`GOOGLE_OAUTH_ANDROID_CLIENT_ID` con el valor de arriba, mismo patrón
que `YOUTUBE_API_KEY` — inyectarlo en `local.properties` en el
workflow y exponerlo como
`BuildConfig.GOOGLE_OAUTH_ANDROID_CLIENT_ID`. Sin hacer todavía.

**Directriz §4.5 aplica sin excepción:** antes de implementar la
integración con Drive REST API / Google Sign-In / Credential Manager,
actualizarse en línea obligatoriamente — son APIs que cambian de
forma de uso con frecuencia (Google ha migrado varias veces el
mecanismo de sign-in en Android en los últimos años).

---

## Estado de Tareas

| Paso | Descripción | Estado |
|---|---|---|
| 1 | Modelo de exportación — DTOs + serialización/deserialización JSON de las 4 tablas | HECHO — S006 (`BackupDto.kt` + `BackupRepository.kt`, commit `4655895`) |
| 2 | Integración Google Drive — auth + subida/descarga de archivo | HECHO — S006 (`DriveAuthorizationHelper` + `DriveApiService`/`DriveUploadApiService` + `BackupDriveRepository`, commit `a514e4e`) |
| 3 | Pantalla Exportar | HECHO — S006 (`SettingsScreen`/`SettingsViewModel`, entrada "Ajustes" en el drawer, commit `72fdcec`) |
| 4 | Pantalla Importar + lógica destructiva de sustitución | HECHO — S006 (`BackupImportRepository`, listado+confirmación destructiva en `SettingsScreen`, commit `691ca65`) |
| 5 | Auto-descarga tras importar, con metadatos ya fijados (sin diálogo de edición) | HECHO — S006 (`SettingsViewModel.importNow()`, commit `691ca65`) |
| 6 | Verificación funcional end-to-end en dispositivo (móvil → tablet) | PENDIENTE — siguiente sesión |

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

### PASO 3 — Pantalla Exportar (HECHO, S006)

Punto de entrada decidido con Miguel Ángel: pantalla "Ajustes" nueva
(`SettingsScreen`), con entrada propia en el drawer principal — no un
ítem suelto en el menú. Botón único "Exportar repositorio a Drive":
construye el `BackupBundle` (PASO 1), pide autorización a Drive
(`DriveAuthorizationHelper` — silenciosa si ya estaba concedida,
diálogo de consentimiento vía `ActivityResultLauncher` si no) y sube
(PASO 2), confirmando con un Snackbar el nombre del archivo subido.
Sin selección parcial —
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

## COMPLETADAS EN S006

- H06 abierto de cero: DTOs de exportación (`BackupDto.kt`), lectura/
  escritura Room vía `BackupRepository` (PASO 1); integración real con
  Google Drive vía `AuthorizationClient`/`Identity` + Drive REST v3
  (`DriveAuthorizationHelper`, `DriveApiService`,
  `DriveUploadApiService`, `BackupDriveRepository`, PASO 2); pantalla
  "Ajustes" nueva con Exportar/Importar (`SettingsScreen`/
  `SettingsViewModel`, PASO 3-4); sustitución destructiva real sobre
  Room + borrado físico SAF (`BackupImportRepository`, PASO 4);
  auto-encolado de descargas tras importar (PASO 5).
- Prerrequisito de Google Cloud completo: proyecto `mimoo-501004`
  reutilizado, Drive API habilitada, scope `drive.file` añadido en
  Data Access, Miguel Ángel como Test user, Cliente OAuth Android
  creado con SHA-1 real (impreso ahora por el workflow de Actions,
  nuevo paso añadido este mismo día).
- Bug real de código encontrado y corregido en la verificación de
  PASO 3/4: `SettingsScreen` comprobaba `resultCode == RESULT_OK`
  antes de extraer el resultado de autorización, contra el patrón
  oficial de Google (que no hace esa comprobación) -- un consentimiento
  válido se trataba como cancelado, abandonando exportar/importar en
  silencio. Corregido y verificado que ya no se comporta así (ahora
  sí aparece un mensaje de error real).
- Doble mecanismo de diagnóstico añadido para H06: logging a Logcat
  (tags `MiMoo-Backup-*`) y log a archivo `backup_debug.txt` en la
  raíz SAF (`BackupDebugLogger`, mismo patrón que
  `NotificationDebugLogger`) -- este último a petición de Miguel Ángel,
  usando `notification_debug.txt`/`debug_error.txt`/`crash_log.txt`
  como ejemplo del mecanismo ya existente en la app. De paso,
  `BackupImportRepository` excluye ahora esos 4 archivos de
  diagnóstico del borrado destructivo (antes se borraban a sí mismos
  en cada importación).
- Verificación end-to-end en dispositivo (PASO 6) sigue bloqueada: ver
  sección de investigación abierta más abajo.

---

## INVESTIGACIÓN ABIERTA — H06 PASO 6, sin resolver al cierre de S006

**Petición explícita de Miguel Ángel para quien retome esto:**
investiga este error de cero. Las hipótesis que aparecen marcadas como
tal más abajo son conjeturas de S006, no conclusiones verificadas --
no las des por buenas sin más, ni asumas que el camino que falta es
simplemente "esperar" o "borrar caché". Contrástalo tú mismo con
documentación actual y con el código real antes de proponer nada.

### El hecho, sin interpretar

Al tocar "Exportar a Drive" en el móvil, tras elegir cuenta de Google
en el diálogo de consentimiento, la app recibe este error (capturado
literal en `backup_debug.txt`, ver también Logcat tag
`MiMoo-Backup-Auth`):

```
com.google.android.gms.common.api.ApiException: 8: [8] Unknown error [status=UNREGISTERED_ON_API_CONSOLE].
	at com.google.android.gms.internal.auth-api.zbad.getAuthorizationResultFromIntent(com.google.android.gms:play-services-auth@@21.6.0:6)
	at com.miguelaetxio.mimoo.data.backup.DriveAuthorizationHelper.extractAccessTokenFromResolution(DriveAuthorizationHelper.kt:160)
```

Ocurre siempre en el mismo punto: `authorize()` devuelve
`hasResolution() == true` (primera vez pidiendo el scope, o acceso
revocado), se lanza el `IntentSenderRequest`, el usuario completa el
diálogo de Google, y al volver
`Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(resultData)`
lanza esta `ApiException` en vez de devolver un resultado con
`accessToken`.

### Comprobaciones ya hechas en S006, con su resultado real (hechos, no hipótesis)

Estas comprobaciones SÍ están hechas con evidencia (capturas de
pantalla de la consola de Google Cloud contrastadas carácter a
carácter donde aplica) -- no hace falta repetirlas de cero, pero sí
vale la pena que quien retome esto las re-verifique con sus propios
ojos si tiene alguna duda, en vez de fiarse de esta nota:

- SHA-1 del cliente Android en Google Cloud === SHA-1 real de la
  keystore de firma (impreso por el workflow de Actions) -- coinciden
  carácter a carácter.
- Package name del cliente: `com.miguelaetxio.mimoo` -- coincide con
  `applicationId` real del proyecto.
- Proyecto de Google Cloud del cliente OAuth: `miMoo` (`mimoo-501004`)
  -- mismo proyecto donde está habilitada la Drive API, confirmado por
  captura de pantalla del selector de proyecto.
- `Google Auth Platform` → `Audience`: modo `Testing`, con la cuenta de
  Gmail de Miguel Ángel añadida como Test user.
- `Google Auth Platform` → `Data Access`: scope
  `.../auth/drive.file` presente y guardado.
- `APIs & Services` → `Enabled APIs and services`: Google Drive API
  aparece como habilitada (`Enabled`) en el proyecto `miMoo`.
- El código de la llamada (`AuthorizationRequest.Builder`,
  `Identity.getAuthorizationClient()`, manejo del
  `IntentSenderRequest`, extracción vía
  `getAuthorizationResultFromIntent()`) se contrastó línea a línea
  contra la documentación oficial vigente
  (`developer.android.com/identity/authorization`) y coincide con el
  patrón oficial actual -- no se encontró ninguna discrepancia.

### Hipótesis de S006 -- NO VERIFICADAS, no dar por buenas

- **Caché negativa de Play Services en el dispositivo.** Sin probar
  todavía si borrar la caché de Google Play Services (o reiniciar el
  móvil) cambia algo.
- **Propagación del cliente OAuth recién creado.** Basada en un hilo
  de GitHub (`googlesamples/google-services#381`) sobre el mismo
  código de error en general, no específico de este proyecto ni de
  esta API (`AuthorizationClient`/Identity, no el
  `GoogleSignInClient` clásico al que se refiere la mayoría de
  reportes de ese hilo). No hay ninguna confirmación de que el tiempo
  transcurrido sea la causa real aquí.

Ninguna de las dos se ha confirmado ni descartado con una prueba real
tras agotar el tiempo de espera. Quien retome esto debería, como
mínimo, cuestionar si `UNREGISTERED_ON_API_CONSOLE` con
`AuthorizationClient`/`Identity` (API relativamente nueva, distinta de
`GoogleSignInClient`) tiene alguna causa documentada específica que
S006 no encontró -- toda la investigación de S006 se apoyó en
resultados de búsqueda sobre el `GoogleSignInClient` clásico y
`ApiException: 10`, no en documentación específica de `Identity`/
`AuthorizationClient` para este código exacto.

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

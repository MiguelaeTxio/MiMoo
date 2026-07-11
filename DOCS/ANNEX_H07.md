# MIMOO — ANEXO HITO 07
# Persistencia de Enlaces + Sincronización Automática + Actualizaciones In-App + Controles de Reproducción

*Vive en `DOCS/ANNEX_H07.md` — flujo NewFlow Android. Ver estado en
`DOCS/MASTER_DOCUMENT.md`. EN PROGRESO.*

---

## NOTA DE REDEFINICIÓN (S008, 2026-07-10)

Este anexo **sustituye por completo** el diseño abierto en S007. No es
un ajuste menor: el diagnóstico de un bug real (export/import vacíos)
destapó un problema de fondo que obliga a rehacer el planteamiento
entero del hito.

### Qué pasó

Miguel Ángel reportó que "Exportar a Drive" subía un JSON vacío
(`tracks: []`, `favoriteAlbums: []`, `playlists: []`), y por tanto
"Importar" no traía nada. Investigado con código real (no memoria):

1. `BackupRepository.buildCurrentBundle()` filtra explícitamente
   cualquier `SearchResultTrack` cuyo `youtubeId` empiece por
   `local:` (decisión de S006, ver histórico en `ANNEX_H06.md`) — son
   filas sintéticas que `LibraryReconciler` genera para archivos
   encontrados en disco sin fila en Room.
2. `LibraryReconciler.rescan()` genera esas filas sintéticas
   **solo** la primera vez que se elige la carpeta SAF (el
   comentario de cabecera de `LibraryReconciler.kt` decía "en CADA
   arranque" pero es un docstring obsoleto — el código real de
   `MainActivity.kt`, corregido el 2026-07-05, confirma que solo
   dispara en el primer picker, más el botón de refresco manual).
   Es exactamente lo que ocurre tras una reinstalación: Room vive en
   almacenamiento interno (se borra al desinstalar), la carpeta SAF
   externa no — al volver a elegirla, todo lo que había se reconstruye
   como sintético.
3. **Causa raíz real, más profunda que el filtro de S006:**
   `downloader.py`/`DownloadWorker.kt` nunca grabaron el `youtubeId`
   ni la URL de origen en ningún sitio del archivo `.opus` en sí —
   solo en la fila de Room. Confirmado leyendo `downloader.py`
   completo: no hay postprocesador de metadatos, ni `-metadata`, ni
   ningún tag con el id o la URL. El link **solo existía en Room**.
   Con varias reinstalaciones sucesivas durante H06 (migración de
   dispositivo, fix de `allowBackup`), Room se vació y el disco
   reconstruyó sintéticamente toda la biblioteca sin links reales —
   de ahí el JSON vacío al exportar.

No había backup antiguo en Drive con datos reales que rescatar
(confirmado con Miguel Ángel). La biblioteca actual del dispositivo
se da por perdida a efectos de link real; Miguel Ángel la borra él
mismo (Room + archivos) para partir de cero, fuera de esta sesión de
trabajo — no es una tarea de este modelo.

### Decisión de fondo

El link de YouTube tiene que persistir **en el propio archivo**, no
solo en Room, para que sobreviva a cualquier reinstalación. Sin eso,
ni el backup manual (H06) ni ninguna sincronización automática pueden
funcionar de verdad — es la base de todo lo demás, por eso pasa a ser
la PARTE 0 de este hito, antes que cualquier otra cosa.

Aprovechando que hay que rehacer esto, se amplía el alcance del hito
para cerrar de una vez varios pendientes que Miguel Ángel quiere
resueltos juntos, sin dilatarlo en sesiones sueltas.

---

## OBJETIVO DEL HITO (redefinido)

### PARTE 0 — Persistencia del link dentro del archivo (prerrequisito de todo lo demás)

Grabar el `youtubeId` (o la URL) como metadato embebido en cada
`.opus` en el momento de la descarga, para que el archivo lleve su
propio origen pegado encima, sobreviva o no Room a una desinstalación.
`LibraryReconciler.buildSyntheticTrack()` pasa a intentar leer ese
metadato al reconciliar un archivo huérfano; solo si no lo encuentra
(archivo de antes de este fix, o copiado a mano desde fuera de MiMoo)
recurre al hash `local:` como hasta ahora.

**Pendiente de verificar en línea (directriz §4.5 del
`MASTER_DOCUMENT.md`) antes de implementar:** qué tag de metadato
Opus/Vorbis Comment es el más robusto para guardar un campo propio
(p.ej. `MIMOO_YOUTUBE_ID` o reutilizar `COMMENT`), y qué API de
Android permite leerlo de vuelta de forma fiable
(`MediaMetadataRetriever` tiene soporte limitado a claves estándar —
puede hacer falta parsear el bloque de comentario Ogg/Vorbis a mano,
que es un formato simple, o evaluar una librería ligera). Este anexo
no fija la solución técnica exacta todavía — se decide al empezar a
codificar esta parte, con la información más actual disponible.

### PARTE 1 — Sincronización automática entre dispositivos (redefinición S008, tercera vuelta — regla de negocio completa de Miguel Ángel)

**Sustituye por completo el diseño de "espejo con diff granular"** de
la segunda vuelta de S008 (`MirrorDiff`/altas-bajas independientes),
tras un bug real reproducido con dos dispositivos: un dispositivo que
solo descargaba pistas (sin tocar favoritos/playlists, los únicos
puntos enganchados entonces) nunca avisaba a Drive de nada, así que
otro dispositivo veía esas pistas como "borradas en otro sitio" en vez
de "nunca subidas". La causa raíz era estructural, no un detalle
suelto: podía existir un estado "solo local, todavía sin subir".

**Regla de negocio (dictada por Miguel Ángel, verbatim resumido):**

1. **Ninguna mutación puede quedar sin reflejar en la nube.** Añadir o
   quitar una pista/favorito/playlist **requiere conexión de red en
   el momento**, sin excepción. Sin conexión, la acción **no se
   ejecuta en absoluto — ni siquiera en local**; se avisa y se pide
   esperar a recuperar la conexión. La app se puede seguir *usando*
   sin conexión con total normalidad (escuchar lo ya descargado,
   navegar la biblioteca) — la restricción es solo sobre *modificar*
   contenido. Con esto, un estado "solo local, sin subir" deja de
   poder existir por construcción.
2. **Cada dispositivo tiene una identidad estable** (UUID generado y
   persistido la primera vez, `DeviceIdentityManager`) y la copia de
   respaldo en Drive va envuelta con quién la subió y cuándo
   (`SyncEnvelope`, ver más abajo) — necesario para poder distinguir
   los casos 2 y 3 de abajo.
3. **Al arrancar la app**, exactamente uno de estos tres casos:
   - **No hay copia en Drive todavía** → este dispositivo la crea
     (con su identidad y la hora), sin preguntar nada.
   - **Hay copia, y la hizo ESTE MISMO dispositivo** → deberían
     coincidir. Si no coinciden, es que el disco local se
     desincronizó por su cuenta (archivos tocados a mano fuera de la
     app, etc.) — **la nube manda siempre en este caso, sin
     preguntar**: se restaura y se avisa. Nunca se pregunta cuando el
     desfase es contra la propia copia del dispositivo.
   - **Hay copia, y la hizo OTRO dispositivo** → aquí sí se pregunta
     explícitamente: *"¿se han añadido o eliminado pistas desde otro
     dispositivo?"*. Responder que sí sustituye lo local por la copia
     de Drive; responder que no sustituye la copia de Drive por lo
     local (y se sube). Tras resolver, el disco se deja exactamente
     igual que la copia ganadora.
4. **Todo-o-nada, nunca fusión parcial**: a diferencia del diseño
   anterior, aquí una de las dos copias completas sustituye siempre a
   la otra entera — reutiliza
   `BackupImportRepository.importDestructively()` (ya construido para
   H06) para el lado "la nube gana". Esto además resuelve gratis el
   problema que quedaba pendiente en la segunda vuelta (contenido de
   playlists coincidentes por nombre sin comparar): ya no hace falta
   comparar contenido, se sustituye entero.

**Distinto de Exportar/Importar (H06):** aquellos siguen siendo
manuales, a petición explícita desde Ajustes, generan un archivo nuevo
con timestamp cada vez (histórico de snapshots), y usan un
`BackupBundle` pelado. La copia de respaldo automática de H07 usa un
**archivo fijo** (`mimoo_sync_state.json`, carpeta "MiMoo Sync") que
se sobreescribe en su sitio, envuelto en un `SyncEnvelope`
(`deviceId`+`deviceLabel`+`timestamp`+`bundle`).

**Hallazgo técnico que desbloqueó el diseño (verificado en línea,
S008):** `Identity.getAuthorizationClient()` acepta un `Context`
normal, no exige una `Activity` real (confirmado contra un ejemplo
oficial de Google que lo invoca con `context` a secas) — esto permite
que `DownloadWorker`, un `CoroutineWorker` en segundo plano sin
`Activity` disponible, pueda pedir el token y empujar el cambio él
mismo. Antes se asumía que esto era imposible; no lo era.
`DriveAuthorizationHelper.requestAuthorization()` pasó de aceptar
`Activity` a aceptar `Context`.

### PARTE 2 — Actualizaciones in-app vía repositorio GitHub dedicado + PIN de acceso

**Redefinido dentro de la propia S008**, tras probar primero la vía de
EnterpriseBot (implementada, revertida en el mismo commit — ver
`ENTERPRISEBOT_ATTACHED_MILESTONE_V17.md`, NOTA DE DESVÍO pendiente al
cierre de esa sesión) y descartarla por decisión explícita de Miguel
Ángel: no quiere mezclar un proyecto personal dentro de la aplicación
cliente de EnterpriseBot.

**Diseño nuevo — dos piezas independientes:**

**A) Distribución del APK — repositorio GitHub nuevo, público, dedicado**

Un repositorio nuevo (nombre a decidir con Miguel Ángel, p.ej.
`MiMoo-Releases`), **público**, que **solo aloja los binarios
compilados vía GitHub Releases — nunca el código fuente de MiMoo**
(ese sigue exclusivamente en el repo privado actual). El propio
workflow de MiMoo (`build-and-deploy.yml`), tras compilar, publica una
Release nueva en ese repositorio con el `.apk` y un `manifest.json`
como assets. Las URLs de descarga de una Release son estables, sirven
el binario directo (sin páginas intermedias, a diferencia de Google
Drive — ver más abajo) y no necesitan servidor ni infraestructura
propia que mantener.

Se evaluó y descartó explícitamente:
- **EnterpriseBot** (diseño de la primera vuelta de S008): descartado
  por Miguel Ángel, no quiere mezclar proyectos.
- **Google Drive**: descartado tras verificación en línea (S008) —
  Drive muestra una página de aviso ("no se puede escanear en busca
  de virus") para archivos por encima de ~25 MB, un APK la supera con
  facilidad. Existen workarounds con un parámetro `confirm=` en la
  URL, pero es un comportamiento no oficial y frágil pensado para
  navegador, no para un `GET` programático limpio desde la propia app.

**Pendiente de Miguel Ángel antes de implementar esta parte:**
1. Crear el repositorio nuevo, público, vacío.
2. Crear un Personal Access Token fine-grained con acceso de escritura
   **solo** a ese repositorio nuevo (nunca al repo privado de MiMoo).
3. Añadir ese token como secret de GitHub Actions **en el repositorio
   privado de MiMoo** (el que ejecuta el workflow), con un nombre tipo
   `RELEASES_REPO_TOKEN` — el `GITHUB_TOKEN` automático de Actions
   solo tiene permisos sobre el repo donde corre, no sobre un
   repositorio distinto.

**B) PIN de acceso a la propia app**

Independiente de cómo se distribuya el APK: la app, en su primer
arranque tras instalarse (o tras cualquier reinstalación, ya que el
estado vive en `SharedPreferences`/`DataStore`, que se borra igual que
Room), muestra una pantalla de PIN de 4 dígitos que bloquea el acceso
a cualquier otra pantalla hasta introducirlo correctamente. PIN fijo
`0485`, embebido en la app (no configurable desde la UI). Texto del
campo: **"Introduce tu PIN, Silvia"**, literal, igual en ambos
dispositivos — decisión explícita de Miguel Ángel, no condicionado a
quién use el dispositivo.

**Precisión técnica importante, ya aclarada con Miguel Ángel:** esto
no bloquea la instalación del APK en sí — eso lo controla Android, no
hay forma de que código de la propia app intervenga antes de que el
sistema operativo termine de instalarla. Lo que sí hace es dejar la
app completamente inservible sin el PIN correcto desde el instante en
que se abre por primera vez, que es el efecto práctico que se busca.

### PARTE 3 — Controles de reproducción: aleatorio y cíclico

Confirmado en el código actual del reproductor (S008): no existe hoy
ningún control de `repeatMode` ni `shuffleMode`. Se añaden dos modos
independientes y combinables, sobre ExoPlayer/Media3 (soporte nativo,
no hay que reinventar nada):

- **Cíclico (repeat):** al llegar al final de la cola de reproducción
  actual, vuelve a empezar por la primera pista — `Player.REPEAT_MODE_ALL`.
- **Aleatorio (shuffle):** orden aleatorio dentro de la cola de
  reproducción actual, sin pararse nunca al terminar la cola —
  requiere `shuffleModeEnabled = true` combinado con
  `REPEAT_MODE_ALL` (shuffle solo, sin repeat, sí llega al final y se
  para; la combinación es la que da el comportamiento "no para
  nunca" que describe Miguel Ángel).
- Caso descrito explícitamente por Miguel Ángel: cíclico activado +
  cola construida progresivamente añadiendo pistas sueltas (p.ej. 200
  canciones vistas/añadidas durante la sesión) → al llegar a la
  pista 200, vuelve a la 1 y repite la cola completa en el mismo
  orden en que se añadieron.
- UI: dos controles (toggle) en la pantalla del reproductor, junto a
  play/pausa/siguiente/anterior — diseño visual a definir al
  implementar esta parte, sin bloquear el resto del hito.

---

## HOJA DE RUTA DETALLADA

### PARTE 0 — Persistencia del link

**PASO 0.1** — Actualizarse en línea (directriz §4.5) sobre la forma
más robusta de embeber y leer un campo de metadato custom en un
contenedor Ogg/Opus desde Android/Kotlin y desde yt-dlp/ffmpeg (via
Chaquopy). Cerrar la decisión técnica exacta antes de tocar código.

**PASO 0.2** — `downloader.py`/`DownloadWorker.kt`: grabar el
`youtubeId` (y, si es barato hacerlo a la vez, título/artista/álbum)
como metadato del archivo en el propio paso de conversión a Opus, sin
necesitar una segunda pasada de ffmpeg si es evitable.

**PASO 0.3** — `LibraryReconciler.buildSyntheticTrack()`: leer el
metadato embebido al reconciliar un archivo huérfano; usar el
`youtubeId` real si está presente, caer al hash `local:` solo si no
hay metadato (archivo legacy o ajeno a MiMoo).

**PASO 0.4** — Verificación funcional: descargar una pista nueva,
comprobar el metadato con una herramienta externa (p.ej. `ffprobe` si
está disponible, o inspección manual), borrar su fila de Room a mano
y forzar un refresco de Biblioteca — confirmar que recupera el
`youtubeId` real, no uno `local:`.

### PARTE 1 — Sincronización automática (tercera vuelta, regla de negocio completa)

**PASO 1.1 ✅ (S008, tercera vuelta)** — `DeviceIdentityManager`
(UUID estable por dispositivo, persistido en SharedPreferences) +
`SyncEnvelope` (`BackupDto.kt`: `deviceId`+`deviceLabel`+`timestamp`+
`bundle`) + `BackupRepository.toSyncJson()`/`fromSyncJson()`. Carpeta
fija "MiMoo Sync" + archivo fijo `mimoo_sync_state.json` en
`BackupDriveRepository` (`ensureSyncFolder()`/`findSyncFile()`/
`pushSyncState()`/`pullSyncState()`) — sin cambios respecto a la
segunda vuelta, solo que ahora el JSON que viaja es un `SyncEnvelope`,
no un `BackupBundle` pelado.

**PASO 1.2 ✅ (S008, tercera vuelta)** — `NetworkConnectivityChecker`
(`ConnectivityManager`, nuevo permiso `ACCESS_NETWORK_STATE`) +
`AutoSyncPusher.executeIfConnected(context, mutation)`, que sustituye
por completo a `pushIfAuthorized()` de la segunda vuelta: comprueba
conexión **antes** de ejecutar la mutación (si no hay, la mutación NO
se ejecuta, ni siquiera en local — `MutationOutcome.NoConnection`), y
si la hay, ejecuta la mutación y sube el estado completo como
`SyncEnvelope` con la identidad de este dispositivo.

**Hallazgo que desbloqueó el punto más difícil:** verificado en línea
que `Identity.getAuthorizationClient()` acepta `Context`, no exige
`Activity` — `DriveAuthorizationHelper.requestAuthorization()` pasó de
`Activity` a `Context`. Esto permitió enganchar también a
`DownloadWorker` (antes descartado como "imposible sin Activity").

**Enganchado, todos con el mismo patrón (`executeIfConnected` +
`MutationOutcome.NoConnection` mostrado como Snackbar/aviso):**
- `LibraryViewModel`: `toggleFavoriteAlbum()`, `deleteDownload()`,
  `deleteAlbum()`, `deleteArtist()`.
- `PlaylistsViewModel`: `createPlaylist()`, `deletePlaylist()`.
  `renamePlaylist()` sigue sin empujar a propósito — ya no importa el
  motivo original (el diff por nombre), ahora es irrelevante porque
  todo-o-nada sustituye la copia entera igualmente en el siguiente
  arranque si hay cualquier otro cambio; un renombrado solo, aislado,
  sin ningún otro cambio, seguiría sin reflejarse hasta la próxima
  mutación real -- aceptado como limitación menor, no bloqueante.
- `PlaylistDetailViewModel.removeTrack()` — **ya NO aplazado** (a
  diferencia de la segunda vuelta): con el diseño todo-o-nada ya no
  hace falta comparar contenido de playlists, así que dejó de ser un
  problema.
- `AddToPlaylistDialogViewModel.addToExistingPlaylist()`/
  `createPlaylistAndAdd()` — el diálogo ya no se cierra solo al
  pulsar; espera a que la operación aplique de verdad antes de
  cerrarse, y muestra el aviso de sin conexión dentro del propio
  diálogo si hace falta.
- `DownloadWorker` — **ya NO aplazado** (a diferencia de la segunda
  vuelta, donde se creía imposible sin `Activity`): los dos puntos
  donde se marca una pista `DONE` (descarga real completada, y el
  caso idempotente de archivo ya existente) pasan por
  `executeIfConnected(applicationContext)`; si no hay conexión justo
  en ese instante, no se compromete en Room y el `Worker` devuelve
  `Result.retry()` en vez de `Result.success()`.

**PASO 1.3 ✅ (S008, tercera vuelta)** — `AutoSyncViewModel.runSync()`
reescrito por completo con la máquina de 3 casos:
1. Sin copia en Drive → se crea (`pushAsNewEnvelope()`), sin preguntar.
2. Copia de este mismo dispositivo, no coincide → la nube gana
   siempre, sin preguntar (`importRepository.importDestructively()`,
   reutilizado tal cual de H06).
3. Copia de otro dispositivo → `AutoSyncUiState.ConflictOtherDevice`,
   espera respuesta explícita.

`BackupMirrorRepository` ya no calcula un diff granular
(`MirrorDiff` retirado por completo) — solo compara dos
`BackupBundle` completos por conjuntos de claves y devuelve un
`BundleComparison` (¿son iguales?, cuántas pistas/favoritos/playlists
tiene cada lado), usado solo para saber si hay diferencia y para el
texto del aviso.

**PASO 1.4 ✅ (S008, tercera vuelta)** — Tres `AlertDialog` en
`MainActivity.kt`, uno por caso: `ConflictOtherDevice` (pregunta
sí/no, no descartable sin responder — `onDismissRequest = {}`),
`RestoredFromCloud` (informativo, un botón "Vale"), `Done` con mensaje
opcional (solo se muestra si hay mensaje que dar; el caso 1
silencioso y el caso "idéntico, nada que hacer" no muestran nada).

**PASO 1.5** — Verificación funcional end-to-end con dos dispositivos
reales: descargar una pista suelta en uno (sin tocar favoritos ni
playlists) y confirmar que el otro la ve al siguiente arranque —
**el escenario exacto que falló en la segunda vuelta**, ahora debería
funcionar de punta a punta gracias al enganche de `DownloadWorker`.
**Pendiente por Miguel Ángel.**

### PARTE 2 — Actualizaciones in-app + PIN de acceso

**PASO 2.1** — Pendiente de Miguel Ángel: nombre y creación del
repositorio nuevo público, PAT de solo ese repo, y el secret
`RELEASES_REPO_TOKEN` en el repo privado de MiMoo (ver OBJETIVO DEL
HITO arriba, apartado A). Bloqueante para el resto de esta parte.

**PASO 2.2** — Generar el manifiesto de versión en el workflow de
MiMoo (`build-and-deploy.yml`), tras `assembleDebug`:
`{"versionCode": N, "versionName": "X.Y", "apkUrl": "..."}`, usando
`versionCode` desde `-PversionCode=${{ github.run_number }}` (ya
existe, ver `android-deploy`).

**PASO 2.3** — Publicar Release en el repositorio nuevo desde el
workflow de MiMoo (`softprops/action-gh-release` o equivalente
verificado en línea en su momento — §4.5), adjuntando `.apk` y
`manifest.json` como assets, usando `RELEASES_REPO_TOKEN` del
PASO 2.1.

**PASO 2.4** — `AppUpdateRepository` en MiMoo: `GET` al
`manifest.json` de la última Release (URL pública, sin token, sin
autenticación — el repo es público), comparar `versionCode` contra
`BuildConfig.VERSION_CODE`.

**PASO 2.5** — UI en Ajustes: "Buscar actualizaciones", muestra
versión actual vs. disponible, botón de descarga solo si Miguel Ángel
confirma. Instalación vía `FileProvider` + `Intent(ACTION_VIEW)`
(fuentes desconocidas, MiMoo no está en Google Play).

**PASO 2.6** — Verificación funcional: publicar una versión de prueba
con `versionCode` mayor, confirmar detección, descarga e instalación
correcta sobre la instalada.

**PASO 2.7 — PIN de acceso (independiente de 2.1-2.6, sin
bloqueantes, puede implementarse ya):**
- Pantalla de PIN de 4 dígitos, previa a cualquier otra pantalla de la
  app, mostrada mientras no exista un flag "desbloqueado" persistido
  (`DataStore`/`SharedPreferences`).
- PIN correcto fijo: `0485`, embebido en el código (no configurable
  desde la UI, no viaja en `DOCS/*.md` ni en ningún sitio en claro
  aparte del propio código fuente — mismo criterio que cualquier
  constante de la app).
- Texto exacto del campo: "Introduce tu PIN, Silvia" — literal, en
  los dos dispositivos.
- Comparación en tiempo constante (mismo criterio que
  `BackupRepository`/tokens del proyecto — evitar canales laterales
  de tiempo, aunque el riesgo real aquí es bajo).
- Sin límite de intentos ni bloqueo temporal salvo que Miguel Ángel lo
  pida explícitamente — no estaba en el encargo original.

### PARTE 3 — Controles de reproducción

**PASO 3.1 ✅ (S008)** — `PlayerManager.toggleRepeatMode()`/
`toggleShuffleMode()`, sobre `player.repeatMode`/
`player.shuffleModeEnabled` de ExoPlayer directamente (sin booleanos
propios mantenidos a mano) — `PlaybackState` expone
`repeatModeEnabled`/`shuffleModeEnabled`, sincronizados vía
`onRepeatModeChanged`/`onShuffleModeEnabledChanged` como única fuente
de verdad.

**PASO 3.2 ✅ (S008)** — Dos `IconButton` (Repeat/Shuffle) en
`PlayerBar.kt`, junto a anterior/reproducir-pausar/siguiente, con
tinte de color cuando están activos. De paso, "anterior"/"siguiente"
dejan de deshabilitarse en los extremos de la cola cuando el modo
cíclico está activo (antes se deshabilitaban siempre en la primera/
última pista, lo cual no tenía sentido con cíclico encendido).

**PASO 3.3** — Verificación funcional: cola larga (~200 pistas)
añadida progresivamente, cíclico activado, confirmar que al terminar
vuelve a la primera en el mismo orden; aleatorio activado (con y sin
cíclico), confirmar que no se detiene al agotar la cola cuando ambos
están activos. **Pendiente en dispositivo real por Miguel Ángel.**

---

## Fuera de Alcance de Este Hito (explícitamente pospuesto)

- Resolución de conflictos más allá de "la copia de respaldo manda"
  en la sincronización automática — no hay fusión inteligente de
  cambios concurrentes en dos dispositivos sin conexión a la vez,
  más allá del aviso antes de borrar.
- Reetiquetado retroactivo de la biblioteca existente con metadatos —
  la biblioteca actual del dispositivo se pierde y se reconstruye
  desde cero (decisión de Miguel Ángel, S008); Parte 0 solo afecta a
  descargas hechas a partir de su implementación.
- Sincronización en segundo plano fuera de apertura de app/inicio de
  sesión (WorkManager periódico) — posible iteración futura, no parte
  de este hito.
- Actualización silenciosa/automática de la app sin confirmación
  explícita de Miguel Ángel.
- Publicación en Google Play — fuera de alcance del proyecto entero.

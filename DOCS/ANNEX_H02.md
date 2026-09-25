# MIMOO — ANEXO HITO 02
# Descarga a Local: yt-dlp + Opus + Queue

*Vive en `DOCS/ANNEX_H02.md` — flujo NewFlow Android. Ver estado en
`DOCS/ANNEX_ROUTER.md`.*

---

## OBJETIVO DEL HITO

Descarga real de audio desde YouTube en formato Opus mediante yt-dlp
+ FFmpeg ejecutados como subprocesos (Chaquopy), reaprovechando el
mismo módulo yt-dlp del Hito 01. Cola de descarga persistida en Room
(WorkManager), estados PENDING → DOWNLOADING → DONE / ERROR,
almacenamiento SAF en `{raíz}/{artista}/{álbum}/titulo.opus`.

---

## COMPLETADAS EN S003 (2026-07-01)

**Bloque 1 — Motor de descarga**
- `SearchResultTrack` ya tenía `filePath`/`downloadStatus`/
  `DownloadStatus`. `MiMooApp.kt` con `HiltWorkerFactory` +
  `Configuration.Provider`.
- `DownloadWorker.kt`: `@HiltWorker`, `CoroutineWorker`, actualiza
  estado en Room en cada transición, ejecuta yt-dlp vía Chaquopy.
- `DownloadQueueManager.kt`: `@Singleton`,
  `OneTimeWorkRequestBuilder<DownloadWorker>`, `addTag(youtubeId)`.
- `downloader.py`: `download_audio(url, path)` con `yt_dlp.YoutubeDL`,
  `FFmpegExtractAudio` a opus.

**Bloque 2 — UI de descarga**
- `SearchViewModel`: `_currentYoutubeIds` +
  `flatMapLatest` sobre Room para reflejar cambios de
  `downloadStatus` en tiempo real. `requestDownload()` encola vía
  `downloadQueueManager.enqueue(youtubeId, title, artist)`.
- `SearchScreen`: `DownloadButton` con estado visual por
  `DownloadStatus` (PENDING/DOWNLOADING/DONE/ERROR).

**Bloque 3 — SAF storage**
- Diagnóstico: `WRITE_EXTERNAL_STORAGE` no aplica en API 29+.
- `StorageManager.kt` (nuevo): persiste Uri SAF, `takePersistableUriPermission`.
- `DownloadDirManager` reescrito con `DocumentFile.fromTreeUri()`.
- `MainActivity`: lanza `OpenDocumentTree` la primera vez.

**Bloque 4 — ffmpeg binario nativo**
- ffmpeg 8.0.1 ARM64 estático LGPL →
  `jniLibs/arm64-v8a/libffmpeg_bin.so`.
- `packaging { jniLibs { useLegacyPackaging = true } }` (W^X en
  Android 14+).
- `downloader.py`: `ffmpeg_location`, `http_headers` con User-Agent
  de navegador real (evita HTTP 403 de YouTube).

**Verificación final:** descarga completada con éxito, confirmada por
Miguel Ángel. Hito 02 verificado funcionalmente.

---

## Nota de sesión S036 (durante la sesión de H12 EN PROGRESO -- mantenimiento transversal, sin PCH)

Hito ya cerrado y verificado (S003), pero con trabajo real nuevo sobre
la cola de descarga durante esta sesión -- se documenta aquí por
indicación explícita de Miguel Ángel al cierre ("todo lo que se ha
hecho se fija en su hito correspondiente"), sin reabrir el hito.

**Límite de descargas simultáneas reales (S074):** petición explícita
de Miguel Ángel -- "si me descargo una lista de reproducción con 400
temas, se ponen demasiados descargando a la vez y enlentece mucho".
Causa real: `DownloadWorker.doWork()` corre en `Dispatchers.IO` (pool
de 64 hilos) sin ningún límite de concurrencia propio, y `WorkManager`
tampoco impone ninguno. Nuevo `DownloadConcurrencyLimiter` (semáforo
`@Singleton` compartido por todo el proceso) -- como mucho 3 descargas
reales corren a la vez, el resto espera suspendida sin coste.

**Botón de pausa de descargas (S075, corregido de fondo en un segundo
pase):** nuevo `DownloadPauseController`. Primer intento (comprobar la
pausa solo una vez, al principio de `doWork()`, antes del semáforo) no
funcionaba de verdad -- con una descarga masiva ya en marcha, cientos
de intentos ya habían pasado ese punto y solo esperaban turno en el
semáforo, que no sabe nada de pausa. Corregido añadiendo una SEGUNDA
comprobación justo antes de la transferencia real (ya dentro del
permiso del semáforo) -- este es el punto que de verdad importa.

**Estados `DOWNLOADING` colgados de sesiones interrumpidas (S076):**
si un `Worker` moría a media ejecución (p.ej. al instalar una
actualización de la app, que reinicia el proceso) después de marcar
una pista `DOWNLOADING` pero antes de terminar, esa fila se quedaba
con ese estado en Room para siempre -- daba la falsa impresión de que
el límite de concurrencia y la pausa no funcionaban. Corregido
reafirmando `QUEUED` explícitamente al arrancar cada intento, antes de
esperar turno.

**Botón "Borrar todas" en descargas con error (S083):** ver detalle en
`DOCS/ANNEX_H07.md`, S036 -- mismo commit, aplica igual a este hito.

## Estado

**Hito 02 completado y verificado funcionalmente (S003).**

Pendientes menores sin bloquear el hito, retomados o no según
prioridad:
- Limpieza del bloque de escritura de `debug_error.txt` en
  `DownloadWorker` (código de diagnóstico temporal).
- Gestión de errores de descarga más visible en UI (actualmente solo
  icono rojo).

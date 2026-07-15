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

## Estado

**Hito 02 completado y verificado funcionalmente (S003).**

Pendientes menores sin bloquear el hito, retomados o no según
prioridad:
- Limpieza del bloque de escritura de `debug_error.txt` en
  `DownloadWorker` (código de diagnóstico temporal).
- Gestión de errores de descarga más visible en UI (actualmente solo
  icono rojo).

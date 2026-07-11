# MIMOO — MASTER DOCUMENT

*Vive en `DOCS/MASTER_DOCUMENT.md` del propio repositorio
(`github.com/MiguelaeTxio/MiMoo`) — flujo NewFlow Android. Editar y
commitear como cualquier otro archivo del proyecto, vía
`newflow-android-edit`. No es una skill.*

---

## TABLA DE HITOS Y ESTADO

El estado (`EN PROGRESO` / vacío) vive **aquí, en esta tabla**, no en
ningún enrutador aparte. Solo un hito puede estar `← EN PROGRESO` a
la vez. Cambiar de hito es editar esta tabla y commitear — ver
`newflow-android-pch`.

| Hito | Título | Anexo | Estado |
|---|---|---|---|
| H01 | Buscar y Escuchar: Streaming de Audio bajo Demanda | `DOCS/ANNEX_H01.md` | |
| H02 | Descarga a Local: yt-dlp + Opus + Queue | `DOCS/ANNEX_H02.md` | |
| H03 | Biblioteca Local: Reproducción Offline, CRUD, Favoritos y Carátulas | `DOCS/ANNEX_H03.md` | |
| H04 | Listas de Reproducción Locales | `DOCS/ANNEX_H04.md` | |
| H05 | Búsqueda de Álbumes Completos vía MusicBrainz | `DOCS/ANNEX_H05.md` | |
| H06 | Exportar/Importar Repositorio de Música vía Google Drive | `DOCS/ANNEX_H06.md` | |
| H07 | Persistencia de Enlaces + Sincronización Automática + Actualizaciones In-App + Controles de Reproducción | `DOCS/ANNEX_H07.md` | |
| H08 | Búsqueda de Listas de Reproducción + Música Relacionada ("Radio") | `DOCS/ANNEX_H08.md` | ← EN PROGRESO |

**Resultado actual (migrado desde la sesión skill-based, 2026-07-02;
actualizado 2026-07-11 S008):**
H01 y H02 completados y verificados funcionalmente. H03 (PASOS 6, 7,
9 hechos en la sesión del 2026-07-02) y H04 (PASOS 1-6 hechos, mismo
día) tienen todo el código escrito pero comparten un pendiente:
verificación física en el dispositivo de Miguel Ángel (H03 PASO 8,
H04 PASO 6) — no bloquea nada, simplemente no se ha hecho. H05 tiene
los PASOS 1-5 implementados y el toggle de vista + "Editar álbum" de
S005 ya verificados en dispositivo (S006); **PASO 6c queda pausado sin
tocar** (Lou Reed, búsqueda por artista/título suelto, Importar
enlace — ver `DOCS/ANNEX_H05.md`, no es el hito activo). H06 queda
**pausado, no completado**: "Exportar a Drive" verificado en
dispositivo real (proyecto Google Cloud `mimoo-drive`, tras resolver
en S007 un bloqueo de registro OAuth irresoluble en el proyecto
original `mimoo-501004` — ver `DOCS/ANNEX_H06.md`); **"Importar desde
Drive" queda sin probar**, pendiente para cuando se retome H06. **H07
queda pausado, completado y verificado en dispositivo real con dos
dispositivos** (S008): persistencia del link en metadatos + DB,
sincronización automática con regla de negocio completa (identidad de
dispositivo, bloqueo de mutaciones sin conexión, verificación
disco↔BBDD, restauración selectiva), actualizaciones in-app vía
GitHub Releases dedicado, PIN de acceso, y controles de reproducción
cíclico/aleatorio — ver `DOCS/ANNEX_H07.md` para el detalle completo
de las seis vueltas de fixes reales de esta sesión. H08 es hito nuevo,
sin código todavía, abierto en S008 a petición explícita de Miguel
Ángel.

---

## 0. Nota de Refundación (S002-H02, 2026-06-30)

Esta versión del master document sustituye por completo a la
anterior, por decisión explícita de Miguel Ángel ("borrón y cuenta
nueva"). La visión original (biblioteca de Artist/Album/Playlist
gestionada vía CRUD manual, con importación de playlists de YouTube)
quedó descartada: no resolvía la necesidad real, que es un reproductor
de audio tipo NewPipe, donde el dato nace de YouTube/fuentes externas
y el usuario escribe lo mínimo posible. El código del CRUD anterior
se purga del repositorio. Se conserva del proyecto original únicamente
lo que no depende de ese modelo de datos: el motor de descarga
yt-dlp + FFmpeg + WorkManager (Hito 02 original, renombrado y
reconectado como Hito 02 de esta versión) y la integración con
YouTube Data API v3 como fuente de metadatos/búsqueda.

**Nota de migración a NewFlow (2026-07-02):** este documento y los
cinco anexos (`ANNEX_H01.md`...`ANNEX_H05.md`) migran desde skills
instaladas en el cliente Claude a archivos Markdown normales dentro
de este mismo repositorio, a petición explícita de Miguel Ángel, tras
adoptar el flujo de trabajo `newflow-android-edit` (clon directo +
commit/push contra GitHub, sin PythonAnywhere de por medio para el
código). El contenido íntegro se conserva sin pérdida de historial;
solo cambia dónde vive el archivo. Las skills originales
(`doc-master-mimoo`, `mimoo-annex-v0X`, `android-annex-router`,
`doc-android-directory-mimoo`) siguen instaladas y siguen siendo
válidas **exclusivamente** si en algún momento se retoma el flujo
antiguo basado en PythonAnywhere (`android-edit`/`android-git`) — no
se han borrado, pero quedan congeladas en el estado de esa fecha y no
se actualizarán en paralelo a este archivo.

---

## 1. Visión General del Proyecto

MiMoo es una aplicación Android personal de Miguel Ángel para buscar,
escuchar y descargar música desde YouTube — un "NewPipe a su gusto"
centrado en audio. El reproductor es el componente central de la app
desde el primer hito, no un añadido final. El flujo de uso es:
buscar un tema o un álbum, escucharlo en streaming de alta calidad
sin necesidad de descargarlo primero, y opcionalmente descargarlo a
local en formato Opus para escucha offline. La organización del
catálogo (favoritos de artista/álbum/canción, álbumes con portada y
tracklist verificada) se construye a partir de metadatos que ya
existen en fuentes externas (YouTube, MusicBrainz/Cover Art Archive),
nunca a base de formularios que el usuario rellena a mano. No existe
backend remoto: todo el almacenamiento de datos y audio vive en el
propio dispositivo Android, con un respaldo opcional de metadatos
(no de audio) en Google Drive para no perder favoritos/catálogo al
reinstalar o cambiar de dispositivo.

**Prioridad de construcción, de más a menos básico:**
1. Buscar un tema suelto y escucharlo en streaming (Hito 01).
2. Descargar ese tema a local para escucha offline (Hito 02).
3. Biblioteca local de lo ya descargado: reproducción desde archivo,
   reorganización de almacenamiento por Artista/Álbum, CRUD de
   biblioteca (listar/borrar/editar metadatos), favoritos y carátulas
   vía MusicBrainz/Cover Art Archive (Hito 03).
4. Listas de reproducción locales (crear/renombrar/borrar,
   añadir/quitar pistas, reproducir en orden) — Hito 04.
5. Búsqueda de álbumes completos contra MusicBrainz, con
   emparejamiento automático de cada pista a un vídeo de YouTube por
   minutaje, y edición manual cuando el emparejamiento falla —
   Hito 05.
6. Backup/restauración de metadatos vía Google Drive entre
   dispositivos — Hito 06 (`DOCS/ANNEX_H06.md`). Búsqueda de listas de
   reproducción y música relacionada — Hito 08
   (`DOCS/ANNEX_H08.md`) — explícitamente otra cosa, no confundir con
   H06.

---

## 2. Arquitectura Técnica

### 2.1. Entorno de Desarrollo y Compilación

- **Lenguaje y UI:** Kotlin + Jetpack Compose.
- **Inyección de dependencias:** Hilt.
- **Navegación:** Navigation Compose.
- **Persistencia local:** Room (SQLite).
- **Reproducción de audio:** ExoPlayer (Media3), streaming HTTP
  progresivo sobre la URL de stream resuelta por yt-dlp.
- **Almacenamiento de audio descargado:** sistema de archivos local
  del dispositivo vía SAF, estructura `{raíz elegida}/{artista}/{álbum}/`.
- **Compilación:** NO se realiza localmente con Android Studio ni
  `gradlew` (recursos insuficientes en el equipo de desarrollo). Se
  realiza en la nube vía GitHub Actions (`assembleDebug`, JDK 17), que
  tras cada push compila el APK y la sube a dos sitios (H07,
  S008): PythonAnywhere (histórico, descarga manual por sftp) y una
  Release en el repositorio público dedicado `AndroidReleases`
  (`MiguelaeTxio/AndroidReleases`, con `manifest.json`) para que la
  propia app compruebe y descargue actualizaciones sola — ver
  `DOCS/ANNEX_H07.md` PARTE 2.

### 2.2. Motor de Audio — yt-dlp vía Chaquopy

yt-dlp corre embebido vía **Chaquopy** (Python 3.11 empaquetado en el
APK), no como binario nativo standalone (no existe binario ARM64
standalone de yt-dlp — es un script Python). El mismo módulo
(`resolver.py`/`downloader.py`) sirve tanto para resolver streaming
(`-f bestaudio -g`, Hito 01) como para descarga a Opus
(`-x --audio-format opus`, Hito 02).

- **ffmpeg**: binario nativo ARM64 estático LGPL empaquetado en
  `jniLibs/arm64-v8a/libffmpeg_bin.so` (`packaging { jniLibs {
  useLegacyPackaging = true } }` para forzar extracción a disco en
  Android 14+, W^X lo exige).
- **Actualizarse en línea** sobre el estado de yt-dlp/Chaquopy antes
  de tocar este componente — cambia con frecuencia (directriz §4.5).

### 2.3. Integración con YouTube — Solo Metadatos y Búsqueda

- **Búsqueda y emparejamiento de metadatos vía `yt-dlp` (búsqueda
  libre, "ytsearchN:query"), sin cuota ni API key** —
  `ExternalLinkResolver.searchYoutube()`. Cubre tanto la pantalla de
  Búsqueda normal (desde el 4 de julio) como la búsqueda/emparejamiento
  de álbumes completos de H05 (desde S007, 2026-07-10).
- **HISTÓRICO — YouTube Data API v3, eliminada por completo en S007
  (2026-07-10):** hasta esa sesión, la búsqueda de álbumes (H05) usaba
  `search.list`/`playlistItems.list` vía API key
  (`BuildConfig.YOUTUBE_API_KEY`, proyecto `mimoo-501004`). Se retiró
  del proyecto entero — código (`YouTubeApiService.kt`,
  `YouTubeRepository.kt`, wiring de Retrofit en `NetworkModule.kt`),
  secret de GitHub Actions y `buildConfigField` — a petición explícita
  de Miguel Ángel, para poder borrar `mimoo-501004` sin dejar ninguna
  función de MiMoo dependiendo de él (ver `DOCS/ANNEX_H06.md`,
  "COMPLETADAS EN S007", para el detalle completo). La pérdida real:
  ya no existe la estrategia "playlist primero" que evitaba gastar
  cuota en álbumes largos (no tenía sentido mantenerla sin cuota que
  ahorrar) — sustituida por búsqueda libre pista a pista, igual de
  automática.
- **`yt-dlp` nunca necesitó la YouTube Data API para resolver el
  stream de audio en sí** — eso siempre fue responsabilidad exclusiva
  de `yt-dlp`, sin cambios por esta eliminación.

### 2.4. Fuentes Externas Adicionales

- **MusicBrainz API** (`https://musicbrainz.org/ws/2/`, sin clave,
  JSON, User-Agent obligatorio, límite ~1 req/s por IP) — tracklist y
  duración por pista para búsqueda de álbumes (Hito 05), búsqueda de
  release para carátulas (Hito 03).
- **Cover Art Archive** (`coverartarchive.org`) — portada de álbum a
  partir del MBID de MusicBrainz.
- **Google Drive** — backup/restauración de metadatos de todo el
  repositorio (pistas, favoritos de álbum, listas de reproducción),
  nunca de archivos de audio — Hito 06, ver `DOCS/ANNEX_H06.md`.
  "Exportar a Drive" implementado y verificado en dispositivo real
  (S007); "Importar desde Drive" implementado, verificación pendiente.
  Proyecto Google Cloud: `mimoo-drive` (ver anexo para el porqué del
  cambio desde `mimoo-501004`).

### 2.5. Control de Versiones — NewFlow

- **Repositorio remoto:** `github.com/MiguelaeTxio/MiMoo` — única
  fuente de verdad del código fuente y, desde esta migración, también
  de la documentación del proyecto (este archivo y los anexos en
  `DOCS/`).
- **Edición:** clon directo en el workspace del modelo, edición con
  `str_replace`/`create_file`, commit + push directo — ver
  `newflow-android-edit`. Sin PythonAnywhere, sin SWAP, sin sftp para
  el código ni para la documentación.
- **Compilación automática:** GitHub Actions
  (`.github/workflows/build-and-deploy.yml`) — compila APK debug y la
  sube a `ANDROID/MiMoo/apk/MiMoo.apk` en PythonAnywhere (vía manual
  por sftp), y publica una Release con el APK + `manifest.json` en
  `MiguelaeTxio/AndroidReleases` (H07, S008) para que la app compruebe
  actualizaciones sola — ver `DOCS/ANNEX_H07.md` PARTE 2.
- **Secrets necesarios en GitHub Actions:**
  `GOOGLE_OAUTH_ANDROID_CLIENT_ID` (proyecto `mimoo-drive`, ver
  `DOCS/ANNEX_H06.md`), `DEBUG_KEYSTORE_BASE64`, `PA_API_TOKEN`,
  `RELEASES_REPO_LAST_TOKEN` (H07, S008 — PAT de solo el repo
  `AndroidReleases`, `Contents: Read and write`).
  `YOUTUBE_API_KEY` ya no existe — eliminado en S007 junto con toda la
  YouTube Data API (ver §2.3).

---

## 3. Hoja de Ruta Estratégica

### Hito 1: Buscar y Escuchar — Streaming de Audio bajo Demanda
(Ver `DOCS/ANNEX_H01.md`) — Completado y verificado.

### Hito 2: Descarga a Local — yt-dlp + Opus + Queue
(Ver `DOCS/ANNEX_H02.md`) — Completado y verificado.

### Hito 3: Biblioteca Local — Reproducción Offline, CRUD, Favoritos y Carátulas
(Ver `DOCS/ANNEX_H03.md`) — Código completo, PASO 8 (verificación
funcional en dispositivo) pendiente.

### Hito 4: Listas de Reproducción Locales
(Ver `DOCS/ANNEX_H04.md`) — Código completo, PASO 6 (verificación
funcional en dispositivo) pendiente.

### Hito 5: Búsqueda de Álbumes Completos vía MusicBrainz
(Ver `DOCS/ANNEX_H05.md`) — Pausado (no completado). PASOS 1-5 hechos,
S005 (toggle Biblioteca + Editar álbum) verificado en dispositivo en
S006. PASO 6c pendiente sin tocar: Lou Reed, búsqueda por
artista/título suelto, Importar enlace.

### Hito 6: Exportar/Importar Repositorio de Música vía Google Drive
(Ver `DOCS/ANNEX_H06.md`) — Pausado. Exportar toda la biblioteca local
(metadatos, no audio) a un archivo en Google Drive, e importarla en
otro dispositivo sustituyendo por completo el repositorio local
destino y encolando la descarga de cada pista con los metadatos ya
corregidos. "Exportar" verificado en dispositivo real (S007);
"Importar" implementado, verificación pendiente. Explícitamente
independiente de la búsqueda de listas de reproducción y la música
relacionada (Hito 08).

### Hito 7: Persistencia de Enlaces + Sincronización Automática + Actualizaciones In-App + Controles de Reproducción
(Ver `DOCS/ANNEX_H07.md`) — Pausado, completado y verificado en
dispositivo real con dos dispositivos (S008). Persistencia del
`youtubeId` en metadatos del archivo + base de datos; sincronización
automática entre dispositivos con regla de negocio completa (identidad
de dispositivo, bloqueo de mutaciones sin conexión, verificación
disco↔BBDD, restauración selectiva); actualizaciones in-app vía
repositorio GitHub dedicado (`AndroidReleases`); PIN de acceso; y
controles de reproducción cíclico/aleatorio.

### Hito 8: Búsqueda de Listas de Reproducción + Música Relacionada ("Radio")
(Ver `DOCS/ANNEX_H08.md`) — EN PROGRESO. Hito nuevo abierto en S008:
dos funciones distintas comparten hito por surgir de la misma
conversación, no por dependencia técnica entre ellas.
1. **Búsqueda de listas de reproducción** — la pantalla de Playlists
   no tiene filtro/búsqueda propio, a diferencia de Biblioteca
   ("Filtrar biblioteca"). Alcance bien definido, sin ambigüedad.
2. **Música relacionada ("Radio")** — cuando la cola de reproducción
   se queda sin nada más que reproducir, sugerir/encolar
   automáticamente música relacionada con lo que se estaba
   escuchando, en vez de simplemente parar. **Alcance deliberadamente
   sin cerrar** — ver `DOCS/ANNEX_H08.md` para las preguntas de diseño
   abiertas que Miguel Ángel planteó explícitamente sin resolver
   (cuándo se dispara exactamente, de dónde sale la relación
   artista↔artista, si hace falta un tercer control además de
   cíclico/aleatorio).

---

## 4. Directrices Técnicas Vinculantes

Estas directrices son de **OBLIGADO CUMPLIMIENTO** en todas las
sesiones de desarrollo del proyecto sin excepción, en cualquier flujo
(NewFlow o el antiguo basado en PythonAnywhere).

### 4.1. Lectura de Entidades Antes de Codificar

**[PROHIBICIÓN ABSOLUTA]** El modelo nunca infiere nombres de campos,
funciones de DAO/repositorio o tipos de datos desde memoria o desde
el nombre del anexo. Siempre se lee primero el archivo real (`view`
sobre el clon local en NewFlow) antes de escribir cualquier código
que dependa de su contrato. Nace de fallos de build reales en
S003-H01 (versión anterior del proyecto) por asumir nombres de campo
incorrectos.

### 4.2. NavGraph y Pantallas No Implementadas

`NavGraph.kt` **nunca** referencia pantallas que todavía no existen.
Si un bloque de UI no está implementado, se revierte cualquier
adición a `NavGraph.kt` hasta que la pantalla exista.

### 4.3. Único Almacén de Código Fuente y Documentación — GitHub (NewFlow)

**Sustituye a la directriz original que designaba PythonAnywhere como
almacén.** En NewFlow, `github.com/MiguelaeTxio/MiMoo` es el único
almacén tanto del código fuente como de la documentación del proyecto
(este archivo y los anexos). Todo el flujo de edición pasa por: clon
en el workspace del modelo → edición directa → commit + push — ver
`newflow-android-edit`. **QUEDA TERMINANTEMENTE PROHIBIDO** asumir
que existe un directorio Android en PythonAnywhere que deba
mantenerse sincronizado con el código para este flujo — no existe tal
cosa en NewFlow, salvo la APK compilada (ver §2.5).

### 4.4. Compilación Exclusivamente en la Nube

**QUEDA TERMINANTEMENTE PROHIBIDO** intentar compilar el proyecto
localmente con Android Studio o `gradlew`. Cada push a `main` es, en
sí mismo, un intento de compilación completo vía GitHub Actions.

### 4.5. Requisito SINE QUA NON

Antes de entregar o implementar cualquier código que involucre
servicios externos o APIs (YouTube Data API, yt-dlp, ExoPlayer,
MusicBrainz, Cover Art Archive), el modelo **DEBE** actualizarse en
línea obligatoriamente para usar datos actuales de implementación en
lugar de datos obsoletos del entrenamiento.

### 4.6. Secrets Nunca en Claro

Las credenciales (API keys, tokens de GitHub) nunca se escriben en
claro en código versionado ni en `.git/config`. La API key de YouTube
vive exclusivamente como secret de GitHub Actions. El token de
sesión de NewFlow vive solo en memoria de comandos puntuales — ver
`newflow-android-token`.

### 4.7. Ningún Comentario de Cabecera con la Ruta del Archivo

**[PROHIBICIÓN ABSOLUTA]** Ningún archivo generado o editado lleva
como primera línea un comentario con su propia ruta. Esta práctica
causó el fallo del Build #15 en S001-H02: en XML, la declaración
`<?xml version="1.0"?>` debe ser literalmente el primer carácter del
documento. Aplica sin excepción a `AndroidManifest.xml` y a cualquier
otro archivo del proyecto.

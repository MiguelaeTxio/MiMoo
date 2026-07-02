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
| H05 | Búsqueda de Álbumes Completos vía MusicBrainz | `DOCS/ANNEX_H05.md` | ← EN PROGRESO |

**Resultado actual (migrado desde la sesión skill-based, 2026-07-02):**
H01 y H02 completados y verificados funcionalmente. H03 (PASOS 6, 7,
9 hechos en la sesión del 2026-07-02) y H04 (PASOS 1-6 hechos, mismo
día) tienen todo el código escrito pero comparten un pendiente:
verificación física en el dispositivo de Miguel Ángel (H03 PASO 8,
H04 PASO 6) — no bloquea nada, simplemente no se ha hecho. H05 tiene
los PASOS 1-5 implementados; PASO 6 en curso con dos incidencias
reales detectadas en la prueba de Miguel Ángel (ver `DOCS/ANNEX_H05.md`).

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
6. Música relacionada y backup de metadatos en Google Drive (hitos
   futuros, sin anexo aún).

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
  realiza en la nube vía GitHub Actions (`assembleDebug`, JDK 17),
  que tras cada push compila el APK y la sube directamente a
  PythonAnywhere (único uso de PythonAnywhere que sigue vigente en
  NewFlow: alojar la APK compilada para descarga, nunca el código
  fuente).

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

- **YouTube Data API v3** para búsqueda de vídeos y metadatos
  (título, canal, duración, miniatura). Autenticación mediante API
  key (`BuildConfig.YOUTUBE_API_KEY`), inyectada en build vía
  `buildConfigField` desde `local.properties`, generado en el
  workflow de GitHub Actions a partir del secret de repositorio
  `YOUTUBE_API_KEY`.
- **Coste real:** `search.list` cuesta 1 unidad/llamada, límite
  propio de cuota 100/día (verificado 2026-06-30 contra documentación
  oficial de Google — no asumir de memoria, puede cambiar).
- **Proyecto Google Cloud:** `mimoo-501004`. API key restringida
  exclusivamente a YouTube Data API v3, sin restricción de
  aplicación.
- **YouTube Data API v3 nunca se usa para resolver el stream de
  audio en sí** — eso es responsabilidad exclusiva de yt-dlp. La Data
  API solo aporta metadatos de búsqueda.

### 2.4. Fuentes Externas Adicionales

- **MusicBrainz API** (`https://musicbrainz.org/ws/2/`, sin clave,
  JSON, User-Agent obligatorio, límite ~1 req/s por IP) — tracklist y
  duración por pista para búsqueda de álbumes (Hito 05), búsqueda de
  release para carátulas (Hito 03).
- **Cover Art Archive** (`coverartarchive.org`) — portada de álbum a
  partir del MBID de MusicBrainz.
- **Google Drive** — backup de metadatos (favoritos, catálogo), no
  de archivos de audio. Sin implementar todavía.

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
  sube a `ANDROID/MiMoo/apk/MiMoo.apk` en PythonAnywhere (único punto
  de contacto restante con PythonAnywhere: servir la APK compilada
  para descarga, gestionado por el propio workflow sin intervención
  manual).
- **Secrets necesarios en GitHub Actions:** `YOUTUBE_API_KEY`,
  `PA_API_TOKEN`.

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
(Ver `DOCS/ANNEX_H05.md`) — EN PROGRESO. PASOS 1-5 hechos, PASO 6 con
incidencias reales detectadas por Miguel Ángel, hoja de ruta de
corrección ya redactada en el propio anexo.

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

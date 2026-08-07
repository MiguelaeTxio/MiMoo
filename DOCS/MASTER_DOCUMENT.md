# MIMOO — MASTER DOCUMENT

*Vive en `DOCS/MASTER_DOCUMENT.md` del propio repositorio
(`github.com/MiguelaeTxio/MiMoo`) — flujo NewFlow Android. Editar y
commitear como cualquier otro archivo del proyecto, vía
`newflow-android-edit`. No es una skill.*

---

## TABLA DE HITOS

**El estado de cada hito (EN PROGRESO / PAUSADO) vive exclusivamente
en `DOCS/ANNEX_ROUTER.md` — este archivo nunca lo menciona.** Este
documento es puramente descriptivo: qué es cada hito y, más abajo en
la Hoja de Ruta Estratégica, qué hay construido/verificado y qué
queda abierto en cada uno.

| Hito | Título | Anexo |
|---|---|---|
| H01 | Buscar y Escuchar: Streaming de Audio bajo Demanda | `DOCS/ANNEX_H01.md` |
| H02 | Descarga a Local: yt-dlp + Opus + Queue | `DOCS/ANNEX_H02.md` |
| H03 | Biblioteca Local: Reproducción Offline, CRUD, Favoritos y Carátulas | `DOCS/ANNEX_H03.md` |
| H04 | Listas de Reproducción Locales | `DOCS/ANNEX_H04.md` |
| H05 | Búsqueda de Álbumes Completos vía MusicBrainz | `DOCS/ANNEX_H05.md` |
| H06 | Exportar/Importar Repositorio de Música vía Google Drive | `DOCS/ANNEX_H06.md` |
| H07 | Persistencia de Enlaces + Sincronización Automática + Actualizaciones In-App + Controles de Reproducción | `DOCS/ANNEX_H07.md` |
| H08 | Búsqueda de Listas de Reproducción + Música Relacionada ("Radio") | `DOCS/ANNEX_H08.md` |
| H09 | Radios Online del Mundo por Género/Tema/Década (Radio-Browser.info) | `DOCS/ANNEX_H09.md` |
| H10 | Hash de Compartición de Contenido | `DOCS/ANNEX_H10.md` |
| H11 | Canales — Suscripciones y Descarga Automática | `DOCS/ANNEX_H11.md` |
| H12 | Directorio de Música (Artista/Álbum/Canción) + Favoritos sin Descarga | `DOCS/ANNEX_H12.md` |
| H13 | UX del Reproductor (ExoPlayer) — Estado Visual de Controles | `DOCS/ANNEX_H13.md` |
| H14 | Almacenamiento de la Biblioteca — Carpeta Configurable y Traslado | `DOCS/ANNEX_H14.md` |
| H15 | miMooutCast — Radio de Ancla a la Carta (Géneros/Décadas) | `DOCS/ANNEX_H15.md` |
| H16 | Lista Negra ("No me gusta") de Artistas y Temas | `DOCS/ANNEX_H16.md` |
| H17 | Karaoke & Lyrics | `DOCS/ANNEX_H17.md` |
| H18 | Play y Ordenación de Listas de Items | `DOCS/ANNEX_H18.md` |

**Resumen de qué hay construido en cada hito (migrado desde la sesión
skill-based, 2026-07-02; actualizado 2026-07-15):**
H01 y H02, verificados funcionalmente. H03 (PASOS 6, 7, 9) y H04
(PASOS 1-6) tienen todo el código escrito, con un pendiente
compartido: verificación física en el dispositivo de Miguel Ángel
(H03 PASO 8, H04 PASO 6) — no bloquea nada, simplemente no se ha
hecho. H05 tiene los PASOS 1-5 implementados y el toggle de vista +
"Editar álbum" de S005 verificados en dispositivo (S006); **PASO 6c
sin tocar** (Lou Reed, búsqueda por artista/título suelto, Importar
enlace — ver `DOCS/ANNEX_H05.md`). H06: "Exportar a Drive" verificado
en dispositivo real (proyecto Google Cloud `mimoo-drive`, tras
resolver en S007 un bloqueo de registro OAuth irresoluble en el
proyecto original `mimoo-501004` — ver `DOCS/ANNEX_H06.md`);
**"Importar desde Drive" sin probar**. H07: persistencia del link en
metadatos + DB, sincronización automática con regla de negocio
completa (identidad de dispositivo, bloqueo de mutaciones sin
conexión, verificación disco↔BBDD, restauración selectiva),
actualizaciones in-app vía GitHub Releases dedicado, PIN de acceso, y
controles de reproducción cíclico/aleatorio, verificado en
dispositivo real con dos dispositivos (S008) — ver
`DOCS/ANNEX_H07.md`; **fix real de un `SocketTimeoutException` sin
capturar en el diálogo de conflicto de sync, 2026-07-15, pendiente de
verificación en dispositivo**. H08: búsqueda de listas/canales
construida (verificación pendiente de confirmación explícita) y Radio
(música relacionada) verificada en dispositivo real (S009-S010),
aunque con un fallo de idioma detectado y pospuesto sin tocar (ver
`DOCS/ANNEX_H08.md`). H09: ver `DOCS/ANNEX_H09.md` para el detalle
completo, incluido lo construido en S010 que no había quedado
documentado hasta ahora. H10: 8 de 10 niveles de compartición
construidos (ver `DOCS/ANNEX_H10.md`), sin verificar en dispositivo
real todavía; los niveles 9-10 (Canales) dependen de que exista H11.
H11: hito nuevo, construido (suscripciones, pantalla
de gestión, descarga automática en segundo plano) -- ver
`DOCS/ANNEX_H11.md`, sin verificar en dispositivo real todavía.
H12: hito nuevo (2026-07-19), construido entero en S018 -- directorio
de música navegable vía MusicBrainz (páginas de artista/álbum/canción
cruzadas entre sí), unificación de las búsquedas de H01/H05 con chips
de filtro por tipo, streaming y descarga al vuelo desde esas páginas,
favoritos de artista/álbum desacoplados de la descarga, y Explorador
(letra -> local + muestra paginada de MusicBrainz, scroll infinito).
Dos fixes reales confirmados en dispositivo durante la propia sesión
(normalización de puntuación en nombres, cruce con lo descargado por
posición de pista en vez de por youtubeId). Ver `DOCS/ANNEX_H12.md`
para el detalle completo.
H13: hito nuevo (2026-07-19), construido en S020 -- todos los botones
del reproductor (expandido y mini-barra) y el bloque de metadatos
junto a la carátula sobre cristal esmerilado, con chapita ENCENDIDA
para aleatorio y cíclico. Causa de fondo encontrada por el camino: el
estado activo se señalaba con `colorScheme.primary`, que en esta
paleta es blanco, el mismo blanco del estado inactivo -- el cambio de
color no cambiaba nada en pantalla. En S019 se arregló además un bug
de layout real (el botón de contraer se salía de pantalla). Pendiente:
verificación visual en dispositivo real. Ver `DOCS/ANNEX_H13.md`.
H08, estado tras S021: la lógica de la Radio quedó completa en S020 y
sin fugas en S021 (se cerraron cuatro peldaños que mantenían el género
pero soltaban la década, incumpliendo la mitad de la regla que Miguel
Ángel había cerrado en S020), y el diccionario de éxitos quedó
auditado, corregido y ampliado de 286 a 751 entradas, con las siete
décadas en el entorno de las ~100 fijadas como objetivo y con la
proporción de `pop` bajando del 55% al 33%. Hallazgo de fondo: los
saltos de década que S020 achacó a entradas mal fechadas venían en
realidad del código, no del dato. No queda trabajo de implementación
pendiente en H08 -- solo verificación en dispositivo. Ver
`DOCS/ANNEX_H08.md`, "COMPLETADAS EN S021".
H14: hito nuevo (2026-07-25), construido en S021 -- la carpeta donde
vive el audio descargado pasa a ser configurable desde Ajustes, con
selector del sistema y dos ramas al cambiarla: llevarse toda la
biblioteca a la carpeta nueva, o cambiar solo el ajuste dejando lo ya
descargado donde está. Nuevo `LibraryMigrator` (copia + borrado, nunca
destructivo antes de tiempo, reentrante). Motivado por el caso de uso
de mover la biblioteca a una tarjeta externa sin perder favoritos,
listas ni canales. Sin verificar en dispositivo todavía. Ver
`DOCS/ANNEX_H14.md`.
H15: hito nuevo (2026-08-04, cierre de S028) -- miMooutCast, ancla de
Radio elegida a mano (género y/o década de MusicBrainz) en vez de
derivada de una pista sonando, reutilizando el motor de H08 ya
construido y corregido en S028. Sin código todavía. En S029 se
cerraron tres de los cuatro puntos de alcance abiertos: se pide
también origen/país (misma organización en 4 grupos que ya usa la
Radio, `OriginGroup`), la interacción entre secciones es "se elige una
única dimensión (género/década/origen) y las otras quedan libres", y
el catálogo de géneros será una lista fija, no una consulta en vivo a
MusicBrainz. Queda un único punto abierto: el rango de décadas de esa
lista fija. Ver `DOCS/ANNEX_H15.md`.
H16: hito nuevo (2026-08-05, apertura en S029) -- Lista Negra ("No me
gusta") de artistas y temas, a petición explícita de Miguel Ángel
surgida durante el diseño de H15: botón nuevo en el ExoPlayer que, al
pulsarse, pregunta si el rechazo es del artista o del tema sonando;
marcar algo como "no me gusta" es GLOBAL (excluye de cualquier sesión
de Radio y de cualquier popurrí de Favoritos futuros) y también se
puede añadir desde el Explorador (H12). El rechazo de un tema concreto
excluye CUALQUIER VERSIÓN de ese tema del artista, no solo el vídeo de
YouTube concreto que sonaba. Un tema ya descargado que se marca como
"no me gusta" deja de sonar en cualquier contexto (Radio, popurrí).
Vista de gestión (CRUD) con entrada propia en el menú lateral. Sin
código todavía -- diseño técnico abierto (ver
`DOCS/ANNEX_H16.md`), a cerrar con Miguel Ángel antes de escribir
nada.
H17: hito nuevo (2026-08-06, cierre de S030) -- Karaoke & Lyrics:
entrada en el drawer para buscar y leer letras de cualquier canción, y
entrada en el menú de tres puntos del ExoPlayer que muestra el
karaoke del tema en curso si hay letra disponible. Diseño cerrado y
construido entero en S031 -- cliente lrclib.net + caché Room, panel de
karaoke sobre el ExoPlayer con tres variantes según disponibilidad de
letra, pantalla de búsqueda del drawer con distinción "ya en tu
biblioteca", log de diagnóstico propio y un fix real de limpieza de
título de YouTube antes de consultar la API. Sin código pendiente --
solo verificación en dispositivo real. Ver `DOCS/ANNEX_H17.md`.
H18: hito nuevo (2026-08-07, apertura en S032) -- Play y Ordenación de
Listas de Items, a petición explícita de Miguel Ángel surgida al
cierre de S031: falta el botón de play en las filas de item de
Favoritos (artistas, álbumes, sencillos, listas) y falta ordenación
(alfabética / por orden de adición, ascendente y descendente)
aplicable a todas las listas de items de la app. Confirmado contra el
código real de `FavoritesScreen.kt` (ninguna de las cuatro pestañas
tiene botón de play en su fila). Sin código todavía -- diseño técnico
abierto (ver `DOCS/ANNEX_H18.md`), a cerrar con Miguel Ángel antes de
escribir nada.

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
(Ver `DOCS/ANNEX_H01.md`) — Verificado funcionalmente.

### Hito 2: Descarga a Local — yt-dlp + Opus + Queue
(Ver `DOCS/ANNEX_H02.md`) — Verificado funcionalmente.

### Hito 3: Biblioteca Local — Reproducción Offline, CRUD, Favoritos y Carátulas
(Ver `DOCS/ANNEX_H03.md`) — Código completo, PASO 8 (verificación
funcional en dispositivo) pendiente.

### Hito 4: Listas de Reproducción Locales
(Ver `DOCS/ANNEX_H04.md`) — Código completo, PASO 6 (verificación
funcional en dispositivo) pendiente.

### Hito 5: Búsqueda de Álbumes Completos vía MusicBrainz
(Ver `DOCS/ANNEX_H05.md`) — PASOS 1-5 hechos, S005 (toggle Biblioteca
+ Editar álbum) verificado en dispositivo en S006. PASO 6c pendiente
sin tocar: Lou Reed, búsqueda por artista/título suelto, Importar
enlace.

### Hito 6: Exportar/Importar Repositorio de Música vía Google Drive
(Ver `DOCS/ANNEX_H06.md`) — Exportar toda la biblioteca local
(metadatos, no audio) a un archivo en Google Drive, e importarla en
otro dispositivo sustituyendo por completo el repositorio local
destino y encolando la descarga de cada pista con los metadatos ya
corregidos. "Exportar" verificado en dispositivo real (S007);
"Importar" implementado, verificación pendiente. Explícitamente
independiente de la búsqueda de listas de reproducción y la música
relacionada (Hito 08).

### Hito 7: Persistencia de Enlaces + Sincronización Automática + Actualizaciones In-App + Controles de Reproducción
(Ver `DOCS/ANNEX_H07.md`) — Persistencia del `youtubeId` en metadatos
del archivo + base de datos; sincronización automática entre
dispositivos con regla de negocio completa (identidad de dispositivo,
bloqueo de mutaciones sin conexión, verificación disco↔BBDD,
restauración selectiva); actualizaciones in-app vía repositorio
GitHub dedicado (`AndroidReleases`); PIN de acceso; y controles de
reproducción cíclico/aleatorio. Verificado en dispositivo real con
dos dispositivos (S008). Fix real 2026-07-15: el diálogo de conflicto
de sync (`confirmCloudWins()`/`confirmLocalWins()`) lanzaba la
llamada de red sin `try/catch`, y un `SocketTimeoutException` real
dejaba la app en bucle de crash-reapertura preguntando lo mismo;
corregido con el mismo patrón de `try/catch` que ya tenía
`startAutoSync()` más bloqueo síncrono de doble pulsación —
verificación en dispositivo pendiente.

### Hito 8: Búsqueda de Listas de Reproducción + Música Relacionada ("Radio")
(Ver `DOCS/ANNEX_H08.md`) — Dos funciones distintas comparten hito
por surgir de la misma conversación, no por dependencia técnica entre
ellas.
1. **Búsqueda de listas de reproducción y canales (online)** — buscar
   por texto y encontrar listas/canales ya creados por otros usuarios
   en YouTube (no las playlists propias del usuario, que ya tienen su
   propio filtro local, añadido como mejora aparte). Vía los filtros
   nativos de YouTube (Listas/Canales), mismo mecanismo gratuito que
   la búsqueda de vídeos actual. Podcasts/audiolibros descartados como
   filtro dedicado: YouTube no los distingue como tipo de búsqueda.
   Construido; verificación en dispositivo real pendiente. Ver
   `DOCS/ANNEX_H08.md` para el detalle y la corrección de alcance de
   S009.
2. **Música relacionada ("Radio")** — cuando la cola de reproducción
   se queda sin nada más que reproducir sin cíclico activado,
   continúa sola en streaming con un artista relacionado (géneros
   compartidos vía MusicBrainz). Solo streaming, nunca descarga
   (decisión explícita de Miguel Ángel). Verificado funcionando en
   dispositivo real por Miguel Ángel (S009-S010), tras varias rondas
   de corrección (autoplay real, filtro de compilaciones, "Various
   Artists" sin géneros matando la cadena de relacionados, sufijos de
   canal VEVO/Oficial/Topic, ancla de género+país al primer tema). **Un
   fallo real sigue abierto y explícitamente pospuesto (no tocar salvo
   que se retome H08):** el "relacionado" solo mira género, nunca
   idioma, así que un género amplio como "rock" sugiere
   predominantemente artistas anglosajones aunque el tema de partida
   sea en español — ver `DOCS/ANNEX_H08.md` PASO 2.3.

### Hito 9: Radios Online del Mundo por Género/Tema/Década
(Ver `DOCS/ANNEX_H09.md` para el detalle completo, incluida una
brecha real de documentación de S010 que este archivo tenía sin
reflejar hasta 2026-07-15). Vista para escuchar emisoras de radio
online de todo el mundo, navegables por género/tema/país/década. Solo
streaming, nunca descarga (decisión explícita de Miguel Ángel, mismo
principio que Radio). Backend: Radio-Browser.info (directorio
comunitario gratuito, sin API key) en vez del Shoutcast real (marca de
iHeartMedia, requiere clave de desarrollador) — decisión técnica
tomada en S009. Capa de red, repositorio, pantalla y navegación
construidos y con varios crashes reales corregidos tras uso en
dispositivo; catálogo de géneros/décadas curado a mano (petición
explícita de Miguel Ángel, S010) en vez del `/json/tags` en bruto;
favoritos de emisora añadidos como ampliación no prevista en el plan
original. Sin confirmación explícita todavía de que funciona entero
en dispositivo, y sin decidir si una radio en directo debería mostrar
algún indicador tipo "En directo" en el reproductor (hoy simplemente
no muestra barra de progreso).

### Hito 10: Hash de Compartición de Contenido
(Ver `DOCS/ANNEX_H10.md`) — Genera un código "miMoo+hash" que, enviado
por cualquier medio (WhatsApp, etc.), se abre directamente con MiMoo
y añade una réplica de lo compartido a la biblioteca de quien lo abre
(favoritos, orden, ediciones de nombre, enlaces originales incluidos),
redescargando cada pista desde YouTube -- nunca destructivo, nunca
toca lo que el receptor ya tenía. 8 de los 10 niveles de compartición
construidos y con punto de entrada en la UI: Biblioteca completa,
Artista, Álbum, Tema de álbum, Sencillo, Sencillos favoritos, Listas
de reproducción. Canales/Canal dependen de que exista H11 -- ver ese
hito. Sin verificación en dispositivo real del flujo completo
todavía.

### Hito 11: Canales — Suscripciones y Descarga Automática
(Ver `DOCS/ANNEX_H11.md`) — Abierto 2026-07-15 a petición explícita de
Miguel Ángel al aclarar qué significaba "Canal" en H10: "lo mismo que
canal en YouTube... suscripciones y búsqueda de canales para
suscribirse y descargar contenido de esos canales para verlo cuando
se quiera. Es como un guardado de podcast." Construido en la misma
sesión: suscribirse desde la búsqueda de canales ya existente (H08
PARTE 1), pantalla "Canales" para ver/reproducir/dar de baja lo
suscrito, y comprobación periódica en segundo plano (una vez al día)
que encola automáticamente el contenido nuevo -- sin descargar de
golpe el catálogo histórico al suscribirse. Sin verificación en
dispositivo real todavía.

### Hito 12: Directorio de Música (Artista/Álbum/Canción) + Favoritos sin Descarga
(Ver `DOCS/ANNEX_H12.md`) — Abierto 2026-07-19, a petición explícita
de Miguel Ángel: punto de partida fue un menú de tres puntos en el
reproductor para ver la página de álbum/artista del tema que suena, y
poder marcar como favorito un artista o álbum sin tener nada
descargado de él. Al hablarlo se amplió a un directorio completo
navegable vía MusicBrainz (páginas de Artista/Álbum/Canción, cruzadas
entre sí en ambos sentidos), que unifica las dos búsquedas separadas
que existen hoy (H01 sencillos, H05 álbumes) en una sola experiencia,
con streaming y descarga al vuelo desde cualquier página tenga o no
algo descargado ya. Favoritos de álbum ya existían (`FavoriteAlbum`,
H03); favoritos de artista son concepto nuevo. Diseñado en la propia
sesión de apertura (sin código) y construido entero en la sesión
siguiente (S018): navegación exacta, unificación de búsqueda (con
chips de filtro por tipo, ampliación pedida en la misma S018), modelo
de datos de favorito de artista, desambiguación de artistas homónimos,
y Explorador (letra -> local + muestra paginada de MusicBrainz).
Confirmado en dispositivo real por Miguel Ángel tras dos fixes reales
(normalización de puntuación, cruce con lo descargado por posición de
pista). Ver `DOCS/ANNEX_H12.md` para el detalle completo.

### Hito 13: UX del Reproductor (ExoPlayer) — Estado Visual de Controles
(Ver `DOCS/ANNEX_H13.md`) — Abierto 2026-07-19 (cierre de S018) a
petición explícita de Miguel Ángel: los controles ON/OFF del
reproductor expandido (aleatorio, cíclico) deben verse claramente
activos de un vistazo, no solo con un cambio sutil de color de icono
como hoy. Petición deliberadamente abierta ("chapitas... y aleatorio
deben verse cuando están activos, etc.") -- la sesión que lo retome
debe cerrar el alcance exacto con Miguel Ángel antes de tocar código.
Diagnóstico técnico del estado actual ya leído y documentado en el
anexo. Sin código todavía.

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

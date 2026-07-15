# MIMOO — ANEXO HITO 05
# Búsqueda de Álbumes Completos vía MusicBrainz

*Vive en `DOCS/ANNEX_H05.md` — flujo NewFlow Android. Ver estado en
`DOCS/ANNEX_ROUTER.md`.*

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
| 6 | Verificación funcional end-to-end (dispositivo) | EN CURSO — Prioridad 1 confirmada (S003); S004 resolvió una cadena larga de bugs de fondo (reconciliación, hilos, notificación, firma) que bloqueaban probar esto en condiciones; quedan Lou Reed/Beethoven/playlist normal sin confirmación explícita |

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

## COMPLETADAS EN S004 (2026-07-04 a 2026-07-06, sesión NewFlow — la más larga y de mayor alcance hasta la fecha)

**Nota de alcance:** esta sesión desbordó ampliamente el objetivo
formal de H05 (búsqueda de álbumes vía MusicBrainz). Nació como
continuación del PASO 6c pendiente, pero la propia verificación real
de Miguel Ángel en dispositivo fue destapando una cadena larga de
bugs estructurales de fondo (reconciliación, hilos, firma de
compilación, reproducción en segundo plano) que requerían resolverse
antes de que las pruebas de H05 tuvieran sentido. Se documenta todo
aquí, agrupado por tema, en vez de forzarlo a la forma de "PASO 6c".

**1. Reconciliación SAF↔Room — de frágil a robusta:**
`LibraryReconciler.rescan()` pasó de mirar solo 2 niveles fijos y solo
`.opus`, a recorrido recursivo real con formatos comunes
(mp3/m4a/flac/ogg/wav/aac/wma) — causa raíz real de que pistas de
Beethoven en otro formato aparecieran "ignoradas por completo". Se
hizo resiliente por-hijo (un archivo problemático ya no aborta todo el
escaneo, bug real que dejaba Biblioteca mostrando solo el artista "Air"
tras horas de reproducción). Limpieza añadida: carpetas vacías,
archivos no musicales fuera de la raíz, filas sintéticas muertas,
huérfanos de descarga (`QUEUED`/`DOWNLOADING` sin `WorkRequest` vivo).
**Decisión de producto revertida en la propia sesión:** primero se puso
a reconciliar en cada arranque (petición explícita del 04/07), pero
Miguel Ángel corrigió el 05/07 que debía ser solo en el primer arranque
tras elegir carpeta — con 2.000 canciones sería trabajo repetido
innecesario. Queda así: solo una vez, con spinner de pantalla completa
mientras dura. Todas las operaciones de disco (antes en el hilo
principal sin excepción) pasaron a `Dispatchers.IO` — causa real de
"se queda sin responder" al borrar, sin nada en los logs de error.

**2. Biblioteca — reorganización completa:** navegación por capas
Letras → Artistas → Álbumes → Pistas (Sencillos sin capa de álbum),
sustituyendo la vista plana anterior. Letra de agrupación por nombre
tal cual guardado (sin heurística de apellido). Borrado en cascada
(pista/álbum/artista) con limpieza de carpetas vacías. Favoritos de
**álbum** como concepto nuevo y separado del favorito por pista (tabla
`favorite_albums`, migración v8→v9), con entrada "★ Álbumes favoritos"
antes de las letras.

**3. Descargas — cierre de vectores de "zombis" y mejoras de flujo:**
idempotencia real en `DownloadWorker` (si el archivo ya existe, no
vuelve a descargar), reintento automático hasta 3 veces antes de
marcar error definitivo, orden de pista guardado en el propio nombre
de archivo (`"NN - Título.opus"`) para que sobreviva a una
reconciliación futura — bug real: discos conceptuales como *The Wall*
perdían el orden al reconciliar. Confirmar/editar metadatos antes de
descargar (Búsqueda e Importar enlace, este último corregido para que
el diálogo aparezca siempre, no solo cuando falta nombre de canal).
Pantalla Descargas: borrar definitivamente una descarga que falla
siempre, y "Reintentar todas" de golpe (36 de 100 títulos fallados en
una sesión real).

**4. Búsqueda gratuita:** pantalla de Búsqueda migrada de
`search.list` (100 unidades/llamada) a `yt-dlp` (`ytsearchN:`, coste
cero) — petición explícita de Miguel Ángel, priorizando gratuidad
sobre precisión de metadatos.

**5. Reproducción en segundo plano — la cadena de bugs más larga de
la sesión:** `MediaSessionService` en primer plano (antes, el
`ExoPlayer` vivía en un singleton sin protección, y el sistema mataba
el proceso al cerrar otra app, sin excepción capturada). Encadenado:
`ForegroundServiceDidNotStartInTimeException` (startForeground manual
e inmediato), notificación fija sin controles (ID/canal no coincidían
con `DefaultMediaNotificationProvider`), permiso `POST_NOTIFICATIONS`
nunca solicitado (la notificación no aparecía en absoluto), y
finalmente — confirmado con `notification_debug.txt`, no con teoría —
`onGetSession()` nunca se llamaba porque ningún `MediaController` se
conectaba nunca a la sesión; se añadió uno conectado a la propia
sesión solo para forzar esa conexión. Cola de reproducción de sesión
(temporal, en memoria, distinta de las Playlists guardadas):
Reproducir ahora / a continuación / Añadir al final, gestión con
drag-and-drop. Migrada después a vivir dentro de la playlist real de
`ExoPlayer` (antes solo cargaba una pista suelta cada vez) — causa
real de que el botón "siguiente" de la notificación no funcionara,
mientras "anterior" sí.

**6. Listas de reproducción:** añadir un álbum completo de golpe, no
solo pista a pista (la gestión de listas ya existía completa desde
antes).

**7. Compartir enlaces por WhatsApp:** enlace de origen (`sourceUrl`)
guardado al importar por enlace, para poder compartir el álbum/pista
después. Bug real corregido: pistas sintéticas (reconciliadas desde
disco, `youtubeId` con prefijo `local:`) generaban un enlace
`youtu.be` inválido — ahora `shareableUrl` es `null` para esas, sin
enlace roto.

**8. Identidad visual:** icono de app nuevo (antes no existía
ninguno) — fondo azul MSX2 (nostalgia personal de Miguel Ángel), "mi"
arriba y "Moo" debajo. Tema de la app a azul MSX con letra blanca y
rojo→amarillo en controles/errores (contraste real sobre azul). Nombre
visible corregido de "MiMoo" a "miMoo" en todos los textos de usuario.

**9. Verificación de desarrollador Android (fuera del código, proceso
guiado en tiempo real):** registro completo de cuenta "Full
distribution" en Android Developer Console, identidad verificada,
paquete `com.miguelaetxio.mimoo` registrado y verificado. **Causa
real encontrada de "conflicto con un paquete"/"firma diferente"**
(incidencia arrastrada desde S002, entonces sin resolver): nunca había
un `signingConfig` explícito en `build.gradle.kts` — se había
verificado que el archivo del keystore tenía la huella correcta, pero
nunca que Gradle lo usara de verdad para firmar. Con el
`signingConfig` explícito, Android Developer Console pasó de rechazar
el APK a verificarlo correctamente. Pendiente confirmar en una
actualización real sobre instalación existente (ver hoja de ruta).

**10. Bugs de build corregidos en el camino:** manifiesto XML
inválido (`--` no permitido dentro de comentarios XML, dos veces),
`[Dagger/MissingBinding]` por un `@Provides` olvidado para
`FavoriteAlbumDao`.

**Pregunta abierta sin resolver, planteada por Miguel Ángel:** ¿hace
falta un menú de configuración para elegir tema/color? Se confirmó que
nunca existió; sin decisión tomada.

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

**Verificación en dispositivo de lo nuevo de S005 (prioridad sobre
retomar H05 desde cero):**

- **Toggle de vista en Biblioteca** (Álbumes y Sencillos) — probar en
  dispositivo real: el icono de la TopAppBar alterna correctamente
  entre letras y lista plana, la lista plana muestra todos los
  artistas, y el botón atrás desde dentro de un artista/álbum vuelve
  a la raíz correcta según qué modo estaba activo al entrar.
- **"Editar álbum"** — probar en dispositivo con el caso real que lo
  motivó (el álbum de Herbert von Karajan con el nombre mal escrito):
  confirmar que el menú de tres puntos del álbum muestra "Editar
  álbum", que el diálogo permite corregir artista/álbum, y que tras
  guardar las 4 pistas aparecen bajo el nombre correcto (sin quedar
  huérfanas ni duplicadas) y el archivo físico se movió de carpeta.

**Pendiente original de H05 (PASO 6c), sigue sin confirmación real
tras dos sesiones seguidas de otra actividad (S004 verificación,
S005 UI de Biblioteca):**

- Reimportar Lou Reed - Transformer (búsqueda por álbum) y confirmar
  emparejamiento vía playlist, 11 pistas de golpe.
- Buscar álbum con solo artista o solo título suelto (p.ej.
  "Beethoven"/"Sinfonía") — confirmar tracklist + emparejamiento +
  orden real de pistas.
- "Importar enlace" con una playlist normal de YouTube (no YouTube
  Music) y con un vídeo suelto — confirmar carátula y ruta en disco
  real.

**Decisión de producto pendiente, sin resolver:**

- ¿Hace falta un menú de configuración para elegir tema/color de la
  app? Confirmado que nunca existió; Miguel Ángel no ha decidido si
  se construye.

---

## COMPLETADAS EN S005 (2026-07-07 a 2026-07-08, sesión NewFlow)

- **Verificación en dispositivo de todo S004 — CONFIRMADO:**
  - Actualización real sobre instalación existente en el teléfono
    principal, con el `signingConfig` explícito ya en su sitio: ya no
    da "conflicto con un paquete". Cierra el pendiente heredado de
    S004.
  - Notificación de reproducción (controles reales + botón
    "siguiente" con la cola migrada a la playlist de ExoPlayer),
    reintento/borrado de descargas fallidas, favoritos de álbum y
    compartir enlaces — los cuatro confirmados funcionando en
    dispositivo.
- **Incidencia real encontrada y resuelta — Uri SAF "fantasma" tras
  clonado de dispositivo.** En la tablet: no reproducía nada, la
  descarga fallaba de forma intermitente, y los archivos pegados a
  mano en la carpeta no se reconciliaban. Causa: la tablet se
  configuró clonando los datos del teléfono origen (tipo "Mi Move to
  Xiaomi") en vez de una instalación limpia desde la APK, lo que copió
  el `SharedPreferences` de `StorageManager` con un `Uri` SAF del
  teléfono origen ya guardado — `hasRootUri()` daba `true` en la
  tablet (así que la app nunca relanzaba el selector propio), pero
  `takePersistableUriPermission` nunca se había tomado para ese `Uri`
  en este dispositivo. Solución: desinstalar y reinstalar limpio en
  la tablet. Lección para el futuro: un clonado de dispositivo Android
  puede dejar un `Uri` SAF con apariencia válida pero sin permiso real
  en el dispositivo nuevo.
- **Toggle de vista en Biblioteca (Álbumes y Sencillos)** — petición
  explícita de Miguel Ángel: alternar entre la vista actual por letras
  (Letras → Artistas → Álbumes/Pistas) y una vista plana con todos los
  artistas ordenados alfabéticamente, sin la capa de letras. Añadido
  `AlbumsDrillLevel.ArtistsFlat` / `SinglesDrillLevel.ArtistsFlat`
  como nivel raíz alternativo (junto a `Letters`), con
  `AlbumsViewMode`/`SinglesViewMode` para recordar qué modo está
  activo y que el botón atrás desde Artistas/Álbumes vuelva a la raíz
  correcta según el modo. Icono nuevo en la TopAppBar (lista ↔ A-Z)
  visible en la raíz de cada pestaña. Reutiliza el mismo `ArtistList`
  ya existente, sin duplicar UI. Favoritos no se tocó — esa pestaña ya
  era una lista plana desde siempre, no tenía agrupación por letra.
  Commits `1b0a547` (Álbumes) y `ad3c105` (Sencillos).
- **"Editar álbum" en el menú de un álbum** — petición explícita de
  Miguel Ángel tras encontrar una errata real ("Herbet von Katajan" en
  vez de "Herbert von Karajan") que no se podía corregir: hasta ahora
  solo existía edición de metadatos por pista suelta
  (`editMetadata()`), y corregir un álbum entero pista a pista era
  tedioso y podía dejarlo roto a medias. Nueva función
  `editAlbumMetadata(artist, album, newArtist, newAlbum)` en
  `LibraryViewModel`: reubica el archivo de cada pista del álbum a la
  carpeta `{nuevoArtista}/{nuevoÁlbum}/` vía el mismo
  `TrackFileRelocator`, sin tocar el título de cada pista; si una
  reubicación falla a mitad, se detiene ahí y reporta el error (las
  pistas ya movidas se quedan movidas, sin rollback). Nuevo ítem
  "Editar álbum" en el menú de tres puntos de `AlbumHeaderRow` (nivel
  Álbumes y Álbumes favoritos), con `EditAlbumDialog` (campos Artista
  y Álbum, sin título). Commit `ab61c4e`.
- **BUILD ROTO tras el cierre de S005 y arreglado en la misma
  continuación de sesión.** El commit `ab61c4e` ("Editar álbum") dejó
  `LibraryScreen.kt` con un cierre de bloque duplicado (`},` `)` `}`
  sobrante justo después del nuevo `albumPendingEdit?.let {...}`) —
  un `str_replace` mal delimitado dejó el cierre original del diálogo
  anterior huérfano en vez de dentro de él. El workflow #123
  (`ab61c4e`) y #124 (`df82a76`, el cierre de sesión, que solo tocaba
  `.md` y heredó el mismo `.kt` roto) fallaron con el mismo log
  exacto — no eran dos errores distintos, era el mismo arrastrado.
  Corregido en `b0af47d` eliminando las tres líneas sobrantes;
  **workflow #125 verde, APK ya en PythonAnywhere.** "Editar álbum"
  y el toggle de vista quedan ahora sí listos para probar en
  dispositivo en la próxima sesión.
- **Incidencia de sesión (no de la app) — token PAT expirado a media
  sesión.** El primer token entregado (`mimoo_temp_token`) expiraba el
  8 de julio de 2026; expiró literalmente durante la sesión, dejando
  un commit local (`ab61c4e`) sin pushear hasta que Miguel Ángel
  entregó un token nuevo. Sin impacto en el código, solo constancia
  para el futuro: los tokens de 24h pueden caducar a mitad de una
  sesión larga.

**Pendiente original de H05 (PASO 6c) — sigue sin tocar, otra vez
pospuesto por la actividad de verificación/UI de esta sesión:**

- Reimportar Lou Reed - Transformer (búsqueda por álbum) y confirmar
  emparejamiento vía playlist, 11 pistas de golpe.
- Buscar álbum con solo artista o solo título suelto (p.ej.
  "Beethoven"/"Sinfonía") — confirmar tracklist + emparejamiento +
  orden real de pistas.
- "Importar enlace" con una playlist normal de YouTube (no YouTube
  Music) y con un vídeo suelto — confirmar carátula y ruta en disco
  real.

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

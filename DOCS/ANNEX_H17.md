# Hito 17 — Karaoke & Lyrics

*Apertura: 2026-08-06 (cierre de S030), a petición explícita de Miguel
Ángel. Cita textual: "vamos a pasar a crear un hito nuevo, Karaoke &
Lyrics: consistirá en añadir una entrada en la sidebar para buscar y
leer letras de canciones y una entrada en el menú de tres puntos del
ExoPlayer que abrirá una ventana encima o debajo del mismo donde
visualizar el karaoke del tema que se está ejecutando si se dispone de
letras."*

---

## Objetivo del hito

Dos piezas distintas, un mismo tema de fondo (letras de canciones):

1. **Entrada nueva en el drawer** -- pantalla para BUSCAR y LEER la
   letra de cualquier canción (no necesariamente la que está sonando).
2. **Entrada nueva en el menú de tres puntos del ExoPlayer** (el mismo
   que ya tiene "Ver álbum"/"Ver artista"/"Elegir como tono para un
   contacto", ver `PlayerBar.kt`) -- abre una ventana superpuesta al
   reproductor (encima o debajo, sin decidir todavía) con el KARAOKE
   del tema que está sonando en ese momento, **si se dispone de
   letras** para ese tema concreto.

Sin nada construido todavía en el proyecto para esto -- confirmado
grepeando el código entero de `app/src/main/java` en el cierre de
S030: no hay ningún repositorio, entidad, ni pantalla de letras ya
empezada. Se parte de cero.

---

## Contexto técnico -- qué ya existe y qué hace falta añadir

### Lo que ya existe y se reutiliza tal cual

- **Menú de tres puntos del ExoPlayer** (`PlayerBar.kt`, `DropdownMenu`
  sobre `showMenu`) -- ya tiene "Ver álbum", "Ver artista" y "Elegir
  como tono para un contacto" como precedentes de items condicionales
  (el de tono solo aparece si hay archivo local). La entrada de
  Karaoke encaja en el mismo sitio, con la misma lógica de "solo si
  aplica" (aquí: solo si hay letra disponible para el tema actual, o
  siempre visible pero informando "sin letra" al pulsar -- **decidir**).
- **Patrón de pantalla de gestión con pestañas + chapitas de cristal
  esmerilado** ya consolidado en la app (`DislikedScreen.kt`,
  `MimooutcastScreen.kt`, `FavoritesScreen.kt`) -- mismo lenguaje
  visual esperable para la pantalla de búsqueda de letras.
- **Metadatos de tema en curso** (`PlayerBar.kt`/`PlayerManager`) --
  artista y título estructurados ya disponibles para consultar
  cualquier fuente de letras por "artista + título", sin tener que
  volver a parsear nada.
- **`Room` + repositorios locales** (patrón ya usado en TODA la app,
  p.ej. `DislikedArtistRepository`/`FavoriteTrackRepository`) -- si se
  decide cachear letras localmente para no volver a pedirlas (ver
  punto 3 de "Puntos de diseño -- ABIERTOS").

### Lo que hace falta construir

Todo -- entidad/DAO/repositorio de letras (si se cachean), cliente de
la fuente de letras que se elija, pantalla de búsqueda en el drawer,
ventana de karaoke sobre el ExoPlayer, entrada en el menú de tres
puntos, entrada en `NavGraph.kt`/`MainActivity.kt`.

---

## Puntos de diseño -- CERRADOS EN S031

Los seis puntos quedaron cerrados con Miguel Ángel, en sesión de
diseño puro (sin código), en el orden previsto -- el punto 1
condicionaba el 2, tal como estaba anotado.

1. **Fuente de las letras: lrclib.net.** Confirmado en línea (S031):
   API abierta, gratuita, sin API key ni registro, sin límite de
   peticiones. Endpoints relevantes -- `GET /api/get` (búsqueda exacta
   por track/artista/álbum/duración) y `GET /api/search` (búsqueda más
   flexible, la que usa el punto 5). La respuesta trae `syncedLyrics`
   (formato LRC, timestamp por línea) y `plainLyrics` por separado --
   un tema puede tener ambas, solo la plana, o ninguna.
2. **Definición de "karaoke" y comportamiento según disponibilidad de
   letra --** revisado una vez cerrado el punto 4 (ver más abajo, la
   revisión queda incorporada aquí sin dejar versiones contradictorias:
   - Si el tema tiene `syncedLyrics` -> karaoke real, resaltado línea a
     línea según el tiempo de reproducción.
   - Si el tema NO tiene `syncedLyrics` pero SÍ tiene `plainLyrics` ->
     se muestra la letra completa, scrolleable, SIN resaltado y SIN
     ningún aviso de "no hay karaoke" (el aviso se reservó
     exclusivamente para el caso siguiente).
   - Si el tema no tiene ninguna letra (ni sincronizada ni plana) ->
     mensaje informativo de que no hay letra disponible.
3. **Caché local en Room.** Mismo patrón que el resto de la app
   (Radio, Favoritos, Lista Negra): primera consulta a lrclib.net por
   artista+título, resultado guardado localmente (letra sincronizada /
   letra plana / "sin letra confirmado"); consultas siguientes del
   mismo tema se sirven de caché sin red.
4. **Forma de la ventana de karaoke sobre el ExoPlayer.** Panel de
   cristal esmerilado justo encima del reproductor (que hoy ocupa ~1/3
   de la pantalla), con altura variable según el caso:
   - **1/3 de pantalla** cuando se muestra letra plana scrolleable (sin
     `syncedLyrics` pero con `plainLyrics`).
   - **1/9 de pantalla** cuando hay karaoke sincronizado activo (panel
     tipo teleprompter, se auto-desplaza con el tiempo, no necesita
     scroll manual) y también cuando no hay ninguna letra (mensaje
     informativo mínimo).
5. **Alcance de la pantalla de búsqueda del drawer: ambas, con
   distinción visual.** Buscador libre contra `lrclib.net` (endpoint
   `search`, cualquier canción exista o no en la biblioteca), marcando
   con un chip/icono los resultados que coinciden con algo ya
   descargado en MiMoo.
6. **Procedencia de pista: aplica a cualquier origen EXCEPTO Radios
   Online del Mundo (H09) y cualquier stream sin metadatos fiables.**
   Biblioteca local, Radio (H08), Popurrí, miMooutCast (H15) y
   streaming de búsqueda (H01) sí lo tienen -- todos resuelven
   artista+título estructurados igual. Radio-Browser.info (H09) queda
   excluido explícitamente por Miguel Ángel.

---

## COMPLETADAS EN S031

Los tres bloques de la Hoja de Ruta, el log de diagnóstico y un fix
real encontrado con ese mismo log, construidos y en build verde
(GitHub Actions, runs `8959f75`, `ddd61e2`, `ecf2463`, `f5e1f7c` y
`c8eb2e9`), pendientes solo de verificación en dispositivo real:

- **Bloque 1 -- cliente de lrclib.net + caché local.**
  `LrcLibApiService` (endpoints `get`/`search`, campos de respuesta
  verificados en línea esta sesión), `NetworkModule` (Retrofit +
  OkHttpClient con User-Agent recomendado), entidad `LyricsCache` +
  `LyricsCacheDao` (clave compuesta artistKey+titleKey normalizada vía
  `SearchNormalizer`), `AppDatabase` v14->v15 (`MIGRATION_14_15`),
  `LyricsRepository` (`getLyrics()` cachea con o sin letra;
  `searchLyrics()` búsqueda libre en crudo para el bloque 3).
- **Bloque 2 -- ventana de karaoke sobre el ExoPlayer.**
  `PlaybackState.currentIsRadioStation` (nuevo campo en
  `PlayerManager.kt`, reflejo de `QueueItem.isRadioStation` de la
  pista en curso -- implementa la exclusión del punto 6).
  `PlayerBarViewModel`: estado de letras + `toggleLyricsPanel()`,
  refresco automático al cambiar de pista si el panel está abierto.
  `LrcParser` (util): parseo de `syncedLyrics` (LRC) a líneas con
  timestamp. `PlayerBar.kt`: entrada "Karaoke"/"Ocultar karaoke" en el
  menú de tres puntos (oculta si `currentIsRadioStation`),
  `KaraokeLyricsPanel` justo encima del reproductor expandido con las
  tres alturas/variantes cerradas en el punto 4 del diseño, y
  `KaraokeTeleprompter` con auto-scroll por posición de reproducción.
- **Bloque 3 -- pantalla de búsqueda de letras del drawer.**
  `LyricsSearchViewModel`/`LyricsSearchScreen` (ui/lyricssearch):
  búsqueda libre por botón contra `LyricsRepository.searchLyrics()`,
  distinción visual "ya en tu biblioteca" (chip) cruzando artista+
  título normalizados contra `SearchResultTrackRepository`, letra
  legible al expandir un resultado (`plainLyrics` con prioridad,
  `syncedLyrics` desnudado de timestamps como respaldo). Ruta
  `Screen.LyricsSearch` en `NavGraph.kt`, entrada "Letras" en el
  drawer de `MainActivity.kt`.
- **Log de diagnóstico** (`LyricsDebugLogger`, mismo patrón exacto que
  `RadioDebugLogger`/`RadioBrowserDebugLogger`, run `f5e1f7c`):
  `letras_debug.txt`, últimas 400 líneas. `LyricsRepository` registra
  cada decisión real de `getLyrics()`/`searchLyrics()` -- hit de caché
  (con qué tipo de letra), consulta a lrclib.net, excepción si falla
  la petición, y el resultado final antes de cachear (sincronizada /
  plana / instrumental / sin ninguna letra, con el id de lrclib.net).
  Petición explícita de Miguel Ángel al detectar que ambos métodos son
  defensivos por diseño (`catch (e: Exception)` -> "sin letra"/lista
  vacía en silencio) y no dejaban ninguna pista diagnosticable.
- **Fix real encontrado en la primera prueba en dispositivo** (run
  `c8eb2e9`), diagnosticado con el propio `letras_debug.txt`:
  `getLyrics()` consultaba lrclib.net con el título CRUDO del vídeo de
  YouTube ("The Beach Boys - Surfin' U.S.A. (Lyric Video)", "Eric
  Clapton - Wonderful Tonight [Official Live]"...) en vez del título
  real de la canción. `GET /api/get` de lrclib.net exige coincidencia
  exacta de `track_name`, así que fallaba con 404 en la mayoría de
  casos (Willie Nelson, King Crimson, Thin Lizzy en el log real), y en
  los que "acertaba" (Beach Boys, Eric Clapton, Roy Orbison) era por
  coincidencia parcial inconsistente -- el único tema con título ya
  limpio en el log (Fleetwood Mac, `title='Go Your Own Way'`, sin
  prefijo de artista ni sufijo) fue el único fiable de primeras.
  Arreglo: `SearchNormalizer.cleanSongTitle()` (nueva función, mismos
  cuatro primeros pasos que `songTitleKey()` -- quitar paréntesis/
  corchetes, partir por guion, quitar el segmento que sea el propio
  artista, quitar coletillas finales y años sueltos -- pero SIN el
  paso final de `normalize()`+`tight()`, conservando texto legible
  para la API en vez de una clave interna), usada en
  `PlayerBarViewModel.fetchLyricsForCurrentTrack()` antes de llamar a
  `LyricsRepository.getLyrics()`. Verificado contra los 7 casos reales
  del log: los 7 resuelven al título real de la canción.

Sin tocar: verificación en dispositivo real de que el fix resuelve los
casos que antes daban 404 (Willie Nelson, King Crimson, Thin Lizzy).

---

## Hoja de Ruta para la Siguiente Sesión que retome H17

1. Verificar en dispositivo real, con el fix de `cleanSongTitle()` ya
   aplicado, que los temas que antes fallaban con 404 en
   `letras_debug.txt` (Willie Nelson "Always On My Mind", King Crimson
   "The Court Of The Crimson King", Thin Lizzy "The Boys Are Back In
   Town") ahora sí traen letra si lrclib.net la tiene. Verificar
   también el resto sin construir: cliente + caché de letras (bloque
   1), ventana de karaoke con sus tres variantes de altura/contenido y
   su exclusión de Radio-Browser.info (bloque 2), y la pantalla de
   búsqueda de letras del drawer con su distinción visual "ya en tu
   biblioteca" (bloque 3). Sin código pendiente -- si la verificación
   revela algo roto, es una incidencia real que retoma H17
   puntualmente (PCH), no algo que quede "a medias" de S031.

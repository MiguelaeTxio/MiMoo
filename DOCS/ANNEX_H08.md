# MIMOO — ANEXO HITO 08
# Búsqueda de Listas de Reproducción + Música Relacionada ("Radio")

*Vive en `DOCS/ANNEX_H08.md` — flujo NewFlow Android. Ver estado en
`DOCS/MASTER_DOCUMENT.md`. EN PROGRESO.*

---

## NOTA DE APERTURA (S008, 2026-07-11)

Hito nuevo, abierto a petición explícita de Miguel Ángel al cierre de
la sesión de H07 (persistencia de enlaces, sincronización automática,
actualizaciones in-app, PIN, cíclico/aleatorio — completado y
verificado en dispositivo real con dos dispositivos en esa misma
sesión). Dos funciones comparten hito por surgir de la misma
conversación de cierre, sin dependencia técnica real entre ellas — ver
`ANNEX_H07.md` para el precedente de este mismo criterio de
agrupación.

---

## PARTE 1 — Búsqueda de listas de reproducción y canales (online)

**Corrección de alcance (S009).** Definición inicial equivocada: se
implementó primero un filtro sobre las playlists **locales** del
propio usuario (`PlaylistsScreen.kt`) — funcionalidad real y ya
mereció la pena, **se queda**, pero no es esto. Lo que pidió Miguel
Ángel es buscar por texto (p. ej. "música de los 90") y encontrar
**listas de reproducción ya creadas por otros usuarios en YouTube**,
para escucharlas en streaming o descargarlas — esto toca la pantalla
de Búsqueda (H01), no Playlists locales.

**Requisito explícito: búsqueda 100% gratuita, cero cuota** — mismo
principio que ya rige toda la búsqueda de MiMoo desde H01 (PASO
2026-07-04, `ExternalLinkResolver.searchYoutube()`). Verificado
(S009): el mecanismo es el mismo scraping directo de la página de
resultados de YouTube que ya usa la búsqueda de vídeos actual
(`youtube.com/results?search_query=...`), no la Data API — cero coste
añadido.

**Filtro por tipo — verificado con captura real de la app de YouTube
(S009):** el propio selector "Filtros de búsqueda" de YouTube
distingue Todo / Vídeos / Shorts / Canales / Listas / Películas — no
existe un filtro de tipo "Podcast" ni "Audiolibro". Valores del
parámetro `sp` (confirmados por varias fuentes independientes, no solo
una):
- **Listas:** `sp=EgIQAw%3D%3D`
- **Canales:** `sp=EgIQAg%3D%3D`

**Colocación en la UI — RESUELTO (S009).** Miguel Ángel confirmó: un
selector dentro de la propia pantalla de Búsqueda ("un selector estaría
bien... así no complicamos mucho la sidebar"), no el drawer de
navegación. Implementado como chips (`FilterChip`, Material 3):
Vídeos / Listas / Canales.

**Aviso de riesgo técnico, no oculto:** el tracker de yt-dlp muestra
temporadas en las que la búsqueda filtrada por tipo (Listas/Canales)
ha dejado de devolver resultados (roto y luego parcheado). Es una
zona menos estable que la búsqueda normal de vídeos. No hay forma de
probarlo en vivo desde el entorno del modelo (`api.github.com` y
similares están permitidos, `youtube.com` no) — la verificación real
solo puede pasar por construirlo y probarlo en dispositivo real.

### Hoja de ruta

**PASO 1.1 — HECHO (S009), pero fuera del alcance real.** Filtro
sobre playlists locales (`PlaylistsViewModel`/`PlaylistsScreen.kt`),
con `SearchNormalizer` insensible a acentos (corregido también
retroactivamente en `LibraryViewModel`). Se conserva como mejora
independiente, no cuenta como progreso de esta PARTE.

**PASO 1.2 — HECHO (S009).** `link_resolver.search_by_type(query, sp,
limit)`: resuelve `youtube.com/results?search_query=...&sp=...` con
`extract_flat`, devolviendo `{"results": [...]}` (id, title, url,
subtitle, thumbnail_url) — playlists o canales según el `sp` pasado.
Nunca lanza por "sin resultados", solo si yt-dlp no puede llegar a la
página.

**PASO 1.3 — HECHO (S009).** Capa Kotlin: `SearchTypeResult`/
`SearchTypeResultsWrapper` (DTOs), `SearchResultType` enum (con los
dos valores `sp` verificados) y `ExternalLinkResolver.searchByType()`.

**PASO 1.4 — HECHO (S009).** Integración en `SearchScreen.kt`:
selector de modo (chips), lista de resultados de playlist/canal
(`SearchTypeResultRow`), y al tocar uno se navega a `ImportLinkScreen`
con la url ya resuelta (`Screen.ImportLink.routeFor(url)`, argumento
de navegación opcional nuevo) — `ImportLinkViewModel` la detecta por
`SavedStateHandle` y llama a `resolveLink()` sola, reutilizando el
100% del flujo ya existente de "Importar enlace" (elegir pistas,
reproducir en streaming o descargar) sin duplicar lógica.

**PASO 1.5 — PENDIENTE.** Verificación en dispositivo real: confirmar
que la búsqueda filtrada por Listas y por Canales sigue devolviendo
resultados hoy (dado el histórico de inestabilidad de yt-dlp en esta
zona), y que abrir un resultado lleva correctamente a "Importar
enlace" con la lista/canal ya resuelto.

---

## PARTE 2 — Música relacionada ("Radio")

**Diseño cerrado y CONSTRUIDO (S009).** Decisiones explícitas de
Miguel Ángel:

1. **Disparo:** al terminar la última canción de la cola, con cíclico
   desactivado. Sin tercer control aparte que activar/desactivar.
2. **Fuente del "relacionado":** MusicBrainz (géneros compartidos).
   Descartado el Mix automático de YouTube (`list=RD...`) — el
   tracker de yt-dlp muestra errores documentados ("This playlist
   type is unviewable") y reportes recientes que sugieren necesidad
   de sesión/cookies de YouTube iniciada, infraestructura que MiMoo no
   tiene ni tenía previsto tener.
3. **Solo streaming, nunca descarga** — cita textual: "para eso están
   las listas, los álbumes y los sencillos... descargar una lista que
   se va autogenerando sería una brutalidad".

### Implementación (S009, corregida tras prueba real)

**Corrección tras probarlo en dispositivo (S009):** la primera versión
tenía dos fallos.
1. Añadía la pista siguiente pero no arrancaba sola — `player.play()`
   no basta para salir de `Player.STATE_ENDED`; hacía falta volver a
   llamar a `prepare()`, documentado por Media3. Corregido.
2. Una sola pista no era "radio" de verdad. Petición explícita tras
   probarlo: mantener siempre hasta **10 pistas** de Radio por
   delante en la cola, reponiendo una cada vez que la que suena
   termina, no esperar a quedarse sin nada para buscar la siguiente.

- `MusicBrainzApiService.searchArtists()`/`lookupArtist(inc=genres)`
  — nuevos endpoints, verificados contra la documentación oficial de
  MusicBrainz (API estable, sin el riesgo de inestabilidad de
  yt-dlp/YouTube).
- `RadioRepository.suggestRelatedArtist(artista)`: resuelve MBID →
  géneros → busca otros artistas con uno de esos géneros → elige uno
  al azar entre los candidatos, excluyendo al propio artista de
  origen. Nunca lanza excepción (mismo patrón que
  `CoverArtRepository`).
- `QueueItem` gana `artist: String?` e `isFromRadio: Boolean` — hilado
  por todos los puntos de la app que construyen la cola (Biblioteca,
  Playlists, Importar enlace, Búsqueda).
- `PlayerManager.topUpRadioQueueIfNeeded()` (RADIO_QUEUE_SIZE = 10):
  mientras el backlog de pistas `isFromRadio` por delante de la
  actual sea menor que 10 y el cíclico esté desactivado, sigue
  pidiendo una más (`fetchOneRadioTrack()`: relacionado vía
  MusicBrainz → búsqueda gratuita en YouTube → resolución de stream)
  y la añade a la cola. Se llama desde `onPlaybackStateChanged`
  (STATE_ENDED — arranque inicial) y desde `onMediaItemTransition`
  (solo si la pista a la que se acaba de saltar es ella misma de
  Radio — sin necesitar ningún flag de "modo Radio" aparte). Guardia
  `isRadioTopUpRunning` para no relanzar la corrutina si ya hay una
  en marcha. Todo en el `CoroutineScope` propio del singleton
  (`managerScope`), con vuelta a `Dispatchers.Main` antes de tocar el
  `ExoPlayer`. Completamente silenciosa si no encuentra nada.

### Segunda corrección (S009, tras capturas reales de la cola)

El primer intento de corregir el autoplay (`prepare()` +
`seekTo()` + `play()` al detectar `STATE_ENDED`) **seguía sin
funcionar** — Miguel Ángel confirmó que la pista siguiente se veía en
cola pero no arrancaba sola. Diagnóstico real, no repetir el mismo
parche: reanudar un ExoPlayer que ya ha llegado a `STATE_ENDED` es
frágil de por sí, mejor evitar llegar a ese estado.

**Fix de fondo:** `topUpRadioQueueIfNeeded()` ya no se dispara solo
al llegar a `STATE_ENDED` (reactivo) — se dispara también en cuanto
empieza a sonar la ÚLTIMA pista de la cola, aunque sea la única
(proactivo, en `onMediaItemTransition`). Así, cuando esa pista
termina de verdad, ExoPlayer ya tiene la siguiente en su propia
lista y avanza solo con el mecanismo de auto-avance normal (el mismo
que ya funciona siempre para cualquier cola con más de una pista) —
sin depender de resucitar el player desde un estado terminal. El
disparo en `STATE_ENDED` se conserva como red de seguridad (por si la
red tardó más que la propia canción), pero deja de ser el mecanismo
principal.

**Detectado por el propio modelo en las capturas aportadas, no
reportado por Miguel Ángel:** varios de los "temas" que añadía la
Radio eran en realidad vídeos de "Greatest Hits Full Album" de 1-2
horas (Elvis, Beatles, Led Zeppelin, Grateful Dead...) — el motivo:
`searchYoutube(artista, limit=1)` se quedaba con el primer resultado
sin más criterio, y el primer resultado al buscar solo el nombre de
un artista es a menudo una compilación. Corregido: `limit=6` +
filtro de duración (`RADIO_MAX_TRACK_SECONDS`, 15 min, generoso a
propósito) + lista de palabras que delatan compilación en el título
(`COMPILATION_TITLE_HINTS`).

**Mejoras de `PlayerBar` en la misma pasada (peticiones explícitas):**
- Botón "anterior" siempre visible, no solo con cola de más de una
  pista — `PlayerManager.playPrevious()` reinicia la pista actual
  desde el principio si no hay una anterior real.
- Tiempo transcurrido/restante + barra de progreso arrastrable
  (`PlayerManager.seekTo()` nuevo, `positionMs` sondeado cada 500ms
  desde `PlayerBarViewModel` — ExoPlayer no notifica la posición de
  forma continua, solo eventos puntuales).

### Tercera corrección (S009, tras confirmar autoplay funcionando)

**Buena noticia confirmada por Miguel Ángel:** el autoplay ya
funciona de verdad — la cola avanza sola sin tocar nada.

**Fallo nuevo, distinto:** la Radio solo llegó a añadir 3 temas
(nunca los 10) y luego dejó de reponer por completo, incluida la
última pista sin nada detrás de ella. Causa raíz confirmada leyendo
el código: **"Various Artists"** es una entidad real de MusicBrainz
(usada para créditos de compilaciones), sin géneros propios. Cuando
`RadioRepository.suggestRelatedArtist()` la eligió como "relacionada"
(posible porque estaba etiquetada con el género buscado), la
siguiente iteración intentó buscar los géneros de "Various Artists"
— vacío, `suggestRelatedArtist()` devolvió `null`, y el bucle de
`topUpRadioQueueIfNeeded()` se detuvo con un `break` sin reintentar
nunca más.

**Corrección:**
- `RadioRepository.isPlaceholderArtist()`: descarta "Various Artists"
  (y otras entidades placeholder equivalentes:
  `[unknown]`/`[anonymous]`/`[traditional]`) tanto como origen como
  candidato.
- `PlayerManager`: nuevo `radioAnchorArtist` — el artista que de
  verdad arrancó la Radio (el último tema propio del usuario, no de
  Radio). Si el eslabón inmediato de la cadena muere, se reintenta
  una vez desde el ancla antes de rendirse — defensa adicional para
  cualquier otro artista igual de "sin géneros" que pueda aparecer en
  el futuro, no solo para este caso concreto.

### Cuarta observación (S009, calidad del "relacionado" — POSPUESTA, no ejecutar en H09)

Tras confirmar que el mecanismo funciona de forma sostenida (autoplay
real, buffer de 10, sin cortes), Miguel Ángel probó la calidad real de
las sugerencias: puso un tema de Héroes del Silencio (rock español) y
la Radio, tras esa pista, volvió a sugerir temas al azar en inglés —
nada relacionado con rock en español ni con el idioma de la pista de
origen.

**Diagnóstico de Miguel Ángel, textual:** "no hay un relacionado real
[...] lo que estamos haciendo es poner temas, una radio de poner
temas, poner temas, poner temas. Pero tendríamos que tener un baremo
para elegir esos temas que se van a poner."

**Lectura técnica de por qué pasa esto (no verificado a fondo
todavía, para la sesión que retome esto):** el algoritmo actual de
`RadioRepository.suggestRelatedArtist()` solo mira **género** de
MusicBrainz (`tag:"<género>"`) — nunca **idioma**, ni ningún otro eje
de similitud. Un género tan amplio como "rock" agrupa por igual a
Héroes del Silencio, Nirvana o AC/DC, así que estadísticamente es
totalmente esperable que la mayoría de resultados salgan en inglés,
simplemente porque hay muchísimo más rock anglosajón que hispanohablante
catalogado en MusicBrainz con esa etiqueta. No es un bug puntual — es
que el "baremo" actual (un único género compartido, elegido al azar
entre los que tenga el artista de origen) es demasiado pobre para lo
que Miguel Ángel espera de un "relacionado real".

**Explícitamente pospuesto — NO se toca en la próxima sesión.** La
siguiente sesión se dedica en exclusiva a H09 (SHOUTcast vía
Radio-Browser.info). Esta observación queda anotada para cuando se
retome H08 más adelante, sin fecha decidida. Cuando se retome, hará
falta una conversación de diseño con Miguel Ángel sobre qué "baremo"
usar de verdad (¿idioma como filtro obligatorio, no solo género?
¿combinar varios ejes de MusicBrainz -- género + idioma + época --
en vez de uno solo? ¿alguna otra fuente de datos?) antes de tocar
código, mismo criterio que ya se aplicó al diseño original de esta
misma PARTE 2 en S008.

### Pendiente

**PASO 2.2 — PENDIENTE.** Volver a verificar en dispositivo real:
confirmar que ahora sí llega a mantener ~10 pistas de forma sostenida
sin cortarse, con el autoplay ya confirmado funcionando.

**PASO 2.3 — PENDIENTE, pospuesto explícitamente.** Mejorar el
"baremo" de relacionado (ver observación de arriba) — no antes de
cerrar H09.

---

## Fuera de Alcance de Este Hito

- Cualquier forma de "me gusta"/entrenamiento de preferencias más allá
  de favoritos ya existentes (H03) — no se ha planteado, no está en
  el objetivo descrito por Miguel Ángel.
- Playlists colaborativas o compartidas entre Miguel Ángel y Silvia —
  no mencionado, fuera de alcance salvo que se pida explícitamente.

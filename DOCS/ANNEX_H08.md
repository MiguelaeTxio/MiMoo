# MIMOO — ANEXO HITO 08
# Búsqueda de Listas de Reproducción + Música Relacionada ("Radio")

*Vive en `DOCS/ANNEX_H08.md` — flujo NewFlow Android. Ver estado en
`DOCS/ANNEX_ROUTER.md`.*

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

**PASO 2.2 — RESUELTO (S010).** Verificado en dispositivo real por
Miguel Ángel: la Radio mantiene ~10 pistas de forma sostenida sin
cortarse.

**PASO 2.3 — RESUELTO (S010).** "Baremo" de relacionado mejorado:
además del género, `RadioRepository.suggestRelatedArtist()` ahora
también filtra por país del artista de origen (`country:<código>`,
campo nativo de MusicBrainz), con reintento sin país si esa búsqueda
acotada no encuentra candidatos. Confirmado por Miguel Ángel tras
prueba real: sembrar con techno da techno, con rock da rock — ya no
sale el "popurrí de música inglesa" reportado en la observación
anterior.

Dos bugs reales adicionales encontrados y corregidos en la misma
sesión, con evidencia de log real (`radio_relacionados_debug.txt`
nuevo en S010, mismo patrón que `RadioBrowserDebugLogger`/
`BackupDebugLogger`):

- **Sufijo `" - Topic"` sin limpiar.** YouTube nombra automáticamente
  los canales de audio autogenerado como `"<Artista> - Topic"`, y ese
  sufijo se colaba tal cual como `channel_title`/`artist` en toda la
  app (no solo Radio) — causa real de que la cola no se generase la
  primera vez que se probó tras el fix de país. Corregido en el origen
  único de ese dato, `link_resolver.py` (mismo bloque donde ya se
  normalizaba el caso `"-"` como uploader, precedente de H03/S006).
- **`onPlayerError` sin gestionar.** El `Player.Listener` de
  `PlayerManager` nunca implementaba `onPlayerError()` — si el stream
  de una pista fallaba, ExoPlayer pasaba a `Player.STATE_IDLE` (mismo
  tipo de estado terminal que `STATE_ENDED`, requiere `prepare()` para
  reanudar) y el reproductor se quedaba mudo sin ningún aviso, sin que
  ni el botón "Siguiente" lo resucitara. Corregido con recuperación
  automática (salta a la siguiente pista + `prepare()`+`play()`) y
  refuerzo defensivo en `playNext()`/`playPrevious()`.

---

## Cerrado (S010)

Confirmado por Miguel Ángel tras prueba real sostenida: la Radio
funciona de principio a fin -- autoplay, buffer de 10, país+género
coherentes, recuperación ante fallos de stream. Se da por zanjado salvo
que surja un nuevo fallo en uso real.

---

## Ampliación S011 (2026-07-18): filtro por década + diccionario de éxitos conocidos

Reabierto puntualmente sobre el cierre de S010 -- fallo real reportado
por Miguel Ángel en dispositivo: *"he puesto una canción de Alaska y
Dinarama y ahora me pone reguetón... si pones una canción de los
Beatles no es lógico que después te ponga reguetón"*. País+género no
bastaban -- dos artistas del mismo país y género pueden ser de décadas
completamente distintas.

**Filtro por década**, mismo patrón que país+género (S010): se fija
UNA VEZ al arrancar la sesión, del primer artista, y nunca se
recalcula en saltos posteriores.
- `RadioAnchor` gana `decadeBegin: Int?`, calculado en
  `resolveAnchor()` a partir de `life-span.begin` del artista en
  MusicBrainz (nuevo campo en `MusicBrainzArtistDetail`, de primer
  nivel, sin `inc=` especial) -- redondeado hacia abajo a la década.
- `findCandidates()` añade `begin:[década TO década+9]` a la query
  Lucene (campo `begin` confirmado como campo de búsqueda real de
  artista antes de usarlo). Cascada de fallback en 4 niveles:
  género+país+década → género+década → género+país → género solo.

**Diccionario de artistas conocidos por década** (petición explícita:
*"solo vamos a tener que obtener las listas una única vez... sacamos
un diccionario, ya está"*) -- `assets/known_hit_artists.json`,
compilado UNA SOLA VEZ leyendo Wikipedia (números uno de LOS40/España
por década, verificados en vivo para 2020-2024) y conocimiento
histórico de Billboard, sin scraping en tiempo de ejecución ni llamada
de red para esto. A nivel de ARTISTA, no de canción concreta -- encaja
con que `RadioRepository` ya sugiere artistas, nunca canciones sueltas.
`KnownHitsRepository` (nuevo) lo carga una vez (`lazy`) y consulta por
artista+década.

**Cupo de exploración del 10%** (petición explícita: *"si no está en
la base de datos, no se mete, hasta que digamos que falte un 10%...
una de cada diez"*) -- `PlayerManager.isAcceptableByHitsQuota()`:
9 de cada 10 pistas que Radio añade deben ser un artista conocido; la
décima puede ser cualquier cosa. `radioTracksAccepted`/
`radioExploreTracksUsed`, reiniciados junto con `radioAnchor` en cada
sesión nueva.

**Fuentes descartadas tras investigación real** (Billboard, Promusicae,
40 Principales, 40 Principales Classics): ninguna tiene API pública,
solo páginas web pensadas para lectura humana. Sustituidas por
Wikipedia como fuente única de "históricos" -- misma cobertura de
intención (canciones que de verdad fueron populares) sin mantener
cuatro scrapers frágiles ni dudas de permisos de uso.

**Sin verificar en dispositivo real todavía** -- pendiente confirmar
que Alaska y Dinarama ya no deriva a reguetón, y que el diccionario
tiene cobertura suficiente para no romper la cadena de sugerencias
con demasiada frecuencia (JSON deliberadamente no exhaustivo, ver
comentario del propio archivo).

---

## Reconciliación de una brecha documental: S012 (commit sin
## documentar, previo a esta sesión)

Al arrancar S013 se detectó un commit real (`f9c7cfd`, ya en
`origin/main` antes de que empezara esta sesión) que nunca quedó
documentado en este anexo ni en `RESUMPTION_POINT.md` -- mismo patrón
de brecha que ya pasó con H09/S010. Mensaje del commit: *"fix: Radio
no se para entera por un rechazo del cupo de éxitos conocidos"*.

**Bug real que arregla (evidenciado con log real de Miguel Ángel,
`radio_relacionados_debug.txt`, caso 'Belle and Sebastian'
rechazado -- ancla 'Lori Meyers', década=1990):** antes, cuando
`isAcceptableByHitsQuota()` rechazaba un candidato por cupo,
`fetchOneRadioTrack()` devolvía `null` directamente, y
`topUpRadioQueueIfNeeded()` trataba eso exactamente igual que "no
quedan candidatos en absoluto" -- parando la Radio entera con un solo
rechazo de cupo, algo que por diseño debe pasar 1 de cada 10 veces.
Corregido: un rechazo de cupo ahora pide otro candidato en la misma
llamada (`rejectedByQuota`, excluyendo el ya rechazado), sin gastar
ninguna búsqueda de YouTube en él. Solo se rinde de verdad cuando
`suggestRelatedArtist()` ya no tiene ningún candidato más que ofrecer.

**El log que aportó Miguel Ángel para esta sesión se generó con una
build ANTERIOR a este commit** (no muestra el segundo intento tras el
rechazo) -- así que este fix concreto sigue sin confirmación de
dispositivo real, pero la causa que describe el log ya tiene
corrección escrita y en `main`.

---

## S013 (2026-07-18): diseño cerrado del rediseño completo de Radio
## -- SIN CONSTRUIR, hoja de ruta para la sesión siguiente

Sesión de diseño puro a petición explícita de Miguel Ángel ("no vamos
a hacer aquí un trabajo de este hito para no terminar... cortamos aquí
y lo hacemos todo en la siguiente") -- se acordó el diseño completo
con él, pero **no se ha tocado código de este rediseño en S013**. Lo
único ya en `main` es la reconciliación de S012 (arriba) y el cambio
de hito del PCH (H09 a PAUSADO, H08 a EN PROGRESO, ver
`DOCS/ANNEX_ROUTER.md`).

### Motivación real (evidencia de S013)

Con el log `radio_relacionados_debug.txt` de Miguel Ángel se
confirmaron DOS problemas reales, distintos del que arregla S012:

1. **Sesgo hacia música inglesa pese al filtro país=ES.** La cascada
   género+país+década de MusicBrainz cae a "género solo" con mucha
   frecuencia (log: docenas de "0 candidatos CON país, reintentando
   solo por género"), y en ese fallback el cupo de éxitos
   (`isAcceptableByHitsQuota`) no distingue origen -- acepta igual de
   contento a Queen o a The Police que a Estopa, porque
   `known_hit_artists.json` es una lista plana sin marca de origen
   (Billboard y LOS40 mezclados en la misma lista por década, sin
   ningún campo que diga cuál es cuál).
2. **Colisión de nombre entre artista y vídeo real** (mismo patrón que
   ya se vio con "The Enid" -> tema de "Hey Duggee" en S010): buscar
   solo el nombre del artista en YouTube a veces devuelve un vídeo de
   otro tema homónimo de otro artista completamente distinto (caso
   real: MusicBrainz artist "Tonto" -- pop rock, ES -- resuelto a un
   tema de reguetón de J Balvin llamado igual; también "Cork" ->
   vídeo turístico, "Mate" -> vídeo de cómo hacer yerba mate,
   "Asuntos Internos" -> tráiler de película).

### Diseño cerrado, punto por punto (conversación completa con Miguel
### Ángel, S013)

**1. Regla de origen -- se fija con el PRIMER tema de la sesión de
Radio, igual que género/década, y es ABSOLUTA para el resto de la
sesión (nunca se relaja, ni siquiera en el último peldaño del
fallback):**
- Primer tema de un **grupo español** -> TODA la sesión de Radio es
  de grupos españoles, sin excepción. El idioma en que canten es
  irrelevante -- lo que cuenta es que el grupo sea español (hay
  grupos españoles que cantan en inglés).
- Primer tema de un **grupo extranjero** -> el resto puede ser
  español o extranjero sin restricción de origen, pero cuando sea
  extranjero tiene que ser un tema CONOCIDO EN ESPAÑA de ese artista
  (ver punto 2) -- nunca cualquier tema del Billboard sin más, porque
  "el Billboard aquí no lo conocemos".

**2. "Conocido" ya no es a nivel de artista, es a nivel de
canción concreta.** El diccionario deja de ser
`{década: [artistas]}` y pasa a ser, por década, una lista de pares
`{artista, canción}` -- la canción que de verdad se conoce en España
de ese artista en esa década. Caso guía explícito de Miguel Ángel:
Yes en los 80 -> "Owner of a Lonely Heart" ("Dueño de un Corazón
Solitario"), nunca "Roundabout" (que sí sería la correcta si el ancla
fuera los 70). Cuando el cupo acepta un candidato por estar en el
diccionario, la búsqueda en YouTube pasa a ser "artista + título de
la canción concreta" en vez de solo "artista" -- esto además de
acertar la canción correcta, mitiga de paso el problema 2 de la
motivación (colisión de nombre), porque una búsqueda "artista +
canción" es mucho más específica que solo "artista".

**3. Estructura del diccionario ampliado.** Separar
`known_hit_artists.json` en dos listas por década --
española/internacional -- cada entrada con su canción concreta (en
vez de una lista plana de nombres como ahora). Esto sustituye por
completo el `Map<Int, Set<String>>` actual de `KnownHitsRepository`.
Compilar con el mismo criterio que la primera vez (conocimiento
propio + verificación puntual por búsqueda donde haya duda, sin
scraping en tiempo de ejecución) -- reutilizar los ~210 artistas ya
existentes como base y asignarles su canción por década, no empezar
de cero.

**4. Cómo se sabe si un candidato es "de aquí" que no está en el
diccionario -- híbrido (confirmado explícitamente por Miguel Ángel):**
primero se comprueba contra la sublista española del diccionario
ampliado; si el candidato no aparece ahí, se cae al campo `country`
de MusicBrainz (`lookupArtist`) como respaldo, aun sabiendo que ese
campo falla a menudo (ver motivación, problema 1) -- mejor un dato
imperfecto que ninguno para los casos fuera del diccionario curado
(p.ej. el 10% de exploración o el 10% de disco).

**5. Cascada género/década, prioridad género > década (aplica igual
en modo español y en modo mixto):**
1. Mismo género + misma década exacta (nunca década posterior a la
   del ancla -- si el ancla es Pink Floyd años 70, rock sinfónico de
   los 70 antes que rock sinfónico de cualquier década posterior).
2. Se agota el género en TODAS las décadas -> se abandona el género,
   se mantiene la década, cualquier género.
3. Se agota también la década -> fallback final (ver punto 7).

**6. Reparto de cupo por cada pista nueva que añade Radio -- 80/10/10
base:**
- **80%** -- artista+canción del diccionario de éxitos ampliado
  (respetando década+origen).
- **10%** -- "exploración" vía MusicBrainz: mismo criterio de
  género/década/origen que el 80%, pero SIN exigir que esté en el
  diccionario ("no conocido" se permite; el origen NO se relaja
  nunca, confirmado explícitamente).
- **10%** -- de la biblioteca local (lo ya descargado en el
  dispositivo). Ver punto 8 para cómo se resuelve género/década/país
  de un artista de disco (no hay ese dato guardado localmente, se
  consulta a MusicBrainz bajo demanda). Misma cascada género->década,
  con origen respetado igual que los otros dos cupos.
- **Ratio configurable en Ajustes (80/10/10 como cualquier otro
  reparto, p.ej. 20/30/50) -- APLAZADO explícitamente por Miguel
  Ángel a una sesión futura, fuera del alcance de S013/S014. No
  construir en la sesión que retome este anexo salvo que él lo pida
  de forma explícita entonces.**

**7. Fallback final -- ya NO es directamente clásica.** Orden completo
cuando se agotan género Y década dentro de un cupo:
1. **Español agotado (modo todo-español):** si el 10% de disco ya no
   tiene NINGÚN candidato español que cumpla ni siquiera "cualquier
   género, cualquier década" (ver punto 8, último peldaño), ese 10%
   se retira sin más y el reparto pasa a **90/10** (conocido +
   exploración, sin disco) -- no se rellena ese hueco con nada, se
   reduce el número de fuentes activas.
2. Si TODO lo español se agota (conocido + exploración + disco, en
   ningún género/década quedan candidatos españoles en absoluto --
   caso límite, poco probable): antes de caer a clásica, se permite
   UNA vez un tema conocido pero **extranjero** del diccionario
   (rompe la regla de origen solo en este último peldaño de
   necesidad, confirmado explícitamente por Miguel Ángel: "pasaríamos
   a cualquier tema ya conocido, pero extranjero, antes que caer a
   clásica").
3. Solo si ni siquiera eso da resultado, fallback final a género fijo
   "classical" sin país (el mecanismo que ya existe en
   `resolveAnchorWithFallbacks()`/`FALLBACK_GENRE` para cuando ni
   siquiera se puede fijar un ancla -- se reutiliza aquí como red de
   seguridad de última instancia, "non-stop": la Radio nunca debe
   pararse del todo salvo que de verdad no haya nada, en ningún sitio,
   que ofrecer).
- El 10% de disco, específicamente, tiene su PROPIO último peldaño
  antes de tocar el fallback general de arriba: si no hay ningún tema
  en disco que cumpla género o década (respetando origen), se coge
  **cualquier tema del disco** que cumpla el origen -- nunca cae
  directa a clásica ni al extranjero-conocido del punto 7.2, esos dos
  son solo para cuando de verdad no queda NADA español en ningún
  sitio (ni conocido, ni exploración, ni disco).

**8. Fuente de disco -- diseño técnico (biblioteca local sin
género/década/país guardados, confirmado leyendo `LibraryReconciler.kt`
real: la biblioteca se deriva en caliente de la estructura de
carpetas `{artista}/{álbum}/{archivo}`, no hay entidad Room de pista
con esos campos):**
1. Listar artistas ya descargados (misma fuente que usa
   `LibraryViewModel` para pintar la Biblioteca), excluyendo los ya
   usados en esta sesión de Radio.
2. Para cada candidato, en orden aleatorio: `lookupArtist(inc=genres)`
   de MusicBrainz (mismo endpoint que ya usa `resolveAnchor()`) ->
   género + país. Cachear el resultado por artista dentro de la
   sesión de Radio (un `Map` en memoria, se resetea junto con
   `radioAnchor`), para no repetir la consulta si el mismo artista de
   disco vuelve a salir candidato.
3. Aceptar si género coincide con el ancla y (si el modo es
   todo-español) el país es ES. Si no hay ninguno así, relajar a
   década+origen sin exigir género (mismo principio de la cascada
   general). Si tampoco, coger cualquier pista de disco que cumpla
   origen (ver punto 7, último peldaño específico del 10% de disco).
4. Cuando se acepta un candidato de disco, se reproduce la pista tal
   cual ya está en el dispositivo (no hace falta buscar en YouTube ni
   resolver stream -- ya es local), igual que cualquier otra
   reproducción desde Biblioteca.

### Explícitamente fuera de alcance de la sesión que construya esto
### (S014 o la que Miguel Ángel decida)

- Porcentajes del cupo configurables en Ajustes (punto 6) -- apuntado
  como mejora futura, no construir salvo petición explícita.
- Sincronización de favoritos/ajustes entre dispositivos vía Drive
  (bug real reportado por Miguel Ángel en S013: divergencia de
  favoritos entre teléfono y tablet) -- esto es H07, no H08, y queda
  explícitamente para otra sesión con su propio PCH. No mezclar con
  esta hoja de ruta.

---

## COMPLETADAS EN S014

Construido el diseño completo cerrado en S013 (arriba, sección "S013:
diseño cerrado del rediseño completo de Radio"). Trabajo real:

- **`known_hit_artists.json` reescrito por completo**: de lista plana
  `{década: [artistas]}` a `{década: {es: [...], intl: [...]}}`, cada
  entrada ahora `{artist, song}` -- canción concreta real, no solo el
  nombre del artista (caso guía de Miguel Ángel: Yes en los 80 ->
  "Owner of a Lonely Heart"). Reutiliza los ~210 artistas ya
  existentes, separación estricta "de España" (no "de habla hispana"
  -- Alejandro Fernández, Chayanne, Ricky Martin, Shakira van en
  `intl`).
- **`KnownHitsRepository` reescrito**: `lookupHit()`, `randomHit()`
  (cupo del 80%, exclusión de ya-usados), `isKnownSpanishArtist()`
  (filtro barato de origen).
- **`RadioAnchor` gana `isSpanishOrigin: Boolean`**, fijado UNA VEZ en
  `RadioRepository.resolveAnchor()` (diccionario + respaldo
  `country=ES` de MusicBrainz), absoluto para el resto de la sesión de
  Radio -- nunca se relaja salvo en el fallback final de "classical".
- **`RadioRepository.suggestRelatedArtist()` (cupo de exploración,
  10%)**: mantiene el origen fijo en toda la cascada, que ahora sigue
  el orden género+década exacta -> género cualquier década -> década
  exacta cualquier género (antes: género+país+década -> género+década
  -> género+país -> género).
- **`RadioRepository.lookupArtistProfile()` nuevo**: perfil completo
  (géneros/país/década) de un artista bajo demanda, para el cupo de
  disco.
- **`PlayerManager`, cupo 80/10/10 completo** (disco -> exploración ->
  diccionario, con degradación a 90/10 si el 10% de disco se queda sin
  candidatos españoles y cascada de fallback final: español agotado ->
  un tema extranjero conocido -> género fijo "classical"). Inyecta
  `SearchResultTrackRepository` como fuente de artistas ya descargados
  para el 10% de disco.
- **Verificado**: el workflow de GitHub Actions compiló en verde
  (commit `a776d8d`) tras el fix de firma entre `KnownHitsRepository`
  y `PlayerManager` (el primer commit intermedio, `2f55652`, falló en
  compilación por ese desajuste -- reconciliado en el mismo bloque de
  trabajo).

**Sin construir en S014 (aplazado explícitamente, ver arriba):**
porcentajes del cupo configurables en Ajustes.

**Sin verificar en dispositivo real todavía** -- pendiente que Miguel
Ángel pruebe: sesión de Radio arrancada con un grupo español (caso
guía: Alaska y Dinarama, S011), sesión arrancada con un grupo
extranjero (modo mixto), y que el 10% de disco funcione si tiene algo
descargado en el dispositivo de prueba.

## S016 -- fix real: década no respetada en el cupo de diccionario

Reportado por Miguel Ángel con `radio_relacionados_debug.txt` real
(sesión de ~4 horas, ancla española década 1980): tras los primeros
~15 aciertos (todo el sublistado español de esa década en
`known_hit_artists.json` -- deliberadamente no exhaustivo), el cupo de
diccionario empezó a devolver artistas de décadas completamente
distintas (Rosalía, C. Tangana, Aitana, Ana Mena -- todos 2010s/2020s)
mientras la sesión seguía anclada a 1980.

**Causa raíz confirmada leyendo el código real** (no solo el log):
`PlayerManager.pickDictCandidate()` tenía un segundo intento que, al
agotarse el pool específico de la década del ancla, caía
silenciosamente a `KnownHitsRepository.randomHit(decadeBegin = null,
...)` -- CUALQUIER década -- sin ninguna marca distinta en el log
frente a un acierto normal de década. Ese segundo intento estaba
pensado originalmente para el caso legítimo de "ancla sin década
conocida" (`anchor.decadeBegin == null`, sin `life-span.begin` en
MusicBrainz), pero como esa entrada nula ya se cubre en la PRIMERA
llamada (pasando `anchor.decadeBegin` directamente), el segundo
intento solo se disparaba de verdad en el caso real: década SÍ
conocida, pool agotado -- justo el escenario que rompía la promesa de
ancla.

**Corregido:** eliminado el segundo intento. Si el pool
década+origen del diccionario está agotado, `pickDictCandidate()`
devuelve `null` y el resto de la cascada ya existente (S013 punto 7)
toma el relevo respetando SIEMPRE la década: primero
`resolveFinalFallback()` intenta un "extranjero conocido" de la MISMA
década (el sublistado `intl` de esa década, sin tocar todavía),
después, si eso también se agota, cae al fallback final de género fijo
"classical" (sin década ni país, mecanismo ya existente). Log nuevo
añadido cuando el diccionario agota su pool de década, para que quede
explícito en `radio_relacionados_debug.txt` en vez de inferirse por
ausencia.

**Consecuencia esperada, no un fallo nuevo:** en sesiones largas con
una década muy poblada de exclusiones, la Radio recurrirá antes a
exploración/disco (ambos ya respetan década vía MusicBrainz) y al
"extranjero conocido" de la misma década -- reduce el ritmo al que se
agota el diccionario español puro, pero nunca deja que la década
derive sola, que era la queja real de Miguel Ángel.

**Sin verificar en dispositivo real todavía** -- pendiente que Miguel
Ángel confirme con una sesión larga que, tras agotar el diccionario
español de la década, la Radio pasa a extranjero-conocido-misma-década
y finalmente a clásica, sin mostrar nunca año/década ajena en el cupo
de diccionario.

## S016, segundo bloque -- orden explícita: fuera "classical" del todo, y diccionario ampliado

Miguel Ángel corrigió dos cosas de raíz, muy explícitamente, tras el
primer fix de década de esta misma sesión:

**1. "Classical" queda eliminado por completo, en TODO el flujo, no
solo en el cupo de diccionario.** Había DOS sitios que caían a género
fijo "classical" (`FALLBACK_GENRE`, ya eliminado del código):
- `resolveFinalFallback()` (cupo agotado en una vuelta): ahora, tras
  el extranjero-conocido-misma-década (punto 7.2, sin tocar), el
  último recurso es `pickDiscoCandidate()` -- la MISMA biblioteca
  local del 10% de disco, con su propia relajación interna
  género->década->origen puro (origen siempre respetado). Si ni
  siquiera eso tiene nada, la función devuelve `null` y la Radio
  simplemente no añade nada esa vuelta -- nunca música sin relación.
- `resolveAnchorWithFallbacks()` (no se pudo identificar NADA del
  artista que arrancó la sesión -- ni canal, ni artista de H05, ni
  título parseable): antes fijaba un ancla dura a "classical" sin
  país. Ahora deriva el ancla de disco (`resolveAnchorFromDisco()`,
  nueva función): recorre la biblioteca local al azar y usa el
  primer artista cuyo perfil MusicBrainz tenga género -- ese perfil
  (género/país/década) se convierte en el ancla de toda la sesión. Si
  la biblioteca tampoco tiene nada resoluble, la Radio no arranca esa
  vez, en vez de arrancar con un género arbitrario.

**2. Diccionario ampliado.** La lista `es` de cada década tenía
~15 artistas (motivo real del fix de década de este mismo bloque de
trabajo: se agotaba en minutos de sesión). Ampliada con entradas
reales verificables (conocimiento propio, mismo criterio de
compilación que el resto del diccionario, sin inventar nada):

| Década | `es` antes | `es` ahora |
|---|---|---|
| 1960 | 11 | 17 |
| 1970 | 10 | 18 |
| 1980 | 15 | 23 |
| 1990 | 9 | 17 |
| 2000 | 12 | 19 |
| 2010 | 13 | 17 |
| 2020 | 6 | 10 |

De paso, corregido un error de clasificación de origen que ya existía:
**Quevedo** (canario, España) estaba en la lista `intl` de los 2020 --
movido a `es`.

**Pendiente, dicho explícitamente por Miguel Ángel:** esto sigue sin
llegar a ~100 por década. Ampliar más allá de esta pasada requiere
más tiempo de curación real (verificar canción+década concreta uno a
uno) para no repetir el mismo tipo de error que motivó el primer fix
de este bloque -- se deja como trabajo pendiente explícito, no
resuelto por decisión unilateral de recortar el alcance.

**Sin verificar en dispositivo real todavía** -- ninguno de los dos
puntos de este bloque se ha probado en dispositivo real.

## S016, tercer bloque -- corrección de Miguel Ángel: género+década nunca fue "opcional", disco también debe cumplirlo, e historial entre sesiones

Miguel Ángel corrigió una interpretación mía del bloque anterior: **no
dijo nunca que la década se relajara sin más** -- la intención siempre
fue aguantar el máximo tiempo posible poniendo música de la semilla
(mismo género+década), y por eso el diccionario tiene que ser lo más
extenso posible: con ~15-25 entradas por década se agota en poco más
de una hora (10 diccionario + ~1-2 explora/disco ≈ 12-13 pistas a 3
min ≈ 40 min hasta el primer cambio real de década/género, y la sesión
completa se sale del todo sobre la hora y media). Cuanto más grande el
diccionario, más tiempo se tarda en agotar género+década antes de
degradar, y esa degradación gradual es la que hace que la Radio "vaya
enganchada" al gusto real de quien la puso.

**Tres correcciones de este bloque:**

1. **El 10% de disco no respetaba género+década, solo origen.**
   `pickDiscoCandidate()` tenía un último peldaño ("cualquier pista
   que cumpla origen") que ignoraba género Y década -- en una sesión
   de flamenco rock español podía meter un tema de Pink Floyd. Nunca
   fue una decisión de Miguel Ángel, es evidente que no debía pasar.
   Corregido: `pickDiscoCandidate()` sigue ahora la MISMA cascada
   simétrica género/década que el diccionario (género+década exacta ->
   se agota género, se mantiene década -> se agota también eso, se
   mantiene género -> `null`), con origen siempre fijo.

2. **Diccionario ampliado por tercera vez en esta sesión** (aún sin
   llegar a 100/década, pendiente explícito):

   | Década | Antes | Ahora |
   |---|---|---|
   | 1960 | 20 | 22 |
   | 1970 | 22 | 22 |
   | 1980 | 25 | 28 |
   | 1990 | 20 | 23 |
   | 2000 | 23 | 27 |
   | 2010 | 21 | 24 |
   | 2020 | 12 | 13 |

3. **Historial entre sesiones (petición nueva, explícita de Miguel
   Ángel: "que las listas no sean siempre igual"):** nuevo
   `RadioSessionHistoryManager` (SharedPreferences, hasta 400
   artistas, rotatorio) que persiste cada artista que Radio acepta de
   verdad, ENTRE sesiones (no solo dentro de una, que ya cubría
   `radioUsedArtists`). Se usa como preferencia SUAVE en las tres
   cascadas (diccionario/exploración/disco): en cada paso, si evitar
   los artistas recientes deja el paso sin candidatos, se ignora esa
   preferencia para ese paso y se elige igual -- nunca bloquea que
   Radio encuentre algo por evitar repetición.

**Sin verificar en dispositivo real todavía** -- ninguno de los tres
puntos de este bloque se ha probado en dispositivo real. Pendiente
explícito: seguir ampliando el diccionario en próximas sesiones, es
"lo más importante" según palabras textuales de Miguel Ángel.

---



1. **Verificación en dispositivo real** de todo lo listado en
   "COMPLETADAS EN S014" -- caso guía explícito: Alaska y Dinarama ya
   no debería derivar a reguetón (S011), y ahora además el resto de la
   sesión española debería mantenerse consistentemente en artista+
   canción conocidos de esa era, con la mezcla 80/10/10 visible en
   `radio_relacionados_debug.txt` a lo largo de una sesión larga
   (~15-20 pistas).
2. Si aparece algún bug real de dispositivo, diagnosticar leyendo el
   log real (`radio_relacionados_debug.txt`) antes de suponer causa --
   mismo criterio que siempre.
3. Solo si Miguel Ángel lo pide explícitamente: porcentajes del cupo
   80/10/10 configurables en Ajustes.

---

## S020 (2026-07-25) -- DIAGNÓSTICO SOBRE LOG REAL, PREPARACIÓN DE LA SESIÓN DE RADIO

**Sin código.** Miguel Ángel entregó dos logs reales
(`radio_relacionados_debug.txt`, 299 líneas, ~30 horas de uso; y
`radio_debug.txt`, de H09) con un veredicto textual: *"la radio está
funcionando realmente mal, mezclando décadas y géneros y orígenes
resultando en un poutpourri de temas sin sentido alguno"*. Esta
sección es el diagnóstico leído del código real y contrastado con el
log, para que la sesión siguiente arranque implementando en vez de
investigando.

**Lo primero, para no repetir el malentendido de S016:** esto NO es
"el fix de S016 no funcionó". El fix de S016 hace exactamente lo que
se diseñó. El problema es que lo diseñado tiene tres agujeros
estructurales que solo se ven con un log largo de verdad.

### Causa 1 (la gorda) -- el filtro de origen es ASIMÉTRICO

`PlayerManager.pickDictCandidate()`:

    val requireEs = anchor.isSpanishOrigin && !allowForeignFallback

y `KnownHitsRepository.pool()`:

    val raw = if (requireEs) d.es else d.es + d.intl

Con ancla **española** el pool se restringe al bloque `es`. Con ancla
**extranjera** (`isSpanishOrigin = false`) `requireEs` es `false` y el
pool es `es + intl`: **el bloque español entero sigue dentro**. No hay
ningún camino de código que diga "ancla extranjera -> solo `intl`".

Recuento real del diccionario (`known_hit_artists.json`, medido en
esta sesión): década 1980 tiene **28 entradas `es` frente a 19
`intl`**. Es decir, con ancla Pixies (rock, US, 1980) **el 60% del
pool es música española**. El log lo confirma sin margen de duda --
sesión anclada en Pixies, `origen_es=false`, y salen Loquillo, Leño,
Kiko Veneno, Barricada, Gabinete Caligari, Barón Rojo, Parálisis
Permanente, Radio Futura, Burning, Golpes Bajos, Danza Invisible,
Nacha Pop, Alaska, Dúo Dinámico, Isabel Pantoja, Rocío Jurado,
Alejandro Sanz, Víctor Manuel, Ana Belén...

Totales del diccionario: 159 `es` / 130 `intl` = 289 entradas. Con
ancla extranjera, más de la mitad de lo que puede sonar es español.

### Causa 2 -- el segundo peldaño de la cascada tira el género entero

`KnownHitsRepository.randomHit()`, peldaño 2:

    if (decadeBegin != null) { pick(pool(decadeBegin, requireEs))?.let { return it } }

Sin filtro de género. En cuanto el peldaño 1 (género + década) se
queda sin candidatos no usados, se cae aquí y entra CUALQUIER género
de esa década. Y se queda sin candidatos enseguida: el diccionario
tiene solo **12 géneros distintos** y está aplastantemente sesgado --
158 entradas `pop` de 289 (55%), 70 `rock`, y luego colas de 17 o
menos. Una sesión anclada en `rock`/1980 agota su puñado de entradas
rock en pocas vueltas y a partir de ahí es, de facto, una sesión de
pop de los 80.

Evidencia directa en el log, todas con `ancla=género:'rock'/década:1980`:
Rocío Jurado (copla), Isabel Pantoja (copla), Mecano, Michael Jackson,
Whitney Houston, Wham!, Duran Duran, Culture Club, a-ha, Depeche Mode,
Cyndi Lauper, Phil Collins (todas `género='pop'`). Y con
`ancla=género:'electronica'/década:1970`: Rocío Jurado (copla), José
Luis Perales (pop), Massiel (pop).

### Causa 3 -- el ancla se fija mal de origen

Tres fallos distintos, los tres visibles en el log:

1. **Género de ancla elegido al azar.** `RadioRepository.resolveAnchor()`:
   `val chosenGenre = genres.random()`. De todos los géneros que
   MusicBrainz da para el artista se coge uno a cara o cruz, y ese
   decide las siguientes horas de escucha.
2. **El ancla se busca por el nombre del CANAL de YouTube.** En el log
   aparecen anclas `'知心音樂網'`, `'Havalina Chiel'`,
   `'Natacha Atlas Official'`. MusicBrainz no encuentra nada (son
   nombres de canal, no de artista) y se cae a
   `resolveAnchorFromDisco()`.
3. **La caída a disco produce anclas sin ninguna relación.** Caso
   real: escuchando **Natacha Atlas** el ancla acabó siendo **Jethro
   Tull** (`art rock`, GB, 1960), derivada de la biblioteca local. A
   partir de ahí, toda la sesión gira sobre un artista que Miguel
   Ángel no estaba escuchando.

### Causa 4 -- el cupo de exploración (10%) devuelve cosas que no son música

`suggestRelatedArtist()` busca en MusicBrainz por `tag:"<género>"` y
se queda con lo que salga, sin comprobar después que el vídeo de
YouTube resuelto sea realmente música de ese artista. Dos casos reales
del log:

- `género='art rock'` -> **'Art & Language'** (colectivo de arte
  conceptual) -> vídeo añadido a la cola: *"Art & Language --
  Conceptual Art, Mirrors and Selfies | TateShots"*.
- `género='rock'` -> **'Гражданская оборона'** -> vídeo añadido:
  un vídeo de **noticias sobre la guerra en Rusia**, ni siquiera
  música.

### Causa 5 -- décadas mal puestas dentro del propio diccionario

Independiente de la lógica: hay entradas colocadas en la década
equivocada, así que aunque el filtro de década funcione, el dato es
falso. Ejemplos reales servidos como década 1980: **Måneskin**
(*Beggin'*, 2021), **Blur** (*Song 2*, 1997), **Ska-P** (1990s),
**Love of Lesbian** (2000s), **The Animals** (*House of the Rising
Sun*, 1964).

### Causa 6 -- el diccionario sigue muy por debajo del objetivo

289 entradas totales / 7 décadas ≈ 41 por década, frente a las ~100
por década que Miguel Ángel fijó como objetivo. Sigue siendo, en sus
palabras, "lo más importante": mientras el pool sea pequeño, cualquier
cascada se agota y degrada. Recuento actual por década y origen:

| Década | `es` | `intl` |
|---|---|---|
| 1960 | 22 | 20 |
| 1970 | 22 | 19 |
| 1980 | 28 | 19 |
| 1990 | 23 | 19 |
| 2000 | 27 | 14 |
| 2010 | 24 | 16 |
| 2020 | 13 | 23 |

### Anexo -- H09 (Radios Online) también trae fallo real

`radio_debug.txt` (mismo lote de logs) muestra que Radio-Browser.info
está devolviendo **HTTP 503** en `searchStations()` y `getCountries()`
y **SocketTimeoutException** en `searchByAnyTag()` para los términos
`'80s'` y `'1980s'`. No es un fallo de la lógica de MiMoo: es el
servidor. Pero MiMoo ataca hoy un único host y no reintenta contra
otro mirror ni degrada con un mensaje claro al usuario. No es H08;
queda anotado aquí para que la sesión de Radio decida si lo toca de
paso o lo deja para H09.

### ESPECIFICACIÓN CERRADA DE LA RADIO (S020, dictada por Miguel Ángel)

**Esta especificación es la ley. Sustituye a la hoja de ruta que yo
había propuesto en esta misma sección, y corrige dos puntos de ella
que estaban mal planteados.** Transcrita punto por punto de su
mensaje, con la traducción técnica al lado.

#### 1. Anclaje

- **"La primera canción fija la radio."** Género, década y origen se
  derivan del PRIMER tema y no se vuelven a tocar.
- **"El género queda fijado para toda la sesión."**
- **"La década se fija para toda la sesión."**
- **Origen: separación dura en los DOS sentidos.** Regla final:
  *"mejor origen separando España/extranjera"*. Ancla española -> solo
  artistas españoles. Ancla no española -> solo artistas NO españoles.
  No existe el "modo mixto".

> **Historial de esta regla dentro de la propia S020, para que ninguna
> sesión futura la revierta por error.** Miguel Ángel dictó primero
> *"si es española de origen se fija en España. Si no, no hay origen
> fijado"* -- es decir, sin restricción con ancla extranjera, que era
> justo lo que el código ya hacía. Minutos después lo reconsideró y
> cerró la regla en la separación dura de arriba. **Manda la segunda.**
> Esto valida la causa 1 del diagnóstico: con ancla Pixies
> (rock/US/1980) el 60% del pool disponible era música española, y eso
> sí era un fallo. HECHO en esta misma sesión.

#### 2. Regla suprema

**"Lo principal es el género, el género no se abandona nunca."**
**"Siempre se respeta género y década, siempre."** Ninguna degradación,
en ningún cupo, en ningún fallback, puede servir un tema fuera del
género y la década del ancla. HECHO en el commit `15119db` de esta
sesión para los dos cupos que lo incumplían.

#### 3. Repetición: la unidad es la CANCIÓN, no el artista

**"Si hay que repetir artista se repite. Mientras, no se repite
canción hasta que no quede más remedio."**

Cambio estructural respecto a lo construido: hoy `radioUsedArtists` +
`excludeNames` excluyen **artistas** ya usados de forma dura, y eso es
justo lo que fuerza las degradaciones que Miguel Ángel quiere eliminar.
La exclusión dura debe pasar a ser por **tema** (artista+canción, o
`youtubeId`); el artista pasa a preferencia **suave** -- se prefiere no
repetirlo, pero repetirlo es siempre mejor que degradar el género.

#### 4. La escalera de degradación -- "lo de `hasta agotar` es un error"

Palabras textuales: *"no se agota, lo único que se agota es la lista
de temas conocidos, pero podemos seguir poniendo temas de artistas
conocidos aunque no se conozcan los temas."*

Orden exacto, respetando SIEMPRE género + década + (origen si
español):

1. **Artista conocido + tema conocido** -- entrada del diccionario que
   cumple género y década, con su canción concreta. Es el estado
   normal.
2. **Artista conocido + tema NO catalogado** -- cuando se acaban los
   temas del diccionario, se siguen sirviendo los MISMOS artistas
   conocidos buscando en YouTube otras canciones suyas, no la del
   diccionario. Técnicamente: `resolveYoutubeCandidate(artista,
   songTitle = null)` sobre artistas del diccionario ya usados.
3. **Artistas desconocidos** -- *"si se agotan los artistas pq no
   debemos repetir temas, se siguen poniendo de artistas
   desconocidos"*. Es el actual cupo de exploración (MusicBrainz por
   `tag:género` + rango de década), pero deja de ser un cupo del 10%
   para ser también el peldaño final de la escalera.
4. **Nunca** se sale de género + década. Si de verdad no hay nada, la
   Radio se para -- nunca rellena con música sin relación.

Consecuencia: **`resolveFinalFallback()` desaparece tal como está
hoy.** Su peldaño 7.2 (con ancla española, permitir una vez un
conocido extranjero) contradice frontalmente "si es española se fija
en España" y se elimina.

#### 5. Las TRES porciones y el reparto dinámico

Resumen final dictado por Miguel Ángel, que sustituye a cualquier
descripción anterior de los cupos en este anexo.

**El primer tema fija origen, género y década.** Las tres porciones
trabajan siempre dentro de ese ancla, sin excepción.

| Porción | Qué sirve, en orden |
|---|---|
| **Conocidos** | 1º temas conocidos; 2º artistas conocidos con temas NO catalogados |
| **Disco** | 1º artistas (de la biblioteca local); 2º más temas de esos artistas |
| **Desconocidos** | artistas sin éxito catalogado, vía MusicBrainz |

**Agotamiento y reparto.** Una porción puede agotarse. *"Cuando se
agotan, su porción se reparte entre las que queden."* El reparto es a
partes iguales entre las supervivientes:

    efectivo(i) = base(i) + (suma de las bases agotadas) / (nº de porciones vivas)

Ejemplo textual de Miguel Ángel, partiendo del 80/10/10 de Ajustes:
si Conocidos (80) se agota, *"dejamos un 40 por cien para cada una de
las otras dos porciones"* -> Disco 10+40 = 50, Desconocidos 10+40 = 50.
Suma 100.

Encadenado: *"como tenemos 3, cuando falla una se reparte entre las
otras dos, y cuando falle otra ya solo nos queda una"* -> la
superviviente pasa al 100%.

**Cuándo se considera agotada cada porción:**

- **Conocidos:** cuando no quedan temas del diccionario para el género
  + década + origen del ancla **Y ADEMÁS** ya no quedan artistas
  conocidos con temas no catalogados que servir. Los dos peldaños
  deben fallar, no solo el primero.
- **Disco:** cuando la biblioteca local no tiene NADA que cumpla el
  ancla. *"Si se pone reggae y no hay reggae en el disco, no se ponen
  temas del disco; si se pone rock de los setenta y no hay rock de los
  setenta en el disco, no se pone disco."* No degrada jamás: o cumple
  el ancla, o esa porción se reparte.
- **Desconocidos:** en la práctica no se agota. *"Es prácticamente
  imposible agotar el último baremo aunque no repitamos temas."*

**Cuando se agotan las tres:** *"ya es lo que venga respetando género y
década y origen"*. Es el único desenlace en el que se deja de
distinguir procedencia -- pero el ancla se sigue respetando entera,
como siempre.

**Consecuencia sobre lo construido.** `radioDiscoExhausted` ya existe
pero (a) solo se activa con ancla española y (b) no reparte nada: el
porcentaje liberado se pierde. Hay que generalizarlo a las tres
porciones y hacer que `dueForQuota()` trabaje con porcentajes
EFECTIVOS recalculados según qué porciones siguen vivas, no con los
porcentajes base de Ajustes.

#### 6. Construido en S020 sobre la especificación

Todo lo anterior queda implementado en esta sesión, en cuatro commits:

1. `15119db` -- el género no se abandona en ningún cupo.
2. `a9cb2b1` + `8b39266` -- origen separado en los dos sentidos
   (`KnownHitsRepository.Origin`), fuera el peldaño español ->
   extranjero, fuera `findCandidatesAnyGenre`.
3. `0ac5b58` -- las tres porciones con sus dos peldaños, reparto
   dinámico (`effectiveQuotaPercent()`), no-repetición por CANCIÓN
   (`radioUsedSongs`) y desenlace terminal.
4. Ancla determinista y validación de candidatos (este bloque):
   - `MusicBrainzGenre` gana `count` (votos de la comunidad). El
     género del ancla deja de ser `genres.random()` y pasa a ser el
     más votado, con desempate alfabético -- el mismo artista da
     SIEMPRE el mismo ancla.
   - `resolveAnchorWithFallbacks()` invierte el orden: primero el
     artista estructurado de la pista, después el nombre del canal de
     YouTube. Era el origen de anclas como `'知心音樂網'` o
     `'Natacha Atlas Official'`, y del caso peor: una sesión de
     Natacha Atlas anclada en Jethro Tull.
   - `matchesArtist()` valida que el vídeo resuelto sea del artista
     pedido (nombre normalizado presente en título o canal) antes de
     encolarlo. Cierra la puerta al vídeo de la Tate y al de noticias
     de guerra. Importa más con el reparto dinámico, porque la porción
     de desconocidos hereda porcentaje.

**Pendiente de verificación en dispositivo real con una sesión larga.**

### COMPLETADAS EN S021

Sesión dedicada a la hoja de ruta que Miguel Ángel fijó al cierre de
S020 (*"comenzamos la siguiente sesión dejando el diccionario
correcto. Y aumentando la muestra si es posible."*). Se cumplió
entera, pero el primer hallazgo obligó a corregir antes el diagnóstico
del que partía.

#### 1. El diagnóstico de S020 sobre las décadas era incorrecto

S020 atribuyó los saltos de década ("Måneskin servido como los 80") a
entradas mal fechadas del diccionario. La auditoría entrada por entrada
de S021 comprobó que **no era eso**: Måneskin estaba en 2020, Blur en
1990, Ska-P en 1990 y The Animals en 1960 -- todas bien colocadas. Solo
Love of Lesbian estaba realmente floja (tema de 2009 en el bloque de
2010). Además `known_hit_artists.json` **no se tocó en S020** (su
último commit era `12b399e`, del 19 de julio), así que el log que
delató el fallo se produjo contra ese mismo archivo correcto.

La década no venía mal del dato: **la soltaba el código**.

#### 2. Las cuatro fugas de década (`f592b40`)

En S020 se eliminaron los peldaños que soltaban el GÉNERO, pero los
simétricos, los que soltaban la DÉCADA, se quedaron dentro. Eso
incumplía la mitad de la regla suprema cerrada en S020: *"siempre se
respeta género y década, siempre"*.

1. `KnownHitsRepository.randomHit()` -- peldaño 2 `pool(null, origin)`,
   comentado como "género, cualquier década".
2. `KnownHitsRepository.knownArtists()` -- el mismo patrón en la rama
   `anyDecade`.
3. `RadioRepository.suggestRelatedArtist()` -- segunda vuelta con
   `findCandidates(..., decadeBegin = null, ...)`.
4. `PlayerManager.pickDiscoCandidate()` -- `?: pickPreferred { genreOk(it) }`,
   sin `decadeOk`. **Esta cuarta no estaba en el análisis inicial**;
   apareció al revisar la porción de disco.

Las cuatro pasan a vuelta ÚNICA: género Y década del ancla, o la
porción se declara agotada y su porcentaje se reparte entre las vivas
(mecanismo de S020, `effectiveQuotaPercent()`, sin cambios). El caso
"ancla sin década" sigue cubierto sin peldaño extra, porque
`pool()`, `findCandidates()` y `decadeOk()` ya lo resuelven cada uno
por su cuenta cuando `decadeBegin` es null.

Con un diccionario de ~40 entradas por década estas fugas se
disparaban casi de inmediato: en cuanto el pool exacto se quedaba sin
candidatos, la sesión pasaba a servir cualquier década.

#### 3. Auditoría del diccionario (`2a2e62d`)

Errores reales encontrados y corregidos:

- **3 duplicados exactos de tema.** Importan más de lo que parece
  desde S020: la no-repetición es por `songKey()` = `artista|canción`,
  así que dos entradas del mismo tema con el nombre del artista escrito
  distinto contaban como temas distintos y el mismo tema podía sonar
  dos veces en una sesión. Eran `Eagles`/`The Eagles` (Hotel
  California), `Wham!`/`Wham` (Wake Me Up Before You Go-Go) y
  Alejandro Sanz -- Corazón Partío, presente a la vez en 1990 y 2000.
- **6 reubicaciones de década** (Ana Belén *Contamíname* 1993,
  Seguridad Social *Chiquilla* 1991, Víctor Manuel *Solo Pienso en Ti*
  1978, Sergio Dalma *Bailar Pegados* 1991, Enrique Iglesias
  *Bailamos* 1999, Alejandro Sanz *No Es Lo Mismo* 2003).
- **3 temas sustituidos** por otro del mismo artista realmente de esa
  década, para no vaciar bloques ya escasos (Rosalía, Love of Lesbian,
  Estopa).
- **4 géneros mal etiquetados** y **3 nombres de artista** ("La
  Pantoja" -> "Isabel Pantoja", unificación de "Dúo Dinámico", y
  sustitución de un tema de 1989 editado bajo el alias "Alejandro
  Magno", irresoluble en YouTube).

#### 4. Ampliación del diccionario (`afd5934`)

De 286 a 750 entradas. Con las fugas cerradas, el motor ya no tiene
ninguna válvula de escape, así que el tamaño y el reparto del
diccionario pasan a ser lo único que sostiene una sesión larga.

| Década | `es` | `intl` | Total | (antes) |
|---|---|---|---|---|
| 1960 | 38 | 59 | 97 | 42 |
| 1970 | 48 | 56 | 104 | 41 |
| 1980 | 52 | 61 | 113 | 43 |
| 1990 | 52 | 63 | 115 | 46 |
| 2000 | 51 | 65 | 116 | 39 |
| 2010 | 50 | 60 | 110 | 39 |
| 2020 | 35 | 61 | 96 | 36 |

Los dos bloques `intl` más flacos eran 2000 (14) y 2010 (16), justo
los que con la separación dura de origen tienen que sostener por sí
solos cualquier sesión anclada en un artista no español. Pasan a 65 y
60. De 12 géneros a 27, con `pop` cayendo del 55% al 33%. Géneros que
sencillamente no existían y que ahora tienen pool propio: punk (19),
indie rock (32), heavy metal (10), new wave (9), synth-pop (6),
electronic (36, antes 3), disco (6), funk (4), jazz (4), r&b (7), ska,
blues, country, bachata, bolero, regional mexicano.

Esto importa directamente desde S020: con el género inabandonable,
anclar en algo que no fuese pop o rock agotaba el peldaño 1 casi de
inmediato.

#### 5. Cinco entradas falsas verificadas en línea (`f5b0f38`)

Las cinco que la auditoría había dejado marcadas como "no
verificables" eran **todas inventadas**. Ninguna habría resuelto jamás
en YouTube, así que cada una quemaba su peldaño sin devolver nada.

- Dover, *En El Río* -> *Serenade* (1997). Dover cantaba en inglés en
  los 90.
- Danza Invisible, *Sevilla Sur* -> *Sabor de Amor* (1988).
- Objetivo Birmania, *Verano Africano* -> *Desidia* (1984), y género
  `pop` -> `synth-pop`: eran dance-pop/synth-funk, no pop genérico.
- Tam Tam Go!, *Cerca del Ecuador* -> *Manuel Raquel*, que es de
  *Spanish Shuffle* (1988) y por tanto de los 80. Eso obligó a corregir
  además la entrada de los 90 creada en `afd5934` con ese mismo tema,
  que pasa a *Espaldas Mojadas* (1990).
- Micky, *El Koala* -> Micky y Los Tonys, *La Luna y el Toro* (1964).
  En los 60 grababa con ese nombre; su carrera en solitario es de los
  70, así que se añadió además *El Chico de la Armónica* (1971) en la
  década que le corresponde.

También apareció por el camino que Triana figuraba con *Todo Es de
Color*, que es de Lole y Manuel (1975). Triana pasa a *Abre la Puerta*
y Lole y Manuel entra con el tema que sí es suyo.

**751 entradas, sin duplicados, sin ninguna entrada dudosa pendiente.**

#### 6. Estado real de H08 al cerrar S021

La lógica quedó completa en S020 y sin fugas en S021; los datos
quedaron correctos y ampliados en S021. **Nada de esto está verificado
en dispositivo.** Todo compila en verde; nada se ha escuchado.

Riesgo a vigilar en la primera escucha, heredado de S020:
`matchesArtist()` puede rechazar vídeos legítimos si el artista no
aparece ni en el título ni en el canal.

Géneros con pool mínimo tras la ampliación: blues, country y bolero
tienen una sola entrada; ska, bachata y regional mexicano dos. Anclar
ahí agota la porción de conocidos casi de inmediato y reparte su cupo.
Es comportamiento correcto por diseño, pero conviene saberlo antes de
interpretar un log.

### HOJA DE RUTA PARA LA SIGUIENTE SESIÓN QUE RETOME H08

## COMPLETADAS EN S022

La sesión arrancó en H14 y derivó aquí al reportar Miguel Ángel tres
fallos con música española: una radio de La Frontera que se fue entera
a música extranjera, una de Fangoria que sirvió doce temas seguidos de
Fangoria, y una de Alaska y Dinarama que acabó poniendo el LP completo
de Quentin Gas y Los Zíngaros.

**Causa raíz de los tres, sobre `radio_relacionados_debug.txt`:** los
errores transitorios de MusicBrainz (503, timeout, 429) se trataban
como respuestas negativas definitivas. `lookupArtistProfile()`,
`findCandidates()` y `resolveAnchor()` devuelven `null`/vacío tanto
cuando NO HAY candidatos como cuando NO SE HA PODIDO PREGUNTAR, y como
cada negativa agotaba una porción de forma irreversible, una racha de
mala red condenaba la sesión entera. El caso de Alaska es el más
claro: un HTTP 503 descartó al artista real, el fallback probó con el
nombre del canal ('Chapuzasmix') y la sesión acabó anclada en un
artista arbitrario de la biblioteca local.

Falla más con lo español porque los géneros de nicho (`electropop`/ES,
`flamenco`/ES) dependen casi por completo de MusicBrainz, mientras que
`hard rock`/GB tiene colchón de sobra en el diccionario.

Implementado:

- **La porción de disco se agota** al quedarse sin artistas NUEVOS y
  cede su cuota a diccionario y exploración. Antes "seguía viva
  sacando más temas de los ya usados", que es exactamente cómo se
  producían los doce Fangorias.
- **Ningún artista dos veces en 10 canciones** (`RADIO_ARTIST_WINDOW`).
  Reincidir dentro de la ventana agota a ese artista para el resto de
  la sesión. Criterio literal de Miguel Ángel: a veinte canciones de
  distancia no molesta, dos veces en diez sí.
  `radioUsedArtists` no servía -- es un `Set` sin orden, sabe SI sonó
  pero no HACE CUÁNTO -- de ahí `radioRecentArtists`.
- **Ningún tema repetido jamás.** `radioUsedSongs` ya se alimentaba
  pero no se consultaba como filtro.
- **Modo degradado** ante caída de MusicBrainz: `RadioRepository`
  distingue fallo transitorio de respuesta legítima
  (`isServiceDegraded`, umbral 4 fallos SEGUIDOS). La porción no se
  agota por un fallo de red, y no se deriva ancla de la biblioteca
  local por un 503.
- **Excepción deliberada de emergencia:** SOLO en modo degradado, el
  diccionario suelta el género y conserva origen y década. Va contra
  la regla de S020 a sabiendas. Decisión explícita de Miguel Ángel
  ("habrá que soltarlo", y al revisarlo: "es una situación de
  emergencia que hemos solventado bien"). **No es una grieta en la
  regla del ancla: es una excepción acotada a que MusicBrainz esté
  caído.** No eliminar sin hablarlo con él.
- **Pertenencia por INTERSECCIÓN de géneros reales.** MusicBrainz da
  siete géneros de Dead Can Dance y el código se quedaba con uno,
  tirando seis, y luego había que reconstruir a mano con familias
  escritas a ojo lo que se acababa de descartar. Así apareció Pet Shop
  Boys en una radio de Dead Can Dance -- agrupados por usar
  sintetizadores. `RadioAnchor.genres` conserva el conjunto y
  `sharesGenreWith()` decide por intersección.
- **`GENRE_FAMILIES` reescritas estrechas y por ESCENA**, no por
  instrumento. `dark wave` con gothic/ethereal/cold wave y NO con
  synth-pop/house/techno. `reggaeton` separado de salsa/bachata/cumbia
  y `regional mexicano` en la suya -- la primera versión cometía
  literalmente el error que Miguel Ángel puso como ejemplo ("Bob
  Marley y Bad Bunny porque reggae y reguetón suenan parecido").
- **Diccionario a 777 entradas** (`es/1960` 38→54, `es/2020` 35→45).
  Nota importante: la premisa con la que se abrió este trabajo era
  falsa. El diccionario NO estaba en 22-28 por década, sino en 96-116;
  esa cifra venía de sesiones antiguas y se dio por buena sin medirla.
  Medido, el problema nunca fue el volumen sino la etiqueta.

---

## S023 -- INCIDENCIA ABIERTA: `resolveAnchor()` fija el ancla equivocada

Descubierta al revisar juntas las 658 resoluciones de artista del
rastreo de S023. **No la introdujo el rastreo: estaba viva en la app y
el rastreo la saco a la luz.**

`RadioRepository.resolveAnchor()` busca `artist:"NOMBRE"` con `limit=5`
y se queda con el **primer** resultado, sin comprobar que el nombre
devuelto se parezca al buscado. Con nombres largos acierta casi
siempre. Con nombres cortos o ambiguos, no:

| Se pide | MusicBrainz devuelve | Ancla que queda fijada |
|---|---|---|
| Pink | Pink Floyd | progressive rock, psychedelic rock |
| Los Ángeles | Los Angeles Philharmonic | classical |
| Burning | Burning Spear | reggae, roots reggae |
| Bebe | Bebe Rexha | pop, dance-pop |
| Deluxe | Samy Deluxe | hip hop |
| Second | A Split-Second | ebm, minimal synth |

Hoy, arrancar una radio desde Pink construye una cadena de Pink Floyd.
Y como el ancla fija genero Y pais desde el primer tema y no se vuelve
a derivar, **el error contamina la cadena entera**, no un tema suelto.

Ojo con la relacion con `classical`: en S016 se ordeno sacar `classical`
del todo. Por esta via vuelve a entrar, porque no llega como genero de
un tema sino como genero de un ancla mal resuelta.

**Estado.** El lado del diccionario ya esta cubierto:
`app/src/main/assets/artist_disambiguation.json` lista los diez casos
y el rastreador los salta, de modo que ninguna entrada recibe generos
de otro artista. **El lado de la app NO esta cubierto**: la app todavia
no lee ese archivo y `resolveAnchor()` sigue como estaba.

**Pendiente.** Que `resolveAnchor()` consulte `artist_disambiguation.json`
antes de aceptar el primer resultado, y que descarte los resultados
cuyo nombre no case con el buscado una vez normalizada la tipografia.
No se metio en el bloque de S023 sin consultarlo con Miguel Angel,
porque toca el motor y no el dato.

---

## COMPLETADAS EN S023

Sesión dedicada a una sola idea, planteada por Miguel Ángel en el
primer mensaje: **el género no puede decidirse comparando nombres ni
consultando sacos escritos por el modelo.** De ahí salió todo lo
demás.

### El punto de partida, corregido

La sospecha inicial era que el cruce usaba expresiones regulares. No
era así: `GENRE_FAMILIES` comparaba pertenencia exacta a un saco. Pero
el efecto era peor, porque los sacos los había escrito el modelo. En
la línea 192 convivían `new wave` y `post-punk`, y por esa arista
Tears for Fears entraba en una radio de Dead Can Dance. El problema no
era el algoritmo: era que el dato era opinión.

### La regla, cerrada por Miguel Ángel

Su analogía la fija entera: **un oso hormiguero y un oso polar
comparten ancestro —mamífero— y no son parientes.** Compartir un
antepasado lejano no significa nada; lo que importa es la posición en
el árbol. De ahí:

- Desde el ancla se **desciende**; nunca se sube al padre, porque la
  carpeta padre contiene todo lo demás.
- Los **hermanos** valen solo como último peldaño ("a última hora
  dices, es comible").
- Las aristas de **influencia no se recorren jamás**.

Y su segunda aportación, igual de decisiva: **la década la marca el
tema, no el artista.** Yes se formó en 1968, pero entre "Roundabout"
(1971) y "Owner of a Lonely Heart" (1983) hay doce años y dos grupos
distintos.

### Datos construidos

- **`genre_tree.json`** — 2176 géneros de MusicBrainz con su
  parentesco real, 0 fichas fallidas. Las relaciones género-género NO
  salen por `/ws/2` (la documentación oficial excluye explícitamente
  los géneros), así que hubo que rastrear las fichas HTML una a una,
  a una petición por segundo, en un workflow de disparo manual.
  Guarda los dos tipos de arista —parentesco e influencia— a
  propósito, para poder revisar el criterio sin volver a rastrear.
- **`known_hit_artists.json`** — 621 de 777 entradas (79,9%) con su
  conjunto real de géneros, media de 6,2 por entrada. De 258 entradas
  etiquetadas `pop` solo 6 siguen siéndolo a secas; de 165 `rock`,
  solo 13.
- **`artist_disambiguation.json`** — diez artistas que la búsqueda
  automática resuelve mal; cinco con MBID confirmado por Miguel Ángel
  sobre candidatos con evidencia.

### Cinco fallos del motor, todos vivos desde antes de esta sesión

| Fallo | Cómo se descubrió |
|---|---|
| `resolveAnchor()` aceptaba el primer resultado sin comprobar el nombre | Pink → Pink Floyd, Los Ángeles → Los Angeles Philharmonic, Burning → Burning Spear |
| La década salía del `life-span` del artista | P!nk, nacida en 1979, anclaba una radio en los 70 |
| El límite de búsqueda de 5 dejaba fuera al artista real | De 'Kanye West' salían una banda tributo y una colaboración |
| El cupo DISCO se agotaba por fallos de red | Diez 503 seguidos leídos como "la biblioteca no tiene nada" |
| El cruce de géneros usaba sacos del modelo | Creed y Café Tacvba entraban compartiendo solo `rock` |

Los dos primeros llevaban ahí desde que existe la Radio. Se escondían
porque con grupos el año de formación daba un número plausible y con
nombres largos la búsqueda acertaba.

### Verificado en dispositivo

Radio de P!nk, tres estados sucesivos:

1. Antes: Cat Stevens, Lynyrd Skynyrd, ELO, Supertramp — los 70.
2. Tras fechar por tema: Coldplay, Café Tacvba, Christina Aguilera,
   Creed — los 2000, pero con Café Tacvba y Creed colados por `rock`.
3. Tras el árbol: The Killers, Keane, Kings Of Leon, Kaiser Chiefs.

### Incidencias propias de la sesión, resueltas

- Un workflow salió **en verde sin producir nada**: `git diff` no ve
  archivos nuevos sin seguimiento. Costó una hora de rastreo. Desde
  entonces el resultado se sube como artefacto ANTES de commitear.
- Un segundo rastreo perdió el commit final porque `main` había
  avanzado durante la hora que duró. Resuelto con `pull --rebase`
  antes del push, y recuperado sin volver a rastrear gracias al
  artefacto.
- El descarte por nombre se pasó de estricto y rechazó a **M-Clan**:
  `normalize()` borra la puntuación en vez de sustituirla por espacio.
- El filtro de especificidad faltaba en la **intersección**, no solo
  en el descenso. Apareció al simular el cruce sobre los datos reales
  antes de compilar.

### Decisiones abiertas de S022, resueltas sin criterio del modelo

- **Tears for Fears** entraba por una arista de influencia. Fuera.
- **New Order** entra, pero por intersección directa en `post-punk`,
  que MusicBrainz le atribuye de verdad — no por la vía casual de
  `electronic` que motivó la duda.

---

## S023 -- INCIDENCIA ABIERTA: pantalla en blanco al navegar

Reportada por Miguel Ángel al final de la sesión, con captura. **No
guarda relación con el trabajo de S023** — no se tocó interfaz.

Al entrar en Ajustes, el hueco del NavGraph queda vacío: sin cabecera
y sin contenido. La barra del reproductor sigue intacta debajo, con el
mismo tamaño que cuando funciona.

Lo que la evidencia descarta: no es la pantalla de Ajustes
(`SettingsScreen` pinta siempre su `Scaffold` con la barra "Ajustes",
no tiene rama que dibuje vacío), no es el reparto de alturas (el hueco
conserva su tamaño), y no es la navegación (el destino sigue ahí).

**Se recupera solo** abriendo el menú lateral, lo que apunta a que una
recomposición forzada lo resuelve. Hipótesis no confirmada: el ciclo
de vida de la entrada del `NavHost` se queda por debajo del nivel al
que Navigation-Compose dibuja el destino.

Miguel Ángel decidió no perseguirlo ("déjalo, se recupera, no hay
problema"). Encaja en H13, que es el hito de UX. Primer paso propuesto
si se retoma: instrumentar qué ruta cree activa la raíz y si la
pantalla llegó a componerse.

---

---

## S023 (continuación) -- REAPERTURA TRAS EL PRIMER CIERRE

La sesión se cerró y se reabrió al probar en dispositivo. Cinco
commits más, todos sobre el mismo hilo: **de dónde sale el artista que
ancla una sesión de Radio.**

### El fallo del canal de YouTube

Miguel Ángel puso "Radio Futura - Divina" y la Radio devolvió The
Strokes, R.E.M., The Smiths y Travis. El ancla se había fijado en
**Kurt Cobain**, que es el nombre del CANAL que subió el vídeo.

El respaldo de S010 no lo cazaba porque se diseñó para canales que NO
son un artista ("OldGuitar8", sin resultados en MusicBrainz): solo
salta cuando el canal FALLA. 'Kurt Cobain' es un artista real, resuelve
perfectamente, y el título -- donde estaba el dato bueno -- no llegaba
a probarse nunca.

**La lección es de fuente, no de validación.** `pickAnchorArtist()` no
podía ayudar: se buscó Kurt Cobain y se encontró Kurt Cobain. El
problema no era un mal emparejamiento, era preguntar por la fuente
equivocada. En palabras de Miguel Ángel: *"no podemos buscar por el
nombre de quien lo sube"*.

La cascada pasa a ordenarse por fiabilidad de la FUENTE: artista
estructurado → título → canal.

### La hambruna del diccionario

Con el ancla ya correcta (Radio Futura, ES, 1980), la porción del
diccionario se agotó en dos décimas. MusicBrainz le atribuye UN género:
`rock`, con 129 descendientes, y el filtro de especificidad lo
rechazaba. Ninguna de las 777 entradas podía encajar.

El umbral se había medido contra anclas de cuatro a siete géneros
(P!nk, Dead Can Dance, Led Zeppelin), todas con alguna carpeta concreta.
Nunca contra un ancla cuyo conjunto ENTERO fuese una raíz. Cuarto
peldaño añadido: si el ancla no tiene ningún género concreto, lo ancho
cuenta.

### La búsqueda por palabras, idea de Miguel Ángel

Un vídeo titulado `Led Zeppelin Immigrant song`, subido por
`oldschoolrockerkid`, no se pudo identificar: `parseArtistFromTitle()`
solo parte por `" - "`. La sesión se ancló en un artista SORTEADO AL
AZAR de la biblioteca local (Chumbawamba, `alternative dance`), de las
61 entradas internacionales de los 80 solo encajaban 6, y la Radio las
repitió en bucle.

**Incidencia de método, anotada a propósito.** El modelo afirmó que ese
tema "no tenía artista identificable por ninguna vía". Era falso: el
artista y la canción estaban los dos en el título. Lo cierto era que el
parser no supo leerlos. Miguel Ángel lo señaló: afirmar que el dato no
está, en vez de decir que no se ha encontrado, cierra la puerta a la
solución. La regla: **"no he encontrado coincidencia" nunca es "no
existe"**.

Su propuesta, implementada: si el título no viene partido, se parte por
palabras y se pregunta, probando prefijos **del más largo al más
corto** -- porque 'Led' también existe como artista y buscando al revés
se perdería 'Led Zeppelin'. Verificado en dispositivo:

    identifyFromTitleWords('Led Zeppelin Immigrant song')
      -> artista='Led Zeppelin', canción='Immigrant song' (2 prefijos)
    ancla fijada: hard rock, GB, 1970

### Medición del diccionario, que corrige lo que creíamos

Las cifras que arrastraba este anexo (1960:22, 1970:22, 1980:28...)
estaban OBSOLETAS. El diccionario ya supera el objetivo de ~100 por
década:

    década   es  intl  total        década   es  intl  total
    1960     54    59    113        2000     51    65    116
    1970     48    56    104        2010     50    60    110
    1980     52    61    113        2020     45    61    106
    1990     52    63    115        TOTAL   352   425    777

**El volumen no es el problema.** Lo es el pool que queda tras cruzar
década + origen + género. Simulando cada una de las 777 entradas como
ancla, contra su propio pool:

    pool            mediana de candidatos   entradas con <5
    español                    6 - 11            14-19 por década
    internacional             21 - 40             0-5 por década

Y la causa está aislada:

    entradas SIN conjunto de géneros    media de géneros
    español    151 de 352  (43%)              2,7
    intl         5 de 425  ( 1%)              7,8

**MusicBrainz apenas cataloga a los artistas españoles.** De las 151
españolas sin conjunto, 81 llevan `pop` o `rock` a secas -- las dos
carpetas raíz. Por eso una radio anclada en un español se queda sin
candidatos: no faltan entradas, falta el DATO DE GÉNERO justo donde más
se necesita. Añadir cien entradas españolas más sin resolver esto solo
añadiría más "pop".

---

### COMPLETADAS EN S024

Sesión larga, con dos mitades muy distintas. La primera fue arreglar
fallos del motor uno a uno, según iban apareciendo en los logs de
dispositivo. La segunda empezó cuando Miguel Ángel paró esa dinámica:

> *"Esta forma de probar, caso no contemplado, implementar caso,
> probar, caso no contemplado, implementar caso, conlleva a una
> implementación eterna."*

Tenía razón, y medirlo lo confirmó: el problema de fondo no era que
faltaran casos en el filtro, sino que el pool era tan pequeño que
**cualquier** exigencia del filtro lo dejaba seco. Ese cambio de
enfoque es lo que cerró el objetivo del hito.

**El objetivo de S023 se cumple por primera vez.** Recorrido de la
mediana de candidatos del bloque español a lo largo de la sesión:

    partida                                 7    (18% con <5 candidatos)
    relleno con Discogs                    10    (11%)
    anclas que solo tenían carpetas raíz   12     (9%)
    ampliación con listas de Los 40        44     (5%)

    objetivo fijado al cerrar S023:  mediana ~15, menos del 5%

#### 1. Búsqueda por palabras del NOMBRE del artista

Regla dictada por Miguel Ángel al ver fallar la 9ª de Beethoven:
*"las búsquedas no se deben ni de invertir, ni de esto ni de lo otro
— se debe buscar por palabras"*.

La búsqueda por palabras existía desde S023 pero solo se aplicaba al
TÍTULO del tema. Sobre `Beethoven, Ludwig van` —formato de catálogo
que trae la etiqueta del archivo y que MusicBrainz no conoce— se
probaron cinco prefijos del título y ni una sola vez las palabras del
nombre. Ese nombre se buscó doce veces en un solo log y volvió vacío
las doce.

`findAnchorArtistMbid()` aplica ahora la misma mecánica al nombre, y
`pickAnchorArtist()` acepta por igualdad de CONJUNTO de palabras. No
se invierte nada ni se interpretan comas: el formato `Apellido,
Nombre` se resuelve como efecto lateral de la regla general. No
reabre lo que cerró S023 porque exige igualdad y no inclusión —
`Los Ángeles` no casa con `Los Angeles Philharmonic`, `Pink` no casa
con `Pink Floyd`.

#### 2. La década nula abría las siete décadas

`pool()` hacía `if (decadeBegin != null) byDecade[decadeBegin] else
byDecade.values`: no tener década significaba TODAS. Se vio entero en
un log — la copla *"Ay, pena, penita, pena"* de Carlos Cano no se pudo
fechar (MusicBrainz no tiene fecha para esa actuación de TV), el ancla
quedó con década nula, y la radio de una copla de 1999 sirvió David
Bisbal, La Oreja de Van Gogh, **Aitana** y Dvicio.

`randomHit()` y `knownArtists()` cortan antes de llegar a `pool()`.
No toca la regla de S023: la década la sigue marcando el TEMA. Lo que
cambia es que no tener dato pase de significar "todo vale" a "de aquí
no sale nada".

#### 3. La Radio ya no se ancla en un artista sorteado

Con la 9ª de Beethoven la cascada falló entera y
`resolveAnchorFromDisco()` recorrió la biblioteca local **en orden
aleatorio**, anclando en el primero que resolviera perfil. Salió The
Offspring, y la Radio de una sinfonía sirvió INXS, The Smiths, Depeche
Mode, Def Leppard y The Cure.

El propio código declaraba el principio correcto dos guardas más
arriba (*"antes no arrancar Radio que anclarla en un artista
arbitrario"*), pero esa guarda solo saltaba ante fallo de red. Función
retirada entera.

#### 4. Repertorio clásico

Miguel Ángel rechazó que "no arrancar" fuera un resultado aceptable:
*"que con el mejor compositor de todos los tiempos la radio no
arranque es un fallo más que garrafal"*. Y tenía razón — el log
demostraba que el anclaje ya funcionaba y que lo que fallaba era
dejar sonar lo que la propia app encontraba.

- **Tope de duración propio, 45 minutos.** `RADIO_MAX_TRACK_SECONDS`
  eran 15, y el tema del ancla duraba 18:34: el ancla no pasaba su
  propio filtro. Se decide contra `genre_tree.json`
  (`RadioAnchor.isClassical`), no contra una lista escrita a mano.
- **El país deja de filtrar POR COMPLETO.** Orden de Miguel Ángel,
  precisada: *"no se trata de permitir españoles, se trata de permitir
  cualquiera — alemanes, franceses, ingleses, italianos, españoles,
  rusos, checos, y de cualquier país del mundo"*. Ni `country:ES` ni
  `NOT country:ES`, en los tres sitios donde el país filtraba.
  `Origin.ANY` existía en el enum desde S020 sin que ningún camino de
  la Radio lo usara; éste es el primero.
- **El filtro de compilación no se aplica en clásica.** Con el tope ya
  en 45 minutos seguían pasando 0 de 6 resultados: no era la duración
  sino la lista de nombres. Buscar `Richard Strauss` devuelve sobre
  todo *Best of*, *Complete Works* y *Full Concert*, que en clásica es
  cómo se publica el repertorio.

#### 5. Filtro por nombre de lo que no es una canción

Idea de Miguel Ángel: *"full album o greatest hits podemos descartarlos
por nombre igual que interview, chap, capítulo, álbum completo,
grandes éxitos, película completa, entrevista"*. La lista pasa de 6 a
37 términos, con el castellano que faltaba por completo.

**Cambió también el criterio de comparación, y era necesario:** el
filtro hacía `title.contains(hint)` a pelo, y con la lista ampliada
eso habría descartado a Tracy CHAPman por `chap` y a CAPitol Records
por `cap`. Ahora se compara por palabra completa sobre el título
plegado de acentos. Deliberadamente fuera: `mix` a secas, porque
"Original Mix" y "Extended Mix" son temas legítimos.

Partida en dos listas, porque en clásica solo aplica la segunda:
`COMPILATION_TITLE_HINTS` (recopilaciones) y `NOT_MUSIC_TITLE_HINTS`
(entrevistas, capítulos, documentales, audiolibros, películas).

#### 6. Cuatro estrangulamientos del pool, todos por exceso de celo

Aparecieron uno a uno en los logs, y en conjunto explican por qué la
Radio se agotaba pese a tener material:

1. **La exploración se rendía al primer fallo.** `fetchFromUnknown()`
   pedía UN artista, intentaba UNA resolución en YouTube, y si fallaba
   daba la porción entera por agotada — con diez candidatos
   encontrados. Ahora prueba hasta cuatro. El motivo que escribía en
   el log era además falso: decía que MusicBrainz no daba artistas
   cuando sí los daba.
2. **El offset aleatorio se pasaba del final.** `findCandidates()`
   pedía con `offset` aleatorio entre 0 y 90 (S010, para dar variedad)
   y sobre un conjunto de ~15 artistas un offset de 90 devuelve vacío.
   El código lo leía como "eslabón roto". Ahora reintenta desde el
   principio.
3. **La porción DESCONOCIDOS se agotaba.** Contradice el diseño
   dictado por Miguel Ángel y recogido literal en este mismo anexo:
   *"Desconocidos: en la práctica no se agota. Es prácticamente
   imposible agotar el último baremo aunque no repitamos temas."*
   Disco y Conocidos sí pueden agotarse — son finitos. MusicBrainz no
   lo es.
4. **La ventana de diez vetaba para toda la sesión.** Al detectar que
   un artista repetiría dentro de la ventana lo metía en lista negra
   permanente, convirtiendo una ventana deslizante en una lista que
   solo crecía. Miguel Ángel lo precisó: *"de cada diez canciones no
   se puede repetir el artista; cuando pasen las diez, se puede volver
   a poner una del mismo artista"*. `radioBlockedArtists` retirada.

#### 7. Sondeo de fuentes de género — Discogs elegido

Miguel Ángel pidió sondear cuál de las fuentes candidatas daba mejor
resultado. Se hizo desde GitHub Actions (idea suya, porque la red del
entorno del modelo va por lista blanca y Wikidata, Discogs y
MusicBrainz están todas bloqueadas), con
`tools/probe_genre_sources.py`.

    fuente      cobertura   riqueza   CONCRETOS
    discogs      102/125       4.1     101/125
    wikipedia     65/125       2.6      49/125
    wikidata      71/125       1.7      45/125

**La cifra que decidió fue "concretos"** — artistas que reciben una
etiqueta que el árbol reconoce Y que no es carpeta raíz. Wikidata
cubría 71 pero casi todo con `pop music` y `rock music`, que caen en
las raíces y dejan la entrada igual que estaba.

El sondeo necesitó **cuatro pasadas** por errores del modelo, todos
detectados revisando el resultado antes de escribir en el diccionario
—que no se tocó hasta tenerlo limpio— y todos anotados en el código:

- No se filtraba el papel del artista, y entraban recopilatorios de
  varios intérpretes: Taburete recibía *merengue, guaguancó, calypso*
  y Antonio Molina *techno, makina, euro house*.
- Homónimos de otra época: Fórmula V con *funk, disco, electro*.
  Resuelto con contraste de época contra la década de la entrada.
- El contraste de país se hizo sobre el listado de discos, donde ese
  campo NO existe — sólo está en el detalle. Fue un no-op completo:
  cuarta pasada con cifras idénticas a la tercera.
- Un colador que contrastaba los estilos contra el `genre` grueso de
  la entrada apartaba 28 artistas y la mayoría eran BUENOS, porque
  `pop rock` no cuelga de `pop` en el árbol de MusicBrainz. Retirado.

Quedaron **cinco homónimos** que ningún filtro automático cazó, porque
son contemporáneos: Chanel (house), Leiva (techno), Natos y Waor (tech
house), Saiko (makina) y Los Pecos (cumbia). Miguel Ángel pidió que
los resolviera el modelo. Se hizo **sin violar la restricción de
S023**: ninguna etiqueta sale de la opinión del modelo, todas de una
fuente consultada y citada en `tools/spanish_genres_manual.json`, con
el motivo por el que Discogs fallaba en cada uno.

#### 8. Las anclas que solo tenían carpetas raíz

Clase que se escapó al primer relleno. El criterio de objetivo era "no
tiene `genres`", y se quedaba corto: **Radio Futura SÍ tenía conjunto,
pero era `['rock']`** — una etiqueta, y carpeta raíz de 129
descendientes. Con eso `matchesGenre()` cae al último peldaño y solo
acepta entradas que lleven literalmente `rock`: diez artistas de las
51 del bloque ES de los ochenta. Su radio se agotaba y entraba en
bucle.

Lo que descalifica a una entrada como ancla no es no tener géneros, es
no tener ninguno CONCRETO. Medido: 31 sin conjunto + 41 con solo
raíces = **72 de 352 inservibles como ancla**. Ambas herramientas usan
ahora ese criterio, y el relleno FUSIONA en vez de sustituir.

#### 9. Ampliación del diccionario con las listas de Los 40

La palanca que cerró el objetivo, en tres fases con herramienta y
workflow propios:

| fase | herramienta | resultado |
|---|---|---|
| 1 · cosecha | `harvest_los40_charts.py` | 2121 canciones, 946 artistas, 1966–2025 |
| 2 · enriquecer | `enrich_chart_artists.py` | país y género: 378 vía MusicBrainz, 154 vía Discogs, 332 ya en el diccionario |
| 3 · fusión | `merge_charts_into_dictionary.py` | 675 canciones españolas añadidas |

Fuente: `Anexo:Los números uno de Los 40 Principales (España) {año}`
de Wikipedia. La lista cambió de nombre a `LOS40` por el camino, lo
que perdió nueve años en la primera pasada; se prueban ahora cinco
formas del título por año.

**Solo españolas, decisión de Miguel Ángel** — el bloque internacional
ya iba holgado con mediana 26 y meter ahí 1338 canciones más sería
trabajo sin retorno. El tamaño del asset se le consultó y lo descartó
como problema: son 277 KB.

    bloque ES   352 -> 1027 entradas
    total       777 -> 1452

Criterios de admisión: artista español (por MusicBrainz o por estar ya
en el bloque `es`), al menos un género CONCRETO, y sin duplicar. La
década sale del AÑO en que la canción fue número uno — regla de S023
aplicada, y aquí sale gratis porque es justo lo que da la lista.

#### 10. El ancla se enriquece con el diccionario

Descubierto al ver que las 675 canciones nuevas no se estaban
aprovechando: `resolveAnchor()` construye el ancla desde
**MusicBrainz**, no desde el diccionario. Y MusicBrainz, de Radio
Futura, solo da `rock`. Así que la entrada quedó enriquecida a
`[rock, pop rock, new wave, alternative rock, synth-pop]` y el ancla
siguió siendo `[rock]` a secas.

`KnownHitsRepository.genresOfArtist()` y unión de las dos fuentes.
**Ésta fue la pieza que hizo funcionar todo lo demás**: con el ancla
ancha, la sesión de prueba sirvió catorce temas de la movida
madrileña seguidos —Azul y Negro, Los Zombies, La Década Prodigiosa,
Los Elegantes, Ramoncín, Glutamato Ye-Yé, Tino Casal, Rey Lui,
Extremoduro, Los Nikis, Loco Mía, Olé Olé, The Refrescos, Orquesta
Mondragón— con **cero repeticiones, cero violaciones de la ventana de
diez y cero porciones agotadas**.

#### 11. El canal deja de ser fuente de ancla. Nunca más

Orden de Miguel Ángel, sin matices: *"el canal no puede ser objeto de
ancla nunca. ¿Qué vamos a anclar por canal? De hecho lo que estamos es
contaminando las anclas si metemos los nombres de los canales."*

S023 ya lo había bajado a último peldaño tras el caso "Radio Futura -
Divina" subido por un canal llamado *Kurt Cobain*. Bajarlo no bastó:
volvió a colarse en S024 en cuanto el peldaño bueno falló, y la radio
sirvió Lou Reed.

La cadena completa fue instructiva y quedó en el log:

    resolveAnchor('Radio Futura') -- EXCEPCIÓN: SocketTimeout
    resolveAnchor('Kurt Cobain')  -- ...
    ancla fijada: género='grunge', país=US, década=null
    fetchFromDisco(ancla='Kurt Cobain') -> 'Lou Reed'

**El peldaño bueno no falló por no resolver: falló por un timeout.** Y
la cascada lo trató igual que un "no existe". De ahí: ancla
grunge/US/sin década → el diccionario no sirve nada → KNOWN agotada al
segundo → su 80% se reparte a DISCO → Lou Reed, que en una radio de
grunge estadounidense es una elección impecable.

Dos correcciones: el canal sale de la cascada, y **un fallo transitorio
aborta el anclaje en vez de bajar de peldaño** — es la lección de S022
(*"un 503 no es 'este artista no tiene géneros', es 'ahora no'"*), que
no se estaba aplicando aquí pese a existir ya `lastFailureWasTransient`.

#### 12. Un peldaño mal colocado, y por qué importa

Al decir Miguel Ángel que "desconocidos" incluye temas y no solo
artistas, el modelo añadió a `fetchFromUnknown()` un peldaño que
buscaba en YouTube el nombre del artista a secas. Con `Los Locos` vino
**"Los locos, Presentación. Carnaval de Cádiz 2026"** en una radio
anclada en la movida madrileña.

El diseño documentado en este anexo ya coloca los temas no catalogados
en CONOCIDOS (peldaño 2), no en Desconocidos, y `fetchFromKnown()` ya
lo implementaba. Se retiró. Queda anotado porque el reproche de Miguel
Ángel era el correcto: *"¿de qué sirve tener todo lo que tenemos
documentado?"* — la respuesta estaba escrita y no se leyó.

#### Incidencia comprobada y desmentida

Miguel Ángel sospechó que las cuotas se habían pisado (*"ya nos
limpiamos el culo con las cuotas"*). Se comprobó con el recuento del
log: KNOWN=49, UNKNOWN=16, DISCO=1 — un 74/24/1 con Disco agotada y su
10% repartido a KNOWN=85% y UNKNOWN=15%. `effectiveQuotaPercent()`
reparte exactamente como él lo dictó, incluido el encadenado. Lo que
fallaba era el contenido de un cupo, no el reparto.

---

## Hoja de Ruta para la Siguiente Sesión que retome H08

El objetivo que abrió S023 y S024 —**que la Radio no se agote**— está
cumplido y medido: mediana española de 44 candidatos y 5% de entradas
con menos de 5, contra un objetivo de ~15 y menos del 5%. Lo que sigue
son cabos sueltos, ninguno bloqueante.

### 1. La única excepción viva a "no se repite nunca"

`resolveFinalFallback()` todavía puede repetir un tema cuando las tres
porciones fallan en la misma vuelta. Se dejó repitiendo **el más
antiguo** en vez de al azar —usando que `radioUsedSongs` es un
`LinkedHashSet` y ya conserva el orden—, pero repite.

En la última sesión de prueba no llegó a dispararse ni una vez. Aun
así, Miguel Ángel dijo que un tema no se repite NUNCA, y eso obliga a
decidir qué hace la Radio cuando de verdad no queda nada: **pararse o
repetir**. *Decisión pendiente de Miguel Ángel — no tomarla por él.*

### 2. Bloque internacional sin ampliar

Mediana 26 frente a los 44 del español. Las **1338 canciones no
españolas** de la cosecha están ya descargadas y enriquecidas en
`tools/chart_los40_raw.json` y `tools/chart_los40_enriched.json`: la
fusión sería ejecutar `merge_charts_into_dictionary.py` con el criterio
de país invertido. Miguel Ángel lo descartó para S024 por no aportar
donde ya sobra, pero el material está listo si cambia de idea.

### 3. Repertorio clásico sin volver a probar

Lo del país sin filtrar y el tope de 45 minutos entraron después de la
última prueba de clásica. Falta una escucha larga anclada en una obra
clásica para confirmar que no se agota.

### 4. 766 filas de Wikipedia sin interpretar

El parseo de `harvest_los40_charts.py` reporta 766 filas de las tablas
que no logra leer. No se sabe qué hay ahí sin mirarlo — podría ser un
formato de tabla que se escapa en algún tramo de años. Recuperarlas
ampliaría la cosecha, aunque el objetivo ya está cumplido sin ellas.

### 5. Pendientes menores heredados de S023

- La década del `life-span` en `lookupArtistProfile()` sigue
  derivándose del artista y no del tema. Importa menos desde que el
  anclaje por sorteo se retiró, pero afecta al cupo de disco.
- Comprobar si Kanye West y Pink resuelven MBID solos ahora que el
  límite de búsqueda está en 25.
- La etiqueta `ancla=` del log muestra a veces el nombre viejo en el
  primer candidato de cada vuelta. Cosmético, pero dificulta leer los
  logs.

---

## S025 (2026-07-29) — REDISEÑO DE LA CAPA DE ANCLAJE
## Diseño cerrado, dictado por Miguel Ángel. SIN CONSTRUIR.

### Por qué se abre este rediseño

Miguel Ángel, al final de S025, tras la séptima tanda de parches sobre
el ancla: *"Hemos tocado fondo. Peor ya no lo podemos hacer. No es que
lo estemos haciendo mal, es que no tenemos ni idea de cómo hacerlo. Lo
que no puede ser es que ponga Led Zeppelin y salga detrás Alaska.
Porque estamos fallando en décadas, porque estamos fallando en género y
porque estamos fallando en país."*

La evidencia que cierra la discusión, de su propio log
(`radio_relacionados_debug__7_.txt`, 05:33:56):

    resolveTrackDecade('Led Zeppelin' -- 'Black Dog')
      -> década 1980 (primera publicación 1983), de MusicBrainz

"Black Dog" es de *Led Zeppelin IV*, noviembre de 1971. El sistema la
fechó en 1983, ancló la sesión entera en los 80 y no dudó ni avisó.
Led Zeppelin se disolvió en 1980: el dato era imposible y nadie lo
comprobó.

Y en el mismo log, 18:16 y otra vez 18:31:

    resolveAnchor('Beethoven', mbid=a44b4408-...) -- sin géneros ni en
      MusicBrainz ni en el diccionario local -- no se puede fijar ancla
    topUpRadioQueueIfNeeded() -- parado del todo -- backlog final: 0

### Diagnóstico: cinco defectos de arquitectura, no cinco bugs

**1. Cada valor del ancla lo resuelve una sola vía, sin plan B.**
Género y país salen de MusicBrainz del artista; la década, de una
búsqueda de grabaciones. Si esa vía falla, el valor se queda nulo o
sale mal. El diccionario se añadió después como parche, y solo para el
género.

**2. Dos de los tres valores se preguntan al ARTISTA, no al TEMA.**
Por eso Led Zeppelin ancla en `hard rock` aunque suene una folk suya.
El año es del tema. El género es del tema. Solo el país es del artista.

**3. La década se pregunta a nivel de GRABACIÓN.** Una remasterización,
un directo o un recopilatorio son grabaciones tan válidas como la
original, y `searchRecordings` las devuelve mezcladas. De ahí 1983.
El nivel correcto es el *release-group* de la obra, o la ficha de
Discogs, que da el año de edición original.

**4. Un valor ausente se trata como catástrofe o se ignora.** Sin
género no hay ancla y la Radio no arranca. Sin década el filtro
desaparece sin más. Ninguna de las dos salidas es aceptable.

**5. Un valor equivocado no se distingue de uno correcto.** No hay
procedencia, ni confianza, ni comprobación de coherencia.

### Especificación cerrada

#### 1. El ancla son TRES valores, y siempre se responden

Orden de Miguel Ángel: *"cuando yo ponga El Loco de la Colina, me tiene
que decir de dónde es el tío, qué género toca, y de qué año es el tema
que está sonando."*

- **País** — del ARTISTA.
- **Género** — del TEMA, con los del artista como respaldo.
- **Año** — del TEMA. Nunca del artista, nunca de una grabación
  cualquiera: primera edición de la obra.

#### 2. Cascada de fuentes por valor, se para en la primera que conteste

|   | país | género | año del tema |
|---|------|--------|--------------|
| 1 | caché en tarjeta | caché en tarjeta | caché en tarjeta |
| 2 | diccionario semilla | diccionario semilla | diccionario semilla |
| 3 | MusicBrainz (artista) | MusicBrainz (tema, luego artista) | MusicBrainz por **release-group** |
| 4 | Discogs | Discogs (`styles`) | Discogs (año de edición original) |

Discogs entra porque su ficha da las tres cosas juntas y bien, que es
justo el hueco de MusicBrainz. Requiere token propio y respeta su
propio límite de peticiones.

Orden de Miguel Ángel sobre las fuentes: *"me da igual que sea
MusicBrainz, me da igual que sea Discogs, me da igual tener que tener
un diccionario con dos millones de entradas."* La cascada es un
detalle de implementación; el contrato es que los tres valores salgan.

#### 3. Todo lo resuelto se GUARDA, y se guarda EN LA TARJETA

Orden explícita de Miguel Ángel: *"si vamos a tener de forma dinámica
en el teléfono toda la información, esa información debe quedar
guardada en la carpeta donde se guarda todo. Normalmente se va a elegir
la tarjeta SD externa para grabar las descargas. Ahí mismo es donde
tenemos que grabar el diccionario, y todo lo referente al ancla."*

No en Room, no en `SharedPreferences`, no en el almacenamiento interno
de la app: bajo la raíz SAF que el usuario eligió, la misma donde van
las descargas. `RadioDebugLogger` y `NotificationDebugLogger` ya
escriben ahí, así que el mecanismo existe y está probado.

Consecuencias buscadas, y son las tres razones de la orden:

- **Sobrevive a la reinstalación.** Borrar datos de la app no borra lo
  aprendido.
- **Viaja con la tarjeta.** Si la tarjeta cambia de teléfono, el
  diccionario va con ella.
- **Entra solo en la sincronización de H07**, que ya sincroniza esa
  carpeta contra Google Drive. Lo que aprende el teléfono de Miguel
  Ángel lo hereda el de Silvia sin trabajo extra.

Estructura propuesta, bajo la raíz SAF:

    MiMoo/
      diccionario/
        artistas.json      artista -> { país, géneros[], activo_desde,
                                        activo_hasta, fuente, fecha }
        temas.json         artista|tema -> { año, géneros[], fuente, fecha }

JSON y no SQLite: legible, editable a mano por Miguel Ángel, y
sincronizable como un fichero cualquiera. Escritura por lotes, no una
por resolución, para no castigar la tarjeta.

El diccionario actual (1.682 entradas con país y género, más las 104
clásicas) deja de ser un techo y pasa a ser la **semilla** de este.
Nace lleno y crece solo con el uso.

#### 4. Tres reglas que hoy no existen

**Procedencia.** Cada valor lleva de dónde salió y cuándo. Va al log,
para que Miguel Ángel pueda juzgar el dato y no solo el resultado.

**Retirada en la misma sesión: no hay regla de coherencia.** Se llegó a
escribir una que contrastaba el año del tema con los años de actividad
del artista. Miguel Ángel la rechazó por inventada: *"yo en ningún
momento he dicho que haya que estar comparando los años de edición con
los años de disolución de las bandas"*. El año del tema es la fecha de
su primera edición y nada más; si una fuente no la da, se pasa a la
siguiente.

**"No lo sé" es distinto de "no hay".** Sin año se ancla por país y
género y se sigue; ni se para la Radio ni se abre el filtro. La Radio
solo deja de arrancar si faltan los tres.

#### 5. La búsqueda de relacionadas recibe el mismo trato

Hoy depende de MusicBrainz a pelo, y una caída de treinta segundos
mataba la porción para toda la sesión (corregido en S025, commit
0f86093, pero la dependencia sigue). Con el diccionario en tarjeta
creciendo solo, el orden se invierte: primero lo que ya está en la
tarjeta —instantáneo y sin red—, y la red solo para ensanchar.

Orden de Miguel Ángel: *"para buscar las relacionadas, si me falla
MusicBrainz, si me falla esto y lo otro, me suda la polla. Pongo un
diccionario y volvemos a lo mismo."*

### Criterio de aceptación

El que dio Miguel Ángel, literal: **poner Led Zeppelin y que no salga
Alaska detrás.** En concreto, sobre "Black Dog": país GB, género hard
rock, año 1971 — y una cadena británica de rock duro de los 70.

### Fuera de alcance de la sesión que construya esto

- Ampliar el diccionario semilla a mano. Deja de hacer falta: crece
  solo.
- Tocar el reparto de porciones (80/10/10) ni la escalera de
  degradación de S020.
- Tocar H09 (Radio Online).

---

### COMPLETADAS EN S025 (continuación — construcción sobre el diseño de más arriba)

Sesión larga y con muchas idas y vueltas; se documenta con honestidad,
incluidos los propios fallos del modelo, porque varios de ellos
volvieron a intervenir en el resultado final.

**País del ancla, clave de no-repetición, botón de descargar (primera
mitad de la sesión, antes del rediseño):**
- El nombre del canal de YouTube dejó de poder llegar al ancla por
  ninguna vía (semilla, fallback, porción de disco).
- `resolveAnchor()` consulta el diccionario de éxitos ANTES de
  abandonar el ancla por falta de géneros en MusicBrainz (caso
  Pistones).
- La clave de no-repetición de una canción (`songKey()`) pasó de
  comparar cadenas crudas a comparar título+artista normalizados
  (`songTitleKey()`), resolviendo repeticiones reales del log ("Lobo
  Hombre en París", "Todo a pulmón").
- El origen del ancla pasó de ser un booleano `isSpanishOrigin` a ser
  el país real (`RadioAnchor.country`), aplicado también en la porción
  de disco.
- Botón de descargar del reproductor: no escribía nada en Room cuando
  la pista no tenía fila previa (Radio, MusicBrainz). Arreglado con
  `markQueuedEnsuringRow()`.
- Diccionario de éxitos ampliado con campo `country` en las 1.682
  entradas y bloque nuevo de repertorio clásico (104 obras, 55
  compositores). Corregido un fallo de coherencia de géneros
  introducido en la propia ampliación.
- Un fallo de red ya no agota permanentemente la porción de
  exploración ni la de disco (`lastFailureWasTransient`).

**Rediseño de la capa de anclaje (según el diseño cerrado más arriba):**
- El ancla se resuelve en el orden dictado por Miguel Ángel: artista →
  ¿compositor o intérprete de clásica? (ancla solo por género, sin
  origen ni década) → si no, origen y género del artista → década de
  la EDICIÓN ORIGINAL del tema (nunca del artista, nunca de una
  grabación cualquiera).
- **Incidencia real de proceso:** en un punto intermedio de la sesión
  se introdujo una regla de "coherencia" que descartaba fechas
  posteriores a la disolución del artista. Miguel Ángel no la pidió en
  ningún momento — fue una decisión unilateral del modelo, señalada
  por él como tal y retirada por completo (código, comentarios y
  campo `end` del DTO de MusicBrainz) en el mismo turno en que se
  detectó. Queda anotado como ejemplo de lo que NO debe volver a
  ocurrir: no ampliar el alcance de una orden sin que la orden lo diga.
- `AnchorDictionary`: diccionario del ancla persistido en la tarjeta
  SD elegida por el usuario (`MiMoo/diccionario/`), no en Room ni en
  almacenamiento interno — orden explícita de Miguel Ángel, mismo
  sitio que las descargas. Cuatro ficheros: artistas, temas, y dos
  colas de pendientes (temas sin año, artistas sin resolver).
- Semilla inicial de 1.161 artistas (país + géneros), escrita a mano
  por el modelo a partir del diccionario de éxitos, el repertorio
  clásico (compositores **e intérpretes** — orden explícita de Miguel
  Ángel tras señalar que directores, orquestas y solistas también
  cuentan) y una tanda de grandes artistas por categoría (jazz,
  flamenco, copla, rock australiano/neozelandés, huecos del rock
  anglosajón). **Sin verificar contra ninguna fuente externa** — puede
  contener errores de país o género.
- Cascada de la fecha de primera edición: tarjeta → diccionario de
  éxitos → MusicBrainz por release-group → MusicBrainz por grabación →
  Discogs → Wikidata (SPARQL, propiedad P577). Discogs requiere el
  secreto de repositorio `DISCOGS_TOKEN`, ya existente y cableado vía
  `BuildConfig`. Wikidata no necesita credenciales. Ninguna de las dos
  fuentes nuevas se ha podido probar contra la red real durante la
  sesión.
- Cajón de "sin red": artistas y temas que no se pudieron resolver por
  falta de conexión se apuntan y se reconcilian solos, unos pocos por
  vuelta, aprovechando que la Radio ya tiene red por otra búsqueda
  (`RadioRepository.reconcilePending()`).
- El diccionario aprendido persiste en la copia de Google Drive (H07),
  fusionando en vez de reemplazar, y respetando el ámbito de cuenta
  (dispositivos de la misma cuenta se reconcilian; cuentas distintas
  construyen su propio diccionario).
- Botón "Crear base de datos" en Ajustes: recorre los géneros de la
  semilla contra MusicBrainz (no contra la biblioteca local — primera
  versión incorrecta, corregida tras que Miguel Ángel señalara que la
  fuente no podía ser el disco del usuario), guarda con reintentos y
  tolerancia a fallos de red por página/género, y al terminar
  reconcilia los nombres de carpeta de la biblioteca que quedaron con
  nombre de canal de YouTube en vez de nombre de artista.
- `suggestRelatedArtist()` consulta primero la base de datos de la
  tarjeta (instantáneo, sin red) y solo cae a MusicBrainz en vivo si
  no hay cobertura — este punto de conexión faltaba y fue el motivo
  central de la frustración de Miguel Ángel en la segunda mitad de la
  sesión: se había construido una base de datos que no se estaba
  usando para lo que importaba.
- La porción de exploración (UNKNOWN) dejó de poder agotarse: es la
  cuota diseñada para ser inagotable (paginación sobre el catálogo de
  MusicBrainz), y estaba siendo marcada como agotada tras la primera
  página vacía. El fallback final ya no repite temas ya sonados bajo
  ninguna circunstancia — se eliminó el único camino de código que lo
  hacía a propósito — y en su lugar tira de la porción de exploración.
- Red de seguridad adicional de no-repetición por TÍTULO puro
  (`radioUsedTitles`), independiente de la clave artista+canción, para
  cubrir casos donde el mismo artista llega escrito de formas
  distintas.

**Incidencias reales de la sesión, para que no se repitan:**
- Varios fallos de compilación por adivinar el error en vez de leerlo
  (el zip de logs de Actions no es accesible por red desde el entorno
  de trabajo). Se corrigió añadiendo un paso al workflow
  (`build-and-deploy.yml`) que publica los errores de Kotlin como
  anotaciones del check, legibles por API — esto debe seguir
  funcionando en sesiones futuras y ahorra rondas de adivinanza.
- Un fallo de tipo MIME (`application/json` en vez de `text/plain`) en
  la escritura SAF hizo que el proveedor de almacenamiento renombrara
  los ficheros del diccionario, y por tanto que NADA persistiera entre
  aperturas de Ajustes durante varias iteraciones — el contador volvía
  a cero cada vez. Corregido, con limpieza automática de los ficheros
  duplicados que dejó el fallo (`cleanupStrayFiles()`).
- Un borrado automático de la base de datos, escrito para limpiarla de
  una contaminación real (nombres de canal colados como artistas),
  quedó mal secuenciado (borraba antes de confirmar que podía marcar
  el borrado como hecho) y se ejecutó en bucle en cada apertura de
  Ajustes, destruyendo trabajo repetidamente. Corregido y el borrado
  automático retirado por completo una vez cumplida su función.
- Lentitud severa de toda la aplicación (no solo Ajustes) causada por
  resolver la carpeta SAF del diccionario en cada lectura/escritura en
  vez de cachearla — cada resolución lista la raíz completa de la
  tarjeta. Corregido con caché de sesión.
- El botón "Parar" del constructor de base de datos no respondía
  porque el estado final se fijaba dentro de la propia corrutina
  cancelada. Corregido.

### Hoja de ruta para la siguiente sesión

1. **Verificación en dispositivo real**, pendiente en toda la sesión.
   En concreto: que el ancla de Led Zeppelin no derive a géneros no
   relacionados, que no se repita ningún tema, que el botón "Crear
   base de datos" complete un recorrido sin colgar la aplicación, y
   que `suggestRelatedArtist()` esté sirviendo candidatos de la base
   de datos local (verificable en el log: línea "DE LA BASE DE DATOS,
   sin red").
2. **Ampliar/verificar la semilla de 1.161 artistas.** Se escribió a
   mano y sin contraste con ninguna fuente externa; puede tener país o
   género incorrectos para artistas concretos.
3. **Probar Discogs y Wikidata contra la red real** — no se pudo hacer
   en esta sesión por falta de acceso de red al entorno de trabajo.
4. Sigue abierta la verificación física de dos dispositivos (H07,
   PASO 5) y el resto de la hoja de ruta original de H13 (auditoría de
   botones de `PlayerBar.kt`, aspecto de "algunos ítems").
5. **Incidencia de proceso, RESUELTA en S026 (ver más abajo):**
   `ANNEX_ROUTER.md` marcaba H13 como hito activo desde el cierre de
   S024 pese a que la práctica totalidad del trabajo de S025 fue sobre
   H08. PCH formal ejecutado al arranque de S026 — ver
   `DOCS/ANNEX_ROUTER.md`, entrada 2026-07-29.

---

## S026 (2026-07-29) — AUDITORÍA DE CÓDIGO: ¿SE USA DE VERDAD LA BASE
DE DATOS?

**Motivo.** S025 cerró extenuada por tokens y, según Miguel Ángel, sin
seguir su propio criterio en varios puntos finales. Al arrancar S026
reporta que la Radio sigue sin usar la base de datos construida —
ejemplo dado: un tema de Led Zeppelin deriva a "rock sinfónico" en vez
de hard rock/rock arena. Petición explícita: auditar el código real
antes de dar nada por bueno, no fiarse de las notas de cierre.

**Hallazgo: el ejemplo dado es literalmente el mismo caso, con las
mismas palabras, que motivó el rediseño de S025** (ver más arriba,
sección "S025", cita textual de Miguel Ángel: *"me he hinchado a
escuchar rock sinfónico después de poner un tema de Led Zeppelin, que
no me apetecía en absoluto"*). Es decir: no es un fallo nuevo — es el
recuerdo del fallo que ya motivó la reescritura.

**Verificado leyendo el código real, en el HEAD del repositorio tras
S025 (commit `b760f544`), archivo por archivo:**

1. `RadioRepository.resolveAnchor()` (línea 662) consulta
   `anchorFromDictionary()` ANTES de tocar la red — confirmado, no es
   una afirmación de comentario sin más: es el primer `return` posible
   de la función.
2. `RadioRepository.suggestRelatedArtist()` (línea 904) consulta
   `anchorDictionary.artistsMatching()` ANTES de `findCandidatesForGenres()`
   (la búsqueda en vivo) — confirmado igual, con log explícito ("DE LA
   BASE DE DATOS, sin red") en el propio camino de éxito.
3. La semilla de 1.161 artistas (`AnchorDictionary.seed`, línea 123)
   se carga desde `app/src/main/assets/anchor_artists.json` — es un
   ASSET EMPAQUETADO EN EL APK, no un fichero de la tarjeta SD que
   dependa de haber pulsado "Crear base de datos" ni de haber escuchado
   Radio antes. Está disponible desde la primera instalación de
   cualquier build que incluya este commit.
4. Comprobado el contenido real de ese fichero: la entrada de **Led
   Zeppelin** existe (`país: GB`, `géneros: arena rock, blues rock,
   classic rock, folk rock, hard rock, heavy metal, progressive rock,
   rock`) — ninguno de esos ocho géneros es "rock sinfónico" ni nada
   emparentado con `symphonic rock`.
5. El emparejamiento de género usa el CONJUNTO completo del ancla, no
   un único género alfabético, en las tres porciones: `fetchFromKnown()`
   pasa `anchorGenres = anchor.genres` a `KnownHitsRepository.matchesGenre()`
   (intersección + descenso en el árbol de géneros, nunca ascenso);
   `suggestRelatedArtist()` construye la búsqueda con
   `listOf(anchor.genre) + anchor.genres`; el propio `resolveAnchor()`
   guarda `genres = allGenres` (unión diccionario + MusicBrainz), no
   solo el género ganador por voto/alfabético.
6. Confirmado en GitHub Actions (`GET /actions/runs`) que el commit de
   cierre de S025 (`b760f544`) compiló y desplegó en verde
   (`conclusion: success`, 2026-07-29T17:33:21Z) — el código auditado
   arriba es el que de verdad llegó a compilarse, no una versión sin
   probar.

**Conclusión de la auditoría, con la honestidad que pide Miguel
Ángel:** sobre el código, el punto central que S025 dio por resuelto
("la base de datos se consulta primero") está correctamente resuelto
y encadenado en los tres puntos donde importa (ancla, exploración,
emparejamiento de género). No se ha encontrado ningún punto de
conexión roto ni ausente. La explicación más probable de lo que Miguel
Ángel sigue viendo es que el dispositivo no tuviera instalado todavía
un APK posterior a este commit — el propio cierre de S025 dejó constancia
de que nada de esto se había verificado en dispositivo real. Esto NO
es una garantía de que el comportamiento en vivo sea correcto —
sigue siendo cierto que nadie lo ha visto funcionar en un teléfono —
solo que el código fuente no contiene el fallo descrito.

**No verificado en esta auditoría, sigue pendiente:** todo lo que ya
figuraba pendiente en la Hoja de Ruta anterior (puntos 1-4 de más
arriba) — en particular el comportamiento real en dispositivo, y si la
semilla de 1.161 artistas tiene errores de país/género en entradas
distintas a Led Zeppelin.

---

## CORRECCIÓN DE LA AUDITORÍA ANTERIOR, CON LOG REAL DE DISPOSITIVO

Miguel Ángel aportó `radio_relacionados_debug.txt`, un log real de una
sesión de Radio de Led Zeppelin de varias horas. Dos cosas quedan
confirmadas y una queda corregida respecto a la auditoría de más
arriba:

- **Confirmado:** el dispositivo SÍ tiene instalado un build posterior
  al commit auditado — el log muestra literalmente las líneas
  predichas (`resolveAnchor(...) -> ancla del DICCIONARIO (sin red)`,
  `suggestRelatedArtist(...) -> '...' (88 candidatos DE LA BASE DE
  DATOS, sin red)`). La hipótesis de "APK desactualizado" queda
  descartada por evidencia directa.
- **Corregido:** pese a que la base de datos SÍ se consulta primero
  (eso era correcto), el log muestra a Elton John y a Emerson, Lake &
  Palmer colándose en la radio de Led Zeppelin de todos modos. La
  auditoría anterior no había detectado esto porque no había log real
  con el que contrastar el código — solo se había verificado que el
  MECANISMO de consulta (diccionario antes que red) funcionaba, no que
  el RESULTADO de la coincidencia de género fuera bueno.

**Causa real, encontrada esta vez con el log delante:** Elton John
comparte con Led Zeppelin únicamente `classic rock` (de ocho géneros);
Emerson, Lake & Palmer comparte únicamente `progressive rock`. Ambas
etiquetas tienen CERO descendientes en `genre_tree.json`, así que
`GenreTree.isSpecific()` las trata como carpetas concretas de verdad,
cuando en la práctica son etiquetas de formato de radio que MusicBrainz
cuelga de casi cualquier artista de rock/pop de los 60-80 (63 de 1.682
entradas del diccionario de éxitos y 46 de los 1.161 artistas de la
semilla llevan `classic rock`).

### Fix aplicado (commit `2923d28`, compilado en verde)

Nuevo `GenreMatchQuality.kt`: objeto compartido por las tres porciones
que puntúa la coincidencia como **FUERTE** (2+ géneros específicos
compartidos) o **DÉBIL** (exactamente 1, o coincidencia por descenso/
hermanos/ancla-genérica de rescate). Cada porción intenta primero la
categoría FUERTE y solo cae a DÉBIL como último recurso si se queda
sin candidatos — diseño pedido explícitamente por Miguel Ángel en vez
de una lista negra de géneros genéricos:

- **Conocidos (80%)** — `KnownHitsRepository.randomHit()`/
  `knownArtists()`.
- **Exploración (10%)** — `RadioRepository.suggestRelatedArtist()`;
  `AnchorDictionary.artistsMatching()` ahora devuelve también los
  géneros de cada candidato para poder puntuarlo.
- **Disco (10%)** — `PlayerManager.fetchFromDisco()`, vía el nuevo
  `RadioRepository.genreMatchQuality()` público (PlayerManager no
  tiene acceso directo a `GenreTree`). `RadioAnchor.sharesGenreWith()`
  retirada por quedar sin llamadores.

Con este cambio, Emerson, Lake & Palmer y Elton John pasan a nivel
DÉBIL/último recurso frente a Led Zeppelin; un candidato que comparta,
por ejemplo, `hard rock` + `heavy metal` pasa a nivel FUERTE y se
prefiere.

**Respuesta directa a la pregunta de Miguel Ángel** ("¿llamamos a
MusicBrainz siempre, o solo cuando no hay datos?"): confirmado sobre
el propio log, MusicBrainz en vivo solo se llama cuando el diccionario
local se queda sin candidatos tras aplicar las exclusiones de la
sesión (`if (fromDictionary.isNotEmpty())` en `suggestRelatedArtist()`,
sin tocar en este commit). La porción de Conocidos (80% del cupo)
nunca ha llamado a MusicBrainz bajo ninguna circunstancia: es
enteramente local, `KnownHitsRepository` es un asset empaquetado.

**Sin verificar en dispositivo real todavía.**

---

## CORRECCIÓN SOBRE EL FIX ANTERIOR — "2+ géneros" no bastaba (captura real)

Miguel Ángel probó el build del fix anterior en dispositivo real e
instaló el APK nuevo. Captura de pantalla de la cola de Radio,
21:48: tras Led Zeppelin - Black Dog, el segundo tema es **Supertramp
- The Logical Song**, seguido de Black Sabbath, ELO y Deep Purple.

**Esto SÍ es una regresión real del fix de "2+ géneros específicos",
no una confusión de logs viejos** (a diferencia del episodio anterior
de este mismo hilo). Verificado contra los datos reales del propio
repositorio: Supertramp comparte con Led Zeppelin no una, sino **dos**
etiquetas -- `classic rock` Y `progressive rock` -- y ambas pasan
`GenreTree.isSpecific()` (0 y 6 descendientes). El umbral de "2+" que
se acababa de construir se cumplía con dos etiquetas que no dicen nada
real, exactamente el mismo mecanismo que ya había colado a Elton John
antes, solo que esta vez con dos etiquetas de formato en vez de una.

Palabras de Miguel Ángel sobre el resultado, que resumen el problema
mejor que cualquier métrica: *"si tenemos que podemos relacionar a
Supertramp con Led Zeppelin... el único que se parece es que son
personas que forman un grupo y que hacen música... la música que
hacen no se parece absolutamente en nada."*

### Fix (commit `3439312`, compilado en verde)

`GenreMatchQuality` gana una lista corta y razonada de etiquetas de
FORMATO/ÉPOCA que nunca cuentan, ni fuerte ni débil, en ningún
peldaño: `classic rock` (categoría de emisora de radio en EE.UU.),
`rock and roll` (descriptor de década que se cuelga retroactivamente
de casi cualquier cosa), `mainstream rock` (categoría de lista de
éxitos Billboard). **`progressive rock` NO entra en la lista** -- es
un género real y específico (Yes, King Crimson, Genesis); el problema
no es la etiqueta, es que la propia semilla de Led Zeppelin la incluye
de forma cuestionable entre sus ocho géneros.

Resultado simulado contra los datos reales del propio repositorio,
antes de compilar:
- **Elton John** deja de tener NINGÚN género específico en común con
  Led Zeppelin tras quitar `classic rock` -- queda excluido del todo,
  no solo degradado.
- **Supertramp** y **Emerson, Lake & Palmer** comparten únicamente
  `progressive rock` -- un solo género específico -- y caen a nivel
  DÉBIL/último recurso.
- **Black Sabbath** y **Deep Purple** siguen en nivel FUERTE:
  comparten de verdad `hard rock` + `heavy metal` + `blues rock` con
  Led Zeppelin.

**Sin verificar en dispositivo real todavía** -- pendiente que Miguel
Ángel instale este build y confirme si la cola deja de mezclar
Supertramp/ELP/Elton John con Led Zeppelin.

---

## TERCERA ITERACIÓN — Queen y Pink Floyd, causa en el DATO no en el ALGORITMO

Miguel Ángel probó el build anterior (`3439312`) en dispositivo real.
Log y captura de pantalla, 22:14-22:15: cola de Led Zeppelin con
Queen, Motörhead, Jethro Tull y Pink Floyd. Su reacción, textual:
*"Jethro Tull... puede coincidir en algún tema de rhythm and blues,
vale. Pero de ahí a Queen y a Pink Floyd... hay un mundo enorme."*

Verificado contra los datos reales antes de tocar nada: esta vez el
algoritmo (`GenreMatchQuality`, sin cambios desde `3439312`) funcionaba
exactamente como se diseñó. El problema estaba en el DATO: la propia
semilla de Led Zeppelin (`anchor_artists.json`) incluye `progressive
rock` entre sus ocho géneros -- etiqueta cuestionable para este
artista, y ampliamente colgada por MusicBrainz en artistas muy
distintos entre sí (Queen, Pink Floyd, Yes, Jethro Tull...). Con
`progressive rock` en la semilla:
- Queen comparte `{hard rock, progressive rock}` = 2 géneros
  específicos → FUERTE.
- Pink Floyd comparte `{blues rock, progressive rock}` = 2 → FUERTE.

### Fix (commit `d2a8845`, compilado en verde)

Se quita `progressive rock` de la entrada de Led Zeppelin en la
semilla -- corrección de UN DATO concreto, no del algoritmo de
coincidencia. Verificado por simulación contra los datos reales antes
de compilar:
- **Queen** se queda solo con `hard rock` compartido (1) → DÉBIL/
  último recurso.
- **Pink Floyd** se queda solo con `blues rock` compartido (1) →
  DÉBIL/último recurso.
- **Jethro Tull** sigue en FUERTE (comparte `folk rock` + `hard rock`,
  sin depender de `progressive rock`) -- coherente con que Miguel
  Ángel no lo cuestionó.
- **Motörhead** no se ve afectado (`heavy metal` + `hard rock`).

**Pendiente, sin poder verificarse desde este entorno de trabajo**
(sin acceso de red a `musicbrainz.org`): auditar el resto de los
1.161 artistas de la semilla por si tienen el mismo problema --
etiquetas de género "de kitchen sink" con algún tag ampliamente
sobre-usado que pueda seguir generando puentes falsos con otros
anclajes. Ya figuraba como pendiente en la hoja de ruta original de
S025; esta sesión lo confirma como riesgo real, no solo teórico.

**Sin verificar en dispositivo real todavía.**

---

## FALLO DE NOMBRE DE FICHERO ENCONTRADO POR MIGUEL ÁNGEL EN EL EXPLORADOR DE ARCHIVOS

Miguel Ángel navegó él mismo hasta `Tarjeta SD > miMoo > MiMoo >
diccionario` y encontró un único fichero: `temas.json.txt` (111 B) --
no `temas.json`. El tamaño coincide exactamente con
`AnchorDictionary.writeText('temas.json') -> OK, 111 caracteres` del
log, confirmando que es el mismo fichero, mal nombrado.

**Es la segunda vuelta del mismo fallo que ya se documentó en S025**
(entonces con `application/json` → `.json.json`). El fix de S025 pasó
a `text/plain` asumiendo que no tenía extensión canónica -- pero SÍ la
tiene (`.txt`), y al menos el proveedor SAF de la tarjeta de Miguel
Ángel se la añade igual cuando el nombre pedido no termina ya en ella.

### Fix (commit `1b0e5b8`, compilado en verde)

Cambiado a `application/octet-stream` (tipo "binario genérico" de
Android, sin extensión canónica en el mapa de MIME del sistema -- no
hay nada que un proveedor SAF pueda añadir), en los dos puntos de
creación (`writeText()` y `cleanupStrayFiles()`). `findDoc()` ya
tolera ficheros renombrados (busca por prefijo si el nombre exacto no
aparece), así que `cleanupStrayFiles()` migrará solo, en la próxima
carga, el `temas.json.txt` existente a un `temas.json` con el nombre
correcto -- sin que Miguel Ángel tenga que borrar nada a mano.

### Aclaración aparte -- la carpeta doble no es un fallo

Miguel Ángel también preguntó por la ruta `miMoo > MiMoo`, que le
pareció rara. **No es un fallo:** `miMoo` (minúscula) es la carpeta
raíz que él mismo eligió con el selector de Android; `MiMoo`
(mayúscula, `DIR_BASE`) es la subcarpeta que la propia app crea
siempre dentro de la raíz elegida, para organizar su contenido --
mismo patrón que usa `LibraryFolderReconciler.DICT_DIR` para las
descargas. Coincidencia de nombres entre la carpeta que él eligió y la
que crea la app, no un error de la app.

**Sin verificar en dispositivo real todavía.**

---

## VERIFICACIÓN REAL DE EXISTENCIA DEL TEMA, O PARAR LA RADIO SIN RED

Tras el fallo de "Free" (corto de Sony Animation colado en la Radio),
Miguel Ángel pidió ir más allá del filtro de canal: verificar de
verdad que el vídeo encontrado es un tema real del artista, contra
cualquier fuente disponible (diccionario local, MusicBrainz, Discogs,
Wikidata), y si no hay red para comprobarlo, **parar la Radio del
todo** con aviso — nunca meter un vídeo sin verificar. Cita textual:
*"el título del vídeo tiene que ser de un artista y de un tema de ese
artista... si no coincide con ningún título de ese artista, se
desecha. Y si no hay red no hay radio... antes parar que meter un
vídeo que no viene a cuento."* Y, sobre qué fuente usar: *"me da igual
validar contra MusicBrainz, Discogs, local, Wikidata o la fuente que
sea. Lo que no quiero son audios de vídeos que no son canciones."*

### Implementado (commit `4ddfecd`, compilado en verde)

- **`RadioRepository.verifyTrackExists(artist, rawVideoTitle)`** (nueva):
  reutiliza la misma cascada que ya usaba `resolveOriginalDecade()`
  (diccionario en tarjeta → diccionario de éxitos → MusicBrainz →
  Discogs → Wikidata), pero devuelve un resultado de tres vías —
  `TrackExistence.Confirmed`/`NotFound`/`NetworkUnavailable` — en vez
  de tratar "no sé" y "no hay red" igual. La distinción se apoya en
  `lastFailureWasTransient`, el mismo mecanismo que ya usa
  `reconcilePending()`.
- **`PlayerManager.resolveYoutubeCandidate()`**: cuando no hay canción
  conocida (Exploración, o "artista conocido, tema no catalogado" de
  Conocidos), cada candidato que pasa el filtro de canal (ver bloque
  anterior) se verifica contra `verifyTrackExists()` antes de
  aceptarlo. Confirmado → se usa. No encontrado → se prueba el
  siguiente candidato de los 6 que trae la búsqueda. Sin red → la
  Radio se detiene del todo (no reintenta sola en la siguiente vuelta)
  y se avisa a la UI. Con canción conocida (viene del diccionario, ya
  es un dato curado por construcción) no se verifica -- sería
  redundante.
- **UI**: `PlayerBar.kt` muestra un `AlertDialog` ("Radio detenida")
  con botón "Reintentar" (`PlayerManager.dismissRadioNetworkLost()`,
  expuesto vía `PlayerBarViewModel`) que limpia el aviso y relanza el
  reparto de la cola.

**Alcance deliberadamente limitado a las búsquedas "solo artista".**
Las porciones Conocidos (con canción catalogada) y Disco (pistas ya
descargadas/reales) no necesitan esta verificación -- su dato ya es
curado o real por construcción; extenderlo ahí sería redundante y
solo añadiría llamadas de red innecesarias.

**Sin verificar en dispositivo real todavía** -- en particular, que el
aviso "Radio detenida" aparezca de verdad sin red, y que "Reintentar"
relance la cola correctamente.

---

## UMBRAL DE COINCIDENCIA DE GÉNERO POR PORCENTAJE, CONFIGURABLE EN AJUSTES

Con log real delante, Miguel Ángel confirmó que el sistema de dos
niveles (2+ géneros específicos = fuerte, exactamente 1 = último
recurso) seguía colando artistas que no encajaban: Pink Floyd (13
géneros catalogados, solo 1 -- `blues rock` -- compartido con Led
Zeppelin) y Fleetwood Mac (8 géneros, también solo 1 compartido). *"Pink
Floyd... hay un mundo enorme... Fleetwood Mac más en una lista con
Neil Young, Creedence Clearwater Revival."*

**Rediseño, textual:** *"si tiene diez géneros, y el otro tiene diez
géneros, y tienen ocho que son iguales, coincide... un 30% o un 40%...
configurable en ajustes, con escalones de diez."*

### Implementado (commit `3109a7b`, compilado en verde)

`GenreMatchQuality` reescrita por completo: en vez de contar géneros
específicos compartidos, calcula **intersección/unión** (índice de
Jaccard) de los géneros específicos de cada lado, y admite el
candidato solo si el porcentaje supera un umbral. Verificado contra
los datos reales antes de implementar:

| Artista | Compartidos/unión | % |
|---|---|---|
| Pink Floyd | 1/17 | 6% |
| Fleetwood Mac | 1/10 | 10% |
| Queen | 1/12 | 8% |
| Jethro Tull | 2/6 | 33% |
| Motörhead | 2/5 | 40% |
| Black Sabbath | 3/7 | 43% |
| Deep Purple | 3/6 | 50% |

Con el **40% por defecto**, la línea separa exactamente donde Miguel
Ángel la puso a ojo. Sustituye por completo el sistema fuerte/débil:
ya no hay "último recurso" aparte -- el porcentaje decide solo, y si
nadie lo alcanza esa vuelta, la porción se declara agotada igual que
antes.

**Configurable en Ajustes → Radio:** nuevo slider en escalones de 10
(`UiPreferencesManager.radioGenreMatchThresholdPercent`, mismo patrón
de `SharedPreferences` que el cupo 80/10/10). Se aplica de inmediato,
sin reiniciar la Radio -- las cuatro llamadas que lo usan
(`fetchFromKnown` ×2, `resolveFinalFallback`, `fetchFromDisco`) leen
el valor actual en cada vuelta.

**Sin verificar en dispositivo real todavía** -- pendiente instalar,
probar con Led Zeppelin, y comprobar también el slider nuevo en
Ajustes.

---

## FALLO ADICIONAL ENCONTRADO EN EL PROPIO LOG (sin que Miguel Ángel lo señalara)

Releyendo `radio_relacionados_debug__4_.txt` a fondo (el mismo del
episodio Pink Floyd/Fleetwood Mac), aparecía una excepción real que no
había comentado nadie todavía:

```
AnchorDictionary.writeText('temas.json') -> EXCEPCIÓN: IllegalArgumentException:
Failed to determine if .../temas.json is child of ...: Missing file
```

Después de varias escrituras en verde (18:56, 21:41, 22:14), empieza a
fallar sistemáticamente a partir de las 05:03 -- salto de varias
horas, compatible con que el proceso de la app se reiniciara de por
medio. `docCache` (el mapa en memoria que evita repetir la búsqueda
del fichero en cada escritura) no sobrevive a un reinicio de proceso;
si una escritura llega con un handle de `DocumentFile` que ya no
corresponde a un fichero real (por ejemplo, consolidado y borrado por
`cleanupStrayFiles()` en la instancia anterior), la apertura del
`OutputStream` falla con ese mensaje.

### Fix (commit `cbeadf3`, compilado en verde)

`writeText()` ahora reintenta UNA vez si el primer intento falla:
invalida la entrada de `docCache` para ese nombre, resuelve de nuevo
contra el disco (`dir.findFile()` o `dir.createFile()` si tampoco
existe ya) y reintenta. Si el segundo intento también falla, se
informa como antes -- nunca reintentos indefinidos, y lo aprendido se
queda en memoria para el resto de la sesión sin romper la Radio.

**Sin verificar en dispositivo real todavía.**

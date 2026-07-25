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

#### 5. Los dos cupos por proporción

- **"Se compara con la lista de conocidos y se pone la proporción
  fijada en settings."** -- cupo de conocidos, porcentaje de Ajustes.
- **"Se pone la proporción fijada de artistas en el disco."** -- cupo
  de biblioteca local, porcentaje de Ajustes.
- **Disco, regla propia:** *"si se agota se deja de poner la
  proporción... si se pone reggae y no hay reggae en el disco, no se
  ponen temas del disco; si se pone rock de los setenta y no hay rock
  de los setenta en el disco, no se pone disco."* El cupo de disco no
  degrada NUNCA: o hay material del género+década exactos en la
  biblioteca local, o ese cupo simplemente no se sirve y su porcentaje
  se reparte entre los demás. La marca `radioDiscoExhausted` ya existe
  pero hoy solo se activa con ancla española -- debe activarse en
  cualquier ancla.

#### 6. Lo que sobrevive de mi hoja de ruta anterior

Estos puntos no los tocó Miguel Ángel y siguen siendo válidos, en este
orden, DESPUÉS de lo anterior:

- **Ancla determinista.** `resolveAnchor()` usa `genres.random()`:
  sustituir por el género con más votos en MusicBrainz. Y no anclar
  desde el nombre del canal de YouTube (`'Natacha Atlas Official'`,
  `'知心音樂網'`) si existe artista estructurado.
- **Validar el candidato antes de encolar** -- el caso del vídeo de la
  Tate y el de noticias de guerra. Cobra más importancia todavía con
  el peldaño 3, que se apoya entero en artistas desconocidos.
- **Auditoría de décadas del diccionario** (Måneskin, Blur, Ska-P,
  Love of Lesbian, The Animals mal colocados).
- **Ampliación del diccionario hacia ~100/década.** Con la escalera
  nueva deja de ser un cuello de botella que rompe la sesión, pero
  sigue mandando en la variedad.
- Failover de Radio-Browser (H09) ante 503/timeout, solo si lo pide.

---

## Fuera de Alcance de Este Hito

- Cualquier forma de "me gusta"/entrenamiento de preferencias más allá
  de favoritos ya existentes (H03) — no se ha planteado, no está en
  el objetivo descrito por Miguel Ángel.
- Playlists colaborativas o compartidas entre Miguel Ángel y Silvia —
  no mencionado, fuera de alcance salvo que se pida explícitamente.


# Hito 15 — miMooutCast: Radio de Ancla a la Carta (Géneros/Décadas)

*Apertura: 2026-08-04 (cierre de S028), a petición explícita de Miguel
Ángel. Cita textual: "vamos a preparar un hito nuevo para la siguiente
sesión, nuestro propio ShoutCast: miMooutCast, en esta vista vamos a
usar la radio que hemos montado eligiendo el ancla a la carta: con dos
secciones, géneros y décadas — ahora pondremos música eligiendo entre
los géneros o entre las décadas que existen en MusicBrainz."*

---

## Objetivo del hito

El motor de Radio (H08, `RadioRepository`/`PlayerManager`) ya sabe
generar sesiones completas de música relacionada (género + origen +
década, 80/10/10 conocidos/disco/desconocidos, todo lo construido y
corregido en S028) — pero SIEMPRE parte de una pista que ya está
sonando: el ancla se deriva de su artista estructurado
(`resolveAnchor()`/`anchorFromDictionary()`).

miMooutCast es una pantalla nueva donde el ancla se elige A MANO, sin
necesidad de tener nada sonando antes: el usuario entra, elige un
género o una década (o ambos) de una lista real sacada de MusicBrainz,
y la Radio arranca directamente anclada en esa elección. Mismo motor
de generación de cola que ya existe — lo que cambia es únicamente el
PUNTO DE ENTRADA del ancla.

No es un rediseño de la Radio: es una vía de arranque nueva para la
Radio ya construida.

---

## Contexto técnico -- qué ya existe y qué hace falta añadir

### Lo que ya existe y se reutiliza tal cual

- **`RadioAnchor`** (`RadioRepository.kt`) -- género, país, década,
  `isClassical`, fijado una sola vez al arrancar una sesión. Todo el
  resto del motor (cupos 80/10/10, `fetchRoundCandidate()`,
  `suggestRelatedArtist()`, `verifyTrackExists()`, exclusión de
  artistas fallidos, cupo de disco en clásica, etc. -- todo lo
  arreglado en S028) opera sobre esta estructura sin saber de dónde
  salió.
- **`resolveAnchor()`/`anchorFromDictionary()`** -- hoy es la ÚNICA
  vía para construir un `RadioAnchor`, siempre partiendo de un
  artista. miMooutCast necesita una segunda vía que construya el
  mismo objeto directamente desde género+década elegidos a mano, sin
  artista de origen.
- **`fetchOneRadioTrack()`/`topUpRadioQueueIfNeeded()`** (PlayerManager)
  -- una vez hay un `RadioAnchor` fijado, esto ya rellena la cola
  solo. No debería hacer falta tocarlo si el ancla manual se
  construye con la misma forma que la automática.
- **Catálogo de géneros y décadas curado a mano de H09** (Radio
  Online/ShoutCast, `RadioGenreCatalog.kt` según el propio
  `MASTER_DOCUMENT.md`) -- posible punto de partida o inspiración
  para el catálogo de géneros de MusicBrainz de esta pantalla nueva,
  aunque H09 es un sistema completamente distinto (Radio-Browser.info,
  streaming de emisoras reales) -- no compartir código sin comprobar
  antes que el catálogo es del tipo correcto (géneros MUSICALES de
  MusicBrainz, no etiquetas de emisora).
- **Estilo visual** -- "chapitas de cristal esmerilado" ya
  consistente en toda la app (`GlassTokens`, `glassChip()`, ver
  `ui/theme/`), usado en H13 (UX del reproductor) y en toda la
  pantalla de Favoritos construida en esta misma sesión S028. Petición
  explícita de Miguel Ángel: cuidar la UX con el mismo lenguaje visual
  y que sea muy intuitivo.

### Lo que hace falta construir (sin cerrar el diseño exacto todavía)

1. **Nueva función de construcción de ancla manual** en
   `RadioRepository` -- recibe género (y opcionalmente década) elegidos
   por el usuario, sin artista de origen, y devuelve un `RadioAnchor`
   con la misma forma que `resolveAnchor()` ya produce. Decisión
   pendiente: ¿el país/origen se pide también, o se deja "cualquiera"
   por defecto al no haberlo mencionado Miguel Ángel entre las dos
   secciones ("géneros y décadas") que sí especificó? -- **preguntar
   antes de construir, no asumir.**
2. **Catálogo real de géneros de MusicBrainz** navegable -- de dónde
   sale la lista exacta que se le enseña al usuario (¿la misma
   `GenreTree`/diccionario ya usado por la Radio automática para
   `GenreMatchQuality`? ¿Una llamada nueva a la API de géneros de
   MusicBrainz? Revisar qué hay ya construido en H08 antes de duplicar
   nada).
3. **Lista de décadas** -- previsiblemente más simple (rango fijo,
   p.ej. 1950-2020), pero decidir el límite superior/inferior con
   Miguel Ángel.
4. **Pantalla nueva** (`ui/mimooutcast/` o nombre que se decida) --
   dos secciones/pestañas, Géneros y Décadas, con el mismo lenguaje de
   chapitas de cristal esmerilado que el resto de la app. Diseño
   exacto de la interacción (¿se elige un género Y una década antes de
   arrancar, o cada sección arranca por su cuenta con la otra
   dimensión "cualquiera"?) -- **sin cerrar, decidir con Miguel Ángel
   al empezar la sesión.**
5. **Entrada en el drawer/navegación** -- mismo patrón que el resto de
   entradas del menú lateral (`MainActivity.kt`, `NavGraph.kt`).

---

## COMPLETADAS EN S029

Sesión de diseño (sin código): se cerraron tres de los cuatro puntos
de alcance que dejó abiertos la apertura del hito, concertados
explícitamente con Miguel Ángel:

1. **Origen/país:** SÍ se pide, como tercera sección junto a Géneros y
   Décadas, con la misma organización de 4 grupos que ya usa la Radio
   (`OriginGroup`: Iberoamericana/Anglosajona/Europea/Mundial).
2. **Interacción entre secciones:** se elige una única dimensión
   (género, década u origen) y las otras dos quedan libres -- no hace
   falta elegir varias antes de arrancar.
3. **Catálogo de géneros:** lista fija, no una consulta en vivo a la
   API de géneros de MusicBrainz.

Sin código todavía. La sesión derivó hacia el diseño de un hito nuevo,
H16 (Lista Negra / "No me gusta"), surgido de una petición explícita
de Miguel Ángel durante esta misma conversación -- ver
`DOCS/ANNEX_H16.md`.

## COMPLETADAS EN S030

Sesión que retomó H15 tras un PCH a mitad de la sesión (venía de
trabajar en H16/Lista Negra). Hito completo en código, con un bug real
encontrado y corregido tras prueba en dispositivo real:

1. **Rango de décadas cerrado**: 1950-2020 (ocho décadas), extendiendo
   un decenio el precedente ya usado en H09 (`RadioGenreCatalog`,
   50s-2010s) -- asunción declarada a Miguel Ángel, sin objeción.
2. **`RadioRepository.manualAnchor(genre, decadeBegin, originGroup)`**
   -- construye el `RadioAnchor` a mano, sin artista de origen.
3. **SIN CUPOS -- corrección de rumbo a mitad de sesión.** El primer
   intento reutilizó el motor de cupos 80/10/10 de la Radio automática
   (interpretación literal de "mismo motor" en el objetivo del hito) --
   equivocado, orden explícita de Miguel Ángel: streaming simple, sin
   porcentajes ni rondas de 10, eso es exclusivo de H08.
   `PlayerManager.fetchSimpleManualCandidate()`: dictionario
   (`relaxGenre` cuando no hay género) -> MusicBrainz en vivo ->
   biblioteca local, el primero que encuentre algo. Reutiliza
   `fetchOneRadioTrack()`/`topUpRadioQueueIfNeeded()` sin tocarlos --
   `manualAnchorActive` es lo que distingue qué motor usar.
4. **`buildGenreQuery()`/`findCandidates()` arreglados** para
   funcionar sin género (ancla "origen solo" vía `country:` en la
   consulta a MusicBrainz). "Década sola" (sin género ni origen) no
   tiene término real que mandar a MusicBrainz -- limitación real de
   la arquitectura (decadeBegin nunca ha formado parte de la
   consulta, se verifica por tema, no por artista, desde S027), no un
   bug: esa combinación solo puede tirar de la biblioteca local y del
   diccionario de éxitos (`relaxGenre`).
5. **`GenreMatchQuality.of()`**: un ancla sin género ya no da 0%
   siempre -- se trata como "sin restricción de género".
6. **`MimooutcastCatalog.kt`**: 24 géneros raíz verificados uno a uno
   contra `genre_tree.json` + 8 décadas + los 4 `OriginGroup` ya
   cerrados en H08.
7. **Subgéneros (petición durante la sesión)**:
   `GenreTree.directChildren()` -- un segundo nivel de chapitas al
   pinchar un género raíz con hijos catalogados (p.ej. Electrónica),
   con una chapita "Todo `<género>`" para el género entero. Los
   géneros sin hijos (hojas) arrancan directo, "siempre que se
   pueda" tal como se pidió.
8. **Loop de batería + capa opaca (peticiones durante la sesión)**:
   `PlayerManager.playOpeningLoopIfAvailable()` reutilizado tal cual
   mientras se resuelve el primer tema; fondo opaco al 94% detrás del
   spinner de carga, sin mezclarse con las chapitas de debajo.
9. **`MimooutcastDebugLogger.kt`** (`mimooutcast_debug.txt`) -- fichero
   de diagnóstico propio, separado de `radio_relacionados_debug.txt`
   (H08), mismo patrón que el resto de logs del proyecto. No existía
   al principio de la sesión; las cuatro llamadas de
   `fetchSimpleManualCandidate()` escribían por error en el de H08.
10. **Bug real, encontrado por Miguel Ángel probando en dispositivo**:
    temas sin relación con el ancla colándose a mitad de sesión
    (Soundgarden dentro de una sesión de "minimal techno"). Causa: los
    `QueueItem` de `fetchSimpleManualCandidate()` nunca marcaban
    `isFromRadio = true` -- el flag que distingue "esto lo añadió la
    propia Radio" de "pista propia nueva del usuario". Sin él, al
    llegar al final de la cola el código reseteaba el ancla entera y
    caía en el motor automático de H08, anclado en lo último que
    sonaba. Corregido con `.copy(isFromRadio = true)` en los tres
    retornos de la función.

Miguel Ángel lo está probando en dispositivo real ahora mismo --
"parece que va bien" tras el arreglo del punto 10.

## COMPLETADAS EN S031 (incidencia real, H15 PAUSADO -- H17 es el hito EN PROGRESO)

Miguel Ángel compartió `mimooutcast_debug.txt` de una sesión real de
verificación mientras H17 estaba activo. Un hallazgo real, ajeno a
H17 pero en código compartido con H08 (Radio):

11. **Falso positivo de artista en `RadioRepository.stripTitleNoise()`**
    -- caso real del log: sesión de "Minimal Techno" resolvió `'Teste'
    - 'MUUD - Testē'`, presentando como tema de "Teste" un vídeo que en
    realidad es de otro artista, MUUD. Causa: el segmento anterior al
    primer " - " del título del vídeo se quitaba SIEMPRE, asumiendo
    que era el propio artista repetido, sin comprobar nunca que
    coincidiera de verdad con el artista buscado -- con "MUUD -
    Testē", la limpieza ciega descartaba "MUUD" y dejaba "Testē",
    texto que por casualidad se parece a "Teste" y que
    `firstReleaseYearFromMusicBrainz()` terminaba "confirmando".
    Arreglado: `stripTitleNoise()` ahora recibe el artista y solo
    quita el segmento inicial si coincide de verdad con él (mismo
    criterio que `SearchNormalizer.songTitleKey()`/`cleanSongTitle()`,
    H17). Build verde (`9328658`). Comparte código con H08 -- el fix
    corrige la verificación en ambos hitos. Sin verificar en
    dispositivo real todavía (H15 sigue pausado).

## COMPLETADAS EN S032 (incidencia real, H15 PAUSADO -- H18 es el hito EN PROGRESO)

Miguel Ángel compartió `mimooutcast_debug.txt`/`radio_relacionados_debug.txt`
de sesiones reales. Dos incidencias reales en el mismo bloque, ambas
**EXCLUSIVAS de miMooutCast** -- ni una sola línea de la Radio
automática (H08) se ha tocado:

12. **`fetchSimpleManualCandidate()` usaba `radioUsedArtists`
    (preferencia BLANDA, de toda la sesión, pensada para la Radio
    automática) como si fuera la regla de no-repetición de artista de
    miMooutCast.** Orden explícita y repetida de Miguel Ángel
    (2026-08-07), tras un malentendido sobre qué papel juegan
    "biblioteca local"/cupos en este modo (ninguno -- la única
    pregunta es si el tema encaja con el ancla elegida): *"la única
    regla es no repetir tema jamás y no repetir artista en una
    ventana de 10 temas"*. Arreglado con `miMooutCastRecentArtists`
    (nuevo, `ArrayDeque` FIFO de tamaño máximo 10, `PlayerManager.kt`)
    -- ventana DURA e independiente de `radioUsedArtists`, que sigue
    intacta para la Radio. Las tres fuentes de
    `fetchSimpleManualCandidate()` (dictionario, MusicBrainz,
    biblioteca local) comparten ahora el mismo veto explícito contra
    esta ventana, sin que ninguna fuente valga más que otra.
13. **Bug real de fondo, mismo log: una sesión anclada en "Minimal
    Techno" sirvió un único tema (Charlotte Bendiks) y se quedó muda
    para siempre, sin ningún aviso en pantalla.** Causa:
    `topUpRadioQueueIfNeeded()` hacía `break` sin más en cuanto
    `fetchOneRadioTrack()` devolvía `null` -- sin distinguir "de
    verdad no queda nada de este género en ninguna fuente" de
    cualquier otro motivo. Como el ancla en miMooutCast nunca se
    relaja (regla del punto 12), un agotamiento real no tiene ninguna
    alternativa que ofrecer -- la única opción honesta es avisar,
    igual que ya hace `radioNetworkLost` para la pérdida de conexión.
    Nuevo `PlaybackState.miMooutCastAnchorExhausted: String?` (guarda
    la etiqueta del ancla agotada), publicado solo cuando
    `manualAnchorActive && !radioNetworkLost` (para no confundirlo con
    el aviso de sin conexión, que ya tiene el suyo propio). Diálogo
    nuevo en `PlayerBar.kt` ("Sin más música... prueba con otra
    combinación"), sin reintento automático (`dismissMiMooutCastAnchorExhausted()`
    -- a diferencia de `dismissRadioNetworkLost()`, reintentar aquí
    solo repetiría el mismo aviso de inmediato, ya que el ancla no
    cambia sola).
14. **BUG REAL, cerrado: `resolveAnchorWithFallbacks()` (la cascada de
    Radio para fijar un artista-ancla) se disparaba durante una sesión
    de miMooutCast activa.** Miguel Ángel reportó "se nos mete Radio y
    no funciona nada" con un log real (`radio_relacionados_debug__1_.txt`);
    confirmado línea a línea: a las 20:41-20:46, en medio de una
    sesión `miMooutCast: Años 80`, aparecen tres llamadas a
    `resolveAnchor()` con nombres sueltos sin relación con la década
    1980 ('Beethoven, Ludwig van', 'Juan Carlos Carrillo', 'Deep
    Purple Official'). Diagnóstico añadido primero (sin cambiar
    comportamiento) para localizar el disparador exacto; Miguel Ángel
    señaló la causa antes de que hiciera falta: el bloque de "nueva
    sesión" en `onMediaItemTransition()` no comprobaba
    `manualAnchorActive` en absoluto -- solo miraba si el ítem que
    arranca lleva `isFromRadio`/`isRadioStation`, y ese chequeo por sí
    solo no bastaba para blindarlo contra una sesión de miMooutCast en
    marcha. Orden explícita: *"hay que deshabilitar la radio cuando se
    hace el minuscast"*. Corregido añadiendo `!manualAnchorActive`
    como condición explícita del bloque -- mientras miMooutCast está
    activo, este detector de "el usuario acaba de arrancar una pista
    propia" queda completamente inerte, sin depender de que
    `isFromRadio` llegue siempre a tiempo. Diagnóstico de
    `onMediaItemTransition()` retirado (inalcanzable ya, protegido por
    el guardián); el de `clearQueue()` se mantiene, por si ese es el
    otro punto de entrada real. Sin verificar en dispositivo real
    todavía.
15. **BUG REAL, cerrado: el propio archivo `radio_relacionados_debug.txt`
    se actualizaba durante sesiones de miMooutCast, aunque el bug del
    punto 14 ya estuviera corregido.** Causa real, no cosmética:
    `resolveYoutubeCandidate()` (compartida, `PlayerManager.kt`) y
    `RadioRepository.suggestRelatedArtist()`/`verifyTrackExists()`/
    `ensureDiscographyCached()`/`resolveAnchor()` (compartidas,
    `RadioRepository.kt`) usan legítimamente el mismo mecanismo de
    exploración de MusicBrainz para el tier 2 de
    `fetchSimpleManualCandidate()`, y todas escribían siempre a
    `RadioDebugLogger`/`radio_relacionados_debug.txt`, con
    independencia de si las llamaba Radio o miMooutCast --
    `AnchorDictionary` (temas.json/pendientes.json, también
    compartido) hacía lo mismo. Orden explícita y repetida de Miguel
    Ángel, tras varias rondas de confusión real por esto: *"el debug
    de radio relacionados no tiene por qué actualizarse cuando se está
    usando miMooutCast"* -- sin excepción, sea cual sea el motivo
    interno de la llamada compartida.

    Corregido con una señal compartida mínima nueva,
    `MiMooutcastSessionFlag` (`@Singleton`, `data/playback/`):
    `PlayerManager.manualAnchorActive` deja de ser un booleano propio
    y pasa a DELEGAR en `mimooutcastSessionFlag.active` como única
    fuente de verdad (evita que dos banderas separadas puedan
    desincronizarse). `RadioRepository`/`AnchorDictionary` reciben
    esta misma señal inyectada (inyectar `PlayerManager` directamente
    en ellas habría creado una dependencia circular) y centralizan su
    log en una función `log()`/`sharedResolveLog()` que enruta a
    `MimooutcastDebugLogger` o `RadioDebugLogger` según el valor de la
    señal. Con el motor de cupos de Radio (`fetchRoundCandidate()`) y
    `resolveAnchorWithFallbacks()` ya bloqueados en seco por el punto
    14, en la práctica `radio_relacionados_debug.txt` no debería
    recibir ni una sola línea mientras miMooutCast está activo.

    **Corrección real tras la primera verificación de Miguel Ángel**
    (capturas de pantalla, tamaño de `radio_relacionados_debug.txt`
    creciendo unos bytes durante una sesión de miMooutCast): el
    arreglo de arriba no cubría `topUpRadioQueueIfNeeded()` ni
    `fetchOneRadioTrack()` -- las dos funciones compartidas que
    envuelven a `fetchRoundCandidate()`/`fetchSimpleManualCandidate()`
    y que también llamaban a `RadioDebugLogger.log()` directamente en
    varios de sus propios puntos de control (backlog lleno, sin
    artista ancla, agotamiento total, sin red al fijar década,
    excepción general) -- estas SÍ se ejecutan igual en ambos modos,
    así que sus logs directos escapaban al enrutador. Las cinco
    llamadas sueltas de estas dos funciones pasadas también a
    `sharedResolveLog()`.
16. **BUG REAL DE FONDO, corregido: `fetchSimpleManualCandidate()`
    probaba primero el diccionario de éxitos
    (`knownHitsRepository.randomHit()`) -- un concepto de la Radio
    automática (curado con música POPULAR) que nunca tuvo ningún papel
    en miMooutCast.** Esto explica por qué Minimal Techno/Schranz/
    Bulería no encontraban apenas nada (esos géneros no existen en un
    diccionario pensado para éxitos mainstream, así que siempre caían
    al peldaño 2), pero el problema de fondo era más grave: incluso
    para anclas donde el diccionario SÍ tenía contenido (Años 80/90),
    estaba sesgando lo que sonaba hacia "lo que es un éxito conocido"
    en vez de "lo que encaja con el ancla", exactamente lo contrario
    de la regla. Orden explícita y repetida de Miguel Ángel, hasta
    agotar la paciencia: *"esto no tiene nada que ver con la radio...
    aquí no hay éxitos, que aquí no hay cuota... lo único que hay que
    hacer es cumplir el ancla, y el ancla solamente va por una cosa: o
    género, o década, o origen. Y ya está. No repetir temas y no
    repetir artista en una ventana de diez temas."*

    Quitado el peldaño del diccionario de `fetchSimpleManualCandidate()`
    por completo. Solo quedan dos fuentes, renumeradas: (1) MusicBrainz
    en vivo (`suggestRelatedArtist()`/`resolveYoutubeCandidate()`), (2)
    biblioteca local (`pickDiscoCandidate()`) -- las dos comprueban
    género/década/origen contra metadatos reales, sin ningún filtro de
    "es conocido". **El diccionario sigue existiendo tal cual en
    `fetchRoundCandidate()` (Radio automática) -- Miguel Ángel confirmó
    explícitamente que ahí sí debe seguir.** Sin verificar en
    dispositivo real todavía.
17. **Auditoría completa a petición de Miguel Ángel ("no voy a instalar
    ninguna build hasta que me asegures que el código de miMooutCast
    no tiene absolutamente nada que ver con el de la radio").**
    Rastreado el camino de código completo desde
    `startRadioFromManualAnchor()` hasta el final, función por función.
    Dos hallazgos reales más, de la misma clase que los puntos 12-16
    (variables de sesión compartidas sin necesidad):
    - `startRadioFromManualAnchor()` y `topUpRadioQueueIfNeeded()`
      escribían en `radioUsedArtists` (exclusiva de la Radio) incluso
      durante una sesión de miMooutCast, aunque
      `fetchSimpleManualCandidate()` nunca la lee -- no cambiaba nada
      del comportamiento (ambas rutas de entrada, `clearQueue()` para
      miMooutCast y el bloque de "nueva sesión" para la Radio natural,
      limpian `radioUsedArtists` antes de usarla, así que no había
      contaminación real posible entre sesiones), pero sí era código
      que "tenía que ver con la radio" sin necesidad. Quitado --
      miMooutCast ya solo escribe en `miMooutCastRecentArtists`.
    - Dos comentarios (KDoc) desactualizados que todavía describían el
      orden "dictionario -> MusicBrainz -> biblioteca local" tras
      haberse quitado el diccionario en el punto 16 -- corregidos para
      que no describan un comportamiento que ya no existe.

    Confirmado con grep exhaustivo sobre el archivo completo: la única
    llamada real a `knownHitsRepository.randomHit()` (el diccionario)
    que queda en todo `PlayerManager.kt` está dentro de
    `fetchRoundCandidate()`; todas las llamadas a `RadioDebugLogger.log()`
    que quedan caen dentro de `resolveAnchorWithFallbacks()`/
    `fetchRoundCandidate()` (bloqueadas en seco para miMooutCast) o de
    sus funciones auxiliares exclusivas (`exhaustPortion()`,
    `fetchFromUnknown()`, `resolveFinalFallback()`, todas solo
    invocadas desde dentro de `fetchRoundCandidate()`). Sin verificar
    en dispositivo real todavía.
18. **BUG REAL DE FONDO, encontrado con evidencia fechada: las
    corrutinas de reposición de una sesión ANTERIOR nunca se
    cancelaban al arrancar una sesión nueva.** Miguel Ángel confirmó
    tras instalar el build con fecha en los logs (punto 17): el log
    `mimooutcast_debug__4_.txt` mostraba `resolveAnchor('Beethoven,
    Ludwig van')`/`resolveAnchor('Juan Carlos Carrillo')` con fecha de
    HOY, en medio de una sesión de "Minimal Techno". Descartado que
    fuera el guardián de los puntos 14/17 fallando -- confirmado con
    grep exhaustivo que `resolveAnchor()` solo tiene un llamante real
    en todo el proyecto (`resolveAnchorWithFallbacks()`, bloqueada en
    seco). Causa real: `isRadioTopUpRunning` era solo un booleano de
    reentrancia -- nunca cancelaba la corrutina de
    `topUpRadioQueueIfNeeded()` de una sesión ANTERIOR (posiblemente
    una Radio normal usada antes en la misma sesión de app) si esta
    seguía viva, atascada reintentando contra MusicBrainz caído (503
    en cadena, visto en el mismo log). Esa corrutina vieja seguía
    corriendo de fondo después de `clearQueue()`/una nueva sesión,
    generando exactamente estas líneas sueltas -- y de paso, con
    `isRadioTopUpRunning` todavía en `true` hasta que ella misma
    terminara, bloqueaba el relleno de la sesión NUEVA mientras tanto
    (encaja con "se pega un rato" antes de encontrar el primer tema).

    Corregido con `topUpJob: Job?` (nuevo campo), que guarda la
    corrutina de `topUpRadioQueueIfNeeded()` y se cancela
    explícitamente (`topUpJob?.cancel()` + `isRadioTopUpRunning =
    false`) en los DOS puntos donde arranca una sesión nueva --
    `clearQueue()` y el bloque de "nueva sesión" de
    `onMediaItemTransition()`. Aplica por igual a Radio y a miMooutCast
    -- cualquier corrutina de la sesión que se abandona se cancela,
    sea cual sea el modo. Sin verificar en dispositivo real todavía.
19. **Orden explícita de Miguel Ángel tras insistir en que la búsqueda
    en biblioteca local no era la causa real: "sigue mirando a ver qué
    es lo que falla... vamos a ver cómo estamos buscando en
    YouTube".** Quitada la biblioteca local de miMooutCast por
    completo (`pickDiscoCandidate()` fuera de
    `fetchSimpleManualCandidate()` -- streaming puro, más rápido). Y
    arreglo real de fondo en `resolveYoutubeCandidate()` (compartida
    con Radio): con dos casos reales del propio log ('Shed' -- nombre
    real de un productor de minimal techno, también la palabra inglesa
    "cobertizo"; 'Scuba' -- ídem, "submarinismo"), la búsqueda "solo
    artista" (sin canción conocida) mandaba a YouTube el nombre pelado
    a secas -- de los 20 resultados, ninguno era música, todos vídeos
    de bricolaje o de buceo. Nuevo parámetro `genreHint` en
    `resolveYoutubeCandidate()`: cuando no hay canción conocida, añade
    el género del ancla a la consulta ("Shed minimal techno" en vez de
    "Shed" a secas) para desambiguar. Aplicado en los tres puntos de
    llamada "solo artista" -- `fetchRoundCandidate()` (Exploración),
    `fetchFromUnknown()`/`resolveFinalFallback()` y
    `fetchSimpleManualCandidate()` -- beneficia a Radio y a miMooutCast
    por igual, sin tocar ningún filtro de exactitud
    (`verifyTrackExists()` sigue exigiendo lo mismo de siempre).
20. **BUG REAL DE FONDO, el más grave de todos: un solo candidato
    fallido bastaba para declarar el ancla entera agotada.**
    `fetchSimpleManualCandidate()` probaba UN solo artista por llamada
    (`suggestRelatedArtist()` + `resolveYoutubeCandidate()`); si ese
    fallaba, devolvía `null` -- y `topUpRadioQueueIfNeeded()`
    interpreta CUALQUIER `null` como agotamiento TOTAL, mostrando el
    aviso "Sin más música". Caso real, verificado en el log con fecha:
    sesión "Minimal Techno" encontró y añadió 'Altinbas - Drifting
    Figures' (con el `genreHint` del punto 19 funcionando), probó
    'Marco Carola' a continuación -- 0 de 20 resultados de YouTube
    pasaron el filtro -- y con ESE ÚNICO fallo se declaró el ancla
    agotada "de verdad", sin haber probado ni una fracción de los
    otros 23+ candidatos que `suggestRelatedArtist()` ya tenía en su
    propia bolsa de 25. Miguel Ángel, tras ver el patrón repetirse:
    *"si seguimos buscando con el mismo código, podemos estar así
    hasta el 3052... el código no sirve, hay que buscar otra
    estrategia."*

    Mismo patrón que `resolveFinalFallback()`/`UNKNOWN_CANDIDATE_ATTEMPTS`
    ya usa para la Radio automática (S027) -- para miMooutCast no
    existía ningún reintento equivalente hasta ahora. Nueva constante
    `MIMOOUTCAST_CANDIDATE_ATTEMPTS = 8`:
    `fetchSimpleManualCandidate()` reescrita con un `repeat(8)` que
    prueba hasta ocho candidatos DISTINTOS antes de rendirse -- cada
    fallo se añade a la ventana de exclusión SOLO para esa llamada
    (`triedThisCall`), para que `suggestRelatedArtist()` no vuelva a
    sugerir el mismo artista que ya falló.
21. **Corrección inmediata del propio Miguel Ángel al ver el punto 20:
    "cuando lleguemos a los 25 candidatos, también muere... no son 25
    candidatos, hay cientos de miles de temas de minimal techno."**
    Con razón: `suggestRelatedArtist()` sin `offset` explícito consulta
    siempre la MISMA región del catálogo (solo con un pequeño
    desplazamiento aleatorio interno de `findCandidates()`, 0-90) --
    el `repeat(8)` del punto 20 probaba ocho veces dentro de esa misma
    región estrecha, no ocho regiones distintas. La Radio automática
    ya tenía resuelto este mismo problema desde S025
    (`radioUnknownOffset`, con su propio comentario: *"MusicBrainz
    tiene dos millones de artistas... la exploración NO se agota
    nunca"*) -- miMooutCast nunca heredó ese mecanismo.

    Nuevo `miMooutCastOffset` (campo de sesión, EXCLUSIVO de
    miMooutCast, nunca toca `radioUnknownOffset`), reseteado a 0 en
    los mismos dos puntos que su equivalente de Radio
    (`clearQueue()`, bloque de "nueva sesión" de
    `onMediaItemTransition()`). `fetchSimpleManualCandidate()` ahora
    pasa `offset = miMooutCastOffset` a `suggestRelatedArtist()` y lo
    avanza en `MIMOOUTCAST_PAGE_SIZE` (25, mismo tamaño que
    `UNKNOWN_PAGE_SIZE`) cada vez que una página no da nada
    aprovechable -- tanto dentro de los 8 intentos de una misma
    llamada como entre llamadas sucesivas de la misma sesión, sin
    reiniciarse nunca hasta el final real de la sesión. El ancla ya no
    se declara agotada tras rebuscar en el mismo rincón estrecho del
    catálogo una y otra vez -- avanza de verdad por los cientos de
    miles de temas reales que existen. Sin verificar en dispositivo
    real todavía.

22. **"Década sola" (sin género ni origen) no tenía NINGUNA fuente
    posible en streaming puro.** Descubierto al probar "Años 90" sin
    origen seleccionado: `suggestRelatedArtist()` devuelve `null`
    limpio por diseño cuando no hay género ni origen (MusicBrainz no
    tiene un campo fiable de "década de actividad" a nivel de
    artista) -- antes esto lo salvaba la biblioteca local (quitada en
    el punto 20), así que década sola se quedó sin ninguna fuente
    posible. Miguel Ángel, tras confirmar que era década sola de
    verdad: *"sí es posible hacerlo con streaming puro... en vez de
    'dame un artista de este género' hay que preguntar 'dame una
    grabación publicada en esta década'."*

    Nueva `RadioRepository.suggestArtistFromDecade()`: busca
    directamente por FECHA DE PRIMERA EDICIÓN del release-group
    (`firstreleasedate:[decadaInicio-01-01 TO decadaFin-12-31]`) en
    vez de por artista, y extrae el artista del `artist-credit` de los
    resultados (campo nuevo en el DTO `MusicBrainzReleaseGroup`,
    reutilizando `MusicBrainzArtistCredit` ya existente). Paginación
    con el mismo `miMooutCastOffset`/`MIMOOUTCAST_PAGE_SIZE` del punto
    21 -- tampoco se agota nunca. `fetchSimpleManualCandidate()`
    detecta el caso "década sola" (`genre.isBlank() && originGroup ==
    null && decadeBegin != null`) y usa esta función en vez de
    `suggestRelatedArtist()`.

    **AVISO EXPLÍCITO, no ocultado: el nombre del campo
    `firstreleasedate` no se ha podido verificar contra la API en
    vivo** (bloqueada por robots.txt en el entorno de Claude, ni
    `web_fetch` ni `bash_tool` consiguen alcanzar `musicbrainz.org`).
    Se basa en que el campo DEVUELTO por la búsqueda es
    `first-release-date` (confirmado contra la documentación oficial)
    y en la convención de MusicBrainz de quitar guiones entre campo
    devuelto y campo buscable -- razonable, pero no probado. El log
    de `suggestArtistFromDecade()` deja constancia clara de cuántos
    release-groups trae la respuesta cruda en cada llamada, para poder
    distinguir de un vistazo "la sintaxis del campo está mal" (0
    release-groups siempre) de "de verdad no hay nada en esta página"
    (release-groups > 0 pero sin artist-credit útil).

    **CONFIRMADO CON LOG REAL (2026-08-08): el campo `firstreleasedate`
    es correcto.** `suggestArtistFromDecade(década=1990, offset=0)`
    devolvió 25 release-groups reales en la primera llamada, no una
    lista vacía -- ya no es una apuesta, es un hecho verificado.

    **Refinamiento en el mismo log**: el primer artista devuelto ('The
    Spectres') tenía un disco real de 1990, pero el grueso de su
    discografía es de los 60-80 -- al pasar solo el nombre del artista
    (sin canción conocida), `resolveYoutubeCandidate()` gastaba
    intentos enteros descartando temas suyos de décadas ajenas antes
    de acertar por casualidad. Corregido: `suggestArtistFromDecade()`
    ahora devuelve `DecadeCandidate(artist, title)` -- el TÍTULO del
    disco concreto que tiene la fecha correcta, no solo el artista --
    y `fetchSimpleManualCandidate()` lo pasa como canción conocida a
    `resolveYoutubeCandidate()`, con la misma precisión de "artista +
    canción" que el resto del proyecto usa en cualquier otro punto.
    Sin verificar en dispositivo real todavía.

23. **Metodología nueva de Miguel Ángel: describir lo que hace un DJ
    humano ("me dicen 'pincha minimal techno', miro en Google, cojo el
    PRIMER resultado...") y depurar el sistema contra ese patrón.**
    Comparado punto por punto contra el código real: un solo fallo
    real confirmado -- todo el sistema elegía candidato con
    `.randomOrNull()` (al azar entre toda la página), nunca "el
    primero" como haría un humano confiando en el orden de relevancia
    de la búsqueda. El resto de la lista de un DJ humano (validar en
    YouTube que el tema es real, construir la cola sobre la marcha, no
    repetir artista en 10 temas, no repetir tema+artista aunque cambie
    la versión pero sí permitir el mismo tema por artista distinto,
    nunca usar el nombre del canal) ya estaba correctamente cumplido.

    Diseño de Miguel Ángel para el hueco encontrado, confirmado en dos
    rondas antes de tocar código: ventana CRECIENTE de candidatos entre
    los que elegir al azar -- 5 en el primer tema de la sesión, 10 en
    el segundo, 15, 20, 25... subiendo de 5 en 5 sin techo fijo, hasta
    el tope real de lo que MusicBrainz devuelva para ese ancla. Así el
    primer tema de cada sesión sigue siendo variado entre sesiones (no
    siempre el mismo top-1), y la aleatoriedad real crece según avanza
    la propia sesión.

    Implementado con un contador de sesión nuevo,
    `miMooutCastTracksServed` (mismos dos puntos de reseteo que
    `miMooutCastOffset`), y `currentMiMooutCastWindow()` = `(temas
    servidos + 1) * 5`. Nuevo parámetro `resultWindowLimit: Int?` en
    `suggestRelatedArtist()` y en `suggestArtistFromDecade()` -- `null`
    (valor por defecto) preserva el comportamiento de siempre de la
    Radio automática sin ningún cambio; miMooutCast pasa la ventana
    calculada, que limita tanto cuántos candidatos se piden a
    MusicBrainz como cuántos de los devueltos entran en el sorteo
    final (los primeros N según el orden de relevancia que ya trae la
    API, no un recorte a ciegas). `ENOUGH_CANDIDATES`/`ANCHOR_SEARCH_LIMIT`
    siguen siendo los valores por defecto de la Radio, intactos.

    **De paso, arreglado algo que Miguel Ángel ya había señalado y
    seguía sin tocar**: `suggestRelatedArtist()` consultaba primero
    `AnchorDictionary` (base local aprendida de sesiones anteriores,
    sin red) antes de ir a MusicBrainz en vivo -- correcto y deseado
    para la Radio automática (orden explícita y distinta de S025), pero
    exactamente el "local" que Miguel Ángel prohibió para miMooutCast
    ("todo en streaming"). Nuevo parámetro `useLocalDictionary: Boolean`
    -- `true` por defecto (Radio sin cambios), miMooutCast pasa
    `false` explícitamente y salta ese bloque entero. Sin verificar en
    dispositivo real todavía.

24. **EXCLUSIVO DE DÉCADA -- Miguel Ángel fue explícito: "vamos a hacer
    exactamente el mismo razonamiento, esta vez para las décadas.
    Espero que no lo extrapoles por tu cuenta a absolutamente nada
    más."** El punto 23 (ventana creciente) se había aplicado también
    a década sin que se pidiera para ahí -- mismo error que el
    diccionario local del punto 23, mismo patrón que Miguel Ángel ya
    había señalado antes. No se revierte (orden explícita: *"quién te
    ha dicho que pierdas el tiempo en revertir... quieres estarte
    quieto"*), pero este punto 24 es un cambio nuevo, acotado solo a
    década, con su propio permiso explícito.

    Log real (`Años 90`, 2026-08-08 21:59-22:00): el rango de década
    completo (`firstreleasedate:[1990-01-01 TO 1999-12-31]`) combinado
    con la ventana creciente del punto 23 usada como límite de la
    PETICIÓN a MusicBrainz (no solo de la selección) provocó 16
    intentos seguidos con 0 artistas encontrados -- páginas de solo 5
    discos al principio de sesión, demasiado pequeñas, con el offset
    saltando de 25 en 25 por encima sin explorar lo saltado.

    Propuesta de Miguel Ángel, aceptada tal cual: *"si tenemos un
    tema+artista de referencia en un año del medio de la década (1965,
    1975... 1995...) y buscamos relacionados con ese tema comprobando
    ±5 años"* -- confirmado explícitamente que es SOLO por año, sin
    ninguna noción de parecido/género. `suggestArtistFromDecade()`
    ahora consulta `firstreleasedate:AÑO_CENTRAL` (año exacto, no
    rango) -- más estrecha, resultados más claramente "de esa época".
    La comprobación de ±5 reutiliza el mecanismo YA EXISTENTE
    `expectedYear`/`yearWindow` de `resolveYoutubeCandidate()` (el
    mismo que usa la Radio automática al anclar en un tema concreto,
    S027) en vez de inventar uno nuevo -- `fetchSimpleManualCandidate()`
    pasa `expectedYear = añoCentral, yearWindow = 5` solo en la rama de
    década sola; género/origen siguen usando `expectedDecadeBegin`
    exactamente como antes, sin tocar.

    De paso, separado (dentro de esta misma función, sin tocar
    `suggestRelatedArtist()`) el bug real del punto 23: `limit` de la
    petición a MusicBrainz vuelve a ser siempre
    `MIMOOUTCAST_DECADE_PAGE_SIZE` fijo; `resultWindowLimit` ahora solo
    recorta cuántos de los ya devueltos entran en el sorteo. Sin
    verificar en dispositivo real todavía.

25. **CAUSA REAL, por fin, de "las sesiones siguen activas por debajo"
    -- confirmada con log real, no supuesta.** Miguel Ángel, con toda
    la razón tras varios intentos previos que no lo resolvían del
    todo: *"lo de que las sesiones siguen activas por debajo ya no
    cuantas veces lo has arreglado ya he perdido la cuenta."* El
    arreglo del punto 18 (`topUpJob?.cancel()`) SÍ funcionaba -- la
    prueba es que en el log aparece `JobCancellationException:
    StandaloneCoroutine was cancelled`, la señal de que la cancelación
    se disparaba de verdad. El problema real estaba un paso más allá:
    **`findCandidates()` (y otras 19 funciones más en los dos
    archivos) atrapaban esa excepción con un `catch (e: Exception)`
    genérico**, que en Kotlin también captura `CancellationException`
    -- y en vez de dejarla propagarse (que es lo que de verdad para
    una corrutina), la trataban como un fallo más: "0 candidatos,
    reintento". Log real, sesión "Minimal Techno" cambiada a "Años 90"
    a las 10:16:07: la sesión vieja de Minimal Techno siguió
    reintentando 8 veces más, 20 segundos, con `JobCancellationException`
    en cada intento, sin llegar a pararse nunca de verdad.

    **No es un fallo exclusivo de miMooutCast** -- es un error de
    manejo de excepciones que rompe la cancelación cooperativa en
    cualquier corrutina, Radio incluida, y por eso se corrige en los
    20 sitios donde aparece el patrón (`RadioRepository.kt`: 15,
    `PlayerManager.kt`: 5), no solo en el camino de miMooutCast --
    orden explícita de Miguel Ángel de no volver a pedirle alcance
    para esto: *"no es una elección de diseño... es una cosa que está
    simplemente mal hecha en todos los sitios donde aparece."* Cada
    `catch (e: Exception)` ahora tiene delante un
    `catch (e: CancellationException) { throw e }` que la relanza sin
    tocarla -- patrón estándar de Kotlin para no romper la cancelación
    cooperativa. Import de `kotlinx.coroutines.CancellationException`
    añadido en ambos archivos. Sin verificar en dispositivo real
    todavía.

    **Nota aparte, sin cerrar:** en el mismo log, "The Supremes"
    (grupo real de Motown de los 60) acabó sirviendo un "2019 Tour
    Trailer" -- claramente no un tema real de los 90, un fallo de
    calidad en la verificación que queda pendiente de mirar, distinto
    de la cancelación.

26. **Objetivo cuantitativo nuevo de Miguel Ángel, con vía libre de
    implementación: "todo lo que sea tardar más de 10 segundos en
    encontrar un tema lo voy a considerar fracaso."** Medido contra su
    propio log real: sesión "Hard Rock" tardó 18,5s en confirmar
    'Megadeth' (incluye un `retryOnceIfTransient()` de por medio),
    sesión "Minimal Techno" tardó 11,8s con 'Christian Morgenstern' --
    las dos por encima del límite. Solo "Hispanoamérica" (7,2s) lo
    cumplía.

    Causa real: `verifyTrackExistsForArtist()` consultaba MusicBrainz,
    Discogs y Wikidata **en cadena** -- cada una con su propio
    reintento de 1,5s si fallaba, y no se empezaba la siguiente fuente
    hasta que la anterior hubiera terminado del todo (éxito, fallo, Y
    su reintento). Con tres fuentes potencialmente lentas en serie,
    los tiempos se suman en el peor caso.

    Corregido: las tres arrancan A LA VEZ (`kotlinx.coroutines.async`)
    dentro de un `coroutineScope`, y se espera a las tres con
    `.await()` -- el tiempo total pasa a depender de la MÁS LENTA de
    las tres, no de la SUMA. El orden de prioridad para decidir qué
    respuesta vale si varias confirman (MusicBrainz > Discogs >
    Wikidata) se mantiene exactamente igual que antes, solo cambia que
    ya no se espera a que cada una termine para lanzar la siguiente.
    La comprobación de "sin red real" (antes repetida tras cada
    fuente, en cadena) pasa a hacerse una sola vez, con el resultado
    conjunto de las tres.

    Acotado a `verifyTrackExistsForArtist()`, que es la que está en el
    camino crítico de encontrar cada tema de miMooutCast --
    `resolveOriginalDecade()` (mismo patrón secuencial, pero usado por
    la Radio automática al fijar su propio ancla desde una pista
    real, no en el bucle de candidatos de miMooutCast) se deja sin
    tocar. Sin verificar en dispositivo real todavía -- pendiente
    confirmar que los tiempos bajan de verdad de los 10 segundos con
    un log nuevo.

27. **Confirmado por Miguel Ángel ("Sí") y pendiente de implementar
    desde entonces -- ahora sí.** Bug real con timestamps: `clearQueue()`
    a las 11:33:15.474 (botón "vaciar cola"), y 200ms después,
    `topUpRadioQueueIfNeeded() -- parado: no hay artista ancla... se
    pregunta al usuario` -- disparado automáticamente por el cambio de
    estado del player, no por ninguna acción del usuario, cayendo en
    la lógica de continuación de la Radio aunque lo vaciado fuera una
    sesión de miMooutCast.

    Nuevo parámetro `clearQueue(stayStopped: Boolean = true)` --
    vaciar la cola SIEMPRE deja el reproductor parado del todo por
    defecto, sin intentar continuar nada, ni para miMooutCast ni para
    la Radio (un vaciado explícito no tiene "después" que planificar
    en ningún caso). `startRadioFromManualAnchor()` pasa
    `stayStopped = false` explícitamente, porque esa llamada concreta
    es la preparación de una sesión NUEVA que sí sigue buscando justo
    después. Nuevo campo `radioStayStopped`, comprobado al principio
    de `topUpRadioQueueIfNeeded()`; se resetea también en el bloque de
    "nueva sesión" de `onMediaItemTransition()` (una pista propia
    nueva del usuario siempre puede continuar con normalidad). Sin
    verificar en dispositivo real todavía.

28. **Botón nuevo, transversal, pedido por Miguel Ángel:** *"activar o
    desactivar las listas de éxitos españolas y comparar contra estas
    listas de éxitos. En géneros nicho la desactivamos para tener
    candidatos, y en décadas como los 90 o en géneros como hard rock,
    podemos activar conocido en España."* Distinto del diccionario
    quitado en el punto 16: aquello era una FUENTE prioritaria
    (probar primero); esto es un FILTRO opcional que se puede
    encender o apagar, y que se aplica DESPUÉS de encontrar un
    candidato real por streaming, no en su lugar.

    Nuevo toggle "Conocido en España" en `MimooutcastScreen.kt`,
    encima de las pestañas (transversal de verdad -- afecta a género,
    década y origen por igual, no a una pestaña concreta). `false` por
    defecto -- streaming puro, sin cambios en el comportamiento visto
    hasta ahora. `MimooutcastUiState.requireKnownInSpain` +
    `toggleRequireKnownInSpain()`, pasado a
    `PlayerManager.startRadioFromManualAnchor(..., requireKnownInSpain)`
    y guardado en `miMooutCastRequireKnownInSpain` (campo de sesión).

    `fetchSimpleManualCandidate()` comprueba el filtro justo después
    del veto de ventana y ANTES de `resolveYoutubeCandidate()` --
    rechazar un candidato que no es "conocido en España" no debe
    gastar tiempo en YouTube/verificación para descartarlo después,
    tiene que cortar antes. Reutiliza
    `KnownHitsRepository.isKnownArtistAnywhere()`, ya existente (S026,
    salvaguarda de Hispanoamérica) -- compara contra `es` + `intl` del
    diccionario de éxitos, éxitos EN ESPAÑA sean o no artistas
    españoles, nunca "cualquier tema del Billboard sin más". Un
    candidato rechazado por este filtro avanza el offset igual que
    cualquier otro rechazo, para no rebuscar en la misma región
    estrecha del catálogo. Sin verificar en dispositivo real todavía.

29. **Corrección real, misma sesión, del punto 28: el botón "Conocido
    en España" casi no encontraba nada.** Log real: sesión "Años 90"
    con el interruptor encendido encontró 9 artistas reales distintos
    vía MusicBrainz (Ugly Kid Joe, Sleep Chamber, Slapstick, UHF,
    Barricada, Necrosis, NOFX, Dodgy, Joey Calderazzo...) y **solo
    uno** ('Nena') pasó el filtro de `isKnownArtistAnywhere()` --
    agotado tras 8 intentos, "Sin más música" en pantalla. El
    planteamiento del punto 28 era al revés de como tenía que ser:
    buscar un artista al azar en TODO MusicBrainz y comprobar DESPUÉS
    si por casualidad está en una lista de éxitos pequeña y curada es
    buscar una aguja en un pajar -- la probabilidad de acertar por
    azar es minúscula.

    Corregido de raíz: con el interruptor encendido, la FUENTE del
    candidato pasa a ser directamente `KnownHitsRepository.randomHit()`
    -- el mismo mecanismo que usa la Radio automática, con género,
    década y origen del ancla -- en vez de `suggestRelatedArtist()`/
    `suggestArtistFromDecade()` (MusicBrainz) con un filtro a
    posteriori. Cada resultado del diccionario YA es "conocido en
    España" por construcción, así que no hace falta ningún filtro
    después -- se quita el `isKnownArtistAnywhere()` del punto 28, que
    ya no pinta nada aquí. Con el interruptor apagado (el caso por
    defecto, y todo lo probado hasta el punto 28), el comportamiento
    es exactamente el de siempre -- streaming puro, sin tocar. Sin
    verificar en dispositivo real todavía.

30. **BUG REAL DE VELOCIDAD, el más grave medido hasta ahora: 4
    minutos 19 segundos para UN SOLO candidato.** Log real: artista
    'dai' (nombre que colisiona con "Dai Dai", canción del Mundial de
    Shakira/Burna Boy) -- 19 llamadas a `verifyTrackExists()` UNA
    DETRÁS DE OTRA sobre los hasta 20 resultados de YouTube, cada una
    con su propio coste de red (ya paralelizado entre MusicBrainz/
    Discogs/Wikidata por el punto 26, pero el bucle EXTERIOR sobre los
    20 resultados seguía siendo secuencial). Corregido: se comprueban
    ahora por lotes de `YOUTUBE_VERIFY_BATCH_SIZE` (5) en paralelo
    (`async`/`coroutineScope`) dentro de `resolveYoutubeCandidate()`,
    ni uno a uno (demasiado lento) ni los 20 a la vez (arriesgaría
    saturar MusicBrainz, ya inestable con 503 de por sí -- 20
    candidatos × 3 fuentes cada uno serían 60 peticiones simultáneas).
    Dentro de cada lote se conserva el orden original de YouTube para
    decidir cuál vale si varios confirman a la vez.
31. **BUG REAL, mismo log: sesión "Clásica" con candidatos reales y
    muy conocidos (Joe Hisaishi/久石譲) dando "NO ENCONTRADO"
    sistemáticamente, 0 de 4 títulos confirmados, género entero sin
    servir ni un tema.** Causa: `stripTitleNoise()` solo limpiaba
    puntuación ASCII -- paréntesis `()`, separador `" - "` con
    espacios a ambos lados. Los títulos de este log usaban paréntesis
    de ANCHO COMPLETO japoneses (`（）`, carácter Unicode distinto de
    `()`, la regex ASCII no los reconoce) y un guion sin espacios
    (`久石譲-風の谷のナウシカ`, no coincide con `" - "` literal) -- el
    título que llegaba a la comparación seguía contaminado con el
    nombre del artista y los corchetes, nunca coincidía con el
    catálogo real por mucho que el tema existiera y estuviera bien
    documentado. Añadido el mismo tratamiento para `（）`/`【】`/`「」`
    (corchetes de ancho completo corrientes en títulos japoneses/
    chinos) y un guion sin espacios como separador alternativo cuando
    `" - "` no aparece. Ninguno de los dos arreglos toca el resto del
    comportamiento -- solo añaden reconocimiento de puntuación que
    antes se ignoraba por completo. Sin verificar en dispositivo real
    todavía.

32. **Clásica seguía tardando muchísimo tras los puntos 30-31 --
    "a las dos horas ha encontrado unos temas de clásica".** Log real
    nuevo: 'Anton Webern' y 'John Zorn' sí encontraron temas reales en
    tiempo razonable, pero 'Herbie Hancock' arrastró decenas de
    `retryOnceIfTransient()` y muchos "NO ENCONTRADO" antes de dar con
    dos temas válidos. Causa parcial identificada y corregida: varios
    de los títulos rechazados eran contenido hablado/educativo que
    nunca iba a coincidir con ninguna obra catalogada --
    `"JOHN ZORN Composer Portrait"`, `"The Orchestral Woodwind
    Section: An Introduction"` -- y `looksLikeNonSong()` no los
    reconocía como "esto no es música". Añadidos `portrait`/`retrato`/
    `an introduction`/`una introducción` a `NOT_MUSIC_TITLE_HINTS`
    para descartarlos ANTES de gastar una llamada de red en
    verificarlos.

    **Sin resolver del todo, dicho con honestidad:** clásica/jazz
    tiene en YouTube una proporción de contenido que nunca va a
    coincidir con una obra concreta (actuaciones con varios artistas a
    la vez -- "Gustavo Dudamel & Herbie Hancock & George Gershwin",
    versiones de otros intérpretes -- "Cracow Klezmer Band plays
    Zorn") mucho mayor que en géneros populares, y eso no se arregla
    con una lista de palabras clave. Sumado a que MusicBrainz/Discogs
    se muestran especialmente inestables en las sesiones de clásica de
    este log concreto (ráfagas de `retryOnceIfTransient()`), es
    probable que clásica siga siendo, de forma estructural, más lenta
    que otros géneros -- no se puede prometer que quede por debajo de
    los 10 segundos con el diseño actual. Sin verificar en dispositivo
    real todavía.

33. **Dos quejas juntas de Miguel Ángel sobre el mismo problema de
    fondo:** *"el loop de batería, eso era por rellenar un hueco de
    unos segundos, pero esto llega varios minutos antes de poner
    ningún tema. Y hay que introducir un botón de dejar de buscar
    porque cuando ya veo que no encuentra absolutamente nada y voy a
    escuchar otra cosa, te salta lo que estaba buscando."* La segunda
    parte era un bug real, no solo una petición de UX: la búsqueda del
    primer tema (`fetchOneRadioTrack()` dentro de
    `startRadioFromManualAnchor()`) se ejecutaba en la propia corrutina
    del ViewModel de la pantalla, sin ningún `Job` que otra parte del
    código pudiera cancelar -- si el usuario se ponía a escuchar otra
    cosa mientras tanto, esa búsqueda vieja seguía viva de fondo, y en
    cuanto encontraba algo, llamaba a `playQueue()` e interrumpía lo
    que sonaba en ese momento.

    Corregido: la búsqueda ahora se lanza en `managerScope` (vive con
    el propio `PlayerManager`, no con la pantalla) y se guarda en
    `initialSearchJob`, un `Job` de verdad. Dos formas de matarla:
    (1) `playQueue()` la cancela al principio -- cualquier otra cosa
    que empiece a sonar mata automáticamente una búsqueda de
    miMooutCast abandonada, sin que el usuario tenga que hacer nada;
    (2) nuevo botón "Dejar de buscar" en la propia pantalla de carga
    de miMooutCast, que llama a
    `PlayerManager.cancelMimooutcastSearch()` (cancela el `Job` y para
    el loop de apertura, que si no se quedaría sonando para siempre).
    `MimooutcastViewModel` distingue "cancelado a propósito" de
    "buscado de verdad y no se encontró nada" (`searchCancelledByUser`)
    para no mostrar el aviso de "sin resultados" tras una cancelación
    explícita, que sería engañoso. Sin verificar en dispositivo real
    todavía.

34. **Miguel Ángel repitió el mismo aviso varias veces seguidas: "tarda
    un huevo en empezar la música clásica" y "el loop de batería" --
    los arreglos anteriores (paralelizado por lotes, filtro de
    documentales/retratos) no bastaban.** Dos arreglos de fondo más:

    - **Velocidad de clásica**: nueva `RadioRepository.suggestWorkForArtist()`
      -- mismo principio que `suggestArtistFromDecade()` (preguntar
      algo concreto en vez de adivinar a ciegas): con el nombre del
      compositor/intérprete ya en la mano, se pregunta a MusicBrainz
      una OBRA real y concreta suya (`artist:"NOMBRE"` sobre
      release-group) en vez de hacer una búsqueda "solo artista" a
      ciegas por toda su discografía en YouTube. Con el título ya
      conocido, la búsqueda es tan precisa como "artista + canción"
      en cualquier otro punto del proyecto. Solo afecta al ancla
      clásica (`anchor.isClassical`) -- género/origen no clásicos, sin
      tocar.
    - **Loop de apertura sin tope**: aunque ya había botón "Dejar de
      buscar" y cancelación automática al empezar a sonar otra cosa,
      seguía sin haber ningún límite si nadie se daba cuenta y
      actuaba. Nueva `MIMOOUTCAST_INITIAL_SEARCH_TIMEOUT_MS` (30
      segundos, valor de partida sin dato más fino que lo afine) --
      `withTimeoutOrNull()` envolviendo la espera del primer tema; si
      se cumple el plazo, se rinde sola, sin esperar a que el usuario
      pulse nada. `job.cancel()` siempre en el `finally`, pase lo que
      pase (éxito, cancelación manual, o tope de tiempo cumplido) --
      cancelar un `Job` ya terminado es una operación segura, así que
      no hace falta bifurcar la lógica según el motivo. Sin verificar
      en dispositivo real todavía.

35. **Órdenes directas y explícitas de Miguel Ángel, sin margen de
    interpretación: "quita el puto loop de los cojones" y "como mucho
    espero 10 [segundos] y ya es mucho. Si no somos capaces de poner
    un tema en menos de 10 segundos, mejor lo dejamos."** El tope de
    30 segundos del punto 34 no era lo pedido -- se quería quitar el
    loop de apertura del todo, no darle un límite.

    `startRadioFromManualAnchor()` ya no llama a
    `playOpeningLoopIfAvailable()` -- miMooutCast no reproduce nada
    mientras busca el primer tema, silencio hasta que aparece uno real
    (o el tope de tiempo, o el botón "Dejar de buscar"). La función en
    sí se deja en el archivo (podría hacer falta en otro sitio en el
    futuro), solo se quita la llamada. `MIMOOUTCAST_INITIAL_SEARCH_TIMEOUT_MS`
    bajado de 30.000 a 10.000 ms -- mismo límite que ya se pidió para
    encontrar cada tema durante la sesión (punto 29), ahora también
    para el primero. Sin verificar en dispositivo real todavía.

36. **REDISEÑO DE FONDO para género, orden directa de Miguel Ángel tras
    rechazar el tope de tiempo como solución real:** *"si no eres
    capaz de encontrar un algoritmo que sea capaz de poner uno de los
    cientos de miles de temas de clásica en menos de 10 segundos, que
    te estés quieto y elimines toda la funcionalidad de miMooutCast
    porque no sirve para nada."* El camino anterior para género eran
    DOS peticiones a MusicBrainz en serie: `suggestRelatedArtist()`
    (un artista) y, solo para clásica, un segundo viaje a
    `suggestWorkForArtist()` (una obra suya) -- dos ida y vuelta antes
    de tener nada que buscar en YouTube, sin contar reintentos.

    Nueva `RadioRepository.suggestWorkForGenre()`: UNA sola petición
    -- busca directamente release-groups etiquetados con el género
    (`tag:"GÉNERO"`, mismo campo ya confirmado funcionando en la
    búsqueda de artista) y extrae artista Y título A LA VEZ del mismo
    resultado, mismo patrón que `suggestArtistFromDecade()` aplicado a
    género en vez de a fecha. Con género presente (con o sin origen a
    la vez), `fetchSimpleManualCandidate()` usa esta función siempre,
    no solo para clásica -- beneficia a todos los géneros por igual.
    Origen SOLO (sin género) se queda con el camino antiguo
    (`suggestRelatedArtist()`, "solo artista" vía país/región) porque
    release-group no tiene país por artista de forma fiable que
    buscar. `suggestWorkForArtist()` (artista ya conocido -> una obra
    suya) queda sin usar por ahora, se deja en el archivo.

    **AVISO DE VERIFICACIÓN PENDIENTE, mismo criterio que con
    `firstreleasedate` en su momento**: que el campo `tag` funcione en
    la búsqueda de release-group igual de bien que ya está confirmado
    en la de artista no se ha podido probar en vivo (mismo bloqueo de
    robots.txt de siempre). El log de `suggestWorkForGenre()` deja
    constancia de cuántos release-groups trae la respuesta en bruto --
    si es sistemáticamente 0, es el primer sitio a mirar. Sin
    verificar en dispositivo real todavía.

37. **El rediseño del punto 36 falló en dispositivo real: "Clásica" no
    encontró nada -- confirmado con captura de pantalla, "No se ha
    encontrado ningún tema para 'Clásica'".** Descartado primero, con
    el código delante, que fuera un fallo de enrutado (`anchor.genre`
    para clásica es literalmente el texto `"classical"`, no vacío --
    `RadioRepository.manualAnchor()` lo confirma, `isClassical = genre
    == "classical"`; la rama nueva SÍ se estaba usando). Causa más
    probable, sin poder confirmarlo en vivo: el campo `tag` es válido
    tanto en la búsqueda de artista como en la de disco (confirmado
    contra la documentación oficial), pero las etiquetas de género
    están mucho menos pobladas en discos (`release-group`) que en
    artistas en la base de datos real de MusicBrainz -- cambié un
    camino lento-pero-fiable por uno rápido-pero-vacío.

    Corregido con una RED DE SEGURIDAD, no revirtiendo el intento: se
    prueba primero el camino rápido de un solo paso
    (`suggestWorkForGenre()`); si no da nada, cae automáticamente al
    camino de dos pasos que sí sabíamos que funcionaba
    (`suggestRelatedArtist()` y, si es clásica,
    `suggestWorkForArtist()`). Mejor lento-pero-fiable de reserva que
    rápido-pero-vacío sin ninguna alternativa. Sin verificar en
    dispositivo real todavía.

38. **Cambio de estrategia completo para clásica, orden explícita de
    Miguel Ángel tras el fallo del punto 37:** *"clásica no es
    necesario buscar con tanto subgénero, buscamos classical y punto.
    Coges un recopilatorio de los mejores 100 temas de clásica de
    todos los tiempos y vamos poniendo temas aleatoriamente sin
    repetir de ese recopilatorio hasta encontrar temas... nunca se
    para de buscar hasta tener 200 temas en cola."* Se abandona por
    completo, para clásica, la búsqueda dinámica contra MusicBrainz
    (`suggestWorkForGenre()`/`suggestRelatedArtist()`+`suggestWorkForArtist()`)
    -- demasiadas obras marginales, verificación demasiado lenta e
    incierta.

    Nuevo `ClassicalGreatestHits` (archivo aparte): cien obras
    compositor+título reales y muy conocidas (Beethoven, Mozart, Bach,
    Vivaldi, Chopin, Tchaikovsky...), elegidas por ser de las más
    grabadas del repertorio -- máxima probabilidad de encontrar una
    grabación real y verificable rápido en YouTube. Nuevos campos de
    sesión `classicalHitsOrder` (la lista barajada una vez al arrancar
    la sesión, en `startRadioFromManualAnchor()`) y `classicalHitsIndex`
    (el siguiente por probar) -- reseteados en los mismos dos límites
    de sesión de siempre. `fetchSimpleManualCandidate()` tiene ahora
    una rama de máxima prioridad para `anchor.isClassical`: coge
    `classicalHitsOrder[classicalHitsIndex]` directo, sin ninguna
    llamada a MusicBrainz -- todo lo demás (veto de ventana, no
    repetir tema, verificación en YouTube) sigue igual que con
    cualquier otra fuente.

    Nuevo `miMooutCastQueueTarget` (campo de sesión, por defecto
    `RADIO_QUEUE_SIZE`/10 de siempre, nunca se toca la constante en
    sí) -- clásica lo sube a 200 en `startRadioFromManualAnchor()`.
    `topUpRadioQueueIfNeeded()` (compartida con la Radio) usa este
    campo en vez de la constante fija en sus tres puntos de
    comprobación -- para Radio y el resto de miMooutCast sigue
    valiendo 10 exactamente igual que siempre, solo cambia para una
    sesión de clásica en curso. Sin verificar en dispositivo real
    todavía.

39. **Fallo real mío, no una repetición vacía: el punto 38 arregló el
    ALGORITMO de clásica pero dejó la PANTALLA sin tocar.** Captura de
    pantalla de Miguel Ángel: tocar "Clásica" seguía desplegando un
    submenú completo de subgéneros (Andalusian Classical, Chinese
    Classical, Guoyue, Japanese Classical...) -- exactamente lo que ya
    había pedido quitar (*"clásica no es necesario buscar con tanto
    subgénero, buscamos classical y punto"*), y que además, si se
    tocaba cualquiera de esos subgéneros en vez de "Todo Clásica", ni
    siquiera entraba en el recopilatorio nuevo del punto 38 -- caía en
    el buscador viejo de MusicBrainz, porque `genre` dejaba de ser
    exactamente el texto `"classical"` (`RadioAnchor.isClassical`
    exige coincidencia exacta).

    Causa: `MimooutcastViewModel.tapGenre()` despliega el submenú de
    subgéneros cuando el árbol de géneros de MusicBrainz tiene hijos
    para ese género -- y "classical" sí los tiene ahí (existen de
    verdad como géneros de MusicBrainz), aunque miMooutCast ya no deba
    ofrecerlos. Corregido: `tapGenre()` ahora comprueba primero si es
    "classical" -- en ese caso va SIEMPRE directa a arrancar la
    sesión, nunca al desplegable, sea lo que sea que devuelva el árbol
    de géneros. El resto de géneros, sin cambios. Sin verificar en
    dispositivo real todavía.

Todas las incidencias corregidas en la misma sesión, sin necesidad de PCH
(H15 sigue PAUSADO, H18 es el hito EN PROGRESO -- incidencia puntual
sobre código de un hito pausado, mismo criterio que el fix de
centrado del karaoke sobre H17). Sin verificar en dispositivo real
todavía.

## Hoja de Ruta para la Siguiente Sesión que retome H15

Sin código pendiente. Puntos:

1. **Seguir la verificación en dispositivo real** que Miguel Ángel ya
   tiene en marcha -- las tres secciones (Géneros con subgéneros,
   Décadas, Origen), el loop de apertura, y sobre todo confirmar que
   el ancla se mantiene fija toda la sesión sin drift de género tras
   el arreglo del punto 10 de "COMPLETADAS EN S030".
2. **Verificar en dispositivo el arreglo de S032**: que un artista no
   vuelve a sonar hasta pasados 10 temas dentro de una misma sesión de
   miMooutCast, y que un ancla genuinamente agotada (género/década/
   origen muy nicho, sin más candidatos en ninguna fuente) muestra el
   aviso "Sin más música" en vez de quedarse en silencio.
3. **Verificar en dispositivo los puntos 14 y 15**: que
   `radio_relacionados_debug.txt` NO recibe ni una sola línea nueva
   durante una sesión de miMooutCast (todo debe ir a
   `mimooutcast_debug.txt`), y que no vuelve a aparecer ningún nombre
   de artista suelto sin relación con el ancla elegida.

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

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

Ambas incidencias corregidas en la misma sesión, sin necesidad de PCH
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

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

## Hoja de Ruta para la Siguiente Sesión que retome H15

Queda un único punto de alcance sin cerrar:

1. **Rango de décadas** de la lista fija (límite inferior/superior,
   p.ej. 1950-2020) -- cerrar con Miguel Ángel antes de construir la
   lista.

Una vez cerrado ese punto:

2. Construir la función de ancla manual en `RadioRepository` (recibe
   género y/o década y/o origen elegidos a mano, sin artista de
   origen, devuelve un `RadioAnchor` con la misma forma que
   `resolveAnchor()`).
3. Construir la lista fija de géneros y la lista fija de décadas.
4. Construir la pantalla nueva (tres secciones -- Géneros, Décadas,
   Origen --, cristal esmerilado, intuitiva) y su entrada en la
   navegación.
5. Verificar en dispositivo real.

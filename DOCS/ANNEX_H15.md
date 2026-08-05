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

## Hoja de Ruta para la Siguiente Sesión que retome H15

Esta es una petición deliberadamente abierta en dos puntos concretos
(alcance del origen/país, y la interacción exacta género+década) --
**la sesión que la retome debe cerrar esos dos puntos con Miguel
Ángel primero, antes de escribir código**, mismo patrón que ya se usó
para abrir H08 (S013) y H12 (S017): sesión de diseño primero si hace
falta, o preguntas puntuales de alcance al arrancar si el diseño ya
está lo bastante cerrado con lo escrito arriba.

Una vez cerrado el alcance:

1. Construir la función de ancla manual en `RadioRepository`.
2. Resolver de dónde sale el catálogo real de géneros de MusicBrainz
   (reutilizar lo que ya exista de H08/H09 antes de construir algo
   nuevo).
3. Construir la pantalla nueva (dos secciones, cristal esmerilado,
   intuitiva) y su entrada en la navegación.
4. Verificar en dispositivo real.

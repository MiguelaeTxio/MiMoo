# MIMOO — ANEXO H13: UX del Reproductor (ExoPlayer)

*Vive en `DOCS/ANNEX_H13.md`. Este anexo es puramente descriptivo:
qué hay construido/verificado y qué queda abierto. **Nunca contiene
su propio estado (EN PROGRESO/PAUSADO)** -- esa información vive
exclusivamente en `DOCS/ANNEX_ROUTER.md`.*

---

## NOTA DE APERTURA (2026-07-19, S018)

Hito abierto a petición explícita de Miguel Ángel al cierre de la
sesión S018 (H12 -- Directorio de Música), tras confirmar en
dispositivo que el fix del cruce local por posición en `AlbumScreen`
funciona ("Va de lujo, ahora sí"). Petición textual: *"pausar el
hito de momento y hacer un PCH hacia la UX del exoplayer. Chapitas,
opciones de cíclicos y aleatorio deben verse cuando están activos,
etc."*

La petición es deliberadamente abierta ("etc.") -- Miguel Ángel tiene
más cosas en mente sobre la UX del reproductor que no ha detallado
todavía. **La sesión que retome este hito debe empezar preguntando
qué más incluye "etc." antes de tocar código**, salvo que Miguel
Ángel entregue ya una lista cerrada al arrancar.

---

## OBJETIVO DEL HITO

Mejorar la legibilidad visual de los controles del reproductor
expandido (`PlayerBar.kt`) -- en concreto, que los controles con
estado ON/OFF (aleatorio, cíclico, y cualquier otro que se identifique
durante la sesión) se vean claramente activos de un vistazo, no solo
mediante un cambio sutil de color de icono.

---

## CONTEXTO TÉCNICO (leído en S018 antes de abrir este hito)

Archivo relevante: `app/src/main/java/com/miguelaetxio/mimoo/ui/player/PlayerBar.kt`
(reproductor expandido), estado en `PlayerBarViewModel.kt` /
`PlayerManager.PlaybackState`.

Estado actual de los controles con ON/OFF, verificado leyendo el
código real:

- **Aleatorio** (`Icons.Filled.Shuffle`, línea ~123): visible solo si
  `state.queueSize > 1`. Activo/inactivo se distingue HOY
  únicamente por `tint` (`MaterialTheme.colorScheme.primary` si
  `state.shuffleModeEnabled`, color por defecto si no) -- sin fondo,
  sin chapita, sin ningún otro indicador. `contentDescription` sí
  cambia correctamente ("Desactivar orden aleatorio" / "Activar
  orden aleatorio").
- **Cíclico** (`Icons.Filled.Repeat`, línea ~182): mismo patrón
  exacto que aleatorio -- solo `tint` cambia
  (`state.repeatModeEnabled`).
- **Favorito** (`Icons.Filled.Favorite`/`FavoriteBorder`, línea
  ~141): usa un patrón distinto -- cambia el propio icono (relleno
  vs contorno) ADEMÁS del tint (`colorScheme.error` si es favorito).
  Este sí se lee con claridad de un vistazo porque el propio glifo
  cambia, no solo el color.
- **Descarga** (línea ~207): no es un toggle -- el botón desaparece
  por completo cuando la pista ya está descargada o en cola/
  descargando (no hay estado "activo" que mostrar, es
  correcto tal cual).

**Diagnóstico preliminar (a confirmar/ampliar con Miguel Ángel):** el
problema de aleatorio/cíclico probablemente es que un cambio de
`tint` sobre fondo de cristal (glassmorphism, ver
`ui/theme/Glass.kt`) es poco perceptible -- mismo icono, mismo fondo,
solo cambia el color del trazo. El patrón de favorito (cambio de
GLIFO, no solo de color) se lee mucho mejor. La palabra "chapitas"
que usa Miguel Ángel sugiere que quiere algo más parecido a
`FilterChip` (fondo relleno cuando está seleccionado, como los chips
de tipo de resultado de H12 en `UnifiedSearchScreen.kt`) en vez de
solo un `Icon` con tint variable.

**Otros candidatos a revisar en la misma pasada, sin confirmar
todavía que Miguel Ángel los incluye en "etc.":**
- Mini-reproductor colapsado (`PlayerBarCollapsed`, dentro de
  `PlayerBar.kt`) -- no se ha revisado si tiene los mismos controles
  o alguno con el mismo problema.
- Notificación del sistema (`MiMooPlaybackService`) -- los controles
  de notificación de Android tienen sus propias limitaciones de
  iconografía (no soportan chapitas custom), probablemente fuera de
  alcance de este hito salvo que Miguel Ángel diga lo contrario.

---

## HOJA DE RUTA PARA LA SESIÓN QUE RETOME H13

1. **Preguntar primero, no asumir.** Confirmar con Miguel Ángel:
   - ¿"Chapitas" significa fondo tipo `FilterChip`/`glassChip`
     relleno cuando el control está activo, o algo distinto (badge,
     punto indicador, etc.)?
   - ¿Qué controles concretos entran en el "etc." además de
     aleatorio y cíclico? (repasar con él la lista de "Otros
     candidatos" de arriba).
   - ¿Afecta solo al reproductor expandido, o también al
     mini-reproductor colapsado?
2. Una vez cerrado el alcance, implementar los cambios visuales en
   `PlayerBar.kt` (y `PlayerBarViewModel.kt` si hiciera falta exponer
   algún estado nuevo -- no debería, `shuffleModeEnabled`/
   `repeatModeEnabled` ya existen en `PlaybackState`).
3. Verificar visualmente (PASO 4 de `newflow-android-edit`) antes de
   cada commit, prestando atención a contraste sobre el fondo de
   cristal real de la app, no solo a que el código compile.
4. Cierre de sesión: actualizar este anexo con lo construido y
   `RESUMPTION_POINT.md`.

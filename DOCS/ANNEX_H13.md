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

---

## COMPLETADAS EN S019

**Nota de alcance:** esta sesión NO cerró el "etc." de H13 -- se abrió
con la continuación normal (chapitas ya construidas en S018,
verificadas visualmente) y derivó por completo hacia un bug real de
descargas (yt-dlp, restricción por edad) reportado por Miguel Ángel a
mitad de sesión, más una función nueva de producto que salió de ahí.
La hoja de ruta original (arriba) sigue intacta y sin tocar para la
próxima sesión que retome H13 de verdad.

**Único punto real de H13 en esta sesión:** fix de un bug de layout
real en `PlayerBar.kt` -- con el menú de tres puntos (H12) presente
junto a favorito/descarga/aleatorio/cíclico, la fila de controles
superaba el ancho de pantalla y el botón de contraer el reproductor
(el último de la fila) se salía por el borde derecho, invisible e
inalcanzable, dejando el reproductor expandido fijo sin forma de
colapsarlo. El botón de contraer ahora es fijo, fuera de la fila
scrollable de controles variables.

**Todo lo demás de la sesión (fuera de H13, sin PCH formal --
tratado como trabajo de bug real intercalado, mismo patrón que
sesiones anteriores):**

1. **Soporte de cookies de YouTube para vídeos restringidos por
   edad.** Reportado con `debug_error.txt` real: yt-dlp rechazaba la
   descarga de "River Euphrates" (Pixies) con "Sign in to confirm
   your age". `CookiesManager.kt` (nuevo) gestiona un `cookies.txt`
   (Netscape) importable desde Ajustes → YouTube, pasado a
   `downloader.py` como `cookiefile`.
2. **Sincronización de cookies vía Drive (H07), NUNCA vía APK ni
   códigos de compartición.** Petición explícita: que Silvia no
   tenga que importar nada a mano. Rechazada la vía de embeberlo en
   la instalación (riesgo real: el APK es público, cualquiera con él
   podría extraer una sesión completa de la cuenta de Google).
   `SyncEnvelope.cookiesTxtContent` viaja por el canal privado de
   `AutoSyncPusher`/`AutoSyncViewModel`, deliberadamente fuera de
   `BackupBundle`.
3. **Fix real de validación:** `CookiesManager.importCookies()`
   rechazaba en silencio las líneas `#HttpOnly_` (donde viven las
   cookies de autenticación más importantes de Google) -- corregido.
4. **Fix real de fondo, encontrado leyendo la documentación actual de
   yt-dlp:** con cookies, por defecto solo se prueban los clientes
   `tv_downgraded,web` en cuenta gratuita -- `web_creator` (el único
   que gestiona la verificación de edad de cuenta) nunca se probaba
   sin ser Premium. Forzarlo para TODA descarga con cookies fue una
   REGRESIÓN real (rompía vídeos normales con "Please sign in",
   reportado con dos alternativas distintas fallando). Fix final:
   primer intento siempre con el comportamiento por defecto; solo se
   reintenta forzando `web_creator` si el error es el mensaje EXACTO
   de restricción de edad.
5. **Diagnóstico real en `debug_error.txt`:** `cookiesPath`,
   `cookiesExist`, `cookiesDiag` (tamaño, líneas, dominios
   google.com/youtube.com presentes, cookie SID/`__Secure-3PSID`) y
   `ytDlpVersion` -- construido en vivo, iterando con los propios
   `debug_error.txt` reales que Miguel Ángel fue subiendo.
6. **Función nueva de producto: "Buscar alternativa" en Descargas.**
   Petición explícita de Miguel Ángel: cuando una pista falla siempre
   (límite real de yt-dlp, no arreglable desde MiMoo), poder buscar
   otro vídeo de YouTube para la misma canción sin romper el álbum.
   `TrackAlternativeRepository.kt` (nuevo, mismo patrón que
   `BackupImportRepository` -- transacción Room que cruza dos DAOs)
   sustituye youtubeId/canal/duración/miniatura del vídeo,
   PRESERVANDO SIEMPRE título, artista, álbum, posición de disco y
   favorito de la fila original, y recreando las referencias de
   playlist. UI en `DownloadsScreen.kt`: tercer botón junto a
   Reintentar/Borrar, campo de búsqueda editable (precargado con el
   título exacto), lista de resultados.
7. **Fix real de fallo silencioso, encontrado por queja directa de
   Miguel Ángel (probó 4 alternativas, ninguna se aplicó nunca):**
   la función cerraba el diálogo SIEMPRE, sin comprobar
   `MutationOutcome` ni capturar ninguna excepción -- si no había
   conexión o cualquier paso fallaba, el diálogo se cerraba igual sin
   avisar. Corregido: ahora nunca se cierra en silencio.
8. **Paso de confirmación explícita añadido a petición textual**
   ("todo pasa por que siempre hacemos las cosas callando"): tocar un
   resultado ya no descarga directamente -- muestra tema fallido,
   texto de búsqueda, tema alternativo elegido, y Cancelar/Aceptar.
9. **Verificado en dispositivo real por Miguel Ángel, de principio a
   fin:** cookies importadas y sincronizadas, fix de `web_creator`
   sin la regresión, y una descarga completada con éxito vía "Buscar
   alternativa".

**Incidencia de proceso, ya resuelta:** el repositorio estuvo en modo
privado durante parte de la sesión y se quedó sin cuota gratuita de
GitHub Actions -- Miguel Ángel lo puso en público a mitad de sesión y
el build pendiente terminó en verde sin más intervención.

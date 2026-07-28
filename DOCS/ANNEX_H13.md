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

Encargo de Miguel Ángel al cerrar S024, que redefine el alcance de la
sesión que retome este hito:

> *"Comenzamos la sesión siguiente con UX, hay varias cosas que
> comprobar, como el funcionamiento de los botones y el aspecto de
> algunos ítems. En el ExoPlayer, descargar no funciona, por ejemplo.
> Hay que repasar todos los botones a ver cómo está el código."*

O sea: **repaso sistemático de la UX del reproductor**, no un retoque
puntual. Dos frentes distintos que conviene no mezclar.

### 1. Incidencia concreta y reportada: descargar no funciona

Único bug con reporte explícito. Miguel Ángel lo sitúa "en el
ExoPlayer", que en la práctica significa la barra de reproducción. Hay
que reproducirlo antes de tocar nada: no se sabe todavía si el botón
no responde, si responde y la descarga falla, o si descarga y no se
refleja en la interfaz.

Contexto que ya existe y hay que leer antes: S019 de este mismo anexo
derivó por completo hacia un bug de descargas (yt-dlp, restricción por
edad). **Comprobar primero si esto es una regresión de aquello o un
fallo distinto** antes de abrir camino nuevo.

### 2. Repaso sistemático de todos los botones

Miguel Ángel pide revisar **cómo está el código** de los botones, no
solo su comportamiento visible. Enfoque sugerido, a confirmar con él:

1. Inventariar todos los controles de `PlayerBar.kt` — reproducir /
   pausar, anterior, siguiente, aleatorio, cíclico, favorito,
   descargar, colapsar, menú de tres puntos.
2. Por cada uno: qué estado lee, qué acción dispara, si refleja el
   estado real o uno derivado, y si el `onClick` puede quedar sin
   efecto en algún estado (cola vacía, tema local frente a streaming,
   Radio activa).
3. Anotar los que estén mal ANTES de arreglar ninguno, y presentarle
   la lista para que priorice. **No arreglar sobre la marcha** — es
   justo el patrón que él criticó en S024 (*"caso no contemplado,
   implementar caso, probar, caso no contemplado... conlleva a una
   implementación eterna"*).

### 3. Aspecto de algunos ítems

Mencionado sin concretar. **Preguntar cuáles** antes de proponer nada;
no asumir que se refiere a las chapitas de S018, que ya están hechas y
verificadas.

### 4. Pendiente heredado, sin cerrar desde S018

El "etc." del alcance original de H13 sigue sin definirse. Las
preguntas que quedaron abiertas y siguen sin respuesta:

- ¿Qué controles entran en el "etc." además de aleatorio y cíclico?
  (repasar con él la lista de "Otros candidatos" de este anexo).
- ¿Afecta solo al reproductor expandido, o también al
  mini-reproductor colapsado?

Es probable que el repaso sistemático del punto 2 conteste a las dos
por sí solo.

### Método

Verificar visualmente (PASO 4 de `newflow-android-edit`) antes de cada
commit, prestando atención al contraste sobre el fondo de cristal real
de la app y no solo a que compile. Las pruebas de comportamiento las
hace Miguel Ángel en dispositivo.

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

---

## COMPLETADAS EN S020

Sesión que sí cerró el "etc." de la hoja de ruta. Alcance dado por
Miguel Ángel en una sola frase: *"el exoplayer carece del efecto
cristal esmerilado en los botones y en la parte de texto junto a la
carátula donde se muestran los metadatos"*.

**Hallazgo real durante la implementación, causa de fondo de la queja
original del hito.** El estado activo de aleatorio/cíclico se
señalaba con `MaterialTheme.colorScheme.primary` como `tint` del
icono. En la paleta de la app (`MiMooTheme.kt`, petición de Miguel
Ángel de 2026-07-05: fondo azul MSX, letra blanca) **`primary` ES
`Color.White`** -- exactamente el mismo blanco que
`LocalContentColor` del estado inactivo. El "cambio de color" nunca
cambió un solo píxel en pantalla; el único diferenciador real era la
chapita añadida en S018. El diagnóstico preliminar de la nota de
apertura ("un cambio de tint sobre fondo de cristal es poco
perceptible") se queda corto: no es poco perceptible, es idéntico.

**Construido:**

1. **`Glass.kt` -- chapita ENCENDIDA.** `GlassTokens.activeFillTop`/
   `activeFillBottom` (blanco casi opaco, 0.88/0.72) y cuarto
   parámetro `active` en `glassChip()`, con valor por defecto `false`
   -- firma retrocompatible, ninguna llamada existente de la app
   cambia. `active` manda sobre `interactive`: una chapita encendida
   es clicable por definición.
2. **`PlayerBar.kt` -- `GlassIconButton` privado.** TODOS los botones
   del reproductor (expandido y mini-barra colapsada) pasan por este
   composable, así que el aspecto se ajusta en un único sitio. El
   `padding` se aplica ANTES del cristal, para dejar aire real entre
   chapitas contiguas sin que ese aire se pinte de cristal.
3. **Aleatorio y cíclico:** chapita encendida cuando el modo está
   activo, con el icono en azul MSX (`colorScheme.onPrimary`) sobre la
   placa clara -- una tecla iluminada, legible sin depender del color
   del trazo.
4. **Favorito:** conserva su patrón propio (glifo relleno vs contorno
   + amarillo), que ya se leía bien; ahora también sobre cristal base.
5. **Anterior, play/pausa, siguiente, descargar, menú de tres puntos y
   contraer:** cristal base.
6. **Bloque de metadatos junto a la carátula** (título / artista /
   Local-Streaming) sobre chapita de cristal interactivo -- es
   clicable, abre la cola de sesión.
7. **Mini-barra colapsada:** mismo tratamiento (metadatos y sus dos
   botones), para que contraer el reproductor no cambie el lenguaje
   visual. Esto responde además a la pregunta 3 de la hoja de ruta
   ("¿solo expandido o también colapsado?"), que Miguel Ángel no
   respondió por separado: se aplicó a ambos por coherencia, declarado
   explícitamente en el momento de hacerlo.

Compilado en verde (`d2fef5c`). **Verificación visual en dispositivo
real pendiente** -- en concreto, si la placa encendida al 0.88/0.72 es
el punto justo de intensidad o hay que subirla/bajarla en
`GlassTokens`, que es un cambio de un solo número.

**Incidencia de proceso, ya corregida:** el primer commit del bloque
salió sin su línea de asunto (`style: H13 -- ...`) porque el heredoc
del mensaje iba encadenado con `&& \`, y el shell interpretó la
primera línea del mensaje como un comando. Corregido con
`git commit --amend -F <fichero>` y `push --force-with-lease`
(`d177cbc` -> `d2fef5c`), mismo árbol de archivos. Para futuras
sesiones: mensaje de commit multilínea siempre desde fichero, nunca
heredoc encadenado.

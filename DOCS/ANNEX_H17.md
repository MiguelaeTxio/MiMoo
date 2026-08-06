# Hito 17 — Karaoke & Lyrics

*Apertura: 2026-08-06 (cierre de S030), a petición explícita de Miguel
Ángel. Cita textual: "vamos a pasar a crear un hito nuevo, Karaoke &
Lyrics: consistirá en añadir una entrada en la sidebar para buscar y
leer letras de canciones y una entrada en el menú de tres puntos del
ExoPlayer que abrirá una ventana encima o debajo del mismo donde
visualizar el karaoke del tema que se está ejecutando si se dispone de
letras."*

---

## Objetivo del hito

Dos piezas distintas, un mismo tema de fondo (letras de canciones):

1. **Entrada nueva en el drawer** -- pantalla para BUSCAR y LEER la
   letra de cualquier canción (no necesariamente la que está sonando).
2. **Entrada nueva en el menú de tres puntos del ExoPlayer** (el mismo
   que ya tiene "Ver álbum"/"Ver artista"/"Elegir como tono para un
   contacto", ver `PlayerBar.kt`) -- abre una ventana superpuesta al
   reproductor (encima o debajo, sin decidir todavía) con el KARAOKE
   del tema que está sonando en ese momento, **si se dispone de
   letras** para ese tema concreto.

Sin nada construido todavía en el proyecto para esto -- confirmado
grepeando el código entero de `app/src/main/java` en el cierre de
S030: no hay ningún repositorio, entidad, ni pantalla de letras ya
empezada. Se parte de cero.

---

## Contexto técnico -- qué ya existe y qué hace falta añadir

### Lo que ya existe y se reutiliza tal cual

- **Menú de tres puntos del ExoPlayer** (`PlayerBar.kt`, `DropdownMenu`
  sobre `showMenu`) -- ya tiene "Ver álbum", "Ver artista" y "Elegir
  como tono para un contacto" como precedentes de items condicionales
  (el de tono solo aparece si hay archivo local). La entrada de
  Karaoke encaja en el mismo sitio, con la misma lógica de "solo si
  aplica" (aquí: solo si hay letra disponible para el tema actual, o
  siempre visible pero informando "sin letra" al pulsar -- **decidir**).
- **Patrón de pantalla de gestión con pestañas + chapitas de cristal
  esmerilado** ya consolidado en la app (`DislikedScreen.kt`,
  `MimooutcastScreen.kt`, `FavoritesScreen.kt`) -- mismo lenguaje
  visual esperable para la pantalla de búsqueda de letras.
- **Metadatos de tema en curso** (`PlayerBar.kt`/`PlayerManager`) --
  artista y título estructurados ya disponibles para consultar
  cualquier fuente de letras por "artista + título", sin tener que
  volver a parsear nada.
- **`Room` + repositorios locales** (patrón ya usado en TODA la app,
  p.ej. `DislikedArtistRepository`/`FavoriteTrackRepository`) -- si se
  decide cachear letras localmente para no volver a pedirlas (ver
  punto 3 de "Puntos de diseño -- ABIERTOS").

### Lo que hace falta construir

Todo -- entidad/DAO/repositorio de letras (si se cachean), cliente de
la fuente de letras que se elija, pantalla de búsqueda en el drawer,
ventana de karaoke sobre el ExoPlayer, entrada en el menú de tres
puntos, entrada en `NavGraph.kt`/`MainActivity.kt`.

---

## Puntos de diseño -- ABIERTOS

**Ninguno de estos se cierra solo -- decidir con Miguel Ángel al
empezar la siguiente sesión, antes de escribir una sola línea de
código, mismo patrón ya usado en H08/H12/H15.**

1. **Fuente de las letras.** No hay ninguna elegida todavía. Esto es
   la decisión técnica que condiciona todo lo demás -- opciones reales
   a valorar en la propia sesión de diseño (con sus condiciones de uso
   y disponibilidad de letras SINCRONIZADAS, que es lo que hace falta
   para "karaoke" de verdad, no solo texto estático):
   - APIs de letras sincronizadas (formato LRC, con timestamp por
     línea) -- p.ej. lrclib.net (gratuita, sin API key, pensada
     exactamente para esto).
   - APIs de letras SIN sincronizar (texto plano) -- más fáciles de
     conseguir pero no sirven para "karaoke" tal cual, solo para
     "leer la letra" (la pantalla del drawer).
   - Revisar términos de uso de cualquier fuente antes de integrarla --
     algunas prohíben explícitamente el uso en apps de terceros.
2. **¿"Karaoke" significa sincronizado línea a línea (resaltando la
   línea que toca según el tiempo de reproducción), o solo mostrar la
   letra completa mientras suena, sin resaltar nada?** Si es lo
   primero (lo que sugiere la palabra "karaoke"), hace falta una
   fuente con timestamps -- ver punto 1. Si una fuente da letra pero
   sin sincronizar, ¿se muestra igual sin resaltado, o se trata como
   "sin letra"?
3. **¿Se cachean las letras localmente (Room) o se piden a la fuente
   cada vez?** Coherente con el patrón general de la app (todo lo demás
   cachea) pero a decidir explícitamente.
4. **Forma de la ventana de karaoke sobre el ExoPlayer** -- el propio
   Miguel Ángel lo dejó abierto ("encima o debajo del mismo"): ¿modal
   a pantalla completa, hoja inferior (bottom sheet) parcial, o
   superpuesta sin tapar los controles de reproducción? Cristal
   esmerilado, coherente con el resto de la app.
5. **Alcance de la pantalla de búsqueda del drawer** -- ¿busca letras
   de cualquier canción vía la fuente elegida (aunque no esté en tu
   biblioteca), solo entre tu biblioteca local/descargada, o ambas
   cosas con alguna distinción visual?
6. **¿Aplica igual a cualquier procedencia de pista** (biblioteca
   local, Radio, Popurrí, miMooutCast, streaming de búsqueda) **o solo
   a según qué tipos**? Previsiblemente debería ser igual para
   cualquier pista que suene, dado que el punto de entrada es el
   ExoPlayer genérico -- confirmar.

---

## Hoja de Ruta para la Siguiente Sesión que retome H17

1. Cerrar los seis puntos de diseño de arriba con Miguel Ángel, en
   orden -- el punto 1 (fuente) condiciona la viabilidad real del
   punto 2 (sincronizado o no), así que se cierra primero.
2. Una vez cerrado el diseño: construir el cliente de la fuente
   elegida (+ caché local si se decide), la pantalla de búsqueda del
   drawer, la ventana de karaoke sobre el ExoPlayer y su entrada en el
   menú de tres puntos, y la navegación.
3. Verificar en dispositivo real.

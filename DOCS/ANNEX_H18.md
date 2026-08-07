# Hito 18 — Play y Ordenación de Listas de Items

*Apertura: 2026-08-07 (arranque de S032), a petición explícita de
Miguel Ángel registrada al cierre de S031 en `RESUMPTION_POINT.md`.
Cita textual: "Cuando diseñamos el CRUD de favoritos y su vista,
tuvimos un error de bulto, no incluimos el botón del play en ninguna
de las columnas de items, ni en artistas, ni en álbumes, ni en
sencillos, ni en listas. Tampoco incluimos ordenar las listas por
orden alfabético o por orden de adición, ascendentes y descendentes."*
Confirmado sin cambios al arrancar S032: alcance de dos piezas
correcto, hito nuevo (no encajaba limpiamente en H03/H04/H12 por ser
petición transversal a varias pantallas).

---

## Objetivo del hito

Dos piezas confirmadas por Miguel Ángel:

1. **Botón de play en las filas/columnas de item de Favoritos** --
   artistas, álbumes, sencillos y listas. Hoy ninguna de las cuatro
   pestañas lo tiene.
2. **Ordenación** (alfabético / orden de adición, cada uno ascendente
   y descendente) -- aplicable a **todas las listas de items de la
   app**, no solo a Favoritos (instrucción explícita de Miguel Ángel).

---

## Contexto técnico -- qué ya existe, verificado contra el código real en S032

### `FavoritesScreen.kt` (372 líneas) -- confirma el punto 1 tal cual lo describió Miguel Ángel

Cuatro pestañas vía `TabRow`/`FavoritesTab` (`ARTISTS`, `ALBUMS`,
`TRACKS`, `PLAYLISTS`), cada una con su propio composable de fila:

- **`ArtistsTab`/`AlbumsTab`** -- comparten `FavoriteRow` (icono +
  título/subtítulo + `Checkbox` de selección + `IconButton` de
  quitar-favorito con `Icons.Filled.Star`). **Sin botón de play
  individual.** El play existe solo a nivel de bloque, en
  `SelectionHeader` (`onPlaySequential`/`onPlayShuffled` sobre el
  conjunto seleccionado, ya conectado a
  `viewModel.playSelectedArtists()`/`playSelectedAlbums()`).
- **`TracksTab`** -- fila propia (`Row` con `glassChip()`, título +
  artista + `IconButton` de quitar-favorito). **Sin botón de play
  individual**; el play solo existe a nivel de bloque completo
  (`playAllFavoriteTracks(shuffle)` en el header de la pestaña).
- **`PlaylistsTab`** -- fila propia (`Row` con `glassChip()`, icono +
  nombre + `IconButton` de quitar-favorito, `clickable` que abre
  `PlaylistDetailScreen` vía `onOpenPlaylist`). **Sin botón de play
  directo** -- solo abrir.

Ninguna de las cuatro pestañas tiene control de ordenación.

### Repositorios/entidades de Favoritos ya existentes (reutilizables)

`FavoriteArtistRepository`, `FavoriteAlbumRepository`,
`FavoriteTrackRepository`/`FavoriteTrackDao`,
`FavoritePlaylistRepository`/`FavoritePlaylistDao` -- patrón Room ya
consolidado en toda la app (mismo que Radio/Lista Negra).

### Otras pantallas de listas de items en la app -- candidatas a la pieza 2, sin confirmar todavía cuáles entran

Localizadas por estructura del proyecto, sin auditar su código interno
todavía: `PlaylistsScreen.kt`/`PlaylistDetailScreen.kt` (H04),
Explorador y páginas de Artista/Álbum/Canción (H12), Canales (H11),
Lista Negra (H16, CRUD). Cuál de estas entra en "todas las listas de
items de la app" es el punto de diseño 3, abajo.

---

## Puntos de diseño -- ABIERTOS, a cerrar con Miguel Ángel antes de escribir código

Mismo patrón que H08/H12/H15/H16: sesión de diseño puro antes de tocar
nada. Ningún paso de código se ejecuta hasta cerrar estos cinco
puntos:

1. **Qué significa "play" para cada tipo de item de Favoritos.**
   ¿Artista/álbum reproducen el equivalente a la selección individual
   de ese único elemento en el popurrí ya existente
   (`playSelectedArtists`/`playSelectedAlbums` con un solo item), o
   necesitan una función dedicada? ¿Sencillo reproduce solo esa pista
   sola, o la encola sobre lo que suene? ¿Lista reproduce la playlist
   entera en su orden guardado?
2. **Icono y posición del botón de play en cada fila.** Junto al
   `IconButton` de estrella ya existente, o sustituyendo el `clickable`
   de fila completa en `PlaylistsTab` (que hoy abre detalle en vez de
   reproducir).
3. **Alcance cerrado de "todas las listas de items de la app".**
   Confirmar explícitamente con Miguel Ángel qué pantallas entran --
   ¿las cuatro pestañas de Favoritos, más `PlaylistsScreen`,
   Explorador (H12), Canales (H11)? ¿Entra también la vista CRUD de
   Lista Negra (H16), que es de exclusión, no de reproducción? No
   inventar la lista -- preguntarla.
4. **Mecanismo de "orden de adición".** Verificar contra las entidades
   reales (`FavoriteArtist`, `FavoriteAlbum`, `FavoriteTrack`,
   `FavoritePlaylist` y las que correspondan de las pantallas
   confirmadas en el punto 3) si ya existe un campo de timestamp de
   alta, o si hace falta añadirlo con una migración de Room nueva.
5. **UI del control de orden.** Un único selector por pantalla con las
   cuatro combinaciones (alfabético asc/desc, adición asc/desc), o dos
   controles independientes (criterio + dirección). Mismo lenguaje
   visual de cristal esmerilado que el resto de la app.

---

## Hoja de Ruta para la Siguiente Sesión que retome H18

Sesión de diseño puro, sin código: cerrar los cinco puntos de arriba
con Miguel Ángel, en el orden dado (el punto 3 condiciona el alcance
real de investigación de código de los puntos 4-5, así que conviene
cerrarlo pronto). Como parte de la propia sesión de diseño, grepear y
leer el código real de las pantallas que Miguel Ángel confirme en el
punto 3 (nunca inferir su estructura de memoria) antes de proponer la
solución técnica concreta. Solo tras cerrar los cinco puntos se
escribe una hoja de ruta ejecutable con pasos de código.

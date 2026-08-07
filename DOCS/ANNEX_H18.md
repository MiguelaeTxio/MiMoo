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

## Puntos de diseño -- CERRADOS EN S032

Los cinco puntos, más una precisión técnica surgida al revisar
`TracksTab`, quedaron cerrados con Miguel Ángel en la propia sesión de
apertura del hito (sin código todavía, ver "Hoja de Ruta" abajo):

1. **Qué significa "play"/"aleatorio" por tipo de item, y matriz exacta
   por fila -- solo en las cuatro pestañas de Favoritos:**
   - **Artistas:** play = mix de temas de ESE artista concreto (no
     mezcla con otros favoritos); aleatorio = lo mismo, barajado. Misma
     lógica que ya usa `SelectionHeader.onPlaySequential`/
     `onPlayShuffled` -> `playSelectedArtists()`, pero aplicada a un
     conjunto de un solo elemento en vez de a la selección con
     checkbox.
   - **Álbumes:** igual criterio que Artistas -- play/aleatorio del
     álbum concreto de esa fila, reutilizando
     `playSelectedAlbums()` con un único `AlbumKey`.
   - **Sencillos:** **SOLO play, sin botón de aleatorio** -- una única
     pista no tiene nada que barajar (precisión de Miguel Ángel al
     revisar `TracksTab`: "en los sencillos no tiene sentido el
     aleatorio").
   - **Listas:** play = reproducir la playlist entera en su orden
     guardado; aleatorio = la misma playlist barajada. Ambos botones,
     igual que Artistas/Álbumes.
2. **Posición: junto al `IconButton` de estrella ya existente**, en las
   cuatro filas (`FavoriteRow` para Artistas/Álbumes, fila propia de
   `TracksTab`, fila propia de `PlaylistsTab`).
3. **Alcance cerrado, con dos piezas separadas por pantalla:**
   - **Botón de play/aleatorio:** exclusivo de las cuatro pestañas de
     `FavoritesScreen.kt`. **No aplica a Lista Negra** (confirmado
     explícitamente por Miguel Ángel -- "ahí no encaja para nada,
     evidentemente", son artistas/temas que no se quieren escuchar).
   - **Ordenación:** aplica a las cuatro pestañas de `FavoritesScreen`,
     más `PlaylistsScreen.kt` (H04), Explorador/páginas de
     Artista-Álbum-Canción (H12), Canales (H11) y **Lista Negra (H16,
     confirmado explícitamente que sí entra)**.
4. **Mecanismo de "orden de adición" -- verificado contra las
   entidades reales en S032:**
   - `FavoriteArtist`, `FavoriteAlbum`, `FavoriteTrack`,
     `FavoritePlaylist` -- **sin ningún campo de timestamp.** Hace
     falta una migración de Room nueva (siguiente en la cadena:
     `MIGRATION_15_16`, versión de `AppDatabase` 15->16) que añada un
     campo de alta (p.ej. `addedAt: Long`) a las cuatro tablas.
   - `Playlist` (H04) ya tiene `createdAt`. `ChannelSubscription`
     (H11) ya tiene `subscribedAt`. `DislikedArtist`/`DislikedTrack`
     (H16) ya tienen `dislikedAt`. **Ninguna de estas tres necesita
     migración** para el orden de adición.
   - Explorador (H12) queda pendiente de verificar en el bloque que le
     corresponda -- sus listas vienen de MusicBrainz (catálogo remoto
     paginado), no de una tabla local con alta propia, así que "orden
     de adición" puede no tener el mismo sentido ahí. **No se cierra
     ahora** -- se revisa contra el código real de esa pantalla al
     llegar a ese bloque, sin asumir.
5. **UI del control de orden: criterio aparte, un único control que
   solo alterna dirección.** Un selector (chip/dropdown, cristal
   esmerilado) para elegir el criterio -- alfabético o adición -- y,
   una vez elegido, un único icono/control que al pulsarlo invierte
   ascendente/descendente del criterio activo. No es un ciclo que rote
   por las cuatro combinaciones con un solo toque.

---

## Hoja de Ruta para la Siguiente Sesión que retome H18

Diseño cerrado -- ya se puede escribir código. Orden recomendado
(cada bloque cerrado se commitea de inmediato, `newflow-android-edit`
PASO 5):

1. **Migración `MIGRATION_15_16`.** Añadir campo de timestamp de alta
   a `FavoriteArtist`, `FavoriteAlbum`, `FavoriteTrack`,
   `FavoritePlaylist` (`ALTER TABLE ... ADD COLUMN addedAt INTEGER NOT
   NULL DEFAULT 0`, patrón ya usado en migraciones anteriores de la
   cadena) + subir versión de `AppDatabase` a 16 + actualizar los
   `data class` de las cuatro entidades.
2. **Botones de play/aleatorio en `FavoritesScreen.kt`.** Extraer de
   `FavoritesViewModel` las funciones ya existentes
   (`playSelectedArtists`/`playSelectedAlbums`/
   `playAllFavoriteTracks`) o añadir variantes de un solo elemento;
   añadir `IconButton`s de `Icons.Filled.PlayArrow`/
   `Icons.Filled.Shuffle` junto a la estrella en `FavoriteRow`
   (Artistas/Álbumes) y en la fila de `PlaylistsTab`; en `TracksTab`
   solo `PlayArrow`, sin `Shuffle` (punto 1).
3. **Control de ordenación en las cuatro pestañas de
   `FavoritesScreen`.** Selector de criterio (alfabético/adición) +
   toggle de dirección, aplicado sobre las listas ya cargadas en
   `FavoritesUiState` (orden en memoria, sin tocar los repositorios
   salvo que el volumen lo justifique).
4. **Extender ordenación** a `PlaylistsScreen.kt`, Canales (H11) y
   Lista Negra (H16) -- mismas tres entidades que ya tienen timestamp,
   sin migración adicional.
5. **Explorador (H12).** Leer el código real de esa pantalla antes de
   decidir cómo (o si) aplica el orden de adición ahí -- ver punto 4
   de diseño. Si no aplica, dejar solo el orden alfabético en esa
   pantalla y decirlo explícitamente en el anexo, no en silencio.

Cualquier incidencia real que aparezca durante la construcción
(imports que faltan, verificación en dispositivo, etc.) se corrige de
inmediato en la misma sesión, mismo criterio que el resto del
proyecto.

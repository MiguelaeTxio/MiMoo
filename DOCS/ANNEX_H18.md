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

## COMPLETADAS EN S032

Los tres primeros bloques, construidos y en build verde (GitHub
Actions, runs `041e151`, `95d2bdc`, `299e751`), pendientes de
verificación en dispositivo real:

- **Bloque 1 -- migración + propagación a backup/sync.**
  `AppDatabase` 15->16, `MIGRATION_15_16` (4x `ALTER TABLE ADD COLUMN
  addedAt INTEGER NOT NULL DEFAULT 0` sobre `favorite_artists`/
  `favorite_albums`/`favorite_tracks`/`favorite_playlists`, filas
  existentes colapsan a 0, no a un "ahora" inventado -- mismo criterio
  que `isFavorite` en `MIGRATION_2_3`). Las cuatro entidades con
  `addedAt: Long = System.currentTimeMillis()` como default de alta.
  Hallazgo real corregido en el mismo bloque: los DTOs de backup/sync
  (`BackupDto.kt`) de las cuatro entidades no llevaban `addedAt` --
  un Export/Import (H06) o una sincronización automática (H07) habría
  perdido la fecha real. Corregido con default `0L` en los DTOs para
  compatibilidad con backups antiguos, propagado en `toBackupDto()`/
  `toEntity()` y en los tres puntos de importación de
  `BackupImportRepository.kt`. `FavoritePlaylist`, que se exporta por
  NOMBRE (el `playlistId` se remapea siempre en destino), resuelto
  emparejando nombre+`addedAt` en `BackupRepository`.
- **Bloque 2 -- botones de play/aleatorio individuales.** Matriz
  exacta del punto 1 de diseño: Artistas/Álbumes/Listas con
  play+aleatorio del item concreto, Sencillos solo play. Nueva
  `PlaylistRepository.playPlaylistById()` (lógica extraída de
  `PlaylistDetailViewModel.playAll()`, que ahora delega en ella --
  sin duplicación). `FavoritesViewModel`:
  `playArtist()`/`playAlbum()`/`playTrack()`/`playPlaylist()`
  (variantes de un solo elemento sobre las funciones de selección ya
  existentes, refactorizadas a `playArtists()`/`playAlbums()`
  parametrizadas). `FavoritesScreen`: `IconButton`s nuevos junto a la
  estrella en las cuatro filas, deshabilitados durante
  `isGeneratingPopurri` (mismo blindaje que `SelectionHeader` contra
  el bug de doble-tap de S030).
- **Bloque 3 -- control de ordenación.** Nuevo `SortControl`
  (`FavoritesScreen.kt`): dos `FilterChip` de criterio (alfabético/
  adición) + un `IconButton` que solo invierte ascendente/descendente
  del criterio activo -- exactamente el punto 5 de diseño, criterio y
  dirección separados. `sortedByCriterion()`, función genérica
  aplicada en las cuatro pestañas antes de pintar cada `LazyColumn`.
  `PlaylistsTab` gana un `Column` envolvente (antes no tenía ninguna
  cabecera). Hallazgo real corregido en el mismo bloque: ni
  `FavoriteTrackRow` ni `FavoritePlaylistRow` llevaban `addedAt`, así
  que el orden de adición no habría tenido datos en Sencillos ni
  Listas -- añadido a ambos, propagado desde `FavoritePlaylist.addedAt`
  real (no `Playlist.createdAt`, que es un dato distinto) y desde
  `FavoriteTrack.addedAt` para el favorito en streaming.
  **Excepción documentada, no ocultada:** un sencillo ya descargado
  (`isFavorite=true` en `SearchResultTrack`) no tiene NINGÚN
  timestamp de cuándo se marcó -- esa tabla nunca lo tuvo, y añadirlo
  es una migración sobre la tabla más grande y más usada de la app,
  fuera de alcance de H18. Colapsa a `0L`, documentado en el KDoc de
  `FavoriteTrackRow`.

Sin tocar: verificación en dispositivo real de los tres bloques.

---

## Hoja de Ruta para la Siguiente Sesión que retome H18

1. **Verificar en dispositivo real** los bloques 1-3: migración sin
   pérdida de datos existentes, botones de play/aleatorio con la
   matriz exacta por tipo de fila, y el control de ordenación
   (criterio + dirección) en las cuatro pestañas de Favoritos.
2. **Extender ordenación** a `PlaylistsScreen.kt` (H04), Canales (H11)
   y Lista Negra (H16) -- mismas tres entidades que ya tienen
   timestamp (`Playlist.createdAt`, `ChannelSubscription.subscribedAt`,
   `DislikedArtist`/`DislikedTrack.dislikedAt`), sin migración
   adicional.
3. **Explorador (H12).** Leer el código real de esa pantalla antes de
   decidir cómo (o si) aplica el orden de adición ahí -- sus listas
   vienen de MusicBrainz (catálogo remoto paginado), no de una tabla
   local con alta propia. Si no aplica, dejar solo el orden alfabético
   ahí y decirlo explícitamente, no en silencio.

Cualquier incidencia real que aparezca durante la construcción
(imports que faltan, verificación en dispositivo, etc.) se corrige de
inmediato en la misma sesión, mismo criterio que el resto del
proyecto.

# Hito 16 — Lista Negra ("No me gusta") de Artistas y Temas

*Apertura: 2026-08-05 (S029), a petición explícita de Miguel Ángel,
surgida durante la sesión de diseño de H15 (miMooutCast). No encaja en
H15 (que trata de elegir el ancla de la Radio a mano) ni en H08 en
solitario (toca también el ExoPlayer y el Explorador), así que se abre
como hito propio -- mismo criterio que motivó abrir H14.*

---

## Objetivo del hito

Un botón nuevo de "no me gusta" en el ExoPlayer que permite excluir,
de forma GLOBAL y persistente, un artista o un tema concreto de
volver a sonar nunca más en ningún contexto automático de la
aplicación (Radio de H08/H15, popurrí de Favoritos), con una vista de
gestión (CRUD) para ver y deshacer esas exclusiones, y un segundo
punto de entrada para añadir exclusiones desde el Explorador (H12).

---

## Decisiones ya cerradas con Miguel Ángel en S029

1. **Alcance del rechazo de un tema:** cuando se marca un TEMA
   concreto como "no me gusta" (no el artista entero), la exclusión
   cubre CUALQUIER VERSIÓN de ese tema de ese artista -- no solo el
   vídeo de YouTube concreto que sonaba en ese momento. La coincidencia
   se hace por artista+título normalizados (mismo mecanismo de
   `SearchNormalizer` ya usado en el resto de la app para deduplicar y
   cruzar con lo descargado), nunca por ID de vídeo de YouTube.
2. **Alcance del rechazo de un artista:** exclusión total del
   artista de cualquier sesión de Radio y de cualquier popurrí,
   presente y futuro.
3. **Efecto sobre lo ya descargado:** un tema ya descargado que se
   marca como "no me gusta" deja de sonar en cualquier contexto
   automático (Radio, popurrí) -- se queda en el disco/Biblioteca
   local, pero no vuelve a entrar en una cola generada
   automáticamente. Miguel Ángel es explícito: nadie descarga algo que
   no le gusta, pero SÍ puede llegar a la Biblioteca vía un popurrí de
   Favoritos que lo reprodujo desde streaming y el usuario decidió
   descargarlo aparte, o vía Radio -- de ahí que el filtro tenga que
   aplicar también a lo ya descargado, no solo a streaming en directo.
4. **Puntos de entrada:** el botón "no me gusta" vive en el ExoPlayer
   (al pulsarlo, pregunta si el rechazo es del artista o del tema que
   suena en ese momento) y, como segundo punto de entrada, también
   se puede marcar un artista o un tema como "no me gusta" desde el
   Explorador (H12) -- mismo criterio de "global" en ambos sitios,
   una única lista negra, no dos independientes.
5. **Vista de gestión (CRUD):** entrada propia en el menú lateral
   (`MainActivity.kt`/drawer), no dentro de Ajustes.

---

## Contexto técnico -- qué ya existe y qué hace falta revisar antes de construir

### Lo que ya existe y es precedente directo

- **Favoritos** (`FavoriteArtist.kt`, `FavoriteAlbum.kt`,
  `FavoritesRepository.kt`) -- entidades Room + repositorio +
  sincronización a Drive, mismo patrón de "marca global en el
  dispositivo/cuenta que afecta a toda la app" que necesita la lista
  negra. Revisar su forma exacta (campos, DAO, cómo se sincronizan a
  Drive) antes de diseñar `DislikedArtist`/`DislikedTrack` -- no
  inventar un mecanismo de sincronización nuevo si el de Favoritos
  sirve tal cual.
- **`RadioSessionHistoryManager`** -- preferencia SUAVE de artistas
  usados recientemente (`SharedPreferences`, nunca bloquea si el pool
  se queda sin candidatos). La lista negra es lo opuesto: una
  exclusión DURA que sí debe poder dejar sin candidatos a un cupo
  (degradación normal de la cascada 80/10/10 si hace falta, mismo
  principio que ya usa el resto de la Radio ante escasez).
- **`PopurriRepository`** -- cruza contra lo ya descargado por
  nombre normalizado (artista+título) para decidir streaming vs local;
  es el punto natural donde inyectar el filtro de lista negra antes de
  añadir un tema a la cola efímera.
- **`RadioRepository`** (`suggestRelatedArtist()`,
  `fetchRoundCandidate()`, `verifyTrackExists()`) -- punto natural
  donde inyectar el filtro de artista/tema excluido en las tres
  cascadas (diccionario/exploración/disco), antes de aceptar
  cualquier candidato.
- **`PlayerBar.kt`** -- todos los botones del reproductor (expandido y
  mini-barra) ya usan el lenguaje visual de cristal esmerilado con
  chapita ON/OFF (H13). El botón de "no me gusta" debe seguir ese
  mismo lenguaje.
- **`ExplorerScreen.kt`/`ExplorerViewModel.kt`** -- pantalla de
  Artista/Álbum/Canción de H12; punto natural para añadir la acción
  "no me gusta" en el menú de cada fila/página.

### Lo que hace falta construir (sin cerrar el diseño exacto todavía)

1. Entidades Room `DislikedArtist`/`DislikedTrack` + DAO + repositorio,
   con sincronización a Drive equivalente a la de Favoritos (a
   confirmar tras revisar `FavoritesRepository`).
2. Diálogo "¿artista o tema?" al pulsar "no me gusta" en el
   ExoPlayer, sobre el artista/tema que suena en ese momento.
3. Filtro de exclusión inyectado en `RadioRepository` (las tres
   cascadas) y en `PopurriRepository` (antes de encolar cualquier
   tema, streaming o ya descargado).
4. Acción "no me gusta" añadida en `ExplorerScreen` (artista y tema).
5. Pantalla nueva de gestión (CRUD: ver, borrar de la lista negra) +
   entrada propia en el drawer de `MainActivity.kt`/`NavGraph.kt`.

---

## Puntos de diseño SIN cerrar -- decidir con Miguel Ángel antes de escribir código

1. **Ubicación exacta del botón en el ExoPlayer** -- ¿mini-barra y
   expandido, o solo uno de los dos? H13 tocó ambos para
   aleatorio/cíclico; ¿mismo criterio aquí?
2. **Interacción entre "no me gusta" y "favorito"** -- si un
   artista/álbum está en Favoritos y se marca como "no me gusta"
   (o al revés), ¿son estados independientes que pueden coexistir sin
   más, o uno debe impedir/quitar al otro?
3. **Comportamiento si lo que suena AHORA MISMO se marca como "no me
   gusta"** -- ¿la pista en curso se corta y salta a la siguiente de
   la cola de inmediato, o termina de sonar y solo se excluye a partir
   de ahí?
4. **Alcance del CRUD** -- ¿solo ver/borrar lo ya añadido desde el
   reproductor y el Explorador, o también permite añadir un
   artista/tema a mano escribiendo su nombre?

---

## Hoja de Ruta para la Siguiente Sesión que retome H16

La sesión que retome H16 debe cerrar los cuatro puntos de diseño de
arriba con Miguel Ángel primero. Cerrado el diseño:

1. Leer `FavoritesRepository`/`FavoriteArtist`/`FavoriteAlbum`
   completos (directriz 4.1 -- nunca inferir su forma de memoria) para
   decidir si `DislikedArtist`/`DislikedTrack` los replica tal cual.
2. Construir entidades + DAO + repositorio + migración Room +
   sincronización a Drive.
3. Inyectar el filtro de exclusión en `RadioRepository` (tres
   cascadas) y `PopurriRepository` (streaming y ya descargado).
4. Construir el botón + diálogo en `PlayerBar.kt`.
5. Añadir la acción en `ExplorerScreen.kt`.
6. Construir la pantalla CRUD + entrada en el drawer.
7. Verificar en dispositivo real.

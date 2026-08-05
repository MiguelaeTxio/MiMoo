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

## Puntos de diseño -- CERRADOS con Miguel Ángel en S029

1. **Ubicación del botón en el ExoPlayer:** en AMBOS sitios, mini-barra
   y expandido -- mismo criterio que H13 con aleatorio/cíclico.
2. **Interacción "no me gusta" vs "favorito":** SE EXCLUYEN. Marcar
   algo como "no me gusta" quita cualquier estado de favorito que
   tuviera (y viceversa) -- no pueden coexistir.
3. **Si lo que suena AHORA MISMO se marca como "no me gusta":** se
   corta y salta a la siguiente pista de la cola de inmediato.
4. **Alcance del CRUD:** SOLO ver/borrar lo ya añadido desde el
   reproductor y el Explorador -- sin añadir a mano escribiendo un
   nombre.

---

## COMPLETADAS EN S029

Sesión de apertura de H16 (surgió durante el diseño de H15, ver
`DOCS/ANNEX_H15.md`). Sin UI ni motor de Radio tocados todavía --
trabajo real de esta sesión:

1. **Diseño cerrado con Miguel Ángel** -- los cuatro puntos de la
   sección anterior, más las cuatro decisiones de alcance registradas
   arriba en "Decisiones ya cerradas" (exclusión de cualquier versión
   del tema, exclusión total de artista, efecto sobre lo ya
   descargado, doble punto de entrada ExoPlayer+Explorador).
2. **Capa de datos completa, construida y verificada en build verde**
   (commit `31ad6b0`):
   - `DislikedArtist.kt`/`DislikedTrack.kt` (entidades Room). Artista:
     clave primaria simple (`artist`), mismo patrón que
     `FavoriteArtist`. Tema: clave compuesta (`artist`, `title`) --
     DELIBERADAMENTE sin `youtubeId`, para que la exclusión cubra
     cualquier versión del tema (directo/remasterizado/estudio), no un
     vídeo concreto.
   - `DislikedArtistDao.kt`/`DislikedTrackDao.kt` y
     `DislikedArtistRepository.kt`/`DislikedTrackRepository.kt`, con
     `normalizedKeysSnapshot()` en ambos para comprobación en memoria
     O(1) desde Radio/Popurrí (usa
     `SearchNormalizer.normalizeArtistName()` +
     `SearchNormalizer.songTitleKey()`, el mismo mecanismo que ya
     colapsa versiones distintas del mismo tema en el resto de la
     app).
   - `AppDatabase.kt`: versión 13→14, `MIGRATION_13_14` (tablas
     `disliked_artists`/`disliked_tracks`, ambas tablas nuevas, no
     toca ninguna existente). `DatabaseModule.kt`: migración
     registrada, DAOs provistos.
   - Ciclo de backup/sync a Drive cableado ENTERO, leyendo
     `FavoritesRepository`/`BackupRepository`/`BackupImportRepository`/
     `BackupMirrorRepository` reales antes de tocar nada (directriz
     4.1) para no repetir el bug histórico de `favorite_artists`
     quedándose fuera del bundle (2026-08-02): `BackupDto.kt`
     (`DislikedArtistBackupDto`/`DislikedTrackBackupDto`, campo nuevo
     en `BackupBundle`, versión de bundle 4→5, con valor por defecto
     para no romper copias antiguas), `BackupRepository.kt`
     (`buildCurrentBundle()`), `BackupImportRepository.kt`
     (`importDestructively()` y `applyCloudWinsTargeted()`),
     `BackupMirrorRepository.kt` (`BundleComparison`/`compare()`).
   - Decisión técnica explícita: la lista negra NO viaja en
     `importSharedBundle()` (H10, hash de compartición) -- es
     preferencia personal de Radio/Popurrí, no contenido musical
     compartido.
3. **Verificación de compilación real** vía GitHub Actions (no solo
   inspección visual): commit `31ad6b0` build verde.
4. Sin verificación en dispositivo real todavía -- no hay nada visible
   en la UI que probar en este punto (solo capa de datos).

---

## Hoja de Ruta para la Siguiente Sesión que retome H16

Petición explícita de Miguel Ángel al cierre de S029: **empezar por el
CRUD**, no por el orden original de la sección "Contexto técnico".

1. Construir la pantalla nueva de gestión (CRUD: listar y borrar
   artistas/temas de `disliked_artists`/`disliked_tracks` -- sin
   añadir a mano, ver "Puntos de diseño -- CERRADOS" punto 4) + su
   entrada propia en el drawer de `MainActivity.kt`/`NavGraph.kt`.
2. Leer `RadioRepository.kt` completo (2.314 líneas, lógica de
   cascada sensible con varios bugs reales de fondo en sesiones
   anteriores -- S020 a S028) antes de tocarlo. Inyectar el filtro de
   exclusión en las tres cascadas
   (`suggestRelatedArtist()`/`fetchRoundCandidate()`/
   `verifyTrackExists()`), usando
   `normalizedKeysSnapshot()` de ambos repositorios.
3. Mismo filtro en `PopurriRepository`, antes de encolar cualquier
   tema (streaming o ya descargado).
4. Botón + diálogo "¿artista o tema?" en `PlayerBar.kt` (mini-barra y
   expandido), con corte inmediato de la pista en curso si se marca lo
   que suena ahora mismo, y con la exclusión mutua con Favoritos
   (punto 2 de "Puntos de diseño -- CERRADOS").
5. Acción "no me gusta" en `ExplorerScreen.kt` (artista y tema).
6. Verificar en dispositivo real -- CRUD, Radio, Popurrí y
   exclusión mutua con Favoritos.

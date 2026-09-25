# MIMOO — ANEXO HITO 04
# Listas de Reproducción Locales

*Vive en `DOCS/ANNEX_H04.md` — flujo NewFlow Android. Ver estado en
`DOCS/ANNEX_ROUTER.md`.*

---

## NOTA DE APERTURA (S001, 2026-07-02)

Abierto tras cerrar el bloque de trabajo de H03 (PASOS 6, 7, 9
completados, PASO 8 pendiente de verificación física). Miguel Ángel
pidió continuar avanzando funcionalidad mientras esa verificación
esperaba a tener el dispositivo a mano.

---

## OBJETIVO DEL HITO

Listas de reproducción locales: crear, renombrar y borrar listas;
añadir y quitar pistas; reordenarlas; reproducir la lista completa en
el orden guardado. Sin dependencia de descarga ni de MusicBrainz — una
lista puede mezclar pistas descargadas (reproducción offline) y
resultados de búsqueda en streaming.

---

## COMPLETADAS EN S001 (2026-07-02)

**PASO 1 — Modelo de datos.** `Playlist` (`id` autogenerado, `name`,
`createdAt`) y `PlaylistTrackCrossRef` (clave compuesta
`playlistId`+`youtubeId`, `position: Int`, `ON DELETE CASCADE` en
ambas FK — decisión de diseño documentada en el propio archivo: una
playlist no debe dejar referencias huérfanas). `AppDatabase`
`MIGRATION_4_5` (versión 5), sin tocar `search_result_tracks`.

**PASO 2 — DAO + Repository.** `PlaylistDao`: CRUD de playlists,
añadir/quitar/reordenar pista, `JOIN` ordenado por `position` para
listar pistas. `PlaylistRepository`: envoltorio fino,
`addTrackToPlaylist` calcula la siguiente posición automáticamente.

**PASO 3 — Pantallas.** `PlaylistsScreen`/`PlaylistsViewModel`:
listado, crear/renombrar/borrar. `PlaylistDetailScreen`/
`PlaylistDetailViewModel`: pistas en orden, quitar, reordenar
(subir/bajar — drag&drop fuera de alcance), "Reproducir todo".
`NavGraph`: rutas `playlists` y `playlist/{playlistId}`
(`NavType.LongType`). `MainActivity`: entrada "Listas" en el drawer.

**PASO 4 — Añadir a playlist desde otras pantallas.**
`AddToPlaylistDialog` + `AddToPlaylistDialogViewModel` (nuevo,
compartido): lista playlists existentes + crear nueva inline, usado
idéntico desde `SearchScreen` y `LibraryScreen` sin tocar
`SearchViewModel`/`LibraryViewModel`. Botón "Añadir a lista" en
`SearchResultRow` y `LibraryTrackRow`.

**PASO 5 — Reproducción de playlist completa.**
`PlaylistDetailViewModel.playAll()`: pistas descargadas reproducen en
local; pistas sin descargar resuelven streaming vía
`StreamResolver.resolveAudioStreamUrl()` (mismo patrón que
`SearchViewModel.playTrack`). Fallos de resolución individuales no
abortan el resto de la cola — se omite esa pista y se informa cuántas
fallaron.

---

## Nota de sesión S036 (durante la sesión de H12 EN PROGRESO -- mantenimiento transversal, sin PCH)

Se documenta aquí por indicación explícita de Miguel Ángel al cierre
("todo lo que se ha hecho se fija en su hito correspondiente"), sin
tocar el estado del hito (PASO 6 sigue pendiente, sin novedad al
respecto esta sesión).

**Crash real de clave duplicada (S072):** `IllegalArgumentException`
al desplazar el detalle de una lista -- `PlaylistDetailScreen.kt` usa
`itemsIndexed` pero la `key` solo usaba `track.youtubeId`, descartando
el índice. Desde que se permite añadir el mismo tema dos veces a una
lista (confirmación de duplicados), dos filas con el mismo tema
acababan con la misma clave. Corregido incluyendo el índice en la key.

**Simplificación del aviso de tema duplicado (S073):** petición
explícita de Miguel Ángel ("no nos compliquemos la vida") -- el flujo
"¿añadir de todas formas?" (con opción de forzar el añadido) se
sustituye por un aviso puramente informativo de un solo botón, que
además cierra los dos diálogos de golpe (el aviso Y el selector de
listas de debajo) en vez de dejar el segundo abierto pidiendo un
cierre aparte.

**Bug real: la cola solo mostraba un tema de listas muy grandes
(S087):** `PlaylistRepository.playPlaylistById()` resolvía TODAS las
pistas restantes de la lista una detrás de otra (llamada de red por
pista para las que están en streaming) y solo llamaba a `addToQueue()`
UNA VEZ, con todas juntas, al terminar de resolverlas todas -- con una
lista de cientos/miles de temas eso podía tardar minutos u horas antes
de que apareciera nada más allá del primer tema. Corregido añadiendo
cada pista a la cola en cuanto se resuelve, una a una.

## Estado

**PASOS 1-5 completos.**

### PASO 6 — Verificación funcional (PENDIENTE)

Build en verde confirmado (GitHub Actions), pero **no se ha probado
en dispositivo todavía** (a fecha de esta migración, 2026-07-02) —
Miguel Ángel solo probó el flujo de H05 en la sesión de verificación.
Falta: crear/renombrar/borrar una playlist real, añadir pistas
descargadas y en streaming a la misma, reordenar, reproducir completa
y confirmar que ambos tipos suenan.

---

## Fuera de Alcance de Este Hito (explícitamente pospuesto)

- Drag-and-drop real para reordenar (mínimo viable: botones
  subir/bajar).
- Compartir o exportar playlists.
- Playlists inteligentes/automáticas (por género, más escuchadas, etc.).

# MIMOO — ANEXO HITO 08
# Búsqueda de Listas de Reproducción + Música Relacionada ("Radio")

*Vive en `DOCS/ANNEX_H08.md` — flujo NewFlow Android. Ver estado en
`DOCS/MASTER_DOCUMENT.md`. EN PROGRESO.*

---

## NOTA DE APERTURA (S008, 2026-07-11)

Hito nuevo, abierto a petición explícita de Miguel Ángel al cierre de
la sesión de H07 (persistencia de enlaces, sincronización automática,
actualizaciones in-app, PIN, cíclico/aleatorio — completado y
verificado en dispositivo real con dos dispositivos en esa misma
sesión). Dos funciones comparten hito por surgir de la misma
conversación de cierre, sin dependencia técnica real entre ellas — ver
`ANNEX_H07.md` para el precedente de este mismo criterio de
agrupación.

---

## PARTE 1 — Búsqueda de listas de reproducción

**Alcance bien definido, sin ambigüedad.** La pantalla de Playlists
(`PlaylistsScreen.kt`, H04) lista todas las listas de reproducción sin
ningún filtro ni buscador — a diferencia de Biblioteca, que ya tiene
"Filtrar biblioteca" (`LibraryViewModel.onFilterQueryChange()`) desde
H03. Con pocas playlists no se nota; en cuanto haya unas cuantas, hace
falta lo mismo que ya existe en Biblioteca.

**Diseño (a confirmar con Miguel Ángel al empezar a implementar, pero
sin ambigüedad real):**
- Un campo de texto en `PlaylistsScreen.kt`, mismo patrón visual que
  "Filtrar biblioteca".
- Filtra por nombre de playlist — coincidencia parcial, insensible a
  mayúsculas y a acentos vía `util/SearchNormalizer.kt` (NFD +
  eliminación de marcas combinantes). **Corrección de criterio
  (S009):** el filtro de Biblioteca en el que se iba a basar este
  únicamente hacía `trim().lowercase()`, sin normalizar acentos — un
  fallo real de usabilidad, no un patrón a copiar. Se creó
  `SearchNormalizer` como utilidad compartida y se corrigió también
  retroactivamente `LibraryViewModel.recompute()`, no solo el filtro
  nuevo de Playlists.
- Filtrado en memoria sobre la lista ya cargada
  (`PlaylistsViewModel.uiState.playlists`), sin tocar Room — mismo
  patrón que Biblioteca, no hace falta una query nueva.

### Hoja de ruta

**PASO 1.1 — HECHO (S009).** `filterQuery`/`filteredPlaylists` en
`PlaylistsUiState` + `onFilterQueryChange()`/`recompute()` en
`PlaylistsViewModel`, lista filtrada derivada (no sustituye
`playlists`).

**PASO 1.2 — HECHO (S009).** Campo "Filtrar listas" en
`PlaylistsScreen.kt`, mismo estilo que `LibraryScreen.kt`; visible
mientras haya al menos una playlist. Distingue "no hay listas" de
"el filtro no encuentra nada".

**PASO 1.3 — PENDIENTE.** Verificación funcional en dispositivo real:
crear varias playlists con nombres parecidos (con y sin acentos),
confirmar que el filtro reduce la lista correctamente y que
crear/borrar/renombrar sigue funcionando con el filtro activo.

---

## PARTE 2 — Música relacionada ("Radio")

**Alcance deliberadamente sin cerrar.** Miguel Ángel lo planteó
explícitamente como una idea sin resolver, no como un encargo con
diseño ya decidido — cita textual resumida: *"esto es algo muy
subjetivo, no sé cómo lo podríamos abordar (...) no lo tengo muy
claro"*. Este anexo documenta el objetivo y dos posibles mecanismos
técnicos, pero **ninguna decisión de diseño está tomada todavía** —
la sesión que retome esta parte debe cerrarlas con Miguel Ángel antes
de escribir código, no asumirlas.

### Objetivo

Cuando la cola de reproducción se queda sin nada más que reproducir
(la última pista de una playlist/álbum/sencillo termina, sin cíclico
activado — ver H07 PARTE 3), en vez de simplemente parar, sugerir o
encolar automáticamente música "relacionada" con lo que se estaba
escuchando.

Ejemplo dado por Miguel Ángel: escuchando Led Zeppelin, lo razonable
es seguir con Black Sabbath, Deep Purple o Rolling Stones — nunca con
Tiësto. Escuchando Fangoria, en cambio, música electrónica sí podría
encajar. La relación "artista A se parece a artista B" es
inherentemente subjetiva y no está resuelta por MiMoo hoy de ninguna
forma (no hay ningún dato de género/similitud en el modelo actual).

### Preguntas de diseño abiertas (sin resolver, textual de Miguel Ángel)

1. **¿Cuándo se dispara exactamente?**
   - ¿Solo cuando termina la cola entera (última pista, sin cíclico)?
   - ¿También cuando termina una única pista reproducida suelta (no
     parte de una cola con más contenido)?
   - ¿Hace falta un tercer control explícito (algo así como "modo
     exploración"), al lado de cíclico/aleatorio (H07 PARTE 3), para
     activar/desactivar esto explícitamente? Miguel Ángel lo propuso
     y a la vez dudó de si hace falta o si debería ser automático sin
     más, condicionado solo a que cíclico esté desactivado.

2. **¿De dónde sale la relación artista↔artista/tema↔tema?** MiMoo no
   tiene hoy ninguna fuente de datos de género/similitud musical.
   Candidatos a investigar en línea (§4.5) cuando se retome esta
   parte, ninguno confirmado todavía:
   - **Mix automático de YouTube** ("Mix" / listas `RD{videoId}`
     autogeneradas por YouTube a partir de un vídeo) — si `yt-dlp`
     puede resolver esa lista para un `youtubeId` dado, daría
     "relacionados" ya calculados por YouTube mismo, sin que MiMoo
     tenga que modelar similitud musical por su cuenta. Coherente con
     el principio ya establecido del proyecto ("el dato nace de
     YouTube", ver `MASTER_DOCUMENT.md` §0) y con que toda la
     búsqueda actual ya pasa por `yt-dlp` sin cuota (ver §2.3). **Sin
     verificar todavía** si `yt-dlp` expone esto de forma fiable — es
     el primer sitio por donde mirar.
   - Alguna noción de género/etiqueta vía MusicBrainz (ya integrado
     para álbumes, H05) — MusicBrainz tiene relaciones artista↔artista
     y tags de género, pero requeriría diseño propio de "qué tan
     relacionado es esto" en vez de recibirlo ya resuelto.
   - Cualquier heurística propia sobre lo que Miguel Ángel ya tiene en
     su biblioteca/favoritos — no explorado, no se sabe si tiene
     sentido.

3. **¿Se descarga, o solo se reproduce en streaming?** Dado que es
   "exploración" (contenido que igual no interesa conservar), podría
   tener sentido que la música relacionada solo se reproduzca en
   streaming (como H01) sin pasar por el flujo de descarga (H02) a
   menos que Miguel Ángel decida quedársela explícitamente — sin
   decidir.

### Hoja de ruta

**PASO 2.1 (bloqueante, primero de todo)** — Sesión de diseño con
Miguel Ángel para cerrar las tres preguntas de arriba antes de escribir
ninguna línea de código. Sin esto, cualquier implementación estaría
adivinando una decisión de producto que Miguel Ángel dijo explícitamente
que no tiene tomada.

**PASO 2.2** — Una vez cerrado el diseño: verificación en línea (§4.5)
del mecanismo técnico elegido (Mix de YouTube vía `yt-dlp`, MusicBrainz,
u otro) antes de implementar — mismo criterio que el resto del
proyecto con servicios externos.

**PASO 2.3 en adelante** — Sin definir todavía; depende por completo
de lo que salga de PASO 2.1 y 2.2.

---

## Fuera de Alcance de Este Hito

- Cualquier forma de "me gusta"/entrenamiento de preferencias más allá
  de favoritos ya existentes (H03) — no se ha planteado, no está en
  el objetivo descrito por Miguel Ángel.
- Playlists colaborativas o compartidas entre Miguel Ángel y Silvia —
  no mencionado, fuera de alcance salvo que se pida explícitamente.

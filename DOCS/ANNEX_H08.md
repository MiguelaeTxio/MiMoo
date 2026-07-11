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

## PARTE 1 — Búsqueda de listas de reproducción y canales (online)

**Corrección de alcance (S009).** Definición inicial equivocada: se
implementó primero un filtro sobre las playlists **locales** del
propio usuario (`PlaylistsScreen.kt`) — funcionalidad real y ya
mereció la pena, **se queda**, pero no es esto. Lo que pidió Miguel
Ángel es buscar por texto (p. ej. "música de los 90") y encontrar
**listas de reproducción ya creadas por otros usuarios en YouTube**,
para escucharlas en streaming o descargarlas — esto toca la pantalla
de Búsqueda (H01), no Playlists locales.

**Requisito explícito: búsqueda 100% gratuita, cero cuota** — mismo
principio que ya rige toda la búsqueda de MiMoo desde H01 (PASO
2026-07-04, `ExternalLinkResolver.searchYoutube()`). Verificado
(S009): el mecanismo es el mismo scraping directo de la página de
resultados de YouTube que ya usa la búsqueda de vídeos actual
(`youtube.com/results?search_query=...`), no la Data API — cero coste
añadido.

**Filtro por tipo — verificado con captura real de la app de YouTube
(S009):** el propio selector "Filtros de búsqueda" de YouTube
distingue Todo / Vídeos / Shorts / Canales / Listas / Películas — no
existe un filtro de tipo "Podcast" ni "Audiolibro". Valores del
parámetro `sp` (confirmados por varias fuentes independientes, no solo
una):
- **Listas:** `sp=EgIQAw%3D%3D`
- **Canales:** `sp=EgIQAg%3D%3D`

**Colocación en la UI — RESUELTO (S009).** Miguel Ángel confirmó: un
selector dentro de la propia pantalla de Búsqueda ("un selector estaría
bien... así no complicamos mucho la sidebar"), no el drawer de
navegación. Implementado como chips (`FilterChip`, Material 3):
Vídeos / Listas / Canales.

**Aviso de riesgo técnico, no oculto:** el tracker de yt-dlp muestra
temporadas en las que la búsqueda filtrada por tipo (Listas/Canales)
ha dejado de devolver resultados (roto y luego parcheado). Es una
zona menos estable que la búsqueda normal de vídeos. No hay forma de
probarlo en vivo desde el entorno del modelo (`api.github.com` y
similares están permitidos, `youtube.com` no) — la verificación real
solo puede pasar por construirlo y probarlo en dispositivo real.

### Hoja de ruta

**PASO 1.1 — HECHO (S009), pero fuera del alcance real.** Filtro
sobre playlists locales (`PlaylistsViewModel`/`PlaylistsScreen.kt`),
con `SearchNormalizer` insensible a acentos (corregido también
retroactivamente en `LibraryViewModel`). Se conserva como mejora
independiente, no cuenta como progreso de esta PARTE.

**PASO 1.2 — HECHO (S009).** `link_resolver.search_by_type(query, sp,
limit)`: resuelve `youtube.com/results?search_query=...&sp=...` con
`extract_flat`, devolviendo `{"results": [...]}` (id, title, url,
subtitle, thumbnail_url) — playlists o canales según el `sp` pasado.
Nunca lanza por "sin resultados", solo si yt-dlp no puede llegar a la
página.

**PASO 1.3 — HECHO (S009).** Capa Kotlin: `SearchTypeResult`/
`SearchTypeResultsWrapper` (DTOs), `SearchResultType` enum (con los
dos valores `sp` verificados) y `ExternalLinkResolver.searchByType()`.

**PASO 1.4 — HECHO (S009).** Integración en `SearchScreen.kt`:
selector de modo (chips), lista de resultados de playlist/canal
(`SearchTypeResultRow`), y al tocar uno se navega a `ImportLinkScreen`
con la url ya resuelta (`Screen.ImportLink.routeFor(url)`, argumento
de navegación opcional nuevo) — `ImportLinkViewModel` la detecta por
`SavedStateHandle` y llama a `resolveLink()` sola, reutilizando el
100% del flujo ya existente de "Importar enlace" (elegir pistas,
reproducir en streaming o descargar) sin duplicar lógica.

**PASO 1.5 — PENDIENTE.** Verificación en dispositivo real: confirmar
que la búsqueda filtrada por Listas y por Canales sigue devolviendo
resultados hoy (dado el histórico de inestabilidad de yt-dlp en esta
zona), y que abrir un resultado lleva correctamente a "Importar
enlace" con la lista/canal ya resuelto.

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

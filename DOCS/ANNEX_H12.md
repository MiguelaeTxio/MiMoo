# MIMOO — ANEXO HITO 12
# Directorio de Música (Artista/Álbum/Canción) + Favoritos sin Descarga

*Vive en `DOCS/ANNEX_H12.md` — flujo NewFlow Android. Ver estado en
`DOCS/ANNEX_ROUTER.md`.*

---

## NOTA DE APERTURA (S016, 2026-07-19)

Hito nuevo, abierto a petición explícita de Miguel Ángel. Punto de
partida literal: un menú de tres puntos en el reproductor para ver la
página del álbum o del artista del tema que suena, y poder marcar como
favorito un artista o álbum aunque no tengas ni una pista descargada
de él. Al hablarlo se vio que la magnitud real es mucho mayor —
palabras textuales: *"esto es de gran calado, hay que definirlo
bien"*.

**Alcance real acordado en la conversación de apertura (S016):**

1. **Directorio navegable al vuelo, vía MusicBrainz** -- páginas de
   Artista, Álbum y Canción, cruzadas entre sí en ambos sentidos:
   canción→álbum, canción→artista, álbum→artista, álbum→canciones,
   artista→álbumes, artista→sencillos. Nunca se duplica el catálogo de
   MusicBrainz en Room -- se resuelve al entrar en cada página, igual
   que ya hace H05 al buscar un álbum.
2. **Unifica las dos búsquedas que hoy están separadas** -- cita
   textual: *"nosotros ahora mismo buscamos un álbum o buscamos un
   sencillo... hacemos una búsqueda entre todo, sin tener en cuenta
   páginas de artista ni páginas de álbum... vamos a unificarlo todo
   en una explotación online a través del directorio"*. H01 (búsqueda
   de sencillos por YouTube) y H05 (búsqueda de álbumes por
   MusicBrainz+artista) siguen siendo los mecanismos de búsqueda por
   debajo -- lo que cambia es que CUALQUIER resultado (canción, álbum
   o artista) deja de ser un resultado suelto y se convierte en una
   puerta de entrada al resto del grafo.
3. **Streaming + descarga al vuelo desde cualquier página**, tenga o
   no algo descargado ya -- cita textual: *"al entrar ahí habría que
   poder descargar al vuelo, o sea, descargar y tocar en streaming"*.
   Mismo mecanismo de emparejamiento MusicBrainz↔YouTube que ya usa
   H05 para descargar álbumes completos, pero ahora también para
   "escuchar ya" pista a pista sin descargar.
4. **Favoritos desacoplados de la descarga.** Cada página muestra DOS
   anotaciones independientes:
   - **Descargado**: cuántos sencillos y cuántos álbumes de ese
     artista/álbum ya están en la biblioteca local -- esto NO necesita
     tabla nueva, se deriva de `SearchResultTrack` por nombre, igual
     que hoy hace `LibraryViewModel`.
   - **Favorito**: independiente de si hay algo descargado. Álbum
     favorito ya existe (`FavoriteAlbum`, ver abajo); artista favorito
     es concepto nuevo, no existe today.
5. **Menú de tres puntos en el reproductor** es solo UNA puerta de
   entrada más al directorio (ver álbum/artista del tema que suena) --
   la búsqueda unificada (punto 2) es la puerta principal.

---

## Punto de partida real ya construido (no arrancar desde cero)

- **`FavoriteAlbum(artist, album)`** (`data/local/entity/FavoriteAlbum.kt`,
  `FavoriteAlbumRepository`, S005) -- álbum favorito, clave compuesta
  por TEXTO (artista+álbum), no por MusicBrainz ID. Ya independiente
  de si el álbum está descargado entero. Patrón a seguir para la tabla
  de artista favorito nueva, por consistencia con el resto de la app.
- **`SearchResultTrack.isFavorite`** -- favorito por pista suelta,
  siempre de algo ya descargado. No se toca, sigue siendo un concepto
  distinto (favorito de PISTA CONCRETA descargada, no de artista/álbum
  al completo).
- **Ningún dato de MusicBrainz se persiste hoy con MBID** -- todo el
  cruce entre "lo que tienes descargado/favorito" y "lo que devuelve
  MusicBrainz" se hace en el resto de la app por NOMBRE de texto
  (artista/álbum), nunca por ID (`FavoriteAlbum`, matching de Radio en
  H08, etc.). Para "unificar con lo que ya tenemos" (petición explícita
  de Miguel Ángel), el criterio por defecto de este hito es seguir ese
  mismo patrón por nombre, salvo que la sesión de diseño decida lo
  contrario para un caso concreto (ver homónimos, más abajo).
- **H05 (`RadioRepository`-adjacent, `AlbumSearchViewModel`)** ya
  resuelve un álbum completo de MusicBrainz y empareja cada pista con
  un vídeo de YouTube por minutaje -- mecanismo a REUTILIZAR para
  "reproducir en streaming ya" desde una página de álbum/artista, no
  reconstruir.
- **H01 (`SearchViewModel`/`SearchScreen`)** ya resuelve búsqueda
  suelta de sencillos contra YouTube -- sigue siendo la fuente de
  candidatos de vídeo cuando se resuelve una pista al vuelo.
- **`ExternalLinkResolver.searchYoutube()`** ya soporta filtro de
  canal (reutilizado en H11) -- no directamente relevante aquí, pero
  confirma que la capa de búsqueda de YouTube ya admite variantes de
  tipo de resultado.

---

## Problema real detectado en la conversación de apertura, sin resolver todavía

**Artistas homónimos.** H05 ya lidia con esto a nivel de emparejar UNA
canción con heurística de minutaje (si hay ambigüedad, el margen de
error de una sola pista es tolerable). Una "página de artista", en
cambio, necesita fijar UN MusicBrainz ID concreto de una vez -- si hay
dos artistas distintos con el mismo nombre, hace falta:
1. Un paso de desambiguación la primera vez que se entra a la página
   de ese nombre (mostrar 2-3 candidatos con país/década/género para
   elegir).
2. Recordar la elección para las siguientes veces -- probablemente una
   tabla nueva (`artist_name` -> `mbid` elegido), a diseñar.

Esto queda **explícitamente sin resolver** -- es uno de los puntos
centrales de la sesión de diseño que abre este hito.

---

## S017 -- Diseño cerrado

Sesión de diseño puro (sin código), mismo patrón que S013 para Radio
(H08). Los siete puntos de la hoja de ruta anterior quedan cerrados
con Miguel Ángel:

1. **Pantallas y navegación: tres páginas separadas.** `NavGraph.kt`
   gana tres rutas nuevas, clave por nombre de texto (consistente con
   `FavoriteAlbum`):
   - `artist/{artistName}` → `ArtistScreen`
   - `album/{artistName}/{albumName}` → `AlbumScreen`
   - `song/{artistName}/{songTitle}` → `SongScreen`

   Cruces en ambos sentidos: `SongScreen` enlaza a su álbum (si
   pertenece a uno) y a su artista; `AlbumScreen` muestra tracklist
   completa (cada pista tappable a `SongScreen`) y enlaza a su
   artista; `ArtistScreen` lista álbumes (tappable a `AlbumScreen`) y
   sencillos sueltos (tappable a `SongScreen`).

2. **Búsqueda unificada -- una sola pantalla, cinco tipos de
   resultado.** Sustituye a `SearchScreen` (H01) y
   `AlbumSearchScreen` (H05) como único punto de entrada de búsqueda
   de todo el catálogo online. Resultados tipados por
   icono/sección -- canción, álbum, artista, lista de reproducción,
   canal -- ninguno queda fuera ni tiene pantalla de búsqueda aparte:
   - Canción → `SongScreen` (directorio nuevo).
   - Álbum → `AlbumScreen` (directorio nuevo).
   - Artista → `ArtistScreen` (directorio nuevo).
   - Lista de reproducción → destino ya existente (detalle de lista,
     sin cambios de H04/H10).
   - Canal → destino ya existente (pantalla de canal/suscripción de
     H08 Parte 1/H11, sin cambios).

   Los motores de búsqueda por debajo no cambian (YouTube para
   sencillos/listas/canales, MusicBrainz para álbumes/artistas) --
   se unifica el punto de entrada y la presentación de resultados
   (una query, reparto a las fuentes que corresponda, lista
   mezclada), no cinco búsquedas en paralelo.

3. **`FavoriteArtist(artist)`**, clave por nombre TEXT -- mismo
   patrón que `FavoriteAlbum`, sin campos extra.

4. **Homónimos -- dos mecanismos distintos:**
   - **Normalización** (para que variantes del mismo artista no
     generen dos páginas, ej. "The Chemical Brothers" / "Chemical
     Brothers"): `normalizeArtistName()` quita `"The "` inicial, pasa
     a minúsculas y recorta espacios/acentos, solo para
     matching/routing -- el nombre mostrado en pantalla sigue siendo
     el canónico de MusicBrainz. Asunción declarada y confirmada: solo
     `"The "` en inglés; no se tocan artículos en español (`"Los"`,
     `"La"`, etc.) por falta de caso real detectado -- revisar si
     aparece uno.
   - **Homónimos reales** (dos artistas *distintos* con el mismo
     nombre normalizado): tabla nueva
     `ArtistDisambiguation(normalizedNameKey TEXT PRIMARY KEY,
     chosenMbid TEXT)`. Se dispara la primera vez que se entra a una
     página de artista y MusicBrainz devuelve más de un candidato con
     MBID distinto para ese nombre normalizado -- se muestran 2-3
     candidatos (país/década/género) para elegir, y la elección se
     persiste ahí.

5. **Streaming/descarga al vuelo -- dos botones separados**, por
   consistencia con el resto de la app:
   - Por pista: ▶ **Reproducir** (streaming inmediato, reutiliza el
     emparejamiento MusicBrainz↔YouTube de H05, no descarga nada) y
     ⬇ **Descargar** (flujo yt-dlp existente, añade a biblioteca
     local).
   - Por álbum: **Reproducir álbum** (cola en streaming, orden de
     tracklist) y **Descargar álbum** (flujo de descarga completa ya
     existente en H05).

6. **Menú de tres puntos en el reproductor -- confirmado.** Dos
   opciones: "Ver álbum" / "Ver artista". Si la pista actual no tiene
   metadatos estructurados (sencillo suelto de YouTube), se intenta
   resolver primero con `parseArtistFromTitle()` (mismo patrón que
   Radio); si tampoco da nada, la opción se oculta.

7. **Sección "Descargado" -- criterio de conteo:**
   - Página de Álbum: `"X de Y pistas descargadas"` (deriva de
     `SearchResultTrack` por nombre, igual que `LibraryViewModel`
     hoy); se etiqueta "Álbum completo" solo si X==Y.
   - Página de Artista: cuenta por separado álbumes completos,
     álbumes parciales y sencillos sueltos descargados, ej.:
     `"2 álbumes completos, 1 álbum parcial, 3 sencillos"`.

---

## Hoja de Ruta de Construcción para la Siguiente Sesión (ejecutable, sin contexto adicional)

1. **Entidades y DAO:**
   - `FavoriteArtist(artist: String)` (clave primaria `artist`),
     `FavoriteArtistDao`, `FavoriteArtistRepository` -- mismo patrón
     que `FavoriteAlbum`/`FavoriteAlbumRepository` (S005). Migración
     Room nueva (siguiente número tras `MIGRATION_9_10`, ver H09).
   - `ArtistDisambiguation(normalizedNameKey: String PRIMARY KEY,
     chosenMbid: String)`, DAO y repositorio a juego.
   - Leer primero `data/local/entity/FavoriteAlbum.kt` y el `Database`
     real (directriz §4.1 vinculante) antes de escribir nada -- no
     inferir nombres de campo.

2. **`normalizeArtistName()`** -- función util nueva (proponer
   ubicación: junto a otros helpers de texto ya existentes, ej. cerca
   de `parseArtistFromTitle()` de Radio). Quita `"The "` inicial
   (case-insensitive), minúsculas, trim de espacios/acentos. Solo para
   matching interno -- nunca para mostrar en UI.

3. **Tres pantallas nuevas** (`ArtistScreen`, `AlbumScreen`,
   `SongScreen`) + rutas en `NavGraph.kt` (directriz §4.2: no añadir
   rutas hasta que la pantalla exista). Cada una resuelve datos de
   MusicBrainz al entrar (sin persistir catálogo en Room, igual que ya
   hace H05), cruza con `FavoriteArtist`/`FavoriteAlbum` y con
   `SearchResultTrack` (por nombre) para las secciones
   Favorito/Descargado.

4. **Flujo de desambiguación:** al entrar a `ArtistScreen`, si
   MusicBrainz devuelve >1 MBID para el nombre normalizado y no hay
   fila en `ArtistDisambiguation`, mostrar diálogo/pantalla de
   selección (2-3 candidatos, país/década/género) antes de resolver el
   resto de la página; guardar elección.

5. **Búsqueda unificada:** nueva pantalla que sustituye a
   `SearchScreen` (H01) y `AlbumSearchScreen` (H05) como entrada
   principal; reparte la query a YouTube (sencillos/listas/canales) y
   MusicBrainz (álbumes/artistas), presenta resultados tipados y
   mezclados. Revisar `NavGraph.kt` para retirar las entradas de menú
   que apuntaban a las dos pantallas viejas y apuntarlas a la nueva.

6. **Botones de Reproducir/Descargar** por pista y por álbum en las
   tres pantallas nuevas, reutilizando el emparejamiento existente de
   H05 (`AlbumSearchViewModel`-adjacent) para resolver
   MusicBrainz→YouTube pista a pista.

7. **Menú de tres puntos en el reproductor:** dos opciones nuevas
   ("Ver álbum" / "Ver artista") navegando a las pantallas nuevas,
   con fallback a `parseArtistFromTitle()` y ocultación si no resuelve
   nada.

8. **Verificación:** inspección visual (`.kt`, directriz de
   `newflow-android-edit` PASO 4) tras cada bloque; commit por bloque
   cerrado (entidades, luego pantallas, luego búsqueda unificada,
   luego menú del reproductor) -- no acumular varios bloques sin
   commitear.

No queda ningún punto de diseño abierto para arrancar la
construcción.

---

## Incidencia real detectada, S033 (2026-08-17)

Miguel Ángel, revisando la app tras meses sin tocar H12 (S018 lo dio
por construido y compilando en verde, pendiente solo de verificación
en dispositivo -- ver `DOCS/ANNEX_ROUTER.md` entrada del
2026-07-19/S018): dos fallos reales, cita textual: *"tenemos un
problema en el explorador, lo primero es que carece de campo búsqueda
para buscar en musicbrainz, por otro lado cuando añado favoritos,
tanto artistas como álbumes, no persisten"*.

Sin diagnosticar todavía -- ningún código leído en esta sesión sobre
estos dos síntomas. Dos hipótesis sin confirmar, ninguna de las dos
debe darse por buena sin leer el código real primero:

1. **Campo de búsqueda ausente en el Explorador**: la búsqueda
   unificada (S017 punto 2, más arriba) se diseñó como pantalla
   propia que sustituye a `SearchScreen`/`AlbumSearchScreen`, no
   necesariamente embebida DENTRO del Explorador -- puede que el
   fallo real sea de navegación/descubribilidad (no hay forma de
   llegar a ella desde el Explorador) y no de ausencia de
   funcionalidad. Puede también que nunca se completara del todo en
   S018 pese a constar como cerrada. Verificar contra el `NavGraph.kt`
   y el composable real antes de suponer nada.
2. **Favoritos de artista/álbum que no persisten**: comprobar primero
   si `FavoriteArtist`/`FavoriteArtistDao` llegaron a crearse de
   verdad (punto 1 de la hoja de ruta de construcción, más arriba) o
   si la entidad quedó a medias; comprobar la migración Room asociada
   está registrada en la base de datos real (mismo patrón de
   verificación que ya dio problemas reales en H07/H09 con
   migraciones olvidadas); comprobar que el botón de favorito de
   `ArtistScreen`/`AlbumScreen` llama de verdad al repositorio y no a
   un estado solo en memoria del `ViewModel` que se pierde al salir de
   la pantalla.

## COMPLETADAS EN S034 (2026-08-23)

Sesión que retomó los dos síntomas de S033, diagnosticados esta vez
contra código real antes de tocar nada:

1. **Campo de búsqueda del Explorador -- diagnóstico confirmado: no
   era un fallo funcional, era de descubribilidad.** La búsqueda
   unificada (S017 punto 2) sí existía y sí consultaba MusicBrainz
   para álbumes/artistas -- pero como pantalla "Búsqueda" aparte en el
   drawer, sin ningún campo dentro del propio Explorador. Decisión de
   Miguel Ángel: añadir un campo de búsqueda embebido DENTRO del
   Explorador que dispare la misma búsqueda unificada (MusicBrainz +
   YouTube) sin navegar a la pantalla aparte. `ExplorerViewModel`
   amplía su estado con los mismos campos que `UnifiedSearchViewModel`
   (duplicado a propósito, mismo criterio que el resto del proyecto
   para pantallas que reutilizan un motor ya construido);
   `ExplorerScreen` gana campo de texto + chips de filtro + las cinco
   secciones tipadas; `NavGraph.kt` amplía la ruta del Explorador con
   `onOpenSong`/`onOpenAlbum`/`onOpenExternalLink`, mismo patrón que la
   ruta de Búsqueda. Commit `692eb75`, build verde.
2. **Favoritos de artista/álbum que no persistían -- causa real
   confirmada, no eran los sospechosos habituales.** Entidad, DAO,
   repositorio y migración de `FavoriteArtist` estaban todos
   correctos. La causa real: `AutoSyncPusher.executeIfConnected()` --
   punto único obligatorio por el que pasa cualquier mutación de
   favorito -- comprobaba conectividad con
   `NetworkConnectivityChecker.isConnected()`
   (`NET_CAPABILITY_VALIDATED`), el mismo indicador ya documentado en
   la propia clase como causa confirmada del bug real "Radio detenida"
   por falsos negativos transitorios con conexión real funcionando. El
   fix ya existía (`hasRealInternetAccess()`, sonda HTTP real) y ya se
   usaba en `RadioRepository`, pero nunca se replicó en
   `AutoSyncPusher`. Corregido para usar la misma sonda real. Commit
   `a61ee94`, build verde.
3. **Incidencia puntual de UI, sin relación con H12**: sidebar
   (`MainActivity.kt`) vuelta a tamaño de letra/icono normal (petición
   de Miguel Ángel, revirtiendo la compresión a 3/4 de S032) y hecha
   scrollable (`Column` + `verticalScroll()`) para que quepan las 15
   opciones sin necesidad de comprimir nada. Commit `4860687`, build
   verde.

Los tres puntos quedan pendientes de **verificación en dispositivo
real** de Miguel Ángel -- ninguno se ha probado todavía fuera de
GitHub Actions.

## Cascada de incidencias reales sobre H15 (miMooutCast, PAUSADO) -- S034

Mismo patrón que S031/S033: incidencias puntuales sobre código de un
hito pausado, sin PCH (H12 sigue EN PROGRESO durante toda esta
cascada). Detalle técnico completo en `DOCS/ANNEX_H15.md`, sección
"COMPLETADAS EN S034" -- resumen aquí:

1. Miguel Ángel entregó dos exportaciones (`mimooutcast_database_json.txt`,
   `mimooutcast_database2_json.txt`, la segunda superconjunto exacto
   de la primera) y un log de debug de 67 minutos. Análisis (sin
   código, orden explícita: *"no generes código... vamos a analizar el
   resultado antes de tomar una decisión"*): 41 géneros llevaban
   atascados a 0 temas desde la primera pasada, reabriéndose sin fin
   por el propio fix de reapertura de S033, con tres causas de fondo
   medidas en el log real (escasez de datos en MusicBrainz, red
   intermitente, verificación final que rechaza siempre a los pocos
   candidatos que aparecen).
2. Decisión de Miguel Ángel: *"Eliminamos estos géneros que no llevan
   a ningún sitio."* Primer intento equivocado (tocó `genre_tree.json`
   en vez del catálogo de miMooutCast, afectando sin necesidad a la
   Radio) -- corregido tras verificación posterior. Miguel Ángel amplió
   después la orden: *"que no deben molestar en ningún sitio, ni en la
   radio."* Fix final: `GenreTree.isBarren()` (fuente única) + corte
   ANTES de cualquier llamada de red en `RadioRepository.
   suggestWorkForGenre()`/`findCandidates()` -- los dos puntos de
   entrada reales a MusicBrainz compartidos por Radio y miMooutCast.
   Commits `a5e0d95` (equivocado), `7fb5d9a` (corrección) y `f03a741`
   (ampliación a Radio).
3. **Objetivo de fondo aclarado por Miguel Ángel, con fuerza**: todo el
   mes de generación en dispositivo era el MEDIO, nunca el fin -- el
   fin siempre fue que la lista viajara empaquetada en el APK, sin
   tener que regenerarse nunca más. `mimooutcast_seed.json` (copia
   exacta del export real, 22.220 temas/536 géneros) bundleado en
   `app/src/main/assets/`. Commit `7fb5d9a`.
4. **Motor de cola instantánea + búsqueda en paralelo + curación de
   enlaces rotos**, método completo dictado por Miguel Ángel:
   generaliza a todos los géneros un mecanismo que ya existía, probado,
   solo para Clásica (`ClassicalValidatedLinksRepository`). Piezas
   nuevas: `MimooutcastSeedRepository`, `MimooutcastBrokenLinksLogger`
   (enlaces rotos + sustituto por género, contador = tamaño de lista,
   aviso a partir de 10). `PlayerManager` amplía `QueueItem`,
   `startRadioFromManualAnchor()`, `fetchSimpleManualCandidate()`,
   `onPlayerError()`, `onMediaItemTransition()` y `clearQueue()`.
   Botón "Compartir enlaces rotos" en Ajustes, mismo mecanismo que el
   ya existente para la base de datos. Commit `82c73a1`, build verde.

**Nada de esta cascada se ha verificado en dispositivo real todavía.**

## Hoja de Ruta para la Siguiente Sesión que retome H12

Sustituye a la hoja de ruta anterior (ya ejecutada en S034).

1. **Verificar en dispositivo real** los tres puntos de "COMPLETADAS
   EN S034" de arriba: búsqueda embebida en el Explorador, persistencia
   de favoritos de artista/álbum, y la sidebar (tamaño normal +
   scroll).
2. Si la verificación revela algo roto, corregir con evidencia real
   (log/captura de Miguel Ángel), mismo criterio que el resto del
   proyecto -- no reabrir el diagnóstico sin evidencia nueva.
3. **Sin más síntomas pendientes de H12** más allá de la verificación
   -- ambas hipótesis de S033 quedaron confirmadas y corregidas.

## Cascada de incidencias reales sobre H15/H16/H17/H18 -- S035 (2026-08-23/28)

Mismo patrón que S034: incidencias puntuales sobre código de otros
hitos, sin PCH (H12 sigue EN PROGRESO durante toda la sesión, sin
tocarse). Sesión larguísima -- resumen organizado por área, detalle
completo en el historial real de commits (`git log` entre
`8dd156b3e2e94aef8059193ad3c902c3291ed7bb` y el commit de cierre).

**H15 (miMooutCast) -- fuentes de datos nuevas para el diccionario de
éxitos:**
1. Cosecha de LOS40 (número uno semanal, 1966-2025, ya existente de
   antes) fusionada por fin en el bloque `intl` (antes solo `es`) --
   1990 pasa de 71%/29% es/intl a ~51%/49%.
2. Tres fuentes nuevas cosechadas y fusionadas: Spotify100 ("Las 100
   canciones del año en España", 1960-1986, 2.815 canciones tras
   validar), MUZIKALIA ("Las 100 mejores de la década", 80s+90s, 200
   canciones, cosecha manual sin pipeline), y Spotify oficial para los
   años 50 ("All Out 50s"/"Soft 50s", cierra un hueco que no existía
   en ninguna fuente).
3. **Bug real de arquitectura, encontrado tarde**: `FIRST_DECADE=1960`
   en los scripts de fusión metía cualquier canción de los 50 dentro
   del bloque "1960" en vez de crear su propio bloque -- corregido a
   1950.
4. **Semilla de década VALIDADA contra YouTube** (mismo mecanismo ya
   probado para género): primer intento vía GitHub Actions falló casi
   entero (YouTube bloquea las IPs de centro de datos, "Sign in to
   confirm you're not a bot") -- igual que la semilla de género en su
   día, tuvo que generarse EN EL DISPOSITIVO
   (`MimooutcastDecadeDatabaseBuilder`, nuevo). Resultado real: 2.815
   canciones validadas, 8 décadas completas.
5. **Bug real de prioridad, encontrado el mismo día de terminar la
   validación**: la semilla validada solo se usaba con "Conocido en
   España" APAGADO -- con el interruptor encendido (el caso más común)
   la búsqueda ni la tocaba, cayendo entera en el camino lento de
   siempre. Corregido: la semilla manda siempre para década sola, sea
   cual sea el interruptor.
6. **Bug real, encontrado después**: las tres semillas (género/década/
   clásica) nunca fijaban `artworkUri` en el atajo de `knownYoutubeId`
   -- con la semilla de década ahora dominante, esto se convirtió en
   "todo es el logo genérico". Corregido con la miniatura real de
   YouTube (`i.ytimg.com/vi/{id}/hqdefault.jpg`).
7. Candidatos del diccionario que fallaban la resolución se repetían
   sin fin en la misma sesión (nunca se marcaban como descartados) --
   corregido con un set de exclusión nuevo por sesión.
8. Diseño de "barajar y encolar todo lo conocido, lo descubierto va al
   final" generalizado a década sola (ya existía para género/clásica).
9. Botones de miMooutCast: `if (loadingLabel != null) return` ignoraba
   en silencio un segundo toque si el estado ya estaba "cargando" por
   cualquier motivo -- corregido para cancelar y reiniciar limpio en
   cada toque, sin cortar la pista en curso.
10. URLs de streaming caducadas (cola construida hace horas): antes se
    saltaba la pista sin más; ahora se reintenta una vez con una URL
    fresca antes de darla por perdida.

**H16 (Lista Negra) -- dos bugs reales de fondo:**
1. La comprobación exigía coincidencia EXACTA de nombre (pertenencia a
   un `Set`) -- si el artista vetado venía con texto de más
   (colaboraciones, variaciones de parseo), nunca coincidía. Corregido
   con `containsDislikedArtist()`, contención con límite de palabra.
2. Duetos (ej. "Julio Iglesias, Diana Ross"): el campo `artist`
   guardado a veces es solo UNO de los dos nombres -- el vetado podía
   no estar ni en el campo comparado. Corregido comprobando también el
   título completo.
3. Recopilatorios propios (listas de Miguel Ángel): `PlaylistRepository.
   playPlaylistById()` nunca comprobaba "no me gusta" a nivel de TEMA
   en absoluto -- un tema marcado seguía sonando al reproducir la
   lista. Corregido con `DislikedTrackRepository.normalizedKeysSnapshot()`.

**Llamadas telefónicas -- evolución completa, varios intentos:**
1º intento: foco de audio explícito (`AudioAttributes` +
`handleAudioFocus=true`) -- rompió miMooutCast por completo (la
construcción de `ExoPlayer` lanzaba una excepción real en dispositivo,
pese a compilar bien), revertido.
2º intento: `AudioFocusRequest` manual, reaccionando solo a
`AUDIOFOCUS_LOSS_TRANSIENT`/`GAIN` -- mejoró pero seguía fallando (se
activaba a mitad de llamada por eventos espurios de Android en cambios
de ruta de audio).
3º intento (el bueno para llamadas, pero con un efecto secundario
grave): `TelephonyManager` real (`READ_PHONE_STATE`, nuevo permiso) --
detección fiable de llamada, PERO seguía coexistiendo con el
`AudioFocusRequest` de arriba, que pausaba ante CUALQUIER notificación
de cualquier app, no solo llamadas.
**Decisión final de Miguel Ángel**: quitar el `AudioFocusRequest` por
completo -- "antes, cuando sonaba una notificación, la música seguía
sonando igual... con el tema del foco eso lo hemos roto". Se conserva
únicamente `TelephonyManager` (no pide foco, no le afecta el problema
de las notificaciones) para llamadas reales. Tras quitar el
`AudioFocusRequest`, quedó viva por error una red de seguridad vieja
que deshacía la reanudación al colgar -- corregida.
**Pendiente para la siguiente sesión** (Miguel Ángel, en el cierre):
el volumen de audio se sigue bajando solo cuando otra app usa el
sonido y hay que subirlo a mano -- comportamiento distinto del descrito
arriba (no es pausa, es "ducking"), sin diagnosticar todavía en esta
sesión.

**H17 (Karaoke) -- dos fixes de UI reales:**
1. Panel de letras sincronizadas más pequeño (1/9 de pantalla) que el
   de letra plana (1/3) -- decisión original de S031 revertida a
   petición de Miguel Ángel, mismo tamaño en los dos casos.
2. Desplazamiento brusco de la línea resaltada (subía arriba del todo
   y luego bajaba al centro, dos animaciones seguidas) -- corregido a
   una sola animación directa, estimando la altura de línea de
   antemano en vez de medir tras el primer salto.

**Otros, sin hito claro asignado:**
- Refuerzo de volumen (`LoudnessEnhancer`, 0-12dB) configurable en
  Ajustes, cambia en caliente.
- Cola de reproducción (H18): se desplaza sola al tema actual al
  abrirla, y lo mantiene siempre visible mientras avanza sola con la
  pantalla abierta. Vaciar la cola ya no corta la pista en curso, solo
  el resto.
- Botón "+" en el reproductor para añadir el tema actual a una lista.
- Filtro de texto en Favoritos (las cuatro pestañas).
- Logo de la app como carátula de respaldo cuando de verdad no hay
  ninguna (emisoras de radio online, etc.) -- en el reproductor, mini-
  reproductor y notificación.

**Nada de esta cascada se ha verificado en dispositivo real de forma
exhaustiva todavía** -- varios puntos SÍ fueron confirmados por Miguel
Ángel durante la propia sesión (Lista Negra, llamadas tras el último
ajuste, semilla de década, carátulas, karaoke), otros quedan
pendientes de una pasada de verificación completa.

## Pendiente para abrir la siguiente sesión (palabras de Miguel Ángel al cierre)

1. **Volumen que se baja solo**: "se baja solo y hay que estar
   subiendo el volumen cada vez que otra aplicación toca el volumen.
   Antes estaba mejor pq no había que tocar nada. Volvemos a dejarlo
   como estaba originalmente." Sin diagnosticar -- el `AudioFocusRequest`
   que causaba pausas ya se quitó esta sesión (ver cascada de
   llamadas, arriba), pero este síntoma de "ducking" (bajada de
   volumen, no pausa) parece distinto y sigue reportado tras ese
   revert. Empezar leyendo si queda algún mecanismo de audio (aparte
   de `TelephonyManager`, que no debería tocar volumen) que pueda
   estar causando esto.
2. **Rediseño de la interfaz del ExoPlayer**: "tenemos muchos
   controles, hay que añadir compartir el tema que se está tocando,
   habría que poner una fila con los controles de reproducción y otra
   fila con like/dislike/add2list/download/share." Sin diseño cerrado
   todavía -- sesión de diseño antes de tocar código, mismo criterio
   que el resto del proyecto para cambios de alcance abierto.

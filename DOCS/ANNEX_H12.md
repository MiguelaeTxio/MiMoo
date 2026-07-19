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

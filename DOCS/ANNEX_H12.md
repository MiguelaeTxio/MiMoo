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

## Hoja de Ruta para la Siguiente Sesión -- DISEÑO PURO, SIN TOCAR CÓDIGO

Decisión explícita de Miguel Ángel: *"es mejor diseñar y luego
construir"* -- mismo patrón que S013 para Radio (H08). La sesión que
retome este hito NO escribe código; cierra el diseño punto por punto,
lo deja documentado en este mismo anexo, y solo entonces (sesión
siguiente a esa) se construye.

Puntos a cerrar, con Miguel Ángel, en esa sesión de diseño:

1. **Pantallas y navegación exacta.** ¿Página de Artista y página de
   Álbum como dos Composables/rutas nuevas en `NavGraph.kt`? ¿Existe
   página de "Canción" propia o una canción siempre se representa
   dentro de la página de su álbum? Dibujar el grafo de navegación
   completo (qué botón/tap lleva a dónde) antes de tocar código.
2. **Unificación de búsqueda.** ¿Una sola pantalla de búsqueda con
   resultados tipados (canción/álbum/artista) reemplazando a
   `SearchScreen` (H01) y `AlbumSearchScreen` (H05), o conviven las
   dos pantallas actuales y se añade navegación cruzada entre sus
   resultados? Decidir el punto de entrada único.
3. **Modelo de datos de favorito de artista.** Tabla nueva
   `FavoriteArtist`, clave por nombre (mismo patrón que
   `FavoriteAlbum`) salvo que se decida lo contrario. Confirmar
   nombre de tabla/campos y si necesita más que `(artist)`.
4. **Desambiguación de homónimos.** Diseñar el flujo de selección +
   la tabla de "artista elegido" (ver sección de arriba). Decidir
   dónde vive esa persistencia y cómo se dispara (¿en cuanto se detecta
   más de un candidato al buscar, o solo al entrar a una página?).
5. **Streaming al vuelo desde una página.** Confirmar que reutiliza el
   emparejamiento MusicBrainz↔YouTube de H05 pista a pista, y decidir
   la UI exacta: ¿"Reproducir" vs "Descargar" como dos acciones
   separadas por pista/álbum, o un único botón que reproduce y de paso
   pregunta si se quiere conservar?
6. **Menú de tres puntos en el reproductor.** Confirmar las dos
   opciones exactas ("Ver álbum" / "Ver artista" del tema que suena) y
   qué pasa si la pista actual no tiene artista/álbum estructurado
   (pista suelta de YouTube sin metadatos de H05) -- ¿se oculta la
   opción, o se intenta resolver por título parseado, mismo patrón que
   ya usa Radio en `parseArtistFromTitle()`?
7. **Sección "Descargado" en cada página.** Confirmar el criterio
   exacto de conteo ("2 álbumes, 1 sencillo") y si distingue álbum
   completo descargado vs. álbum parcialmente descargado.

Al cerrar esa sesión de diseño, esta misma sección de este anexo se
sustituye por la hoja de ruta de construcción, ya ejecutable sin
contexto adicional -- mismo patrón que siguió `ANNEX_H08.md` con la
sección "S013 -- diseño cerrado" seguida de "COMPLETADAS EN S014".

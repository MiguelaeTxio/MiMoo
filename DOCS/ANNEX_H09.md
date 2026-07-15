# MIMOO — ANEXO HITO 09
# SHOUTcast — Radios Online del Mundo por Género/Tema/Década

*Vive en `DOCS/ANNEX_H09.md` — flujo NewFlow Android. Ver estado en
`DOCS/ANNEX_ROUTER.md`.*

---

## NOTA DE APERTURA (S009, 2026-07-12)

Hito nuevo, abierto a petición explícita de Miguel Ángel tras cerrar
H08 (Radio confirmada funcionando en dispositivo real, búsqueda de
listas/canales construida y pendiente solo de confirmación final).
Objetivo: una vista para escuchar emisoras de radio online de todo el
mundo, navegables por género/tema/país/década.

---

## Decisión técnica: Radio-Browser.info, no el Shoutcast real (S009)

Miguel Ángel usó el nombre "Shoutcast" de forma coloquial (como se
usaba tradicionalmente en reproductores como VLC/Winamp para
"directorio de radios online"), no como encargo literal de integrar
la marca Shoutcast.

**El Shoutcast real** (hoy propiedad de iHeartMedia) requiere una
clave de desarrollador y tiene más restricciones — no encaja con el
principio ya establecido en todo el proyecto (búsqueda de YouTube,
MusicBrainz: todo gratis, sin cuota, sin registro).

**Alternativa elegida: [Radio-Browser.info](https://www.radio-browser.info/)**
— directorio comunitario, libre y de código abierto, **sin API key**,
con más de 25.000 emisoras activas de todo el mundo (dato real de las
estadísticas del propio servicio, verificado S009). Encaja
perfectamente con la filosofía del proyecto.

**Nota de brecha documental (S011, 2026-07-15):** entre el cierre
documentado de S009 y esta nota, S010 implementó por completo la
hoja de ruta de 5 pasos de abajo (más ampliaciones no previstas) en
35 commits reales, pero la sesión no ejecutó PCS -- probablemente un
corte de conexión -- y este anexo, `RESUMPTION_POINT.md` y la tabla
de `MASTER_DOCUMENT.md` se quedaron describiendo el hito como si no
tuviera código todavía. Reconciliado ahora leyendo el `git log` y el
código real, no de memoria. Ver "COMPLETADAS EN S010" más abajo.

**Verificado contra la documentación oficial (S009), no asumido:**
- Base URL: hay varios servidores espejo (`de1.api.radio-browser.info`,
  `de2...`, `fi1...`, etc.), descubribles vía DNS de
  `all.api.radio-browser.info`. **Simplificación deliberada para el
  primer PASO:** usar un único servidor fijo (`de1.api.radio-browser.info`)
  en vez de implementar descubrimiento dinámico de servidores — si ese
  mirror concreto da problemas de disponibilidad más adelante, se
  revisita entonces, no antes (mismo criterio de "no resolver un
  problema que todavía no existe" que el resto del proyecto).
- Endpoints relevantes (todos gratuitos, sin clave):
  - `/json/stations/bytag/{término}` — búsqueda de género/tema por
    coincidencia parcial (`bytagexact` para coincidencia exacta).
  - `/json/stations/bycountry/{término}` — por país.
  - `/json/stations/search?name=...&tag=...&country=...` — búsqueda
    combinada con varios filtros a la vez.
  - `/json/tags` — lista de todas las etiquetas disponibles con su
    número de emisoras (`stationcount`) — de aquí sale el listado real
    de géneros/temas a mostrar como filtro, no una lista inventada a
    mano.
  - `/json/countries` — lista de países con número de emisoras.
- Campos relevantes de cada emisora (`Station`, confirmados contra la
  documentación oficial): `stationuuid` (identificador estable, NO
  usar el campo `id` antiguo — la propia documentación lo desaconseja
  explícitamente), `name`, `url_resolved` (URL de streaming ya
  resuelta — playlists M3U/PLS y redirecciones HTTP ya procesadas por
  el propio servicio, lista para pasarle directamente a ExoPlayer),
  `favicon`, `tags` (string separado por comas), `country`,
  `countrycode`, `language`, `codec`, `bitrate`, `votes`,
  `lastcheckok` (1/0 — si la emisora respondió correctamente en el
  último chequeo automático del servicio; **filtrar por
  `lastcheckok=1`** para no ofrecer emisoras muertas).
- Buena práctica documentada (no obligatoria pero recomendada):
  enviar un User-Agent identificable (p.ej. `MiMoo/1.0`) y, opcional
  para más adelante, llamar al endpoint de "click counter" cuando se
  reproduce una emisora, para contribuir a las estadísticas de
  popularidad de la comunidad.

**Década:** no existe un campo nativo de año/década en el modelo de
datos. Se resuelve buscando por etiqueta de texto libre (`bytag`) con
términos como "80s", "90s", "oldies" — funciona en la práctica porque
muchas emisoras están etiquetadas así, pero no es una decena
garantizada ni estructurada. Contárselo a Miguel Ángel tal cual está,
sin prometer más de lo que hay.

---

## COMPLETADAS EN S009

- Investigación de ambas opciones técnicas (Shoutcast real vs
  Radio-Browser.info) y decisión documentada arriba.
- Miguel Ángel preguntó explícitamente por el coste del Shoutcast
  real (pensando que su cuenta de desarrollador de Google Play
  pagada podría aplicar) — aclarado que son sistemas sin relación:
  el registro de Google Play Console no da ningún acceso al API de
  Shoutcast. Investigado también el coste real del Shoutcast
  (Developer ID del directorio real, `shoutcast.com/Developer`):
  gratuito de registrar, pero sujeto a aprobación a su criterio y a
  condiciones de marca/uso — distinto de "Shoutcast Net"
  (shoutcastnet.com, ~4$/mes), que es un servicio de *hosting* para
  emitir una emisora propia, no para consultar el directorio, y por
  tanto no aplica a este caso de uso.
- **Confirmación final de Miguel Ángel: Radio-Browser.info.** Ningún
  código escrito todavía en este hito — la sesión se cerró justo
  tras la confirmación, con la hoja de ruta de abajo lista para
  ejecutar en la siguiente sesión sin necesitar más contexto.

---

## COMPLETADAS EN S010 (reconciliado en S011, ver nota de brecha arriba)

- **PASO 1 (capa de red):** `RadioBrowserApiService`, instancia de
  Retrofit en `NetworkModule.kt` contra `de1.api.radio-browser.info`
  con interceptor de User-Agent, DTOs `RadioStation`/`RadioTag`/
  `RadioCountry` (`RadioBrowserDtos.kt`).
- **PASO 2 (repositorio):** `RadioBrowserRepository`, defensivo (nunca
  lanza, lista vacía en fallo de red).
- **PASO 3+4 (UI + navegación):** pantalla "Radios Online"
  (`RadioBrowserScreen.kt`/`RadioBrowserViewModel.kt`), entrada en el
  drawer y ruta en `NavGraph.kt`. Confirmado en código: **ningún**
  botón de descarga en ninguna fila.
- **Desviación real del plan original, a petición explícita de
  Miguel Ángel (S010):** en vez de listar `/json/tags` en bruto
  (miles de etiquetas libres de la comunidad, inservibles como filtro
  directo), se construyó un catálogo curado a mano
  (`RadioGenreCatalog.kt`) -- 12 categorías de género (Electrónica,
  Rock, Metal, Pop, Hip-Hop/Rap, Jazz, Clásica, Reggae/Ska, Latina,
  Folk/Country, Blues, Noticias/Charla), cada una con sus términos de
  búsqueda reales verificados, más una lista fija de décadas (50s a
  2010s + Oldies). No pretende ser exhaustivo, ampliable si en uso
  real aparecen géneros importantes sin cubrir.
- **Ampliación no prevista en el plan original de 5 pasos:**
  favoritos de emisora (`FavoriteRadioStation`,
  `FavoriteRadioStationDao`, `FavoriteRadioStationRepository`,
  `MIGRATION_9_10`).
- **Fixes reales tras uso en dispositivo** (solo salen de probarlo,
  no de leer código): crash real reproduciendo emisoras HLS
  (`ClassNotFoundException`), crash real "Key already used" en los
  chips de país, fila de país que se quedaba invisible sin aviso si
  fallaba `getCountries()`, blindaje contra emisoras que la propia
  API devuelve pero que no están emitiendo realmente.
- **PASO 3, decisión pendiente sin resolver:** qué mostrar en la
  barra de progreso para una radio en directo sin duración real. En
  la práctica quedó resuelto por omisión -- `PlayerBar` oculta la
  barra entera cuando `durationMs <= 0` -- pero nunca se implementó
  el indicador "En directo" que este anexo dejaba pendiente de
  confirmar. Funciona, pero sigue siendo una decisión de diseño sin
  tomar explícitamente.
- **PASO 5 (verificación en dispositivo real):** los fixes de crashes
  reales de arriba solo pueden salir de haberlo probado en
  dispositivo, pero no hay ningún commit ni confirmación explícita de
  Miguel Ángel tipo "esto ya funciona entero" (a diferencia de H08,
  que sí tiene ese commit de cierre -- `11237a4`). Pendiente.

---

## Alcance

- Vista nueva para explorar emisoras por género/tema (`/json/tags`),
  por país (`/json/countries`), y por búsqueda de texto libre
  (`/json/stations/search`).
- Tocar una emisora la reproduce **directamente en streaming** vía
  `PlayerManager.play()`, reutilizando toda la infraestructura de
  reproducción ya existente (ExoPlayer, MediaSession, notificación,
  barra de progreso -- aunque una radio en directo no tiene duración
  real, ver PASO 3).
- **Solo streaming, nunca descarga** -- decisión explícita de Miguel
  Ángel, mismo principio que Radio (H08 PARTE 2): "por supuesto, sin
  opción a esa descarga". Ningún botón de descarga en esta pantalla,
  en ningún caso.
- No se integra con la Radio de H08 (música relacionada por artista)
  ni con la búsqueda de YouTube -- es una fuente de contenido
  completamente aparte (emisoras en directo, no pistas sueltas).

---

## Hoja de ruta (histórica -- los 5 pasos ya se ejecutaron en S010, ver COMPLETADAS arriba)

**PASO 1 — Capa de red.** Nuevo `RadioBrowserApiService` (interfaz
Retrofit), nueva instancia de Retrofit en `NetworkModule.kt` con base
URL `https://de1.api.radio-browser.info/` (servidor fijo, ver
decisión técnica arriba) y un interceptor de User-Agent (mismo patrón
que ya existe para MusicBrainz en `NetworkModule.kt` -- releer ese
archivo real antes de replicar el patrón, §4.1). DTOs:
`RadioStation`, `RadioTag`, `RadioCountry` -- nombres de campo exactos
confirmados arriba, usar `@SerializedName` donde el campo JSON no siga
camelCase de Kotlin (la mayoría son snake_case: `url_resolved`,
`stationuuid`, `lastcheckok`, `countrycode`).

**PASO 2 — Repositorio.** `RadioBrowserRepository`: `searchStations()`,
`getTags()`, `getCountries()`. Mismo patrón defensivo que
`CoverArtRepository`/`RadioRepository` (H08) -- nunca lanza, devuelve
lista vacía en fallo de red, no rompe la pantalla.

**PASO 3 — UI.** Pantalla nueva (nombre a decidir con Miguel Ángel --
"Radios Online" es más claro para el usuario final que "SHOUTcast",
que es jerga técnica; confirmar antes de programarlo). Chips o lista
de géneros (de `/json/tags`, ordenados por `stationcount` descendente,
no alfabético -- los más relevantes primero), selector de país
opcional, campo de búsqueda por nombre. Lista de resultados con
favicon, nombre, país, etiquetas. Tocar una fila llama a
`PlayerManager.play(station.urlResolved, station.name, isLocal =
false)`. **Pendiente de decidir con Miguel Ángel:** qué mostrar en la
barra de progreso (H08's `PlayerBar` ya tiene barra de tiempo/duración)
cuando suena una radio en directo, que no tiene duración real --
probablemente ocultar la barra de progreso y mostrar solo "En
directo" en su lugar, a confirmar.

**PASO 4 — Navegación.** Nueva entrada en el drawer de navegación
(`MainActivity.kt`) y ruta nueva en `NavGraph.kt` -- releer ambos
archivos reales antes de tocar nada (§4.1/§4.2), seguir el mismo
patrón que las entradas ya existentes (Biblioteca, Playlists,
Búsqueda, Importar enlace).

**PASO 5 — Verificación en dispositivo real.** Confirmar que las
emisoras suenan, que el favicon carga, que el filtro por
género/país/búsqueda funciona, y que ninguna opción de descarga
aparece en ningún punto de esta pantalla.

---

## Hoja de ruta para la siguiente sesión (real, S011 en adelante)

1. Confirmación explícita de Miguel Ángel de que "Radios Online"
   funciona entero en dispositivo real tras los fixes de S010 (o
   reportar lo que falle).
2. Decidir con Miguel Ángel si se implementa el indicador "En
   directo" en `PlayerBar` para radios, o si se deja como está
   (barra de progreso oculta, sin texto sustitutivo).
3. Ampliar `RadioGenreCatalog.kt` si en uso real aparecen géneros
   importantes sin cubrir por las 12 categorías actuales.

---

# MIMOO — PUNTO DE REANUDACIÓN (NewFlow Android)

*Vive en `DOCS/RESUMPTION_POINT.md`. Sustituye a la bitácora de
sesión que antes era una skill (`doc-session-log-mimoo`) — con
NewFlow, cada paso ya queda registrado como un commit individual con
mensaje detallado en el propio `git log`, así que este archivo es
solo un resumen corto de "dónde estamos" y "qué sigue", no un diario
completo. Para el detalle exacto de qué cambió en cada paso, consultar
`git log` directamente sobre el repo clonado. Este archivo nunca dice
qué hito está EN PROGRESO -- ver `DOCS/ANNEX_ROUTER.md` para eso.*

---

## Última actualización: 2026-07-19 (cierre de sesión S017 NewFlow)

**Hito activo: H12** (Directorio de Música + Favoritos sin descarga)
-- sin PCH en esta sesión, sigue EN PROGRESO tras el cierre, ver
`DOCS/ANNEX_ROUTER.md`.

**S017, resumen: sesión de diseño puro, sin código (un commit,
`989d932`).** Cerrados con Miguel Ángel los siete puntos pendientes de
`DOCS/ANNEX_H12.md`: tres páginas separadas (Artista/Álbum/Canción)
con rutas nuevas en `NavGraph.kt` y cruces en ambos sentidos; búsqueda
unificada en una sola pantalla que sustituye a `SearchScreen` (H01) y
`AlbumSearchScreen` (H05), cubriendo también listas de reproducción y
canales (sin pantallas de búsqueda separadas, corregido tras
malentendido inicial en la conversación); `FavoriteArtist(artist)` como
tabla nueva, mismo patrón que `FavoriteAlbum`; homónimos resueltos con
dos mecanismos -- `normalizeArtistName()` (quita `"The "` inicial) para
variantes del mismo artista, y tabla `ArtistDisambiguation` para
artistas distintos con el mismo nombre; botones separados
Reproducir/Descargar por pista y por álbum; menú de tres puntos del
reproductor con fallback a `parseArtistFromTitle()`; y criterio de
conteo de la sección "Descargado" por álbum y por artista. El anexo
quedó reescrito con la hoja de ruta de construcción completa, ejecutable
sin contexto adicional. **Sin código en esta sesión** -- construcción
pendiente para la sesión siguiente.

## Sesión anterior (S016, contexto histórico)

**Hito activo al cierre de S016: H12** (Directorio de Música +
Favoritos sin descarga, hito nuevo) -- H08 se pausó al cierre de esa
sesión (PCH explícito de Miguel Ángel), ver `DOCS/ANNEX_ROUTER.md`.

**S016, resumen narrativo completo (seis commits reales, `f08e8b4`..`a6003b6`):**

1. **`36c972e` -- cupo 80/10/10 de Radio configurable en Ajustes.**
   `UiPreferencesManager` gana `radioExplorePercent`/`radioDiscoPercent`
   (SharedPreferences + `StateFlow`, patrón del borde de cristal),
   `radioDictPercent` siempre derivado (100 - los otros dos).
   `PlayerManager.dueForExploreQuota()`/`dueForDiscoQuota()` dejan la
   fórmula fija `used*10 < accepted+1` (solo válida para p=10) por
   `dueForQuota(used, percent) = percent>0 && used*100 <
   (accepted+1)*percent`, sin división, válida para cualquier reparto.
   Nueva sección "Radio" en Ajustes con dos sliders + texto derivado
   de diccionario.

2. **`86fb3d5` -- fix real: la década dejaba de respetarse tras agotar
   el pool de la sesión.** Diagnosticado leyendo el log real
   (`radio_relacionados_debug.txt`, sesión de ~4h, ancla 1980):
   `pickDictCandidate()` tenía un segundo intento que, al agotarse el
   pool español de esa década (~15 artistas), caía silenciosamente a
   CUALQUIER década. Eliminado -- la cascada ya existente (extranjero
   conocido -> clásica, en ese momento) toma el relevo respetando
   siempre la década.

3. **`384ed8a` -- orden explícita y repetida de Miguel Ángel: NUNCA
   MÁS cae a "classical".** `resolveFinalFallback()` (cupo agotado en
   una vuelta) y `resolveAnchorWithFallbacks()` (no se identifica nada
   del artista semilla) sustituyen su último recurso por disco
   (biblioteca local) -- si tampoco eso tiene nada, la Radio no añade
   nada esa vuelta / no arranca, nunca rellena con música sin
   relación. `FALLBACK_GENRE` eliminado del código. Primera pasada de
   ampliación del diccionario español (+23 entradas) y corrección de
   Quevedo (canario, estaba mal clasificado como extranjero).

4. **`b38304e` -- segunda pasada de ampliación del diccionario**
   (+23 entradas más), comunicando explícitamente a Miguel Ángel que
   no se llegaba a las 100/década pedidas.

5. **`12b399e` -- corrección de Miguel Ángel sobre el bloque anterior
   (malentendido real, aclarado en la conversación): la intención
   siempre fue aguantar el máximo tiempo posible con género+década de
   la semilla, degradando solo cuando se agota uno de los dos -- por
   eso el diccionario debe ser lo más extenso posible.** Encontrado y
   corregido un bug real no reportado hasta entonces: `pickDiscoCandidate()`
   (10% disco) tenía un último peldaño que ignoraba género Y década,
   solo miraba origen -- podía meter un tema sin relación alguna en
   medio de una sesión coherente. Ahora sigue la misma cascada
   simétrica que el diccionario. Nuevo `RadioSessionHistoryManager`
   (SharedPreferences, hasta 400 artistas) -- historial de Radio
   persistente ENTRE sesiones (no solo dentro de una), usado como
   preferencia suave en las tres cascadas, a petición explícita de
   Miguel Ángel ("que las listas no sean siempre igual"). Tercera
   pasada de ampliación del diccionario (+16 entradas), con género
   incluido en cada entrada por primera vez.

6. **`a6003b6` -- PCH de cierre.** H08 pasa a PAUSADO (todo lo de
   arriba construido y compilando en verde en los seis commits,
   **nada verificado en dispositivo real todavía**). H12 abierto EN
   PROGRESO: hito nuevo, conversación de apertura completa capturada
   en `DOCS/ANNEX_H12.md` -- directorio de música navegable vía
   MusicBrainz (páginas de artista/álbum/canción cruzadas entre sí),
   unificación de las búsquedas de H01/H05, streaming y descarga al
   vuelo desde cualquier página, favoritos de artista/álbum
   desacoplados de la descarga. Miguel Ángel decidió explícitamente
   que la sesión que lo retome sea de **diseño puro, sin tocar
   código** -- mismo patrón que S013 para H08.

**Incidencia real de esta sesión, ya resuelta:** el token de GitHub
usado al empezar la sesión dejó de funcionar a mitad (HTTP 401 en el
segundo bloque de trabajo) -- Miguel Ángel proporcionó un token nuevo
y se retomó sin perder ningún commit local ya hecho.

**Pendiente explícito, dicho textualmente por Miguel Ángel -- "es lo
más importante":** el diccionario de éxitos conocidos sigue sin llegar
a las ~100 entradas por década que pidió (estado real al cierre:
1960:22, 1970:22, 1980:28, 1990:23, 2000:27, 2010:24, 2020:13). Tres
pasadas de ampliación en esta sesión, todas con entradas verificables
de memoria real, sin inventar nada -- seguir ampliando es candidato
fuerte para dedicar una sesión aparte si H08 se retoma antes de llegar
a un tamaño satisfactorio.

**Siguiente sesión (H12, construcción -- diseño ya cerrado en S017, ver
`DOCS/ANNEX_H12.md` sección "Hoja de Ruta de Construcción para la
Siguiente Sesión" para los ocho bloques exactos, ya ejecutable sin
contexto adicional):** entidades `FavoriteArtist`/`ArtistDisambiguation`
+ migración Room, `normalizeArtistName()`, tres pantallas nuevas
(`ArtistScreen`/`AlbumScreen`/`SongScreen`) + rutas en `NavGraph.kt`,
flujo de desambiguación de homónimos, búsqueda unificada (sustituye a
H01/H05, incluye listas y canales), botones Reproducir/Descargar por
pista y por álbum, menú de tres puntos del reproductor, y verificación
+ commit por bloque cerrado.

**Pendiente sin resolver, distinto de H12 -- para cuando se retome H08
con su propio PCH:** verificación en dispositivo real de TODO lo
construido en S016 (ver bitácora arriba), y continuar ampliando el
diccionario hacia las ~100/década pedidas.

---


## Sesión anterior (S011, contexto histórico)

**S010 se cerró sin ejecutar PCS** (probablemente un corte de
conexión) -- 35 commits reales de H09 y de H08 quedaron sin
reflejarse en la documentación. Reconciliado al arrancar S011 -- ver
`DOCS/ANNEX_H09.md` sección "COMPLETADAS EN S010".

## S011 en resumen (sesión larga, varios frentes)

1. **Reconciliación de la brecha documental de S010** (H09).
2. **Cambio de metodología, instrucción explícita y repetida de
   Miguel Ángel:** los hitos solo tienen dos estados posibles, EN
   PROGRESO y PAUSADO -- nunca "completado" -- y esa información vive
   ahora exclusivamente en `DOCS/ANNEX_ROUTER.md` (archivo nuevo).
   Ningún otro archivo (`MASTER_DOCUMENT.md`, ningún `ANNEX_H0X.md`,
   este archivo) vuelve a mencionar su propio estado.
3. **Fix real en H07:** `confirmCloudWins()`/`confirmLocalWins()` en
   `AutoSyncViewModel.kt` lanzaban la llamada de red a Drive sin
   `try/catch`, a diferencia de `startAutoSync()`. Un
   `SocketTimeoutException` real dejaba la excepción sin capturar --
   crash en bucle al reabrir la app, preguntando el mismo conflicto de
   sincronización una y otra vez. Corregido con `try/catch` + bloqueo
   síncrono de doble pulsación (commit `4784c9d`). **Verificación en
   dispositivo todavía pendiente** -- Miguel Ángel no ha confirmado
   este fix concreto, solo otros de la sesión.
4. **H10 abierto y construido (8 de 10 niveles).** Código de
   compartición "miMoo+hash", autocontenido (sin depender de Drive),
   registrado como destino de `ACTION_SEND` de texto plano --
   importación siempre ADITIVA, nunca borra nada del receptor. Niveles
   con punto de entrada real en la UI: Biblioteca completa, Artista,
   Álbum, Tema de álbum, Sencillo, Sencillos favoritos, Listas de
   reproducción. Canales/Canal (niveles 9-10) sin construir -- no
   existe ese concepto en el modelo de datos, pendiente de diseño con
   Miguel Ángel. Ver `DOCS/ANNEX_H10.md` para el detalle completo.
   **Sin verificar en dispositivo real el flujo end-to-end** (generar
   código → enviarlo → abrirlo en otro dispositivo → confirmar
   import).
5. **Fix real de carátula en el reproductor:** `PlayerBarViewModel`
   solo leía `coverArtUrl` de Room de forma pasiva -- nunca disparaba
   la resolución real contra MusicBrainz/Cover Art Archive, que solo
   vivía en `LibraryViewModel`. Si se reproducía un tema de un álbum
   con carátula real pero nunca visitado en Biblioteca, se quedaba
   sin carátula para siempre. Corregido: mismo mecanismo de
   resolución proactiva que ya usaba Biblioteca.
6. **Fix real de notificación sin carátula ni artista:**
   `PlayerManager.toMediaItem()` nunca poblaba `MediaMetadata` más
   allá del título -- la notificación (fabricada directamente de esos
   metadatos por `DefaultMediaNotificationProvider`) nunca tuvo nada
   que mostrar, para ninguna pista, desde que existe el servicio.
   `QueueItem` gana `artworkUri`; todos los puntos de reproducción
   actualizados (carátula real con respaldo en miniatura de YouTube;
   favicon de emisora en Radios Online).
7. **Botón de favoritos en la notificación**, API oficial actual de
   Media3 (`CommandButton` + `SessionCommand`, verificado contra
   developer.android.com), reutilizando la misma operación de
   favorito que el reproductor/Biblioteca.
8. **"2 binarios" de descarga -- resuelto solo, no era un problema de
   binario.** Investigado a fondo: el error real capturado en el log
   de Miguel Ángel era YouTube bloqueando con "Sign in to confirm
   you're not a bot" (PO Tokens/BotGuard), no un fallo de códec. Es un
   bloqueo temporal e intermitente documentado en la propia comunidad
   de yt-dlp -- **Miguel Ángel confirmó que las descargas que fallaban
   ya funcionan solas**, sin tocar nada. Solución de cookies vía
   WebView (la única respaldada oficialmente para un bloqueo
   persistente) queda **aparcada, no construida** -- retomar solo si
   el bloqueo vuelve a darse de forma persistente, no puntual.
9. **Favoritos↔Drive (H07), confirmado sin tocar código:** el
   `BackupBundle` que ya usa H06/H07 desde antes de esta sesión ya
   incluye `isFavorite` por pista y `favoriteAlbums` -- confirmado al
   reutilizarlo tal cual para H10. No hacía falta ningún cambio, ya
   estaba cubierto.
10. **H11 abierto y construido entero (4 de 5 pasos):** al aclarar
    qué significaba "Canal" en H10, Miguel Ángel describió una
    funcionalidad nueva de verdad -- suscripciones a canal + descarga
    automática, "como un guardado de podcast". Abierto como hito
    nuevo (no forzado dentro de H10). Construido: entidad/persistencia,
    suscribirse desde la búsqueda de canales ya existente (H08 PARTE
    1, sin duplicar búsqueda), pantalla "Canales", y comprobación
    periódica en segundo plano (`ChannelCheckWorker`, WorkManager,
    una vez al día) que reutiliza `ExternalLinkResolver.resolveLink()`
    tal cual contra la URL de vídeos del canal -- sin Python nuevo.
    Decisión de diseño clave: la primera comprobación de un canal
    recién suscrito NO descarga nada (línea base), solo a partir de
    la segunda se encola contenido genuinamente nuevo -- evita
    descargar de golpe el catálogo histórico entero al suscribirse.
    **Sin verificar en dispositivo real.** Un fallo real de Dagger
    (`ChannelSubscriptionDao` sin proveedor en `DatabaseModule`) se
    coló en el primer commit de H11 y rompió el build -- corregido en
    el siguiente commit, mismo patrón que un fallo idéntico ya
    documentado en ese archivo (2026-07-05, `FavoriteAlbumDao`).

11. **H10 rediseñado de texto a archivo real, tras prueba de Miguel
    Ángel en dispositivo:** el código "miMoo+hash" compartido como
    texto plano (`ACTION_SEND`) no daba nada que "tocar para abrir"
    en WhatsApp/SMS -- solo texto pegado sin ninguna acción asociada.
    Además el hash resultaba excesivamente largo, y el prefijo
    "miMoo+" fue una lectura mía equivocada de la instrucción
    original (el "+" nunca se refería al carácter literal). Rediseño
    completo: MiMoo genera y comparte ahora un ARCHIVO real con
    extensión propia `.mimoo` (GZIP crudo de JSON, sin Base64/prefijo
    -- ya no hace falta sobrevivir como texto), registrado en
    `AndroidManifest.xml` vía `ACTION_VIEW` + `pathPattern` (mismo
    patrón que usan apps reales para asociarse a una extensión
    propia). Se comparte con `EXTRA_STREAM` (no `EXTRA_TEXT`), vía
    `FileProvider` (mismo mecanismo que ya usa
    `AppUpdateRepository.downloadApk()` para el APK de actualización).
    **Cacería de un build roto sin poder leer el log real** (Azure
    Blob redirigió de forma consistente a subdominios no permitidos
    en la red durante varios intentos, hasta dar con `sa17` al quinto
    commit de esta cacería): la causa real, tras diagnóstico manual
    exhaustivo comparando cada diff línea a línea, fue un comentario
    KDoc en `MainActivity.kt` que citaba literalmente `mimeType="*/*"`
    entre comillas -- la secuencia `*/` cierra cualquier bloque
    `/** */` de Kotlin sin importar que esté entre comillas, lo que
    convertía el resto del comentario en código real y generaba
    decenas de errores en cascada. Un `pathPattern` sobre-escapado (4
    barras invertidas en vez de 1) también se corrigió por el camino,
    aunque no era la causa del fallo.

## S011, continuación larga (misma sesión, tras compactación de contexto)

12. **H10, cuarto y quinto rediseño (segundo y tercer fallo real en
    dispositivo tras el rediseño a archivo del punto 11):**
    - MiMoo no aparecía en absoluto en "Abrir con" de WhatsApp, ni
      tampoco en el explorador de archivos de Android. Investigado a
      fondo: desde Android 7, las URIs `content://` son opacas y
      `MimeTypeMap` no reconoce extensiones inventadas (`.mimoo`) --
      Android nunca sabe qué tipo asignarle al guardarlo. Intento con
      tipo MIME propio (`application/x-mimoo-share`) tampoco resolvió
      el problema de fondo.
    - **Solución real, propuesta por Miguel Ángel:** sustituir la
      extensión inventada por `.txt` (tipo ya reconocido de fábrica),
      con una marca interna al principio del archivo
      (`SHARE_FILE_MARKER`) para que MiMoo distinga sus propios
      archivos de cualquier otro `.txt`. Registrado por tipo MIME
      (`text/plain` y `application/txt`, esta última una variante no
      estándar que algunas apps usan). **Confirmado funcionando en
      dispositivo real** -- MiMoo aparece en "Abrir con" y decodifica
      bien.
    - Vía manual de emergencia añadida en paralelo (Ajustes →
      Compartir → "Importar código recibido, elegir archivo"),
      independiente de que la apertura automática funcione.
    - Línea de solución alternativa explorada pero no construida:
      Android App Links verificados contra el dominio propio de
      Miguel Ángel (`campusstudioonline.com`, hosting en
      PythonAnywhere) -- requeriría servidor propio, documentado en
      `DOCS/ANNEX_H10.md`.
13. **Fallo real de build encontrado sin poder leer el log inicialmente
    dos veces más** (mismo patrón que el punto 11): un `pathPattern`
    mal escapado (corregido, no era la causa final) y, la causa real,
    un comentario KDoc con `"*/*"` literal cerrando el bloque de
    comentario antes de tiempo -- mismo mecanismo que el fallo del
    punto 11, en un archivo distinto.
14. **PlayerBar colapsable** (fallo real: el reproductor expandido
    fijo, pedido en S010, tapaba pantallas con poco contenido propio
    como Ajustes, sin dejar forma de llegar a "Buscar
    actualizaciones"). Botón para contraer a una mini-barra de una
    sola fila; diseño expandido original intacto.
15. **Fallback de carátula vía iTunes** cuando MusicBrainz/Cover Art
    Archive no tiene coincidencia (petición explícita: "falta
    descargar carátula cuando no existe"). Dos fallos reales
    encontrados y corregidos sobre la marcha con el mismo caso real
    (Crystal Method / Vegas): el artista real es "The Crystal Method"
    (reintento con "The " añadido), y la app aceptaba el primer
    release de MusicBrainz sin comprobar si Cover Art Archive tenía
    imagen real (ahora prueba varios candidatos con petición HEAD
    real). Botón manual "Actualizar carátula" añadido para forzar un
    reintento cuando una URL rota ya quedó guardada de antes de estos
    fixes.
16. **Botón de descarga en la notificación y en el reproductor
    expandido** (petición explícita). En la notificación, límite real
    de Android documentado (5 huecos totales, uno ocupado por un
    icono del propio sistema MIUI) -- no siempre visible, no es un
    fallo de código. En el reproductor propio (Compose, sin ese
    límite), funciona siempre.
17. **Repo de GitHub hecho público** (petición de Miguel Ángel, se
    quedó al 90% de su cuota gratuita de Actions en el plan privado) +
    caché de Gradle/pip añadida al workflow para reducir minutos de
    build cuando vuelva a privado el 1 de agosto.
18. **Cristal esmerilado (glassmorphism) extendido a toda la
    aplicación**, petición explícita con una captura de un teclado
    como referencia. Iteración por feedback real: sin volumen/sombra,
    variante sin borde, interruptor persistido en Ajustes
    (`LocalGlassBorderEnabled`, reactivo en toda la app sin
    reiniciar), y finalmente distinción visual entre chapitas
    clicables y decorativas (`glassChip(interactive = ...)`) para que
    se note a simple vista cuál se puede tocar. Aplicado
    exhaustivamente: menú lateral, títulos de todas las pantallas,
    filas de artista/álbum/pista/playlist/canal/emisora/resultado de
    búsqueda, selector alfabético, pestañas de Biblioteca, Ajustes en
    acordeón (con cristal en cada título de sección -- petición
    explícita, para que quepa "Buscar actualizaciones" con el
    reproductor expandido), cola de reproducción, botones de
    reproducir todo/aleatorio.
19. **Radio (H08) reabierto puntualmente:** filtro por década (mismo
    patrón de ancla de sesión que país+género, S010) + diccionario de
    artistas conocidos por década compilado una sola vez (Wikipedia +
    Billboard, sin scraping en tiempo de ejecución) + cupo de
    exploración del 10%. Ver `DOCS/ANNEX_H08.md`, sección "Ampliación
    S011" para el detalle completo. **Sin verificar en dispositivo.**

## Siguiente sesión — orden sugerido

1. Ver `DOCS/ANNEX_ROUTER.md` para el hito EN PROGRESO real al
   arrancar (H09, sin tocar en toda esta sesión S011 -- confirmar que
   sigue siendo el que corresponde antes de asumirlo).
2. Verificación en dispositivo real de todo lo construido en la
   continuación de S011 (lista completa en la sección de arriba):
   H10 (.txt, confirmado ya en un caso real por Miguel Ángel, seguir
   probando el resto de niveles), PlayerBar colapsable, carátula vía
   iTunes + botón "Actualizar carátula", botones de descarga
   (notificación y reproductor), cristal completo, Radio con
   filtro de década + diccionario de éxitos (caso concreto a
   confirmar: Alaska y Dinarama ya no debería derivar a reguetón).
3. H11: sin probar en dispositivo desde su construcción, más las
   asunciones pendientes de confirmar (`DOCS/ANNEX_H11.md`, sección
   "Lo que queda por confirmar": audio vs vídeo, cuántos vídeos atrás
   al suscribirse, notificación de contenido nuevo, Shorts sí/no).
4. Si sigue H09: hoja de ruta real en `DOCS/ANNEX_H09.md` (sección
   final) -- confirmación de que funciona entero en dispositivo,
   decisión sobre el indicador "En directo", ampliar catálogo de
   géneros si hace falta.
5. Decisión de producto pendiente, sin construir: Android App Links
   verificados con el dominio propio de Miguel Ángel
   (`campusstudioonline.com` + PythonAnywhere) como alternativa a
   compartir por archivo -- ver `DOCS/ANNEX_H10.md`.
6. Recordatorio de calendario: 1 de agosto, Miguel Ángel vuelve a
   poner el repo de MiMoo en privado (ahora público para no gastar la
   cuota gratuita de Actions) -- la caché de Gradle/pip ya está lista
   para entonces.

## Pendientes antiguos, sin tocar en S011, no bloquean nada

- H03 PASO 8 y H04 PASO 6 (verificación funcional en dispositivo).
- H05 PASO 6c (Lou Reed, búsqueda por artista/título suelto, Importar
  enlace).
- H06 (Importar desde Drive) — implementado, verificación pendiente.
- H08: fallo real de idioma en la Radio (relacionados) -- ver
  `DOCS/ANNEX_H08.md` PASO 2.3, sección "Cuarta observación".
  **Superado por el diseño cerrado en S013** (ver sección de arriba y
  `DOCS/ANNEX_H08.md` sección "S013") -- ya no es "pospuesto sin
  tocar", es la hoja de ruta activa de H08.
- H08 PARTE 1 (búsqueda de listas/canales): pendiente de que Miguel
  Ángel la pruebe en dispositivo real y confirme si funciona bien.
- H07: sincronización de favoritos/ajustes entre dispositivos --
  divergencia real reportada por Miguel Ángel en S013 (ver sección de
  arriba). Sin investigar todavía, requiere su propio PCH.
- Decisión de producto pendiente: ¿menú de configuración para
  tema/color de la app? Sin decisión tomada.
- Carátulas realmente ausentes (álbum sin match en MusicBrainz) --
  no hay forma de proporcionar una a mano todavía; distinto del fix
  de S011 (que solo arregla carátulas que sí existen pero no se
  pedían). Sin log ni petición concreta de Miguel Ángel para esto
  todavía, no priorizado.

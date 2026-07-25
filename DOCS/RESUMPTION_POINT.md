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

## Última actualización: 2026-07-25 (cierre de sesión S021 NewFlow)

**Hito activo: H14** (Almacenamiento de la Biblioteca) -- hito nuevo,
abierto por PCH al cierre de esta sesión; H08 pasó a PAUSADO. Ver
`DOCS/ANNEX_ROUTER.md`.

**S021, resumen: sesión de dos mitades. Primero se cumplió entera la
hoja de ruta de H08 fijada al cierre de S020 (diccionario correcto y
muestra ampliada), corrigiendo por el camino un diagnóstico
equivocado; después, a petición de Miguel Ángel, se construyó la
carpeta de biblioteca configurable, que venía registrada aquí sin hito
desde S020. Cinco commits, `2a2e62d`..`3ff10ab`, todos compilando en
verde.**

**H08, cerrado y pausado.** El punto de partida era *"comenzamos la
siguiente sesión dejando el diccionario correcto. Y aumentando la
muestra si es posible."* Hallazgo de fondo nada más empezar: **el
diagnóstico de S020 sobre las décadas era incorrecto.** Las cinco
entradas que S020 señaló como mal fechadas (Måneskin, Blur, Ska-P,
Love of Lesbian, The Animals) estaban bien colocadas, y
`known_hit_artists.json` ni siquiera se tocó en S020 -- el log que
delató el fallo se produjo contra ese mismo archivo correcto. La
década no venía mal del dato: **la soltaba el código**, en cuatro
sitios que en S020 se habían quedado dentro al eliminar solo los
peldaños que soltaban el género. Las cuatro cerradas. Después,
auditoría real del diccionario (duplicados de tema, seis
reubicaciones, géneros y nombres), ampliación de 286 a 751 entradas
con las siete décadas en el entorno de las ~100, y verificación en
línea de cinco entradas que resultaron ser **todas inventadas**. Ver
`DOCS/ANNEX_H08.md`, "COMPLETADAS EN S021".

**H14, hito nuevo.** Recoge la petición que estaba anotada más abajo en
este mismo archivo desde S020 y que Miguel Ángel concretó en S021:
poder cambiar desde Ajustes la carpeta donde vive el audio, llevándola
por ejemplo a una tarjeta externa, con todas las canciones, álbumes,
listas y canales, y sin perder favoritos. Construido: sección
"Almacenamiento" en Ajustes, `LibraryMigrator` nuevo, y las dos ramas
que pidió (mover todo / solo cambiar el ajuste). Ver
`DOCS/ANNEX_H14.md`.

**Nada de las dos mitades está verificado en dispositivo.** Todo
compila; ni la Radio se ha escuchado ni la migración de carpeta se ha
probado sobre una tarjeta real.

**Riesgos conocidos a vigilar en la primera prueba:**
- **Radio:** que ninguna línea del `radio_relacionados_debug.txt` sirva
  una década distinta a la del ancla (si aparece, queda una quinta
  fuga sin localizar). Y `matchesArtist()`, que puede rechazar vídeos
  legítimos: si abundan los `0 de N resultados pasaron el filtro`, el
  criterio está demasiado apretado.
- **Carpeta:** que `takePersistableUriPermission()` no lance
  `SecurityException` sobre la tarjeta externa -- algunos fabricantes
  restringen ciertas rutas.

**Siguiente sesión:** hoja de ruta de `DOCS/ANNEX_H14.md`, encabezada
por la verificación en dispositivo con tarjeta externa. Si la escucha
de la Radio revela algo, H08 vuelve con un PCH trivial.

**S020, resumen: sesión de dos mitades. Primero se cerró y construyó
el "etc." de H13 (cristal esmerilado del reproductor); después, a
petición de Miguel Ángel, PCH hacia H08 y reescritura completa del
motor de la Radio sobre especificación dictada por él. Diez commits,
`d2fef5c`..`19c5c87`, todos compilando en verde.**

**H13, cerrado y pausado.** Alcance dado en una frase: *"el exoplayer
carece del efecto cristal esmerilado en los botones y en la parte de
texto junto a la carátula"*. Hallazgo de fondo durante la
implementación: el estado activo de aleatorio/cíclico se señalaba con
`colorScheme.primary` como tint, y en esta paleta `primary` ES BLANCO
-- el mismo blanco del estado inactivo. El "cambio de color" nunca
cambió un píxel. Ver `DOCS/ANNEX_H13.md`, "COMPLETADAS EN S020".
Pendiente: verificación visual en dispositivo, en concreto si la placa
encendida (0.88/0.72 en `GlassTokens`) tiene la intensidad correcta.

**H08, reescritura completa.** Punto de partida: dos logs reales y un
veredicto textual -- *"la radio está funcionando realmente mal,
mezclando décadas y géneros y orígenes"*. Diagnóstico completo sobre
código real en `DOCS/ANNEX_H08.md` sección "S020", y a partir de ahí
una especificación dictada por Miguel Ángel que se implementó entera:
género nunca abandonado en ningún cupo, origen separado España/
extranjero en los dos sentidos, tres porciones con dos peldaños cada
una, reparto dinámico del porcentaje de las porciones agotadas,
no-repetición por CANCIÓN en vez de por artista, ancla determinista
(género más votado, y buscada por el artista real en vez de por el
nombre del canal de YouTube) y validación de que el vídeo encolado es
del artista pedido.

**Nada de la Radio está verificado en dispositivo.** Todo compila; nada
se ha escuchado. Ver `DOCS/ANNEX_H08.md`, "Construido en S020", para
las líneas concretas del `radio_relacionados_debug.txt` que delatan si
cada pieza funciona.

**Riesgo conocido a vigilar en la primera escucha:** `matchesArtist()`
puede rechazar vídeos legítimos si el artista no aparece ni en el
título ni en el canal. Si en el log abundan los `0 de 6 resultados
pasaron el filtro`, el criterio está demasiado apretado y hay que
relajarlo.

**Siguiente sesión, fijada por Miguel Ángel al cierre:** *"comenzamos
la siguiente sesión dejando el diccionario correcto. Y aumentando la
muestra si es posible."* Trabajo de datos sobre
`known_hit_artists.json`, no de lógica. Hoja de ruta completa con
recuentos y criterios en `DOCS/ANNEX_H08.md`, "HOJA DE RUTA PARA LA
SIGUIENTE SESIÓN".

## Incidencias de proceso de S020, útiles para futuras sesiones

1. **Cuota de GitHub Actions con el repo en privado.** A mitad de
   sesión los builds empezaron a fallar; el síntoma limpio fue un
   commit de SOLO Markdown muriendo en 4 segundos con cero pasos
   ejecutados. No era código. Miguel Ángel puso el repo en público y
   el mismo commit, relanzado sin tocar nada, pasó a verde.
2. **Lectura de logs de Actions -- estado real.**
   `GET /actions/runs/{id}/jobs` SÍ funciona y devuelve cada paso con
   su `conclusion`: es la vía buena para localizar el paso rojo, y
   solo necesita *Actions: Read*. El texto del log sigue sin poder
   leerse desde el entorno del modelo: `/actions/runs/{id}/logs` y
   `/actions/jobs/{job_id}/logs` redirigen a subdominios rotatorios
   (`productionresultssa12`, `results-receiver...`) y la lista de
   dominios accesibles solo contempla `productionresultssa17`. Fijar
   un subdominio no sirve porque GitHub los rota.
3. **El permiso "Checks" NO existe para PAT de grano fino.** Se pidió
   por error en esta sesión. La documentación de GitHub lo cita como
   requisito del endpoint de check-runs, pero su propio personal
   confirma en el foro que solo las GitHub Apps pueden tenerlo. El
   token de sesión se queda con Contents RW + Actions RW + Metadata.
4. **Mensajes de commit multilínea: siempre desde fichero**
   (`git commit -F fichero`), nunca heredoc encadenado con `&& \` --
   el shell se come la primera línea del mensaje y el commit sale sin
   asunto. Pasó una vez y hubo que corregirlo con `--amend` +
   `--force-with-lease`.

## Sesión anterior (S018, contexto histórico)

Cierre de H12 (Directorio de Música), PCH hacia H13 (UX del
Reproductor) a petición explícita de Miguel Ángel. Ver
`DOCS/ANNEX_H13.md`, "NOTA DE APERTURA" para la petición textual
completa.

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

## Petición de producto ya recogida en un hito (S020 -> H14 en S021)

**Carpeta de descarga configurable.** Petición textual de Miguel
Ángel: *"dar la posibilidad de cambiar la carpeta de descarga con la
opción de copiar todo a esa carpeta o solamente los settings, sin
perder favoritos etc."*

**Resuelta en S021.** Dejó de estar sin hito: el PCH del cierre de
S021 abrió **H14 (Almacenamiento de la Biblioteca)** y el trabajo
quedó construido allí -- ver `DOCS/ANNEX_H14.md`. Se conserva aquí el
enunciado original porque es la fuente de verdad de lo que pidió, y
porque la verificación en dispositivo sigue pendiente.

Alcance tal como lo describió:
- Poder elegir una carpeta de descarga distinta a la actual.
- Al cambiarla, elegir entre **copiar todo** el audio ya descargado a
  la carpeta nueva, o **copiar solo los ajustes** (es decir, que a
  partir de ahora se descargue ahí, dejando el audio anterior donde
  está).
- En ninguno de los dos casos se pierden favoritos ni el resto de
  metadatos: la base de datos Room no se toca, solo cambian las rutas
  de archivo.

Las tres cosas quedaron construidas en S021 tal como las describió. La
intuición de "encaja como hito propio (H14)" resultó ser la acertada:
ni H06 (respaldo de metadatos en Drive) ni H07 (sincronización entre
dispositivos) gobiernan dónde vive el audio en el dispositivo local.

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

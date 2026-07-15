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

## Última actualización: 2026-07-15 (cierre de sesión S011 NewFlow)

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

## Siguiente sesión — orden sugerido

1. Ver `DOCS/ANNEX_ROUTER.md` para el hito EN PROGRESO real al
   arrancar (no asumir que sigue siendo H09 sin comprobar).
2. Verificación en dispositivo de todo lo construido en S011: flujo
   completo de H10 (generar/enviar/recibir/importar un código), fix
   de sync de H07, carátula en reproductor/notificación, botón de
   favoritos en notificación.
3. Si sigue H09: hoja de ruta real en `DOCS/ANNEX_H09.md` (sección
   final) -- confirmación de que funciona entero en dispositivo,
   decisión sobre el indicador "En directo", ampliar catálogo de
   géneros si hace falta.
4. H10: conversación de diseño sobre Canales/Canal (niveles 9-10)
   antes de tocar código ahí.

## Pendientes antiguos, sin tocar en S011, no bloquean nada

- H03 PASO 8 y H04 PASO 6 (verificación funcional en dispositivo).
- H05 PASO 6c (Lou Reed, búsqueda por artista/título suelto, Importar
  enlace).
- H06 (Importar desde Drive) — implementado, verificación pendiente.
- H08: fallo real de idioma en la Radio (relacionados) -- ver
  `DOCS/ANNEX_H08.md` PASO 2.3, sección "Cuarta observación".
  Explícitamente pospuesto, no tocar salvo que se retome H08.
- H08 PARTE 1 (búsqueda de listas/canales): pendiente de que Miguel
  Ángel la pruebe en dispositivo real y confirme si funciona bien.
- Decisión de producto pendiente: ¿menú de configuración para
  tema/color de la app? Sin decisión tomada.
- Carátulas realmente ausentes (álbum sin match en MusicBrainz) --
  no hay forma de proporcionar una a mano todavía; distinto del fix
  de S011 (que solo arregla carátulas que sí existen pero no se
  pedían). Sin log ni petición concreta de Miguel Ángel para esto
  todavía, no priorizado.

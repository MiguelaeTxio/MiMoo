# MIMOO — PUNTO DE REANUDACIÓN (NewFlow Android)

*Vive en `DOCS/RESUMPTION_POINT.md`. Sustituye a la bitácora de
sesión que antes era una skill (`doc-session-log-mimoo`) — con
NewFlow, cada paso ya queda registrado como un commit individual con
mensaje detallado en el propio `git log`, así que este archivo es
solo un resumen corto de "dónde estamos" y "qué sigue", no un diario
completo. Para el detalle exacto de qué cambió en cada paso, consultar
`git log` directamente sobre el repo clonado.*

---

## Última actualización: 2026-07-06 (cierre de sesión S004 NewFlow)

**Hito EN PROGRESO:** H05 — ver `DOCS/MASTER_DOCUMENT.md` para la
tabla completa de hitos y estado.

**S004 fue la sesión más larga y de mayor alcance del proyecto hasta
la fecha (2026-07-04 a 2026-07-06, 33 commits).** Empezó como
continuación del PASO 6c pendiente de H05, pero la propia
verificación real de Miguel Ángel en dispositivo destapó una cadena
larga de bugs estructurales de fondo que necesitaban resolverse
primero. Detalle completo, agrupado por tema, en
`DOCS/ANNEX_H05.md`, sección "COMPLETADAS EN S004" — no repetirlo
aquí, solo el resumen de qué falta confirmar.

**Incidencia histórica de "conflicto con un paquete" al actualizar —
CAUSA REAL ENCONTRADA en S004, no descartada esta vez.** Nunca había
un `signingConfig` explícito en `build.gradle.kts`: se había
verificado que el archivo del keystore tenía la huella SHA-256
correcta, pero nunca que Gradle lo usara de verdad para firmar. Con el
`signingConfig` explícito, Android Developer Console (registro de
desarrollador completado en la misma sesión, cuenta "Full
distribution", paquete `com.miguelaetxio.mimoo` verificado) pasó de
rechazar el APK ("firma diferente") a verificarlo correctamente.
**CONFIRMADO en S005 (2026-07-07):** actualización real sobre
instalación ya existente en el teléfono principal de Miguel Ángel —
ya no da "conflicto con un paquete". El `signingConfig` explícito
queda verificado en dispositivo, cierra este pendiente.

**Incidencia nueva encontrada y resuelta en S005 — Uri SAF "fantasma"
tras clonado de dispositivo.** En la tablet, "no reproduce nada" y
"la descarga va a veces" (incluyendo archivos pegados a mano en la
carpeta, que tampoco se reconciliaban). Causa: la tablet se configuró
copiando los datos del teléfono origen (clonado de dispositivo, tipo
"Mi Move to Xiaomi", en vez de una instalación limpia desde la APK).
Eso copió el `SharedPreferences` de `StorageManager` con el `Uri` SAF
del teléfono origen ya guardado — `hasRootUri()` daba `true` en la
tablet, así que la app nunca volvía a lanzar el selector de carpeta
propio, pero `takePersistableUriPermission` nunca se había tomado
para ese `Uri` en la tablet, dejándolo sin permiso real. Solución
aplicada: desinstalar y reinstalar limpio en la tablet (borra el
`SharedPreferences` heredado) — al arrancar, `hasRootUri()` vuelve a
dar `false`, la app lanza el selector real, se toma el permiso
persistente en este dispositivo y se dispara el escaneo inicial.
**Confirmado por Miguel Ángel: funciona correctamente tras
reinstalar.** Lección para el futuro: un clonado de dispositivo
Android puede dejar un `Uri` SAF con permiso inválido en el
dispositivo nuevo; no asumir que "ya tiene carpeta configurada" es lo
mismo que "tiene permiso real".

**Próxima sesión — verificar lo que queda de S004, por este orden de
prioridad:**
1. Notificación de reproducción: controles reales confirmados
   (play/pausa/anterior/barra), botón "siguiente" corregido al final
   de la sesión (cola migrada a la playlist real de ExoPlayer) sin
   confirmación explícita todavía.
2. Reintento de descargas fallidas (borrar definitiva/reintentar
   todas) — nace de una sesión real con 36 de 100 títulos fallados,
   sin probar la solución todavía.
3. Favoritos de álbum — funcionalidad nueva, sin verificar en
   dispositivo.
4. Compartir enlaces — arreglado para pistas sintéticas, pendiente de
   confirmar con una descarga nueva que el enlace de origen se
   comparte bien.

**Pendiente original de H05 (PASO 6c), sin confirmación de que se
probara pese a la actividad intensa de S004:**
1. Reimportar Lou Reed - Transformer (búsqueda por álbum) y confirmar
   emparejamiento vía playlist (11 pistas de golpe).
2. Probar búsqueda de álbum por artista o título sueltos
   ("Beethoven"/"Sinfonía"), confirmando también el orden real de
   pistas (prefijo `NN -` en el nombre de archivo, S004 — solo afecta
   a descargas nuevas a partir de ahora).
3. Probar "Importar enlace" con playlist normal de YouTube y con
   vídeo suelto.

**Decisión pendiente de Miguel Ángel (no técnica, de producto):**
¿hace falta un menú de configuración para elegir tema/color de la
app? Confirmado en S004 que nunca existió; sin decisión tomada.

**Ya resuelto en S004, no hace falta seguir preguntando:** la
migración de `SearchScreen` a búsqueda vía `yt-dlp` (sin cuota) que
quedaba pendiente de decisión en S003 — **implementada**, ya no es
una pregunta abierta.

**H03 PASO 8 y H04 PASO 6** (verificación funcional en dispositivo)
siguen pendientes — no tocados en S004, sin bloquear nada.

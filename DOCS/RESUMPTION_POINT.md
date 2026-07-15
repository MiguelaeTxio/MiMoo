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

## Última actualización: 2026-07-15 (sesión S011 NewFlow, en curso)

**S010 se cerró sin ejecutar PCS** (probablemente un corte de
conexión) -- 35 commits reales de H09 (capa de red, repositorio,
pantalla, navegación, catálogo curado de géneros/décadas, favoritos
de emisora, varios fixes de crashes reales) y de H08 (cierre real de
Radio confirmado en dispositivo) quedaron sin reflejarse en este
archivo, en `DOCS/ANNEX_H09.md` ni en `DOCS/MASTER_DOCUMENT.md`.
Reconciliado en S011 leyendo el `git log` real, no de memoria -- ver
`DOCS/ANNEX_H09.md` sección "COMPLETADAS EN S010" para el detalle
completo.

**S011 en resumen (hasta el momento):**
1. Reconciliación de la brecha documental de S010 (arriba).
2. Fix real en H07: `confirmCloudWins()`/`confirmLocalWins()` en
   `AutoSyncViewModel.kt` lanzaban la llamada de red a Drive sin
   `try/catch`, a diferencia de `startAutoSync()`. Un
   `SocketTimeoutException` real (confirmado en logs de Miguel Ángel)
   dejaba la excepción sin capturar -- crash en bucle al reabrir la
   app, preguntando el mismo conflicto de sincronización una y otra
   vez. Corregido con el mismo patrón `try/catch` más bloqueo síncrono
   de doble pulsación (commit `4784c9d`). Verificación en dispositivo
   pendiente.
3. **Cambio de metodología, instrucción explícita y repetida de
   Miguel Ángel:** los hitos solo tienen dos estados posibles, EN
   PROGRESO y PAUSADO -- nunca "completado" -- y esa información vive
   ahora exclusivamente en `DOCS/ANNEX_ROUTER.md`, archivo nuevo
   creado en esta sesión. Ni `MASTER_DOCUMENT.md` ni ningún
   `ANNEX_H0X.md` ni este archivo vuelven a mencionar su propio
   estado. `MASTER_DOCUMENT.md` y los anexos quedaron reescritos para
   quitar toda palabra de estado.
4. H10 abierto (`DOCS/ANNEX_H10.md`, nuevo): hash de compartición de
   contenido. Planteamiento inicial recibido de Miguel Ángel, pero
   **el diseño no está cerrado** -- ver ese anexo, sección "Lo que
   queda por cerrar de diseño", antes de escribir código.

**Siguiente sesión — orden sugerido:**
1. Ver `DOCS/ANNEX_ROUTER.md` para el hito EN PROGRESO real en el
   momento de arrancar (no asumir que sigue siendo H09 sin comprobar).
2. Si sigue H09: hoja de ruta real en `DOCS/ANNEX_H09.md` (sección
   final) -- confirmación de Miguel Ángel de que funciona entero en
   dispositivo, decisión sobre el indicador "En directo", ampliar el
   catálogo de géneros si hace falta.
3. Verificación en dispositivo del fix de sync de H07 (punto 2 de
   arriba).
4. H10: conversación de diseño con Miguel Ángel antes de tocar código
   (ver `DOCS/ANNEX_H10.md`).

**Pendientes antiguos, sin tocar en S010/S011, no bloquean nada:**
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
- Frentes de MiMoo pendientes de concretar (mensaje de Miguel Ángel,
  S011): carátulas que no se ven en el ExoPlayer aunque el disco sí
  las tenga; carátulas realmente ausentes que habría que
  proporcionar; posible necesidad de un segundo binario de
  descarga/codificación (algunas canciones fallan con el actual,
  pendiente de log real con el error exacto); notificación --
  añadir favoritos + carátula de fondo de la canción sonando;
  favoritos persistidos al subir a Drive (a confirmar leyendo el
  modelo de datos real si ya está cubierto por H07).

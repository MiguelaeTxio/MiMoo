# MIMOO — PUNTO DE REANUDACIÓN (NewFlow Android)

*Vive en `DOCS/RESUMPTION_POINT.md`. Sustituye a la bitácora de
sesión que antes era una skill (`doc-session-log-mimoo`) — con
NewFlow, cada paso ya queda registrado como un commit individual con
mensaje detallado en el propio `git log`, así que este archivo es
solo un resumen corto de "dónde estamos" y "qué sigue", no un diario
completo. Para el detalle exacto de qué cambió en cada paso, consultar
`git log` directamente sobre el repo clonado.*

---

## Última actualización: 2026-07-03 (cierre de sesión S003 NewFlow)

**Hito EN PROGRESO:** H05 — ver `DOCS/MASTER_DOCUMENT.md` para la
tabla completa de hitos y estado.

**Prioridad 1 de PASO 6c (cierre de app al reimportar) — CONFIRMADA
RESUELTA en S003.** Causa real: NPE en `ConcurrentHashMap` dentro de
`CoverArtRepository` (no las dos capas de S002, que eran fixes reales
pero insuficientes). Detalle completo en `DOCS/ANNEX_H05.md`, sección
"COMPLETADAS EN S003".

**Próxima sesión: resto de PASO 6c** — ver
`DOCS/ANNEX_H05.md`, sección "Hoja de Ruta para la Siguiente Sesión".
Resumen:
1. Reimportar Lou Reed - Transformer (búsqueda por álbum) y confirmar
   emparejamiento vía playlist (11 pistas de golpe, no una a una).
   Puede fallar por cuota agotada hasta el reset de medianoche hora
   del Pacífico.
2. Probar búsqueda de álbum por artista o título sueltos
   ("Beethoven"/"Sinfonía"), confirmando también que el orden de
   pistas en Biblioteca ahora es el real (fix `trackPosition` de
   S003).
3. Probar "Importar enlace" con playlist normal de YouTube y con
   vídeo suelto, confirmando que la carpeta en disco ahora es
   `{artista}/{álbum}/` real (fix de S003), no `Sencillos/`.

**Decisión pendiente de Miguel Ángel (no técnica, de producto):**
¿se aborda como hito propio la migración de `SearchScreen` a búsqueda
vía `yt-dlp` (`ytsearch:`, sin cuota de YouTube Data API), o queda
pospuesta? Contexto completo en `DOCS/ANNEX_H05.md`.

**Housekeeping pendiente (no bloquea nada):** el álbum Moon Safari de
Air, ya descargado antes del fix de carpetas de S003, sigue
físicamente en `Air/Sencillos/` — borrar desde Biblioteca y
redescargar cuando convenga para que se reubique en
`Air/Air - Moon Safari [Full Album]/`.

**Sin confirmación definitiva:** dos pistas de Moon Safari
("La femme d'argent", "Sexy Boy") parecían faltar tras la primera
importación completa, pero Miguel Ángel limpió la biblioteca antes de
poder diagnosticarlo del todo. Si vuelve a pasar con cualquier álbum,
la nueva sección "Con error" de Descargas (S003) debería mostrarlo de
inmediato.

**H03 PASO 8 y H04 PASO 6** (verificación funcional en dispositivo)
siguen pendientes — no tocados en S003, sin bloquear nada.

**Incidencia histórica sin confirmación de resolución:** actualización
de la APK fallaba con "conflicto con un paquete" (ver
`DOCS/ANNEX_H03.md`, sección "Incidencias Abiertas") — confirmado
sistemático (10-12 versiones seguidas), no un caso puntual; hipótesis
de keystore antigua residual descartada. Pista real de Miguel Ángel:
revisar changelogs de NewPipe. **Pospuesto explícitamente hasta
terminar H05** — no sacarlo salvo que Miguel Ángel lo mencione.

**Duplicados de prueba sin limpiar (sin confirmación de vigencia):**
4 archivos físicos en `Canal IMAR/_sin_album` (3) y
`Canal IMAR/Sencillos` (1) — Miguel Ángel prefiere borrarlos desde la
app.

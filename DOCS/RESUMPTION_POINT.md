# MIMOO — PUNTO DE REANUDACIÓN (NewFlow Android)

*Vive en `DOCS/RESUMPTION_POINT.md`. Sustituye a la bitácora de
sesión que antes era una skill (`doc-session-log-mimoo`) — con
NewFlow, cada paso ya queda registrado como un commit individual con
mensaje detallado en el propio `git log`, así que este archivo es
solo un resumen corto de "dónde estamos" y "qué sigue", no un diario
completo. Para el detalle exacto de qué cambió en cada paso, consultar
`git log` directamente sobre el repo clonado.*

---

## Última actualización: 2026-07-02 (cierre de sesión que migró a NewFlow)

**Hito EN PROGRESO:** H05 — ver `DOCS/MASTER_DOCUMENT.md` para la
tabla completa de hitos y estado.

**Próxima sesión: retomar H05 por el PASO 6a/6b/6c** — ver
`DOCS/ANNEX_H05.md`, sección "Hoja de Ruta para la Siguiente Sesión".
Resumen:
1. Permitir búsqueda de álbum con artista o álbum sueltos.
2. Corregir el bug de pistas importadas invisibles + descarga
   automática al importar (petición explícita de producto).
3. Repetir la verificación funcional.

**H03 PASO 8 y H04 PASO 6** (verificación funcional en dispositivo)
siguen pendientes — no tocados en la última sesión, sin bloquear
nada.

**Incidencia histórica sin confirmación de resolución:** actualización
de la APK fallaba con "conflicto con un paquete" (ver
`DOCS/ANNEX_H03.md`, sección "Incidencias Abiertas") — sin mención
desde S004, no sacarlo salvo que Miguel Ángel lo mencione.

**Duplicados de prueba sin limpiar (sin confirmación de vigencia):**
4 archivos físicos en `Canal IMAR/_sin_album` (3) y
`Canal IMAR/Sencillos` (1) — Miguel Ángel prefiere borrarlos desde la
app.

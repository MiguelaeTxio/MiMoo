# MIMOO — PUNTO DE REANUDACIÓN (NewFlow Android)

*Vive en `DOCS/RESUMPTION_POINT.md`. Sustituye a la bitácora de
sesión que antes era una skill (`doc-session-log-mimoo`) — con
NewFlow, cada paso ya queda registrado como un commit individual con
mensaje detallado en el propio `git log`, así que este archivo es
solo un resumen corto de "dónde estamos" y "qué sigue", no un diario
completo. Para el detalle exacto de qué cambió en cada paso, consultar
`git log` directamente sobre el repo clonado.*

---

## Última actualización: 2026-07-03 (cierre de sesión S002 NewFlow)

**Hito EN PROGRESO:** H05 — ver `DOCS/MASTER_DOCUMENT.md` para la
tabla completa de hitos y estado.

**Próxima sesión: PASO 6c, verificación real en dispositivo — máxima
prioridad, incluye confirmar un fix de cierre de aplicación** — ver
`DOCS/ANNEX_H05.md`, sección "Hoja de Ruta para la Siguiente Sesión".
Resumen:
1. **Prioridad 1:** reimportar el mismo álbum de YouTube Music que
   cerraba la app ("Importar enlace") y confirmar que ya no se
   cierra. Dos capas de arreglo aplicadas, ninguna probada en
   dispositivo todavía.
2. Reimportar Lou Reed - Transformer (búsqueda por álbum) y confirmar
   emparejamiento vía playlist (11 pistas de golpe, no una a una).
   Puede seguir fallando por cuota agotada de las pruebas de hoy
   hasta el reset de medianoche hora del Pacífico.
3. Probar búsqueda de álbum por artista o título sueltos
   ("Beethoven"/"Sinfonía") y el flujo completo de selección de
   candidato → tracklist → importar → visible en Biblioteca.
4. Probar "Importar enlace" con playlist normal de YouTube y con
   vídeo suelto.

**H03 PASO 8 y H04 PASO 6** (verificación funcional en dispositivo)
siguen pendientes — no tocados en esta sesión, sin bloquear nada.

**Incidencia histórica sin confirmación de resolución:** actualización
de la APK fallaba con "conflicto con un paquete" (ver
`DOCS/ANNEX_H03.md`, sección "Incidencias Abiertas") — confirmado en
esta sesión que es sistemático (10-12 versiones seguidas), no un caso
puntual; hipótesis de keystore antigua residual descartada. Pista real
de Miguel Ángel: revisar changelogs de NewPipe. **Pospuesto
explícitamente hasta terminar H05** — no sacarlo salvo que Miguel
Ángel lo mencione.

**Duplicados de prueba sin limpiar (sin confirmación de vigencia):**
4 archivos físicos en `Canal IMAR/_sin_album` (3) y
`Canal IMAR/Sencillos` (1) — Miguel Ángel prefiere borrarlos desde la
app.

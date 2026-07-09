# MIMOO — PUNTO DE REANUDACIÓN (NewFlow Android)

*Vive en `DOCS/RESUMPTION_POINT.md`. Sustituye a la bitácora de
sesión que antes era una skill (`doc-session-log-mimoo`) — con
NewFlow, cada paso ya queda registrado como un commit individual con
mensaje detallado en el propio `git log`, así que este archivo es
solo un resumen corto de "dónde estamos" y "qué sigue", no un diario
completo. Para el detalle exacto de qué cambió en cada paso, consultar
`git log` directamente sobre el repo clonado.*

---

## Última actualización: 2026-07-08 (cierre de sesión S006 NewFlow)

**Hito EN PROGRESO:** H06 — ver `DOCS/MASTER_DOCUMENT.md` para la
tabla completa de hitos y estado. H05 queda pausado sin tocar (PASO 6c
pendiente, ver `DOCS/ANNEX_H05.md`).

**S006 en resumen:** verificación en dispositivo de las dos features
de S005 (toggle Biblioteca + "Editar álbum") confirmada correcta.
Apertura de H06 completo (exportar/importar todo el repositorio vía
Google Drive) a petición de Miguel Ángel al incorporar una tablet:
PASOS 1-5 de la hoja de ruta implementados de punta a punta en esta
misma sesión (DTOs, integración real con Drive, pantalla Ajustes con
Exportar/Importar, sustitución destructiva, auto-descarga). Detalle
técnico completo en `DOCS/ANNEX_H06.md`, sección "COMPLETADAS EN
S006" — no repetirlo aquí.

**PASO 6 (verificación end-to-end en dispositivo) queda bloqueado al
cierre, sin resolver.** Un bug real de código sí se encontró y
corrigió en el camino (comprobación de `resultCode` que abandonaba la
autorización en silencio) — pero tras el fix, la autorización con
Drive sigue fallando con `ApiException: 8
[status=UNREGISTERED_ON_API_CONSOLE]`. Todas las comprobaciones de
configuración de Google Cloud verificables desde la consola (SHA-1,
package name, proyecto, test users, scope, API habilitada) se hicieron
con evidencia real y están correctas.

**Petición explícita de Miguel Ángel para la próxima sesión: que quien
retome esto investigue el error de cero, sin heredar las hipótesis de
S006 (caché de Play Services / propagación del cliente OAuth) como si
fueran conclusiones confirmadas — no lo son, están marcadas como tal
en `DOCS/ANNEX_H06.md`, sección "INVESTIGACIÓN ABIERTA — H06 PASO 6".**
Esa sección separa explícitamente los hechos verificados de las
conjeturas sin probar; empezar por ahí, releer el código real de
`DriveAuthorizationHelper.kt` en el clon, y contrastar de nuevo con
documentación actual antes de repetir ninguna de las hipótesis de
S006 como si fuera la explicación.

**Pendiente original de H05 (PASO 6c), pausado, no bloquea nada,
sigue ahí para cuando se retome H05:**
1. Reimportar Lou Reed - Transformer (búsqueda por álbum) y confirmar
   emparejamiento vía playlist (11 pistas de golpe).
2. Probar búsqueda de álbum por artista o título sueltos
   ("Beethoven"/"Sinfonía"), confirmando también el orden real de
   pistas.
3. Probar "Importar enlace" con playlist normal de YouTube y con
   vídeo suelto.

**Decisión pendiente de Miguel Ángel (no técnica, de producto):**
¿hace falta un menú de configuración para elegir tema/color de la
app? Confirmado que nunca existió; sin decisión tomada.

**H03 PASO 8 y H04 PASO 6** (verificación funcional en dispositivo)
siguen pendientes — no tocados en S006, sin bloquear nada.

# MIMOO — PUNTO DE REANUDACIÓN (NewFlow Android)

*Vive en `DOCS/RESUMPTION_POINT.md`. Sustituye a la bitácora de
sesión que antes era una skill (`doc-session-log-mimoo`) — con
NewFlow, cada paso ya queda registrado como un commit individual con
mensaje detallado en el propio `git log`, así que este archivo es
solo un resumen corto de "dónde estamos" y "qué sigue", no un diario
completo. Para el detalle exacto de qué cambió en cada paso, consultar
`git log` directamente sobre el repo clonado.*

---

## Última actualización: 2026-07-08 (cierre de sesión S005 NewFlow)

**Hito EN PROGRESO:** H05 — ver `DOCS/MASTER_DOCUMENT.md` para la
tabla completa de hitos y estado.

**S004 queda íntegramente verificado en dispositivo tras S005**
(signingConfig sobre actualización real, notificación de
reproducción, reintento de descargas, favoritos de álbum, compartir
enlaces — los cinco confirmados). Detalle completo en
`DOCS/ANNEX_H05.md`, sección "COMPLETADAS EN S005".

**S005 en resumen:** sesión de verificación de S004 + una incidencia
real nueva (Uri SAF "fantasma" tras clonar la tablet desde el
teléfono principal, resuelta reinstalando limpio) + dos features de
UI pedidas sobre la marcha: toggle letras/lista plana en Biblioteca
(Álbumes y Sencillos) y "Editar álbum" (corrige artista/álbum de
todas las pistas de un álbum a la vez, en vez de una por una).
Detalle técnico completo de ambas en `DOCS/ANNEX_H05.md`, sección
"COMPLETADAS EN S005" — no repetirlo aquí.

**Build roto tras el primer cierre de S005, ya arreglado.** El commit
de "Editar álbum" dejó un cierre de bloque duplicado en
`LibraryScreen.kt` que rompía la compilación (workflows #123 y #124
en rojo, mismo error). Diagnosticado y corregido en `b0af47d` en esta
misma continuación de sesión — **workflow #125 verde, APK ya en
PythonAnywhere.** Detalle en `DOCS/ANNEX_H05.md`.

**Próxima sesión — verificar en dispositivo lo nuevo de S005 antes de
nada más** (ninguna de las dos features de S005 se ha probado en el
móvil todavía — el build ya está verde, así que ahora sí se puede):
1. Toggle de vista en Biblioteca (icono en la TopAppBar, Álbumes y
   Sencillos).
2. "Editar álbum" — probar justo con el caso real que lo motivó
   (álbum de Herbert von Karajan con el nombre mal escrito).

**Pendiente original de H05 (PASO 6c), sigue sin confirmación real
tras dos sesiones seguidas de otra actividad (S004 verificación, S005
UI de Biblioteca):**
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
siguen pendientes — no tocados en S005, sin bloquear nada.

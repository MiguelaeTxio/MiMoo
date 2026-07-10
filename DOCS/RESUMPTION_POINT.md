# MIMOO — PUNTO DE REANUDACIÓN (NewFlow Android)

*Vive en `DOCS/RESUMPTION_POINT.md`. Sustituye a la bitácora de
sesión que antes era una skill (`doc-session-log-mimoo`) — con
NewFlow, cada paso ya queda registrado como un commit individual con
mensaje detallado en el propio `git log`, así que este archivo es
solo un resumen corto de "dónde estamos" y "qué sigue", no un diario
completo. Para el detalle exacto de qué cambió en cada paso, consultar
`git log` directamente sobre el repo clonado.*

---

## Última actualización: 2026-07-10 (cierre de sesión S007 NewFlow)

**Hito EN PROGRESO: H07 — Sincronización entre Dispositivos +
Actualizaciones In-App** (`DOCS/ANNEX_H07.md`, recién abierto, PCH al
cierre de esta misma sesión). Ver `DOCS/MASTER_DOCUMENT.md` para la
tabla completa. H06 queda **pausado** (no completado — ver abajo).

**S007 en resumen, de más a menos reciente:**

1. **H06 PASO 6 resuelto.** El bloqueo `UNREGISTERED_ON_API_CONSOLE`
   era un registro OAuth roto internamente en el proyecto de Google
   Cloud `mimoo-501004`, sin causa diagnosticable desde la consola —
   confirmado tras agotar con evidencia real todas las comprobaciones
   de configuración y código documentadas. Solución: proyecto nuevo
   desde cero, **`mimoo-drive`**, funcionó a la primera. "Exportar a
   Drive" verificado en dispositivo real. **"Importar desde Drive"
   sigue sin probar** — primer pendiente real si se retoma H06.
   Detalle completo en `DOCS/ANNEX_H06.md`.
2. **Bug real corregido**: `android:allowBackup="true"` causaba que
   el Auto Backup de Android restaurase `saf_root_uri` obsoleta tras
   reinstalar, rompiendo el selector de carpeta y el logging SAF en
   silencio. Ahora `allowBackup="false"`.
3. **YouTube Data API eliminada del proyecto por completo** (decisión
   de producto explícita, para poder borrar `mimoo-501004` sin dejar
   nada de MiMoo dependiendo de él). Búsqueda/emparejamiento de
   álbumes (H05) pasa a usar la misma búsqueda libre de `yt-dlp` que
   ya usa la pantalla de Búsqueda normal. `mimoo-501004` sin ninguna
   función viva de MiMoo — **borrado programado por Miguel Ángel para
   el 9 de agosto de 2026.**
4. **H07 abierto (PCH)**: dos funciones nuevas, sin dependencia
   técnica entre sí — sincronización incremental (no destructiva) vía
   Drive, y comprobación/descarga de actualizaciones de la app desde
   dentro de MiMoo. Hoja de ruta completa, con varias decisiones de
   producto explícitamente marcadas como pendientes de confirmar con
   Miguel Ángel antes de escribir código (emparejamiento de playlists
   en la sincronización; vía de publicación de la APK para
   actualizaciones — PythonAnywhere vs GitHub Releases). Ver
   `DOCS/ANNEX_H07.md`.
5. **`MASTER_DOCUMENT.md` corregido**: las secciones 2.3 (YouTube),
   2.4 (Drive) y 2.5 (secrets) estaban desactualizadas tras los
   cambios de esta misma sesión — puestas al día para que la próxima
   sesión no parta de información falsa.

**Siguiente sesión — orden sugerido (a decidir con Miguel Ángel, no
asumido):**
1. H07 PASO 1: añadir `silviaytxio@gmail.com` como test user en
   `mimoo-drive` (dos minutos, sin código).
2. H07 PASO 2: cerrar con Miguel Ángel la decisión de producto sobre
   emparejamiento de playlists en la sincronización, antes de escribir
   `BackupImportRepository`.
3. Alternativamente, si se prefiere cerrar H06 del todo primero:
   probar "Importar desde Drive" (PASO 6 de H06) antes de avanzar en
   H07 — ambas rutas son válidas, decisión de Miguel Ángel al empezar.

**Pendiente original de H05 (PASO 6c), pausado, no bloquea nada:**
1. Reimportar Lou Reed - Transformer (búsqueda por álbum) y confirmar
   emparejamiento — nota: tras S007 el emparejamiento ya no usa
   playlist-primero (ver `ANNEX_H06.md`), así que este paso también
   sirve para verificar el nuevo camino por yt-dlp.
2. Probar búsqueda de álbum por artista o título sueltos
   ("Beethoven"/"Sinfonía"), confirmando también el orden real de
   pistas.
3. Probar "Importar enlace" con playlist normal de YouTube y con
   vídeo suelto.

**Decisión pendiente de Miguel Ángel (no técnica, de producto):**
¿hace falta un menú de configuración para elegir tema/color de la
app? Confirmado que nunca existió; sin decisión tomada.

**H03 PASO 8 y H04 PASO 6** (verificación funcional en dispositivo)
siguen pendientes — no tocados en S007, sin bloquear nada.

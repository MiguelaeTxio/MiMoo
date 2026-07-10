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

**Hito EN PROGRESO:** H06 — ver `DOCS/MASTER_DOCUMENT.md` para la
tabla completa de hitos y estado. H05 queda pausado sin tocar (PASO 6c
pendiente, ver `DOCS/ANNEX_H05.md`).

**S007 en resumen:** el bloqueo de H06 PASO 6
(`UNREGISTERED_ON_API_CONSOLE`) quedó **resuelto**. Causa real: el
proyecto de Google Cloud `mimoo-501004` tenía su registro OAuth roto
internamente, sin ninguna causa diagnosticable desde la consola —
confirmado tras agotar con evidencia real todas las comprobaciones de
configuración y código documentadas (incluida la creación del cliente
Web application que faltaba, con 14h de margen sin efecto, y una
prueba de aislamiento de scope que descartó que fuera específico de
`drive.file`). La solución fue crear un proyecto de Google Cloud
nuevo desde cero — **`mimoo-drive`** — con la misma configuración
mínima: funcionó a la primera. "Exportar a Drive" verificado en
dispositivo real. **"Importar desde Drive" queda sin probar,
siguiente paso de PASO 6.** Detalle técnico completo en
`DOCS/ANNEX_H06.md`, sección "INVESTIGACIÓN H06 PASO 6 — RESUELTA EN
S007" y "COMPLETADAS EN S007".

**Cambios adicionales de esta sesión:**
- Bug real corregido: `android:allowBackup="true"` causaba que
  Android restaurase `saf_root_uri` obsoleta tras reinstalar la app
  (Auto Backup), rompiendo el selector de carpeta y el logging SAF en
  silencio. Ahora `allowBackup="false"`.
- **YouTube Data API eliminada del proyecto por completo** (decisión
  de producto de Miguel Ángel, para poder borrar `mimoo-501004` sin
  dependencias vivas): la búsqueda/emparejamiento de álbumes
  (`AlbumSearchViewModel`/`AlbumMatchRepository`) pasa a usar la
  búsqueda libre de `yt-dlp` que ya usa la pantalla de Búsqueda normal
  — sin cuota, sin API key. `YouTubeApiService.kt`/`YouTubeRepository.kt`
  eliminados, `YOUTUBE_API_KEY` fuera del workflow y de
  `build.gradle.kts`. Build verde tras el cambio.
- `mimoo-501004` sin ninguna función viva de MiMoo. **Borrado
  programado por Miguel Ángel para el 9 de agosto de 2026.**
- `GOOGLE_OAUTH_ANDROID_CLIENT_ID` actualizado al Client ID del
  proyecto `mimoo-drive` (el de `mimoo-501004` quedó obsoleto).

**Siguiente sesión — orden sugerido:**
1. Probar "Importar desde Drive" en dispositivo real para cerrar H06
   PASO 6 del todo (export ya verificado).
2. Si PASO 6 cierra, H06 queda completo — decidir con Miguel Ángel si
   se retoma H05 (PASO 6c pendiente, ver abajo) o se abre el
   siguiente hito.

**Pendiente original de H05 (PASO 6c), pausado, no bloquea nada,
sigue ahí para cuando se retome H05:**
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

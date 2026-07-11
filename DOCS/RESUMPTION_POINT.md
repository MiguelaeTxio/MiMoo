# MIMOO — PUNTO DE REANUDACIÓN (NewFlow Android)

*Vive en `DOCS/RESUMPTION_POINT.md`. Sustituye a la bitácora de
sesión que antes era una skill (`doc-session-log-mimoo`) — con
NewFlow, cada paso ya queda registrado como un commit individual con
mensaje detallado en el propio `git log`, así que este archivo es
solo un resumen corto de "dónde estamos" y "qué sigue", no un diario
completo. Para el detalle exacto de qué cambió en cada paso, consultar
`git log` directamente sobre el repo clonado.*

---

## Última actualización: 2026-07-11 (cierre de sesión S008 NewFlow)

**Hito EN PROGRESO: H08 — Búsqueda de Listas de Reproducción +
Música Relacionada** (`DOCS/ANNEX_H08.md`, recién abierto, PCH al
cierre de esta misma sesión). Ver `DOCS/MASTER_DOCUMENT.md` para la
tabla completa. **H07 queda pausado, completado y verificado en
dispositivo real con dos dispositivos** (ver abajo).

**S008 en resumen — sesión larga, H07 construido de cero a verificado
funcionalmente, con varias vueltas de fixes reales tras pruebas en
dispositivo:**

1. **H07 completo, las cuatro partes:**
   - PARTE 0: persistencia del `youtubeId` embebido como metadato
     Vorbis Comment en cada `.opus` + recuperación en
     `LibraryReconciler`.
   - PARTE 1: sincronización automática entre dispositivos —
     rediseñada tres veces sobre la marcha a partir de bugs reales
     encontrados probando en dispositivo (no de fallos de
     compilación): identidad estable por dispositivo
     (`DeviceIdentityManager`), bloqueo de altas/bajas sin conexión
     (`NetworkConnectivityChecker` + `AutoSyncPusher.executeIfConnected()`),
     máquina de 3 casos al arrancar (sin copia / mismo dispositivo
     desincronizado / otro dispositivo con pregunta explícita),
     verificación disco↔BBDD en cada sincronización
     (`LibraryReconciler.verifyDiskState()`), y restauración
     **selectiva** (`applyCloudWinsTargeted()`, solo toca lo que
     difiere) tras descubrir que la primera versión redescargaba todo
     el repositorio en cada restauración.
   - PARTE 2: actualizaciones in-app — descartada la vía inicial
     (ruta oculta en EnterpriseBot) por decisión explícita de Miguel
     Ángel de no mezclar proyectos; sustituida por un repositorio
     GitHub nuevo y dedicado, público, solo para Releases
     (`MiguelaeTxio/AndroidReleases`), con `manifest.json` generado
     por el propio workflow de MiMoo.
   - PARTE 3: reproducción cíclica y aleatoria sobre ExoPlayer.
2. **PIN de acceso** ("Introduce tu PIN, Silvia") añadido dentro de la
   misma sesión, independiente del resto.
3. **Bugs reales encontrados y corregidos tras pruebas en
   dispositivo** (no adivinados, todos con evidencia — logs o
   capturas de Miguel Ángel):
   - `REQUEST_INSTALL_PACKAGES` faltante — el botón "Instalar" no
     hacía nada, sin error visible.
   - `buildCurrentBundle()` subía a la sincronización **toda** la
     caché de resultados de búsqueda, no solo lo realmente
     descargado — causaba que la sincronización disparase la
     descarga de decenas de pistas ajenas nunca elegidas por Miguel
     Ángel.
   - `setTab()` en Biblioteca no reseteaba el nivel de navegación al
     pulsar una pestaña ya activa.
   - Formato del archivo de sincronización cambiado a mitad de sesión
     (de `BackupBundle` pelado a `SyncEnvelope` con identidad) sin
     manejo defensivo — dejaba a la app en bucle de error si Drive
     tenía un archivo del formato anterior.
4. **H08 abierto (PCH)**: búsqueda de listas de reproducción (alcance
   cerrado, sin ambigüedad) + música relacionada/"Radio" (alcance
   deliberadamente sin cerrar — Miguel Ángel lo planteó como idea sin
   resolver, con preguntas de diseño abiertas documentadas en el
   anexo, no como encargo ya decidido).

**Siguiente sesión — orden sugerido (a decidir con Miguel Ángel, no
asumido):**
1. H08 PARTE 1 (búsqueda de listas) — se puede empezar directamente,
   sin decisiones pendientes.
2. H08 PARTE 2 (música relacionada) — **empezar por una conversación
   de diseño con Miguel Ángel**, no por código; ver "Preguntas de
   diseño abiertas" en `DOCS/ANNEX_H08.md`.
3. H07 PASO 1.5/3.3 (verificación funcional que quedó pendiente antes
   de que aparecieran los bugs reales que se llevaron el resto de la
   sesión) — comprobar cíclico/aleatorio con una cola larga en
   dispositivo real, si no se hizo ya.

**Pendientes de H07 documentados como aceptados, no bloqueantes** (ver
`DOCS/ANNEX_H07.md` para el detalle): añadir/quitar pista de una
playlist ya existente y borrado de pista desde Biblioteca sí están
enganchados a la sincronización (S008 tercera vuelta); lo que queda
fuera de alcance a propósito es cualquier fusión de contenido de
playlists coincidentes por nombre entre dispositivos — el diseño
todo-o-nada de la sincronización lo hace innecesario, no es un hueco
pendiente.

**Pendiente original de H05 (PASO 6c), pausado, no bloquea nada:**
1. Reimportar Lou Reed - Transformer (búsqueda por álbum) y confirmar
   emparejamiento.
2. Probar búsqueda de álbum por artista o título sueltos
   ("Beethoven"/"Sinfonía"), confirmando también el orden real de
   pistas.
3. Probar "Importar enlace" con playlist normal de YouTube y con
   vídeo suelto.

**Decisión pendiente de Miguel Ángel (no técnica, de producto):**
¿hace falta un menú de configuración para elegir tema/color de la
app? Confirmado que nunca existió; sin decisión tomada.

**H03 PASO 8 y H04 PASO 6** (verificación funcional en dispositivo)
siguen pendientes — no tocados en S008, sin bloquear nada.

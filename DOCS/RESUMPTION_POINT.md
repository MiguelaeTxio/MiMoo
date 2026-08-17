# PUNTO DE REANUDACIÓN — MiMoo

**Última sesión cerrada:** S033 (2026-08-17)
**Hito con la hoja de ruta activa:** ver `DOCS/ANNEX_ROUTER.md`

> El estado de los hitos vive **exclusivamente** en
> `DOCS/ANNEX_ROUTER.md`. Este archivo no lo declara ni lo duplica.

---

## Qué hacer en la siguiente sesión

**H12 (Directorio de Música + Favoritos sin Descarga) EN PROGRESO** --
PCH de esta misma sesión (S033), dos fallos reales reportados por
Miguel Ángel sobre lo que S018 dio por construido: el Explorador
carece de campo de búsqueda para buscar en MusicBrainz, y los
favoritos de artista/álbum no persisten. **Sin diagnosticar
todavía** -- la hoja de ruta ejecutable (leer el código real antes de
suponer la causa, dos hipótesis por síntoma sin confirmar ninguna)
está completa en `DOCS/ANNEX_H12.md`, sección "Hoja de Ruta para la
Siguiente Sesión que retome H12".

## H18 (Play y Ordenación de Listas de Items) -- PAUSADO, sin incidencia propia esta sesión

Pasó a PAUSADO en el PCH de S033 sin recibir trabajo de
implementación -- toda la sesión, desde su apertura en S032, se fue
en la cascada de incidencias reales sobre H15 (ver abajo). Sigue
exactamente como quedó en S032: diseño y los cinco bloques de código
cerrados, build verde, **sin código pendiente -- solo verificación en
dispositivo real** (las cuatro combinaciones de orden en cada
pantalla, la matriz de play/aleatorio, la migración sin pérdida de
datos). Detalle completo en `DOCS/ANNEX_H18.md`, "COMPLETADAS EN
S032".

## H15 (miMooutCast) -- PAUSADO, cascada de incidencias reales resueltas en S033 sobre el generador de base de datos

Sesión entera de incidencias puntuales sobre código de un hito
pausado (mismo criterio que S031 con el fix de karaoke sobre H17).
Punto de partida: Miguel Ángel reportó que el generador de base de
datos de miMooutCast (H15, punto 50) "sigue igual de mal" al parar y
reanudar. Cascada real, todo documentado con número de punto en
`DOCS/ANNEX_H15.md` (51-56):

1. **Punto 51**: la pantalla se quedaba con el género/etiqueta de una
   tanda anterior al saltar de largo por géneros ya completos --
   corregido actualizando el progreso al ENTRAR en cada género, no
   solo al buscar.
2. **Punto 52**: cada "Parar" + reanudar reintentaba ENTERO cada
   género nicho ya agotado, gastando hasta 15 búsquedas reales cada
   vez sin avanzar -- corregido persistiendo `doneGenres` en el
   fichero (nuevo formato `{tracks, doneGenres}`, con compatibilidad
   hacia atrás).
3. **Punto 53**: el generador contaminaba `radio_relacionados_debug.txt`
   (prohibido explícitamente en S032) por no activar
   `mimooutcastSessionFlag` -- corregido.
4. **Punto 54**: causa real de la mayoría de agotamientos prematuros,
   confirmada con log real -- `suggestWorkForGenre()` pasaba títulos
   de ÁLBUM/EP/directo/recopilatorio a YouTube como si fueran una
   canción suelta, rechazados por el filtro de duración. Corregido
   filtrando por `primary-type == "Single"`.
5. **Punto 55**: la pantalla mostraba la posición del recorrido (que
   arranca de cero cada apertura de la app) como si fuera "géneros
   completados", dando pie a lecturas erróneas de los datos (total
   grande con índice bajo, que Miguel Ángel cuestionó dos veces con
   razón). Corregido con `genresCompleted` (`doneGenres.size`,
   persistente y monótono) como dato principal en pantalla.
6. **Punto 56**: estudio real de Miguel Ángel del fichero exportado
   reveló 377 de 577 géneros "agotados" con CERO temas -- incluidos
   jazz, blues, breakbeat, afro house, amapiano, celtic, classical.
   Un género real encuentra al menos alguno en 15 intentos si el
   mecanismo funciona; cero es señal de fallo, no de vacío. Corregido:
   un género a cero nunca se marca agotado, y se limpian los ya
   heredados al cargar (sin tocar ni un tema ya encontrado).

**Estado real del fichero de Miguel Ángel a mitad de sesión** (antes
del punto 56, dato de referencia): 9548-9557 temas guardados, 206
géneros con al menos 1 tema (55 completos a 100/100, el resto
parciales, muchos por debajo de 20). El recorrido completo (581
géneros) lleva **varios días** de ejecución real en el dispositivo --
confirmado como ritmo normal por Miguel Ángel, iba por 358/581 al
cierre de esta sesión.

**Pendiente, sin fecha ni alcance definidos todavía** ("ya te
explicaré", palabras de Miguel Ángel): una segunda pasada sobre H15
para cubrir lo que falte tras el recorrido actual. No empezar sin que
él lo concrete primero.

**Rechazado explícitamente en esta sesión**: generar a mano una lista
de temas/artistas por género para que la app solo tuviera que
verificarla contra YouTube. Sin acceso en vivo a ninguna base de
datos musical desde este entorno (MusicBrainz bloqueado por
robots.txt, confirmado varias veces), una lista así habría metido
datos no verificados -- mismo criterio de "nada sin verificar de
verdad" que rige el resto del proyecto (paralelo explícito con
AperturasAjedrez). Se optó por corregir la causa real (puntos 54 y
56) en vez de rodearla con datos inventados.

## H17 (Karaoke & Lyrics) -- PAUSADO, diseño y construcción completos, sin verificar en dispositivo

Sin cambios en S033. Sigue exactamente como quedó en S031/S032: sin
código pendiente, solo verificación en dispositivo real, con foco
especial en los tres temas que en `letras_debug.txt` fallaban con 404
antes del fix de `cleanSongTitle()`. Detalle completo en
`DOCS/ANNEX_H17.md`, "COMPLETADAS EN S031".

## H16 (Lista Negra) -- PAUSADO, cinco puntos de código completos, sin fallos conocidos

Sin cambios en S033. Sigue sin verificación exhaustiva en dispositivo
de todos los casos, pero nada reportado como roto.

## Trabajo pendiente de otras sesiones, sin tocar en S033

1. **Auditoría pendiente de la semilla de 1.161 artistas**
   (`anchor_artists.json`) -- sigue sin tocar, no se puede verificar
   contra MusicBrainz en vivo desde este entorno de trabajo.
2. **Fallo de streaming ajeno a la Radio**: `notification_debug.txt`
   mostraba errores `ERROR_CODE_IO_BAD_HTTP_STATUS`/
   `NETWORK_CONNECTION_FAILED`. Se corrigió un caso concreto en S027,
   sin confirmar si cubre el resto.
3. **Bug sin localizar**: el `.txt` de log compartido desde el móvil
   llegaba a veces con contenido viejo. Mitigado indirectamente
   bajando `MAX_LINES`, causa real sin diagnosticar.
4. **H08, dos hallazgos de S028 sin confirmar con log real**: umbral
   de coincidencia al 40% (Loquillo y Los Trogloditas, "se quedó
   parada totalmente"), y Émilie Simon "falló estrepitosamente" antes
   del arreglo de la sonda de conectividad -- sin reprobar desde
   entonces.

## Incidencias de proceso a tener en cuenta

- **Antes de dar una tanda por representativa del comportamiento
  real, comprobar si los géneros/casos que interesan ya están
  marcados como "hechos" en algún mecanismo de resume/skip.** Bug de
  proceso real de S033: se pidió un log de prueba para diagnosticar
  jazz/blues/etc., pero esos géneros ya estaban marcados agotados por
  el propio fix de la sesión, así que el log nuevo nunca podía
  llegar a mostrarlos -- el propio mecanismo de "no reintentar lo ya
  hecho" bloqueaba la evidencia que hacía falta para verificarlo.
- **Limpieza de título que quita un segmento por posición debe
  comprobar SIEMPRE que ese segmento coincide de verdad con lo que se
  cree que es**, nunca asumirlo solo por estar en esa posición. Dos
  bugs reales de la misma familia en S031 (`PlayerBarViewModel` +
  `RadioRepository.stripTitleNoise()`), ambos arreglados comparando el
  segmento candidato contra el artista normalizado antes de darlo por
  bueno.
- **Nunca poner `--` dentro de un comentario XML.** Rompió el build
  una vez en S028 (`AndroidManifest.xml`) -- la especificación XML lo
  prohíbe en cualquier punto de un comentario. El resto del proyecto
  usa `—` (guion largo) por este motivo.
- **Antes de añadir un composable nuevo, comprobar sus imports reales
  del archivo, no darlos por hechos.** Bug real de S030 en
  `MainActivity.kt` (imports de `Row`/`fillMaxWidth`/etc. ausentes).
- **Patrón muy repetido S027-S033**: la inmensa mayoría de los bugs
  reales de estas sesiones se encontraron SOLO al probar el arreglo
  anterior con datos/logs/capturas reales de Miguel Ángel -- nunca
  darlos por buenos sin esa confirmación, por razonable que parezca el
  arreglo sobre el papel.
- **`PopurriDebugLogger`** (`popurri_favoritos_debug.txt`) -- pedir
  este archivo específico ante cualquier fallo futuro de popurrís de
  Favoritos.
- **El zip de logs de GitHub Actions sigue sin ser accesible por red**
  desde el entorno del modelo -- usar la API de anotaciones del check
  (`/check-runs/{job_id}/annotations`), no el zip.
- **`isFromRadio = true` es obligatorio en CUALQUIER `QueueItem` que
  añada un motor de reproducción automática** (Radio, miMooutCast, lo
  que venga después) -- sin él, `onMediaItemTransition` resetea el
  ancla de la sesión en marcha. Bug real en H15/S030, con el síntoma
  apareciendo solo tras minutos de reproducción real.
- **MusicBrainz está bloqueado por robots.txt para el modelo en este
  entorno de trabajo** (confirmado repetidamente en S033, `web_fetch`
  rechaza `musicbrainz.org/ws/2/...`) -- cualquier verificación en vivo
  de una consulta a la API tiene que hacerse con logs reales del
  dispositivo, nunca reproduciendo la llamada desde aquí.

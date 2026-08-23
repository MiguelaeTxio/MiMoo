# PUNTO DE REANUDACIÓN — MiMoo

**Última sesión cerrada:** S034 (2026-08-23)
**Hito con la hoja de ruta activa:** ver `DOCS/ANNEX_ROUTER.md`

> El estado de los hitos vive **exclusivamente** en
> `DOCS/ANNEX_ROUTER.md`. Este archivo no lo declara ni lo duplica.

---

## Qué hacer en la siguiente sesión

**H12 (Directorio de Música + Favoritos sin Descarga) EN PROGRESO** --
las dos hipótesis de S033 quedaron diagnosticadas y corregidas esta
sesión (S034): campo de búsqueda embebido en el Explorador, y fix
real de persistencia de favoritos (`AutoSyncPusher` usaba el chequeo
de conectividad con falsos negativos ya corregido en `RadioRepository`
para el bug de "Radio detenida"). **Sin diagnóstico nuevo pendiente**
-- solo queda **verificación en dispositivo real** de los tres puntos
cerrados esta sesión (búsqueda embebida, persistencia de favoritos,
sidebar a tamaño normal + scrollable). Hoja de ruta ejecutable
completa en `DOCS/ANNEX_H12.md`, sección "Hoja de Ruta para la
Siguiente Sesión que retome H12".

## H15 (miMooutCast) -- PAUSADO, cascada larga de incidencias reales sobre el generador y la reproducción en S034

Mismo criterio que S031/S033: incidencia puntual sobre código de un
hito pausado, sin PCH -- H12 fue el hito EN PROGRESO durante toda la
sesión. Detalle técnico completo en `DOCS/ANNEX_H15.md`, sección
"COMPLETADAS EN S034". Resumen:

1. **41 subgéneros descartados en TODA la app** (miMooutCast Y Radio
   automática) -- llevaban atascados a 0 temas desde la pasada
   anterior del generador, confirmado con análisis real (dos
   exportaciones + 67 minutos de log) antes de tocar código. Fuente
   única: `GenreTree.isBarren()`, cortando ANTES de cualquier llamada
   de red en `RadioRepository.suggestWorkForGenre()`/`findCandidates()`.
   Hubo una corrección real a mitad de camino (primer intento tocó
   `genre_tree.json` en vez del catálogo propio de miMooutCast,
   afectando sin necesidad a la Radio -- descubierto y corregido en la
   misma sesión, restaurando el árbol byte a byte).
2. **`mimooutcast_seed.json` bundleado en `assets/`** -- copia exacta
   del export real de Miguel Ángel (22.220 temas/536 géneros). Cierra
   el objetivo de fondo de todo el hito, aclarado por él con fuerza:
   la lista tenía que viajar empaquetada en el APK, no regenerarse en
   cada dispositivo.
3. **Motor de cola instantánea + búsqueda en paralelo + curación de
   enlaces rotos por reinstalación** -- método completo dictado por
   Miguel Ángel: al elegir género se encolan al instante hasta 100
   pistas de la semilla, la búsqueda en vivo sigue de fondo, los
   enlaces rotos se anotan con su sustituto (`MimooutcastBrokenLinksLogger`,
   `mimooutcast_broken_links.json`), y a partir de 10 rotos en un
   género aparece un aviso en Ajustes. Generaliza a todos los géneros
   un mecanismo que ya existía, probado, solo para Clásica.

**Nada de esta cascada se ha verificado en dispositivo real
todavía.** Hoja de ruta de verificación completa (orden concreto de
qué probar primero) en `DOCS/ANNEX_H15.md`, sección "Hoja de Ruta
para la Siguiente Sesión que retome H15".

**Ciclo de curación pendiente, sin fecha:** cuando Miguel Ángel
comparta un `mimooutcast_broken_links.json` real (botón "Compartir
enlaces rotos" de Ajustes) tras uso real del dispositivo, sustituir
cada enlace roto por su sustituto dentro de `mimooutcast_seed.json`
antes de la siguiente build.

## Peticiones de producto nuevas, sin hito asignado todavía (S034, cierre de sesión)

Miguel Ángel las dio al cierre, palabras suyas: *"la siguiente sesión
tardará dos temas, no sé qué hitos tocará"* -- no encajan en la hoja
de ruta técnica de H12 ni de H15, quedan aquí hasta que él decida a
qué hito pertenecen (o si hace falta uno nuevo):

1. **Reproducción de favoritos (artistas/álbumes) se muere esperando
   el primer tema.** Cita textual: *"al seleccionar artistas favoritos
   o álbumes favoritos lo primero es poner un tema local, hoy he
   intentado escuchar una selección de artistas y se muere uno
   esperando el primer tema, no hay espera, se pone uno local y luego
   se genera el resto de la cola de reproducción."* Sin diagnosticar
   -- leer primero el código real de la reproducción de
   favoritos/selección de artistas (candidato: `FavoritesViewModel`/
   `LibraryViewModel`-adjacent, comprobar si hay alguna espera
   bloqueante al primer tema en vez de arrancar con algo local
   inmediato y generar el resto después) antes de suponer la causa.
2. **Normalización de la salida de audio.** Sin más detalle todavía
   -- a concretar con Miguel Ángel qué está pidiendo exactamente
   (¿ganancia por pista, compresión de rango dinámico, algo del
   propio ExoPlayer/AudioProcessor?) antes de diseñar nada.

## H18 (Play y Ordenación de Listas de Items) -- PAUSADO, sin incidencia propia esta sesión

Sin cambios desde S033. Sigue exactamente como quedó en S032: diseño
y los cinco bloques de código cerrados, build verde, **sin código
pendiente -- solo verificación en dispositivo real** (las cuatro
combinaciones de orden en cada pantalla, la matriz de play/aleatorio,
la migración sin pérdida de datos). Detalle completo en
`DOCS/ANNEX_H18.md`, "COMPLETADAS EN S032".

## H17 (Karaoke & Lyrics) -- PAUSADO, diseño y construcción completos, sin verificar en dispositivo

Sin cambios desde S033. Sigue exactamente como quedó en S031/S032:
sin código pendiente, solo verificación en dispositivo real, con foco
especial en los tres temas que en `letras_debug.txt` fallaban con 404
antes del fix de `cleanSongTitle()`. Detalle completo en
`DOCS/ANNEX_H17.md`, "COMPLETADAS EN S031".

## H16 (Lista Negra) -- PAUSADO, cinco puntos de código completos, sin fallos conocidos

Sin cambios desde S033. Sigue sin verificación exhaustiva en
dispositivo de todos los casos, pero nada reportado como roto.

## Trabajo pendiente de otras sesiones, sin tocar en S034

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

- **S034, nueva**: al construir un mensaje de commit con heredoc en
  varios pasos, verificar SIEMPRE el mensaje final con
  `git log -1 --format="%B"` antes de darlo por bueno -- una plantilla
  reutilizada de forma descuidada dejó basura literal (`EOF`, la
  siguiente línea de comando) dentro del cuerpo de un commit ya
  pusheado en esta sesión. Preferir heredocs limpios de un solo bloque,
  sin anidar un segundo `EOF`/comando dentro del mismo heredoc.
- **S034, nueva**: tras cualquier fix que module qué géneros/etiquetas
  se excluyen o incluyen en un motor compartido por varias
  funcionalidades (Radio + miMooutCast, ambas sobre `GenreTree`),
  verificar el estado real del archivo tocado con `git diff`/lectura
  directa antes de reportarlo a Miguel Ángel como hecho -- un primer
  intento en esta sesión modificó un archivo distinto al que se
  describió verbalmente, y no se detectó hasta una verificación
  posterior no pedida explícitamente.
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
- **Patrón muy repetido S027-S034**: la inmensa mayoría de los bugs
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

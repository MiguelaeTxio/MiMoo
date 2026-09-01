# PUNTO DE REANUDACIÓN — MiMoo

**Última sesión cerrada:** S035 (2026-08-23/28)
**Hito con la hoja de ruta activa:** ver `DOCS/ANNEX_ROUTER.md`

> El estado de los hitos vive **exclusivamente** en
> `DOCS/ANNEX_ROUTER.md`. Este archivo no lo declara ni lo duplica.

---

## Qué hacer en la siguiente sesión — EMPEZAR POR AQUÍ

Miguel Ángel dio dos peticiones explícitas al cerrar S035, palabras
suyas, sin diagnosticar ni diseñar todavía en esta sesión:

1. **El volumen se baja solo.** *"El volumen de audio... se baja solo
   y hay que estar subiendo el volumen cada vez que otra aplicación
   toca el volumen. Antes estaba mejor pq no había que tocar nada.
   Volvemos a dejarlo como estaba originalmente."* Contexto real: en
   la propia S035 se quitó por completo el `AudioFocusRequest` que
   causaba PAUSAS ante notificaciones de otras apps (ver cascada de
   llamadas más abajo) -- pero este síntoma es distinto ("ducking",
   bajada de volumen, no pausa) y sigue reportado DESPUÉS de ese
   revert. Empezar leyendo si queda algún mecanismo de audio en
   `PlayerManager.kt` (aparte de `TelephonyManager`, que no debería
   tocar volumen en ningún caso) que pueda estar causando esto --
   nunca asumir la causa sin leer el código real primero.
2. **Rediseño de la interfaz del ExoPlayer.** *"Tenemos muchos
   controles, hay que añadir compartir el tema que se está tocando,
   habría que poner una fila con lo que son los controles de
   reproducción y otra fila con los like/dislike/add2list/download/
   share."* Sin diseño cerrado -- sesión de diseño antes de tocar
   código, mismo criterio que el resto del proyecto para cambios de
   alcance abierto (confirmar con Miguel Ángel qué iconos exactos van
   en cada fila, si "share" comparte el enlace de YouTube o algo
   propio de la app, etc.).

**H12 (Directorio de Música + Favoritos sin Descarga) EN PROGRESO** --
sin tocar en S035 (sesión entera de incidencias sobre otros hitos, ver
más abajo). Sigue exactamente como quedó en S034: **sin diagnóstico
nuevo pendiente**, solo **verificación en dispositivo real** de los
tres puntos cerrados en S034 (búsqueda embebida en el Explorador,
persistencia de favoritos de artista/álbum, sidebar a tamaño normal +
scrollable). Hoja de ruta ejecutable completa en
`DOCS/ANNEX_H12.md`, sección "Hoja de Ruta para la Siguiente Sesión
que retome H12".

## Cascada larguísima de incidencias reales en S035 (H15/H16/H17/H18 + varios)

Mismo criterio que S033/S034: incidencias puntuales sobre código de
hitos pausados, sin PCH -- H12 fue el hito EN PROGRESO durante toda la
sesión, sin tocarse. **Detalle técnico completo en
`DOCS/ANNEX_H12.md`, sección "Cascada de incidencias reales sobre
H15/H16/H17/H18 -- S035"** -- resumen muy breve aquí:

- **H15 (miMooutCast)**: tres fuentes de datos nuevas cosechadas y
  fusionadas en el diccionario de éxitos (Spotify100, MUZIKALIA,
  Spotify años 50 -- cierra el hueco que faltaba). Semilla de década
  VALIDADA contra YouTube (2.815 canciones, generada en dispositivo
  tras fallar vía GitHub Actions, mismo motivo que la de género en su
  día -- bloqueo de IP de centro de datos). Varios bugs reales
  encontrados y corregidos por el camino: prioridad de la semilla
  ignorada con "Conocido en España" encendido, semillas sin carátula,
  candidatos fallidos repitiéndose sin fin, botones que ignoraban un
  segundo toque en silencio, URLs de streaming caducadas sin
  reintento.
- **H16 (Lista Negra)**: dos bugs reales de fondo -- comparación
  exacta en vez de por contención (un artista vetado con texto de más
  no coincidía), y duetos donde el campo `artist` guardado solo tenía
  uno de los dos nombres. Además, los recopilatorios propios nunca
  comprobaban "no me gusta" a nivel de tema.
- **Llamadas telefónicas**: evolución completa en tres intentos (foco
  de audio explícito -> rompió miMooutCast, revertido; foco manual con
  `AudioFocusRequest` -> mejoró pero fallaba a mitad de llamada;
  `TelephonyManager` real con `READ_PHONE_STATE` -> fiable de verdad
  para llamadas). Decisión final de Miguel Ángel: quitar el
  `AudioFocusRequest` por completo, porque pausaba ante CUALQUIER
  notificación de cualquier app, no solo llamadas -- se conserva solo
  `TelephonyManager`, que no le afecta ese problema.
- **H17 (Karaoke)**: panel de letras sincronizadas igualado en tamaño
  al de letra plana, y desplazamiento de la línea resaltada corregido
  a una sola animación (antes subía arriba y luego bajaba, dos
  animaciones seguidas).
- **Otros sin hito claro**: refuerzo de volumen configurable en
  Ajustes (`LoudnessEnhancer`, 0-12dB); cola de reproducción (H18) se
  desplaza sola al tema actual y lo mantiene visible, y ya no corta la
  pista en curso al vaciarse; botón "+" para añadir a lista desde el
  reproductor; filtro de texto en Favoritos; logo de la app como
  carátula de respaldo cuando de verdad no hay ninguna.

**Gran parte de esta cascada SÍ fue confirmada por Miguel Ángel
durante la propia sesión** (Lista Negra, llamadas tras el ajuste
final, semilla de década, carátulas, karaoke) -- pero no hay
verificación exhaustiva de todo. Si algo de esta lista vuelve a
fallar, pedir SIEMPRE evidencia real (log/captura) antes de tocar
nada, nunca reabrir el diagnóstico a ciegas.

## H18 (Play y Ordenación de Listas de Items) -- PAUSADO, sin incidencia propia esta sesión

Sin cambios desde S033/S034 en su propio alcance (aparte de las
mejoras de la cola documentadas en la cascada de arriba, que son
ampliaciones, no parte del hito original). Diseño y los cinco bloques
de código originales siguen cerrados, build verde, sin código
pendiente -- solo verificación en dispositivo real. Detalle completo
en `DOCS/ANNEX_H18.md`, "COMPLETADAS EN S032".

## Trabajo pendiente de otras sesiones, sin tocar en S035

1. **Auditoría pendiente de la semilla de 1.161 artistas**
   (`anchor_artists.json`) -- sigue sin tocar, no se puede verificar
   contra MusicBrainz en vivo desde este entorno de trabajo.
2. **Reproducción de favoritos (artistas/álbumes) se muere esperando
   el primer tema** -- petición de S034, sin diagnosticar todavía.
   Cita textual: *"al seleccionar artistas favoritos o álbumes
   favoritos... hoy he intentado escuchar una selección de artistas y
   se muere uno esperando el primer tema."*
3. **Bug sin localizar**: el `.txt` de log compartido desde el móvil
   llegaba a veces con contenido viejo. Mitigado indirectamente
   bajando `MAX_LINES`, causa real sin diagnosticar.
4. **H08, dos hallazgos de S028 sin confirmar con log real**: umbral
   de coincidencia al 40% (Loquillo y Los Trogloditas), y Émilie Simon
   -- sin reprobar desde entonces.

## Incidencias de proceso a tener en cuenta

- **S035, nueva -- la más cara de toda la sesión**: cuando dos
  mecanismos comparten una misma bandera de estado (`pausedByAudioFocusLoss`/
  `pausedByCallState`), cualquier cambio en UNO de los dos puede dejar
  la bandera en un estado que el OTRO no espera -- pasó dos veces
  seguidas en la cascada de llamadas de esta sesión (una red de
  seguridad vieja deshaciendo la reanudación del mecanismo nuevo).
  Antes de eliminar o simplificar un mecanismo, buscar TODOS los
  puntos que leen/escriben la misma bandera, no solo los que se están
  tocando a propósito.
- **S035, nueva**: GitHub Actions no es viable para tareas que
  necesiten "parecer un usuario real" ante servicios como YouTube --
  las IPs de centro de datos se bloquean con verificación anti-bot
  ("Sign in to confirm you're not a bot"), confirmado dos veces esta
  sesión (cosecha de Spotify inicial, validación de la semilla de
  década). La alternativa real y ya probada en este proyecto es
  generar en el propio dispositivo de Miguel Ángel.
- **S035, nueva**: nunca poner `--` dentro de un comentario XML --
  sigue rompiendo el build cuando se olvida (pasó otra vez esta
  sesión, en `AndroidManifest.xml`, pese a estar ya documentado desde
  S028). Usar siempre `—` (guion largo).
- **S035, nueva**: tras cualquier `str_replace` en un bloque grande,
  verificar el balance de llaves/paréntesis del archivo ENTERO
  inmediatamente, no solo mirar el fragmento editado -- varias
  ediciones de esta sesión dejaron llaves o paréntesis huérfanos que
  el propio recuento numérico detectó antes de comitear, evitando
  builds rotos.
- **Al construir un mensaje de commit con heredoc en varios pasos,
  verificar SIEMPRE el mensaje final con `git log -1 --format="%B"`**
  antes de darlo por bueno (lección de S034, sigue vigente).
- **Antes de dar una tanda por representativa del comportamiento
  real, comprobar si los géneros/casos que interesan ya están
  marcados como "hechos" en algún mecanismo de resume/skip** (lección
  de S033, sigue vigente).
- **Limpieza de título que quita un segmento por posición debe
  comprobar SIEMPRE que ese segmento coincide de verdad con lo que se
  cree que es**, nunca asumirlo solo por estar en esa posición
  (lección de S031, sigue vigente).
- **Antes de añadir un composable nuevo, comprobar sus imports reales
  del archivo, no darlos por hechos** (lección de S030, sigue
  vigente).
- **Patrón muy repetido, todas las sesiones recientes**: la inmensa
  mayoría de los bugs reales se encontraron SOLO al probar el arreglo
  anterior con datos/logs/capturas reales de Miguel Ángel -- nunca
  darlos por buenos sin esa confirmación, por razonable que parezca el
  arreglo sobre el papel.
- **`PopurriDebugLogger`** (`popurri_favoritos_debug.txt`) -- pedir
  este archivo específico ante cualquier fallo futuro de popurrís de
  Favoritos.
- **El zip de logs de GitHub Actions sigue sin ser accesible por red**
  desde el entorno del modelo -- usar la API de anotaciones del check
  (`/check-runs/{job_id}/annotations`); si el fallo no deja anotación
  legible, hacer que el propio script/workflow escriba su diagnóstico
  a un archivo y lo commitee siempre (`if: always()`), patrón usado
  varias veces con éxito en S035.
- **`isFromRadio = true` es obligatorio en CUALQUIER `QueueItem` que
  añada un motor de reproducción automática** -- sin él,
  `onMediaItemTransition` resetea el ancla de la sesión en marcha.
- **MusicBrainz está bloqueado por robots.txt para el modelo en este
  entorno de trabajo** -- cualquier verificación en vivo tiene que
  hacerse con logs reales del dispositivo.

# PUNTO DE REANUDACIÓN — MiMoo

**Última sesión cerrada:** S036 (2026-09-08/14)
**Hito con la hoja de ruta activa:** ver `DOCS/ANNEX_ROUTER.md`

> El estado de los hitos vive **exclusivamente** en
> `DOCS/ANNEX_ROUTER.md`. Este archivo no lo declara ni lo duplica.

---

## Qué hacer en la siguiente sesión — EMPEZAR POR AQUÍ

Miguel Ángel dio tres peticiones explícitas al cerrar S036, palabras
suyas, sin diagnosticar ni diseñar todavía en esta sesión. Ninguna
encaja limpiamente en un hito existente (mismo patrón que motivó abrir
H16/H17/H18 en su día) -- sesión de diseño de alcance antes de tocar
código, empezando por confirmar con Miguel Ángel a qué hito(s) asignar
cada una o si hace falta abrir uno nuevo.

1. **Flujos de trabajo de las skills, equiparados con EnterpriseBot.**
   *"Revisar los flujos de trabajo de las skills para equipararlos con
   los flujos de trabajo de EnterpriseBot."* Tarea de proceso/tooling,
   no de código de la app -- probablemente corresponde a
   `/areas/skill-library.md` más que a un hito de MiMoo. Sin alcance
   cerrado: empezar comparando explícitamente qué tiene EnterpriseBot
   (`com-mysql`, `com-actions-relay`, `com-bash-commands`, etc.) que
   NewFlow Android no tenga todavía, y confirmar con Miguel Ángel cuál
   de esas piezas aplica aquí.
2. **Editar listas de reproducción -- buscar duplicados, borrar a
   discreción, mover entre listas, selección por casilleros.** *"En la
   vista de las listas permitir editar las listas para buscar
   duplicados con un filtro y poder eliminar pistas a discreción,
   poder añadir temas de una lista a otra lista, y todo con selección
   por casilleros."* Encaja en H04 (Listas de Reproducción Locales),
   pero es una ampliación de alcance real, no un bug -- diseño primero
   (qué pantalla, cómo se activa la selección múltiple, qué cuenta
   como "duplicado" -- mismo `youtubeId` o mismo título+artista).
3. **ExoPlayer -- quitar de la cola en ejecución + mostrar qué suena.**
   *"En el exoplayer añadir en su grupo correspondiente un botón para
   quitar de la lista que se está ejecutando el tema correspondiente,
   el nombre de la lista que se está ejecutando debe aparecer en algún
   sitio para que en todo momento se sepa que está sonando, a que
   lista o álbum o si es un sencillo."* No encaja en H18 (ese hito es
   sobre botones de play en filas de item y orden alfabético/de
   adición, no sobre la cola en reproducción) -- probablemente hito
   nuevo o ampliación de H04/H13, a confirmar con Miguel Ángel. Nota
   técnica de partida: `PlayerManager` ya tiene `currentQueueIsPlaylist`
   (S036, ver más abajo) pero NO guarda today el nombre/id de la
   lista/álbum de origen de la cola actual -- hace falta añadir ese
   dato para poder mostrarlo.

**H12 (Directorio de Música + Favoritos sin Descarga) EN PROGRESO** --
sin tocar en S036 (sesión larguísima y transversal sobre otros hitos,
ver más abajo). Sigue exactamente como quedó en S035: sin diagnóstico
nuevo pendiente, solo verificación en dispositivo real de los tres
puntos cerrados en S034. Hoja de ruta ejecutable completa en
`DOCS/ANNEX_H12.md`, sección "Hoja de Ruta para la Siguiente Sesión
que retome H12".

## S037 -- las tres peticiones de arriba, cerradas

Las tres peticiones de "EMPEZAR POR AQUÍ" se diseñaron y construyeron
en S037, todas con build verde: comparativa de skills (aplicadas las
seis, ver `com-actions-relay` §7 para el nuevo relevo dispositivo →
Drive), edición de listas (`DOCS/ANNEX_H04.md`) y quitar de la cola +
origen de lo que suena (`DOCS/ANNEX_H13.md`). Todas pendientes de
verificación en dispositivo real, igual que lo que ya estaba pendiente
de H12 desde S034.

De paso, en S037 se corrigió también un bug real de compartir listas
(las pistas en streaming desaparecían de la lista compartida) y se
añadió el botón "Descargar lista entera" -- ver `DOCS/ANNEX_H10.md` y
`DOCS/ANNEX_H04.md`.

**Botón "Subir logs a Drive" (H10/relevo) verificado en uso real**: en
la misma sesión S037, Miguel Ángel lo pulsó y el modelo leyó los 8
archivos desde "MiMoo - Intercambio Claude" sin problema -- mecanismo
confirmado, misma cuenta de Google en el móvil y en el conector del
chat.

**Crash real encontrado y corregido a partir de esos logs**
(`crash_log.txt`): `ForegroundServiceStartNotAllowedException` al
colgar una llamada con MiMoo en segundo plano --
`handleTelephonyCallStateChanged()` reanuda con `player.play()`, y el
propio `onIsPlayingChanged()` intentaba re-promocionar el servicio a
primer plano; Android 12+ lo rechaza sin actividad visible en ese
instante y tiraba abajo toda la app. Corregido con try/catch (mismo
criterio que `onPhoneStatePermissionGranted()` de la misma clase).
Commit `6cd186b`, build verde. **Pendiente de confirmar** que no
vuelve a ocurrir con una llamada real y la app en segundo plano.

## Sesión larguísima y transversal en S036 (H07/H02/H04/H08 + varios)

Mismo criterio que S033/S034/S035: incidencias reales sobre código de
otros hitos, sin PCH -- H12 fue el hito EN PROGRESO durante toda la
sesión, sin tocarse. Arrancó investigando una pérdida real de ~1300
temas (archivos borrados de verdad de la tarjeta SD) y evolucionó en
un rediseño completo de la sincronización automática con Drive, varios
bugs reales de descarga/cola/Radio, y una corrección de diseño
explícita de Miguel Ángel a mitad de sesión ("nunca debemos machacar
la copia de Drive sin permiso, ni siquiera al añadir, eso es un error
mío de diseño"). Detalle técnico completo en cada anexo -- resumen muy
breve aquí:

- **H07 (sincronización)**: causa real de la pérdida de datos
  encontrada en `LibraryReconciler.pruneEmptyFolders()` (borraba
  carpetas por un falso positivo del proveedor SAF, sin ningún límite
  de seguridad) y corregida. Rediseño completo de cuándo preguntar
  antes de tocar Drive -- de "la nube manda sin preguntar en según qué
  casos" (diseño original de H07) a "SIEMPRE pregunta ante cualquier
  diferencia, suba o baje" (corrección de diseño de Miguel Ángel en
  esta misma sesión). Nuevo diagnóstico manual de descargas sin
  archivo real en Ajustes. Bug real corregido en
  `ImportLinkViewModel` que perdía el estado de descarga al
  reimportar un enlace. Ver `DOCS/ANNEX_H07.md`, "COMPLETADAS EN
  S036".
- **H02 (descargas)**: límite de 3 descargas simultáneas reales,
  botón de pausa (con un bug real de fondo corregido en un segundo
  pase -- la primera comprobación de pausa no bastaba), y estados
  `DOWNLOADING` colgados de sesiones interrumpidas corregidos. Ver
  `DOCS/ANNEX_H02.md`.
- **H04 (listas)**: crash real de clave duplicada al desplazar el
  detalle de una lista, simplificación del aviso de tema duplicado (ya
  no se puede forzar el añadido, solo avisa), y bug real donde la cola
  de una lista grande se quedaba mostrando un solo tema durante mucho
  tiempo (se resolvía toda la lista antes de añadir nada a la cola en
  vez de ir añadiendo poco a poco). Ver `DOCS/ANNEX_H04.md`.
- **H08 (Radio/búsqueda)**: la Radio queda anulada por completo al
  reproducir una lista de reproducción (decisión final de Miguel
  Ángel, tras encontrar un resquicio real en el bloqueo de aleatorio
  de S050), y un bug real de fondo (`lastFailureWasTransient` sin
  resetear) que dejaba la Radio parada para siempre al toparse con un
  artista placeholder ("Various Artists"). Crash real de playlist
  duplicada en resultados de búsqueda, corregido deduplicando por id.
  Ver `DOCS/ANNEX_H08.md`, "COMPLETADAS EN S036".
- **Reproducción -- fuera de cualquier hito concreto**: `resume()` no
  reanudaba tras un error real de red (streaming cortado por falta de
  cobertura) por faltar `prepare()` antes de `play()` -- corregido.

**Todo lo de esta sesión se corrigió con evidencia real de Miguel
Ángel** (capturas de pantalla, logs de depuración, descripciones
exactas del síntoma) -- varios puntos fueron confirmados por él
durante la propia sesión (el límite de concurrencia, la reafirmación
de QUEUED), otros quedan pendientes de que los pruebe todavía (la
segunda pasada de la pausa, el bloqueo total de Radio en listas, el
fix de `resume()`, el fix de reimportar enlaces).

## Trabajo pendiente de sesiones anteriores, sin tocar en S036

1. **El volumen se baja solo** (S035). *"El volumen de audio... se
   baja solo y hay que estar subiendo el volumen cada vez que otra
   aplicación toca el volumen."* El `AudioFocusRequest` que causaba
   pausas ya se quitó en S035, pero este síntoma ("ducking") es
   distinto y sigue sin diagnosticar.
2. **Auditoría pendiente de la semilla de 1.161 artistas**
   (`anchor_artists.json`) -- sigue sin tocar, no se puede verificar
   contra MusicBrainz en vivo desde este entorno de trabajo.
3. **Reproducción de favoritos (artistas/álbumes) se muere esperando
   el primer tema** (S034) -- sin diagnosticar todavía.
4. **Bug sin localizar**: el `.txt` de log compartido desde el móvil
   llegaba a veces con contenido viejo (S027). Mitigado indirectamente
   bajando `MAX_LINES`, causa real sin diagnosticar.
5. **H08, dos hallazgos de S028 sin confirmar con log real**: umbral
   de coincidencia al 40% (Loquillo y Los Trogloditas), y Émilie Simon
   -- sin reprobar desde entonces.

## Incidencias de proceso a tener en cuenta

- **S036, nueva -- la numeración de sesión en los anexos (`S0XX`) y la
  numeración usada dentro de los comentarios de código en los commits
  reales son dos series DISTINTAS** que llevan tiempo desincronizadas
  -- los anexos iban por S035 mientras el código ya llevaba comentarios
  hasta S088. Antes de escribir "COMPLETADAS EN S0XX" en cualquier
  anexo, buscar el número más alto ya usado en TODOS los anexos
  (`grep -rohP "S0\d\d" DOCS/*.md | sort -t 'S' -k2 -n -u | tail`), no
  asumir que es "el último + 1" sin comprobarlo -- esta misma sesión
  estuvo a punto de reutilizar S035, ya ocupado.
- **S036, nueva**: el token de GitHub de sesión puede caducar A MITAD
  de una sesión larga (pasó dos veces en S036) -- si un `push` falla
  con "could not read Username" o similar, comprobar el código HTTP
  real contra la API (`curl -o /dev/null -w "%{http_code}"`) antes de
  suponer un fallo de red puntual; un 401 significa token caducado o
  revocado, pedir uno nuevo a Miguel Ángel sin insistir con el mismo.
- **S036, nueva**: el entorno de trabajo (contenedor) puede reiniciarse
  entre turnos de una misma conversación larga, perdiendo el clon
  local -- si un comando falla con "can't cd to .../repo/MiMoo", volver
  a clonar antes de asumir cualquier otro fallo.
- **S036, nueva**: al comprobar el balance de llaves de un archivo tras
  una edición, un comentario que MENCIONA una llave como texto (p.ej.
  "cierra el bloque `foo {`") cuenta como una llave suelta para un
  recuento ingenuo (`s.count('{')`) aunque el código en sí esté bien --
  si el recuento no cuadra tras una edición pequeña, revisar primero si
  el desajuste está en un comentario, no asumir un fallo estructural
  real sin mirar.
- **`isFromRadio = true` es obligatorio en CUALQUIER `QueueItem` que
  añada un motor de reproducción automática** -- sin él,
  `onMediaItemTransition` resetea el ancla de la sesión en marcha. A
  partir de S036, además, ese flag por sí solo puede saltarse el
  bloqueo de aleatorio de Radio -- ver `currentQueueIsPlaylist` en
  `DOCS/ANNEX_H08.md` para el reemplazo correcto cuando se necesite un
  bloqueo total e incondicional.
- **Cuando dos mecanismos comparten una misma bandera de estado**,
  cualquier cambio en UNO de los dos puede dejar la bandera en un
  estado que el OTRO no espera (lección de S035, confirmada otra vez
  en S036 con `lastFailureWasTransient`) -- antes de tocar un valor de
  ese tipo, buscar TODOS los puntos que lo leen/escriben, no solo los
  que se están tocando a propósito.
- **GitHub Actions no es viable para tareas que necesiten "parecer un
  usuario real" ante servicios como YouTube** -- las IPs de centro de
  datos se bloquean con verificación anti-bot. La alternativa real y ya
  probada es generar en el propio dispositivo de Miguel Ángel.
- **Nunca poner `--` dentro de un comentario XML** -- sigue rompiendo
  el build cuando se olvida. Usar siempre `—` (guion largo).
- **Al construir un mensaje de commit con heredoc en varios pasos,
  verificar SIEMPRE el mensaje final con `git log -1 --format="%B"`**
  antes de darlo por bueno.
- **La inmensa mayoría de los bugs reales se encontraron SOLO al
  probar el arreglo anterior con datos/logs/capturas reales de Miguel
  Ángel** -- nunca darlos por buenos sin esa confirmación, por
  razonable que parezca el arreglo sobre el papel. Patrón repetido
  varias veces en S036 (el fix de la pausa, el "177 descargando").
- **`PopurriDebugLogger`** (`popurri_favoritos_debug.txt`) -- pedir
  este archivo específico ante cualquier fallo futuro de popurrís de
  Favoritos.
- **El zip de logs de GitHub Actions sigue sin ser accesible por red**
  desde el entorno del modelo -- usar la API de anotaciones del check
  (`/check-runs/{job_id}/annotations`).
- **MusicBrainz está bloqueado por robots.txt para el modelo en este
  entorno de trabajo** -- cualquier verificación en vivo tiene que
  hacerse con logs reales del dispositivo.

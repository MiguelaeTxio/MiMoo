# PUNTO DE REANUDACIÓN — MiMoo

**Última sesión cerrada:** S030 (2026-08-06)
**Hito con la hoja de ruta activa:** ver `DOCS/ANNEX_ROUTER.md`

> El estado de los hitos vive **exclusivamente** en
> `DOCS/ANNEX_ROUTER.md`. Este archivo no lo declara ni lo duplica.

---

## Qué hacer en la siguiente sesión

**H17 (Karaoke & Lyrics) EN PROGRESO** -- hito nuevo, sin diseño
cerrado todavía. Antes de escribir una sola línea de código: cerrar
con Miguel Ángel los seis puntos de diseño ABIERTOS de
`DOCS/ANNEX_H17.md` -- empezando por el punto 1 (fuente de las
letras), que condiciona la viabilidad del punto 2 (¿karaoke
sincronizado línea a línea, o solo letra estática?). Mismo patrón de
sesión de diseño primero ya usado en H08/H12/H15 -- no dar nada por
supuesto ni inventar la fuente de letras sin decidirla explícitamente.

**Petición explícita de Miguel Ángel, cita textual (2026-08-06):**
*"vamos a pasar a crear un hito nuevo, Karaoke & Lyrics: consistirá en
añadir una entrada en la sidebar para buscar y leer letras de
canciones y una entrada en el menú de tres puntos del ExoPlayer que
abrirá una ventana encima o debajo del mismo donde visualizar el
karaoke del tema que se está ejecutando si se dispone de letras."*

## H15 (miMooutCast) -- PAUSADO, completo en código, en verificación de dispositivo

Los diez puntos de "COMPLETADAS EN S030" en `DOCS/ANNEX_H15.md` están
hechos y en build verde, incluido un bug real de pérdida de ancla a
mitad de sesión encontrado y corregido tras prueba en dispositivo real
(Soundgarden colándose en una sesión de "minimal techno" -- causa:
`QueueItem` sin `isFromRadio = true`). Miguel Ángel lo estaba probando
al cierre de esta sesión, "parece que va bien" tras el arreglo. **No
hay código pendiente** -- si al seguir probando aparece algo nuevo, es
una incidencia que retoma H15 puntualmente (PCH), no algo que quede
"a medias" de esta sesión.

## H16 (Lista Negra) -- PAUSADO, cinco puntos de código completos, sin fallos conocidos

Confirmado por Miguel Ángel al empezar S030: "el CRUD de Lista Negra
está activo y funcionando y no sé de que falle de momento". Los cinco
puntos de `DOCS/ANNEX_H16.md` siguen sin verificación exhaustiva en
dispositivo de TODOS los casos (exclusión mutua con Favoritos en los
dos sentidos, filtro en Popurrí, drawer compacto) pero nada reportado
como roto.

## Incidencia de proceso de S029->S030, ya corregida -- para no repetirla

El cierre de S029 dejó a H16 como el hito EN PROGRESO pese a que
Miguel Ángel había pedido explícitamente al cierre de S028 que la
siguiente sesión empezara por H15 (miMooutCast) -- el cierre no dejó
constancia clara de que ese compromiso seguía vivo, lo que causó
confusión real a Miguel Ángel al empezar S030 (ver
`DOCS/ANNEX_ROUTER.md`, entrada "2026-08-06 (S030)"). **Regla para
sesiones futuras**, ya aplicada en el PCH de H15->H17 de esta misma
sesión: si una sesión deriva hacia un hito nuevo a mitad de camino, o
si se pausa un hito antes de agotar su hoja de ruta, el cierre debe
decir EXPLÍCITAMENTE en este archivo qué pasa con el compromiso
anterior -- no basta con que el nuevo hito EN PROGRESO quede bien
descrito, tiene que quedar igual de explícito qué se pospone y por
qué.

## Trabajo pendiente de otras sesiones, sin tocar en S030

Sigue exactamente igual que al cierre de S029:

1. **Auditoría pendiente de la semilla de 1.161 artistas**
   (`anchor_artists.json`) -- sigue sin tocar, no se puede verificar
   contra MusicBrainz en vivo desde este entorno de trabajo.
2. **Fallo de streaming ajeno a la Radio**: `notification_debug.txt`
   mostraba 50 errores `ERROR_CODE_IO_BAD_HTTP_STATUS`/
   `NETWORK_CONNECTION_FAILED` repartidos en muchas sesiones. Se
   corrigió un caso concreto en S027, sin confirmar si cubre el resto.
3. **Bug sin localizar**: el `.txt` de log compartido desde el móvil
   llegaba a veces con contenido viejo. Mitigado indirectamente
   bajando `MAX_LINES`, causa real sin diagnosticar.

## Dos hallazgos de Radio en S028, reportados pero sin confirmar con log real

- **Umbral de coincidencia al 40%** (Ajustes > Base de datos de la
  Radio): Miguel Ángel describió que con Loquillo y Los Trogloditas,
  al 40% "se quedó parada totalmente sin encontrar tema" -- pero solo
  se pudo confirmar el mecanismo en el código (devuelve "eslabón roto"
  limpiamente, no debería colgarse literalmente) sin un log real a
  40% que confirme el síntoma exacto que vio. Si vuelve a pasar, pedir
  log con el umbral puesto a 40%.
- **Émilie Simon "falló estrepitosamente"**: en el log más antiguo
  compartido, el ancla de Émilie Simon acumulaba muchos "SIN RED" --
  debería estar resuelto por el arreglo de la sonda de conectividad
  real (`e96f2d2`), pero no se ha vuelto a probar específicamente con
  ese artista tras el arreglo.

## Incidencias de proceso a tener en cuenta

- **Nunca poner `--` dentro de un comentario XML.** Rompió el build
  una vez en S028 (`AndroidManifest.xml`, permiso `WRITE_CONTACTS`) --
  la especificación XML prohíbe esa secuencia en cualquier punto de un
  comentario, no solo al final. El resto de comentarios del proyecto
  ya usaba `—` (guion largo) por este motivo; se copió sin querer el
  estilo `--` habitual de los comentarios Kotlin a un archivo XML.
  Corregido en el commit siguiente (`a1229df`).
- **Antes de añadir un composable nuevo, comprobar sus imports reales,
  no darlos por hechos.** En S030, `MainActivity.kt` nunca había usado
  `Row`/`fillMaxWidth`/`size`/`width`/`@Composable` a nivel de archivo
  (solo `Column`/`Box`), así que un `CompactDrawerItem` nuevo con esos
  símbolos rompió el build por imports que faltaban -- corregido en el
  commit siguiente, diagnosticado con la API de anotaciones de GitHub
  Actions. El resto de archivos grandes del proyecto (`PlayerBar.kt`,
  `PlayerManager.kt`) sí los tenían todos, así que este bug fue
  específico de un archivo que hasta ahora solo había usado `Column`.
- **Patrón muy repetido en S027-S028, en línea con lo ya visto**: la
  inmensa mayoría de los bugs reales de esas sesiones (Favoritos y
  Radio) se encontraron SOLO al probar el arreglo anterior en
  dispositivo real -- nunca se dieron por buenos sin log/captura de
  Miguel Ángel confirmándolo. Seguir pidiendo pruebas hasta
  confirmación real, no dar nada por cerrado con el primer arreglo que
  "debería" funcionar -- mismo criterio aplica a H16 cuando se retome
  para su verificación en dispositivo.
- **`PopurriDebugLogger`** (`popurri_favoritos_debug.txt`, mismo
  patrón que `RadioDebugLogger`) -- pedir este archivo específico ante
  cualquier fallo futuro de popurrís de Favoritos.
- **El zip de logs de GitHub Actions sigue sin ser accesible por red**
  desde el entorno de trabajo del modelo (redirige a un host de blob
  storage fuera de la lista permitida). El paso del workflow que
  publica los errores de compilación como anotaciones del check
  (legible por API) sigue siendo el método fiable -- usado en S030
  para diagnosticar y confirmar en verde los dos builds rotos por
  imports del drawer compacto.
- **`str_replace` con `old_str`/`new_str` casi idénticos (una edición
  "sin cambio real de contenido", solo para forzar un chequeo) puede
  fusionar dos líneas sin salto de línea entre ellas** si el
  `old_str`/`new_str` no incluye el `\n` final de la línea -- pasó
  DOS VECES en S030 (import de `RadioRepository` en
  `MimooutcastViewModel.kt`, import de `ExplorerScreen` en
  `NavGraph.kt`), ambas rompieron el build. Si una edición no cambia
  contenido real, mejor no hacerla -- y si hace falta, revisar el
  resultado con `view` antes de dar la edición por buena, no fiarse de
  que "no cambia nada" sea inofensivo.
- **`isFromRadio = true` es obligatorio en CUALQUIER `QueueItem` que
  añada un motor de reproducción automática** (Radio, miMooutCast, lo
  que venga después) -- sin él, `onMediaItemTransition` lo confunde
  con una pista elegida a mano por el usuario y resetea el ancla de la
  sesión en marcha. Bug real en H15/S030 (Soundgarden en una sesión de
  "minimal techno"), con el síntoma apareciendo solo tras minutos de
  reproducción real -- no algo que un build verde detecte nunca.

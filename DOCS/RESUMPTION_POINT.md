# PUNTO DE REANUDACIÓN — MiMoo

**Última sesión cerrada:** S031 (2026-08-06/07)
**Hito con la hoja de ruta activa:** ver `DOCS/ANNEX_ROUTER.md`

> El estado de los hitos vive **exclusivamente** en
> `DOCS/ANNEX_ROUTER.md`. Este archivo no lo declara ni lo duplica.

---

## Qué hacer en la siguiente sesión

**H18 (Play y Ordenación de Listas de Items) EN PROGRESO** -- diseño
cerrado y los tres primeros bloques construidos en S032 (migración +
propagación a backup/sync, botones de play/aleatorio individuales en
Favoritos, control de ordenación en las cuatro pestañas), en build
verde (`041e151`, `95d2bdc`, `299e751`). **Sin verificar en
dispositivo real todavía.** Quedan dos bloques de la hoja de ruta:
extender la ordenación a `PlaylistsScreen.kt`/Canales/Lista Negra
(mismas entidades, ya tienen timestamp, sin migración), y decidir
sobre Explorador (H12) leyendo su código real antes de asumir si
aplica el orden de adición. Detalle completo en `DOCS/ANNEX_H18.md`,
"COMPLETADAS EN S032".

## H17 (Karaoke & Lyrics) -- PAUSADO, diseño y construcción completos, sin verificar en dispositivo

Pasó a PAUSADO al arranque de S032 (PCH explícito, sin incidencia
nueva). Sin código pendiente -- el único punto de su hoja de ruta
(intacta) es verificación en dispositivo real, con foco especial en
los tres temas que en `letras_debug.txt` fallaban con 404 (Willie
Nelson "Always On My Mind", King Crimson "The Court Of The Crimson
King", Thin Lizzy "The Boys Are Back In Town") antes del fix de
`cleanSongTitle()`. Detalle completo en `DOCS/ANNEX_H17.md`,
"COMPLETADAS EN S031".

## H15 (miMooutCast) -- PAUSADO, completo en código, con un fix real más en S031

Sigue completo y en build verde (ver "COMPLETADAS EN S030" en
`DOCS/ANNEX_H15.md`). En S031, revisando `mimooutcast_debug.txt`
compartido junto con el de H17, se encontró y corrigió un fix real
adicional: falso positivo de artista en
`RadioRepository.stripTitleNoise()` (caso real: sesión de "Minimal
Techno" resolvió como candidato "Teste" un vídeo que en realidad es de
otro artista, "MUUD"). Causa: el segmento antes del primer " - " del
título del vídeo se quitaba siempre sin comprobar que coincidiera de
verdad con el artista buscado. Arreglado -- ver "COMPLETADAS EN S031"
en `DOCS/ANNEX_H15.md`, punto 11. Build verde (`9328658`), **sin
verificar en dispositivo real todavía** (H15 sigue pausado). Comparte
código con H08 (Radio) -- el fix corrige la verificación en ambos.

## H16 (Lista Negra) -- PAUSADO, cinco puntos de código completos, sin fallos conocidos

Sin cambios en S031. Los cinco puntos de `DOCS/ANNEX_H16.md` siguen
sin verificación exhaustiva en dispositivo de TODOS los casos
(exclusión mutua con Favoritos en los dos sentidos, filtro en
Popurrí, drawer compacto) pero nada reportado como roto.

## Trabajo pendiente de otras sesiones, sin tocar en S031

Sigue exactamente igual que al cierre de S030:

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

- **Limpieza de título que quita un segmento por posición (prefijo/
  sufijo antes o después de un separador) debe comprobar SIEMPRE que
  ese segmento coincide de verdad con lo que se cree que es (el
  artista buscado), nunca asumirlo solo por estar en esa posición.**
  Dos bugs reales de la misma familia encontrados y corregidos en
  S031: `PlayerBarViewModel` consultaba lrclib.net con el título crudo
  del vídeo sin limpiar (arreglado con
  `SearchNormalizer.cleanSongTitle()`, que sí hace esta comprobación),
  y `RadioRepository.stripTitleNoise()` quitaba el prefijo antes del
  primer " - " sin comprobar que fuera el artista que se estaba
  verificando (caso real: "MUUD - Testē" al buscar el artista "Teste"
  -- confirmó un tema de un artista completamente distinto). Ambos
  arreglados con el mismo criterio: comparar el segmento candidato
  contra el artista normalizado antes de darlo por bueno.
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
- **Patrón muy repetido en S027-S031**: la inmensa mayoría de los
  bugs reales de estas sesiones (Favoritos, Radio, y ahora Karaoke &
  Lyrics) se encontraron SOLO al probar el arreglo anterior en
  dispositivo real -- nunca se dieron por buenos sin log/captura de
  Miguel Ángel confirmándolo. Seguir pidiendo pruebas hasta
  confirmación real, no dar nada por cerrado con el primer arreglo que
  "debería" funcionar.
- **`PopurriDebugLogger`** (`popurri_favoritos_debug.txt`, mismo
  patrón que `RadioDebugLogger`) -- pedir este archivo específico ante
  cualquier fallo futuro de popurrís de Favoritos.
- **El zip de logs de GitHub Actions sigue sin ser accesible por red**
  desde el entorno de trabajo del modelo (redirige a un host de blob
  storage fuera de la lista permitida). El paso del workflow que
  publica los errores de compilación como anotaciones del check
  (legible por API) sigue siendo el método fiable.
- **`isFromRadio = true` es obligatorio en CUALQUIER `QueueItem` que
  añada un motor de reproducción automática** (Radio, miMooutCast, lo
  que venga después) -- sin él, `onMediaItemTransition` lo confunde
  con una pista elegida a mano por el usuario y resetea el ancla de la
  sesión en marcha. Bug real en H15/S030 (Soundgarden en una sesión de
  "minimal techno"), con el síntoma apareciendo solo tras minutos de
  reproducción real -- no algo que un build verde detecte nunca.

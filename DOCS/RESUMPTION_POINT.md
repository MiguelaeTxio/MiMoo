# PUNTO DE REANUDACIÓN — MiMoo

**Última sesión cerrada:** S030 (2026-08-06)
**Hito con la hoja de ruta activa:** ver `DOCS/ANNEX_ROUTER.md`

> El estado de los hitos vive **exclusivamente** en
> `DOCS/ANNEX_ROUTER.md`. Este archivo no lo declara ni lo duplica.

---

## Qué hacer en la siguiente sesión

**H15 (miMooutCast) EN PROGRESO** -- hoja de ruta técnica completa en
`DOCS/ANNEX_H15.md`, sección "Hoja de Ruta para la Siguiente Sesión
que retome H15". Queda un único punto de alcance por cerrar CON
Miguel Ángel antes de escribir código: el rango de décadas de la lista
fija (límite inferior/superior, p.ej. 1950-2020). Una vez cerrado ese
punto, la hoja de ruta ya tiene los cuatro pasos siguientes detallados
(función de ancla manual en `RadioRepository`, catálogos fijos de
géneros/décadas, pantalla nueva de tres secciones con cristal
esmerilado, entrada en la navegación).

**Petición explícita de Miguel Ángel, cita textual (2026-08-06):**
*"vamos a preparar un hito nuevo para la siguiente sesión, nuestro
propio ShoutCast: miMooutCast, en esta vista vamos a usar la radio que
hemos montado eligiendo el ancla a la carta: con dos secciones,
géneros y décadas -- ahora pondremos música eligiendo entre los
géneros o entre las décadas que existen en MusicBrainz... Tenemos que
cuidar la UX con las chapitas de cristal esmerilado tal como ya está
funcionando en la app y que sea muy intuitivo."*

## H16 (Lista Negra) -- PAUSADO, cinco puntos de código completos

Los cinco puntos de su hoja de ruta están construidos y en build
verde -- CRUD, filtro en las tres cascadas de Radio, filtro en
Popurrí, botón+diálogo del ExoPlayer, acción en el Explorador. Queda
un único punto pendiente al retomarlo: **verificación en dispositivo
real** (CRUD, Radio, Popurrí, botón del ExoPlayer, acción del
Explorador, exclusión mutua con Favoritos en los dos sentidos, y el
drawer compacto). Ver `DOCS/ANNEX_H16.md` sección "COMPLETADAS EN
S030".

## Incidencia de proceso de esta sesión, a tener en cuenta

El cierre de S029 dejó a H16 como el hito EN PROGRESO pese a que
Miguel Ángel había pedido explícitamente al cierre de S028 que la
siguiente sesión empezara por H15 (miMooutCast) -- la sesión de S029
derivó hacia H16 sobre la marcha (petición nueva, legítima en su
momento) pero el cierre no dejó constancia clara de que H15 seguía
pendiente de arrancar tal como se había prometido, lo que causó
confusión real a Miguel Ángel al empezar S030. Corregido en el PCH de
esta misma sesión (ver `DOCS/ANNEX_ROUTER.md`, entrada "2026-08-06
(S030)"). Para sesiones futuras: si una sesión deriva hacia un hito
nuevo a mitad de camino, el cierre debe dejar explícito en
`RESUMPTION_POINT.md` qué pasa con el compromiso anterior (se
mantiene, se pospone, o se cancela), no solo el estado del hito nuevo.

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

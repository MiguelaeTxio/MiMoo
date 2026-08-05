# PUNTO DE REANUDACIÓN — MiMoo

**Última sesión cerrada:** S029 (2026-08-05)
**Hito con la hoja de ruta activa:** ver `DOCS/ANNEX_ROUTER.md`

> El estado de los hitos vive **exclusivamente** en
> `DOCS/ANNEX_ROUTER.md`. Este archivo no lo declara ni lo duplica.

---

## Qué hacer en la siguiente sesión

**H16 (Lista Negra / "No me gusta")** EN PROGRESO -- hoja de ruta
técnica completa en `DOCS/ANNEX_H16.md`, sección "Hoja de Ruta para la
Siguiente Sesión que retome H16". El diseño ya está cerrado entero
(botón en mini-barra y expandido, exclusión mutua con Favoritos, salto
inmediato de pista, CRUD solo ver/borrar) y la capa de datos está
construida y verificada en build verde (commit `31ad6b0`).

**Petición explícita de Miguel Ángel al cierre de S029: empezar por el
CRUD**, antes que por el filtro de Radio/Popurrí o el botón del
reproductor -- orden distinto al que tenía el anexo al abrirse. Ver
`DOCS/ANNEX_H16.md` para el detalle punto por punto.

**Aviso de proceso:** el siguiente paso tras el CRUD (inyectar el
filtro en `RadioRepository.kt`) toca un archivo de 2.314 líneas con
lógica de cascada sensible y varios bugs reales de fondo en sesiones
anteriores (S020-S028) -- leerlo entero antes de tocarlo, directriz
4.1 sin excepción.

## Trabajo pendiente de otras sesiones, sin tocar en S029

Sigue exactamente igual que al cierre de S028 -- nada de esto se tocó
en S029 (todo el trabajo real fue sobre H15/H16):

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

## H15 (miMooutCast) -- PAUSADO, punto de alcance sin cerrar

Al retomar H15: solo queda el rango de décadas de la lista fija por
cerrar con Miguel Ángel (el resto del alcance -- origen/país, la
interacción entre secciones, el catálogo de géneros -- se cerró en
S029, ver `DOCS/ANNEX_H15.md` sección "COMPLETADAS EN S029"). Sin
código todavía.

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
- **Patrón muy repetido en S028, en línea con lo ya visto en S027**:
  la inmensa mayoría de los bugs reales de esa sesión (Favoritos y
  Radio) se encontraron SOLO al probar el arreglo anterior en
  dispositivo real -- nunca se dieron por buenos sin log/captura de
  Miguel Ángel confirmándolo. Seguir pidiendo pruebas hasta
  confirmación real, no dar nada por cerrado con el primer arreglo que
  "debería" funcionar -- mismo criterio aplica a H16 cuando llegue a
  UI/dispositivo.
- **`PopurriDebugLogger`** (`popurri_favoritos_debug.txt`, mismo
  patrón que `RadioDebugLogger`) -- pedir este archivo específico ante
  cualquier fallo futuro de popurrís de Favoritos (también relevante
  para H16, ya que el filtro de "no me gusta" tiene que aplicar
  también dentro del popurrí).
- **El zip de logs de GitHub Actions sigue sin ser accesible por red**
  desde el entorno de trabajo del modelo (redirige a un host de blob
  storage fuera de la lista permitida). El paso del workflow que
  publica los errores de compilación como anotaciones del check
  (legible por API) sigue siendo el método fiable -- usado en S029
  para confirmar build verde de H16 sin pedir el log a Miguel Ángel.

# PUNTO DE REANUDACIÓN — MiMoo

**Última sesión cerrada:** S028 (2026-08-04)
**Hito con la hoja de ruta activa:** ver `DOCS/ANNEX_ROUTER.md`

> El estado de los hitos vive **exclusivamente** en
> `DOCS/ANNEX_ROUTER.md`. Este archivo no lo declara ni lo duplica.

---

## Qué hacer en la siguiente sesión

**H15 (miMooutCast)** es el hito nuevo abierto al cierre de S028 —
hoja de ruta técnica completa en `DOCS/ANNEX_H15.md`. Resumen: una
pantalla nueva que deja elegir el ancla de la Radio A MANO (género y/o
década de MusicBrainz, dos secciones), en vez de que se derive siempre
de una pista que ya está sonando — reutilizando el motor de Radio de
H08 tal cual, sin tocarlo.

**El alcance está deliberadamente abierto en dos puntos** — la sesión
que retome H15 debe cerrarlos con Miguel Ángel antes de escribir
código, mismo patrón que se usó para abrir H08 (S013) y H12 (S017):

1. ¿Se pide también origen/país al elegir el ancla, o se deja
   "cualquiera" por defecto? Miguel Ángel solo mencionó dos secciones
   (géneros, décadas).
2. ¿Cómo interactúan las dos secciones entre sí? (¿se elige género Y
   década antes de arrancar, o cada sección arranca sola con la otra
   dimensión libre?)

Después: de dónde sale el catálogo real de géneros de MusicBrainz que
se le enseña al usuario (revisar qué hay ya construido en H08/H09
antes de duplicar nada), construir la función de ancla manual en
`RadioRepository`, la pantalla (cristal esmerilado, intuitiva) y su
entrada en la navegación.

## Trabajo pendiente de otras sesiones, sin tocar en S028

Sigue exactamente igual que al cierre de S027 -- ninguno de estos
puntos se tocó en S028, todo el trabajo real fue sobre Favoritos y
Radio (ver `DOCS/ANNEX_H08.md`, sección "COMPLETADAS EN S028"):

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
- **Patrón muy repetido en S028, en línea con lo ya visto en S027**:
  la inmensa mayoría de los bugs reales de esta sesión (Favoritos y
  Radio) se encontraron SOLO al probar el arreglo anterior en
  dispositivo real -- nunca se dieron por buenos sin log/captura de
  Miguel Ángel confirmándolo. Varias cadenas de 3-6 arreglos seguidos
  sobre el mismo síntoma aparente (p.ej. sincronización de favoritos:
  bundle -> crash de Gson -> push nunca disparado; ShoutCast: `play()`
  -> segundo camino independiente en `topUpRadioQueueIfNeeded()`).
  Seguir pidiendo pruebas hasta confirmación real, no dar nada por
  cerrado con el primer arreglo que "debería" funcionar.
- **`PopurriDebugLogger` nuevo en S028** (`popurri_favoritos_debug.txt`,
  mismo patrón que `RadioDebugLogger`) -- Favoritos/popurrí no tenía
  ningún registro de depuración hasta esta sesión; encontró el bug de
  HTTP 404 sistemático (`47b3932`) a la primera. Pedir este archivo
  específico ante cualquier fallo futuro de popurrís de Favoritos.
- **El zip de logs de GitHub Actions sigue sin ser accesible por red**
  desde el entorno de trabajo del modelo (redirige a un host de blob
  storage fuera de la lista permitida). El paso del workflow que
  publica los errores de compilación como anotaciones del check
  (legible por API) sigue siendo el método fiable.

# MIMOO — ENRUTADOR DE ANEXOS

*Vive en `DOCS/ANNEX_ROUTER.md`. **Único archivo del repositorio con
potestad para decir qué hito está EN PROGRESO** (instrucción explícita
y repetida de Miguel Ángel, 2026-07-15). Ningún otro archivo —
`MASTER_DOCUMENT.md`, ningún `ANNEX_H0X.md`, `RESUMPTION_POINT.md` —
contiene esa información. Editar y commitear como cualquier otro
archivo del proyecto, vía `newflow-android-edit`.*

---

## PRINCIPIO FUNDAMENTAL

Solo existen **dos** estados posibles para un hito: **EN PROGRESO** y
**PAUSADO**. Nunca "completado" — ningún hito lo está jamás.

Debe haber **siempre exactamente un** hito EN PROGRESO — nunca cero,
nunca dos. Todos los demás están PAUSADOS.

`MASTER_DOCUMENT.md` y los anexos (`DOCS/ANNEX_H0X.md`) son
puramente descriptivos: qué está construido/verificado y qué queda
abierto, sin mencionar jamás su propio estado.

---

## TABLA DE ESTADO

| Hito | Anexo | Estado |
|---|---|---|
| H01 | `DOCS/ANNEX_H01.md` | PAUSADO |
| H02 | `DOCS/ANNEX_H02.md` | PAUSADO |
| H03 | `DOCS/ANNEX_H03.md` | PAUSADO |
| H04 | `DOCS/ANNEX_H04.md` | PAUSADO |
| H05 | `DOCS/ANNEX_H05.md` | PAUSADO |
| H06 | `DOCS/ANNEX_H06.md` | PAUSADO |
| H07 | `DOCS/ANNEX_H07.md` | PAUSADO |
| H08 | `DOCS/ANNEX_H08.md` | ← **EN PROGRESO** |
| H09 | `DOCS/ANNEX_H09.md` | PAUSADO |
| H10 | `DOCS/ANNEX_H10.md` | PAUSADO |
| H11 | `DOCS/ANNEX_H11.md` | PAUSADO |
| H12 | `DOCS/ANNEX_H12.md` | PAUSADO |
| H13 | `DOCS/ANNEX_H13.md` | PAUSADO |
| H14 | `DOCS/ANNEX_H14.md` | PAUSADO |

---

## HISTORIAL DE CAMBIOS DE HITO

- **2026-07-15** — Enrutador creado (migración desde el marcador que
  antes vivía en la tabla de `MASTER_DOCUMENT.md`, instrucción de
  Miguel Ángel). H09 se mantiene EN PROGRESO sin interrupción; H10 se
  abre PAUSADO en la misma sesión (planteamiento inicial recibido,
  sin hoja de ruta ejecutable todavía).
- **2026-07-15 (misma sesión S011)** — H11 abierto PAUSADO: Canales
  (suscripciones + descarga automática tipo podcast), a petición
  explícita de Miguel Ángel al aclarar qué significaba "Canal" en los
  niveles 9-10 de H10. H09 se mantiene EN PROGRESO.
- **2026-07-18** — PCH explícito de Miguel Ángel al arranque de
  sesión: H09 pasa a PAUSADO (sin incidencia nueva, simplemente se
  cierra el foco de esta sesión), H08 pasa a EN PROGRESO (fallo real
  reportado en dispositivo sobre la Ampliación S011: filtro
  país+género+década no está evitando que temas en español deriven a
  sugerencias en inglés — ver `DOCS/ANNEX_H08.md`).
- **2026-07-18 (cierre de S014, misma fecha)** — PCH explícito de
  Miguel Ángel al cierre de sesión: H08 pasa a PAUSADO (el rediseño
  cerrado en S013 quedó construido y compilando en S014, pendiente
  solo de verificación en dispositivo real -- ver `DOCS/ANNEX_H08.md`
  sección "COMPLETADAS EN S014"), H07 pasa a EN PROGRESO --
  divergencia real de favoritos/ajustes entre dispositivos tras
  sincronizar (reportada por Miguel Ángel en S013, ver
  `DOCS/ANNEX_H07.md`).
- **2026-07-18 (cierre de S015, misma fecha)** — PCH explícito de
  Miguel Ángel al cierre de sesión: H07 pasa a PAUSADO (réplica total
  construida en S015 -- datos, tres puntos de conectividad/push
  inmediato, verificación en dispositivo pendiente, ver
  `DOCS/ANNEX_H07.md`), H08 pasa a EN PROGRESO -- Miguel Ángel pide
  explícitamente los porcentajes del cupo 80/10/10 configurables en
  Ajustes, punto ya anticipado y con hoja de ruta ejecutable en
  `DOCS/ANNEX_H08.md` ("Hoja de Ruta para la Siguiente Sesión que
  retome H08", punto 3).
- **2026-07-19 (cierre de S016, misma fecha)** — PCH explícito de
  Miguel Ángel al cierre de sesión: H08 pasa a PAUSADO (bloque de
  trabajo real de esta sesión -- fix de década, eliminación de
  "classical" en todo el flujo, cascada género+década también en
  disco, historial entre sesiones, ampliación del diccionario en tres
  pasadas -- construido y compilando en verde, pendiente de
  verificación en dispositivo real, ver `DOCS/ANNEX_H08.md` secciones
  "S016"), **H12 abierto EN PROGRESO** -- hito nuevo (Directorio de
  Música + Favoritos sin descarga), a petición explícita de Miguel
  Ángel al pedir un menú de tres puntos en el reproductor para ver
  álbum/artista del tema que suena, ampliado en la propia conversación
  a un directorio completo navegable vía MusicBrainz -- ver
  `DOCS/ANNEX_H12.md`. Miguel Ángel decidió explícitamente que la
  sesión que retome H12 sea de **diseño puro, sin tocar código**
  (mismo patrón que S013 para H08).
- **2026-07-19 (cierre de S018, misma fecha)** — PCH explícito de
  Miguel Ángel al cierre de sesión: H12 pasa a PAUSADO (hoja de ruta
  completa de S017 construida entera en S018 -- entidades+migración,
  normalizeArtistName(), ArtistScreen/AlbumScreen/SongScreen+NavGraph,
  desambiguación, búsqueda unificada, botones Reproducir/Descargar,
  menú del reproductor -- más el rediseño de Explorador y dos fixes
  reales confirmados en dispositivo por Miguel Ángel durante la
  sesión: normalización de puntuación en SearchNormalizer y cruce con
  lo descargado por posición de pista en vez de por youtubeId recién
  emparejado, ver `DOCS/ANNEX_H12.md`), **H13 abierto EN PROGRESO** --
  hito nuevo (UX del Reproductor / ExoPlayer, estado visual de
  controles ON/OFF), a petición explícita de Miguel Ángel: "chapitas,
  opciones de cíclicos y aleatorio deben verse cuando están activos,
  etc." -- ver `DOCS/ANNEX_H13.md`.
- **2026-07-25 (S020)** — PCH explícito de Miguel Ángel: H13 pasa a
  PAUSADO (el "etc." quedó cerrado y construido en esta misma sesión
  -- cristal esmerilado en todos los botones del reproductor y en el
  bloque de metadatos, chapita encendida para aleatorio/cíclico,
  compilando en verde, pendiente solo de verificación visual en
  dispositivo, ver `DOCS/ANNEX_H13.md` sección "COMPLETADAS EN
  S020"), **H08 pasa a EN PROGRESO** -- petición textual:
  *"la radio está funcionando realmente mal, mezclando décadas y
  géneros y orígenes resultando en un poutpourri de temas sin sentido
  alguno"*, con dos logs reales entregados como evidencia. Diagnóstico
  completo sobre código real y hoja de ruta cerrada y ejecutable en
  `DOCS/ANNEX_H08.md`, sección "S020".
- **2026-07-25 (cierre de S021, misma fecha)** — PCH explícito de
  Miguel Ángel al cierre de sesión: H08 pasa a PAUSADO (hoja de ruta
  de S020 cumplida entera -- auditoría del diccionario, cierre de las
  cuatro fugas de década que soltaba el código, ampliación de 286 a
  751 entradas y verificación en línea de cinco entradas falsas; no
  queda trabajo de implementación pendiente, solo verificación en
  dispositivo, ver `DOCS/ANNEX_H08.md` sección "COMPLETADAS EN S021"),
  **H14 abierto EN PROGRESO** -- hito nuevo (Almacenamiento de la
  Biblioteca), construido en esta misma sesión a petición explícita de
  Miguel Ángel: poder cambiar desde Ajustes la carpeta donde vive el
  audio, incluida una tarjeta externa, llevándose todas las canciones
  sin perder favoritos, listas ni canales. Recogía una petición que
  venía registrada sin hito desde S020. Instrucción textual de Miguel
  Ángel para este PCH: *"anota las implementaciones en los hitos
  correspondientes, si el movimiento no encaja en ninguno, creas a tal
  efecto"* -- no encajaba (H06 es respaldo en Drive, H07 es
  sincronización entre dispositivos; ninguno gobierna dónde vive el
  audio local), así que se creó `DOCS/ANNEX_H14.md`. Dejó a elección
  del modelo cuál de los dos quedaba EN PROGRESO: se elige H14 por ser
  lo más reciente y lo menos verificado, y porque H08 ya no tiene
  trabajo de implementación pendiente. Si la escucha de la Radio en
  dispositivo revela algo, H08 vuelve con un PCH trivial.

- **2026-07-26** — PCH al cierre de S022, petición explícita de Miguel
  Ángel ("Ok `PCH` y comenzamos con ese bloque en la siguiente
  sesión"): **H08 pasa a EN PROGRESO**, H14 pasa a PAUSADO.

  La sesión arrancó en H14 (verificación en dispositivo del traslado a
  tarjeta externa) y derivó a H08 al aparecer fallos graves de Radio
  con música española. Ambos hitos recibieron trabajo y ambos quedan
  anotados en su anexo.

  El bloque que abre la siguiente sesión está en `DOCS/ANNEX_H08.md`,
  sección "Hoja de Ruta para la Siguiente Sesión que retome H08":
  enriquecer el diccionario local para que cada entrada lleve su
  CONJUNTO de géneros en vez de uno solo, más las dos decisiones de
  criterio musical que quedaron sin resolver.

  H14 se pausa con su verificación en dispositivo hecha y con seis
  fallos reales corregidos, pero con la reconciliación posterior al
  traslado y el traslado en segundo plano todavía abiertos — ver
  `DOCS/ANNEX_H14.md`.

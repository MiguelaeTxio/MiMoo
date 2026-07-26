# MIMOO — ANEXO H14: Almacenamiento de la Biblioteca

*Vive en `DOCS/ANNEX_H14.md`. Documento puramente descriptivo: qué
está construido/verificado y qué queda abierto. **Nunca menciona su
propio estado** — el estado de cada hito vive exclusivamente en
`DOCS/ANNEX_ROUTER.md`.*

---

## Nota de Apertura

Hito abierto en S021 (2026-07-25) por PCH explícito de Miguel Ángel.
Recoge una petición de producto que venía registrada sin hito desde
S020 en `DOCS/RESUMPTION_POINT.md`, en sus palabras textuales:

> *"dar la posibilidad de cambiar la carpeta de descarga con la opción
> de copiar todo a esa carpeta o solamente los settings, sin perder
> favoritos etc."*

Concretada por él en S021 con el caso de uso real:

> *"ahora tenemos una carpeta donde se guarda toda la biblioteca de
> música, pues poder decir, voy a cambiarla, voy a ponerla, por
> ejemplo, en una tarjeta externa, en tal carpeta que he creado para
> tal efecto, y pasar allí todas las canciones, todos los álbumes,
> listas, canales, etcétera. Y por supuesto que no se pierdan los
> favoritos y demás."*

En S020 quedó anotado que esto no era H08 y que abrir hito requería
PCH explícito. Ese PCH llegó al cierre de S021, con instrucción de
crear el hito si el trabajo no encajaba en ninguno existente. No
encajaba: H06 es respaldo de metadatos en Drive y H07 es
sincronización entre dispositivos; ninguno gobierna dónde vive el
audio en el dispositivo local.

---

## Objetivo del Hito

Que Miguel Ángel pueda decidir y cambiar en cualquier momento **dónde
vive físicamente el audio descargado**, incluida una tarjeta externa,
sin perder nada de lo que la app sabe sobre esa música.

---

## Contexto Técnico (leído sobre el código real en S021)

### La raíz de almacenamiento ya era elegida por el usuario

`StorageManager` (en `data/download/`) persiste en SharedPreferences
el Uri de árbol SAF que el usuario elige con `OpenDocumentTree`. Lo
que faltaba no era elegir carpeta —eso existía desde el principio—
sino poder **cambiarla** después, y llevarse lo ya descargado.

### Por qué no bastaba con cambiar la raíz

`SearchResultTrack.filePath` **no guarda una ruta relativa a la raíz**:
guarda el Uri de contenido SAF COMPLETO del archivo. Así lo leen
`LibraryReconciler` y `TrackFileRelocator`
(`DocumentFile.fromSingleUri(context, Uri.parse(filePath))`). Cambiar
la raíz no reescribe esos Uri por sí solo: cada archivo hay que
copiarlo al destino y regenerar su Uri uno a uno.

### Por qué los favoritos no se pierden

Repasadas las ocho entidades de `data/local/entity`, **la única
columna de toda la base de datos que contiene una ruta es
`SearchResultTrack.filePath`**. Favoritos (`isFavorite`,
`FavoriteArtist`, `FavoriteAlbum`), listas (`Playlist` +
`PlaylistTrackCrossRef`), canales (`ChannelSubscription`), emisoras
(`FavoriteRadioStation`), desambiguación de artistas y carátulas
referencian pistas por `youtubeId` o por artista/álbum, nunca por
ruta. Migrar `filePath` es por tanto condición suficiente: el resto
de la base de datos ni se lee ni se escribe durante el traslado.

---

## Construido en S021

### `data/library/LibraryMigrator.kt` (nuevo)

Traslada al destino todas las pistas en `DownloadStatus.DONE` con
`filePath`, respetando la estructura `{Artista}/{Álbum}` vía
`DownloadDirManager.getOrCreateTrackDir()` y conservando el nombre de
archivo, **incluido el prefijo `"NN - "` de posición de pista**: mover
la biblioteca no debe reordenar los álbumes.

Decisiones de diseño, todas deliberadas:

- **Copia + borrado, no `DocumentsContract.moveDocument()`.** El
  soporte de move es específico de cada proveedor SAF y no está
  garantizado **entre dos volúmenes distintos**, que es justo el caso
  de uso principal aquí (memoria interna -> tarjeta externa). Mismo
  criterio, y por la misma razón, que `TrackFileRelocator`.
- **Room primero, borrado del origen después.** En ese orden, una
  interrupción entre ambos deja un archivo huérfano en el origen:
  molesto pero inofensivo. En el orden inverso dejaría una fila
  apuntando a un archivo ya borrado, que sí rompe la reproducción.
- **Nunca destructivo antes de tiempo.** El origen solo se borra si la
  copia terminó entera Y la fila ya apunta al destino. Una migración a
  medias deja la biblioteca funcionando, con unas pistas en el destino
  y otras en el origen — nunca rota.
- **Reentrante.** Si el destino ya tiene el archivo con el mismo
  tamaño, se reutiliza en vez de recopiarlo: repetir un cambio de
  carpeta interrumpido continúa donde iba y no duplica nada. Colisión
  real de nombres con tamaño distinto -> sufijo `" (n)"`.

### `StorageManager`

- `getRootLabel()` — nombre legible de la carpeta actual para
  mostrarlo en Ajustes (nombre del proveedor SAF y, si no lo da, el
  último segmento decodificado del Uri, del estilo `primary:Music/MiMoo`
  o `1A2B-3C4D:MiMoo` en tarjeta externa).
- Documentado explícitamente que **el permiso persistible de la raíz
  anterior no se libera nunca**. Con "solo cambiar la carpeta", o tras
  una migración parcial, hay filas que siguen apuntando con Uri
  absolutos a la raíz vieja; soltar aquel permiso las volvería
  ilegibles de golpe. Android permite mantener varios permisos de
  árbol a la vez.

### `SettingsViewModel`

`LibraryFolderState` (`Idle` / `Migrating` / `Done` / `Error`) y
`changeLibraryFolder(uri, moveFiles)`. **La raíz se guarda SIEMPRE
primero**, antes de mover un solo byte y en las dos ramas, para que el
ajuste quede aplicado aunque la migración se interrumpa: las descargas
nuevas van ya al destino y lo no movido sigue sonando desde el origen.

### `SettingsScreen`

Sección de acordeón **"Almacenamiento"** con la carpeta actual y el
botón "Cambiar carpeta de la biblioteca" (`OpenDocumentTree`). Al
elegir carpeta, diálogo con las dos ramas que pidió Miguel Ángel:

1. **Mover toda la biblioteca a la carpeta nueva.**
2. **Solo cambiar la carpeta** (dejar lo descargado donde está).

Las dos opciones van en el cuerpo del diálogo y no como botones de
acción, porque el `AlertDialog` de Material 3 pone los botones en fila
y con tres no caben legibles en un móvil.

Durante el traslado, diálogo de progreso **no descartable** con
contador `done/total`, cuenta de fallidas y aviso de no cerrar la app.
Al terminar, resumen con cuántas se movieron y, si alguna falló, la
indicación de que sigue sonando desde la carpeta anterior y de que
repetir el cambio reintenta sin duplicar.

---

## Hoja de Ruta para la Siguiente Sesión que Retome H14

1. **Verificación en dispositivo real con tarjeta externa** — es el
   paso bloqueante, nada más tiene sentido antes:
   - Que el selector de Android ofrezca de verdad la tarjeta y que
     `takePersistableUriPermission()` no lance `SecurityException`
     sobre ella (algunos fabricantes restringen ciertas rutas).
   - Que tras "mover toda la biblioteca" las canciones sigan sonando,
     los álbumes conserven su orden (prefijo `NN - `) y favoritos,
     listas y canales sigan intactos.
   - Que tras "solo cambiar la carpeta" lo antiguo siga reproduciéndose
     desde la raíz vieja y lo nuevo se descargue en la nueva.
   - Comportamiento con la app en segundo plano a mitad de traslado.
2. **Reconciliación posterior al traslado.** Hoy el migrador no dispara
   `LibraryReconciler.rescan()` al terminar. Decidir si debe hacerlo:
   dejaría la base de datos y el disco alineados de una pasada, pero
   sobre una tarjeta lenta puede ser costoso. Pendiente de ver el
   comportamiento real primero.
3. **Traslado como trabajo en segundo plano.** Hoy corre en el
   `viewModelScope` de Ajustes, con el diálogo abierto. Con una
   biblioteca grande sobre tarjeta externa esto puede ser demasiado
   largo para un diálogo modal; el candidato natural es un
   `CoroutineWorker` de WorkManager con notificación de progreso,
   igual que `DownloadWorker`. **No se hizo en S021 a propósito**: sin
   una medición real de cuánto tarda, sería complejidad sin evidencia.
4. **Espacio disponible.** No se comprueba antes de empezar. Si la
   tarjeta se queda sin sitio a mitad, las pistas que fallen se
   contabilizan y siguen en el origen (no se pierde nada), pero sería
   mejor avisar antes de empezar. Requiere `StatFs` o la API de
   almacenamiento del proveedor SAF.

---

## Fuera de Alcance de Este Hito

- Múltiples bibliotecas simultáneas (varias raíces activas a la vez).
  Miguel Ángel no lo ha pedido: la petición es cambiar **la** carpeta,
  no tener varias.
- Sincronizar el audio entre dispositivos — eso es H07, y sigue siendo
  explícitamente metadatos, no audio.
- Respaldo del audio en Drive — H06 respalda metadatos, no audio, por
  decisión de diseño ya tomada.

---

## COMPLETADAS EN S022 — verificación en dispositivo y seis fallos reales

El punto 1 de la hoja de ruta (verificación en dispositivo con tarjeta
externa) se ejecutó. `takePersistableUriPermission()` no lanzó
`SecurityException`, que era el riesgo anotado. Todo lo demás falló, y
de formas que el diseño no había previsto:

1. **8 de 763 pistas no se pudieron mover, sin saber cuáles.**
   `LibraryMigrator` tenía cinco caminos distintos hacia el mismo
   `failed++` mudo, y `copyIfNeeded` remataba con
   `catch (e: Exception) { false }`, perdiendo la causa raíz en el
   mismo sitio donde se producía. Ahora cada fallo registra su motivo
   concreto (`FailureReason`), el diálogo los lista con nombre y
   causa, y el detalle con rutas queda en
   `traslado_biblioteca_informe.txt` en la raíz de destino.

2. **El guardián de disco redescargó la biblioteca entera.**
   `LibraryReconciler.verifyDiskState()` marcaba PENDING toda fila
   DONE cuyo archivo no respondiera `exists()`, y
   `AutoSyncViewModel.verifyDiskAndReconcile()` las reencolaba acto
   seguido. No distinguía "el archivo se borró" de "el volumen no está
   accesible". Con la biblioteca en memoria interna eso no pasaba
   nunca; en tarjeta externa es el caso habitual. Tres salvaguardas:
   flag `LibraryMigrator.isMigrating`, comprobación de que la raíz
   responde antes de creerse nada, y tope de cordura (10+ pistas y más
   del 25% desaparecidas a la vez = problema de acceso, no borrado).

3. **La app borró carpetas de música con su contenido dentro.**
   `DocumentFile.listFiles()` devuelve array VACÍO cuando el proveedor
   SAF falla -- no lanza. Y `pruneEmptyFolders()` hacía
   `if (sub.listFiles().isEmpty()) sub.delete()`, de modo que "está
   vacía" y "no he podido leerla" eran el mismo caso. Es el único
   punto de la app que borra directorios, y `rescan()` lo ejecuta en
   CADA sincronización automática. Puerta de seguridad en `rescan()` +
   exigencia de `exists() && canRead()` antes de creerse un listado
   vacío.

4. **La etiqueta de Ajustes no distinguía dónde estaba apuntando.**
   `getRootLabel()` devolvía solo el nombre de la carpeta hoja, y la
   carpeta destino se llamaba `miMoo` igual que la de origen. Se
   construye ahora desde `treeDocumentId`: `Memoria interna · ...` /
   `Tarjeta SD (1A2B-3C4D) · ...`. El cambio de raíz SÍ funcionaba --
   verificado que `saf_root_uri` es el único almacén y todos los
   consumidores leen de él.

5. **`OutOfMemoryError` con heap de 256 MB durante la restauración.**
   `AutoSyncPusher` llamaba a `pushCurrentState()` en CADA mutación, y
   eso construye y serializa el bundle ENTERO. Con varios
   `DownloadWorker` en paralelo, varias copias completas vivas a la
   vez. Amortiguación por ventana (5 s, tope 60 s) y `Mutex` que
   garantiza un solo bundle en memoria.

6. **Traslado atómico y comprobación previa de espacio** (punto 4 de
   la hoja de ruta), a petición explícita de Miguel Ángel antes de
   mover de la tarjeta de vuelta al teléfono. Tres fases: precondiciones
   (bytes necesarios vs. libres con margen de 300 MB, abortando sin
   mover nada), copia completa sin tocar Room ni borrar, conmutación en
   una única transacción (`updateAll()`, nuevo en el DAO), y borrado de
   originales. El punto de no retorno es único e instantáneo. El
   ajuste de raíz se revierte si el traslado aborta.

**Consecuencia sufrida:** la tablet perdió la biblioteca y hubo que
restaurarla desde Drive resolviendo el conflicto a favor de la nube.
El teléfono conservó la suya resolviendo a favor del dispositivo. El
flujo de sincronización -- `runSync()`, diálogo de conflicto,
`confirmCloudWins()`, `confirmLocalWins()` -- **no se tocó en toda la
sesión**, verificado a petición expresa de Miguel Ángel.

### Sigue abierto en H14

- **Punto 2 — reconciliación posterior al traslado.** Sin resolver, y
  ahora con motivo concreto: las filas que el guardián tumbó a PENDING
  antes del arreglo no se recuperan solas. Conviene estudiar si
  `rescan()` puede reasociarlas leyendo el `MIMOO_YOUTUBE_ID` embebido
  en los propios archivos, en vez de redescargar.
- **Punto 3 — traslado como trabajo de WorkManager en segundo plano.**
  Intacto. Hoy sigue colgando del `viewModelScope` de Ajustes.
- **Aviso en UI cuando salta la salvaguarda de cordura.** Hoy solo
  queda en logcat; falta decidir con Miguel Ángel si debe avisarse y
  con qué texto.
- **Contador "N pistas siguen apuntando fuera de esta carpeta"** en
  Ajustes, para responder con un número y no por inferencia a si el
  traslado se completó. Propuesto, no decidido.

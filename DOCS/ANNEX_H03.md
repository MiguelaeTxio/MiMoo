# MIMOO — ANEXO HITO 03
# Biblioteca Local: Reproducción Offline, CRUD, Favoritos y Carátulas

*Vive en `DOCS/ANNEX_H03.md` — flujo NewFlow Android. Ver estado en
`DOCS/ANNEX_ROUTER.md`.*

---

## NOTA DE APERTURA (S004, 2026-07-01)

Creado tras detectar al cierre de S003 una inconsistencia: el Hito 02
marcaba como (DONE) la "reproducción desde archivo local" sin
haberla implementado. Este anexo retoma esa reproducción real como
PASO 1, junto con el resto de trabajo de biblioteca.

---

## OBJETIVO DEL HITO

Convertir el conjunto de pistas ya descargadas (Hito 02) en una
biblioteca local navegable: reproducción offline real, organización
física por Artista/Álbum, pantalla de biblioteca con CRUD, favoritos,
y carátulas vía MusicBrainz/Cover Art Archive.

---

## COMPLETADAS EN S004 (2026-07-01/02)

- **PASO 1** — Reproducción real desde archivo local:
  `SearchViewModel.playTrack()` comprueba `downloadStatus == DONE &&
  filePath != null` y reproduce vía `playerManager.play(filePath,
  title, isLocal = true)`. Verificado por Miguel Ángel en modo avión.
- **PASO 2** — Reorganización por Artista/Álbum: persistido
  `artist`/`album` en `SearchResultTrack` (migración Room), fallback
  de álbum ausente renombrado de `_sin_album` a `"Sencillos"`.
- **PASO 3** — Pantalla de Biblioteca: `LibraryScreen`/
  `LibraryViewModel` con vista jerárquica (Artista→Álbum→Pista) y
  plana ordenable, buscador interno, reproducción de álbum/artista
  completo y aleatorio. Motor de cola nuevo en `PlayerManager`
  (`QueueItem`, `playQueue()`, `playNext()`/`playPrevious()`).
- **PASO 4** — Favoritos: `isFavorite: Boolean` en
  `SearchResultTrack` (migración Room), icono estrella, filtro "solo
  favoritos".
- **PASO 5** — Borrado de descarga: diálogo de confirmación. Filas
  sintéticas se eliminan por completo; filas reales se resetean a
  PENDING/filePath=null.
- **PASO 10** (añadido, fuera de alcance original) — Reconciliación
  de biblioteca: `LibraryReconciler` recorre la carpeta SAF y da de
  alta en Room cualquier `.opus` sin fila correspondiente. Solo se
  invoca al elegir carpeta o desde botón de refresco manual.
- **Fix CI** (fuera de alcance): `versionCode` dinámico vía
  `github.run_number`, keystore de debug fija con verificación
  SHA-256.

---

## COMPLETADAS EN S001 (2026-07-02) — flujo NewFlow

**PASO 6 — Carátulas vía MusicBrainz + Cover Art Archive.**
`SearchResultTrack.coverArtUrl` (nuevo campo, migración Room 3→4).
`MusicBrainzApiService.searchReleases` (Retrofit, base
`https://musicbrainz.org/ws/2/`). `CoverArtRepository`: MBID vía
MusicBrainz, URL de carátula construida directamente como
`https://coverartarchive.org/release/{mbid}/front` (Coil sigue el
307 o falla en 404 sin necesidad de un segundo parseo JSON), con
caché de sesión en memoria. `NetworkModule`: cliente OkHttp separado
para MusicBrainz con User-Agent obligatorio
(`MiMoo/1.0 ( https://github.com/MiguelaeTxio/MiMoo )`) y limitador
de ~1 req/s (verificado contra la documentación oficial de
MusicBrainz, 2026-07-02). `LibraryViewModel.requestCoverArtIfMissing`
resuelve de forma perezosa, una vez por álbum y por ejecución del
proceso. `LibraryScreen.AlbumHeaderRow` muestra la carátula con
fallback a la miniatura de YouTube y, si tampoco hay, a un icono
genérico.

**PASO 7 — Edición manual de metadatos.** `TrackFileRelocator`
(nuevo, `data/library/`): copia+borra el `.opus` a la nueva carpeta
`{artista}/{álbum}/` cuando cambia artista o álbum, con sufijo
incremental si hay colisión de nombre.
`LibraryViewModel.editMetadata`: el título se actualiza en el sitio
sin mover archivo; artista/álbum reubican el archivo físico. Aplica
igual a filas reales y sintéticas. Si la reubicación falla, la
edición completa se aborta (ni Room ni filesystem cambian) — se
muestra vía `editMetadataError` en vez de dejar un `filePath`
obsoleto.

**PASO 9 — Manejador de archivos de audio del sistema.**
`AndroidManifest.xml`: segundo `intent-filter` en `MainActivity`
(`ACTION_VIEW` + categoría `DEFAULT` + `mimeType="audio/*"`, sin
`scheme` — coincide implícitamente con `content:`/`file:`).
`android:launchMode="singleTask"` añadido. `MainActivity.
handleViewIntent()` enruta el Uri recibido directo a
`PlayerManager.play(isLocal=true)`, independiente de
`SearchViewModel`/Biblioteca. `onNewIntent` cubre la app ya en
primer plano.

---

## Estado

**PASOS 1-7, 9 y 10 completos.** Pendiente:

### PASO 8 — Verificación funcional (PENDIENTE)

Requiere instalar la APK en el dispositivo de Miguel Ángel y
confirmar: reproducción offline real (sin conexión), estructura de
carpetas Artista/Álbum en el explorador de archivos, favoritos
persistentes tras reiniciar, borrado de descarga funcional, carátula
visible para al menos un álbum real, edición de metadatos con
reubicación de archivo confirmada, y que un `.opus` abierto desde el
explorador de archivos del sistema ofrece MiMoo como opción de
apertura.

**No se ha probado todavía** (a fecha de esta migración,
2026-07-02) — Miguel Ángel solo probó el flujo de H05 en la sesión
de verificación.

---

## Incidencias Abiertas (sin resolver, no bloqueante, sin confirmación de vigencia)

- **Actualización de la APK falla siempre con "conflicto con un
  paquete" — CONFIRMADO SISTEMÁTICO, no un caso puntual (2026-07-02).**
  Se repite en cada una de las últimas 10-12 versiones de la app: el
  ciclo real de Miguel Ángel es instalar → intentar actualizar en la
  siguiente versión → falla → desinstalar → reinstalar limpio, y
  vuelve a fallar en la versión siguiente. Esto **descarta** la
  hipótesis anterior (keystore antigua residual de antes de fijar la
  keystore de debug por secret): si fuera eso, una única reinstalación
  limpia lo habría resuelto para siempre, y no es el caso. La keystore
  de debug fija y el `versionCode` dinámico creciente ya están
  verificados correctos en el workflow — la causa real sigue sin
  identificar.
  **Pista a investigar (aportada por Miguel Ángel, pendiente de
  sesión):** a NewPipe le pasaba lo mismo hasta que en algún momento
  lo resolvieron; revisar sus changelogs para ver qué cambiaron.
  Hipótesis de partida: algo relacionado con confianza/registro del
  desarrollador ante Google (Play Integrity, verificación de firma a
  nivel de cuenta, etc.), a validar contra el changelog real antes de
  asumir nada. **Deliberadamente pospuesto** — prioridad es terminar
  la app (H05), no perseguir esto ahora.
- 4 archivos físicos duplicados de prueba en `Canal IMAR/_sin_album`
  (3) y `Canal IMAR/Sencillos` (1) — Miguel Ángel prefiere borrarlos
  desde la app (PASO 5, ya disponible) en vez de a mano.

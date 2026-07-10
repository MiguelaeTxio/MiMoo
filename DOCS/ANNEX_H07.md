# MIMOO — ANEXO HITO 07
# Persistencia de Enlaces + Sincronización Automática + Actualizaciones In-App + Controles de Reproducción

*Vive en `DOCS/ANNEX_H07.md` — flujo NewFlow Android. Ver estado en
`DOCS/MASTER_DOCUMENT.md`. EN PROGRESO.*

---

## NOTA DE REDEFINICIÓN (S008, 2026-07-10)

Este anexo **sustituye por completo** el diseño abierto en S007. No es
un ajuste menor: el diagnóstico de un bug real (export/import vacíos)
destapó un problema de fondo que obliga a rehacer el planteamiento
entero del hito.

### Qué pasó

Miguel Ángel reportó que "Exportar a Drive" subía un JSON vacío
(`tracks: []`, `favoriteAlbums: []`, `playlists: []`), y por tanto
"Importar" no traía nada. Investigado con código real (no memoria):

1. `BackupRepository.buildCurrentBundle()` filtra explícitamente
   cualquier `SearchResultTrack` cuyo `youtubeId` empiece por
   `local:` (decisión de S006, ver histórico en `ANNEX_H06.md`) — son
   filas sintéticas que `LibraryReconciler` genera para archivos
   encontrados en disco sin fila en Room.
2. `LibraryReconciler.rescan()` genera esas filas sintéticas
   **solo** la primera vez que se elige la carpeta SAF (el
   comentario de cabecera de `LibraryReconciler.kt` decía "en CADA
   arranque" pero es un docstring obsoleto — el código real de
   `MainActivity.kt`, corregido el 2026-07-05, confirma que solo
   dispara en el primer picker, más el botón de refresco manual).
   Es exactamente lo que ocurre tras una reinstalación: Room vive en
   almacenamiento interno (se borra al desinstalar), la carpeta SAF
   externa no — al volver a elegirla, todo lo que había se reconstruye
   como sintético.
3. **Causa raíz real, más profunda que el filtro de S006:**
   `downloader.py`/`DownloadWorker.kt` nunca grabaron el `youtubeId`
   ni la URL de origen en ningún sitio del archivo `.opus` en sí —
   solo en la fila de Room. Confirmado leyendo `downloader.py`
   completo: no hay postprocesador de metadatos, ni `-metadata`, ni
   ningún tag con el id o la URL. El link **solo existía en Room**.
   Con varias reinstalaciones sucesivas durante H06 (migración de
   dispositivo, fix de `allowBackup`), Room se vació y el disco
   reconstruyó sintéticamente toda la biblioteca sin links reales —
   de ahí el JSON vacío al exportar.

No había backup antiguo en Drive con datos reales que rescatar
(confirmado con Miguel Ángel). La biblioteca actual del dispositivo
se da por perdida a efectos de link real; Miguel Ángel la borra él
mismo (Room + archivos) para partir de cero, fuera de esta sesión de
trabajo — no es una tarea de este modelo.

### Decisión de fondo

El link de YouTube tiene que persistir **en el propio archivo**, no
solo en Room, para que sobreviva a cualquier reinstalación. Sin eso,
ni el backup manual (H06) ni ninguna sincronización automática pueden
funcionar de verdad — es la base de todo lo demás, por eso pasa a ser
la PARTE 0 de este hito, antes que cualquier otra cosa.

Aprovechando que hay que rehacer esto, se amplía el alcance del hito
para cerrar de una vez varios pendientes que Miguel Ángel quiere
resueltos juntos, sin dilatarlo en sesiones sueltas.

---

## OBJETIVO DEL HITO (redefinido)

### PARTE 0 — Persistencia del link dentro del archivo (prerrequisito de todo lo demás)

Grabar el `youtubeId` (o la URL) como metadato embebido en cada
`.opus` en el momento de la descarga, para que el archivo lleve su
propio origen pegado encima, sobreviva o no Room a una desinstalación.
`LibraryReconciler.buildSyntheticTrack()` pasa a intentar leer ese
metadato al reconciliar un archivo huérfano; solo si no lo encuentra
(archivo de antes de este fix, o copiado a mano desde fuera de MiMoo)
recurre al hash `local:` como hasta ahora.

**Pendiente de verificar en línea (directriz §4.5 del
`MASTER_DOCUMENT.md`) antes de implementar:** qué tag de metadato
Opus/Vorbis Comment es el más robusto para guardar un campo propio
(p.ej. `MIMOO_YOUTUBE_ID` o reutilizar `COMMENT`), y qué API de
Android permite leerlo de vuelta de forma fiable
(`MediaMetadataRetriever` tiene soporte limitado a claves estándar —
puede hacer falta parsear el bloque de comentario Ogg/Vorbis a mano,
que es un formato simple, o evaluar una librería ligera). Este anexo
no fija la solución técnica exacta todavía — se decide al empezar a
codificar esta parte, con la información más actual disponible.

### PARTE 1 — Sincronización automática entre dispositivos (sustituye la "sincronización incremental" de S007)

Diseño completamente distinto al aditivo de S007: un archivo de
**copia de respaldo automática** en Drive, actualizado en cada cambio
local, que dos dispositivos comparten para acabar con la biblioteca
en el mismo estado — no es aditivo, es un espejo.

- **Distinto de Exportar/Importar (H06):** aquellos siguen siendo
  manuales, a petición explícita desde Ajustes, y generan un archivo
  nuevo con timestamp cada vez (histórico de snapshots). La copia de
  respaldo automática de H07 es **un único archivo fijo** en Drive
  (no timestamped) que se sobreescribe en su sitio.
- **Actualización automática:** cada vez que se añade o borra un
  álbum, un sencillo o una lista de reproducción, la copia de
  respaldo en Drive se actualiza para reflejarlo — sin acción manual
  de Miguel Ángel.
- **Al arrancar la app / iniciar sesión en un dispositivo:** se
  descarga la copia de respaldo y se compara contra el estado local.
  El disco se deja **exactamente igual** que la copia: lo que está en
  la copia y no en el disco se descarga; lo que está en el disco y ya
  no está en la copia se borra.
- **Nunca se borra nada en silencio:** si la comparación detecta que
  hay que eliminar algo local que ya no está en la copia de respaldo,
  se avisa a Miguel Ángel y se pide confirmación antes de machacar —
  igual que ya hace "Importar desde Drive" en H06.
- Reutiliza el mismo `BackupBundle`/DTOs de `BackupDto.kt` como
  formato — no hace falta inventar uno nuevo, solo un fichero Drive
  distinto (nombre fijo, no timestamped) y una lógica de comparación
  nueva (espejo, no solo-insertar).

### PARTE 2 — Actualizaciones in-app vía EnterpriseBot

MiMoo es uso exclusivo de Miguel Ángel y Silvia — no hace falta
publicación pública real, solo un punto de descarga accesible por
URL que nadie adivine. Decisión cerrada con Miguel Ángel (S008):
reutilizar el web app de **EnterpriseBot**, ya público en
PythonAnywhere, añadiéndole una ruta nueva, no enlazada desde ningún
menú/plantilla, protegida por un token largo aleatorio como parte de
la propia URL (no un simple "sin enlazar" — sin el token la ruta no
sirve nada):

```
https://{dominio-enterprisebot}/mimoo-updates/{TOKEN_LARGO_ALEATORIO}/apk
https://{dominio-enterprisebot}/mimoo-updates/{TOKEN_LARGO_ALEATORIO}/manifest.json
```

El token vive embebido en el APK de MiMoo (mismo patrón que el
Client ID OAuth de Drive, que ya se embebe hoy sin problema) y
registrado en la ruta de EnterpriseBot. La app compara su
`BuildConfig.VERSION_CODE` contra el `versionCode` del manifiesto;
si hay una versión más nueva, avisa a Miguel Ángel en Ajustes y
descarga solo si él confirma explícitamente — nunca automático ni
silencioso.

**Implicaciones de infraestructura, distintas de las asumidas en
S007:**
- Esto toca el repositorio de **EnterpriseBot**, no el de MiMoo — flujo
  de trabajo `nfs-enterprisebot-*`, con su propio token de sesión
  (pendiente: Miguel Ángel debe entregarlo cuando se llegue a esta
  parte).
- El workflow de GitHub Actions de MiMoo (`build-and-deploy.yml`) debe
  seguir subiendo el APK a PythonAnywhere como hasta ahora (ver
  `android-deploy`) — EnterpriseBot necesita poder leer ese mismo
  archivo para servirlo por su nueva ruta, o el workflow lo sube
  también a donde EnterpriseBot lo espera; detalle a resolver al
  implementar esta parte.
- El reload del web app de EnterpriseBot en PythonAnywhere lo hace
  Miguel Ángel a mano desde el dashboard, como siempre — fuera del
  alcance de lo que este modelo puede hacer en NewFlow.

### PARTE 3 — Controles de reproducción: aleatorio y cíclico

Confirmado en el código actual del reproductor (S008): no existe hoy
ningún control de `repeatMode` ni `shuffleMode`. Se añaden dos modos
independientes y combinables, sobre ExoPlayer/Media3 (soporte nativo,
no hay que reinventar nada):

- **Cíclico (repeat):** al llegar al final de la cola de reproducción
  actual, vuelve a empezar por la primera pista — `Player.REPEAT_MODE_ALL`.
- **Aleatorio (shuffle):** orden aleatorio dentro de la cola de
  reproducción actual, sin pararse nunca al terminar la cola —
  requiere `shuffleModeEnabled = true` combinado con
  `REPEAT_MODE_ALL` (shuffle solo, sin repeat, sí llega al final y se
  para; la combinación es la que da el comportamiento "no para
  nunca" que describe Miguel Ángel).
- Caso descrito explícitamente por Miguel Ángel: cíclico activado +
  cola construida progresivamente añadiendo pistas sueltas (p.ej. 200
  canciones vistas/añadidas durante la sesión) → al llegar a la
  pista 200, vuelve a la 1 y repite la cola completa en el mismo
  orden en que se añadieron.
- UI: dos controles (toggle) en la pantalla del reproductor, junto a
  play/pausa/siguiente/anterior — diseño visual a definir al
  implementar esta parte, sin bloquear el resto del hito.

---

## HOJA DE RUTA DETALLADA

### PARTE 0 — Persistencia del link

**PASO 0.1** — Actualizarse en línea (directriz §4.5) sobre la forma
más robusta de embeber y leer un campo de metadato custom en un
contenedor Ogg/Opus desde Android/Kotlin y desde yt-dlp/ffmpeg (via
Chaquopy). Cerrar la decisión técnica exacta antes de tocar código.

**PASO 0.2** — `downloader.py`/`DownloadWorker.kt`: grabar el
`youtubeId` (y, si es barato hacerlo a la vez, título/artista/álbum)
como metadato del archivo en el propio paso de conversión a Opus, sin
necesitar una segunda pasada de ffmpeg si es evitable.

**PASO 0.3** — `LibraryReconciler.buildSyntheticTrack()`: leer el
metadato embebido al reconciliar un archivo huérfano; usar el
`youtubeId` real si está presente, caer al hash `local:` solo si no
hay metadato (archivo legacy o ajeno a MiMoo).

**PASO 0.4** — Verificación funcional: descargar una pista nueva,
comprobar el metadato con una herramienta externa (p.ej. `ffprobe` si
está disponible, o inspección manual), borrar su fila de Room a mano
y forzar un refresco de Biblioteca — confirmar que recupera el
`youtubeId` real, no uno `local:`.

### PARTE 1 — Sincronización automática

**PASO 1.1** — Diseño del archivo de copia de respaldo automática en
Drive: nombre fijo (no timestamped), carpeta (reutilizar "MiMoo
Backups" o carpeta nueva — a decidir al implementar), y cómo se
localiza/actualiza en su sitio (buscar por nombre + `PATCH` de
contenido sobre el mismo `fileId`, mismo patrón que
`BackupDriveRepository.ensureBackupFolder()`).

**PASO 1.2** — Hook de escritura automática: cada operación que añade
o borra un álbum, sencillo o playlist (repositorios ya existentes de
favoritos/playlists/descargas) dispara una actualización de la copia
de respaldo en Drive. Necesita accesToken válido en segundo plano —
revisar cómo encaja con `DriveAuthorizationHelper` (hoy pensado para
una acción puntual disparada por el usuario desde Ajustes, no para
escritura en background).

**PASO 1.3** — Comparación tipo espejo al arrancar/iniciar sesión:
descargar la copia de respaldo, comparar contra Room+disco local,
calcular qué falta (descargar) y qué sobra (candidato a borrar).

**PASO 1.4** — Confirmación antes de borrar: si hay elementos locales
que ya no están en la copia de respaldo, mostrar un diálogo con el
detalle antes de ejecutar ningún borrado — nunca automático.

**PASO 1.5** — Verificación funcional end-to-end con dos dispositivos
reales: añadir/borrar en uno, confirmar que el otro se sincroniza al
iniciar sesión, incluyendo el diálogo de confirmación de borrado.

### PARTE 2 — Actualizaciones in-app

**PASO 2.1** — Token de sesión de EnterpriseBot (cuando Miguel Ángel
lo entregue) + diseño de la ruta oculta con token aleatorio en la URL
(ver OBJETIVO DEL HITO arriba).

**PASO 2.2** — Generar el manifiesto de versión en el workflow de
MiMoo (`build-and-deploy.yml`), tras `assembleDebug`:
`{"versionCode": N, "versionName": "X.Y", "apkUrl": "..."}`, usando
`versionCode` desde `-PversionCode=${{ github.run_number }}` (ya
existe, ver `android-deploy`).

**PASO 2.3** — Resolver cómo EnterpriseBot accede al APK compilado
para servirlo por su ruta (¿lo sube el mismo workflow de MiMoo a
donde EnterpriseBot lo espera, o EnterpriseBot lee de donde ya está
en PythonAnywhere?) — detalle técnico a cerrar al implementar.

**PASO 2.4** — `AppUpdateRepository` en MiMoo: `GET` al manifiesto vía
la URL con token embebido, comparar `versionCode` contra
`BuildConfig.VERSION_CODE`.

**PASO 2.5** — UI en Ajustes: "Buscar actualizaciones", muestra
versión actual vs. disponible, botón de descarga solo si Miguel Ángel
confirma. Instalación vía `FileProvider` + `Intent(ACTION_VIEW)`
(fuentes desconocidas, MiMoo no está en Google Play).

**PASO 2.6** — Verificación funcional: publicar una versión de prueba
con `versionCode` mayor, confirmar detección, descarga e instalación
correcta sobre la instalada.

### PARTE 3 — Controles de reproducción

**PASO 3.1** — Exponer `repeatMode`/`shuffleModeEnabled` de ExoPlayer
en `PlayerManager` (o el repositorio/viewmodel equivalente del
reproductor — releer código real antes de tocar, §4.1).

**PASO 3.2** — UI: dos toggles en la pantalla del reproductor.

**PASO 3.3** — Verificación funcional: cola larga (~200 pistas)
añadida progresivamente, cíclico activado, confirmar que al terminar
vuelve a la primera en el mismo orden; aleatorio activado (con y sin
cíclico), confirmar que no se detiene al agotar la cola cuando ambos
están activos.

---

## Fuera de Alcance de Este Hito (explícitamente pospuesto)

- Resolución de conflictos más allá de "la copia de respaldo manda"
  en la sincronización automática — no hay fusión inteligente de
  cambios concurrentes en dos dispositivos sin conexión a la vez,
  más allá del aviso antes de borrar.
- Reetiquetado retroactivo de la biblioteca existente con metadatos —
  la biblioteca actual del dispositivo se pierde y se reconstruye
  desde cero (decisión de Miguel Ángel, S008); Parte 0 solo afecta a
  descargas hechas a partir de su implementación.
- Sincronización en segundo plano fuera de apertura de app/inicio de
  sesión (WorkManager periódico) — posible iteración futura, no parte
  de este hito.
- Actualización silenciosa/automática de la app sin confirmación
  explícita de Miguel Ángel.
- Publicación en Google Play — fuera de alcance del proyecto entero.

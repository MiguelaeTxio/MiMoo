# MIMOO — ANEXO HITO 07
# Sincronización entre Dispositivos + Actualizaciones In-App

*Vive en `DOCS/ANNEX_H07.md` — flujo NewFlow Android. Ver estado en
`DOCS/MASTER_DOCUMENT.md`. EN PROGRESO.*

---

## NOTA DE APERTURA (S007, 2026-07-10)

Abierto a petición explícita de Miguel Ángel justo después de resolver
el bloqueo de H06 (ver `DOCS/ANNEX_H06.md`) y confirmar "Exportar a
Drive" funcionando en dispositivo real. Dos funciones distintas
comparten hito por haber surgido de la misma conversación, no porque
dependan técnicamente una de otra — se pueden implementar en cualquier
orden:

1. **Sincronización incremental vía Drive** entre dispositivos.
2. **Comprobación y descarga de actualizaciones de la app** desde
   dentro de la propia app.

**Explícitamente independiente de H06**: H06 (exportar/importar
destructivo) no se toca ni se sustituye — sigue siendo la vía para
"quiero este dispositivo exactamente igual que el backup, sin importar
lo que tenía antes" (dispositivo nuevo, restauración tras
reinstalación). H07 añade una vía **adicional**, no destructiva, para
"quiero traer a este dispositivo lo que le falta, sin perder lo que ya
tiene".

---

## OBJETIVO DEL HITO

### Parte 1 — Sincronización incremental

Hoy (H06) importar desde Drive es siempre destructivo:
`BackupImportRepository.importDestructively()` borra las 4 tablas
Room y los archivos SAF del dispositivo destino antes de reinsertar
todo el contenido del `BackupBundle`. Sirve bien para "dispositivo
nuevo, sin nada todavía", pero es la herramienta equivocada para "la
tablet ya tiene descargas propias y solo quiero traerle las pistas
nuevas que añadí desde el móvil" — con la vía actual, sincronizar así
implica perder cualquier cosa exclusiva de la tablet que no esté en el
backup del móvil.

H07 añade un segundo modo de importación, **aditivo**: comparar el
`BackupBundle` descargado de Drive contra el estado local del
dispositivo, y solo insertar/encolar lo que no existe ya localmente —
sin borrar nada.

### Parte 2 — Actualizaciones in-app

Hoy Miguel Ángel se entera de que hay una APK nueva y la instala a
mano (ver skill `android-deploy`: descarga por sftp desde
PythonAnywhere, sin URL pública). H07 añade, dentro de la propia app,
una forma de comprobar si hay una versión más reciente que la
instalada y descargarla sin salir de MiMoo.

---

## CONTEXTO TÉCNICO — LO QUE YA EXISTE (leído del clon, S007)

### Esquema de datos (mismo que H06, sin cambios de esquema previstos)

Las 4 entidades Room de siempre — `SearchResultTrack` (PK
`youtubeId`), `FavoriteAlbum` (PK compuesta `artist`+`album`),
`Playlist` (PK `id` autogenerado), `PlaylistTrackCrossRef` (PK
compuesta) — ver `DOCS/ANNEX_H06.md`, sección "CONTEXTO TÉCNICO", para
el detalle completo de cada campo. H07 reutiliza el mismo
`BackupBundle`/`TrackBackupDto`/`FavoriteAlbumBackupDto`/
`PlaylistBackupDto` de `BackupDto.kt` — no hace falta un formato de
archivo nuevo, la sincronización lee el mismo backup que ya genera
`BackupRepository`.

### Pieza existente a extender, no a duplicar

- `BackupImportRepository.importDestructively(bundle: BackupBundle): BackupImportResult`
  — el modo destructivo de H06, se queda tal cual, intacto.
- El nuevo modo aditivo debería vivir en la misma clase, como un
  método hermano (p.ej. `importIncrementally(bundle: BackupBundle): BackupImportResult`
  o nombre que se decida en su momento), para compartir las funciones
  de mapeo `TrackBackupDto.toEntity()` etc. que ya existen — no
  reescribirlas.
- `SettingsScreen`/`SettingsViewModel` (H06 PASO 3-4) ganan una tercera
  acción junto a "Exportar a Drive"/"Importar desde Drive": algo como
  "Sincronizar con Drive" — mismo flujo de autorización
  (`DriveAuthorizationHelper`, ya funcionando) y de descarga del
  archivo (`BackupDriveRepository`), solo cambia qué se hace con el
  `BackupBundle` una vez descargado.

### Prerrequisito de Google Cloud — SIN HACER TODAVÍA

Añadir `silviaytxio@gmail.com` como test user en el proyecto
`mimoo-drive` (Google Auth Platform → Audience → Test users → Add
users), para que también pueda autorizar el acceso a Drive con MiMoo
desde su propia cuenta — igual que ya está `nummenor@gmail.com`. No
hace falta ningún otro cambio de configuración: cada dispositivo/
cuenta autoriza su propio acceso a su propio Drive vía el mismo
`AuthorizationClient` ya funcionando, `mimoo-drive` no necesita saber
de antemano cuántos usuarios lo van a usar más allá de la lista de
test users (proyecto en modo Testing, límite 100 test users, sobra de
margen).

---

## HOJA DE RUTA DETALLADA

### PASO 1 — Prerrequisito de Google Cloud

Añadir `silviaytxio@gmail.com` como test user en `mimoo-drive` (ver
CONTEXTO TÉCNICO arriba). Un solo paso, dos minutos, sin código de por
medio.

### PASO 2 — Diseño de la comparación "qué es nuevo"

**Decisión de producto pendiente de confirmar con Miguel Ángel al
empezar esta parte** — plantear antes de escribir código:

- **Pistas (`SearchResultTrack`):** trivial — comparar por
  `youtubeId` (PK real y estable, no depende del dispositivo). Un
  `youtubeId` del bundle que no existe ya en la tabla local es
  "nuevo"; se inserta y se encola su descarga
  (`DownloadQueueManager.enqueue()`, mismo patrón que H06 PASO 5). Un
  `youtubeId` que ya existe localmente se deja intacto — no se
  sobrescribe ni se compara contenido, se asume que el existente es
  igual de válido.
- **Favoritos de álbum (`FavoriteAlbum`):** igual de trivial — PK
  compuesta `artist`+`album`, mismo criterio "no existe → se inserta".
- **Listas de reproducción (`Playlist`+`PlaylistTrackCrossRef`): la
  parte con decisión real pendiente.** El `id` de playlist no es
  estable entre dispositivos (autogenerado por Room en cada uno) — a
  diferencia de H06 donde el destino no tenía playlists previas y el
  remapeo era 1:1 sin ambigüedad, aquí puede haber una playlist con el
  **mismo nombre** en origen y destino, con contenido parcialmente
  distinto. Opciones a decidir con Miguel Ángel, no a asumir:
  1. Emparejar por `name` exacto: si ya existe una playlist con ese
     nombre en destino, añadirle las pistas del bundle que le falten
     (unión); si no existe, crearla entera.
  2. Tratar toda playlist del bundle como nueva salvo que el nombre
     coincida Y el contenido sea idéntico (más conservador, puede
     crear duplicados con sufijo si el contenido difiere).
  3. No sincronizar playlists en esta primera versión de H07 — solo
     pistas sueltas y favoritos de álbum, dejar playlists para una
     iteración posterior si la sincronización simple ya resuelve la
     necesidad real de Miguel Ángel.

### PASO 3 — `BackupImportRepository`: modo aditivo

Implementar el método nuevo siguiendo la decisión del PASO 2. Reglas
ya claras sin necesidad de decisión adicional:

- Nunca tocar `deleteExistingPhysicalFiles()` ni ningún borrado — el
  modo aditivo, por definición, no borra nada.
- Pistas nuevas encoladas para descarga automática, igual que H06
  PASO 5 (mismos metadatos ya corregidos, sin pasar por el diálogo de
  edición).
- Devolver un resultado que distinga "cuántas eran nuevas" de "cuántas
  ya existían" (por ejemplo extendiendo `BackupImportResult` o un tipo
  hermano) — la UI necesita poder decir "3 pistas nuevas importadas,
  47 ya las tenías" en vez de un genérico "sincronización completada".

### PASO 4 — UI: "Sincronizar con Drive"

Tercera acción en `SettingsScreen` (H06 PASO 3), junto a Exportar/
Importar. Mismo flujo de autorización y descarga de archivo que ya
funciona; el diálogo de confirmación debe dejar claro que esta acción
**no borra nada** (a diferencia de "Importar desde Drive", que sí es
destructivo y ya tiene su propio aviso desde H06 PASO 4) — evitar que
Miguel Ángel confunda los dos botones por descuido.

### PASO 5 — Verificación funcional end-to-end

Con dos dispositivos reales con contenido parcialmente distinto:
sincronizar y confirmar (a) nada del contenido previo del destino se
pierde, (b) las pistas realmente nuevas aparecen y se descargan solas,
(c) las pistas ya presentes no se duplican ni se re-encolan, (d) el
comportamiento de playlists coincide con lo decidido en el PASO 2.

---

## PARTE 2 — ACTUALIZACIONES IN-APP

### PASO A — Decisión de infraestructura de publicación (pendiente, dos opciones)

Hoy la APK vive en PythonAnywhere sin URL pública (ver skill
`android-deploy`: descarga solo por sftp, deliberadamente sin exponer
nada vía HTTP — decisión previa documentada para no mezclar MiMoo con
el proyecto Django de EnterpriseBot). Para que la propia app pueda
comprobar/descargar una versión nueva hace falta **algún** endpoint
HTTP público — dos caminos razonables, a decidir con Miguel Ángel
antes de tocar el workflow, ninguno asumido de antemano:

1. **Servir la APK + un manifiesto de versión desde PythonAnywhere**,
   como una excepción puntual y aislada a la decisión de
   `android-deploy` (una web app mínima dedicada solo a esto, sin
   tocar EnterpriseBot) — requiere dar de alta esa web app en
   PythonAnywhere, fuera del alcance de lo que este modelo puede hacer
   por sí solo en NewFlow (no hay acceso a la consola de PythonAnywhere
   en este flujo, solo a la API de subida de archivos vía
   `PA_API_TOKEN`, que no crea web apps).
2. **GitHub Releases** del propio repo: el workflow, tras compilar,
   adjunta el `.apk` como asset de una release nueva (usando el token
   de sesión de NewFlow o, mejor, el `GITHUB_TOKEN` automático que ya
   tiene GitHub Actions sin necesidad de ningún secret nuevo). Da una
   URL pública estable por versión de forma nativa, sin infraestructura
   adicional que mantener. **Matiz a resolver:** si el repositorio
   `MiMoo` es privado, la URL de descarga de un asset de Release
   también exige autenticación para un `GET` normal — la app tendría
   que llevar embebido algún token de lectura para poder descargar,
   lo cual es un secreto de más alcance (acceso a la cuenta de GitHub)
   que el Client ID OAuth de Drive que ya se embebe hoy sin problema.
   Antes de elegir esta vía, confirmar si el repo es público o
   privado, y si privado, si Miguel Ángel está cómodo embebiendo un
   token de solo lectura con alcance mínimo (`contents:read`) en el
   APK.

### PASO B — Manifiesto de versión

Sea cual sea la vía del PASO A, generar en cada build (mismo workflow,
tras `assembleDebug`) un JSON pequeño con al menos:
`{"versionCode": N, "versionName": "X.Y", "apkUrl": "..."}` —
`versionCode` viene del mismo `-PversionCode=${{ github.run_number }}`
que ya recibe Gradle hoy (ver `build-and-deploy.yml`), no hace falta
inventar un contador nuevo.

### PASO C — Comprobación desde la app

Nuevo repositorio (p.ej. `AppUpdateRepository`) que haga un `GET`
simple al manifiesto (sin autenticación si PASO A resuelve en
PythonAnywhere/repo público; con el token de solo lectura si acaba
siendo un repo privado vía GitHub Releases) y compare
`versionCode` contra `BuildConfig.VERSION_CODE` (ya existe, lo genera
el propio Gradle a partir de `defaultConfig.versionCode` — ver
`android-deploy`, sección "Versioning").

### PASO D — UI y descarga

Entrada en "Ajustes" (mismo `SettingsScreen` de H06/H07-Parte-1):
"Buscar actualizaciones", muestra versión actual vs. disponible, botón
de descarga. Para la instalación en sí: como MiMoo no está en Google
Play, Android exige el flujo estándar de "fuentes desconocidas" con
`FileProvider` + `Intent(ACTION_VIEW)` apuntando al APK descargado —
patrón conocido de Android, sin necesitar ninguna API adicional más
allá de lo que ya usa el proyecto (Chaquopy/yt-dlp también descargan
archivos por HTTP hoy, mismo tipo de operación de red).

### PASO E — Verificación funcional

Publicar una versión de prueba con `versionCode` mayor, confirmar que
la app la detecta, la descarga, y el flujo de instalación de Android
la reconoce como actualización válida sobre la instalada (mismo
`applicationId`, mismo criterio que ya usa la instalación manual por
sftp).

---

## Fuera de Alcance de Este Hito (explícitamente pospuesto)

- Sincronización automática en segundo plano (WorkManager periódico)
  — esta primera versión es manual, a petición explícita del usuario
  desde "Ajustes". Automatizarlo es una posible iteración futura, no
  parte de H07.
- Resolución de conflictos más allá de "no existe → se añade" — no
  hay ningún escenario de "existe pero es diferente" contemplado en
  esta primera versión (aplica igual a pistas, favoritos y playlists).
- Actualización silenciosa/automática de la app sin acción del
  usuario — el flujo siempre pasa por que Miguel Ángel vea la
  comprobación y pulse descargar/instalar él mismo.
- Publicación en Google Play — fuera de alcance del proyecto entero,
  no solo de este hito (MiMoo es una app personal, no destinada a
  distribución pública).

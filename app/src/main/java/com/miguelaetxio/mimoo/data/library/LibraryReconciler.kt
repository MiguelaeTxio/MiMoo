package com.miguelaetxio.mimoo.data.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fallback label for albums with no name — matches the "Sencillos"
 * convention used by DownloadDirManager for the on-disk folder.
 * ---
 * Etiqueta de respaldo para álbumes sin nombre — coincide con la
 * convención "Sencillos" que usa DownloadDirManager para la carpeta
 * en disco.
 */
private const val UNKNOWN_ALBUM_LABEL = "Sencillos"

/**
 * Legacy on-disk folder name for "no album", used by
 * DownloadDirManager before it was renamed to "Sencillos". Real
 * files downloaded before that rename still sit in a folder with
 * this literal name — treated as equivalent, not as a real album
 * name, or the same physical content ends up split into two
 * different album groups in the UI depending only on when it was
 * downloaded.
 * ---
 * Nombre de carpeta legacy para "sin álbum", usado por
 * DownloadDirManager antes de renombrarse a "Sencillos". Los
 * archivos reales descargados antes de ese cambio siguen en una
 * carpeta con este nombre literal — se trata como equivalente, no
 * como un nombre de álbum real, o el mismo contenido físico acaba
 * repartido en dos grupos de álbum distintos en la UI solo según
 * cuándo se descargó.
 */
private const val LEGACY_UNKNOWN_ALBUM_DIR_NAME = "_sin_album"

private val NULL_ALBUM_FOLDER_NAMES = setOf(
    UNKNOWN_ALBUM_LABEL,
    LEGACY_UNKNOWN_ALBUM_DIR_NAME,
)

/**
 * Extensiones de audio aceptadas por rescan() -- no solo ".opus"
 * (formato que usa el propio descargador de MiMoo), también los
 * formatos habituales de archivos copiados a mano desde un PC. Bug
 * real reportado por Miguel Ángel (2026-07-04): pistas de Beethoven en
 * .mp3/.flac ignoradas por completo porque el filtro anterior solo
 * aceptaba ".opus".
 * ---
 * Audio extensions accepted by rescan() -- not just ".opus" (the
 * format MiMoo's own downloader uses), also the common formats for
 * files copied by hand from a PC. Real bug reported by Miguel Ángel
 * (2026-07-04): Beethoven tracks in .mp3/.flac completely ignored
 * because the previous filter only accepted ".opus".
 */
private val AUDIO_EXTENSIONS = setOf(
    "opus", "mp3", "m4a", "flac", "ogg", "wav", "aac", "wma",
)

/**
 * Fallback artist label for an audio file found directly at the SAF
 * root (no artist folder at all) -- an edge case, but one Miguel
 * Ángel's manually-copied folders could hit if a file was ever placed
 * loose at the root.
 * ---
 * Etiqueta de artista de respaldo para un archivo de audio encontrado
 * directamente en la raíz SAF (sin ninguna carpeta de artista) -- un
 * caso borde, pero uno que las carpetas copiadas a mano por Miguel
 * Ángel podrían tocar si algún archivo quedó suelto en la raíz.
 */
private const val UNKNOWN_ARTIST_FOLDER_LABEL = "Desconocido"

/**
 * Clave de metadato Vorbis Comment que embebe downloader.py en cada
 * .opus descargado desde S008 (H07 PARTE 0) -- ver
 * downloader.py::MIMOO_YOUTUBE_ID_TAG, debe coincidir literalmente
 * con esa constante.
 * ---
 * Vorbis Comment metadata key that downloader.py embeds in every
 * downloaded .opus since S008 (H07 PART 0) -- see
 * downloader.py::MIMOO_YOUTUBE_ID_TAG, must match that constant
 * literally.
 */
private const val EMBEDDED_YOUTUBE_ID_TAG = "MIMOO_YOUTUBE_ID"

/** youtubeId real de YouTube: siempre 11 caracteres de este alfabeto. */
private val YOUTUBE_ID_PATTERN = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * Resultado de mergeDuplicateArtistFolders(), para que
 * LibraryViewModel pueda mostrarle a Miguel Ángel un resumen concreto
 * de lo que se hizo.
 * ---
 * Result of mergeDuplicateArtistFolders(), so LibraryViewModel can
 * show Miguel Ángel a concrete summary of what happened.
 */
data class DuplicateMergeResult(
    val foldersMerged: Int,
    val filesMoved: Int,
    val conflicts: Int,
)

/**
 * Resultado de rescan(), para poder avisar a Miguel Ángel al arrancar
 * la app de qué se ha encontrado/limpiado (petición explícita,
 * 2026-07-04): carpetas vacías borradas y pistas nuevas descubiertas
 * en disco sin fila en Room.
 * ---
 * Result of rescan(), so Miguel Ángel can be notified at app startup
 * of what was found/cleaned up (explicit request, 2026-07-04): empty
 * folders deleted and new tracks discovered on disk with no Room row.
 */
data class RescanResult(
    val emptyFoldersRemoved: Int,
    val junkFilesRemoved: Int,
    val tracksDiscovered: Int,
)

/**
 * Reconciles the SAF storage folder against Room (PASO 10, H03): any
 * {Artista}/{Álbum}/Título.opus file with no matching filePath in the
 * database is registered as a new, synthetic entry. Recovers from
 * Room data loss (e.g. an app uninstall, which wipes internal storage
 * but not the external SAF folder) without touching the physical
 * files at all.
 *
 * Called automatically on EVERY app startup where a storage folder is
 * already chosen (MainActivity.onCreate(), 2026-07-04 -- previously
 * only ran once, the first time the folder was picked), plus on
 * demand via LibraryViewModel's manual refresh button.
 * ---
 * Reconcilia la carpeta de almacenamiento SAF contra Room (PASO 10,
 * H03): cualquier archivo {Artista}/{Álbum}/Título.opus sin filePath
 * correspondiente en la base de datos se registra como una entrada
 * nueva sintética. Recupera de una pérdida de datos de Room (p.ej.
 * una desinstalación, que borra el almacenamiento interno pero no la
 * carpeta SAF externa) sin tocar los archivos físicos.
 *
 * Se llama automáticamente en CADA arranque de la app donde ya hay
 * carpeta elegida (MainActivity.onCreate(), 2026-07-04 -- antes solo
 * corría una vez, la primera vez que se elegía la carpeta), además de
 * bajo demanda vía el botón de refresco manual de LibraryViewModel.
 */
@Singleton
class LibraryReconciler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SearchResultTrackRepository,
    private val storageManager: StorageManager,
) {
    companion object {
        private const val TAG = "MiMoo-Reconciler"

        /**
         * Mínimo de pistas ausentes para que la salvaguarda de cordura
         * de `verifyDiskState()` se plantee siquiera actuar. Por debajo
         * de esto, un borrado real es perfectamente plausible y se
         * trata como tal.
         */
        private const val BULK_MISSING_FLOOR = 10

        /**
         * Fracción de la biblioteca por encima de la cual una
         * desaparición simultánea se considera problema de acceso al
         * volumen y no borrado del usuario. Un cuarto de la biblioteca
         * evaporándose entre dos sincronizaciones no es algo que haga
         * nadie a mano.
         */
        private const val BULK_MISSING_FRACTION = 0.25

        /**
         * Public so callers (e.g. LibraryViewModel.deleteDownload)
         * can tell synthetic rows apart from real, search-originated
         * ones without duplicating this literal.
         * ---
         * Público para que quien lo use (p.ej.
         * LibraryViewModel.deleteDownload) pueda distinguir filas
         * sintéticas de filas reales originadas de una búsqueda sin
         * duplicar este literal.
         */
        const val LOCAL_ID_PREFIX = "local:"
    }
    suspend fun rescan(rootUri: Uri): RescanResult = withContext(Dispatchers.IO) {
        // withContext(Dispatchers.IO) -- bug real reportado por Miguel
        // Ángel (2026-07-05): "cuando intento borrar una carpeta... no
        // responde". Causa raíz confirmada: NINGUNA operación de
        // archivo de este reconciliador cambiaba de dispatcher, así
        // que el recorrido recursivo completo del árbol SAF (llamadas
        // bloqueantes al content provider) corría en el hilo
        // principal -- con una biblioteca pequeña no se nota, pero
        // cuanto más crece más fácil es que bloquee la interfaz por
        // completo (ANR), sin dejar ninguna excepción que capturar en
        // crash_log.txt/debug_error.txt (de ahí que los logs no
        // coincidieran con el momento del bloqueo).
        // ---
        // withContext(Dispatchers.IO) -- real bug reported by Miguel
        // Ángel (2026-07-05): "when I try to delete a folder... it
        // doesn't respond". Confirmed root cause: NO file operation in
        // this reconciler ever switched dispatcher, so the entire
        // recursive SAF tree walk (blocking calls to the content
        // provider) ran on the main thread -- unnoticeable with a
        // small library, but increasingly likely to freeze the UI
        // completely (ANR) as it grows, with no exception left to
        // catch in crash_log.txt/debug_error.txt (which is why those
        // logs never matched the moment of the freeze).
        val root = DocumentFile.fromTreeUri(context, rootUri)
            ?: return@withContext RescanResult(0, 0, 0)

        // ─────────────────────────────────────────────────────────────
        // PUERTA DE SEGURIDAD S022 -- las dos limpiezas de abajo son
        // DESTRUCTIVAS y corren en cada sincronización automática, no
        // solo al arrancar.
        //
        // Fallo real de Miguel Ángel: tras trasladar la biblioteca a
        // una tarjeta externa, *"parece que al entrar en descargas se
        // borró el directorio"*. Mecanismo: DocumentFile.listFiles()
        // devuelve un ARRAY VACÍO cuando el proveedor SAF falla -- no
        // lanza. Así que pruneEmptyFolders() no podía distinguir "esta
        // carpeta está vacía" de "no he podido leer esta carpeta", y
        // ante ambas hacía lo mismo: borrarla. Con la tarjeta a medio
        // montar, las carpetas de artista/álbum listan vacío y se
        // borran CON su música dentro.
        //
        // Nada destructivo se ejecuta si la raíz no responde con
        // garantías, o si Room dice que hay biblioteca y la raíz no la
        // ve. Saltarse una limpieza cosmética no cuesta nada; borrar
        // álbumes enteros sí.
        // ─────────────────────────────────────────────────────────────
        val rootReadable = try {
            root.exists() && root.canRead()
        } catch (e: Exception) {
            false
        }
        val rootChildren = if (rootReadable) {
            try {
                root.listFiles()
            } catch (e: Exception) {
                emptyArray()
            }
        } else {
            emptyArray()
        }
        val roomExpectsFiles = repository.getAll().first().count {
            it.downloadStatus == DownloadStatus.DONE && it.filePath != null
        } > 0

        val safeToPrune = when {
            !rootReadable -> {
                Log.w(TAG, "rescan(): limpieza omitida, la raíz no responde")
                false
            }
            // Room dice que hay biblioteca descargada y la raíz no ve
            // ni un solo hijo: es un volumen ausente, no una carpeta
            // que se haya quedado vacía de verdad.
            roomExpectsFiles && rootChildren.isEmpty() -> {
                Log.w(
                    TAG,
                    "rescan(): limpieza omitida, Room espera archivos pero la raíz " +
                        "no lista ninguno (¿tarjeta no montada?)",
                )
                false
            }
            else -> true
        }

        val junkFilesRemoved = if (safeToPrune) pruneJunkFiles(root, isRoot = true) else 0
        val emptyFoldersRemoved = if (safeToPrune) pruneEmptyFolders(root) else 0

        // Carpetas vacías primero (petición explícita de Miguel Ángel,
        // 2026-07-04, tras encontrar carpetas de artista completamente
        // vacías en disco -- restos de descargas movidas/fusionadas en
        // sesiones anteriores a este fix -- que nunca se limpiaban
        // solas). Nunca borra rootUri en sí, solo sus subcarpetas,
        // recursivamente y de abajo hacia arriba.
        // ---
        // Empty folders first (explicit request from Miguel Ángel,
        // 2026-07-04, after finding completely empty artist folders on
        // disk -- leftovers from downloads moved/merged in sessions
        // before this fix -- that never cleaned themselves up). Never
        // deletes rootUri itself, only its subfolders, recursively and
        // bottom-up.
        // Archivos no musicales dentro de subcarpetas primero (petición
        // explícita de Miguel Ángel, 2026-07-04: "una carpeta con
        // archivos que no son cosas recuperables por MiMoo, en el
        // primer arranque, MiMoo tiene que borrarlas"). Nunca toca la
        // raíz -- ahí SÍ viven a propósito crash_log.txt/
        // debug_error.txt (logs de diagnóstico deliberados, no restos
        // de descargas). Se hace antes que pruneEmptyFolders() para
        // que una carpeta que se quede vacía tras esto también se
        // borre en el mismo pase.
        // ---
        // Non-audio files inside subfolders first (explicit request
        // from Miguel Ángel, 2026-07-04: "a folder with files that
        // aren't recoverable by MiMoo, on first startup, MiMoo has to
        // delete them"). Never touches the root -- that's where
        // crash_log.txt/debug_error.txt deliberately live (intentional
        // diagnostic logs, not download leftovers). Done before
        // pruneEmptyFolders() so a folder left empty by this also gets
        // deleted in the same pass.
        // pruneEmptyFolders() se hace después de pruneJunkFiles() para
        // que una carpeta que se quede vacía tras esa limpieza también
        // se borre en el mismo pase -- las dos, solo si `safeToPrune`.

        // Only real, search-originated rows are protected from being
        // touched again. Synthetic rows (our own, from a previous
        // rescan) are always recomputed and upserted below, so fixes
        // to this reconciler's own logic (e.g. album-name mapping)
        // self-heal on the next manual refresh instead of leaving a
        // stale, wrong row behind forever.
        // ---
        // Solo las filas reales, originadas de una búsqueda, están
        // protegidas de volver a tocarse. Las filas sintéticas
        // (propias, de un rescan anterior) siempre se recalculan y
        // se sobrescriben abajo, así que las correcciones a la
        // lógica de este reconciliador (p.ej. el mapeo de nombres de
        // álbum) se autocorrigen en el siguiente refresco manual en
        // vez de dejar una fila obsoleta y equivocada para siempre.
        val allRows = repository.getAll().first()
        val knownRealPaths = allRows
            .filter { !it.youtubeId.startsWith(LOCAL_ID_PREFIX) }
            .mapNotNull { it.filePath }
            .toSet()
        // Existing synthetic rows, keyed by filePath, so a re-scan
        // preserves isFavorite instead of resetting it to false every
        // time (REPLACE overwrites the whole row on conflict).
        // ---
        // Filas sintéticas existentes, indexadas por filePath, para
        // que un re-escaneo conserve isFavorite en vez de resetearlo
        // a false cada vez (REPLACE sobrescribe la fila entera en
        // caso de conflicto).
        val existingSyntheticByPath = allRows
            .filter { it.youtubeId.startsWith(LOCAL_ID_PREFIX) }
            .associateBy { it.filePath }

        val discovered = mutableListOf<SearchResultTrack>()
        collectAudioFiles(
            dir = root,
            pathNames = emptyList(),
            discovered = discovered,
            knownRealPaths = knownRealPaths,
            existingSyntheticByPath = existingSyntheticByPath,
        )

        if (discovered.isNotEmpty()) {
            repository.cacheSearchResults(discovered)
        }

        RescanResult(emptyFoldersRemoved, junkFilesRemoved, discovered.size)
    }

    /**
     * H07 PARTE 1 -- fallo real señalado por Miguel Ángel: todo el
     * diseño de sincronización compara base de datos (local) contra
     * base de datos (Drive), pero en ningún punto comprueba que lo
     * que Room dice "descargado" (`DownloadStatus.DONE` con
     * `filePath`) siga existiendo de verdad en disco. `rescan()` de
     * arriba solo cubre la dirección contraria (archivo huérfano en
     * disco → fila nueva en Room); esta función cubre la que faltaba.
     *
     * Devuelve las pistas cuyo archivo ya no está, ya marcadas de
     * vuelta como `PENDING` (mismo mecanismo que
     * `SearchResultTrackRepository.clearDownload()`) -- este
     * reconciliador nunca encola descargas él mismo (igual que
     * `rescan()` nunca descarga los archivos que descubre), eso es
     * responsabilidad de quien llama.
     * ---
     * H07 PART 1 -- real gap flagged by Miguel Ángel: the whole sync
     * design compares database (local) against database (Drive), but
     * at no point checks whether what Room calls "downloaded"
     * (`DownloadStatus.DONE` with `filePath`) still actually exists on
     * disk. `rescan()` above only covers the opposite direction
     * (orphaned file on disk → new Room row); this function covers the
     * one that was missing.
     *
     * Returns the tracks whose file is gone, already marked back as
     * `PENDING` (same mechanism as
     * `SearchResultTrackRepository.clearDownload()`) -- this
     * reconciler never queues downloads itself (same as `rescan()`
     * never downloads the files it discovers), that's the caller's
     * responsibility.
     */
    suspend fun verifyDiskState(): List<SearchResultTrack> = withContext(Dispatchers.IO) {
        // ─────────────────────────────────────────────────────────────
        // SALVAGUARDAS S022 -- fallo real reportado por Miguel Ángel:
        // *"al cambiar de carpeta, el guardián detecta el cambio de
        // contenidos y lo que hace es volver a descargar todo de
        // nuevo"*.
        //
        // Esta función marcaba PENDING toda fila DONE cuyo archivo no
        // respondiera exists(), y AutoSyncViewModel.verifyDiskAndReconcile()
        // encola inmediatamente cada una. El problema es que exists()
        // devuelve false por DOS motivos que aquí eran indistinguibles:
        //
        //   a) el archivo se borró de verdad  -> reencolar es correcto
        //   b) el volumen no está accesible   -> reencolar es un desastre
        //
        // Con la biblioteca en memoria interna (b) no pasaba nunca. Con
        // la biblioteca en una tarjeta externa (H14), basta con que la
        // tarjeta no esté montada todavía cuando corre la sincronización
        // para que las 700 y pico pistas respondan false a la vez y se
        // redescarguen enteras. Lo mismo durante un traslado en curso.
        //
        // Ninguna de las tres salvaguardas de abajo toca Room: ante la
        // duda, no se hace nada. Una verificación que se salta un ciclo
        // no cuesta nada; una redescarga masiva sí.
        // ─────────────────────────────────────────────────────────────

        // 1. Nunca mientras se está trasladando la biblioteca: durante
        //    el traslado hay archivos legítimamente en tránsito.
        if (LibraryMigrator.isMigrating) {
            Log.w(TAG, "verifyDiskState() omitido: traslado de biblioteca en curso")
            return@withContext emptyList()
        }

        // 2. La raíz tiene que responder antes de creer nada de lo que
        //    diga exists() sobre los archivos que cuelgan de ella. Si
        //    la tarjeta no está montada, la raíz tampoco responde.
        val rootUri = storageManager.getRootUri()
        if (rootUri != null) {
            val rootAvailable = try {
                val root = DocumentFile.fromTreeUri(context, rootUri)
                root != null && root.exists() && root.canRead()
            } catch (e: Exception) {
                false
            }
            if (!rootAvailable) {
                Log.w(
                    TAG,
                    "verifyDiskState() omitido: la carpeta de la biblioteca no " +
                        "responde (¿tarjeta no montada?)",
                )
                return@withContext emptyList()
            }
        }

        val doneTracks = repository.getAll().first().filter {
            it.downloadStatus == DownloadStatus.DONE && it.filePath != null
        }

        val missing = doneTracks.filter { track ->
            val exists = try {
                DocumentFile.fromSingleUri(context, Uri.parse(track.filePath))?.exists() == true
            } catch (e: Exception) {
                false
            }
            !exists
        }

        // 3. Salvaguarda de cordura. Que desaparezca de golpe una
        //    fracción grande de la biblioteca no es un borrado del
        //    usuario: el usuario borra canciones de una en una o un
        //    álbum entero, no media biblioteca a la vez sin tocar la
        //    app. Es un volumen a medio montar, un permiso SAF caído o
        //    un traslado a medias. Se deja Room intacto y se avisa.
        val suspicious = missing.size >= BULK_MISSING_FLOOR &&
            missing.size > doneTracks.size * BULK_MISSING_FRACTION
        if (suspicious) {
            Log.w(
                TAG,
                "verifyDiskState() abortado: ${missing.size} de ${doneTracks.size} " +
                    "pistas sin archivo. Se asume carpeta no disponible, NO se " +
                    "reencola nada.",
            )
            return@withContext emptyList()
        }

        missing.forEach { track -> repository.clearDownload(track.youtubeId) }
        missing
    }

    /**
     * Borra recursivamente cualquier archivo que NO sea de audio
     * (extensión fuera de AUDIO_EXTENSIONS) dentro de una subcarpeta
     * -- nunca en `dir` cuando `isRoot=true` (ahí viven a propósito
     * crash_log.txt/debug_error.txt). Petición explícita de Miguel
     * Ángel (2026-07-04), probada con una "carpeta fantasma" con un
     * archivo ajeno a la música creada a propósito para verificar que
     * esta limpieza la detecta y la borra en el primer arranque.
     * ---
     * Recursively deletes any file that is NOT audio (extension
     * outside AUDIO_EXTENSIONS) inside a subfolder -- never in `dir`
     * when `isRoot=true` (that's where crash_log.txt/debug_error.txt
     * deliberately live). Explicit request from Miguel Ángel
     * (2026-07-04), tested with a "phantom folder" holding an
     * unrelated file created on purpose to verify this cleanup detects
     * and deletes it on first startup.
     */
    private fun pruneJunkFiles(dir: DocumentFile, isRoot: Boolean): Int {
        var removed = 0
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            return removed
        }
        children.forEach { child ->
            try {
                if (child.isDirectory) {
                    removed += pruneJunkFiles(child, isRoot = false)
                } else if (child.isFile && !isRoot) {
                    val extension = child.name
                        ?.substringAfterLast('.', "")
                        ?.lowercase()
                    if (extension == null || extension !in AUDIO_EXTENSIONS) {
                        child.delete()
                        removed++
                    }
                }
            } catch (e: Exception) {
                // Un fallo puntual con un archivo no debe impedir
                // seguir con sus hermanos.
                // ---
                // A one-off failure with one file must not stop
                // processing its siblings.
            }
        }
        return removed
    }

    /**
     * Borra recursivamente, de abajo hacia arriba, cualquier
     * subcarpeta que quede vacía tras procesar sus propios hijos --
     * nunca borra `dir` en sí (el nivel raíz nunca se toca porque solo
     * se llama a delete() sobre los HIJOS, nunca sobre el parámetro
     * del nivel superior). Bug real reportado por Miguel Ángel
     * (2026-07-04): "Air french Band", "AIRfrenchbandofficial", etc.
     * quedaban como carpetas completamente vacías en disco para
     * siempre tras fusionar/mover su contenido, y tuvo que borrarlas a
     * mano desde el explorador de archivos.
     * ---
     * Recursively, bottom-up, deletes any subfolder left empty after
     * processing its own children -- never deletes `dir` itself (the
     * root level is never touched because delete() is only ever
     * called on CHILDREN, never on the top-level parameter). Real bug
     * reported by Miguel Ángel (2026-07-04): "Air french Band",
     * "AIRfrenchbandofficial", etc. were left as completely empty
     * folders on disk forever after their content was merged/moved,
     * and he had to delete them by hand from the file explorer.
     */
    private fun pruneEmptyFolders(dir: DocumentFile): Int {
        var removed = 0
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            return removed
        }
        children
            .filter { it.isDirectory }
            .forEach { sub ->
                removed += pruneEmptyFolders(sub)
                try {
                    // S022 -- segunda capa de la misma salvaguarda que
                    // la puerta de `rescan()`. listFiles() devuelve un
                    // array vacío ante un fallo del proveedor SAF, así
                    // que "vacía" y "no legible" eran el mismo caso y
                    // los dos acababan en delete(). Se exige ahora que
                    // la carpeta responda de verdad antes de creerse
                    // que está vacía.
                    val readable = sub.exists() && sub.canRead()
                    if (readable && sub.listFiles().isEmpty()) {
                        sub.delete()
                        removed++
                    }
                } catch (e: Exception) {
                    // Un fallo puntual al comprobar/borrar una carpeta
                    // no debe impedir seguir con sus hermanas.
                    // ---
                    // A one-off failure checking/deleting one folder
                    // must not stop processing its siblings.
                }
            }
        return removed
    }

    /**
     * Recorre rootUri recursivamente (no solo 2 niveles fijos
     * {artista}/{álbum}/) y acepta cualquier extensión de
     * AUDIO_EXTENSIONS, no solo .opus. Bug real reportado por Miguel
     * Ángel (2026-07-04): pistas de Beethoven copiadas a mano en el
     * dispositivo (no descargadas vía MiMoo, por tanto en formatos
     * como .mp3/.flac/.m4a, nunca .opus) no aparecían en Biblioteca
     * porque rescan() solo miraba 2 niveles de profundidad y filtraba
     * únicamente por ".opus".
     *
     * artist/album se derivan de la profundidad del archivo respecto
     * a rootUri:
     *   - profundidad 0 (archivo suelto en la raíz): artista
     *     desconocido, sin álbum.
     *   - profundidad 1 (root/Artista/archivo): artista = ese
     *     directorio, sin álbum (sencillo).
     *   - profundidad 2+ (root/Artista/Álbum/.../archivo): artista =
     *     primer nivel, álbum = segundo nivel (los niveles más
     *     profundos, si los hay, se ignoran a efectos de agrupación).
     * ---
     * Walks rootUri recursively (not just a fixed 2 levels
     * {artist}/{album}/) and accepts any extension in
     * AUDIO_EXTENSIONS, not just .opus. Real bug reported by Miguel
     * Ángel (2026-07-04): Beethoven tracks manually copied onto the
     * device (not downloaded via MiMoo, hence in formats like
     * .mp3/.flac/.m4a, never .opus) weren't showing up in Biblioteca
     * because rescan() only looked 2 levels deep and filtered by
     * ".opus" only.
     *
     * artist/album are derived from the file's depth relative to
     * rootUri:
     *   - depth 0 (a loose file at the root): unknown artist, no
     *     album.
     *   - depth 1 (root/Artist/file): artist = that directory, no
     *     album (single).
     *   - depth 2+ (root/Artist/Album/.../file): artist = first
     *     level, album = second level (any deeper levels, if
     *     present, are ignored for grouping purposes).
     */
    private fun collectAudioFiles(
        dir: DocumentFile,
        pathNames: List<String>,
        discovered: MutableList<SearchResultTrack>,
        knownRealPaths: Set<String>,
        existingSyntheticByPath: Map<String?, SearchResultTrack>,
    ) {
        // Bug real reportado por Miguel Ángel (2026-07-04): con la
        // versión anterior, una excepción en CUALQUIER punto del
        // recorrido (un nombre de archivo raro, un proveedor SAF que
        // falla en una carpeta concreta, etc.) abortaba rescan()
        // entero -- y como discovered solo se persiste UNA VEZ al
        // final, se perdía TODO lo encontrado hasta ese momento, no
        // solo lo que venía después. Resultado observado: Biblioteca
        // solo mostraba lo que ya tenía fila real de antes (el álbum
        // "Air" original), y Beethoven/Canal IMAR/DW Classical Music/
        // etc. quedaban invisibles del todo, indefinidamente. Ahora
        // cada hijo (carpeta o archivo) se procesa en su propio
        // try/catch: un problema puntual se salta y se sigue con sus
        // hermanos, en vez de perder el escaneo completo.
        // ---
        // Real bug reported by Miguel Ángel (2026-07-04): in the
        // previous version, an exception at ANY point during the walk
        // (an odd filename, a SAF provider failing on one particular
        // folder, etc.) aborted the entire rescan() -- and since
        // discovered is only persisted ONCE at the end, EVERYTHING
        // found up to that point was lost, not just what came after.
        // Observed result: Biblioteca only showed what already had a
        // real row from before (the original "Air" album), and
        // Beethoven/Canal IMAR/DW Classical Music/etc. were completely
        // invisible, indefinitely. Now every child (folder or file) is
        // processed in its own try/catch: a one-off problem is skipped
        // and its siblings still get processed, instead of losing the
        // whole scan.
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            return
        }

        children.forEach { child ->
            try {
                when {
                    child.isDirectory -> {
                        val childName = child.name ?: return@forEach
                        collectAudioFiles(
                            dir = child,
                            pathNames = pathNames + childName,
                            discovered = discovered,
                            knownRealPaths = knownRealPaths,
                            existingSyntheticByPath = existingSyntheticByPath,
                        )
                    }
                    child.isFile -> {
                        val extension = child.name
                            ?.substringAfterLast('.', "")
                            ?.lowercase()
                        if (extension == null || extension !in AUDIO_EXTENSIONS) {
                            return@forEach
                        }
                        val uriString = child.uri.toString()
                        if (uriString in knownRealPaths) return@forEach

                        val preservedFavorite = existingSyntheticByPath[uriString]
                            ?.isFavorite ?: false
                        val artistName = pathNames.getOrNull(0)
                            ?: UNKNOWN_ARTIST_FOLDER_LABEL
                        val albumName = pathNames.getOrNull(1)

                        discovered.add(
                            buildSyntheticTrack(
                                uriString = uriString,
                                fileName = child.name!!,
                                artistName = artistName,
                                albumName = albumName,
                                isFavorite = preservedFavorite,
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Un hijo problemático no debe tirar abajo el resto
                // del recorrido -- ver comentario de la función.
                // ---
                // One problematic child must not take down the rest
                // of the walk -- see the function's comment.
            }
        }
    }

    /**
     * Elimina las filas sintéticas (LOCAL_ID_PREFIX) cuyo archivo ya no
     * existe en disco -- p.ej. tras mergeDuplicateArtistFolders(), que
     * mueve/borra archivos físicos sin tocar Room directamente. Las
     * filas reales (originadas de una búsqueda) nunca se tocan aquí,
     * igual que en rescan().
     * ---
     * Removes synthetic rows (LOCAL_ID_PREFIX) whose file no longer
     * exists on disk -- e.g. after mergeDuplicateArtistFolders(), which
     * moves/deletes physical files without touching Room directly.
     * Real, search-originated rows are never touched here, same as in
     * rescan().
     */
    suspend fun pruneDeadSyntheticRows() = withContext(Dispatchers.IO) {
        repository.getAll().first()
            .filter { it.youtubeId.startsWith(LOCAL_ID_PREFIX) }
            .forEach { track ->
                val stillExists = track.filePath
                    ?.let { DocumentFile.fromSingleUri(context, Uri.parse(it)) }
                    ?.exists() == true
                if (!stillExists) {
                    repository.delete(track)
                }
            }
    }

    /**
     * Fusiona carpetas de artista duplicadas por la condición de
     * carrera de DownloadDirManager.getOrCreateTrackDir() (ya
     * corregida para descargas futuras, ver ese archivo) -- p.ej.
     * "Air", "Air (1)", "Air (2)"... vuelven a ser una sola carpeta
     * "Air" con todos los álbumes/pistas dentro. Reportado por Miguel
     * Ángel (2026-07-03): Moon Safari repartido en 10 grupos de
     * artista distintos en Biblioteca.
     *
     * Algoritmo, por cada grupo de carpetas con el mismo nombre base
     * (sufijo " (N)" quitado):
     *   1. La carpeta canónica es la que NO tiene sufijo numérico si
     *      existe una así en el grupo; si no, la primera por orden
     *      alfabético (caso borde: la propia "sin sufijo" ya hubiera
     *      colisionado alguna vez).
     *   2. Para cada carpeta duplicada del grupo, cada álbum dentro se
     *      busca/crea en la carpeta canónica, y cada .opus se copia
     *      (con nombre único si ya existe uno igual) y se borra el
     *      original solo si la copia tuvo éxito -- mismo patrón
     *      copy+verify+delete que TrackFileRelocator.relocate().
     *   3. Álbum y carpeta de artista duplicados se borran al quedar
     *      vacíos.
     *
     * No toca Room directamente -- el llamante (LibraryViewModel) debe
     * encadenar pruneDeadSyntheticRows() + rescan() después, para que
     * las filas sintéticas reflejen las rutas nuevas. Las filas reales
     * (de búsqueda) no se ven afectadas por este movimiento de
     * archivos: su artist en Room ya era el nombre canónico desde el
     * principio (el sufijo solo aparecía en el nombre de carpeta real,
     * nunca en el campo Room de una fila real -- ver comentario en
     * DownloadDirManager).
     * ---
     * Merges duplicate artist folders caused by
     * DownloadDirManager.getOrCreateTrackDir()'s race condition
     * (already fixed for future downloads, see that file) -- e.g.
     * "Air", "Air (1)", "Air (2)"... become one "Air" folder again
     * with every album/track inside. Reported by Miguel Ángel
     * (2026-07-03): Moon Safari split across 10 distinct artist groups
     * in Biblioteca.
     *
     * Algorithm, for each group of folders sharing the same base name
     * (with the " (N)" suffix stripped):
     *   1. The canonical folder is the one WITHOUT a numeric suffix if
     *      one exists in the group; otherwise the first one
     *      alphabetically (edge case: even the "no suffix" one could
     *      have collided at some point).
     *   2. For each duplicate folder in the group, every album inside
     *      it is found/created under the canonical folder, and every
     *      .opus is copied (with a unique name if one already exists)
     *      and the original deleted only if the copy succeeded --
     *      same copy+verify+delete pattern as
     *      TrackFileRelocator.relocate().
     *   3. Duplicate album and artist folders are deleted once empty.
     *
     * Does not touch Room directly -- the caller (LibraryViewModel)
     * must chain pruneDeadSyntheticRows() + rescan() afterwards, so
     * synthetic rows reflect the new paths. Real (search-originated)
     * rows are unaffected by this file move: their Room artist field
     * was already the canonical name from the start (the suffix only
     * ever showed up in the real folder name, never in a real row's
     * Room field -- see the comment in DownloadDirManager).
     */
    suspend fun mergeDuplicateArtistFolders(rootUri: Uri): DuplicateMergeResult =
        withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri)
            ?: return@withContext DuplicateMergeResult(0, 0, 0)

        val artistDirs = root.listFiles().filter { it.isDirectory }
        val groups = artistDirs.groupBy { stripDuplicateSuffix(it.name ?: "") }

        var foldersMerged = 0
        var filesMoved = 0
        var conflicts = 0

        groups.values
            .filter { it.size > 1 }
            .forEach { group ->
                val canonical = group.firstOrNull { name(it) == stripDuplicateSuffix(name(it)) }
                    ?: group.sortedBy { name(it) }.first()
                val duplicates = group.filter { it.uri != canonical.uri }

                duplicates.forEach { duplicateArtistDir ->
                    duplicateArtistDir.listFiles()
                        .filter { it.isDirectory }
                        .forEach { duplicateAlbumDir ->
                            val canonicalAlbumDir = canonical
                                .findFile(duplicateAlbumDir.name ?: return@forEach)
                                ?: canonical.createDirectory(duplicateAlbumDir.name!!)
                                ?: return@forEach

                            duplicateAlbumDir.listFiles()
                                .filter {
                                    it.isFile && it.name?.endsWith(".opus") == true
                                }
                                .forEach { file ->
                                    val moved = moveFile(file, canonicalAlbumDir)
                                    if (moved) filesMoved++ else conflicts++
                                }

                            // Borra el álbum duplicado solo si quedó
                            // vacío (algún archivo pudo no copiarse).
                            // ---
                            // Deletes the duplicate album only if it
                            // ended up empty (some file may have
                            // failed to copy).
                            if (duplicateAlbumDir.listFiles().isEmpty()) {
                                duplicateAlbumDir.delete()
                            }
                        }

                    if (duplicateArtistDir.listFiles().isEmpty()) {
                        duplicateArtistDir.delete()
                        foldersMerged++
                    }
                }
            }

        DuplicateMergeResult(foldersMerged, filesMoved, conflicts)
    }

    private fun name(doc: DocumentFile): String = doc.name ?: ""

    /**
     * Quita un sufijo de colisión SAF tipo " (1)", " (12)" del final
     * del nombre, si lo hay.
     * ---
     * Strips a SAF collision suffix like " (1)", " (12)" from the end
     * of the name, if present.
     */
    private fun stripDuplicateSuffix(name: String): String =
        name.replace(Regex(" \\(\\d+\\)$"), "")

    /**
     * Copia un archivo a la carpeta destino (con nombre único si ya
     * existe uno igual) y borra el original solo si la copia tuvo
     * éxito. Devuelve true si se movió, false si hubo que dejar el
     * original intacto por un fallo de copia.
     * ---
     * Copies a file to the target folder (with a unique name if one
     * already exists) and deletes the original only if the copy
     * succeeded. Returns true if it was moved, false if the original
     * had to be left intact due to a copy failure.
     */
    private fun moveFile(source: DocumentFile, targetDir: DocumentFile): Boolean {
        val sourceName = source.name ?: return false
        val targetName = uniqueFileName(targetDir, sourceName)

        val targetDoc = targetDir.createFile("audio/opus", targetName)
            ?: return false

        val copiedOk = try {
            context.contentResolver.openInputStream(source.uri)?.use { input ->
                context.contentResolver.openOutputStream(targetDoc.uri)?.use { output ->
                    input.copyTo(output)
                }
            } != null
        } catch (e: Exception) {
            false
        }

        if (!copiedOk) {
            targetDoc.delete()
            return false
        }

        source.delete()
        return true
    }

    /**
     * Igual que TrackFileRelocator.uniqueFileName() -- añade " (2)",
     * " (3)"... hasta que el nombre quede libre en la carpeta destino,
     * en vez de sobrescribir en silencio.
     * ---
     * Same as TrackFileRelocator.uniqueFileName() -- appends " (2)",
     * " (3)"... until the name is free in the target folder, instead
     * of silently overwriting.
     */
    private fun uniqueFileName(targetDir: DocumentFile, fileName: String): String {
        if (targetDir.findFile(fileName) == null) return fileName
        val base = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        var suffix = 2
        var candidate: String
        do {
            candidate = if (extension.isEmpty()) {
                "$base ($suffix)"
            } else {
                "$base ($suffix).$extension"
            }
            suffix++
        } while (targetDir.findFile(candidate) != null)
        return candidate
    }

    /**
     * "NN - Título.opus" -> trackPosition=NN-1 (0-indexed), título sin
     * el prefijo. Petición explícita de Miguel Ángel (2026-07-05): "en
     * la reconciliación el orden de las canciones cambia... en discos
     * conceptuales como The Wall, eso rompe el concepto de álbum".
     * DownloadWorker (ver KEY_TRACK_POSITION) ya antepone este prefijo
     * al nombre de archivo cuando se conoce la posición, precisamente
     * para que un rescan futuro pueda recuperarla de aquí -- sin este
     * parseo, toda pista reconciliada perdía su trackPosition y
     * Biblioteca caía a orden alfabético (radiofórmula, no álbum).
     * Null si el archivo no lleva el prefijo (descargado antes de este
     * fix, o de otra fuente) -- fallback exactamente al comportamiento
     * de antes, sin regresión.
     * ---
     * "NN - Title.opus" -> trackPosition=NN-1 (0-indexed), title
     * without the prefix. Explicit request from Miguel Ángel
     * (2026-07-05): "on reconciliation the song order changes... on
     * concept albums like The Wall, that breaks the album concept".
     * DownloadWorker (see KEY_TRACK_POSITION) already prepends this
     * prefix to the filename when the position is known, precisely so
     * a future rescan can recover it from here -- without this
     * parsing, every reconciled track lost its trackPosition and
     * Biblioteca fell back to alphabetical order (radio-style, not an
     * album). Null if the file doesn't carry the prefix (downloaded
     * before this fix, or from another source) -- falls back to
     * exactly the previous behavior, no regression.
     */
    private val TRACK_POSITION_PREFIX = Regex("^(\\d{2,3}) - (.+)$")

    private fun buildSyntheticTrack(
        uriString: String,
        fileName: String,
        artistName: String,
        albumName: String?,
        isFavorite: Boolean,
    ): SearchResultTrack {
        // H07 PARTE 0: intenta primero recuperar el youtubeId real
        // embebido en el propio archivo (descargas hechas desde S008
        // en adelante). Solo si no hay tag, o el archivo es anterior
        // al fix / ajeno a MiMoo, se cae al hash local: de siempre --
        // comportamiento idéntico al de antes de este fix.
        // ---
        // H07 PART 0: first tries to recover the real youtubeId
        // embedded in the file itself (downloads made from S008
        // onwards). Only if there's no tag, or the file predates the
        // fix / comes from outside MiMoo, does it fall back to the
        // usual local: hash -- identical behavior to before this fix.
        val embeddedYoutubeId = readEmbeddedYoutubeId(Uri.parse(uriString))

        val resolvedYoutubeId = if (embeddedYoutubeId != null) {
            embeddedYoutubeId
        } else {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(uriString.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(16)
            "$LOCAL_ID_PREFIX$digest"
        }

        val nameWithoutExtension = fileName.substringBeforeLast('.', fileName)
        val prefixMatch = TRACK_POSITION_PREFIX.matchEntire(nameWithoutExtension)
        val trackPosition = prefixMatch?.groupValues?.get(1)?.toIntOrNull()?.let { it - 1 }
        val title = prefixMatch?.groupValues?.get(2) ?: nameWithoutExtension

        return SearchResultTrack(
            youtubeId = resolvedYoutubeId,
            title = title,
            channelTitle = artistName,
            durationSeconds = 0,
            thumbnailUrl = null,
            filePath = uriString,
            downloadStatus = DownloadStatus.DONE,
            artist = artistName,
            album = if (albumName == null || albumName in NULL_ALBUM_FOLDER_NAMES) {
                null
            } else {
                albumName
            },
            isFavorite = isFavorite,
            trackPosition = trackPosition,
        )
    }

    /**
     * Lee el tag Vorbis Comment MIMOO_YOUTUBE_ID directamente del
     * contenedor Ogg/Opus, sin pasar por MediaMetadataRetriever --
     * esa API de Android solo expone un conjunto fijo de claves
     * estándar (álbum, artista, título...) y no da acceso a un tag
     * propio (ver ANNEX_H07.md PARTE 0 para la verificación online que
     * confirmó esto). En vez de parsear la estructura completa de
     * páginas Ogg, aprovecha que el formato de un comentario Vorbis es
     * [longitud LE de 4 bytes]["CLAVE=valor" en UTF-8]: localiza el
     * texto de la clave en los primeros bytes del archivo (el paquete
     * OpusTags está garantizado en la segunda página Ogg, muy cerca
     * del principio) y usa la longitud declarada justo antes para
     * extraer el valor exacto, sin depender de caracteres de corte.
     * Devuelve null ante cualquier fallo o si el tag no está presente
     * -- nunca lanza, para que un archivo problemático no tire abajo
     * el resto del rescan (mismo criterio que collectAudioFiles()).
     * ---
     * Reads the MIMOO_YOUTUBE_ID Vorbis Comment tag directly from the
     * Ogg/Opus container, without going through MediaMetadataRetriever
     * -- that Android API only exposes a fixed set of standard keys
     * (album, artist, title...) and gives no access to a custom tag
     * (see ANNEX_H07.md PARTE 0 for the online check that confirmed
     * this). Instead of parsing the full Ogg page structure, it
     * leverages the fact that a Vorbis comment's framing is [4-byte LE
     * length]["KEY=value" UTF-8]: it locates the key's text in the
     * file's first bytes (the OpusTags packet is guaranteed to be in
     * the second Ogg page, very close to the start) and uses the
     * length declared right before it to extract the exact value,
     * without relying on any cutoff character. Returns null on any
     * failure or if the tag isn't present -- never throws, so one
     * problematic file doesn't take down the rest of the rescan (same
     * criterion as collectAudioFiles()).
     */
    private fun readEmbeddedYoutubeId(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(65536)
                var totalRead = 0
                while (totalRead < buffer.size) {
                    val n = input.read(buffer, totalRead, buffer.size - totalRead)
                    if (n <= 0) break
                    totalRead += n
                }

                val marker = "$EMBEDDED_YOUTUBE_ID_TAG=".toByteArray(Charsets.US_ASCII)
                val markerIndex = indexOfBytes(buffer, totalRead, marker)
                if (markerIndex < 4) return@use null

                // Longitud LE de 4 bytes justo antes del texto de la
                // clave -- ver comentario de la función.
                val declaredLength = (buffer[markerIndex - 4].toInt() and 0xFF) or
                    ((buffer[markerIndex - 3].toInt() and 0xFF) shl 8) or
                    ((buffer[markerIndex - 2].toInt() and 0xFF) shl 16) or
                    ((buffer[markerIndex - 1].toInt() and 0xFF) shl 24)

                val valueLength = declaredLength - marker.size
                if (valueLength <= 0 || valueLength > 32 ||
                    markerIndex + marker.size + valueLength > totalRead
                ) {
                    return@use null
                }

                val value = String(
                    buffer,
                    markerIndex + marker.size,
                    valueLength,
                    Charsets.UTF_8,
                )
                value.takeIf { YOUTUBE_ID_PATTERN.matches(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Búsqueda simple de subarray de bytes -- sin dependencias externas. */
    private fun indexOfBytes(haystack: ByteArray, haystackLength: Int, needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > haystackLength) return -1
        outer@ for (i in 0..(haystackLength - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}

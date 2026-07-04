package com.miguelaetxio.mimoo.data.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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
) {
    companion object {
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
    suspend fun rescan(rootUri: Uri): RescanResult {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return RescanResult(0, 0)

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
        val emptyFoldersRemoved = pruneEmptyFolders(root)

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

        return RescanResult(emptyFoldersRemoved, discovered.size)
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
                    if (sub.listFiles().isEmpty()) {
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
    suspend fun pruneDeadSyntheticRows() {
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
    suspend fun mergeDuplicateArtistFolders(rootUri: Uri): DuplicateMergeResult {
        val root = DocumentFile.fromTreeUri(context, rootUri)
            ?: return DuplicateMergeResult(0, 0, 0)

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

        return DuplicateMergeResult(foldersMerged, filesMoved, conflicts)
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

    private fun buildSyntheticTrack(
        uriString: String,
        fileName: String,
        artistName: String,
        albumName: String?,
        isFavorite: Boolean,
    ): SearchResultTrack {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uriString.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

        return SearchResultTrack(
            youtubeId = "$LOCAL_ID_PREFIX$digest",
            title = fileName.substringBeforeLast('.', fileName),
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
        )
    }
}

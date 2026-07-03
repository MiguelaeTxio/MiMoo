package com.miguelaetxio.mimoo.data.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Resolves and creates the SAF directory tree used to store downloaded
 * audio files under the user-chosen root Uri.
 *
 * Structure: {rootUri}/{artist}/{album}/
 *
 * sanitize() is kept for FAT32-safe segment names.
 * ---
 * Resuelve y crea el arbol de directorios SAF para almacenar archivos
 * de audio descargados bajo el Uri raiz elegido por el usuario.
 *
 * Estructura: {rootUri}/{artista}/{album}/
 *
 * sanitize() se conserva para nombres de segmento seguros en FAT32.
 */
object DownloadDirManager {

    private const val UNKNOWN_ALBUM_DIR_NAME = "Sencillos"

    /**
     * Forbidden characters in FAT32 filesystems, plus control chars.
     * ---
     * Caracteres prohibidos en FAT32, mas caracteres de control.
     */
    private val FORBIDDEN_CHARS_REGEX = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")

    /**
     * Sanitizes a name so it is safe as a FAT32 directory segment.
     * ---
     * Sanitiza un nombre para que sea seguro como segmento FAT32.
     */
    fun sanitize(name: String, fallback: String = "_"): String {
        val cleaned = name
            .replace(FORBIDDEN_CHARS_REGEX, "_")
            .trim()
            .trimEnd('.', ' ')
        return cleaned.ifBlank { fallback }
    }

    /**
     * Returns (creating if needed) the DocumentFile directory for the
     * given artist/album pair under rootUri. Returns null if the root
     * Uri is invalid or the directory cannot be created.
     * ---
     * Devuelve (creando si es necesario) el DocumentFile directorio
     * para el par artista/album bajo rootUri. Devuelve null si el Uri
     * raiz no es valido o el directorio no puede crearse.
     *
     * @param context   Application context for ContentResolver access.
     * @param rootUri   SAF tree Uri returned by OpenDocumentTree.
     * @param artist    Artist name (will be sanitized).
     * @param album     Album name (will be sanitized); null -> "Sencillos".
     */
    fun getOrCreateTrackDir(
        context: Context,
        rootUri: Uri,
        artist: String,
        album: String?,
    ): DocumentFile? = synchronized(this) {
        // synchronized(this) -- bug real reportado por Miguel Ángel
        // (2026-07-03, reimportación de Moon Safari): findOrCreate()
        // hace un find-then-create no atómico (TOCTOU). Cuando varias
        // pistas del mismo álbum descargan en paralelo (varios
        // DownloadWorker concurrentes, más probable aún tras el fix de
        // reconcileOrphanedDownloads(), que reencola de golpe todo lo
        // huérfano al arrancar), dos workers pueden comprobar a la vez
        // que la carpeta "Air"/"Air french Band" no existe todavía y
        // los dos llamar a createDirectory() -- el proveedor SAF
        // resuelve el choque renombrando la segunda a "Air (1)",
        // "Air (2)"... creando carpetas duplicadas físicas en disco
        // que LibraryReconciler.rescan() importa después como
        // artistas/álbumes distintos (visible en Biblioteca como
        // grupos "Air (1)", "Air french Band (3)", etc., cada uno con
        // solo una o dos pistas). synchronized(this) serializa toda
        // resolución/creación de directorio (poco frecuente, coste
        // despreciable) para que el find y el create de cada llamada
        // sean atómicos entre sí.
        // ---
        // synchronized(this) -- real bug reported by Miguel Ángel
        // (2026-07-03, Moon Safari re-import): findOrCreate() does a
        // non-atomic find-then-create (TOCTOU). When several tracks of
        // the same album download in parallel (several concurrent
        // DownloadWorkers, even more likely after the
        // reconcileOrphanedDownloads() fix, which re-enqueues
        // everything orphaned at once on startup), two workers can
        // both check at the same time that the "Air"/"Air french Band"
        // folder doesn't exist yet and both call createDirectory() --
        // the SAF provider resolves the clash by renaming the second
        // one to "Air (1)", "Air (2)"... creating duplicate physical
        // folders on disk that LibraryReconciler.rescan() later
        // imports as distinct artists/albums (visible in Biblioteca as
        // "Air (1)", "Air french Band (3)" groups, etc., each with
        // only one or two tracks). synchronized(this) serializes all
        // directory resolution/creation (infrequent, negligible cost)
        // so each call's find and create are atomic with respect to
        // each other.
        val root = DocumentFile.fromTreeUri(context, rootUri)
            ?: return@synchronized null

        val artistDir = root.findOrCreate(sanitize(artist))
            ?: return@synchronized null

        val albumName = album?.let { sanitize(it) } ?: UNKNOWN_ALBUM_DIR_NAME
        artistDir.findOrCreate(albumName)
    }

    /**
     * Finds an existing child directory by name or creates it if it
     * does not exist. Returns null on failure.
     * ---
     * Busca un subdirectorio hijo por nombre o lo crea si no existe.
     * Devuelve null en caso de fallo.
     */
    private fun DocumentFile.findOrCreate(name: String): DocumentFile? =
        findFile(name) ?: createDirectory(name)
}

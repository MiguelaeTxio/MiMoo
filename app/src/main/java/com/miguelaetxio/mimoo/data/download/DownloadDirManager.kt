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
    ): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, rootUri)
            ?: return null

        val artistDir = root.findOrCreate(sanitize(artist))
            ?: return null

        val albumName = album?.let { sanitize(it) } ?: UNKNOWN_ALBUM_DIR_NAME
        return artistDir.findOrCreate(albumName)
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

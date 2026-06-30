// /home/MiguelAeTxio/ANDROID/MiMoo/app/src/main/java/com/miguelaetxio/mimoo/data/download/DownloadDirManager.kt
package com.miguelaetxio.mimoo.data.download

import java.io.File

/**
 * Resolves and creates the local directory tree used to store
 * downloaded audio files, structured as /sdcard/MiMoo/{artist}/{album}/.
 * ---
 * Resuelve y crea el árbol de directorios local usado para almacenar
 * los archivos de audio descargados, con estructura
 * /sdcard/MiMoo/{artista}/{album}/.
 */
object DownloadDirManager {

    private const val ROOT_DIR_NAME = "MiMoo"
    private const val UNKNOWN_ALBUM_DIR_NAME = "_sin_album"

    /**
     * Forbidden characters in FAT32 filesystems (used by external
     * storage on most Android devices), plus control characters.
     * ---
     * Caracteres prohibidos en sistemas de archivos FAT32 (usados por
     * el almacenamiento externo en la mayoría de dispositivos
     * Android), más caracteres de control.
     */
    private val FORBIDDEN_CHARS_REGEX = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")

    /**
     * Sanitizes a name so it is safe to use as a FAT32 directory
     * segment: replaces forbidden characters, trims trailing dots
     * and spaces, and falls back to a default value if the result
     * is blank.
     * ---
     * Sanitiza un nombre para que sea seguro como segmento de
     * directorio en FAT32: sustituye caracteres prohibidos, recorta
     * puntos y espacios finales, y aplica un valor por defecto si el
     * resultado queda vacío.
     */
    fun sanitize(name: String, fallback: String = "_"): String {
        val cleaned = name
            .replace(FORBIDDEN_CHARS_REGEX, "_")
            .trim()
            .trimEnd('.', ' ')
        return cleaned.ifBlank { fallback }
    }

    /**
     * Returns the directory where tracks for the given artist/album
     * pair must be stored, creating it (and any missing parent
     * directories) if it does not exist yet. A null or blank album
     * falls back to a fixed "_sin_album" subdirectory.
     * ---
     * Devuelve el directorio donde deben almacenarse las pistas del
     * par artista/álbum indicado, creándolo (junto con cualquier
     * directorio padre que falte) si todavía no existe. Un álbum
     * nulo o vacío recae en un subdirectorio fijo "_sin_album".
     */
    fun getTrackDir(externalStorageRoot: File, artist: String, album: String?): File {
        val artistDirName = sanitize(artist)
        val albumDirName = album
            ?.let { sanitize(it) }
            ?: UNKNOWN_ALBUM_DIR_NAME

        val trackDir = File(
            File(File(externalStorageRoot, ROOT_DIR_NAME), artistDirName),
            albumDirName,
        )
        if (!trackDir.exists()) {
            trackDir.mkdirs()
        }
        return trackDir
    }
}

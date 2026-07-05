package com.miguelaetxio.mimoo.data.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.miguelaetxio.mimoo.data.download.DownloadDirManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Physically relocates a downloaded .opus file to a new
 * {artist}/{album}/ folder when a manual metadata edit changes the
 * artist or album (PASO 7, H03). Copy + delete rather than
 * DocumentsContract.moveDocument(): move support is provider-specific
 * and not guaranteed across every SAF backend, while stream copy +
 * delete works uniformly on any provider that supports read/write,
 * at the cost of a real byte copy for a file this small (a single
 * audio track).
 * ---
 * Reubica físicamente un archivo .opus descargado a una nueva carpeta
 * {artista}/{álbum}/ cuando una edición manual de metadatos cambia el
 * artista o el álbum (PASO 7, H03). Copia + borrado en vez de
 * DocumentsContract.moveDocument(): el soporte de move es específico
 * de cada proveedor y no está garantizado en todos los backends SAF,
 * mientras que copiar por streams + borrar funciona de forma uniforme
 * en cualquier proveedor que soporte lectura/escritura, a costa de
 * una copia de bytes real para un archivo de este tamaño (una sola
 * pista de audio).
 */
@Singleton
class TrackFileRelocator @Inject constructor() {

    /**
     * Returns the new file's content Uri (as String) on success, or
     * null if the source file, target directory, or the copy itself
     * could not be resolved/completed. Never leaves the source file
     * deleted unless the copy to the new location fully succeeded.
     * ---
     * Devuelve el Uri de contenido del nuevo archivo (como String) si
     * tiene éxito, o null si no se pudo resolver/completar el archivo
     * origen, el directorio destino o la copia en sí. Nunca borra el
     * archivo origen a menos que la copia al nuevo destino haya
     * terminado por completo con éxito.
     */
    fun relocate(
        context: Context,
        sourceFilePath: String,
        rootUri: Uri,
        newArtist: String,
        newAlbum: String?,
        title: String,
        trackPosition: Int? = null,
    ): String? {
        val sourceDoc = DocumentFile.fromSingleUri(context, Uri.parse(sourceFilePath))
            ?: return null
        if (!sourceDoc.exists()) return null

        val targetDir = DownloadDirManager.getOrCreateTrackDir(
            context = context,
            rootUri = rootUri,
            artist = newArtist,
            album = newAlbum,
        ) ?: return null

        val extension = sourceDoc.name?.substringAfterLast('.', "opus") ?: "opus"
        // Conserva el prefijo "NN - " si la pista tenía una posición
        // conocida -- petición de Miguel Ángel (2026-07-05): editar
        // metadatos no debe romper el orden del álbum igual que ya no
        // lo rompe una reconciliación (ver LibraryReconciler).
        // ---
        // Preserves the "NN - " prefix if the track had a known
        // position -- requested by Miguel Ángel (2026-07-05): editing
        // metadata shouldn't break the album order any more than a
        // reconciliation now does (see LibraryReconciler).
        val baseFileName = if (trackPosition != null) {
            "%02d - %s".format(trackPosition + 1, DownloadDirManager.sanitize(title))
        } else {
            DownloadDirManager.sanitize(title)
        }
        val targetFileName = uniqueFileName(targetDir, baseFileName, extension)

        val targetDoc = targetDir.createFile(
            resolveMimeType(extension),
            targetFileName,
        ) ?: return null

        val copiedOk = try {
            context.contentResolver.openInputStream(sourceDoc.uri)?.use { input ->
                context.contentResolver.openOutputStream(targetDoc.uri)?.use { output ->
                    input.copyTo(output)
                }
            } != null
        } catch (e: Exception) {
            false
        }

        if (!copiedOk) {
            // Clean up the partially-created target file so a failed
            // relocation doesn't leave an empty/corrupt duplicate
            // behind alongside the still-intact original.
            targetDoc.delete()
            return null
        }

        sourceDoc.delete()
        return targetDoc.uri.toString()
    }

    /**
     * Avoids silently overwriting an existing file with the same
     * name in the target album folder (e.g. two edits landing on the
     * same title) by appending " (2)", " (3)"... until the name is
     * free.
     * ---
     * Evita sobrescribir en silencio un archivo existente con el
     * mismo nombre en la carpeta de álbum destino (p.ej. dos
     * ediciones que acaban con el mismo título) añadiendo " (2)",
     * " (3)"... hasta que el nombre quede libre.
     */
    private fun uniqueFileName(
        targetDir: DocumentFile,
        baseFileName: String,
        extension: String,
    ): String {
        var candidate = "$baseFileName.$extension"
        var suffix = 2
        while (targetDir.findFile(candidate) != null) {
            candidate = "$baseFileName ($suffix).$extension"
            suffix++
        }
        return candidate
    }

    private fun resolveMimeType(extension: String): String = when (extension.lowercase()) {
        "opus" -> "audio/opus"
        "ogg" -> "audio/ogg"
        else -> "audio/*"
    }
}

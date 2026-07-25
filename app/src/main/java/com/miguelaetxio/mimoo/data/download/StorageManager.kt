package com.miguelaetxio.mimoo.data.download

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the SAF (Storage Access Framework) tree Uri chosen by the
 * user for storing downloaded audio files. Persists the Uri across
 * sessions in SharedPreferences so the picker is shown only once.
 * ---
 * Gestiona el Uri de arbol SAF elegido por el usuario para almacenar
 * los archivos de audio descargados. Persiste el Uri entre sesiones
 * en SharedPreferences para que el selector solo se muestre una vez.
 */
@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val PREFS_NAME = "mimoo_storage_prefs"
        private const val KEY_ROOT_URI = "saf_root_uri"

        /**
         * Serial del sistema de archivos de un volumen extraíble, del
         * estilo `1A2B-3C4D`. Es lo que Android usa como identificador
         * de volumen en el `treeDocumentId` de una tarjeta externa,
         * frente al literal `primary` de la memoria interna.
         */
        private val SD_VOLUME_REGEX = Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns the persisted SAF root Uri, or null if the user has
     * not yet chosen a storage location.
     * ---
     * Devuelve el Uri raiz SAF persistido, o null si el usuario
     * todavia no ha elegido una ubicacion de almacenamiento.
     */
    fun getRootUri(): Uri? =
        prefs.getString(KEY_ROOT_URI, null)?.let { Uri.parse(it) }

    /**
     * Persists the SAF root Uri chosen by the user and takes
     * persistable permissions so they survive reboots.
     * ---
     * Persiste el Uri raiz SAF elegido por el usuario y toma permisos
     * persistibles para que sobrevivan a reinicios.
     */
    fun saveRootUri(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.edit { putString(KEY_ROOT_URI, uri.toString()) }
    }

    /**
     * Clears the stored Uri, forcing the picker to show again on the
     * next download attempt.
     * ---
     * Borra el Uri almacenado, forzando que el selector aparezca de
     * nuevo en el siguiente intento de descarga.
     */
    fun clearRootUri() {
        prefs.edit { remove(KEY_ROOT_URI) }
    }

    /** Returns true if the user has already chosen a storage location. */
    fun hasRootUri(): Boolean = getRootUri() != null

    /**
     * Nombre legible de la carpeta raíz actual, para mostrarlo en
     * Ajustes ("Carpeta actual: ...").
     *
     * **Fallo real reportado por Miguel Ángel (S022):** *"a pesar de
     * mover todo, la carpeta actual parece seguir siendo la antigua,
     * por lo cual el cambio no se hace efectivo realmente"*. El cambio
     * sí se hacía efectivo -- `saf_root_uri` es el único almacén de la
     * raíz y todos sus consumidores (`DownloadWorker` incluido) leen de
     * ahí. Lo que engañaba era esta etiqueta: devolvía
     * `DocumentFile.fromTreeUri(uri).name`, o sea **solo el nombre de
     * la carpeta hoja**. Con una carpeta destino llamada igual que la
     * de origen (`MiMoo`, `Music`...), la etiqueta salía idéntica antes
     * y después del traslado, y no había forma de distinguir memoria
     * interna de tarjeta externa.
     *
     * La versión anterior documentaba que caería a un formato del
     * estilo `primary:Music/MiMoo` o `1A2B-3C4D:MiMoo`, pero esa rama
     * solo se alcanzaba si el proveedor SAF no daba nombre, cosa que en
     * la práctica no pasa nunca.
     *
     * Ahora la etiqueta se construye desde el `treeDocumentId`, que sí
     * lleva el identificador de volumen delante de los dos puntos, y
     * queda del estilo `Memoria interna · Music/MiMoo` o
     * `Tarjeta SD (1A2B-3C4D) · MiMoo`. `null` si no hay carpeta
     * elegida todavía.
     * ---
     * Human-readable name of the current root folder, for the Settings
     * screen. Built from the tree document id so the storage volume is
     * always visible. Null when no folder has been chosen yet.
     */
    fun getRootLabel(): String? {
        val uri = getRootUri() ?: return null

        val treeId = runCatching {
            DocumentsContract.getTreeDocumentId(uri)
        }.getOrNull()

        if (!treeId.isNullOrBlank() && treeId.contains(':')) {
            val volumeId = treeId.substringBefore(':')
            val path = treeId.substringAfter(':').trim('/')
            val volumeLabel = when {
                volumeId.equals("primary", ignoreCase = true) -> "Memoria interna"
                // Los volúmenes extraíbles se identifican con el serial
                // del sistema de archivos, del estilo 1A2B-3C4D.
                volumeId.matches(SD_VOLUME_REGEX) -> "Tarjeta SD ($volumeId)"
                else -> volumeId
            }
            return if (path.isBlank()) volumeLabel else "$volumeLabel · $path"
        }

        val fromProvider = runCatching {
            DocumentFile.fromTreeUri(context, uri)?.name
        }.getOrNull()
        if (!fromProvider.isNullOrBlank()) return fromProvider
        return runCatching { Uri.decode(uri.lastPathSegment) }.getOrNull()
    }

    /**
     * S021 -- **el permiso persistible de la raíz ANTERIOR no se
     * libera nunca al cambiar de carpeta.** Es deliberado: cuando
     * Miguel Ángel elige "solo cambiar la carpeta" (sin mover el
     * audio), o cuando una migración deja pistas a medio camino, las
     * filas de Room siguen apuntando con Uri absolutos a archivos de
     * la raíz vieja. Soltar aquel permiso las volvería ilegibles de
     * golpe. Android permite mantener varios permisos de árbol a la
     * vez, así que conservarlos no cuesta nada y evita ese modo de
     * fallo.
     * ---
     * S021 -- the previous root's persistable permission is
     * deliberately never released: rows may still point at files under
     * the old tree with absolute Uris.
     */
    fun persistedRootCount(): Int =
        context.contentResolver.persistedUriPermissions.size
}


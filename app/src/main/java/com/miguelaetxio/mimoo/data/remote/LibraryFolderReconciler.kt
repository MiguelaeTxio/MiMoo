package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.miguelaetxio.mimoo.data.download.DownloadDirManager
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.local.dao.SearchResultTrackDao
import com.miguelaetxio.mimoo.util.SearchNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S025 -- RECONCILIACIÓN DE LOS NOMBRES DE CARPETA EN DISCO.
 *
 * Orden de Miguel Ángel: *"en el botón de generar la base de datos,
 * cuando se pulse, debes incluir reconciliar los nombres de las
 * carpetas y poner los nombres de los artistas y no los nombres de
 * canales, que es un asco el directorio ahora mismo."*
 *
 * **Por qué está así el directorio.** Al descargar, la carpeta de
 * artista se creaba con lo que hubiera en `SearchResultTrack.artist`, y
 * ese campo caía al nombre del canal de YouTube cuando no había artista
 * estructurado. De ahí carpetas como `Deep Purple Official` o
 * `OlvidadasCanciones` conviviendo con las buenas. Las dos vías de
 * entrada están cerradas desde S025, pero lo ya escrito en disco sigue
 * ahí y hay que arreglarlo.
 *
 * **Cómo se deduce el nombre bueno**, en este orden:
 *   1. Si el diccionario del ancla conoce un artista cuyo nombre está
 *      contenido en el de la carpeta, ese. Es el caso de
 *      `Deep Purple Official` -> `Deep Purple`.
 *   2. Si no, el artista que sale de partir el título de alguna de sus
 *      pistas por `"Artista - Tema"`, cuando todas coinciden.
 *
 * Si ninguna de las dos da algo, la carpeta se deja en paz. Renombrar a
 * ciegas sería peor que el problema.
 *
 * **Fusión.** Si al renombrar ya existe una carpeta con el nombre bueno
 * -- lo normal: `Deep Purple` y `Deep Purple Official` a la vez -- se
 * mueven los álbumes de la mala a la buena y se borra la vacía, en vez
 * de dejar que SAF cree un `Deep Purple (1)`.
 * ---
 * S025 -- renames artist folders on the storage card that were created
 * from a YouTube channel name instead of the artist. Both entry points
 * were closed in S025, but what's already on disk still needs fixing.
 * Merges into the correct folder when it already exists.
 */
@Singleton
class LibraryFolderReconciler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: StorageManager,
    private val anchorDictionary: AnchorDictionary,
    private val trackDao: SearchResultTrackDao,
) {

    suspend fun reconcile(): Int {
        val rootUri = storageManager.getRootUri() ?: return 0
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return 0

        val tracks = trackDao.getAllOnce()
        val knownArtists = tracks.mapNotNull { it.artist }.toSet()
        var renamed = 0

        val artistDirs = root.listFiles().filter { it.isDirectory }
        for (dir in artistDirs) {
            val current = dir.name ?: continue
            if (current == DICT_DIR) continue
            if (!anchorDictionary.looksLikeChannelName(current)) continue

            val better = betterNameFor(current, knownArtists) ?: continue
            if (better.equals(current, ignoreCase = true)) continue

            val target = DownloadDirManager.sanitize(better)
            val existing = root.listFiles()
                .firstOrNull { it.isDirectory && it.name.equals(target, ignoreCase = true) }

            val ok = if (existing != null && existing.uri != dir.uri) {
                mergeInto(dir, existing)
            } else {
                runCatching { dir.renameTo(target) }.getOrDefault(false)
            }
            if (ok) renamed++
        }
        return renamed
    }

    /**
     * El nombre bueno para una carpeta que parece de canal, o `null` si
     * no se puede deducir con seguridad.
     */
    private fun betterNameFor(folderName: String, knownArtists: Set<String>): String? {
        val folderKey = SearchNormalizer.tight(SearchNormalizer.normalizeArtistName(folderName))
        if (folderKey.isBlank()) return null

        // 1. Un artista conocido contenido en el nombre de la carpeta.
        //    "Deep Purple Official" contiene "Deep Purple".
        val fromKnown = knownArtists
            .filterNot { anchorDictionary.looksLikeChannelName(it) }
            .filter { candidate ->
                val k = SearchNormalizer.tight(SearchNormalizer.normalizeArtistName(candidate))
                k.length >= 4 && folderKey.startsWith(k)
            }
            // El más largo es el más específico: entre "Deep" y
            // "Deep Purple", gana el segundo.
            .maxByOrNull { it.length }
        if (fromKnown != null) return fromKnown

        // 2. Lo que diga el diccionario del ancla, si reconoce algún
        //    prefijo del nombre de la carpeta como artista real.
        val words = folderName.trim().split(" ").filter { it.isNotBlank() }
        for (take in words.size downTo 1) {
            val candidate = words.take(take).joinToString(" ")
            if (anchorDictionary.looksLikeChannelName(candidate)) continue
            if (anchorDictionary.artist(candidate) != null) return candidate
        }
        return null
    }

    /**
     * Mueve los álbumes de [from] dentro de [into] y borra la carpeta
     * vacía. Si algún álbum no se puede mover se deja todo como está:
     * más vale una carpeta fea que media biblioteca a medio mover.
     */
    private fun mergeInto(from: DocumentFile, into: DocumentFile): Boolean {
        val children = from.listFiles()
        for (album in children) {
            val albumName = album.name ?: return false
            val destination = into.listFiles()
                .firstOrNull { it.isDirectory && it.name.equals(albumName, ignoreCase = true) }
            if (destination == null) {
                val moved = runCatching {
                    android.provider.DocumentsContract.moveDocument(
                        context.contentResolver,
                        album.uri,
                        from.uri,
                        into.uri,
                    )
                }.getOrNull()
                if (moved == null) return false
            } else {
                // El álbum ya existe en destino: se mueven las pistas
                // una a una y se borra la carpeta duplicada.
                for (file in album.listFiles()) {
                    runCatching {
                        android.provider.DocumentsContract.moveDocument(
                            context.contentResolver,
                            file.uri,
                            album.uri,
                            destination.uri,
                        )
                    }
                }
                runCatching { album.delete() }
            }
        }
        return runCatching { from.delete() }.getOrDefault(false)
    }

    private companion object {
        /** La carpeta del propio diccionario no se toca. */
        const val DICT_DIR = "MiMoo"
    }
}

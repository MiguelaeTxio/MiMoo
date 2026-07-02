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
 * Prefix for synthetic youtubeId values assigned to files discovered
 * on disk that have no matching Room row. Never collides with real
 * YouTube IDs, which are always exactly 11 chars with no colon.
 * ---
 * Prefijo para los youtubeId sintéticos de archivos encontrados en
 * disco sin fila Room correspondiente. Nunca choca con IDs reales de
 * YouTube, que siempre tienen exactamente 11 caracteres sin dos puntos.
 */
private const val LOCAL_ID_PREFIX = "local:"

/**
 * Reconciles the SAF storage folder against Room (PASO 10, H03): any
 * {Artista}/{Álbum}/Título.opus file with no matching filePath in the
 * database is registered as a new, synthetic entry. Recovers from
 * Room data loss (e.g. an app uninstall, which wipes internal storage
 * but not the external SAF folder) without touching the physical
 * files at all.
 *
 * Called from exactly two places, both explicit user actions — never
 * automatically on screen open, since a full SAF tree walk does not
 * scale to being run on every navigation with a large library:
 *   1. MainActivity, once, right after the user picks the storage
 *      folder for the first time.
 *   2. LibraryViewModel, on demand, via a manual refresh button.
 * ---
 * Reconcilia la carpeta de almacenamiento SAF contra Room (PASO 10,
 * H03): cualquier archivo {Artista}/{Álbum}/Título.opus sin filePath
 * correspondiente en la base de datos se registra como una entrada
 * nueva sintética. Recupera de una pérdida de datos de Room (p.ej.
 * una desinstalación, que borra el almacenamiento interno pero no la
 * carpeta SAF externa) sin tocar los archivos físicos.
 *
 * Se llama desde exactamente dos sitios, ambos acciones explícitas
 * del usuario — nunca automáticamente al abrir la pantalla, ya que un
 * recorrido completo del árbol SAF no escala a ejecutarse en cada
 * navegación con una biblioteca grande:
 *   1. MainActivity, una vez, justo tras elegir la carpeta de
 *      almacenamiento por primera vez.
 *   2. LibraryViewModel, bajo demanda, vía un botón de refresco manual.
 */
@Singleton
class LibraryReconciler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SearchResultTrackRepository,
) {
    suspend fun rescan(rootUri: Uri) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return

        val knownPaths = repository.getAll().first()
            .mapNotNull { it.filePath }
            .toSet()

        val discovered = mutableListOf<SearchResultTrack>()

        root.listFiles()
            .filter { it.isDirectory }
            .forEach { artistDir ->
                val artistName = artistDir.name ?: return@forEach
                artistDir.listFiles()
                    .filter { it.isDirectory }
                    .forEach { albumDir ->
                        val albumName = albumDir.name ?: return@forEach
                        albumDir.listFiles()
                            .filter {
                                it.isFile && it.name?.endsWith(".opus") == true
                            }
                            .forEach { file ->
                                val uriString = file.uri.toString()
                                if (uriString !in knownPaths) {
                                    discovered.add(
                                        buildSyntheticTrack(
                                            uriString = uriString,
                                            fileName = file.name!!,
                                            artistName = artistName,
                                            albumName = albumName,
                                        )
                                    )
                                }
                            }
                    }
            }

        if (discovered.isNotEmpty()) {
            repository.cacheSearchResults(discovered)
        }
    }

    private fun buildSyntheticTrack(
        uriString: String,
        fileName: String,
        artistName: String,
        albumName: String,
    ): SearchResultTrack {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uriString.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

        return SearchResultTrack(
            youtubeId = "$LOCAL_ID_PREFIX$digest",
            title = fileName.removeSuffix(".opus"),
            channelTitle = artistName,
            durationSeconds = 0,
            thumbnailUrl = null,
            filePath = uriString,
            downloadStatus = DownloadStatus.DONE,
            artist = artistName,
            album = if (albumName == UNKNOWN_ALBUM_LABEL) null else albumName,
        )
    }
}

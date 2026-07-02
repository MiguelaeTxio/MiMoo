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
    suspend fun rescan(rootUri: Uri) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return

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
                                if (uriString !in knownRealPaths) {
                                    val preservedFavorite =
                                        existingSyntheticByPath[uriString]
                                            ?.isFavorite ?: false
                                    discovered.add(
                                        buildSyntheticTrack(
                                            uriString = uriString,
                                            fileName = file.name!!,
                                            artistName = artistName,
                                            albumName = albumName,
                                            isFavorite = preservedFavorite,
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
        isFavorite: Boolean,
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
            album = if (albumName in NULL_ALBUM_FOLDER_NAMES) {
                null
            } else {
                albumName
            },
            isFavorite = isFavorite,
        )
    }
}

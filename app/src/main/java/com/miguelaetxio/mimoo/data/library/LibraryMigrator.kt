package com.miguelaetxio.mimoo.data.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.miguelaetxio.mimoo.data.download.DownloadDirManager
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Traslada toda la biblioteca de audio ya descargada a una raíz SAF
 * nueva -- petición de Miguel Ángel (S020, registrada en
 * `DOCS/RESUMPTION_POINT.md`): *"dar la posibilidad de cambiar la
 * carpeta de descarga con la opción de copiar todo a esa carpeta o
 * solamente los settings, sin perder favoritos etc."*, ampliada en
 * S021 con el caso de uso concreto de mover la biblioteca a una
 * tarjeta externa.
 *
 * **Por qué hace falta un migrador y no basta con cambiar la raíz.**
 * `SearchResultTrack.filePath` no guarda una ruta relativa a la raíz,
 * sino el Uri de contenido SAF COMPLETO del archivo
 * (`DocumentFile.fromSingleUri(context, Uri.parse(filePath))`, ver
 * `LibraryReconciler` y `TrackFileRelocator`). Cambiar la raíz no
 * reescribe esos Uri por sí solo: cada archivo hay que copiarlo al
 * destino y regenerar su Uri uno a uno.
 *
 * **Qué NO toca, y por qué los favoritos no se pierden.** La única
 * columna de toda la base de datos que contiene una ruta es
 * `SearchResultTrack.filePath` (comprobado sobre las ocho entidades de
 * `data/local/entity`). Favoritos (`isFavorite`, `FavoriteArtist`,
 * `FavoriteAlbum`), listas (`Playlist` + `PlaylistTrackCrossRef`),
 * canales (`ChannelSubscription`), emisoras y carátulas referencian
 * pistas por `youtubeId` o por artista/álbum, nunca por ruta. Migrar
 * `filePath` es, por tanto, condición suficiente: el resto de la base
 * de datos ni se lee ni se escribe aquí.
 *
 * **Copia + borrado, no `moveDocument()`.** Mismo criterio que
 * `TrackFileRelocator`: el soporte de `DocumentsContract.moveDocument()`
 * es específico de cada proveedor SAF y no está garantizado entre dos
 * volúmenes distintos, que es justo el caso de uso principal aquí
 * (memoria interna -> tarjeta externa). Copiar por streams y borrar
 * después funciona en cualquier proveedor.
 *
 * **Nunca destructivo antes de tiempo.** El archivo origen solo se
 * borra si la copia terminó entera Y la fila de Room ya apunta al
 * destino nuevo. Si algo falla a mitad, esa pista se queda intacta
 * donde estaba, apuntada por su Uri antiguo, y se contabiliza como
 * fallida. Una migración a medias deja la biblioteca funcionando, con
 * unas pistas en el destino y otras en el origen -- nunca rota.
 * ---
 * Moves the whole downloaded audio library to a new SAF root. Copy +
 * delete rather than moveDocument(), and never deletes the source
 * until the copy completed and Room already points at the new Uri.
 */
@Singleton
class LibraryMigrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackRepository: SearchResultTrackRepository,
) {

    /** Progreso vivo de la migración, para la barra de la pantalla de Ajustes. */
    data class Progress(val done: Int, val total: Int, val failed: Int)

    sealed interface Result {
        /**
         * `migrated` pistas movidas con éxito; `failed` que siguen
         * donde estaban (archivo origen desaparecido, destino no
         * creable, o error de copia). `failed > 0` no invalida la
         * migración: la biblioteca sigue entera y reproducible.
         */
        data class Completed(val migrated: Int, val failed: Int) : Result

        /** No se pudo ni empezar -- la raíz destino no es utilizable. */
        data class Aborted(val reason: String) : Result
    }

    /**
     * Copia al destino todas las pistas en `DownloadStatus.DONE` con
     * `filePath`, reescribiendo su fila en Room, y borra el original
     * de cada una que haya llegado bien.
     *
     * Reentrante: si una pista ya está en el destino con el mismo
     * nombre y tamaño, se reutiliza el archivo existente en vez de
     * volver a copiarlo, así que reintentar una migración interrumpida
     * no duplica nada.
     */
    suspend fun migrateTo(
        newRootUri: Uri,
        onProgress: (Progress) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val destinationRoot = DocumentFile.fromTreeUri(context, newRootUri)
        if (destinationRoot == null || !destinationRoot.canWrite()) {
            return@withContext Result.Aborted(
                "No se puede escribir en la carpeta elegida.",
            )
        }

        val tracks = trackRepository.getAllOnce().filter {
            it.downloadStatus == DownloadStatus.DONE && it.filePath != null
        }
        val total = tracks.size
        var migrated = 0
        var failed = 0
        onProgress(Progress(done = 0, total = total, failed = 0))

        for (track in tracks) {
            val sourceUri = Uri.parse(track.filePath)
            val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri)

            // Si el archivo origen ya no está, no hay nada que mover.
            // La fila se deja intacta: de eso se encarga la
            // reconciliación SAF<->Room, no este migrador.
            if (sourceDoc == null || !sourceDoc.exists()) {
                failed++
                onProgress(Progress(migrated, total, failed))
                continue
            }

            val targetDir = DownloadDirManager.getOrCreateTrackDir(
                context = context,
                rootUri = newRootUri,
                artist = track.artist ?: track.channelTitle,
                album = track.album,
            )
            if (targetDir == null) {
                failed++
                onProgress(Progress(migrated, total, failed))
                continue
            }

            // Se conserva el nombre de archivo tal cual, incluido el
            // prefijo "NN - " de posición de pista: mover la
            // biblioteca no debe reordenar los álbumes.
            val fileName = sourceDoc.name ?: "${track.youtubeId}.opus"
            val targetDoc = resolveTarget(targetDir, fileName, sourceDoc.length())
            if (targetDoc == null) {
                failed++
                onProgress(Progress(migrated, total, failed))
                continue
            }

            val copied = copyIfNeeded(sourceDoc, targetDoc)
            if (!copied) {
                // Copia incompleta: se retira el destino a medias para
                // no dejar basura, y el origen queda intacto.
                targetDoc.delete()
                failed++
                onProgress(Progress(migrated, total, failed))
                continue
            }

            // Room primero, borrado después. En este orden, una
            // interrupción entre ambos deja un archivo huérfano en el
            // origen -- molesto pero inofensivo. En el orden inverso
            // dejaría una fila apuntando a un archivo ya borrado, que
            // sí rompe la reproducción.
            trackRepository.update(track.copy(filePath = targetDoc.uri.toString()))
            sourceDoc.delete()

            migrated++
            onProgress(Progress(migrated, total, failed))
        }

        Result.Completed(migrated = migrated, failed = failed)
    }

    /**
     * Devuelve el DocumentFile destino para `fileName`, reutilizándolo
     * si ya existe con el mismo tamaño (migración reintentada) y
     * creando uno con sufijo numérico si existe con tamaño distinto
     * (colisión real de nombres entre dos pistas distintas).
     */
    private fun resolveTarget(
        targetDir: DocumentFile,
        fileName: String,
        sourceLength: Long,
    ): DocumentFile? {
        val existing = targetDir.findFile(fileName)
        if (existing != null && existing.isFile) {
            if (existing.length() == sourceLength) return existing
            val base = fileName.substringBeforeLast('.', fileName)
            val extension = fileName.substringAfterLast('.', "opus")
            var index = 1
            while (targetDir.findFile("$base ($index).$extension") != null) index++
            return targetDir.createFile(MIME_AUDIO, "$base ($index).$extension")
        }
        return targetDir.createFile(MIME_AUDIO, fileName)
    }

    /**
     * Copia origen -> destino salvo que el destino ya tenga el mismo
     * tamaño exacto (ya copiado en un intento anterior). Devuelve true
     * si al terminar el destino contiene la misma cantidad de bytes
     * que el origen.
     */
    private fun copyIfNeeded(source: DocumentFile, target: DocumentFile): Boolean {
        val expected = source.length()
        if (target.length() == expected && expected > 0L) return true
        return try {
            context.contentResolver.openInputStream(source.uri)?.use { input ->
                context.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
                    input.copyTo(output)
                }
            }
            target.length() == expected
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val MIME_AUDIO = "audio/ogg"
    }
}

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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    /**
     * Motivo por el que una pista concreta no se pudo mover.
     *
     * Añadido en S022 tras la primera prueba real de Miguel Ángel: de
     * 700 y pico canciones, 8 fallaron, y al reintentar fallaron las
     * mismas 8 -- fallo determinista. El migrador contabilizaba los
     * fallos pero no registraba **cuál** ni **por qué**, así que el
     * resumen decía "8 no se han podido mover" y ahí se acababa la
     * información ("no sé qué canciones son"). Cada rama de fallo
     * escribe ahora su causa concreta.
     */
    enum class FailureReason {
        /**
         * El archivo origen no existe. La fila sigue en Room como DONE
         * apuntando a un Uri muerto: esa pista **ya no sonaba antes de
         * migrar**. No es un fallo del traslado, es una fila huérfana
         * que la reconciliación SAF<->Room debe devolver a descarga.
         */
        SOURCE_MISSING,

        /** No se pudo crear `{Artista}/{Álbum}` bajo la raíz destino. */
        TARGET_DIR_NOT_CREATED,

        /** `createFile()` devolvió null para el nombre de archivo. */
        TARGET_FILE_NOT_CREATED,

        /** La copia no lanzó, pero el destino no acabó con los bytes del origen. */
        COPY_INCOMPLETE,

        /** La copia lanzó una excepción -- el detalle lleva su mensaje. */
        COPY_THREW,
    }

    /**
     * Una pista que no se pudo mover, con lo necesario para
     * identificarla a simple vista en Ajustes y para diagnosticar la
     * causa en el informe de texto.
     */
    data class Failure(
        val youtubeId: String,
        val title: String,
        val artist: String?,
        val album: String?,
        val reason: FailureReason,
        val detail: String? = null,
        val sourcePath: String? = null,
    ) {
        /** Etiqueta corta para el diálogo de Ajustes. */
        val label: String
            get() = buildString {
                append(artist ?: "Artista desconocido")
                append(" — ")
                append(title)
            }

        val reasonText: String
            get() = when (reason) {
                FailureReason.SOURCE_MISSING ->
                    "el archivo ya no estaba en la carpeta anterior"
                FailureReason.TARGET_DIR_NOT_CREATED ->
                    "no se pudo crear su carpeta en el destino"
                FailureReason.TARGET_FILE_NOT_CREATED ->
                    "el destino rechazó el nombre del archivo"
                FailureReason.COPY_INCOMPLETE ->
                    "la copia quedó incompleta"
                FailureReason.COPY_THREW ->
                    "error al copiar${detail?.let { ": $it" } ?: ""}"
            }
    }

    sealed interface Result {
        /**
         * `migrated` pistas movidas con éxito; `failures` las que
         * siguen donde estaban, cada una con su motivo concreto.
         * `failures` no vacío no invalida la migración: la biblioteca
         * sigue entera y reproducible.
         */
        data class Completed(
            val migrated: Int,
            val failures: List<Failure>,
        ) : Result {
            val failed: Int get() = failures.size
        }

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
        val failures = mutableListOf<Failure>()

        // Registra el fallo con su causa y refresca el progreso. Cada
        // rama de abajo dice exactamente por qué se rindió, en vez del
        // `failed++` mudo que había hasta S022.
        fun fail(
            track: com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack,
            reason: FailureReason,
            detail: String? = null,
        ) {
            failures += Failure(
                youtubeId = track.youtubeId,
                title = track.title,
                artist = track.artist ?: track.channelTitle,
                album = track.album,
                reason = reason,
                detail = detail,
                sourcePath = track.filePath,
            )
            onProgress(Progress(migrated, total, failures.size))
        }

        onProgress(Progress(done = 0, total = total, failed = 0))

        for (track in tracks) {
            val sourceUri = Uri.parse(track.filePath)
            val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri)

            // Si el archivo origen ya no está, no hay nada que mover.
            // La fila se deja intacta: de eso se encarga la
            // reconciliación SAF<->Room, no este migrador.
            if (sourceDoc == null || !sourceDoc.exists()) {
                fail(track, FailureReason.SOURCE_MISSING)
                continue
            }

            val targetDir = DownloadDirManager.getOrCreateTrackDir(
                context = context,
                rootUri = newRootUri,
                artist = track.artist ?: track.channelTitle,
                album = track.album,
            )
            if (targetDir == null) {
                fail(track, FailureReason.TARGET_DIR_NOT_CREATED)
                continue
            }

            // Se conserva el nombre de archivo tal cual, incluido el
            // prefijo "NN - " de posición de pista: mover la
            // biblioteca no debe reordenar los álbumes.
            val fileName = sourceDoc.name ?: "${track.youtubeId}.opus"
            val targetDoc = resolveTarget(targetDir, fileName, sourceDoc.length())
            if (targetDoc == null) {
                fail(track, FailureReason.TARGET_FILE_NOT_CREATED, "nombre: $fileName")
                continue
            }

            val copyError = copyIfNeeded(sourceDoc, targetDoc)
            if (copyError != null) {
                // Copia incompleta: se retira el destino a medias para
                // no dejar basura, y el origen queda intacto.
                targetDoc.delete()
                fail(track, copyError.reason, copyError.detail)
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
            onProgress(Progress(migrated, total, failures.size))
        }

        writeFailureReport(newRootUri, migrated, total, failures)
        Result.Completed(migrated = migrated, failures = failures)
    }

    /**
     * Vuelca el detalle completo de los fallos a un archivo de texto en
     * la raíz nueva -- mismo patrón que `RadioDebugLogger` (H08),
     * `RadioBrowserDebugLogger` (H09) y `BackupDebugLogger` (H06).
     *
     * El diálogo de Ajustes muestra la lista legible; este archivo
     * lleva además el `filePath` completo de origen, que es lo que
     * permite distinguir de un vistazo si las filas huérfanas apuntan a
     * la raíz anterior, a una raíz todavía más antigua, o a un Uri
     * malformado. Se sobrescribe en cada migración: interesa el último
     * intento, no el histórico.
     */
    private fun writeFailureReport(
        rootUri: Uri,
        migrated: Int,
        total: Int,
        failures: List<Failure>,
    ) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
        val report = buildString {
            appendLine("MiMoo -- informe de traslado de biblioteca")
            appendLine("Fecha: $stamp")
            appendLine("Pistas DONE con archivo: $total")
            appendLine("Movidas: $migrated")
            appendLine("Fallidas: ${failures.size}")
            appendLine()
            if (failures.isEmpty()) {
                appendLine("Sin fallos.")
            } else {
                failures.groupBy { it.reason }.forEach { (reason, group) ->
                    appendLine("== $reason (${group.size}) ==")
                    group.forEach { f ->
                        appendLine("  ${f.label}")
                        appendLine("    álbum:   ${f.album ?: "(sin álbum)"}")
                        appendLine("    youtube: ${f.youtubeId}")
                        appendLine("    origen:  ${f.sourcePath ?: "(sin ruta)"}")
                        f.detail?.let { appendLine("    detalle: $it") }
                    }
                    appendLine()
                }
            }
        }
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
            val doc = rootDoc?.findFile(REPORT_FILE_NAME)
                ?: rootDoc?.createFile("text/plain", REPORT_FILE_NAME)
            if (doc != null) {
                context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                    out.write(report.toByteArray())
                }
            } else {
                File(context.filesDir, REPORT_FILE_NAME).writeText(report)
            }
        } catch (e: Exception) {
            // Un fallo escribiendo el informe nunca debe afectar al
            // resultado real del traslado, que ya está consolidado.
            try {
                File(context.filesDir, REPORT_FILE_NAME).writeText(report)
            } catch (ignored: Exception) {
                // Sin sitio donde escribir: el diálogo de Ajustes sigue
                // mostrando la lista, que es la vía principal.
            }
        }
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

    /** Causa concreta de una copia fallida. */
    private data class CopyError(val reason: FailureReason, val detail: String?)

    /**
     * Copia origen -> destino salvo que el destino ya tenga el mismo
     * tamaño exacto (ya copiado en un intento anterior). Devuelve null
     * si al terminar el destino contiene la misma cantidad de bytes que
     * el origen, o la causa concreta del fallo.
     *
     * Hasta S022 esto devolvía un simple `false` desde un
     * `catch (e: Exception)`, de modo que la causa raíz de un fallo de
     * copia se perdía en el mismo sitio donde se producía. Ahora el
     * mensaje de la excepción viaja hasta el informe: es la diferencia
     * entre "8 no se han podido mover" y saber si fue permiso, espacio
     * o Uri roto.
     */
    private fun copyIfNeeded(source: DocumentFile, target: DocumentFile): CopyError? {
        val expected = source.length()
        if (target.length() == expected && expected > 0L) return null
        return try {
            val input = context.contentResolver.openInputStream(source.uri)
                ?: return CopyError(
                    FailureReason.COPY_THREW,
                    "no se pudo abrir el archivo de origen",
                )
            input.use { source0 ->
                val output = context.contentResolver.openOutputStream(target.uri, "wt")
                    ?: return CopyError(
                        FailureReason.COPY_THREW,
                        "no se pudo abrir el archivo de destino para escritura",
                    )
                output.use { target0 -> source0.copyTo(target0) }
            }
            val actual = target.length()
            if (actual == expected) {
                null
            } else {
                CopyError(
                    FailureReason.COPY_INCOMPLETE,
                    "esperados $expected bytes, escritos $actual",
                )
            }
        } catch (e: Exception) {
            CopyError(
                FailureReason.COPY_THREW,
                "${e.javaClass.simpleName}: ${e.message ?: "sin mensaje"}",
            )
        }
    }

    private companion object {
        const val MIME_AUDIO = "audio/ogg"
        const val REPORT_FILE_NAME = "traslado_biblioteca_informe.txt"
    }
}

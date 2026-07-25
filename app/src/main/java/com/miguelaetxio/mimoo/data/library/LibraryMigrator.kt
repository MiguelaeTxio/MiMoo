package com.miguelaetxio.mimoo.data.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.system.Os
import androidx.documentfile.provider.DocumentFile
import com.miguelaetxio.mimoo.data.download.DownloadDirManager
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
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

        // El guardián de disco (LibraryReconciler.verifyDiskState()) se
        // aparta mientras dure esto. En `finally` sin excepción: si el
        // flag se quedara encendido, la verificación de disco quedaría
        // desactivada para siempre.
        isMigrating = true
        try {
            migrateInternal(newRootUri, onProgress)
        } finally {
            isMigrating = false
        }
    }

    private suspend fun migrateInternal(
        newRootUri: Uri,
        onProgress: (Progress) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val tracks = trackRepository.getAllOnce().filter {
            it.downloadStatus == DownloadStatus.DONE && it.filePath != null
        }
        val total = tracks.size
        val failures = mutableListOf<Failure>()

        fun fail(
            track: SearchResultTrack,
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
        }

        // ─────────────────────────────────────────────────────────────
        // FASE 0 -- precondiciones. Nada se toca hasta que todas pasan.
        //
        // Las pistas cuyo archivo de origen ya no existe NO bloquean el
        // traslado: no hay nada que mover, así que se apartan y se
        // reportan. Bloquear la biblioteca entera por unas filas
        // huérfanas sería convertir un problema viejo en un problema
        // nuevo.
        // ─────────────────────────────────────────────────────────────
        val pending = mutableListOf<Pair<SearchResultTrack, DocumentFile>>()
        for (track in tracks) {
            val sourceDoc = DocumentFile.fromSingleUri(context, Uri.parse(track.filePath))
            if (sourceDoc == null || !sourceDoc.exists()) {
                fail(track, FailureReason.SOURCE_MISSING)
                continue
            }
            pending += track to sourceDoc
        }

        val requiredBytes = pending.sumOf { (_, doc) -> doc.length() }
        val freeBytes = freeSpaceAt(newRootUri)
        if (freeBytes != null && freeBytes < requiredBytes + SAFETY_MARGIN_BYTES) {
            return@withContext Result.Aborted(
                "No hay espacio suficiente en la carpeta elegida.\n\n" +
                    "Hacen falta ${formatBytes(requiredBytes)} " +
                    "(más ${formatBytes(SAFETY_MARGIN_BYTES)} de margen) " +
                    "y quedan ${formatBytes(freeBytes)} libres.\n\n" +
                    "No se ha movido nada: la biblioteca sigue intacta " +
                    "donde estaba.",
            )
        }

        onProgress(Progress(done = 0, total = total, failed = failures.size))

        // ─────────────────────────────────────────────────────────────
        // FASE 1 -- copiar TODO al destino, sin tocar Room y sin borrar
        // un solo archivo del origen. Mientras dura esta fase, la
        // biblioteca sigue siendo enteramente la de origen: si algo
        // falla, se retira lo copiado y no ha pasado nada.
        // ─────────────────────────────────────────────────────────────
        val staged = mutableListOf<Staged>()
        for ((track, sourceDoc) in pending) {
            val targetDir = DownloadDirManager.getOrCreateTrackDir(
                context = context,
                rootUri = newRootUri,
                artist = track.artist ?: track.channelTitle,
                album = track.album,
            )
            if (targetDir == null) {
                rollback(staged)
                return@withContext abortedMidCopy(track, "no se pudo crear su carpeta en el destino")
            }

            val fileName = sourceDoc.name ?: "${track.youtubeId}.opus"
            val targetDoc = resolveTarget(targetDir, fileName, sourceDoc.length())
            if (targetDoc == null) {
                rollback(staged)
                return@withContext abortedMidCopy(track, "el destino rechazó el nombre «$fileName»")
            }

            val copyError = copyIfNeeded(sourceDoc, targetDoc)
            if (copyError != null) {
                targetDoc.delete()
                rollback(staged)
                return@withContext abortedMidCopy(
                    track,
                    copyError.detail ?: "error al copiar",
                )
            }

            staged += Staged(track, sourceDoc, targetDoc)
            onProgress(Progress(staged.size, total, failures.size))
        }

        // ─────────────────────────────────────────────────────────────
        // FASE 2 -- conmutación. Una sola transacción de Room para toda
        // la biblioteca: o apunta entera al destino, o entera al
        // origen. Es el único instante en que el traslado "ocurre".
        // ─────────────────────────────────────────────────────────────
        trackRepository.updateAll(
            staged.map { it.track.copy(filePath = it.target.uri.toString()) },
        )

        // ─────────────────────────────────────────────────────────────
        // FASE 3 -- borrar los originales, ya con Room apuntando al
        // destino. Un fallo aquí solo deja un archivo huérfano en el
        // origen: molesto, nunca destructivo.
        // ─────────────────────────────────────────────────────────────
        staged.forEach { runCatching { it.source.delete() } }

        writeFailureReport(newRootUri, staged.size, total, failures)
        Result.Completed(migrated = staged.size, failures = failures)
    }

    /** Una pista ya copiada al destino, pendiente de conmutar en Room. */
    private data class Staged(
        val track: SearchResultTrack,
        val source: DocumentFile,
        val target: DocumentFile,
    )

    /**
     * Retira del destino todo lo copiado en esta pasada. Room no se ha
     * tocado todavía y los originales siguen intactos, así que tras
     * esto la biblioteca queda exactamente como antes de empezar.
     */
    private fun rollback(staged: List<Staged>) {
        staged.forEach { runCatching { it.target.delete() } }
    }

    private fun abortedMidCopy(track: SearchResultTrack, reason: String): Result.Aborted =
        Result.Aborted(
            "El traslado se ha cancelado al copiar «${track.artist ?: track.channelTitle} " +
                "— ${track.title}»: $reason.\n\n" +
                "No se ha movido nada: la biblioteca sigue intacta donde estaba.",
        )

    /**
     * Bytes libres en el volumen de la raíz SAF indicada, o `null` si
     * el proveedor no lo dice.
     *
     * S022, petición explícita de Miguel Ángel antes de trasladar de la
     * tarjeta de vuelta al teléfono: *"antes hay que prever que la
     * operación debe ser atómica y mirar los espacios de origen y el
     * libre a destino"*. El sentido del traslado importa -- de tarjeta
     * a memoria interna se va hacia el volumen más pequeño, y quedarse
     * sin sitio a mitad era hasta ahora perfectamente posible.
     *
     * `null` (proveedor que no responde) NO bloquea el traslado: no
     * saber cuánto hay libre no es lo mismo que saber que no cabe.
     */
    private fun freeSpaceAt(rootUri: Uri): Long? = try {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            rootUri,
            DocumentsContract.getTreeDocumentId(rootUri),
        )
        context.contentResolver.openFileDescriptor(documentUri, "r")?.use { descriptor ->
            val stats = Os.fstatvfs(descriptor.fileDescriptor)
            stats.f_bavail * stats.f_frsize
        }
    } catch (e: Exception) {
        null
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes.toDouble() / (1024 * 1024 * 1024)
        if (gb >= 1.0) return String.format(Locale.getDefault(), "%.1f GB", gb)
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format(Locale.getDefault(), "%.0f MB", mb)
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

    companion object {
        private const val MIME_AUDIO = "audio/ogg"
        private const val REPORT_FILE_NAME = "traslado_biblioteca_informe.txt"

        /**
         * Margen que se exige libre en el destino POR ENCIMA de lo que
         * ocupa la biblioteca. Dejar un volumen al borde de su
         * capacidad -- y más aún la memoria interna -- trae sus propios
         * problemas, así que no basta con que quepa justo.
         */
        private const val SAFETY_MARGIN_BYTES = 300L * 1024 * 1024

        /**
         * True mientras hay un traslado de biblioteca en curso.
         *
         * S022, fallo real de Miguel Ángel: durante el traslado hay
         * archivos legítimamente en tránsito, y
         * `LibraryReconciler.verifyDiskState()` interpretaba eso como
         * "estas pistas ya no existen", las devolvía a PENDING y
         * `AutoSyncViewModel` las reencolaba para descarga. Este flag
         * es lo que le permite al reconciliador quitarse de en medio
         * mientras dure el traslado.
         *
         * `@Volatile` porque se escribe desde el hilo de IO del
         * migrador y se lee desde el de la sincronización.
         */
        @Volatile
        var isMigrating: Boolean = false
            private set
    }
}

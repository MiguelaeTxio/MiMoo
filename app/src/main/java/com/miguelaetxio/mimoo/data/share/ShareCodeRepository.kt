package com.miguelaetxio.mimoo.data.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.miguelaetxio.mimoo.data.backup.BackupBundle
import com.miguelaetxio.mimoo.data.backup.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extensión real del archivo de compartición H10. Ver comentario de
 * clase para el porqué del cambio de formato.
 */
const val SHARE_FILE_EXTENSION = ".mimoo"

/**
 * Tipo MIME propio del archivo de compartición (H10, S011 -- segundo
 * rediseño, tras confirmar en dispositivo real que MiMoo ni siquiera
 * aparecía en el selector "Abrir con" de WhatsApp). Causa real: las
 * URIs `content://` que da WhatsApp al abrir un documento recibido
 * son OPACAS -- no contienen el nombre de archivo real en la propia
 * URI (`.../item/12345`, no `.../algo.mimoo`), así que un
 * `pathPattern=".*\.mimoo"` en el manifiesto NUNCA puede coincidir
 * contra ellas, por mucho que el archivo real sí se llame `.mimoo` --
 * el nombre solo es recuperable en tiempo de ejecución consultando
 * `OpenableColumns.DISPLAY_NAME`, que el sistema de resolución de
 * intents no usa para decidir qué apps ofrecer.
 *
 * El tipo MIME, en cambio, SÍ viaja con el propio Intent de un
 * extremo a otro (se fija al compartir con `Intent.ACTION_SEND`,
 * WhatsApp lo conserva al guardar el archivo recibido, y lo reutiliza
 * en el `Intent.ACTION_VIEW` que dispara al tocarlo) -- por eso este
 * repositorio y `AndroidManifest.xml` usan ahora un tipo MIME propio
 * en vez de la extensión del nombre de archivo como mecanismo de
 * emparejamiento real.
 */
const val SHARE_MIME_TYPE = "application/x-mimoo-share"

/**
 * Construye el [ShareBundle] para cada nivel de compartición y lo
 * materializa como un ARCHIVO real (H10, S011 -- rediseñado tras
 * probarlo Miguel Ángel en dispositivo real: *"He hecho una prueba a
 * compartir el link, eso así no sirve... Tendremos que generar un
 * archivo... que solo se pudiera abrir desde la aplicación"*).
 *
 * **Por qué texto plano no servía:** un `Intent.ACTION_SEND` de
 * `text/plain` no le da al receptor nada que "tocar para abrir" --
 * WhatsApp/SMS lo pegan como texto suelto, sin ninguna acción
 * asociada. Un archivo real, en cambio, sí se puede tocar para
 * abrirlo -- y si MiMoo es la única app registrada para su
 * extensión, el sistema lo abre directamente con MiMoo (ver
 * `AndroidManifest.xml`, intent-filter de `.mimoo`).
 *
 * Formato del archivo: `GZIP(JSON(ShareBundle))`, en crudo -- sin
 * Base64 (ya no hace falta sobrevivir como texto de un solo bloque,
 * es contenido binario de un archivo real) y sin ningún prefijo
 * "miMoo+" (era una lectura mía equivocada de lo que pedía Miguel
 * Ángel -- él no quería el carácter "+" literal. La propia extensión
 * del archivo es ahora el identificador, no hace falta ningún
 * marcador dentro del contenido).
 *
 * Deliberadamente separado de `BackupRepository` (H06) aunque
 * reutiliza su `BackupBundle` -- `BackupRepository` habla de
 * exportar/importar TODO el repositorio a Drive; este repositorio
 * habla de compartir un subconjunto arbitrario por cualquier medio.
 * ---
 * Builds the [ShareBundle] for each sharing level and materializes it
 * as a real FILE (H10, S011 -- redesigned after Miguel Ángel tested it
 * on a real device: plain-text sharing gave the recipient nothing to
 * tap-to-open, just pasted text with no associated action. A real
 * file, registered to open exclusively with MiMoo via its own
 * extension, can actually be tapped to open.
 */
@Singleton
class ShareCodeRepository @Inject constructor(
    private val backupRepository: BackupRepository,
    @ApplicationContext private val context: Context,
) {
    private val gson: Gson = GsonBuilder().create()

    /**
     * Nivel 1 de la lista de Miguel Ángel (S011): Biblioteca completa.
     * Reutiliza `BackupRepository.buildCurrentBundle()` tal cual --
     * "compartir toda la biblioteca" es, por definición, el mismo
     * contenido que "exportar todo" (H06), solo que el destino es un
     * archivo `.mimoo` en vez de un archivo en Drive.
     */
    suspend fun buildLibraryShareFile(): Uri {
        val bundle = backupRepository.buildCurrentBundle()
        return writeShareFile(
            ShareBundle(
                scopeLabel = "Biblioteca completa (${bundle.tracks.size} pistas)",
                sharedAt = System.currentTimeMillis(),
                bundle = bundle,
            ),
            fileNameHint = "biblioteca_completa",
        )
    }

    /**
     * Niveles 2-8 (S011): todos parten del mismo bundle completo que
     * ya construye `buildCurrentBundle()` (solo pistas realmente
     * descargadas, sin filas sintéticas -- mismo filtro que H06/H07)
     * y lo recortan al subconjunto pedido. `label` recibe el bundle
     * YA filtrado, para poder incluir el recuento real de pistas.
     */
    private suspend fun buildScopedShareFile(
        fileNameHint: String,
        filter: (BackupBundle) -> BackupBundle,
        label: (BackupBundle) -> String,
    ): Uri {
        val full = backupRepository.buildCurrentBundle()
        val scoped = filter(full)
        return writeShareFile(
            ShareBundle(scopeLabel = label(scoped), sharedAt = System.currentTimeMillis(), bundle = scoped),
            fileNameHint = fileNameHint,
        )
    }

    /** Nivel 2: Artista -- todas las pistas descargadas de ese artista, sin playlists. */
    suspend fun buildArtistShareFile(artist: String): Uri =
        buildScopedShareFile(
            fileNameHint = artist,
            filter = { full ->
                full.copy(
                    tracks = full.tracks.filter { it.artist == artist },
                    favoriteAlbums = full.favoriteAlbums.filter { it.artist == artist },
                    playlists = emptyList(),
                )
            },
            label = { scoped -> "Artista: $artist (${scoped.tracks.size} pistas)" },
        )

    /** Nivel 3: Álbum -- pistas de ese artista+álbum, en orden real de disco. */
    suspend fun buildAlbumShareFile(artist: String, album: String): Uri =
        buildScopedShareFile(
            fileNameHint = "${artist}_$album",
            filter = { full ->
                full.copy(
                    tracks = full.tracks
                        .filter { it.artist == artist && it.album == album }
                        .sortedBy { it.trackPosition ?: Int.MAX_VALUE },
                    favoriteAlbums = full.favoriteAlbums.filter { it.artist == artist && it.album == album },
                    playlists = emptyList(),
                )
            },
            label = { scoped -> "Álbum: $artist – $album (${scoped.tracks.size} pistas)" },
        )

    /** Niveles 4 y 6: Tema de álbum / Sencillo -- una única pista, mismo mecanismo para ambos. */
    suspend fun buildSingleTrackShareFile(youtubeId: String): Uri =
        buildScopedShareFile(
            fileNameHint = "tema",
            filter = { full ->
                val track = full.tracks.firstOrNull { it.youtubeId == youtubeId }
                full.copy(tracks = listOfNotNull(track), favoriteAlbums = emptyList(), playlists = emptyList())
            },
            label = { scoped -> "Tema: ${scoped.tracks.firstOrNull()?.title ?: "(no encontrado)"}" },
        )

    /** Nivel 5: Sencillos -- pistas favoritas sin álbum asignado (sueltas, no parte de ningún disco). */
    suspend fun buildFavoriteSinglesShareFile(): Uri =
        buildScopedShareFile(
            fileNameHint = "sencillos_favoritos",
            filter = { full ->
                full.copy(
                    tracks = full.tracks.filter { it.isFavorite && it.album.isNullOrBlank() },
                    favoriteAlbums = emptyList(),
                    playlists = emptyList(),
                )
            },
            label = { scoped -> "Sencillos favoritos (${scoped.tracks.size} pistas)" },
        )

    /** Niveles 7 y 8: Listas de reproducción / Lista de reproducción -- una playlist concreta, con su orden real. */
    suspend fun buildPlaylistShareFile(playlistId: Long): Uri {
        val full = backupRepository.buildCurrentBundle()
        val playlistDto = full.playlists.firstOrNull { it.originalId == playlistId }
            ?: return writeShareFile(
                ShareBundle(
                    scopeLabel = "Lista de reproducción (vacía o sin pistas descargadas)",
                    sharedAt = System.currentTimeMillis(),
                    bundle = full.copy(tracks = emptyList(), favoriteAlbums = emptyList(), playlists = emptyList()),
                ),
                fileNameHint = "lista",
            )
        val trackIds = playlistDto.trackYoutubeIdsInOrder.toSet()
        val scoped = full.copy(
            tracks = full.tracks.filter { it.youtubeId in trackIds },
            favoriteAlbums = emptyList(),
            playlists = listOf(playlistDto),
        )
        return writeShareFile(
            ShareBundle(
                scopeLabel = "Lista de reproducción: ${playlistDto.name} (${scoped.tracks.size} pistas)",
                sharedAt = System.currentTimeMillis(),
                bundle = scoped,
            ),
            fileNameHint = playlistDto.name,
        )
    }

    /**
     * Escribe `cacheDir/share_files/{nombre}.mimoo` (GZIP de JSON, sin
     * Base64/prefijo) y devuelve un `content://` Uri vía FileProvider
     * -- mismo patrón exacto que `AppUpdateRepository.downloadApk()`
     * ya usa para compartir el APK de actualización. `fileNameHint` se
     * limpia de caracteres no válidos para nombre de archivo (nunca se
     * usa tal cual: viene de datos reales de usuario -- artista,
     * álbum, nombre de playlist -- que pueden llevar "/", ":", etc.).
     */
    private suspend fun writeShareFile(shareBundle: ShareBundle, fileNameHint: String): Uri =
        withContext(Dispatchers.IO) {
            val json = gson.toJson(shareBundle)
            val shareDir = File(context.cacheDir, "share_files").apply { mkdirs() }
            val safeName = fileNameHint
                .ifBlank { "compartido" }
                .replace(Regex("[^A-Za-z0-9À-ÿ _-]"), "_")
                .take(60)
            val file = File(shareDir, "$safeName$SHARE_FILE_EXTENSION")
            GZIPOutputStream(file.outputStream()).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

    /**
     * Decodifica un archivo `.mimoo` recibido, por su Uri (típicamente
     * `content://`, ver `MainActivity.handleShareFileIntent()`). Nunca
     * lanza excepciones de bajo nivel (GZIP/Gson) hasta la UI -- todas
     * se envuelven en [ShareParseException] con un mensaje legible,
     * mismo criterio que `BackupRepository.fromJson()`.
     */
    suspend fun decodeFile(uri: Uri): ShareBundle = withContext(Dispatchers.IO) {
        val json = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                GZIPInputStream(input).use { it.readBytes().toString(Charsets.UTF_8) }
            } ?: throw ShareParseException("No se pudo abrir el archivo compartido.")
        } catch (e: ShareParseException) {
            throw e
        } catch (e: Exception) {
            throw ShareParseException("El archivo está corrupto o incompleto.", e)
        }
        val shareBundle = try {
            gson.fromJson(json, ShareBundle::class.java)
        } catch (e: JsonSyntaxException) {
            throw ShareParseException("El archivo no tiene un formato válido de MiMoo.", e)
        } ?: throw ShareParseException("El archivo está vacío.")

        if (shareBundle.version != ShareBundle.CURRENT_VERSION) {
            throw ShareParseException(
                "Este archivo es de la versión ${shareBundle.version}, pero esta versión de " +
                    "MiMoo solo sabe leer la versión ${ShareBundle.CURRENT_VERSION}."
            )
        }
        shareBundle
    }

    class ShareParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
}

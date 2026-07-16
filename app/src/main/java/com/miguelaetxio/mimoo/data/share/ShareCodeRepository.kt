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
 * Extensión real del archivo de compartición H10 (tercer rediseño,
 * S011). `.txt` en vez de `.mimoo` -- ver comentario de
 * [SHARE_FILE_MARKER] para el porqué.
 */
const val SHARE_FILE_EXTENSION = ".txt"

/**
 * Marca interna al principio del archivo que identifica un `.txt`
 * como un archivo de compartición real de MiMoo (H10, S011, tercer
 * rediseño -- propuesta de Miguel Ángel tras confirmar en dispositivo
 * real que ni el tipo MIME propio ni la extensión propia
 * funcionaban, ver `DOCS/ANNEX_H10.md` para el diagnóstico completo:
 * desde Android 7, las URIs `content://` que dan WhatsApp/el
 * explorador de archivos al abrir un documento recibido son opacas y
 * `MimeTypeMap` no reconoce extensiones propias como `.mimoo`, así
 * que Android nunca sabe qué tipo asignarle y ninguna app aparece en
 * "Abrir con").
 *
 * `.txt` sí es un tipo reconocido de fábrica por Android
 * (`text/plain`, a veces `application/txt` según la app que lo
 * comparta -- de ahí que el manifiesto registre ambos), así que el
 * problema de fondo desaparece. Esta marca es lo que distingue un
 * `.txt` real de MiMoo de cualquier otro `.txt` que le llegue a
 * Miguel Ángel por error -- sin ella, MiMoo aparecería en "Abrir con"
 * para CUALQUIER texto suelto sin forma de saber si es suyo.
 */
const val SHARE_FILE_MARKER = "MIMOO-SHARE-V1:"

/**
 * Construye el [ShareBundle] para cada nivel de compartición y lo
 * materializa como un archivo `.txt` real (H10, S011 -- tercer
 * rediseño, ver [SHARE_FILE_MARKER]). Historial completo del porqué
 * de cada vuelta en `DOCS/ANNEX_H10.md`.
 *
 * Formato del archivo: texto plano UTF-8,
 * `"MIMOO-SHARE-V1:" + Base64URL(GZIP(JSON(ShareBundle)))` -- Base64
 * porque un `.txt` de verdad debe contener texto válido, no bytes
 * binarios crudos (a diferencia del rediseño anterior, que al ya no
 * depender de sobrevivir como texto había quitado el Base64; vuelve
 * a hacer falta ahora que el contenedor es un `.txt`).
 *
 * Deliberadamente separado de `BackupRepository` (H06) aunque
 * reutiliza su `BackupBundle` -- `BackupRepository` habla de
 * exportar/importar TODO el repositorio a Drive; este repositorio
 * habla de compartir un subconjunto arbitrario por cualquier medio.
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
     * archivo `.txt` en vez de un archivo en Drive.
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
     * Escribe `cacheDir/share_files/{nombre}.txt` (texto UTF-8: marca
     * + Base64 de GZIP de JSON) y devuelve un `content://` Uri vía
     * FileProvider -- mismo patrón exacto que
     * `AppUpdateRepository.downloadApk()` ya usa para compartir el
     * APK de actualización. `fileNameHint` se limpia de caracteres no
     * válidos para nombre de archivo (nunca se usa tal cual: viene de
     * datos reales de usuario -- artista, álbum, nombre de playlist --
     * que pueden llevar "/", ":", etc.).
     */
    private suspend fun writeShareFile(shareBundle: ShareBundle, fileNameHint: String): Uri =
        withContext(Dispatchers.IO) {
            val json = gson.toJson(shareBundle)
            val gzipped = java.io.ByteArrayOutputStream().use { byteStream ->
                GZIPOutputStream(byteStream).use { it.write(json.toByteArray(Charsets.UTF_8)) }
                byteStream.toByteArray()
            }
            val base64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(gzipped)
            val text = SHARE_FILE_MARKER + base64

            val shareDir = File(context.cacheDir, "share_files").apply { mkdirs() }
            val safeName = fileNameHint
                .ifBlank { "compartido" }
                .replace(Regex("[^A-Za-z0-9À-ÿ _-]"), "_")
                .take(60)
            val file = File(shareDir, "$safeName$SHARE_FILE_EXTENSION")
            file.writeText(text, Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

    /**
     * Decodifica un archivo `.txt` recibido, por su Uri (típicamente
     * `content://`, ver `MainActivity.handleShareFileIntent()`).
     * Comprueba primero la marca [SHARE_FILE_MARKER] -- si no está,
     * es un `.txt` cualquiera que no es de MiMoo, y se rechaza con un
     * mensaje amable en vez de intentar decodificarlo (petición
     * implícita de Miguel Ángel: MiMoo va a aparecer en "Abrir con"
     * para cualquier `.txt`, así que tiene que distinguir con
     * elegancia). Nunca lanza excepciones de bajo nivel
     * (Base64/GZIP/Gson) hasta la UI -- todas se envuelven en
     * [ShareParseException] con un mensaje legible.
     */
    suspend fun decodeFile(uri: Uri): ShareBundle = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw ShareParseException("No se pudo abrir el archivo compartido.")
        } catch (e: ShareParseException) {
            throw e
        } catch (e: Exception) {
            throw ShareParseException("No se pudo leer el archivo compartido.", e)
        }

        if (!text.startsWith(SHARE_FILE_MARKER)) {
            throw ShareParseException("Este archivo de texto no es un archivo de compartición de MiMoo.")
        }
        val base64 = text.removePrefix(SHARE_FILE_MARKER).trim()

        val json = try {
            val gzipped = java.util.Base64.getUrlDecoder().decode(base64)
            GZIPInputStream(gzipped.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
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

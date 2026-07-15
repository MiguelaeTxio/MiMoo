package com.miguelaetxio.mimoo.data.share

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.miguelaetxio.mimoo.data.backup.BackupBundle
import com.miguelaetxio.mimoo.data.backup.BackupRepository
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prefijo literal del código de compartición (H10, S011, formato
 * pedido por Miguel Ángel: "miMoo+hash"). Se comprueba con
 * `startsWith` tanto al generar como al recibir -- es lo que permite
 * distinguir un texto compartido que es un código real de MiMoo de
 * cualquier otro texto/enlace que alguien comparta con la app (una
 * vez registrada como destino de "Compartir" de texto plano, ver
 * AndroidManifest.xml).
 */
const val SHARE_CODE_PREFIX = "miMoo+"

/**
 * Codifica/decodifica códigos de compartición H10 y construye el
 * [ShareBundle] para cada nivel. Deliberadamente separado de
 * `BackupRepository` (H06) aunque reutiliza su `BackupBundle` --
 * `BackupRepository` habla de exportar/importar TODO el repositorio a
 * Drive; este repositorio habla de compartir un subconjunto arbitrario
 * por cualquier medio (WhatsApp, etc.), con su propio formato de texto
 * comprimido en vez de un archivo JSON subido a Drive.
 *
 * Formato real del código: `"miMoo+" + Base64URL(GZIP(JSON(ShareBundle)))`.
 * GZIP porque un bundle de Biblioteca completa (cientos de pistas)
 * como JSON plano sería un texto larguísimo para pegar/compartir --
 * comprimido baja sustancialmente. Base64 URL-safe sin relleno de
 * saltos de línea (`NO_WRAP`) para que el resultado sea una única
 * línea de texto pegable en cualquier sitio (WhatsApp, SMS, etc.) sin
 * que el propio medio la corte o la reformatee.
 * ---
 * Encodes/decodes H10 share codes and builds the [ShareBundle] for
 * each level. Deliberately separate from `BackupRepository` (H06)
 * even though it reuses its `BackupBundle` -- `BackupRepository` is
 * about exporting/importing the WHOLE repository to Drive; this
 * repository is about sharing an arbitrary subset through any channel
 * (WhatsApp, etc.), with its own compressed text format instead of a
 * JSON file uploaded to Drive.
 *
 * Real code format: `"miMoo+" + Base64URL(GZIP(JSON(ShareBundle)))`.
 * GZIP because a whole-Library bundle (hundreds of tracks) as plain
 * JSON would be a very long text to paste/share -- compression cuts
 * that substantially. URL-safe Base64 with no line-wrap (`NO_WRAP`)
 * so the result is a single pasteable line of text anywhere
 * (WhatsApp, SMS, etc.) without the channel itself breaking or
 * reformatting it.
 */
@Singleton
class ShareCodeRepository @Inject constructor(
    private val backupRepository: BackupRepository,
) {
    private val gson: Gson = GsonBuilder().create()

    /**
     * Nivel 1 de la lista de Miguel Ángel (S011): Biblioteca completa.
     * Reutiliza `BackupRepository.buildCurrentBundle()` tal cual --
     * "compartir toda la biblioteca" es, por definición, el mismo
     * contenido que "exportar todo" (H06), solo que el destino es un
     * código de texto en vez de un archivo en Drive.
     */
    suspend fun buildLibraryShareCode(): String {
        val bundle = backupRepository.buildCurrentBundle()
        val shareBundle = ShareBundle(
            scopeLabel = "Biblioteca completa (${bundle.tracks.size} pistas)",
            sharedAt = System.currentTimeMillis(),
            bundle = bundle,
        )
        return encode(shareBundle)
    }

    /**
     * Niveles 2-8 (S011): todos parten del mismo bundle completo que
     * ya construye `buildCurrentBundle()` (solo pistas realmente
     * descargadas, sin filas sintéticas -- mismo filtro que H06/H07)
     * y lo recortan al subconjunto pedido. Ninguno hace una consulta
     * nueva a Room -- reutiliza el bundle entero y filtra en Kotlin,
     * igual de correcto para el tamaño real de una biblioteca personal
     * y sin duplicar la lógica de exclusión de pistas sintéticas/no
     * descargadas que ya tiene `buildCurrentBundle()`. `label` recibe
     * el bundle YA filtrado, para poder incluir el recuento real de
     * pistas en la etiqueta.
     */
    private suspend fun buildScopedShareCode(
        filter: (BackupBundle) -> BackupBundle,
        label: (BackupBundle) -> String,
    ): String {
        val full = backupRepository.buildCurrentBundle()
        val scoped = filter(full)
        return encode(ShareBundle(scopeLabel = label(scoped), sharedAt = System.currentTimeMillis(), bundle = scoped))
    }

    /** Nivel 2: Artista -- todas las pistas descargadas de ese artista, sin playlists. */
    suspend fun buildArtistShareCode(artist: String): String =
        buildScopedShareCode(
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
    suspend fun buildAlbumShareCode(artist: String, album: String): String =
        buildScopedShareCode(
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
    suspend fun buildSingleTrackShareCode(youtubeId: String): String =
        buildScopedShareCode(
            filter = { full ->
                val track = full.tracks.firstOrNull { it.youtubeId == youtubeId }
                full.copy(tracks = listOfNotNull(track), favoriteAlbums = emptyList(), playlists = emptyList())
            },
            label = { scoped -> "Tema: ${scoped.tracks.firstOrNull()?.title ?: "(no encontrado)"}" },
        )

    /** Nivel 5: Sencillos -- pistas favoritas sin álbum asignado (sueltas, no parte de ningún disco). */
    suspend fun buildFavoriteSinglesShareCode(): String =
        buildScopedShareCode(
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
    suspend fun buildPlaylistShareCode(playlistId: Long): String {
        val full = backupRepository.buildCurrentBundle()
        val playlistDto = full.playlists.firstOrNull { it.originalId == playlistId }
            ?: return encode(
                ShareBundle(
                    scopeLabel = "Lista de reproducción (vacía o sin pistas descargadas)",
                    sharedAt = System.currentTimeMillis(),
                    bundle = full.copy(tracks = emptyList(), favoriteAlbums = emptyList(), playlists = emptyList()),
                )
            )
        val trackIds = playlistDto.trackYoutubeIdsInOrder.toSet()
        val scoped = full.copy(
            tracks = full.tracks.filter { it.youtubeId in trackIds },
            favoriteAlbums = emptyList(),
            playlists = listOf(playlistDto),
        )
        return encode(
            ShareBundle(
                scopeLabel = "Lista de reproducción: ${playlistDto.name} (${scoped.tracks.size} pistas)",
                sharedAt = System.currentTimeMillis(),
                bundle = scoped,
            )
        )
    }

    private fun encode(shareBundle: ShareBundle): String {
        val json = gson.toJson(shareBundle)
        val gzipped = ByteArrayOutputStream().use { byteStream ->
            GZIPOutputStream(byteStream).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            byteStream.toByteArray()
        }
        val base64 = Base64.getUrlEncoder().withoutPadding().encodeToString(gzipped)
        return SHARE_CODE_PREFIX + base64
    }

    /** `true` si `text` tiene pinta de ser un código de compartición H10 -- comprobación barata antes de intentar decodificar. */
    fun looksLikeShareCode(text: String): Boolean = text.trim().startsWith(SHARE_CODE_PREFIX)

    /**
     * Decodifica un código de compartición. Nunca lanza excepciones de
     * bajo nivel (Base64/GZIP/Gson) hasta la UI -- todas se envuelven
     * en [ShareParseException] con un mensaje legible, mismo criterio
     * que `BackupRepository.fromJson()`.
     */
    fun decode(text: String): ShareBundle {
        val trimmed = text.trim()
        if (!trimmed.startsWith(SHARE_CODE_PREFIX)) {
            throw ShareParseException("Este texto no es un código de compartición de MiMoo.")
        }
        val base64 = trimmed.removePrefix(SHARE_CODE_PREFIX)
        val gzipped = try {
            Base64.getUrlDecoder().decode(base64)
        } catch (e: IllegalArgumentException) {
            throw ShareParseException("El código de compartición está corrupto o incompleto.", e)
        }
        val json = try {
            GZIPInputStream(gzipped.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            throw ShareParseException("El código de compartición está corrupto o incompleto.", e)
        }
        val shareBundle = try {
            gson.fromJson(json, ShareBundle::class.java)
        } catch (e: JsonSyntaxException) {
            throw ShareParseException("El código de compartición no tiene un formato válido.", e)
        } ?: throw ShareParseException("El código de compartición está vacío.")

        if (shareBundle.version != ShareBundle.CURRENT_VERSION) {
            throw ShareParseException(
                "Este código es de la versión ${shareBundle.version}, pero esta versión de " +
                    "MiMoo solo sabe leer la versión ${ShareBundle.CURRENT_VERSION}."
            )
        }
        return shareBundle
    }

    class ShareParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
}

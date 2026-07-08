package com.miguelaetxio.mimoo.data.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.miguelaetxio.mimoo.data.local.dao.FavoriteAlbumDao
import com.miguelaetxio.mimoo.data.local.dao.PlaylistDao
import com.miguelaetxio.mimoo.data.local.dao.SearchResultTrackDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Construye y serializa el BackupBundle completo del repositorio
 * (H06 PASO 1). Deliberadamente NO habla con Google Drive -- eso es
 * DriveRepository (PASO 2), todavía sin implementar. Este
 * repositorio solo sabe leer Room y convertir a/desde JSON; PASO 3
 * (pantalla Exportar) y PASO 4 (pantalla Importar) lo combinan con
 * DriveRepository.
 *
 * Mismo patrón Hilt que el resto de repositorios del proyecto
 * (FavoriteAlbumRepository, PlaylistRepository) -- constructor con
 * los DAOs inyectados, @Singleton.
 * ---
 * Builds and serializes the full repository BackupBundle (H06
 * PASO 1). Deliberately does NOT talk to Google Drive -- that's
 * DriveRepository (PASO 2), not implemented yet. This repository
 * only knows how to read Room and convert to/from JSON; PASO 3
 * (Export screen) and PASO 4 (Import screen) combine it with
 * DriveRepository.
 *
 * Same Hilt pattern as the rest of the project's repositories
 * (FavoriteAlbumRepository, PlaylistRepository) -- constructor with
 * injected DAOs, @Singleton.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val trackDao: SearchResultTrackDao,
    private val favoriteAlbumDao: FavoriteAlbumDao,
    private val playlistDao: PlaylistDao,
) {
    private val gson: Gson = GsonBuilder().create()

    /**
     * Construye el BackupBundle actual leyendo Room en el momento de
     * la llamada (no un Flow -- una exportación es una foto fija).
     * Filtra las filas sintéticas (`local:...`) de raíz: no entran en
     * `tracks`, y cualquier playlist que las contuviera queda
     * acortada en `trackYoutubeIdsInOrder` sin ellas -- decisión de
     * Miguel Ángel (S006), ver ANNEX_H06.md.
     * ---
     * Builds the current BackupBundle reading Room at call time (not
     * a Flow -- an export is a snapshot). Filters synthetic rows
     * (`local:...`) at the root: they don't enter `tracks`, and any
     * playlist that contained them ends up shortened in
     * `trackYoutubeIdsInOrder` without them -- Miguel Ángel's
     * decision (S006), see ANNEX_H06.md.
     */
    suspend fun buildCurrentBundle(): BackupBundle {
        val allTracks = trackDao.getAllOnce()
        val exportableTracks = allTracks.filterNot { it.isSyntheticLocalTrack() }
        val exportableYoutubeIds = exportableTracks.map { it.youtubeId }.toSet()

        val favoriteAlbums = favoriteAlbumDao.getAllOnce()

        val playlists = playlistDao.getAllPlaylistsOnce().map { playlist ->
            val trackIdsInOrder = playlistDao
                .getTracksForPlaylistOnce(playlist.id)
                .map { it.youtubeId }
                .filter { it in exportableYoutubeIds }

            PlaylistBackupDto(
                originalId = playlist.id,
                name = playlist.name,
                createdAt = playlist.createdAt,
                trackYoutubeIdsInOrder = trackIdsInOrder,
            )
        }

        return BackupBundle(
            exportedAt = System.currentTimeMillis(),
            tracks = exportableTracks.map { it.toBackupDto() },
            favoriteAlbums = favoriteAlbums.map { it.toBackupDto() },
            playlists = playlists,
        )
    }

    fun toJson(bundle: BackupBundle): String = gson.toJson(bundle)

    /**
     * Excepción propia para no propagar JsonSyntaxException/
     * IllegalStateException de Gson tal cual hasta la UI -- PASO 4
     * (pantalla Importar) debe poder mostrar un mensaje claro sin
     * conocer detalles de la librería de serialización usada.
     * ---
     * Own exception so JsonSyntaxException/IllegalStateException from
     * Gson don't propagate as-is up to the UI -- PASO 4 (Import
     * screen) should be able to show a clear message without knowing
     * details of the serialization library in use.
     */
    class BackupParseException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    /**
     * Deserializa un JSON de backup, rechazando explícitamente
     * cualquier `version` distinta de la reconocida en vez de
     * intentar leerla a ciegas (ver comentario de BackupBundle).
     * ---
     * Deserializes a backup JSON, explicitly rejecting any `version`
     * other than the recognized one instead of trying to read it
     * blindly (see BackupBundle's comment).
     */
    fun fromJson(json: String): BackupBundle {
        val bundle = try {
            gson.fromJson(json, BackupBundle::class.java)
        } catch (e: JsonSyntaxException) {
            throw BackupParseException("El archivo no es un backup de MiMoo válido (JSON malformado).", e)
        } ?: throw BackupParseException("El archivo está vacío o no tiene el formato esperado.")

        if (bundle.version != BackupBundle.CURRENT_VERSION) {
            throw BackupParseException(
                "Este backup es de la versión ${bundle.version}, pero esta versión de " +
                    "MiMoo solo sabe leer la versión ${BackupBundle.CURRENT_VERSION}."
            )
        }
        return bundle
    }
}

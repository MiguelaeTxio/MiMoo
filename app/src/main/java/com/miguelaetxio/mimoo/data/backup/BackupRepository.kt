package com.miguelaetxio.mimoo.data.backup

import com.google.gson.Gson
import com.miguelaetxio.mimoo.data.remote.AnchorDictionary
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.miguelaetxio.mimoo.data.access.UiPreferencesManager
import com.miguelaetxio.mimoo.data.local.dao.ChannelSubscriptionDao
import com.miguelaetxio.mimoo.data.local.dao.FavoriteAlbumDao
import com.miguelaetxio.mimoo.data.local.dao.FavoriteArtistDao
import com.miguelaetxio.mimoo.data.local.dao.FavoritePlaylistDao
import com.miguelaetxio.mimoo.data.local.dao.FavoriteRadioStationDao
import com.miguelaetxio.mimoo.data.local.dao.FavoriteTrackDao
import com.miguelaetxio.mimoo.data.local.dao.PlaylistDao
import com.miguelaetxio.mimoo.data.local.dao.SearchResultTrackDao
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
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
    private val favoriteArtistDao: FavoriteArtistDao,
    private val favoriteTrackDao: FavoriteTrackDao,
    private val favoritePlaylistDao: FavoritePlaylistDao,
    private val playlistDao: PlaylistDao,
    private val favoriteRadioStationDao: FavoriteRadioStationDao,
    private val channelSubscriptionDao: ChannelSubscriptionDao,
    private val uiPreferencesManager: UiPreferencesManager,
    // S025 -- el diccionario del ancla (H08) viaja en la copia, igual
    // que las descargas, los favoritos y las listas.
    private val anchorDictionary: AnchorDictionary,
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
        // Fix real (S008, quinta vuelta): buildCurrentBundle() cogía
        // TODA la tabla de pistas, incluidas las que solo están
        // cacheadas de una búsqueda y nunca se han descargado
        // (DownloadStatus.PENDING/QUEUED/ERROR) -- caché normal de
        // cada búsqueda, ver
        // SearchResultTrackRepository.cacheSearchResults(). Nunca se
        // notó en H06 (Exportar/Importar manual, uso ocasional), pero
        // con la sincronización automática de H07 -- que reencola
        // TODO lo importado, ver AutoSyncViewModel.restoreFromCloud()
        // -- esto disparaba la descarga de decenas de resultados de
        // búsqueda irrelevantes nunca elegidos por Miguel Ángel, con
        // cada ciclo de sincronización. Exportar/sincronizar "tu
        // biblioteca" debe significar lo que de verdad tienes
        // descargado, no el historial de búsquedas.
        // ---
        // Real fix (S008, fifth round): buildCurrentBundle() grabbed
        // the ENTIRE tracks table, including rows only cached from a
        // search and never downloaded
        // (DownloadStatus.PENDING/QUEUED/ERROR) -- normal cache from
        // every search, see
        // SearchResultTrackRepository.cacheSearchResults(). Never
        // noticed in H06 (manual Export/Import, occasional use), but
        // with H07's automatic sync -- which re-queues EVERYTHING
        // imported, see AutoSyncViewModel.restoreFromCloud() -- this
        // triggered downloading dozens of irrelevant search results
        // Miguel Ángel never chose, on every sync cycle. Exporting/
        // syncing "your library" should mean what you actually have
        // downloaded, not your search history.
        val downloadedTracks = allTracks.filter { it.downloadStatus == DownloadStatus.DONE }
        val exportableTracks = downloadedTracks.filterNot { it.isSyntheticLocalTrack() }
        val exportableYoutubeIds = exportableTracks.map { it.youtubeId }.toSet()

        val favoriteAlbums = favoriteAlbumDao.getAllOnce()

        val allPlaylists = playlistDao.getAllPlaylistsOnce()
        val playlists = allPlaylists.map { playlist ->
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

        // Bug real (2026-08-02, ver comentario de BackupBundle.favoriteArtists):
        // favorite_artists nunca viajaba en el bundle. favoritePlaylists se
        // exporta por NOMBRE (playlistId no sirve en destino, se remapea
        // siempre en la importación -- mismo criterio que PlaylistBackupDto).
        val playlistNamesById = allPlaylists.associate { it.id to it.name }
        val favoritePlaylistNames = favoritePlaylistDao.getAllOnce()
            .mapNotNull { fav -> playlistNamesById[fav.playlistId] }

        return BackupBundle(
            exportedAt = System.currentTimeMillis(),
            tracks = exportableTracks.map { it.toBackupDto() },
            favoriteAlbums = favoriteAlbums.map { it.toBackupDto() },
            playlists = playlists,
            radioStations = favoriteRadioStationDao.getAllOnce().map { it.toBackupDto() },
            channelSubscriptions = channelSubscriptionDao.getAllOnce().map { it.toBackupDto() },
            uiSettings = UiSettingsBackupDto(glassBorderEnabled = uiPreferencesManager.glassBorderEnabled.value),
            favoriteArtists = favoriteArtistDao.getAllOnce().map { it.toBackupDto() },
            favoriteTracks = favoriteTrackDao.getAllOnce().map { it.toBackupDto() },
            favoritePlaylists = favoritePlaylistNames.map { FavoritePlaylistBackupDto(playlistName = it) },
            anchorArtists = anchorDictionary.learnedArtistsSnapshot().map {
                AnchorArtistBackupDto(
                    artist = it.artist,
                    country = it.country,
                    genres = it.genres,
                    source = it.source,
                )
            },
            anchorTracks = anchorDictionary.learnedTracksSnapshot().map {
                AnchorTrackBackupDto(
                    artist = it.artist,
                    title = it.title,
                    year = it.year,
                    source = it.source,
                )
            },
        )
    }

    fun toJson(bundle: BackupBundle): String = gson.toJson(bundle)

    /**
     * Corrige de raíz un bug real (2026-08-02, reportado por Miguel
     * Ángel: "Parameter specified as non-null is null: ...
     * collectionSizeOrDefault" al sincronizar con Drive) -- MISMO
     * mecanismo que el ya conocido y parcheado para `envelope.bundle`
     * más abajo, pero generalizado a TODOS los campos de lista de
     * `BackupBundle` que pueden faltar en una copia de versión
     * anterior. `BackupBundle` no tiene todos sus campos con valor por
     * defecto (`exportedAt`, `tracks`... no lo tienen), así que Gson
     * NUNCA llama al constructor de Kotlin para construirlo -- usa
     * `Unsafe.allocateInstance()`, que reserva memoria a ceros y
     * rellena campo a campo solo lo que SÍ aparece en el JSON. Un
     * campo ausente (p.ej. `favoriteArtists` en una copia de una
     * build anterior a hoy) queda en `null` real en tiempo de
     * ejecución, pese a estar declarado no-nulo con
     * `= emptyList()` -- ese valor por defecto de Kotlin JAMÁS se
     * aplica por esta vía. El comentario anterior de `fromJson()`
     * ("una copia de versión 2 se lee perfectamente... llegan
     * vacíos") era incorrecto -- esto es precisamente lo que
     * reventaba hoy.
     *
     * Se corrige aquí, una sola vez, para todo campo de lista que
     * MIN_READABLE_VERSION permite que falte -- no solo los de hoy
     * (favoriteArtists/favoriteTracks/favoritePlaylists), también los
     * de S025 (anchorArtists/anchorTracks), que tenían exactamente el
     * mismo fallo latente sin haberse disparado todavía.
     * ---
     * Fixes a real bug at the root (2026-08-02, reported by Miguel
     * Ángel: "Parameter specified as non-null is null: ...
     * collectionSizeOrDefault" while syncing with Drive) -- SAME
     * mechanism as the one already known and patched for
     * `envelope.bundle` below, but generalized to EVERY list field of
     * `BackupBundle` that can be absent from an older copy.
     * `BackupBundle` doesn't have every field defaulted (`exportedAt`,
     * `tracks`... don't have one), so Gson NEVER calls Kotlin's
     * constructor to build it -- it uses `Unsafe.allocateInstance()`,
     * which zeroes out memory and fills in field by field only what
     * IS present in the JSON. A missing field (e.g. `favoriteArtists`
     * in a copy from a build older than today) ends up genuinely
     * `null` at runtime, despite being declared non-null with
     * `= emptyList()` -- that Kotlin default is NEVER applied through
     * this path. `fromJson()`'s previous comment ("a version-2 copy
     * reads perfectly fine... arrive empty") was wrong -- this is
     * exactly what blew up today.
     *
     * Fixed here, once, for every list field that MIN_READABLE_VERSION
     * allows to be missing -- not just today's
     * (favoriteArtists/favoriteTracks/favoritePlaylists), also S025's
     * (anchorArtists/anchorTracks), which had the exact same latent
     * flaw without having triggered it yet.
     */
    @Suppress("SENSELESS_COMPARISON")
    private fun BackupBundle.normalizeNullableLists(): BackupBundle = copy(
        tracks = tracks ?: emptyList(),
        favoriteAlbums = favoriteAlbums ?: emptyList(),
        playlists = playlists ?: emptyList(),
        radioStations = radioStations ?: emptyList(),
        channelSubscriptions = channelSubscriptions ?: emptyList(),
        anchorArtists = anchorArtists ?: emptyList(),
        anchorTracks = anchorTracks ?: emptyList(),
        favoriteArtists = favoriteArtists ?: emptyList(),
        favoriteTracks = favoriteTracks ?: emptyList(),
        favoritePlaylists = favoritePlaylists ?: emptyList(),
    )

    /**
     * Serializa un [SyncEnvelope] completo (bundle + quién + cuándo)
     * -- usado solo por la copia de respaldo automática (H07 PARTE 1),
     * nunca por Exportar/Importar manual (H06), que sigue usando
     * `toJson(bundle)` a secas.
     * ---
     * Serializes a full [SyncEnvelope] (bundle + who + when) -- used
     * only by the automatic backup copy (H07 PART 1), never by manual
     * Export/Import (H06), which keeps using plain `toJson(bundle)`.
     */
    fun toSyncJson(envelope: SyncEnvelope): String = gson.toJson(envelope)

    /**
     * Deserializa un [SyncEnvelope]. Reutiliza el mismo criterio de
     * `version` que `fromJson()` -- si el `bundle` interno trae una
     * versión que esta app no reconoce, falla con el mismo mensaje
     * claro en vez de intentar leerlo a ciegas.
     * ---
     * Deserializes a [SyncEnvelope]. Reuses the same `version`
     * criterion as `fromJson()` -- if the inner `bundle` carries a
     * version this app doesn't recognize, it fails with the same
     * clear message instead of trying to read it blindly.
     */
    fun fromSyncJson(json: String): SyncEnvelope {
        val envelope = try {
            gson.fromJson(json, SyncEnvelope::class.java)
        } catch (e: JsonSyntaxException) {
            throw BackupParseException(
                "La copia de respaldo automática de Drive no es válida (JSON malformado).", e
            )
        } ?: throw BackupParseException(
            "La copia de respaldo automática de Drive está vacía o no tiene el formato esperado."
        )

        // Gson no respeta la nulabilidad de Kotlin: si el JSON no
        // tiene el campo "bundle" (p.ej. un archivo del formato
        // ANTERIOR a esta sesión -- BackupBundle pelado, sin sobre --
        // que ya hubiera en Drive de pruebas previas), envelope.bundle
        // queda en null en tiempo de ejecución pese a estar declarado
        // no-nulo, y acceder a .version revienta con un
        // NullPointerException críptico en vez de un error claro --
        // bug real reportado por Miguel Ángel, reproducido con un
        // archivo de sesión anterior en Drive. Se comprueba explícito
        // aquí para convertirlo en un BackupParseException legible y
        // capturable, igual que cualquier otro fallo de formato.
        // ---
        // Gson doesn't respect Kotlin's nullability: if the JSON is
        // missing the "bundle" field (e.g. a file from the format
        // BEFORE this session -- a bare BackupBundle, no envelope --
        // already sitting in Drive from earlier testing),
        // envelope.bundle ends up null at runtime despite being
        // declared non-null, and accessing .version blows up with a
        // cryptic NullPointerException instead of a clear error --
        // real bug reported by Miguel Ángel, reproduced with a file
        // left over from an earlier session in Drive. Checked
        // explicitly here to turn it into a readable, catchable
        // BackupParseException, same as any other format failure.
        @Suppress("SENSELESS_COMPARISON")
        if (envelope.bundle == null) {
            throw BackupParseException(
                "La copia de respaldo automática de Drive tiene un formato antiguo o " +
                    "incompleto (sin datos de dispositivo)."
            )
        }

        if (!BackupBundle.canRead(envelope.bundle.version)) {
            throw BackupParseException(
                "Esta copia de respaldo automática es de la versión ${envelope.bundle.version}, " +
                    "pero esta versión de MiMoo sabe leer hasta la " +
                    "${BackupBundle.CURRENT_VERSION}."
            )
        }
        return envelope.copy(bundle = envelope.bundle.normalizeNullableLists())
    }

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
     * cualquier `version` que no sepa leer, en vez de intentarlo a
     * ciegas (ver comentario de BackupBundle).
     *
     * S025 -- deja de ser igualdad estricta. Al subir el bundle a la
     * versión 3 para llevar el diccionario del ancla, una comprobación
     * `!=` habría dejado ilegibles de golpe TODAS las copias que ya hay
     * en Drive, incluida la del dispositivo de Silvia.
     *
     * 2026-08-02 -- corregido el comentario anterior, que era
     * incorrecto ("una copia de versión 2 se lee perfectamente, los
     * campos nuevos llegan vacíos"): sin `normalizeNullableLists()`,
     * esos campos llegaban `null` de verdad, no vacíos -- ver el
     * comentario de esa función para el mecanismo real.
     * ---
     * Deserializes a backup JSON, explicitly rejecting any `version`
     * other than the recognized one instead of trying to read it
     * blindly (see BackupBundle's comment).
     *
     * 2026-08-02 -- corrected the comment above, which was wrong ("a
     * version-2 copy reads perfectly fine, new fields arrive empty"):
     * without `normalizeNullableLists()`, those fields arrived
     * genuinely `null`, not empty -- see that function's comment for
     * the real mechanism.
     */
    fun fromJson(json: String): BackupBundle {
        val bundle = try {
            gson.fromJson(json, BackupBundle::class.java)
        } catch (e: JsonSyntaxException) {
            throw BackupParseException("El archivo no es un backup de MiMoo válido (JSON malformado).", e)
        } ?: throw BackupParseException("El archivo está vacío o no tiene el formato esperado.")

        if (!BackupBundle.canRead(bundle.version)) {
            throw BackupParseException(
                "Este backup es de la versión ${bundle.version}, pero esta versión de " +
                    "MiMoo sabe leer hasta la ${BackupBundle.CURRENT_VERSION}."
            )
        }
        return bundle.normalizeNullableLists()
    }
}

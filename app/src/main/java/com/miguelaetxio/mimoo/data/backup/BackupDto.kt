package com.miguelaetxio.mimoo.data.backup

import com.miguelaetxio.mimoo.data.local.entity.ChannelSubscription
import com.miguelaetxio.mimoo.data.local.entity.FavoriteAlbum
import com.miguelaetxio.mimoo.data.local.entity.FavoriteArtist
import com.miguelaetxio.mimoo.data.local.entity.FavoritePlaylist
import com.miguelaetxio.mimoo.data.local.entity.FavoriteRadioStation
import com.miguelaetxio.mimoo.data.local.entity.FavoriteTrack
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack

/**
 * Formato de archivo de exportación/importación del repositorio
 * completo (H06). Deliberadamente independiente de las entidades
 * Room reales (SearchResultTrack, FavoriteAlbum, Playlist,
 * PlaylistTrackCrossRef, FavoriteRadioStation, ChannelSubscription)
 * para que el formato de archivo no quede acoplado al esquema interno
 * de la base de datos -- un cambio futuro de esquema no debería
 * romper la lectura de backups antiguos sin más ceremonia que un
 * salto de `version`.
 *
 * `version` sube a 2 (H07, Ampliación S014/S015 -- "réplica total"
 * pedida explícitamente por Miguel Ángel): se añaden
 * `radioStations`/`channelSubscriptions`/`uiSettings`, ausentes hasta
 * ahora del bundle -- ver `DOCS/ANNEX_H07.md`. `BackupRepository`
 * rechaza con un mensaje claro cualquier versión que no reconozca, en
 * vez de intentar leerla a ciegas -- una copia de Drive de la versión
 * 1 (anterior a este cambio) se trata como remoto ilegible y este
 * dispositivo la sobreescribe, mismo criterio que un JSON corrupto.
 * ---
 * File format for exporting/importing the whole repository (H06).
 * Deliberately independent from the real Room entities
 * (SearchResultTrack, FavoriteAlbum, Playlist,
 * PlaylistTrackCrossRef, FavoriteRadioStation, ChannelSubscription)
 * so the file format isn't coupled to the internal DB schema -- a
 * future schema change shouldn't break reading old backups beyond
 * bumping `version`.
 *
 * `version` bumps to 2 (H07, S014/S015 extension -- "total replica"
 * explicitly requested by Miguel Ángel): adds
 * `radioStations`/`channelSubscriptions`/`uiSettings`, missing from
 * the bundle until now -- see `DOCS/ANNEX_H07.md`. `BackupRepository`
 * rejects any version it doesn't recognize with a clear message
 * instead of trying to read it blindly -- a version-1 Drive copy
 * (from before this change) is treated as an unreadable remote and
 * this device overwrites it, same as a corrupt JSON.
 */
data class BackupBundle(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long,
    val tracks: List<TrackBackupDto>,
    val favoriteAlbums: List<FavoriteAlbumBackupDto>,
    val playlists: List<PlaylistBackupDto>,
    val radioStations: List<FavoriteRadioStationBackupDto>,
    val channelSubscriptions: List<ChannelSubscriptionBackupDto>,
    val uiSettings: UiSettingsBackupDto,
    /**
     * S025 -- diccionario del ancla APRENDIDO en este dispositivo
     * (H08). Solo lo aprendido: la semilla va dentro del APK y es
     * idéntica en los dos teléfonos.
     *
     * Orden de Miguel Ángel: *"debe guardarse en Drive cuando se haga
     * la copia para persistir esa base de datos entre dispositivos. Lo
     * mismo que persistimos las grabaciones, los links, los
     * favoritos."*
     *
     * Con valor por defecto para que una copia de versión 2 siga
     * importándose sin tocarla: simplemente no trae diccionario.
     */
    val anchorArtists: List<AnchorArtistBackupDto> = emptyList(),
    val anchorTracks: List<AnchorTrackBackupDto> = emptyList(),
    /**
     * Bug real reportado por Miguel Ángel (2026-08-02): un artista
     * favorito en el móvil no aparecía en la tablet tras sincronizar.
     * Causa raíz: favorite_artists (H12) nunca se incluyó en el
     * bundle de sincronización -- se quedó fuera desde que se creó la
     * tabla, a diferencia de favorite_albums, que sí viaja desde H06.
     * Se corrige aquí sumando también los favoritos nuevos de esta
     * misma sesión (favorite_tracks, favorite_playlists -- sesión de
     * diseño de Favoritos, 2026-08-02) para no dejar el mismo hueco
     * recién abierto. Con valor por defecto para que una copia de
     * versión anterior siga importándose sin tocarla.
     * ---
     * Real bug reported by Miguel Ángel (2026-08-02): a favorite
     * artist on the phone didn't show up on the tablet after
     * syncing. Root cause: favorite_artists (H12) was never included
     * in the sync bundle -- left out since the table was created,
     * unlike favorite_albums, which has traveled since H06. Fixed
     * here, also adding this same session's new favorites
     * (favorite_tracks, favorite_playlists -- Favorites design
     * session, 2026-08-02) so as not to leave the same gap freshly
     * open. Default value so an older-version copy keeps importing
     * without touching it.
     */
    val favoriteArtists: List<FavoriteArtistBackupDto> = emptyList(),
    val favoriteTracks: List<FavoriteTrackBackupDto> = emptyList(),
    val favoritePlaylists: List<FavoritePlaylistBackupDto> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 4

        /**
         * S025 -- versión mínima legible.
         *
         * Hasta aquí la comprobación era `version != CURRENT_VERSION`,
         * igualdad estricta. Al subir a la 3 para llevar el diccionario
         * del ancla, eso habría dejado ilegibles de golpe todas las
         * copias que ya existen en Drive -- las de Miguel Ángel y la
         * del dispositivo de Silvia -- sin ningún motivo: una copia de
         * versión 2 se lee entera, simplemente no trae diccionario y
         * los dos campos nuevos llegan vacíos por defecto.
         *
         * Solo hay que subir este número si alguna vez se cambia un
         * campo YA existente de forma incompatible.
         */
        const val MIN_READABLE_VERSION = 2

        fun canRead(version: Int): Boolean =
            version in MIN_READABLE_VERSION..CURRENT_VERSION
    }
}

/** S025 -- un artista aprendido: país y géneros. */
data class AnchorArtistBackupDto(
    val artist: String,
    val country: String? = null,
    val genres: List<String> = emptyList(),
    val source: String = "",
)

/** S025 -- un tema aprendido: año de su primera edición. */
data class AnchorTrackBackupDto(
    val artist: String,
    val title: String,
    val year: Int,
    val source: String = "",
)

/**
 * Una pista exportable. Deliberadamente SIN filePath/downloadStatus/
 * downloadProgress -- son específicos del dispositivo origen y se
 * reinicializan siempre en la importación (ver PASO 4 del anexo:
 * filePath = null, downloadStatus = PENDING, downloadProgress = 0).
 * youtubeId sí viaja -- es la clave real y estable, no depende del
 * dispositivo.
 * ---
 * An exportable track. Deliberately WITHOUT filePath/downloadStatus/
 * downloadProgress -- those are specific to the source device and
 * are always reset on import (see annex PASO 4: filePath = null,
 * downloadStatus = PENDING, downloadProgress = 0). youtubeId does
 * travel -- it's the real, stable key, device-independent.
 */
data class TrackBackupDto(
    val youtubeId: String,
    val title: String,
    val channelTitle: String,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val artist: String?,
    val album: String?,
    val isFavorite: Boolean,
    val coverArtUrl: String?,
    val trackPosition: Int?,
    val sourceUrl: String?,
)

data class FavoriteAlbumBackupDto(
    val artist: String,
    val album: String,
)

/** Favorito de ARTISTA exportable -- ver comentario de favoriteArtists en BackupBundle. */
data class FavoriteArtistBackupDto(
    val artist: String,
)

fun FavoriteArtist.toBackupDto(): FavoriteArtistBackupDto = FavoriteArtistBackupDto(artist = artist)

/**
 * `originalId` es el id autogenerado de la Playlist en el
 * dispositivo origen -- se conserva solo para trazabilidad y para
 * que dos exports sucesivos del mismo dispositivo sean comparables;
 * la importación (PASO 4) NUNCA lo reutiliza como id real, siempre
 * inserta con id autogenerado nuevo en destino.
 *
 * `trackYoutubeIdsInOrder` aplana PlaylistTrackCrossRef dentro de la
 * propia playlist (orden real, por posición) en vez de exportar la
 * tabla de unión suelta -- simplifica el remapeo de ids en la
 * importación. Si la playlist contenía una pista sintética
 * (`local:...`), su id ya no aparece aquí -- BackupBuilder filtra las
 * pistas sintéticas de raíz, antes de construir esta lista (ver
 * SearchResultTrack.local: en TrackBackupDto/BackupBuilder).
 * ---
 * `originalId` is the Playlist's autogenerated id on the source
 * device -- kept only for traceability and so two successive exports
 * of the same device are comparable; import (PASO 4) NEVER reuses it
 * as a real id, it always inserts with a fresh autogenerated id on
 * the destination.
 *
 * `trackYoutubeIdsInOrder` flattens PlaylistTrackCrossRef inside the
 * playlist itself (real, position-based order) instead of exporting
 * the loose join table -- simplifies id remapping on import. If the
 * playlist contained a synthetic track (`local:...`), its id no
 * longer appears here -- BackupBuilder filters synthetic tracks at
 * the root, before building this list (see SearchResultTrack.local:
 * in TrackBackupDto/BackupBuilder).
 */
data class PlaylistBackupDto(
    val originalId: Long,
    val name: String,
    val createdAt: Long,
    val trackYoutubeIdsInOrder: List<String>,
)

/** Prefijo real de las filas sintéticas generadas por LibraryReconciler -- ver SearchResultTrack. */
private const val SYNTHETIC_TRACK_PREFIX = "local:"

/**
 * `true` si esta fila es sintética (generada por LibraryReconciler
 * para un archivo pegado a mano sin fila real, sin vídeo de YouTube
 * detrás). Decisión de Miguel Ángel (S006): estas filas nunca se
 * exportan.
 */
fun SearchResultTrack.isSyntheticLocalTrack(): Boolean =
    youtubeId.startsWith(SYNTHETIC_TRACK_PREFIX)

fun SearchResultTrack.toBackupDto(): TrackBackupDto = TrackBackupDto(
    youtubeId = youtubeId,
    title = title,
    channelTitle = channelTitle,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    artist = artist,
    album = album,
    isFavorite = isFavorite,
    coverArtUrl = coverArtUrl,
    trackPosition = trackPosition,
    sourceUrl = sourceUrl,
)

fun FavoriteAlbum.toBackupDto(): FavoriteAlbumBackupDto =
    FavoriteAlbumBackupDto(artist = artist, album = album)

/**
 * Favorito de emisora exportable (H07, réplica total). Transporta los
 * mismos campos que la entidad Room -- no hay nada específico del
 * dispositivo que excluir aquí (a diferencia de TrackBackupDto con
 * filePath/downloadStatus): una emisora nunca se descarga, solo se
 * guarda su referencia (ver comentario de FavoriteRadioStation).
 * ---
 * Exportable station favorite (H07, total replica). Carries the same
 * fields as the Room entity -- nothing device-specific to exclude
 * here (unlike TrackBackupDto with filePath/downloadStatus): a
 * station is never downloaded, only its reference is kept (see
 * FavoriteRadioStation's comment).
 */
data class FavoriteRadioStationBackupDto(
    val stationUuid: String,
    val name: String,
    val urlResolved: String,
    val favicon: String?,
    val country: String?,
    val tags: String?,
)

fun FavoriteRadioStation.toBackupDto(): FavoriteRadioStationBackupDto = FavoriteRadioStationBackupDto(
    stationUuid = stationUuid,
    name = name,
    urlResolved = urlResolved,
    favicon = favicon,
    country = country,
    tags = tags,
)

fun FavoriteRadioStationBackupDto.toEntity(): FavoriteRadioStation = FavoriteRadioStation(
    stationUuid = stationUuid,
    name = name,
    urlResolved = urlResolved,
    favicon = favicon,
    country = country,
    tags = tags,
)

/**
 * Favorito de SENCILLO en streaming exportable (sesión de diseño de
 * Favoritos, 2026-08-02) -- ver comentario de la entidad FavoriteTrack.
 * Transporta los mismos campos que la entidad Room, sin nada
 * específico de dispositivo que excluir (nunca se descarga a través
 * de esta tabla).
 * ---
 * Exportable STREAMING single-track favorite (Favorites design
 * session, 2026-08-02) -- see the FavoriteTrack entity's comment.
 * Carries the same fields as the Room entity, nothing device-specific
 * to exclude (never downloaded through this table).
 */
data class FavoriteTrackBackupDto(
    val youtubeId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
)

fun FavoriteTrack.toBackupDto(): FavoriteTrackBackupDto = FavoriteTrackBackupDto(
    youtubeId = youtubeId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
)

fun FavoriteTrackBackupDto.toEntity(): FavoriteTrack = FavoriteTrack(
    youtubeId = youtubeId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
)

/**
 * Marcador de PLAYLIST favorita exportable, por NOMBRE -- no por
 * `playlistId` (sesión de diseño de Favoritos, 2026-08-02): el id es
 * autogenerado y SIEMPRE se remapea en la importación (ver
 * PlaylistBackupDto.originalId, mismo criterio), así que el id de
 * origen no sirve de nada en destino. El nombre es la misma clave que
 * ya usa BackupMirrorRepository para comparar playlists.
 * ---
 * Exportable favorite PLAYLIST marker, by NAME -- not `playlistId`
 * (Favorites design session, 2026-08-02): the id is autogenerated and
 * ALWAYS remapped on import (see PlaylistBackupDto.originalId, same
 * criterion), so the source id is useless on the destination. The
 * name is the same key BackupMirrorRepository already uses to compare
 * playlists.
 */
data class FavoritePlaylistBackupDto(
    val playlistName: String,
)

/**
 * Suscripción de canal exportable (H07, réplica total).
 * Deliberadamente SIN `lastCheckedAt` -- es el reloj propio de
 * `ChannelCheckWorker` en ESTE dispositivo (cuándo comprobó él por
 * última vez contenido nuevo), no un dato de la suscripción en sí;
 * igual criterio que TrackBackupDto excluye filePath/downloadStatus.
 * Se reinicializa a `null` en la importación -- el dispositivo
 * destino comprobará el canal por primera vez en su propio ciclo.
 * ---
 * Exportable channel subscription (H07, total replica). Deliberately
 * WITHOUT `lastCheckedAt` -- it's `ChannelCheckWorker`'s own clock on
 * THIS device (when it last checked for new content), not data about
 * the subscription itself; same criterion as TrackBackupDto excluding
 * filePath/downloadStatus. Reset to `null` on import -- the
 * destination device will check the channel for the first time on its
 * own cycle.
 */
data class ChannelSubscriptionBackupDto(
    val channelId: String,
    val title: String,
    val thumbnailUrl: String?,
    val subscribedAt: Long,
)

fun ChannelSubscription.toBackupDto(): ChannelSubscriptionBackupDto = ChannelSubscriptionBackupDto(
    channelId = channelId,
    title = title,
    thumbnailUrl = thumbnailUrl,
    subscribedAt = subscribedAt,
)

fun ChannelSubscriptionBackupDto.toEntity(): ChannelSubscription = ChannelSubscription(
    channelId = channelId,
    title = title,
    thumbnailUrl = thumbnailUrl,
    subscribedAt = subscribedAt,
    lastCheckedAt = null,
)

/**
 * Ajustes de interfaz exportables (H07, réplica total). Por ahora
 * solo `glassBorderEnabled` (`UiPreferencesManager`, único ajuste que
 * existe hoy). Deliberadamente NO incluye el PIN de acceso
 * (`AccessPinManager`) -- es una credencial de dispositivo, no un
 * ajuste de preferencia, mismo razonamiento que ya recoge
 * `ANNEX_H07.md`; se mantiene fuera de la réplica salvo que Miguel
 * Ángel pida lo contrario.
 * ---
 * Exportable UI settings (H07, total replica). For now only
 * `glassBorderEnabled` (`UiPreferencesManager`, the only setting that
 * exists today). Deliberately does NOT include the access PIN
 * (`AccessPinManager`) -- it's a device credential, not a preference
 * setting, same reasoning already captured in `ANNEX_H07.md`; kept
 * out of the replica unless Miguel Ángel asks otherwise.
 */
data class UiSettingsBackupDto(
    val glassBorderEnabled: Boolean,
)

/**
 * Sobre de la copia de respaldo AUTOMÁTICA (H07 PARTE 1, redefinición
 * S008, regla de negocio de Miguel Ángel) -- envuelve un
 * [BackupBundle] normal con quién lo subió y cuándo, para poder
 * distinguir "esta copia la hice yo mismo" de "esta copia la hizo
 * otro dispositivo". NO se usa para Exportar/Importar manual (H06),
 * que sigue subiendo un [BackupBundle] pelado, sin sobre.
 * ---
 * Envelope for the AUTOMATIC backup copy (H07 PART 1, S008
 * redefinition, Miguel Ángel's business rule) -- wraps a normal
 * [BackupBundle] with who uploaded it and when, to be able to tell
 * "I made this copy myself" apart from "another device made this
 * copy". NOT used for manual Export/Import (H06), which still
 * uploads a bare [BackupBundle], no envelope.
 */
data class SyncEnvelope(
    val deviceId: String,
    val deviceLabel: String,
    val timestamp: Long,
    val bundle: BackupBundle,
    /**
     * Fix real (2026-07-24, petición explícita de Miguel Ángel: "que
     * mi mujer no tenga que importar nada") -- contenido del
     * cookies.txt de YouTube (ver `CookiesManager.kt`), `null` si este
     * dispositivo no tiene ninguno importado. Deliberadamente a este
     * nivel del sobre, NUNCA dentro de `bundle` -- `BackupBundle`
     * también viaja por la exportación/importación manual (H06) y los
     * códigos de compartición (H10), ambos pensados para poder acabar
     * en manos de terceros; este campo solo se lee/escribe desde
     * `AutoSyncPusher`/`AutoSyncViewModel`, el canal privado
     * dispositivo-a-dispositivo de Miguel Ángel vía su propio Drive.
     * ---
     * Real fix (2026-07-24, explicit request from Miguel Ángel: "so my
     * wife doesn't have to import anything") -- YouTube cookies.txt
     * content (see `CookiesManager.kt`), `null` if this device has
     * none imported. Deliberately at this envelope level, NEVER inside
     * `bundle` -- `BackupBundle` also travels through manual export/
     * import (H06) and share codes (H10), both meant to potentially
     * end up in third parties' hands; this field is only read/written
     * by `AutoSyncPusher`/`AutoSyncViewModel`, Miguel Ángel's private
     * device-to-device channel over his own Drive.
     */
    val cookiesTxtContent: String? = null,
)

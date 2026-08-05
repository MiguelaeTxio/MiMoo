package com.miguelaetxio.mimoo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.miguelaetxio.mimoo.data.local.dao.PlaylistDao
import com.miguelaetxio.mimoo.data.local.dao.SearchResultTrackDao
import com.miguelaetxio.mimoo.data.local.dao.FavoriteAlbumDao
import com.miguelaetxio.mimoo.data.local.dao.FavoriteRadioStationDao
import com.miguelaetxio.mimoo.data.local.dao.ChannelSubscriptionDao
import com.miguelaetxio.mimoo.data.local.dao.FavoriteArtistDao
import com.miguelaetxio.mimoo.data.local.dao.ArtistDisambiguationDao
import com.miguelaetxio.mimoo.data.local.dao.FavoriteTrackDao
import com.miguelaetxio.mimoo.data.local.dao.FavoritePlaylistDao
import com.miguelaetxio.mimoo.data.local.dao.DislikedArtistDao
import com.miguelaetxio.mimoo.data.local.dao.DislikedTrackDao
import com.miguelaetxio.mimoo.data.local.entity.FavoriteAlbum
import com.miguelaetxio.mimoo.data.local.entity.FavoriteRadioStation
import com.miguelaetxio.mimoo.data.local.entity.ChannelSubscription
import com.miguelaetxio.mimoo.data.local.entity.Playlist
import com.miguelaetxio.mimoo.data.local.entity.PlaylistTrackCrossRef
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.entity.FavoriteArtist
import com.miguelaetxio.mimoo.data.local.entity.ArtistDisambiguation
import com.miguelaetxio.mimoo.data.local.entity.FavoriteTrack
import com.miguelaetxio.mimoo.data.local.entity.FavoritePlaylist
import com.miguelaetxio.mimoo.data.local.entity.DislikedArtist
import com.miguelaetxio.mimoo.data.local.entity.DislikedTrack

@Database(
    entities = [
        SearchResultTrack::class,
        Playlist::class,
        PlaylistTrackCrossRef::class,
        FavoriteAlbum::class,
        FavoriteRadioStation::class,
        ChannelSubscription::class,
        FavoriteArtist::class,
        ArtistDisambiguation::class,
        FavoriteTrack::class,
        FavoritePlaylist::class,
        DislikedArtist::class,
        DislikedTrack::class,
    ],
    version = 14,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchResultTrackDao(): SearchResultTrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteAlbumDao(): FavoriteAlbumDao
    abstract fun favoriteRadioStationDao(): FavoriteRadioStationDao
    abstract fun channelSubscriptionDao(): ChannelSubscriptionDao
    abstract fun favoriteArtistDao(): FavoriteArtistDao
    abstract fun artistDisambiguationDao(): ArtistDisambiguationDao
    abstract fun favoriteTrackDao(): FavoriteTrackDao
    abstract fun favoritePlaylistDao(): FavoritePlaylistDao
    abstract fun dislikedArtistDao(): DislikedArtistDao
    abstract fun dislikedTrackDao(): DislikedTrackDao

    companion object {
        /**
         * Adds artist/album columns to search_result_tracks. Both are
         * nullable: existing rows get NULL until re-searched (artist
         * repopulates from channelTitle in SearchViewModel.search());
         * album stays NULL until the MusicBrainz milestone or manual
         * edit (PASO 7) exists.
         * ---
         * Anade las columnas artist/album a search_result_tracks. Ambas
         * son nullable: las filas existentes quedan en NULL hasta que
         * se vuelvan a buscar (artist se repuebla desde channelTitle en
         * SearchViewModel.search()); album permanece NULL hasta que
         * exista el hito de MusicBrainz o la edicion manual (PASO 7).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE search_result_tracks ADD COLUMN artist TEXT"
                )
                db.execSQL(
                    "ALTER TABLE search_result_tracks ADD COLUMN album TEXT"
                )
            }
        }

        /**
         * Adds isFavorite to search_result_tracks (PASO 4, H03).
         * NOT NULL with DEFAULT 0: unlike artist/album, a missing
         * favorite flag has one unambiguous correct value (not a
         * favorite) rather than a real "unknown" state, so existing
         * rows get a concrete default instead of NULL.
         * ---
         * Anade isFavorite a search_result_tracks (PASO 4, H03).
         * NOT NULL con DEFAULT 0: a diferencia de artist/album, la
         * ausencia de marcador de favorito tiene un valor correcto
         * inequivoco (no es favorito) en vez de un "desconocido" real,
         * asi que las filas existentes reciben un valor concreto en
         * vez de NULL.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE search_result_tracks " +
                        "ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Adds coverArtUrl to search_result_tracks (PASO 6, H03).
         * Nullable, same reasoning as artist/album: absence of cover
         * art is a real "not resolved yet" state, not a false default,
         * so existing rows get NULL and are picked up lazily by
         * CoverArtRepository the next time their album is rendered in
         * LibraryScreen.
         * ---
         * Anade coverArtUrl a search_result_tracks (PASO 6, H03).
         * Nullable, mismo razonamiento que artist/album: la ausencia
         * de caratula es un estado real de "aun no resuelto", no un
         * falso valor por defecto, asi que las filas existentes
         * quedan en NULL y se resuelven de forma perezosa la proxima
         * vez que su album se renderiza en LibraryScreen.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE search_result_tracks ADD COLUMN coverArtUrl TEXT"
                )
            }
        }

        /**
         * Creates the playlists and playlist_track_cross_refs tables
         * (Hito 04). Does not touch search_result_tracks at all — new
         * tables only, no ALTER on the existing schema.
         * ---
         * Crea las tablas playlists y playlist_track_cross_refs
         * (Hito 04). No toca search_result_tracks en absoluto — solo
         * tablas nuevas, sin ALTER sobre el esquema existente.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlists` (" +
                        "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "`name` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_track_cross_refs` (" +
                        "`playlistId` INTEGER NOT NULL, " +
                        "`youtubeId` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`playlistId`, `youtubeId`), " +
                        "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) " +
                        "ON DELETE CASCADE, " +
                        "FOREIGN KEY(`youtubeId`) REFERENCES `search_result_tracks`(`youtubeId`) " +
                        "ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_playlist_track_cross_refs_youtubeId` " +
                        "ON `playlist_track_cross_refs` (`youtubeId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_playlist_track_cross_refs_playlistId` " +
                        "ON `playlist_track_cross_refs` (`playlistId`)"
                )
            }
        }

        /**
         * Adds downloadProgress to search_result_tracks (pantalla
         * "Descargas"). NOT NULL DEFAULT 0, igual razonamiento que
         * isFavorite: la ausencia de progreso tiene un valor correcto
         * inequivoco (0%) salvo para las filas que ya estan DONE, que
         * reciben un backfill explicito a 100 para que la nueva
         * pantalla no las muestre como "0% descargado" por error.
         * QUEUED es el nuevo valor de DownloadStatus.Converters ya
         * persiste el enum por su .name, asi que no hace falta tocar
         * ninguna columna para soportarlo — solo aplica a filas
         * nuevas a partir de esta version.
         * ---
         * Anade downloadProgress a search_result_tracks (pantalla
         * "Descargas"). NOT NULL DEFAULT 0, mismo razonamiento que
         * isFavorite: la ausencia de progreso tiene un valor correcto
         * inequivoco (0%) salvo para las filas que ya estan DONE, que
         * reciben un backfill explicito a 100 para que la nueva
         * pantalla no las muestre como "0% descargado" por error.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE search_result_tracks " +
                        "ADD COLUMN downloadProgress INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE search_result_tracks SET downloadProgress = 100 " +
                        "WHERE downloadStatus = 'DONE'"
                )
            }
        }

        /**
         * Adds trackPosition to search_result_tracks (orden real de
         * álbum, en vez de siempre alfabético). Nullable sin backfill:
         * las filas existentes no tienen forma de saber su posición
         * real retroactivamente, así que quedan en NULL y
         * LibraryViewModel.recompute() ya cae a orden alfabético para
         * ellas -- exactamente el comportamiento que tenían antes de
         * esta migración, ningún álbum existente empeora.
         * ---
         * Anade trackPosition a search_result_tracks (orden real de
         * album, en vez de siempre alfabetico). Nullable sin backfill:
         * las filas existentes no tienen forma de saber su posicion
         * real retroactivamente, asi que quedan en NULL y
         * LibraryViewModel.recompute() ya cae a orden alfabetico para
         * ellas -- exactamente el comportamiento que tenian antes de
         * esta migracion, ningun album existente empeora.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE search_result_tracks ADD COLUMN trackPosition INTEGER"
                )
            }
        }

        /**
         * Adds sourceUrl to search_result_tracks -- petición explícita
         * de Miguel Ángel (2026-07-04): poder compartir por WhatsApp el
         * enlace de un álbum/playlist que se importó, no solo el ID de
         * vídeo individual (que ya se podía reconstruir siempre vía
         * youtubeUrl). Nullable, sin backfill: solo se rellena para
         * pistas importadas a partir de ahora vía "Importar enlace"
         * (ImportLinkViewModel.importSelected()); las pistas de Buscar
         * álbum y las ya existentes quedan en NULL, y la UI cae a
         * compartir youtubeUrl (el vídeo individual) en ese caso.
         * ---
         * Adds sourceUrl to search_result_tracks -- explicit request
         * from Miguel Ángel (2026-07-04): being able to share via
         * WhatsApp the link of an imported album/playlist, not just
         * the individual video ID (which could already always be
         * reconstructed via youtubeUrl). Nullable, no backfill: only
         * populated for tracks imported from now on via "Importar
         * enlace" (ImportLinkViewModel.importSelected()); Buscar álbum
         * tracks and already-existing ones stay NULL, and the UI falls
         * back to sharing youtubeUrl (the individual video) in that
         * case.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE search_result_tracks ADD COLUMN sourceUrl TEXT"
                )
            }
        }

        /**
         * Crea favorite_albums -- petición explícita de Miguel Ángel
         * (2026-07-05): favoritos a nivel de ÁLBUM, un concepto nuevo
         * y separado del favorito por pista (isFavorite en
         * search_result_tracks, que sigue existiendo tal cual para
         * sencillos). Clave compuesta (artist, album) -- ver comentario
         * de la entidad FavoriteAlbum. Tabla nueva, no toca
         * search_result_tracks.
         * ---
         * Creates favorite_albums -- explicit request from Miguel Ángel
         * (2026-07-05): ALBUM-level favorites, a new concept separate
         * from the per-track favorite (isFavorite on
         * search_result_tracks, which keeps existing as-is for
         * singles). Composite key (artist, album) -- see the
         * FavoriteAlbum entity's comment. New table, doesn't touch
         * search_result_tracks.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorite_albums` (" +
                        "`artist` TEXT NOT NULL, " +
                        "`album` TEXT NOT NULL, " +
                        "PRIMARY KEY(`artist`, `album`))"
                )
            }
        }

        /**
         * Crea favorite_radio_stations (H09, S010) -- petición
         * explícita de Miguel Ángel: favoritos de EMISORA de
         * Radio-Browser.info, concepto nuevo y separado del favorito
         * por pista (isFavorite en search_result_tracks) y del
         * favorito de álbum (favorite_albums). Clave primaria
         * stationUuid -- el identificador estable que la propia
         * documentación de Radio-Browser.info exige usar en vez de
         * "id". Tabla nueva, no toca ninguna tabla existente.
         * ---
         * Creates favorite_radio_stations (H09, S010) -- explicit
         * request from Miguel Ángel: STATION-level favorites for
         * Radio-Browser.info, a new concept separate from the
         * per-track favorite and the per-album favorite. Primary key
         * stationUuid. New table, doesn't touch any existing table.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorite_radio_stations` (" +
                        "`stationUuid` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`urlResolved` TEXT NOT NULL, " +
                        "`favicon` TEXT, " +
                        "`country` TEXT, " +
                        "`tags` TEXT, " +
                        "PRIMARY KEY(`stationUuid`))"
                )
            }
        }

        /**
         * Crea channel_subscriptions (H11, S011) -- petición explícita
         * de Miguel Ángel: suscripciones a canal, concepto nuevo y
         * separado de `channelTitle` (que es solo un campo de texto en
         * cada pista, no una entidad propia). Clave primaria
         * `channelId`, el identificador estable de YouTube -- viene de
         * `SearchTypeResult.id` (H08 PARTE 1), no del título, que puede
         * cambiar. Tabla nueva, no toca ninguna tabla existente.
         * ---
         * Creates channel_subscriptions (H11, S011) -- explicit
         * request from Miguel Ángel: channel subscriptions, a new
         * concept separate from `channelTitle`. Primary key
         * `channelId`, YouTube's stable identifier. New table, doesn't
         * touch any existing table.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `channel_subscriptions` (" +
                        "`channelId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`thumbnailUrl` TEXT, " +
                        "`subscribedAt` INTEGER NOT NULL, " +
                        "`lastCheckedAt` INTEGER, " +
                        "PRIMARY KEY(`channelId`))"
                )
            }
        }

        /**
         * Crea favorite_artists y artist_disambiguations (H12, S018).
         * favorite_artists: favoritos a nivel de ARTISTA, concepto
         * nuevo y separado de favorite_albums e isFavorite (pista) --
         * misma forma que MIGRATION_8_9, clave primaria simple
         * (artist) en vez de compuesta porque no hace falta un segundo
         * campo. artist_disambiguations: elección persistida de MBID
         * para homónimos reales -- ver comentario de la entidad
         * ArtistDisambiguation. Dos tablas nuevas, no toca ninguna
         * tabla existente.
         * ---
         * Creates favorite_artists and artist_disambiguations (H12,
         * S018). favorite_artists: ARTIST-level favorites, a new
         * concept separate from favorite_albums and the per-track
         * isFavorite -- same shape as MIGRATION_8_9, simple primary
         * key (artist) instead of composite since a second field
         * isn't needed. artist_disambiguations: persisted MBID choice
         * for real homonyms -- see the ArtistDisambiguation entity's
         * comment. Two new tables, doesn't touch any existing table.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorite_artists` (" +
                        "`artist` TEXT NOT NULL, " +
                        "PRIMARY KEY(`artist`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `artist_disambiguations` (" +
                        "`normalizedNameKey` TEXT NOT NULL, " +
                        "`chosenMbid` TEXT NOT NULL, " +
                        "PRIMARY KEY(`normalizedNameKey`))"
                )
            }
        }

        /**
         * Crea favorite_tracks y favorite_playlists -- sesión de
         * diseño de Favoritos (2026-08-02). favorite_tracks: favorito
         * de SENCILLO en streaming, concepto nuevo y separado de
         * isFavorite en search_result_tracks (que sigue existiendo tal
         * cual para sencillos ya descargados) -- ver comentario de la
         * entidad FavoriteTrack. favorite_playlists: marcador de
         * PLAYLIST propia favorita, con clave foránea a playlists(id)
         * y ON DELETE CASCADE -- ver comentario de la entidad
         * FavoritePlaylist. Dos tablas nuevas, no toca ninguna tabla
         * existente.
         * ---
         * Creates favorite_tracks and favorite_playlists -- Favorites
         * design session (2026-08-02). favorite_tracks: STREAMING
         * single-track favorite, a new concept separate from isFavorite
         * on search_result_tracks (which keeps existing as-is for
         * already-downloaded singles) -- see the FavoriteTrack entity's
         * comment. favorite_playlists: favorite marker for a user's own
         * playlist, foreign key to playlists(id) with ON DELETE CASCADE
         * -- see the FavoritePlaylist entity's comment. Two new tables,
         * doesn't touch any existing table.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorite_tracks` (" +
                        "`youtubeId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`artist` TEXT NOT NULL, " +
                        "`thumbnailUrl` TEXT, " +
                        "`durationSeconds` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`youtubeId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorite_playlists` (" +
                        "`playlistId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`playlistId`), " +
                        "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) " +
                        "ON DELETE CASCADE)"
                )
            }
        }

        /**
         * Crea disliked_artists y disliked_tracks (H16, S029) --
         * petición explícita de Miguel Ángel: exclusión global y dura
         * de artistas/temas para Radio (H08/H15) y popurrí de
         * Favoritos. disliked_artists: clave primaria simple (artist),
         * mismo patrón que MIGRATION_11_12/favorite_artists.
         * disliked_tracks: clave compuesta (artist, title) -- "no me
         * gusta" de un tema excluye cualquier versión de ese título de
         * ese artista, no un vídeo de YouTube concreto, así que no hay
         * columna youtubeId -- ver comentario de la entidad
         * DislikedTrack. Dos tablas nuevas, no toca ninguna tabla
         * existente.
         * ---
         * Creates disliked_artists and disliked_tracks (H16, S029) --
         * explicit request from Miguel Ángel: global, hard exclusion
         * of artists/tracks for Radio (H08/H15) and the Favorites
         * popurrí. disliked_artists: simple primary key (artist), same
         * pattern as MIGRATION_11_12/favorite_artists. disliked_tracks:
         * composite key (artist, title) -- disliking a track excludes
         * any version of that title by that artist, not one specific
         * YouTube video, so there's no youtubeId column -- see the
         * DislikedTrack entity's comment. Two new tables, doesn't
         * touch any existing table.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `disliked_artists` (" +
                        "`artist` TEXT NOT NULL, " +
                        "`dislikedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`artist`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `disliked_tracks` (" +
                        "`artist` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`dislikedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`artist`, `title`))"
                )
            }
        }
    }
}

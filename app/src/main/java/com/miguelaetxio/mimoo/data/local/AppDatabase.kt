package com.miguelaetxio.mimoo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.miguelaetxio.mimoo.data.local.dao.PlaylistDao
import com.miguelaetxio.mimoo.data.local.dao.SearchResultTrackDao
import com.miguelaetxio.mimoo.data.local.entity.Playlist
import com.miguelaetxio.mimoo.data.local.entity.PlaylistTrackCrossRef
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack

@Database(
    entities = [
        SearchResultTrack::class,
        Playlist::class,
        PlaylistTrackCrossRef::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchResultTrackDao(): SearchResultTrackDao
    abstract fun playlistDao(): PlaylistDao

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
    }
}

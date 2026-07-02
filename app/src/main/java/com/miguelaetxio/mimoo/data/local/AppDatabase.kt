package com.miguelaetxio.mimoo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.miguelaetxio.mimoo.data.local.dao.SearchResultTrackDao
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack

@Database(
    entities = [
        SearchResultTrack::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchResultTrackDao(): SearchResultTrackDao

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
    }
}

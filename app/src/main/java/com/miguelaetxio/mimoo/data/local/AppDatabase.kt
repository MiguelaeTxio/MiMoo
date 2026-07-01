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
    version = 2,
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
    }
}

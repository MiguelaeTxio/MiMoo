package com.miguelaetxio.mimoo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.miguelaetxio.mimoo.data.local.dao.SearchResultTrackDao
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack

@Database(
    entities = [
        SearchResultTrack::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchResultTrackDao(): SearchResultTrackDao
}

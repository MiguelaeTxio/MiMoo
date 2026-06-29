package com.miguelaetxio.mimoo.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import androidx.room.TypeConverters
import com.miguelaetxio.mimoo.data.local.dao.*
import com.miguelaetxio.mimoo.data.local.entity.*

@Database(
    entities = [
        Artist::class,
        Album::class,
        Track::class,
        Playlist::class,
        PlaylistTrack::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
}

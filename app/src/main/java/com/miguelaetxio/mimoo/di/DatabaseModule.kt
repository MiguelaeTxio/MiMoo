package com.miguelaetxio.mimoo.di

import android.content.Context
import androidx.room.Room
import com.miguelaetxio.mimoo.data.local.AppDatabase
import com.miguelaetxio.mimoo.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mimoo.db",
        ).build()

    @Provides fun provideArtistDao(db: AppDatabase): ArtistDao = db.artistDao()
    @Provides fun provideAlbumDao(db: AppDatabase): AlbumDao = db.albumDao()
    @Provides fun provideTrackDao(db: AppDatabase): TrackDao = db.trackDao()
    @Provides fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()
}

package com.miguelaetxio.mimoo.di

import android.content.Context
import androidx.room.Room
import com.miguelaetxio.mimoo.data.local.AppDatabase
import com.miguelaetxio.mimoo.data.local.dao.FavoriteAlbumDao
import com.miguelaetxio.mimoo.data.local.dao.FavoriteRadioStationDao
import com.miguelaetxio.mimoo.data.local.dao.PlaylistDao
import com.miguelaetxio.mimoo.data.local.dao.SearchResultTrackDao
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
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
            )
            .build()

    @Provides
    fun provideSearchResultTrackDao(db: AppDatabase): SearchResultTrackDao =
        db.searchResultTrackDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    /**
     * Faltaba -- causa real del fallo de compilación
     * [Dagger/MissingBinding] (2026-07-05): FavoriteAlbumRepository
     * pide un FavoriteAlbumDao inyectado, pero nunca se declaró cómo
     * proveerlo. Mismo patrón que los otros DAOs de este módulo.
     * ---
     * Was missing -- real cause of the [Dagger/MissingBinding] compile
     * failure (2026-07-05): FavoriteAlbumRepository asks for an
     * injected FavoriteAlbumDao, but how to provide it was never
     * declared. Same pattern as this module's other DAOs.
     */
    @Provides
    fun provideFavoriteAlbumDao(db: AppDatabase): FavoriteAlbumDao =
        db.favoriteAlbumDao()

    @Provides
    fun provideFavoriteRadioStationDao(db: AppDatabase): FavoriteRadioStationDao =
        db.favoriteRadioStationDao()
}

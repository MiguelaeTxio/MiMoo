package com.miguelaetxio.mimoo.di

import com.miguelaetxio.mimoo.data.remote.YouTubeApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/**
 * Hilt module providing network dependencies.
 * ---
 * Módulo Hilt que provee las dependencias de red.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val YOUTUBE_BASE_URL =
        "https://www.googleapis.com/youtube/v3/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(YOUTUBE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideYouTubeApiService(retrofit: Retrofit): YouTubeApiService =
        retrofit.create(YouTubeApiService::class.java)
}

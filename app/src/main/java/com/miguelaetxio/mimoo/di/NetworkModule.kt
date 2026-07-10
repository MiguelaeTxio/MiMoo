package com.miguelaetxio.mimoo.di

import com.miguelaetxio.mimoo.data.remote.DriveApiService
import com.miguelaetxio.mimoo.data.remote.DriveUploadApiService
import com.miguelaetxio.mimoo.data.remote.MusicBrainzApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module providing network dependencies.
 * ---
 * Módulo Hilt que provee las dependencias de red.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // MusicBrainz API root — see MusicBrainzApiService. Verified
    // 2026-07-02 against musicbrainz.org/doc/MusicBrainz_API.
    private const val MUSICBRAINZ_BASE_URL = "https://musicbrainz.org/ws/2/"

    // Drive REST v3 (H06 PASO 2) -- DOS base URL distintas a
    // propósito, igual que documenta Google: la de metadatos/listado/
    // descarga y la de subida de contenido no son el mismo host+path.
    // Verificado en línea en S006.
    private const val DRIVE_BASE_URL = "https://www.googleapis.com/drive/v3/"
    private const val DRIVE_UPLOAD_BASE_URL = "https://www.googleapis.com/upload/drive/v3/"

    // Required by MusicBrainz's rate-limiting rules: every request
    // needs a meaningful User-Agent identifying the app and a way to
    // contact its maintainer (verified 2026-07-02, see
    // musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting). Contact
    // point is the public repo, since MiMoo has no other public URL.
    private const val MUSICBRAINZ_USER_AGENT =
        "MiMoo/1.0 ( https://github.com/MiguelaeTxio/MiMoo )"

    // MusicBrainz allows on average 1 request/second per IP; anything
    // over gets HTTP 503'd and repeat offenders get IP-blocked
    // (verified 2026-07-02). This interceptor enforces a minimum gap
    // between requests from this app, synchronized because OkHttp may
    // dispatch interceptor chains from more than one thread.
    private class MusicBrainzRateLimitInterceptor : Interceptor {
        private val lock = Any()
        private var lastRequestAtMillis = 0L
        private val minIntervalMillis = 1100L // 1 req/s + safety margin

        override fun intercept(chain: Interceptor.Chain): Response {
            synchronized(lock) {
                val waitMillis =
                    minIntervalMillis - (System.currentTimeMillis() - lastRequestAtMillis)
                if (waitMillis > 0) Thread.sleep(waitMillis)
                lastRequestAtMillis = System.currentTimeMillis()
            }
            val request = chain.request().newBuilder()
                .header("User-Agent", MUSICBRAINZ_USER_AGENT)
                .build()
            return chain.proceed(request)
        }
    }

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

    // Separate OkHttpClient/Retrofit for MusicBrainz — different base
    // URL, and it needs its own interceptor stack (User-Agent +
    // rate limit) that must never apply to other API calls.
    @Provides
    @Singleton
    @Named("musicBrainzOkHttpClient")
    fun provideMusicBrainzOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(MusicBrainzRateLimitInterceptor())
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            // MusicBrainz responses can queue behind the rate limiter;
            // generous timeouts avoid spurious failures under load.
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @Named("musicBrainzRetrofit")
    fun provideMusicBrainzRetrofit(
        @Named("musicBrainzOkHttpClient") okHttpClient: OkHttpClient,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(MUSICBRAINZ_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideMusicBrainzApiService(
        @Named("musicBrainzRetrofit") retrofit: Retrofit,
    ): MusicBrainzApiService = retrofit.create(MusicBrainzApiService::class.java)

    // Drive REST v3 (H06 PASO 2). Sin interceptor propio -- a
    // diferencia de MusicBrainz, Drive no exige User-Agent ni rate
    // limiting; la autorización viaja como @Header por llamada
    // (DriveAuthorizationHelper), no como interceptor fijo, porque el
    // token cambia entre llamadas y puede no existir todavía cuando
    // se construye este Retrofit @Singleton.
    @Provides
    @Singleton
    @Named("driveRetrofit")
    fun provideDriveRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(DRIVE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideDriveApiService(
        @Named("driveRetrofit") retrofit: Retrofit,
    ): DriveApiService = retrofit.create(DriveApiService::class.java)

    @Provides
    @Singleton
    @Named("driveUploadRetrofit")
    fun provideDriveUploadRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(DRIVE_UPLOAD_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideDriveUploadApiService(
        @Named("driveUploadRetrofit") retrofit: Retrofit,
    ): DriveUploadApiService = retrofit.create(DriveUploadApiService::class.java)
}

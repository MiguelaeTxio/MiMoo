package com.miguelaetxio.mimoo.di

import com.miguelaetxio.mimoo.data.remote.AppUpdateApiService
import com.miguelaetxio.mimoo.data.remote.DiscogsApiService
import com.miguelaetxio.mimoo.data.remote.DriveApiService
import com.miguelaetxio.mimoo.data.remote.DriveUploadApiService
import com.miguelaetxio.mimoo.data.remote.ItunesApiService
import com.miguelaetxio.mimoo.data.remote.LrcLibApiService
import com.miguelaetxio.mimoo.data.remote.MusicBrainzApiService
import com.miguelaetxio.mimoo.data.remote.RadioBrowserApiService
import com.miguelaetxio.mimoo.data.remote.WikidataApiService
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

    // Manifiesto de actualizaciones (H07 PARTE 2, PASO 2.4) -- raíz de
    // github.com, sin API ni autenticación, ver AppUpdateApiService.
    private const val GITHUB_BASE_URL = "https://github.com/"

    // Radio-Browser.info (H09 PASO 1, S010) -- mirror fijo, decisión
    // deliberada de ANNEX_H09.md: un único servidor en vez de
    // descubrimiento dinámico vía DNS de all.api.radio-browser.info.
    // Sin API key, ver RadioBrowserApiService.
    private const val RADIO_BROWSER_BASE_URL = "https://de1.api.radio-browser.info/"

    // API de búsqueda de iTunes (H03, S011) -- fallback de carátula
    // cuando MusicBrainz/Cover Art Archive no tiene coincidencia. Sin
    // API key, ver ItunesApiService.
    private const val ITUNES_BASE_URL = "https://itunes.apple.com/"

    // lrclib.net (H17, S031) -- fuente de letras de Karaoke & Lyrics,
    // ver DOCS/ANNEX_H17.md punto 1. Confirmado en línea esta sesión:
    // abierta, gratuita, sin API key, sin límite de peticiones. Ver
    // LrcLibApiService.
    private const val LRCLIB_BASE_URL = "https://lrclib.net/api/"

    // Recomendado (no exigido) por la propia documentación de
    // lrclib.net: identificar la app con nombre+versión+enlace al
    // proyecto, mismo espíritu que MUSICBRAINZ_USER_AGENT/
    // RADIO_BROWSER_USER_AGENT arriba.
    private const val LRCLIB_USER_AGENT =
        "MiMoo/1.0 ( https://github.com/MiguelaeTxio/MiMoo )"

    private class LrcLibUserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header("User-Agent", LRCLIB_USER_AGENT)
                .build()
            return chain.proceed(request)
        }
    }

    // Buena práctica documentada por el propio servicio (no
    // obligatoria, pero recomendada) -- identificar la app con un
    // User-Agent significativo, mismo principio que MusicBrainz
    // arriba, sin el rate-limiting estricto que sí exige MusicBrainz.
    private const val RADIO_BROWSER_USER_AGENT =
        "MiMoo/1.0 ( https://github.com/MiguelaeTxio/MiMoo )"

    private class RadioBrowserUserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header("User-Agent", RADIO_BROWSER_USER_AGENT)
                .build()
            return chain.proceed(request)
        }
    }

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

    // S025 -- Wikidata. Sin credenciales, pero con User-Agent
    // identificable y un ritmo contenido: es un servicio publico y
    // gratuito y no hay que castigarlo. Ver WikidataApiService.
    private class WikidataInterceptor : Interceptor {
        private val lock = Any()
        private var lastRequestAtMillis = 0L
        private val minIntervalMillis = 1100L

        override fun intercept(chain: Interceptor.Chain): Response {
            synchronized(lock) {
                val waitMillis =
                    minIntervalMillis - (System.currentTimeMillis() - lastRequestAtMillis)
                if (waitMillis > 0) Thread.sleep(waitMillis)
                lastRequestAtMillis = System.currentTimeMillis()
            }
            val request = chain.request().newBuilder()
                .header("User-Agent", MUSICBRAINZ_USER_AGENT)
                .header("Accept", "application/sparql-results+json")
                .build()
            return chain.proceed(request)
        }
    }

    // S025 -- Discogs. Con token limita a 60 peticiones por minuto, asi
    // que un segundo entre peticiones va sobrado y de paso empareja el
    // ritmo con el de MusicBrainz. Exige User-Agent identificable.
    private class DiscogsInterceptor : Interceptor {
        private val lock = Any()
        private var lastRequestAtMillis = 0L
        private val minIntervalMillis = 1100L

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
    @Named("discogsRetrofit")
    fun provideDiscogsRetrofit(): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.discogs.com/")
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(DiscogsInterceptor())
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideDiscogsApiService(
        @Named("discogsRetrofit") retrofit: Retrofit,
    ): DiscogsApiService = retrofit.create(DiscogsApiService::class.java)

    @Provides
    @Singleton
    @Named("wikidataRetrofit")
    fun provideWikidataRetrofit(): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://query.wikidata.org/")
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(WikidataInterceptor())
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideWikidataApiService(
        @Named("wikidataRetrofit") retrofit: Retrofit,
    ): WikidataApiService = retrofit.create(WikidataApiService::class.java)

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

    // Manifiesto de actualizaciones (H07 PARTE 2, PASO 2.4). Sin
    // interceptor propio -- github.com no exige nada especial para
    // un GET a un asset público de Release.
    @Provides
    @Singleton
    @Named("githubRetrofit")
    fun provideGithubRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideAppUpdateApiService(
        @Named("githubRetrofit") retrofit: Retrofit,
    ): AppUpdateApiService = retrofit.create(AppUpdateApiService::class.java)

    // Radio-Browser.info (H09 PASO 1, S010). Cliente propio solo por
    // el interceptor de User-Agent -- sin rate limiting (no lo exige
    // el servicio, a diferencia de MusicBrainz).
    @Provides
    @Singleton
    @Named("radioBrowserOkHttpClient")
    fun provideRadioBrowserOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(RadioBrowserUserAgentInterceptor())
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()

    @Provides
    @Singleton
    @Named("radioBrowserRetrofit")
    fun provideRadioBrowserRetrofit(
        @Named("radioBrowserOkHttpClient") okHttpClient: OkHttpClient,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(RADIO_BROWSER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideRadioBrowserApiService(
        @Named("radioBrowserRetrofit") retrofit: Retrofit,
    ): RadioBrowserApiService = retrofit.create(RadioBrowserApiService::class.java)

    // iTunes Search API (H03, S011) -- sin interceptor propio, el
    // OkHttpClient base ya vale (sin User-Agent ni rate-limiting
    // exigido, a diferencia de MusicBrainz/Radio-Browser).
    @Provides
    @Singleton
    @Named("itunesRetrofit")
    fun provideItunesRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(ITUNES_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideItunesApiService(
        @Named("itunesRetrofit") retrofit: Retrofit,
    ): ItunesApiService = retrofit.create(ItunesApiService::class.java)

    // lrclib.net (H17, S031). Cliente propio solo por el interceptor
    // de User-Agent recomendado -- sin rate limiting, no lo exige el
    // servicio (confirmado en línea esta sesión).
    @Provides
    @Singleton
    @Named("lrcLibOkHttpClient")
    fun provideLrcLibOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(LrcLibUserAgentInterceptor())
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()

    @Provides
    @Singleton
    @Named("lrcLibRetrofit")
    fun provideLrcLibRetrofit(
        @Named("lrcLibOkHttpClient") okHttpClient: OkHttpClient,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(LRCLIB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideLrcLibApiService(
        @Named("lrcLibRetrofit") retrofit: Retrofit,
    ): LrcLibApiService = retrofit.create(LrcLibApiService::class.java)
}

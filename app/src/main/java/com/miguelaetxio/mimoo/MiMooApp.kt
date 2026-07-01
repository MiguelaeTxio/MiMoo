package com.miguelaetxio.mimoo

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for MiMoo. Bootstraps Hilt, Chaquopy Python
 * runtime, and WorkManager with HiltWorkerFactory so that
 * @HiltWorker classes receive their injected dependencies.
 * ---
 * Clase Application de MiMoo. Arranca Hilt, el runtime Python de
 * Chaquopy y WorkManager con HiltWorkerFactory para que las clases
 * @HiltWorker reciban sus dependencias inyectadas.
 *
 * WorkManager initialisation pattern:
 * 1. The default WorkManagerInitializer ContentProvider is removed
 *    from the Manifest (tools:node="remove") to prevent auto-init
 *    before Hilt is ready.
 * 2. MiMooApp implements Configuration.Provider and returns a
 *    Configuration that uses HiltWorkerFactory.
 * WorkManager then picks up this configuration lazily on first use.
 */
@HiltAndroidApp
class MiMooApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

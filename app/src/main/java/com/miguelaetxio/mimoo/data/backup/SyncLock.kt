package com.miguelaetxio.mimoo.data.backup

import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Un único cerrojo para todo lo que lee y reescribe el archivo de
 * sincronización de Drive desde segundo plano (subidas de cambios y
 * subidas de estadísticas), para que no se pisen entre sí en este
 * dispositivo.
 * ---
 * A single lock for everything that reads and rewrites the Drive sync
 * file in the background (change pushes and statistics pushes), so they
 * don't step on each other on this device.
 */
@Singleton
class SyncLock @Inject constructor() {
    val mutex = Mutex()
}

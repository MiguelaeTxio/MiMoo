package com.miguelaetxio.mimoo.data.stats

import android.content.Context
import android.util.Log
import com.miguelaetxio.mimoo.data.backup.BackupDebugLogger
import com.miguelaetxio.mimoo.data.backup.BackupDriveRepository
import com.miguelaetxio.mimoo.data.backup.BackupRepository
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationHelper
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationOutcome
import com.miguelaetxio.mimoo.data.backup.SyncEnvelope
import com.miguelaetxio.mimoo.data.backup.SyncLock
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.local.dao.PlayEventDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sincroniza las estadísticas con Drive sin tocar nada más del
 * archivo de sincronización: descarga el sobre, le añade las escuchas
 * nuevas de este dispositivo al apartado `stats` y lo vuelve a subir
 * con todo lo demás exactamente igual (dispositivo, hora, pistas,
 * listas, cookies). Las escuchas ya sumadas se borran del dispositivo.
 * ---
 * Syncs statistics with Drive without touching anything else in the
 * sync file: downloads the envelope, adds this device's new listens to
 * its `stats` section and uploads it again with everything else
 * exactly the same (device, time, tracks, lists, cookies). Listens
 * already added are deleted from the device.
 */
@Singleton
class StatsSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authorizationHelper: DriveAuthorizationHelper,
    private val driveRepository: BackupDriveRepository,
    private val backupRepository: BackupRepository,
    private val statsRepository: StatsRepository,
    private val playEventDao: PlayEventDao,
    private val storageManager: StorageManager,
    private val syncLock: SyncLock,
) {
    companion object {
        private const val TAG = "StatsSync"
        private const val DEBOUNCE_MS = 60_000L
        private const val MIN_INTERVAL_MS = 10 * 60_000L
        private const val DELETE_CHUNK = 500
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    @Volatile
    private var lastSyncAt = 0L

    /**
     * Pide una sincronización de estadísticas. Sin `immediate`, espera
     * al menos 1 minuto y no sincroniza más de una vez cada 10 minutos
     * (la subida lleva el archivo de sincronización entero). Ya hay una
     * pendiente: no se programa otra.
     * ---
     * Requests a statistics sync. Without `immediate`, waits at least 1
     * minute and syncs at most once every 10 minutes (the upload
     * carries the whole sync file). One already pending: no other is
     * scheduled.
     */
    fun requestSync(immediate: Boolean = false) {
        if (pending?.isActive == true && !immediate) return
        pending?.cancel()
        pending = scope.launch {
            if (!immediate) {
                val wait = maxOf(
                    DEBOUNCE_MS,
                    MIN_INTERVAL_MS - (System.currentTimeMillis() - lastSyncAt),
                )
                delay(wait)
            }
            try {
                val outcome = authorizationHelper.requestAuthorization(context)
                if (outcome is DriveAuthorizationOutcome.Authorized) {
                    syncNow(outcome.accessToken)
                }
            } catch (e: Exception) {
                Log.w(TAG, "requestSync() -- fallo, se reintentará en la próxima ocasión", e)
            }
        }
    }

    suspend fun syncNow(accessToken: String) = syncLock.mutex.withLock {
        try {
            val closed = playEventDao.getClosedEvents()
            val json = driveRepository.pullSyncState(accessToken) ?: return@withLock
            val envelope = backupRepository.fromSyncJson(json)
            val base = statsRepository.carry(envelope.stats)

            if (closed.isEmpty()) {
                if (envelope.stats != null) {
                    statsRepository.setCache(envelope.stats)
                } else if (base != null) {
                    driveRepository.pushSyncState(
                        accessToken,
                        backupRepository.toSyncJson(envelope.copy(stats = base)),
                    )
                    log("syncNow() -- Drive no tenía totales, se han restaurado desde la copia local")
                }
                lastSyncAt = System.currentTimeMillis()
                return@withLock
            }

            val merged = StatsAggregator.apply(base, closed)
            driveRepository.pushSyncState(
                accessToken,
                backupRepository.toSyncJson(envelope.copy(stats = merged)),
            )
            closed.map { it.id }.chunked(DELETE_CHUNK).forEach { playEventDao.deleteByIds(it) }
            statsRepository.setCache(merged)
            lastSyncAt = System.currentTimeMillis()
            log("syncNow() -- ${closed.size} escucha(s) sumadas a Drive")
        } catch (e: Exception) {
            Log.w(TAG, "syncNow() -- fallo, las escuchas quedan pendientes", e)
            log("syncNow() -- fallo: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Sube un sobre de cambios de pistas/listas conservando los totales
     * que Drive tiene en ese momento (se vuelven a leer justo antes).
     * El que llama es responsable de tener el cerrojo si lo necesita.
     * ---
     * Uploads a track/list change envelope keeping the totals Drive has
     * at that moment (re-read right before). The caller is responsible
     * for holding the lock if it needs to.
     */
    suspend fun pushPreservingStats(accessToken: String, envelope: SyncEnvelope) {
        val remoteStats = try {
            driveRepository.pullSyncState(accessToken)
                ?.let { backupRepository.fromSyncJson(it).stats }
        } catch (e: Exception) {
            null
        }
        val stats = statsRepository.carry(remoteStats)
        driveRepository.pushSyncState(
            accessToken,
            backupRepository.toSyncJson(envelope.copy(stats = stats)),
        )
        if (remoteStats != null) statsRepository.setCache(remoteStats)
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        BackupDebugLogger.log(context, storageManager, msg)
    }
}

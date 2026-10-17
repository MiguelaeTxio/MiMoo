package com.miguelaetxio.mimoo.data.stats

import android.os.SystemClock
import com.miguelaetxio.mimoo.data.local.dao.PlayEventDao
import com.miguelaetxio.mimoo.data.local.entity.PlayEvent
import com.miguelaetxio.mimoo.data.local.entity.PlayKind
import com.miguelaetxio.mimoo.data.playback.QueueItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registro de escuchas para la pantalla de Estadísticas. PlayerManager
 * le avisa de dos cosas: cambio de pista (onItemChanged) y
 * reproduciendo/pausado (onPlayingChanged). Solo cuenta el tiempo en
 * que el reproductor está realmente sonando. Una escucha se guarda
 * cuando la pista lleva MIN_LISTEN_MS sonando; desde entonces la misma
 * fila se actualiza al pausar y al cambiar de pista. Las escrituras a
 * Room van por un único hilo, en orden.
 * ---
 * Listen log for the Statistics screen. PlayerManager reports two
 * things: track change (onItemChanged) and playing/paused
 * (onPlayingChanged). Only time the player is really playing counts.
 * A listen is saved once the track has played MIN_LISTEN_MS; from then
 * on the same row is updated on pause and on track change. Room writes
 * run on a single thread, in order.
 */
@Singleton
class PlaybackStatsRecorder @Inject constructor(
    private val dao: PlayEventDao,
    private val statsSyncManager: StatsSyncManager,
) {
    companion object {
        const val MIN_LISTEN_MS = 30_000L
    }

    private class Tracked(
        val token: Long,
        val kind: PlayKind,
        val title: String,
        val artist: String?,
        val youtubeId: String?,
        val originLabel: String?,
        val startedAt: Long,
    ) {
        var accumMs: Long = 0L
        var playingSince: Long = -1L
        var persisted: Boolean = false
    }

    private val scope = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    )

    // Solo se toca desde el hilo del reproductor (callbacks del Player).
    // Only touched from the player thread (Player callbacks).
    private var tracked: Tracked? = null
    private var nextToken = 1L

    // Solo se toca desde el hilo de escritura único.
    // Only touched from the single writer thread.
    private val rowIds = HashMap<Long, Long>()

    init {
        scope.launch {
            dao.closeAllOpen()
            statsSyncManager.requestSync()
        }
    }

    fun onItemChanged(item: QueueItem?, isPlayingNow: Boolean, mimooutcastActive: Boolean) {
        finish(tracked)
        tracked = null
        if (item == null) return
        val kind = when {
            item.isRadioStation -> PlayKind.STATION
            mimooutcastActive -> PlayKind.MIMOOUTCAST
            item.isFromRadio -> PlayKind.RADIO
            item.isLocal -> PlayKind.LOCAL
            else -> PlayKind.STREAMING
        }
        val t = Tracked(
            token = nextToken++,
            kind = kind,
            title = item.title,
            artist = item.artist?.takeIf { it.isNotBlank() },
            youtubeId = item.youtubeId,
            originLabel = item.originLabel,
            startedAt = System.currentTimeMillis(),
        )
        if (isPlayingNow) t.playingSince = SystemClock.elapsedRealtime()
        tracked = t
    }

    fun onPlayingChanged(isPlaying: Boolean) {
        val t = tracked ?: return
        if (isPlaying) {
            if (t.playingSince < 0) t.playingSince = SystemClock.elapsedRealtime()
        } else {
            closeStretch(t)
            persist(t, final = false)
        }
    }

    private fun closeStretch(t: Tracked) {
        if (t.playingSince >= 0) {
            t.accumMs += SystemClock.elapsedRealtime() - t.playingSince
            t.playingSince = -1L
        }
    }

    private fun finish(t: Tracked?) {
        if (t == null) return
        closeStretch(t)
        persist(t, final = true)
    }

    private fun persist(t: Tracked, final: Boolean) {
        if (t.accumMs < MIN_LISTEN_MS && !t.persisted) return
        t.persisted = true
        val token = t.token
        val ms = t.accumMs
        val event = PlayEvent(
            playedAt = t.startedAt,
            kind = t.kind.name,
            title = t.title,
            artist = t.artist,
            youtubeId = t.youtubeId,
            originLabel = t.originLabel,
            listenedMs = ms,
            closed = if (final) 1 else 0,
        )
        scope.launch {
            val id = rowIds[token]
            if (id == null) {
                rowIds[token] = dao.insert(event)
            } else {
                dao.updateListened(id, ms, if (final) 1 else 0)
            }
            if (final) {
                rowIds.remove(token)
                statsSyncManager.requestSync()
            }
        }
    }
}

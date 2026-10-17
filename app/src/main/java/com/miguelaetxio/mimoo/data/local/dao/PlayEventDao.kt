package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.miguelaetxio.mimoo.data.local.entity.PlayEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayEventDao {

    @Insert
    suspend fun insert(event: PlayEvent): Long

    @Query("UPDATE play_events SET listenedMs = :listenedMs, closed = :closed WHERE id = :id")
    suspend fun updateListened(id: Long, listenedMs: Long, closed: Int)

    // Al arrancar no hay ninguna pista sonando: lo que quedó abierto es
    // de un proceso anterior y ya no va a crecer.
    // At startup nothing is playing: whatever was left open belongs to
    // an earlier process and won't grow any more.
    @Query("UPDATE play_events SET closed = 1 WHERE closed = 0")
    suspend fun closeAllOpen()

    @Query("SELECT * FROM play_events WHERE closed = 1")
    suspend fun getClosedEvents(): List<PlayEvent>

    @Query("DELETE FROM play_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM play_events")
    fun observeAll(): Flow<List<PlayEvent>>
}

package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.miguelaetxio.mimoo.data.local.entity.PlayEvent
import kotlinx.coroutines.flow.Flow

data class PlayKindTotal(
    val kind: String,
    val plays: Int,
    val totalMs: Long,
)

data class TopTrackRow(
    val title: String,
    val artist: String?,
    val plays: Int,
)

data class TopLabelRow(
    val label: String,
    val plays: Int,
    val totalMs: Long,
)

@Dao
interface PlayEventDao {

    @Insert
    suspend fun insert(event: PlayEvent): Long

    @Query("UPDATE play_events SET listenedMs = :listenedMs WHERE id = :id")
    suspend fun updateListened(id: Long, listenedMs: Long)

    @Query(
        "SELECT kind, COUNT(*) AS plays, COALESCE(SUM(listenedMs), 0) AS totalMs " +
            "FROM play_events GROUP BY kind"
    )
    fun observeTotalsByKind(): Flow<List<PlayKindTotal>>

    @Query(
        "SELECT title, artist, COUNT(*) AS plays FROM play_events " +
            "WHERE kind != 'STATION' GROUP BY title, artist " +
            "ORDER BY plays DESC, MAX(playedAt) DESC LIMIT :limit"
    )
    fun observeTopTracks(limit: Int): Flow<List<TopTrackRow>>

    @Query(
        "SELECT artist AS label, COUNT(*) AS plays, COALESCE(SUM(listenedMs), 0) AS totalMs " +
            "FROM play_events WHERE kind != 'STATION' AND artist IS NOT NULL AND artist != '' " +
            "GROUP BY artist ORDER BY plays DESC, totalMs DESC LIMIT :limit"
    )
    fun observeTopArtists(limit: Int): Flow<List<TopLabelRow>>

    @Query(
        "SELECT originLabel AS label, COUNT(*) AS plays, COALESCE(SUM(listenedMs), 0) AS totalMs " +
            "FROM play_events WHERE originLabel IS NOT NULL AND originLabel != '' " +
            "GROUP BY originLabel ORDER BY plays DESC, totalMs DESC LIMIT :limit"
    )
    fun observeTopLists(limit: Int): Flow<List<TopLabelRow>>

    @Query(
        "SELECT title AS label, COUNT(*) AS plays, COALESCE(SUM(listenedMs), 0) AS totalMs " +
            "FROM play_events WHERE kind = 'STATION' " +
            "GROUP BY title ORDER BY totalMs DESC LIMIT :limit"
    )
    fun observeTopStations(limit: Int): Flow<List<TopLabelRow>>

    @Query(
        "SELECT artist AS label, COUNT(*) AS plays, COALESCE(SUM(listenedMs), 0) AS totalMs " +
            "FROM play_events WHERE kind = 'MIMOOUTCAST' AND artist IS NOT NULL AND artist != '' " +
            "GROUP BY artist ORDER BY plays DESC, totalMs DESC LIMIT :limit"
    )
    fun observeTopMimooutcastArtists(limit: Int): Flow<List<TopLabelRow>>

    @Query("SELECT MIN(playedAt) FROM play_events")
    fun observeFirstPlayedAt(): Flow<Long?>
}

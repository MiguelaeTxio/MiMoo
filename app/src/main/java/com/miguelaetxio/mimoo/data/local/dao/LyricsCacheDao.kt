package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miguelaetxio.mimoo.data.local.entity.LyricsCache

/** H17 (S031) -- clave compuesta (artistKey, titleKey), mismo patrón que DislikedTrackDao. */
@Dao
interface LyricsCacheDao {
    @Query("SELECT * FROM lyrics_cache WHERE artistKey = :artistKey AND titleKey = :titleKey")
    suspend fun get(artistKey: String, titleKey: String): LyricsCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LyricsCache)

    /** Búsqueda local para el chip "ya en tu biblioteca" de la pantalla del drawer (punto 5 del anexo). */
    @Query(
        "SELECT * FROM lyrics_cache " +
            "WHERE artist LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' " +
            "ORDER BY cachedAt DESC LIMIT :limit"
    )
    suspend fun searchCached(query: String, limit: Int = 20): List<LyricsCache>
}

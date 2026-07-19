package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miguelaetxio.mimoo.data.local.entity.ArtistDisambiguation

@Dao
interface ArtistDisambiguationDao {
    @Query("SELECT * FROM artist_disambiguations WHERE normalizedNameKey = :normalizedNameKey")
    suspend fun getChoice(normalizedNameKey: String): ArtistDisambiguation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(choice: ArtistDisambiguation)
}

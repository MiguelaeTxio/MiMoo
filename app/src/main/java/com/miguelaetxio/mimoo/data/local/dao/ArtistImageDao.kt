package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miguelaetxio.mimoo.data.local.entity.ArtistImage

/** DAO para la caché de fotos de artista (S059). Ver el kdoc de ArtistImage. */
@Dao
interface ArtistImageDao {
    @Query("SELECT * FROM artist_images WHERE artist = :artist")
    suspend fun getByArtist(artist: String): ArtistImage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: ArtistImage)
}

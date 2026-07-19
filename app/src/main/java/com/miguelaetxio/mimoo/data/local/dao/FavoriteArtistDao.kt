package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miguelaetxio.mimoo.data.local.entity.FavoriteArtist
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteArtistDao {
    @Query("SELECT * FROM favorite_artists")
    fun getAll(): Flow<List<FavoriteArtist>>

    /** Variante de una sola lectura de getAll() -- ver FavoriteAlbumDao.getAllOnce. */
    @Query("SELECT * FROM favorite_artists")
    suspend fun getAllOnce(): List<FavoriteArtist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteArtist)

    @Delete
    suspend fun delete(favorite: FavoriteArtist)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_artists WHERE artist = :artist)")
    suspend fun isFavorite(artist: String): Boolean

    /** Borra TODAS las filas -- ver FavoriteAlbumDao.deleteAll(). */
    @Query("DELETE FROM favorite_artists")
    suspend fun deleteAll()
}

package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miguelaetxio.mimoo.data.local.entity.FavoriteAlbum
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteAlbumDao {
    @Query("SELECT * FROM favorite_albums")
    fun getAll(): Flow<List<FavoriteAlbum>>

    /** Variante de una sola lectura de getAll() -- ver SearchResultTrackDao.getAllOnce, H06 PASO 1. */
    @Query("SELECT * FROM favorite_albums")
    suspend fun getAllOnce(): List<FavoriteAlbum>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteAlbum)

    @Delete
    suspend fun delete(favorite: FavoriteAlbum)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_albums WHERE artist = :artist AND album = :album)")
    suspend fun isFavorite(artist: String, album: String): Boolean

    /** Borra TODAS las filas -- ver SearchResultTrackDao.deleteAll(), H06 PASO 4. */
    @Query("DELETE FROM favorite_albums")
    suspend fun deleteAll()

}

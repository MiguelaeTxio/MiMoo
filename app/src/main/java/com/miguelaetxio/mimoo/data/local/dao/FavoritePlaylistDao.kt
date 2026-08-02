package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miguelaetxio.mimoo.data.local.entity.FavoritePlaylist
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritePlaylistDao {
    @Query("SELECT * FROM favorite_playlists")
    fun getAll(): Flow<List<FavoritePlaylist>>

    /** Variante de una sola lectura de getAll() -- ver FavoriteAlbumDao.getAllOnce(). */
    @Query("SELECT * FROM favorite_playlists")
    suspend fun getAllOnce(): List<FavoritePlaylist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoritePlaylist)

    @Delete
    suspend fun delete(favorite: FavoritePlaylist)

    @Query("DELETE FROM favorite_playlists WHERE playlistId = :playlistId")
    suspend fun deleteById(playlistId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_playlists WHERE playlistId = :playlistId)")
    suspend fun isFavorite(playlistId: Long): Boolean

    /** Borrado total -- mismo patrón que FavoriteRadioStationDao.deleteAll() (réplica completa H07). */
    @Query("DELETE FROM favorite_playlists")
    suspend fun deleteAll()
}

package com.miguelaetxio.mimoo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miguelaetxio.mimoo.data.local.entity.ChannelSubscription
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelSubscriptionDao {
    @Query("SELECT * FROM channel_subscriptions ORDER BY subscribedAt DESC")
    fun getAll(): Flow<List<ChannelSubscription>>

    /** Variante de una sola lectura de getAll() -- usada por ChannelCheckWorker (PASO 4), que no vive dentro de composición. */
    @Query("SELECT * FROM channel_subscriptions ORDER BY subscribedAt DESC")
    suspend fun getAllOnce(): List<ChannelSubscription>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: ChannelSubscription)

    @Delete
    suspend fun delete(subscription: ChannelSubscription)

    @Query("SELECT EXISTS(SELECT 1 FROM channel_subscriptions WHERE channelId = :channelId)")
    suspend fun isSubscribed(channelId: String): Boolean

    /** Set en vez de List -- consulta rápida "¿este canal de un resultado de búsqueda ya está suscrito?" sin más vueltas. */
    @Query("SELECT channelId FROM channel_subscriptions")
    fun getAllChannelIds(): Flow<List<String>>

    /** Usado por ChannelCheckWorker (PASO 4) tras comprobar un canal, para no repetir trabajo si se relanza antes de tiempo. */
    @Query("UPDATE channel_subscriptions SET lastCheckedAt = :checkedAt WHERE channelId = :channelId")
    suspend fun updateLastCheckedAt(channelId: String, checkedAt: Long)
}

package com.miguelaetxio.mimoo.data.local.repository

import com.miguelaetxio.mimoo.data.local.dao.ChannelSubscriptionDao
import com.miguelaetxio.mimoo.data.local.entity.ChannelSubscription
import com.miguelaetxio.mimoo.data.remote.dto.SearchTypeResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Suscripciones a canal (H11, S011) -- petición explícita de Miguel
 * Ángel: "canal en MiMoo significa lo mismo que canal en YouTube...
 * suscripciones y búsqueda de canales para suscribirse y descargar
 * contenido de esos canales para verlo cuando se quiera. Es como un
 * guardado de podcast." `toggle()` recibe un `SearchTypeResult` --
 * el mismo DTO que ya usa la búsqueda de canales de H08 PARTE 1, sin
 * duplicar la búsqueda.
 * ---
 * Channel subscriptions (H11, S011) -- explicit request from Miguel
 * Ángel: search, subscribe, and auto-download new content, like a
 * podcast app. `toggle()` takes a `SearchTypeResult` -- the same DTO
 * H08 PART 1's channel search already returns, no duplicate search.
 */
@Singleton
class ChannelSubscriptionRepository @Inject constructor(
    private val dao: ChannelSubscriptionDao,
) {
    fun getAll(): Flow<List<ChannelSubscription>> = dao.getAll()

    suspend fun getAllOnce(): List<ChannelSubscription> = dao.getAllOnce()

    fun getAllChannelIds(): Flow<List<String>> = dao.getAllChannelIds()

    suspend fun isSubscribed(channelId: String): Boolean = dao.isSubscribed(channelId)

    suspend fun updateLastCheckedAt(channelId: String, checkedAt: Long) =
        dao.updateLastCheckedAt(channelId, checkedAt)

    /** Baja directa por channelId -- usado por la pantalla "Canales" (PASO 3), donde ya se sabe con certeza que está suscrito. */
    suspend fun unsubscribe(channelId: String) = dao.deleteByChannelId(channelId)

    suspend fun toggle(channel: SearchTypeResult) {
        if (dao.isSubscribed(channel.id)) {
            dao.delete(
                ChannelSubscription(
                    channelId = channel.id,
                    title = channel.title,
                    thumbnailUrl = channel.thumbnailUrl,
                ),
            )
        } else {
            dao.insert(
                ChannelSubscription(
                    channelId = channel.id,
                    title = channel.title,
                    thumbnailUrl = channel.thumbnailUrl,
                ),
            )
        }
    }
}

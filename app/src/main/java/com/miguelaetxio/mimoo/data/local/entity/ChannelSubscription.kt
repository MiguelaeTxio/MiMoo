package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Canal de YouTube al que Miguel Ángel está suscrito (H11, S011) --
 * concepto NUEVO, separado de `SearchResultTrack.channelTitle` (que
 * es solo el nombre de canal de un vídeo concreto, no una entidad
 * propia). Petición explícita de Miguel Ángel: "canal en MiMoo
 * significa lo mismo que canal en YouTube... suscripciones y
 * búsqueda de canales para suscribirse y descargar contenido de esos
 * canales para verlo cuando se quiera. Es como un guardado de
 * podcast."
 *
 * Clave primaria `channelId` -- el identificador estable de YouTube
 * (viene de `SearchTypeResult.id`, H08 PARTE 1, no del título, que
 * puede cambiar). `lastCheckedAt` la usa `ChannelCheckWorker` (PASO
 * 4) para saber desde cuándo no se comprueba contenido nuevo de este
 * canal -- null hasta la primera comprobación real.
 * ---
 * A YouTube channel Miguel Ángel is subscribed to (H11, S011) -- a
 * NEW concept, separate from `SearchResultTrack.channelTitle` (just
 * a video's channel name, not its own entity). Explicit request from
 * Miguel Ángel: channels work exactly like YouTube channels --
 * search, subscribe, and auto-download new content, like a podcast
 * app.
 *
 * Primary key `channelId` -- YouTube's stable identifier (comes from
 * `SearchTypeResult.id`, H08 PARTE 1, never the title, which can
 * change). `lastCheckedAt` is used by `ChannelCheckWorker` (STEP 4)
 * to know how long it's been since new content was last checked for
 * this channel -- null until the first real check.
 */
@Entity(tableName = "channel_subscriptions")
data class ChannelSubscription(
    @PrimaryKey val channelId: String,
    val title: String,
    val thumbnailUrl: String?,
    val subscribedAt: Long = System.currentTimeMillis(),
    val lastCheckedAt: Long? = null,
)

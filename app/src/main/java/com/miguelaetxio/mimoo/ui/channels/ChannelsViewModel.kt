package com.miguelaetxio.mimoo.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.local.entity.ChannelSubscription
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.ChannelSubscriptionRepository
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Un canal suscrito junto a sus pistas ya descargadas -- emparejadas
 * por `channelTitle == subscription.title` (mejor esfuerzo: si
 * Miguel Ángel edita manualmente el `artist` de una pista tras
 * descargarla, `channelTitle` sigue intacto, así que este
 * emparejamiento no se ve afectado por esas ediciones, a diferencia
 * de agrupar por `artist`).
 */
data class ChannelWithTracks(
    val subscription: ChannelSubscription,
    val tracks: List<SearchResultTrack>,
)

data class ChannelsUiState(
    val channels: List<ChannelWithTracks> = emptyList(),
)

/**
 * ViewModel de la pantalla "Canales" (H11 PASO 3, S011). Combina las
 * suscripciones (`ChannelSubscriptionRepository`) con las pistas ya
 * descargadas (`SearchResultTrackRepository.getByStatus(DONE)`),
 * agrupando en memoria -- mismo criterio que `LibraryViewModel`
 * agrupa por `artist`/`album`, aquí por `channelTitle`.
 */
@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val channelSubscriptionRepository: ChannelSubscriptionRepository,
    private val searchResultTrackRepository: SearchResultTrackRepository,
    private val playerManager: PlayerManager,
) : ViewModel() {

    val uiState: StateFlow<ChannelsUiState> = combine(
        channelSubscriptionRepository.getAll(),
        searchResultTrackRepository.getByStatus(DownloadStatus.DONE),
    ) { subscriptions, downloadedTracks ->
        ChannelsUiState(
            channels = subscriptions.map { subscription ->
                ChannelWithTracks(
                    subscription = subscription,
                    tracks = downloadedTracks
                        .filter { it.channelTitle == subscription.title }
                        .sortedByDescending { it.lastSearchedAt },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChannelsUiState())

    fun unsubscribe(subscription: ChannelSubscription) {
        viewModelScope.launch {
            channelSubscriptionRepository.unsubscribe(subscription.channelId)
        }
    }

    fun playChannelTracks(channel: ChannelWithTracks) {
        val items = channel.tracks.mapNotNull { track ->
            val filePath = track.filePath ?: return@mapNotNull null
            QueueItem(
                uri = filePath,
                title = track.title,
                isLocal = true,
                artist = track.artist ?: track.channelTitle,
                youtubeId = track.youtubeId,
                channelTitle = track.channelTitle,
                artworkUri = track.coverArtUrl ?: track.thumbnailUrl,
            )
        }
        if (items.isNotEmpty()) {
            playerManager.playQueue(items)
        }
    }

    fun playTrack(track: SearchResultTrack) {
        val filePath = track.filePath ?: return
        playerManager.play(
            filePath,
            track.title,
            isLocal = true,
            artist = track.artist ?: track.channelTitle,
            youtubeId = track.youtubeId,
            channelTitle = track.channelTitle,
            artworkUri = track.coverArtUrl ?: track.thumbnailUrl,
        )
    }
}

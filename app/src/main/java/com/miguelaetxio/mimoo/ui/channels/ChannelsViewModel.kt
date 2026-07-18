package com.miguelaetxio.mimoo.ui.channels

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.backup.MutationOutcome
import com.miguelaetxio.mimoo.data.local.entity.ChannelSubscription
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.local.repository.ChannelSubscriptionRepository
import com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.data.playback.QueueItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    /** H07 PARTE 1 (S015) -- aviso cuando dar de baja se rechaza por falta de conexión. */
    val syncBlockedMessage: String? = null,
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
    private val autoSyncPusher: AutoSyncPusher,
) : ViewModel() {

    /** H07 PARTE 1 (S015) -- separado del combine principal porque no depende de Room/Flow. */
    private val _syncBlockedMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ChannelsUiState> = combine(
        channelSubscriptionRepository.getAll(),
        searchResultTrackRepository.getByStatus(DownloadStatus.DONE),
        _syncBlockedMessage,
    ) { subscriptions, downloadedTracks, syncBlockedMessage ->
        ChannelsUiState(
            channels = subscriptions.map { subscription ->
                ChannelWithTracks(
                    subscription = subscription,
                    tracks = downloadedTracks
                        .filter { it.channelTitle == subscription.title }
                        .sortedByDescending { it.lastSearchedAt },
                )
            },
            syncBlockedMessage = syncBlockedMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChannelsUiState())

    /**
     * H07 PARTE 1 (S015) -- réplica total: hasta ahora dar de baja se
     * ejecutaba siempre en local, sin la garantía de conexión ni la
     * subida inmediata a Drive que ya tienen pistas/álbumes/playlists.
     * Mismo patrón exacto que `LibraryViewModel.toggleFavoriteAlbum()`.
     */
    fun unsubscribe(activity: Activity, subscription: ChannelSubscription) {
        viewModelScope.launch {
            val outcome = autoSyncPusher.executeIfConnected(activity) {
                channelSubscriptionRepository.unsubscribe(subscription.channelId)
            }
            if (outcome is MutationOutcome.NoConnection) {
                _syncBlockedMessage.value = "Sin conexión: no se puede dar de baja ahora mismo."
            }
        }
    }

    /** Descarta el aviso de mutación bloqueada por falta de conexión (H07 PARTE 1, S015). */
    fun dismissSyncBlockedMessage() {
        _syncBlockedMessage.value = null
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

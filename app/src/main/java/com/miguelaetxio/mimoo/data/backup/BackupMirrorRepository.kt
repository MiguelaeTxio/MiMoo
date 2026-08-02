package com.miguelaetxio.mimoo.data.backup

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resumen de comparar dos [BackupBundle] completos -- H07 PARTE 1
 * (redefinición S008, regla de negocio de Miguel Ángel): la
 * sincronización automática ya NO fusiona altas/bajas por separado
 * como en el primer diseño (S008, primera vuelta); es todo-o-nada,
 * una de las dos copias completas sustituye a la otra. Esto solo hace
 * falta para saber SI hay diferencia y darle a Miguel Ángel un número
 * aproximado que mostrar en el aviso -- la decisión real
 * (sustituir/mantener) la toma [AutoSyncViewModel] completa, no
 * pista a pista.
 *
 * Comparación por conjuntos de claves, no por posición/orden:
 * pistas por `youtubeId`, favoritos por `artist`+`album`, playlists
 * por `name`. No compara metadatos dentro de una pista que ya
 * coincide por id (título editado, etc.) ni el contenido de una
 * playlist que ya coincide por nombre -- no hace falta para decidir
 * "sustituir entero o no", solo importaría si algún día se quisiera
 * fusionar en vez de sustituir, que es justo lo que esta regla de
 * negocio evita a propósito.
 * ---
 * Summary of comparing two full [BackupBundle]s -- H07 PART 1 (S008
 * redefinition, Miguel Ángel's business rule): automatic sync no
 * longer merges additions/deletions separately like the first design
 * (S008, first round); it's all-or-nothing, one of the two full
 * copies replaces the other. This is only needed to know WHETHER
 * there's a difference and give Miguel Ángel an approximate number to
 * show in the notice -- the actual decision (replace/keep) is made by
 * the full [AutoSyncViewModel], not track by track.
 *
 * Compared by key sets, not by position/order: tracks by `youtubeId`,
 * favorites by `artist`+`album`, playlists by `name`. Doesn't compare
 * metadata within a track that already matches by id (edited title,
 * etc.) nor the content of a playlist that already matches by name --
 * not needed to decide "replace the whole thing or not", would only
 * matter if merging instead of replacing were ever wanted, which is
 * exactly what this business rule avoids on purpose.
 */
data class BundleComparison(
    val identical: Boolean,
    val localTrackCount: Int,
    val remoteTrackCount: Int,
    val localFavoriteCount: Int,
    val remoteFavoriteCount: Int,
    val localPlaylistCount: Int,
    val remotePlaylistCount: Int,
    val localRadioCount: Int,
    val remoteRadioCount: Int,
    val localChannelCount: Int,
    val remoteChannelCount: Int,
    // Bug real (2026-08-02, ver comentario de BackupBundle.favoriteArtists):
    // favorite_artists nunca entraba en esta comparación, así que dos
    // dispositivos que solo divergieran en artistas favoritos quedaban
    // marcados `identical = true` y la sincronización nunca se disparaba --
    // exactamente el síntoma que reportó Miguel Ángel. Se suman aquí los
    // tres favoritos de la sesión de diseño de Favoritos (artista, sencillo
    // en streaming, playlist), mismo criterio que el resto.
    val localFavoriteArtistCount: Int,
    val remoteFavoriteArtistCount: Int,
    val localFavoriteTrackCount: Int,
    val remoteFavoriteTrackCount: Int,
    val localFavoritePlaylistCount: Int,
    val remoteFavoritePlaylistCount: Int,
) {
    /** Diferencia absoluta total, para el aviso ("X pistas/álbumes/listas/emisoras/canales de diferencia"). */
    val totalDifference: Int
        get() = kotlin.math.abs(localTrackCount - remoteTrackCount) +
            kotlin.math.abs(localFavoriteCount - remoteFavoriteCount) +
            kotlin.math.abs(localPlaylistCount - remotePlaylistCount) +
            kotlin.math.abs(localRadioCount - remoteRadioCount) +
            kotlin.math.abs(localChannelCount - remoteChannelCount) +
            kotlin.math.abs(localFavoriteArtistCount - remoteFavoriteArtistCount) +
            kotlin.math.abs(localFavoriteTrackCount - remoteFavoriteTrackCount) +
            kotlin.math.abs(localFavoritePlaylistCount - remoteFavoritePlaylistCount)
}

@Singleton
class BackupMirrorRepository @Inject constructor() {
    fun compare(local: BackupBundle, remote: BackupBundle): BundleComparison {
        val localTrackIds = local.tracks.map { it.youtubeId }.toSet()
        val remoteTrackIds = remote.tracks.map { it.youtubeId }.toSet()
        val localFavoriteKeys = local.favoriteAlbums.map { it.artist to it.album }.toSet()
        val remoteFavoriteKeys = remote.favoriteAlbums.map { it.artist to it.album }.toSet()
        val localPlaylistNames = local.playlists.map { it.name }.toSet()
        val remotePlaylistNames = remote.playlists.map { it.name }.toSet()
        // H07 Ampliación S014/S015 ("réplica total"): sin comparar también
        // radio/canal/ajustes, dos dispositivos que solo divergieran en eso
        // quedarían marcados `identical = true` y la sincronización nunca
        // dispararía la restauración -- exactamente el síntoma que reportó
        // Miguel Ángel en S013.
        val localRadioKeys = local.radioStations.map { it.stationUuid }.toSet()
        val remoteRadioKeys = remote.radioStations.map { it.stationUuid }.toSet()
        val localChannelKeys = local.channelSubscriptions.map { it.channelId }.toSet()
        val remoteChannelKeys = remote.channelSubscriptions.map { it.channelId }.toSet()
        val settingsIdentical = local.uiSettings == remote.uiSettings

        val localFavoriteArtistKeys = local.favoriteArtists.map { it.artist }.toSet()
        val remoteFavoriteArtistKeys = remote.favoriteArtists.map { it.artist }.toSet()
        val localFavoriteTrackKeys = local.favoriteTracks.map { it.youtubeId }.toSet()
        val remoteFavoriteTrackKeys = remote.favoriteTracks.map { it.youtubeId }.toSet()
        val localFavoritePlaylistKeys = local.favoritePlaylists.map { it.playlistName }.toSet()
        val remoteFavoritePlaylistKeys = remote.favoritePlaylists.map { it.playlistName }.toSet()

        val identical = localTrackIds == remoteTrackIds &&
            localFavoriteKeys == remoteFavoriteKeys &&
            localPlaylistNames == remotePlaylistNames &&
            localRadioKeys == remoteRadioKeys &&
            localChannelKeys == remoteChannelKeys &&
            settingsIdentical &&
            localFavoriteArtistKeys == remoteFavoriteArtistKeys &&
            localFavoriteTrackKeys == remoteFavoriteTrackKeys &&
            localFavoritePlaylistKeys == remoteFavoritePlaylistKeys

        return BundleComparison(
            identical = identical,
            localTrackCount = local.tracks.size,
            remoteTrackCount = remote.tracks.size,
            localFavoriteCount = local.favoriteAlbums.size,
            remoteFavoriteCount = remote.favoriteAlbums.size,
            localPlaylistCount = local.playlists.size,
            remotePlaylistCount = remote.playlists.size,
            localRadioCount = local.radioStations.size,
            remoteRadioCount = remote.radioStations.size,
            localChannelCount = local.channelSubscriptions.size,
            remoteChannelCount = remote.channelSubscriptions.size,
            localFavoriteArtistCount = local.favoriteArtists.size,
            remoteFavoriteArtistCount = remote.favoriteArtists.size,
            localFavoriteTrackCount = local.favoriteTracks.size,
            remoteFavoriteTrackCount = remote.favoriteTracks.size,
            localFavoritePlaylistCount = local.favoritePlaylists.size,
            remoteFavoritePlaylistCount = remote.favoritePlaylists.size,
        )
    }
}

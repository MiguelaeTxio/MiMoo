package com.miguelaetxio.mimoo.data.local.repository

import androidx.room.withTransaction
import com.miguelaetxio.mimoo.data.local.AppDatabase
import com.miguelaetxio.mimoo.data.local.dao.PlaylistDao
import com.miguelaetxio.mimoo.data.local.dao.SearchResultTrackDao
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus
import com.miguelaetxio.mimoo.data.local.entity.SearchResultTrack
import com.miguelaetxio.mimoo.data.remote.dto.ExternalLinkTrack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sustituye la FUENTE (el vídeo de YouTube real) de una pista que
 * lleva fallando siempre en la descarga -- petición explícita de
 * Miguel Ángel (2026-07-24), motivada por "River Euphrates" de Pixies:
 * un límite real y actual de yt-dlp deja el LP roto porque falta esa
 * pista, sin ninguna alternativa dentro de la app hasta ahora salvo
 * reintentar el mismo vídeo que ya sabemos que nunca va a funcionar.
 *
 * Vive fuera de SearchResultTrackRepository/PlaylistRepository (mismo
 * criterio que BackupImportRepository) porque cruza SearchResultTrackDao
 * y PlaylistDao dentro de una única transacción Room
 * (AppDatabase.withTransaction) -- youtubeId es la PRIMARY KEY de
 * search_result_tracks, así que "cambiar de vídeo" no es un UPDATE,
 * es borrar la fila vieja e insertar una nueva con un id distinto.
 * Sin la transacción, un fallo a mitad (p.ej. quedarse sin batería
 * entre el borrado y la reinserción) podría perder la pista por
 * completo.
 *
 * Título, artista, álbum, posición en el álbum y marca de favorito se
 * PRESERVAN tal cual de la fila original -- es la parte que garantiza
 * que la pista sustituida "mantiene su lugar en el disco" (petición
 * textual de Miguel Ángel) en vez de romper la secuencia del LP o el
 * nombre de archivo ya consistente con el resto del álbum. Solo
 * cambian los campos que describen la fuente real: youtubeId,
 * channelTitle, durationSeconds, thumbnailUrl. sourceUrl se limpia a
 * null -- apuntaba al enlace de origen del vídeo VIEJO, ya no es
 * exacto para el nuevo.
 *
 * Las referencias de playlist (PlaylistTrackCrossRef, ON DELETE
 * CASCADE hacia search_result_tracks) se capturan ANTES de borrar la
 * fila vieja y se recrean después con el youtubeId nuevo, en la misma
 * posición -- sin esto, sustituir la fuente sacaría la pista en
 * silencio de cualquier lista de reproducción en la que estuviera.
 * ---
 * Replaces the SOURCE (the real YouTube video) of a track that keeps
 * failing to download -- explicit request from Miguel Ángel
 * (2026-07-24), prompted by Pixies' "River Euphrates": a real, current
 * limitation of yt-dlp leaves the LP broken because that track is
 * missing, with no in-app alternative until now besides retrying the
 * exact same video already known to never work.
 *
 * Lives outside SearchResultTrackRepository/PlaylistRepository (same
 * criterion as BackupImportRepository) because it spans
 * SearchResultTrackDao and PlaylistDao inside a single Room transaction
 * (AppDatabase.withTransaction) -- youtubeId is search_result_tracks'
 * PRIMARY KEY, so "changing video" isn't an UPDATE, it's deleting the
 * old row and inserting a new one with a different id. Without the
 * transaction, a failure partway through (e.g. running out of battery
 * between the delete and the re-insert) could lose the track entirely.
 *
 * Title, artist, album, album position and favorite flag are PRESERVED
 * as-is from the original row -- that's the part guaranteeing the
 * replaced track "keeps its place on the disc" (Miguel Ángel's literal
 * request) instead of breaking the LP's sequence or the filename
 * already consistent with the rest of the album. Only the fields
 * describing the real source change: youtubeId, channelTitle,
 * durationSeconds, thumbnailUrl. sourceUrl is cleared to null -- it
 * pointed at the OLD video's origin link, no longer accurate for the
 * new one.
 *
 * Playlist references (PlaylistTrackCrossRef, ON DELETE CASCADE
 * towards search_result_tracks) are captured BEFORE deleting the old
 * row and recreated afterwards with the new youtubeId, at the same
 * position -- without this, replacing the source would silently drop
 * the track from any playlist it was in.
 */
@Singleton
class TrackAlternativeRepository @Inject constructor(
    private val database: AppDatabase,
    private val trackDao: SearchResultTrackDao,
    private val playlistDao: PlaylistDao,
) {
    suspend fun replaceFailedTrackSource(
        original: SearchResultTrack,
        alternative: ExternalLinkTrack,
    ): SearchResultTrack = database.withTransaction {
        val crossRefs = playlistDao.getCrossRefsForTrack(original.youtubeId)

        trackDao.delete(original)

        val replacement = original.copy(
            youtubeId = alternative.youtubeId,
            channelTitle = alternative.channelTitle,
            durationSeconds = alternative.durationSeconds,
            thumbnailUrl = alternative.thumbnailUrl,
            filePath = null,
            downloadStatus = DownloadStatus.PENDING,
            downloadProgress = 0,
            lastSearchedAt = System.currentTimeMillis(),
            sourceUrl = null,
        )
        trackDao.insert(replacement)

        crossRefs.forEach { ref ->
            playlistDao.addTrackToPlaylist(ref.copy(youtubeId = alternative.youtubeId))
        }

        replacement
    }
}

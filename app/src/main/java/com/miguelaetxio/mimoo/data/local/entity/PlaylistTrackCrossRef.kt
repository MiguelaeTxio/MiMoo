package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join table between Playlist and SearchResultTrack, with an explicit
 * position for manual reordering (Hito 04). Composite primary key
 * (playlistId, youtubeId) — a track can only appear once per playlist,
 * matching the "quitar pista" UX (there is exactly one row to remove,
 * never a duplicate to disambiguate).
 *
 * onDelete = CASCADE on both foreign keys, by design decision (not
 * left as a TODO): deleting a playlist should not leave orphaned
 * cross-ref rows, and deleting a track from search_result_tracks
 * (e.g. Biblioteca's "borrar descarga" on a synthetic row, which
 * fully removes the row rather than resetting it — see
 * LibraryViewModel.deleteDownload) should remove it from any
 * playlist rather than leave a dangling reference that
 * getTracksForPlaylist's JOIN would silently drop anyway. If Miguel
 * Ángel wants "soft" removal (track disappears from the playlist list
 * but the playlist keeps a placeholder) instead, that is a deliberate
 * product decision to revisit explicitly, not a technical default.
 * ---
 * Tabla de unión entre Playlist y SearchResultTrack, con una posición
 * explícita para la reordenación manual (Hito 04). Clave primaria
 * compuesta (playlistId, youtubeId) — una pista solo puede aparecer
 * una vez por playlist, coincidiendo con la UX de "quitar pista"
 * (hay exactamente una fila que eliminar, nunca un duplicado que
 * desambiguar).
 *
 * onDelete = CASCADE en ambas claves foráneas, por decisión de diseño
 * (no dejado como un TODO): borrar una playlist no debería dejar
 * filas de unión huérfanas, y borrar una pista de
 * search_result_tracks (p.ej. el "borrar descarga" de Biblioteca
 * sobre una fila sintética, que elimina la fila por completo en vez
 * de resetearla — ver LibraryViewModel.deleteDownload) debería
 * quitarla de cualquier playlist en vez de dejar una referencia
 * colgante que el JOIN de getTracksForPlaylist descartaría en
 * silencio de todas formas. Si Miguel Ángel quiere un borrado
 * "blando" (la pista desaparece de la lista pero la playlist conserva
 * un hueco) en su lugar, es una decisión de producto deliberada a
 * revisar explícitamente, no un valor técnico por defecto.
 */
@Entity(
    tableName = "playlist_track_cross_refs",
    primaryKeys = ["playlistId", "youtubeId"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SearchResultTrack::class,
            parentColumns = ["youtubeId"],
            childColumns = ["youtubeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("youtubeId"), Index("playlistId")],
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val youtubeId: String,
    val position: Int,
)

package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity

/**
 * Tema marcado como "no me gusta" -- H16 (2026-08-05). Decisión
 * explícita de Miguel Ángel: excluye CUALQUIER VERSIÓN de ese tema de
 * ese artista (directo, remasterizado, estudio...), no solo el vídeo
 * de YouTube concreto que sonaba al pulsar "no me gusta". Clave
 * compuesta (artist, title) -- mismo patrón que FavoriteAlbum -- con
 * los valores TAL CUAL los tenía la pista en el momento de marcarla,
 * para poder mostrarlos en la vista CRUD.
 *
 * La comparación real contra candidatos de Radio/Popurrí usa
 * SearchNormalizer.songTitleKey(title, artist) +
 * SearchNormalizer.normalizeArtistName(artist), el mismo mecanismo que
 * ya colapsa versiones distintas del mismo tema a una única clave en
 * el resto de la app (deduplicación de PopurriRepository) -- nunca por
 * youtubeId, que identifica un vídeo concreto, no la canción.
 * ---
 * Track marked as "disliked" -- H16 (2026-08-05). Explicit decision
 * from Miguel Ángel: excludes ANY VERSION of that track by that artist
 * (live, remastered, studio...), not just the specific YouTube video
 * that was playing when "no me gusta" was pressed. Composite key
 * (artist, title) -- same pattern as FavoriteAlbum -- with the values
 * the track had AT THE MOMENT it was marked, so they can be shown in
 * the CRUD screen.
 *
 * The real comparison against Radio/Popurrí candidates uses
 * SearchNormalizer.songTitleKey(title, artist) +
 * SearchNormalizer.normalizeArtistName(artist), the same mechanism
 * that already collapses different versions of the same song into one
 * key elsewhere in the app (PopurriRepository dedup) -- never by
 * youtubeId, which identifies one specific video, not the song.
 */
@Entity(
    tableName = "disliked_tracks",
    primaryKeys = ["artist", "title"],
)
data class DislikedTrack(
    val artist: String,
    val title: String,
    val dislikedAt: Long,
)

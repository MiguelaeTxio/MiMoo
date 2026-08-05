package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity

/**
 * Artista marcado como "no me gusta" -- H16 (2026-08-05), a petición
 * explícita de Miguel Ángel surgida durante el diseño de H15.
 * Exclusión GLOBAL y DURA: a diferencia de RadioSessionHistoryManager
 * (preferencia SUAVE, solo evita repetir si hay alternativa), un
 * artista en esta lista queda fuera de cualquier sesión de Radio
 * (H08/H15) y de cualquier popurrí de Favoritos, sin excepción, aunque
 * eso deje un cupo sin candidatos (degradación normal de la cascada
 * 80/10/10, mismo principio que ya usa el resto de la Radio).
 *
 * Clave primaria simple por nombre de texto (artist), mismo patrón
 * que FavoriteArtist -- el artista tampoco es una entidad propia en
 * Room. Se guarda el nombre tal cual lo escribió/eligió el usuario
 * (para poder mostrarlo en la vista CRUD); la comparación real contra
 * candidatos de Radio/Popurrí usa
 * SearchNormalizer.normalizeArtistName() en tiempo de ejecución, no en
 * el esquema.
 * ---
 * Artist marked as "disliked" -- H16 (2026-08-05), explicit request
 * from Miguel Ángel that came up while designing H15. GLOBAL, HARD
 * exclusion: unlike RadioSessionHistoryManager (a SOFT preference that
 * only avoids repeats if an alternative exists), an artist on this
 * list is kept out of any Radio session (H08/H15) and any Favorites
 * popurrí, without exception, even if that leaves a quota with no
 * candidates (normal 80/10/10 cascade degradation, same principle the
 * rest of Radio already uses).
 *
 * Simple primary key by text name (artist), same pattern as
 * FavoriteArtist. Stores the name as the user typed/chose it (so it
 * can be shown in the CRUD screen); the real comparison against
 * Radio/Popurrí candidates uses
 * SearchNormalizer.normalizeArtistName() at runtime, not in the
 * schema.
 */
@Entity(
    tableName = "disliked_artists",
    primaryKeys = ["artist"],
)
data class DislikedArtist(
    val artist: String,
    val dislikedAt: Long,
)

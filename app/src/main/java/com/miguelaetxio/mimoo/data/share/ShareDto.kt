package com.miguelaetxio.mimoo.data.share

import com.miguelaetxio.mimoo.data.backup.BackupBundle

/**
 * Sobre de un archivo de compartición H10 (extensión `.mimoo`).
 * Reutiliza [BackupBundle] TAL CUAL para el contenido -- es
 * exactamente el mismo formato que ya usan H06 (Exportar/Importar
 * manual) y H07 (sincronización automática), porque ya carga todo lo
 * que "réplica total" necesita según la definición de Miguel Ángel
 * (S011): favoritos (`isFavorite` por pista, `favoriteAlbums`),
 * enlaces originales (`sourceUrl`), ediciones de nombre
 * (`artist`/`album` estructurados, ya desacoplados de
 * `channelTitle`), y orden de las canciones (`trackPosition` en
 * pistas sueltas, `trackYoutubeIdsInOrder` en playlists).
 *
 * Para el ALCANCE (qué subconjunto entra en `bundle`), cada nivel de
 * compartición de la lista de Miguel Ángel (Biblioteca, Artista,
 * Álbum, Tema de álbum, Sencillos, Sencillo, Listas de reproducción,
 * Lista de reproducción) filtra `bundle.tracks` (y
 * `bundle.playlists`/`bundle.favoriteAlbums` cuando aplica) antes de
 * envolver -- ver `ShareCodeRepository`. Canales/Canal (niveles 9-10)
 * quedan para cuando H11 lo permita.
 *
 * `scopeLabel` es solo para mostrarle al receptor qué está a punto de
 * importar antes de confirmar (p.ej. "Biblioteca completa (42
 * pistas)") -- nunca se usa para decidir lógica de importación, eso
 * lo decide el contenido real de `bundle`.
 * ---
 * Envelope for an H10 share file (`.mimoo` extension). Reuses
 * [BackupBundle] AS-IS for the content -- it's the exact same format
 * H06 (manual Export/Import) and H07 (automatic sync) already use,
 * because it already carries everything Miguel Ángel's "réplica
 * total" (S011) definition needs: favorites (per-track `isFavorite`,
 * `favoriteAlbums`), original links (`sourceUrl`), name edits
 * (structured `artist`/`album`, already decoupled from
 * `channelTitle`), and track order (`trackPosition` on loose tracks,
 * `trackYoutubeIdsInOrder` on playlists).
 *
 * `scopeLabel` is only to show the receiver what they're about to
 * import before confirming (e.g. "Whole library (42 tracks)") -- never
 * used to decide import logic, that's decided by `bundle`'s real
 * content.
 */
data class ShareBundle(
    val version: Int = CURRENT_VERSION,
    val scopeLabel: String,
    val sharedAt: Long,
    val bundle: BackupBundle,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

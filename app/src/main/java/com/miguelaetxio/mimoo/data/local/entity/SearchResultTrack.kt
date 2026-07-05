package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Playback status of a search result that the user has chosen to
 * download for offline listening. QUEUED (añadido: pantalla
 * "Descargas") marca el momento exacto entre "el usuario pidió la
 * descarga" (DownloadQueueManager.enqueue()) y "DownloadWorker ya
 * empezó a ejecutarla" (DOWNLOADING) — sin este estado intermedio,
 * una pista recién encolada era indistinguible en Room de una que
 * nadie ha pedido descargar nunca, porque ambas quedaban en PENDING.
 * ---
 * Estado de descarga de un resultado de búsqueda que el usuario ha
 * elegido descargar para escucha offline. QUEUED (añadido: pantalla
 * "Descargas") marca el momento exacto entre "el usuario pidió la
 * descarga" (DownloadQueueManager.enqueue()) y "DownloadWorker ya
 * empezó a ejecutarla" (DOWNLOADING) — sin este estado intermedio,
 * una pista recién encolada era indistinguible en Room de una que
 * nadie ha pedido descargar nunca, porque ambas quedaban en PENDING.
 */
enum class DownloadStatus { PENDING, QUEUED, DOWNLOADING, DONE, ERROR }

/**
 * A single audio track resolved from a YouTube search result.
 * Unlike the previous Artist/Album/Track CRUD model, this entity is
 * never filled in by hand: every field is populated from YouTube
 * metadata at search time. artist is a structured, queryable field
 * (added in Hito 03 PASO 2) used for storage foldering and Biblioteca
 * grouping/sorting; it starts as a copy of channelTitle but is
 * decoupled so a future manual edit (PASO 7) can diverge from it.
 * album stays null until the MusicBrainz milestone or a manual edit
 * exists. isFavorite (PASO 4) is a plain user toggle, independent of
 * downloadStatus — a track can be favorited whether or not it has
 * been downloaded yet.
 * ---
 * Una pista de audio resuelta a partir de un resultado de búsqueda de
 * YouTube. A diferencia del modelo CRUD anterior de Artist/Album/Track,
 * esta entidad nunca se rellena a mano: todos los campos proceden de
 * los metadatos de YouTube en el momento de la búsqueda. artist es un
 * campo estructurado y consultable (añadido en el Hito 03 PASO 2) que
 * se usa para la organización de carpetas y para agrupar/ordenar en
 * la Biblioteca; empieza como copia de channelTitle pero está
 * desacoplado para que una futura edición manual (PASO 7) pueda
 * divergir de él. album permanece null hasta que exista el hito de
 * MusicBrainz o una edición manual. isFavorite (PASO 4) es un simple
 * marcador del usuario, independiente de downloadStatus — una pista
 * puede marcarse como favorita se haya descargado o no.
 */
@Entity(tableName = "search_result_tracks")
data class SearchResultTrack(
    @PrimaryKey val youtubeId: String,  // 11-char YouTube video ID
    val title: String,
    val channelTitle: String,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val filePath: String? = null,       // local .opus path once downloaded
    val downloadStatus: DownloadStatus = DownloadStatus.PENDING,
    // Porcentaje real 0-100 durante DOWNLOADING (progress_hooks de
    // yt-dlp vía Chaquopy, ver DownloadWorker). 0 en PENDING/QUEUED,
    // 100 en DONE, indefinido/irrelevante en ERROR. Pantalla
    // "Descargas".
    val downloadProgress: Int = 0,
    val lastSearchedAt: Long = System.currentTimeMillis(),
    val artist: String? = null,         // structured artist, PASO 2 H03
    val album: String? = null,          // null until MusicBrainz/manual edit
    val isFavorite: Boolean = false,    // PASO 4 H03
    val coverArtUrl: String? = null,    // MusicBrainz+CAA front cover, PASO 6 H03
    // Posición real de la pista dentro del álbum (0-indexed), tal
    // como llegó de la fuente (orden de la playlist de YouTube Music
    // en ImportLinkViewModel, orden de MusicBrainz en
    // AlbumSearchViewModel). Null cuando no se conoce -- pistas
    // sueltas de SearchScreen, o filas sintéticas de
    // LibraryReconciler -- en cuyo caso Biblioteca cae a orden
    // alfabético para esas pistas. Reportado por Miguel Ángel
    // (2026-07-03): Biblioteca ordenaba SIEMPRE alfabéticamente,
    // nunca por posición real de disco.
    val trackPosition: Int? = null,
    // Enlace de origen (la URL de playlist/álbum pegada en "Importar
    // enlace"), para poder compartirlo luego por WhatsApp -- petición
    // explícita de Miguel Ángel (2026-07-04). Null para pistas de
    // Buscar álbum/Búsqueda (no vienen de un enlace pegado) y para
    // filas ya existentes antes de esta migración; la UI cae a
    // youtubeUrl (el vídeo individual) en ese caso.
    val sourceUrl: String? = null,
) {
    /**
     * Null para pistas sintéticas (youtubeId con prefijo "local:",
     * generado por LibraryReconciler al reconciliar archivos de disco
     * sin fila real en Room -- ver LibraryReconciler.LOCAL_ID_PREFIX).
     * Ese "youtubeId" es un hash interno, no un ID real de vídeo, así
     * que construir una URL youtu.be con él da un enlace roto. Bug
     * real reportado por Miguel Ángel (2026-07-05): compartir el
     * álbum de una pista sintética generaba un enlace youtu.be inválido
     * cuya vista previa en WhatsApp mostraba HTML/JS en crudo en vez
     * de un título real -- exactamente lo que se veía en la captura.
     * ---
     * Null for synthetic tracks (youtubeId with a "local:" prefix,
     * generated by LibraryReconciler when reconciling disk files with
     * no real Room row -- see LibraryReconciler.LOCAL_ID_PREFIX). That
     * "youtubeId" is an internal hash, not a real video ID, so building
     * a youtu.be URL with it produces a broken link. Real bug reported
     * by Miguel Ángel (2026-07-05): sharing a synthetic track's album
     * produced an invalid youtu.be link whose WhatsApp preview showed
     * raw HTML/JS instead of a real title -- exactly what the
     * screenshot showed.
     */
    val youtubeUrl: String?
        get() = if (youtubeId.startsWith("local:")) null else "https://youtu.be/$youtubeId"

    /** Mejor enlace disponible para compartir -- ver comentario de sourceUrl. */
    val shareableUrl: String? get() = sourceUrl ?: youtubeUrl
}

package com.miguelaetxio.mimoo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Origen de una escucha, para la pantalla de Estadísticas.
 * LOCAL: archivo del dispositivo. STREAMING: pista reproducida en
 * directo desde YouTube. RADIO: pista añadida por la Radio automática
 * (H08). MIMOOUTCAST: pista dentro de una sesión de miMooutCast (H15).
 * STATION: emisora de radio online (H09).
 * ---
 * Listening source, for the Statistics screen. LOCAL: device file.
 * STREAMING: track played live from YouTube. RADIO: track added by the
 * automatic Radio (H08). MIMOOUTCAST: track inside a miMooutCast
 * session (H15). STATION: online radio station (H09).
 */
enum class PlayKind { LOCAL, STREAMING, RADIO, MIMOOUTCAST, STATION }

/**
 * Una escucha pendiente de subir a Drive. Tras sumarse a los totales de
 * Drive (StatsSyncManager) la fila se borra: el dispositivo no guarda
 * historial. Solo se crea cuando la pista (o emisora) ha
 * sonado al menos PlaybackStatsRecorder.MIN_LISTEN_MS; después, la
 * misma fila va acumulando listenedMs mientras siga sonando esa pista.
 * kind se guarda como el nombre de PlayKind.
 * ---
 * A recorded listen. Only created once the track (or station) has
 * played for at least PlaybackStatsRecorder.MIN_LISTEN_MS; afterwards
 * the same row keeps accumulating listenedMs while that track plays.
 * kind is stored as the PlayKind name.
 */
@Entity(tableName = "play_events")
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playedAt: Long,
    val kind: String,
    val title: String,
    val artist: String?,
    val youtubeId: String?,
    val originLabel: String?,
    val listenedMs: Long,
    // 0 mientras la pista sigue sonando; 1 cuando ya terminó y la
    // escucha está lista para sumarse a los totales de Drive.
    // 0 while the track is still playing; 1 once it ended and the
    // listen is ready to be added to Drive's totals.
    @ColumnInfo(defaultValue = "0") val closed: Int = 0,
)

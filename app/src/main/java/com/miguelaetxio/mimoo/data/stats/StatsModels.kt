package com.miguelaetxio.mimoo.data.stats

import com.miguelaetxio.mimoo.data.local.entity.PlayEvent
import com.miguelaetxio.mimoo.data.local.entity.PlayKind

/**
 * Totales de uso que viven en Drive (campo `stats` del SyncEnvelope),
 * separados del resto de la copia: no entran en la comparación de
 * pistas ni en el diálogo "con cuál te quedas". Cada dispositivo solo
 * SUMA sus escuchas nuevas a estos totales. Todos los campos tienen
 * valor por defecto para que Gson pueda leer copias que no los traen.
 * ---
 * Usage totals that live in Drive (the SyncEnvelope `stats` field),
 * kept apart from the rest of the copy: they take no part in the track
 * comparison nor in the "which one do you keep" dialog. Each device
 * only ADDS its new listens to these totals. Every field has a default
 * so Gson can read copies that lack them.
 */
data class StatsCounter(
    val plays: Int = 0,
    val ms: Long = 0L,
)

data class StatsTrackEntry(
    val title: String = "",
    val artist: String? = null,
    val plays: Int = 0,
)

data class StatsBlock(
    val firstPlayedAt: Long? = null,
    val byKind: Map<String, StatsCounter> = emptyMap(),
    val tracks: Map<String, StatsTrackEntry> = emptyMap(),
    val artists: Map<String, StatsCounter> = emptyMap(),
    val lists: Map<String, StatsCounter> = emptyMap(),
    val stations: Map<String, StatsCounter> = emptyMap(),
    val mimooutcastArtists: Map<String, StatsCounter> = emptyMap(),
) {
    fun isEmpty(): Boolean = byKind.isEmpty() && tracks.isEmpty() && stations.isEmpty()
}

data class PlayKindTotal(
    val kind: String,
    val plays: Int,
    val totalMs: Long,
)

data class TopTrackRow(
    val title: String,
    val artist: String?,
    val plays: Int,
)

data class TopLabelRow(
    val label: String,
    val plays: Int,
    val totalMs: Long,
)

object StatsAggregator {

    private fun MutableMap<String, StatsCounter>.add(key: String, ms: Long) {
        val old = this[key] ?: StatsCounter()
        this[key] = StatsCounter(old.plays + 1, old.ms + ms)
    }

    /**
     * Devuelve `base` más las escuchas de `events`. No modifica `base`.
     * ---
     * Returns `base` plus the listens in `events`. Does not modify `base`.
     */
    fun apply(base: StatsBlock?, events: List<PlayEvent>): StatsBlock {
        val b = base ?: StatsBlock()
        if (events.isEmpty()) return b
        val byKind = b.byKind.toMutableMap()
        val tracks = b.tracks.toMutableMap()
        val artists = b.artists.toMutableMap()
        val lists = b.lists.toMutableMap()
        val stations = b.stations.toMutableMap()
        val mimooutcast = b.mimooutcastArtists.toMutableMap()
        var first = b.firstPlayedAt
        for (e in events) {
            byKind.add(e.kind, e.listenedMs)
            if (first == null || e.playedAt < first) first = e.playedAt
            if (!e.originLabel.isNullOrBlank()) lists.add(e.originLabel, e.listenedMs)
            if (e.kind == PlayKind.STATION.name) {
                stations.add(e.title, e.listenedMs)
                continue
            }
            val artist = e.artist?.trim().orEmpty()
            val trackKey = "$artist|${e.title}"
            val old = tracks[trackKey]
            tracks[trackKey] = StatsTrackEntry(
                title = e.title,
                artist = artist.ifEmpty { null },
                plays = (old?.plays ?: 0) + 1,
            )
            if (artist.isNotEmpty()) {
                artists.add(artist, e.listenedMs)
                if (e.kind == PlayKind.MIMOOUTCAST.name) mimooutcast.add(artist, e.listenedMs)
            }
        }
        return StatsBlock(first, byKind, tracks, artists, lists, stations, mimooutcast)
    }

    fun totals(b: StatsBlock): Map<String, PlayKindTotal> =
        b.byKind.mapValues { (k, v) -> PlayKindTotal(k, v.plays, v.ms) }

    fun topTracks(b: StatsBlock, limit: Int): List<TopTrackRow> =
        b.tracks.values
            .sortedWith(compareByDescending<StatsTrackEntry> { it.plays }.thenBy { it.title })
            .take(limit)
            .map { TopTrackRow(it.title, it.artist, it.plays) }

    fun topByPlays(map: Map<String, StatsCounter>, limit: Int): List<TopLabelRow> =
        map.entries
            .sortedWith(compareByDescending<Map.Entry<String, StatsCounter>> { it.value.plays }
                .thenByDescending { it.value.ms })
            .take(limit)
            .map { TopLabelRow(it.key, it.value.plays, it.value.ms) }

    fun topByTime(map: Map<String, StatsCounter>, limit: Int): List<TopLabelRow> =
        map.entries
            .sortedByDescending { it.value.ms }
            .take(limit)
            .map { TopLabelRow(it.key, it.value.plays, it.value.ms) }
}

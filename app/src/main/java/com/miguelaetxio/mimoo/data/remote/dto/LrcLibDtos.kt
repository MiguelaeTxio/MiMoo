package com.miguelaetxio.mimoo.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO de lrclib.net (H17, S031) -- campos verificados en línea esta
 * misma sesión contra ejemplos reales de respuesta de la API (no
 * asumidos de memoria de entrenamiento, directriz 4.5 del
 * MASTER_DOCUMENT.md): `GET /api/get` y `GET /api/search` devuelven
 * la misma forma de objeto. `duration` es la duración del tema en
 * lrclib, en segundos (puede llevar decimales). `plainLyrics` y
 * `syncedLyrics` son independientes entre sí -- un tema puede tener
 * ambas, solo una, o ninguna (`instrumental = true` es el caso
 * explícito de "sin letra a propósito", pero también puede no haber
 * ninguna de las dos sin ese flag, simplemente porque nadie la ha
 * aportado todavía). `syncedLyrics` viene en formato LRC completo
 * como una única cadena con saltos de línea (`[mm:ss.xx] texto`), sin
 * parsear -- el parseo a líneas con timestamp vive en el cliente,
 * fuera de este DTO.
 * ---
 * lrclib.net DTO (H17, S031) -- fields verified online this same
 * session against real API response examples (not assumed from
 * training-data memory, MASTER_DOCUMENT.md directive 4.5): `GET
 * /api/get` and `GET /api/search` return the same object shape.
 */
data class LrcLibLyricsResult(
    @SerializedName("id") val id: Long,
    @SerializedName("trackName") val trackName: String,
    @SerializedName("artistName") val artistName: String,
    @SerializedName("albumName") val albumName: String? = null,
    @SerializedName("duration") val duration: Double? = null,
    @SerializedName("instrumental") val instrumental: Boolean = false,
    @SerializedName("plainLyrics") val plainLyrics: String? = null,
    @SerializedName("syncedLyrics") val syncedLyrics: String? = null,
)

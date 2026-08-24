package com.miguelaetxio.mimoo.data.access

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferencias de interfaz de MiMoo (S011) -- interruptor de borde
 * del efecto de cristal ("añade un toggle en ajustes para cambiar de
 * borde a sin borde", petición explícita de Miguel Ángel) y, desde
 * S016, el cupo 80/10/10 de Radio (H08) hecho configurable en
 * Ajustes -- ver `ANNEX_H08.md` punto 6 y `RESUMPTION_POINT.md`
 * (siguiente sesión de H08 tras S015). Mismo patrón de
 * SharedPreferences que `AccessPinManager`, con un `StateFlow` encima
 * para que el cambio se refleje en vivo en toda la app sin tener que
 * reiniciarla -- `MainActivity` lo provee como `CompositionLocal`
 * (`LocalGlassBorderEnabled`, ver `ui/theme/Glass.kt`) para que
 * cualquier pantalla lo lea sin tener que pasarlo a mano por cada
 * ViewModel; el cupo de Radio, en cambio, se inyecta directamente
 * (`PlayerManager`/`SettingsViewModel`) porque solo esos dos sitios lo
 * necesitan.
 * ---
 * MiMoo UI preferences (S011) -- glass-effect border toggle, plus
 * (S016) H08's 80/10/10 Radio quota made configurable in Settings --
 * see `ANNEX_H08.md` point 6. Same SharedPreferences pattern as
 * `AccessPinManager`, with a `StateFlow` on top so the change reflects
 * live across the whole app without restarting it -- `MainActivity`
 * provides the border toggle as a `CompositionLocal`
 * (`LocalGlassBorderEnabled`, see `ui/theme/Glass.kt`); the Radio
 * quota is injected directly (`PlayerManager`/`SettingsViewModel`)
 * since only those two spots need it.
 */
@Singleton
class UiPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val PREFS_NAME = "mimoo_ui_prefs"
        const val KEY_GLASS_BORDER_ENABLED = "glass_border_enabled"

        // S016 -- cupo 80/10/10 de Radio (H08). QUITADO en S027 --
        // sustituido por completo por el recuento fijo por bloque de
        // 10 de más abajo. Se deja sin usar `KEY_RADIO_EXPLORE_PERCENT`/
        // `KEY_RADIO_DISCO_PERCENT` como claves de SharedPreferences
        // muertas (no se leen ni escriben en ningún sitio) en vez de
        // reutilizarlas para otra cosa, para no arrastrar valores
        // viejos con un significado distinto si alguien reinstala
        // sobre una versión anterior.

        // S026 -- umbral de GenreMatchQuality (intersección/unión de
        // géneros específicos, ver esa clase). Petición explícita de
        // Miguel Ángel: "un 30% o un 40%... configurable en ajustes,
        // con escalones de diez". 40% por defecto -- ver
        // GenreMatchQuality.kt para el porqué de ese número, verificado
        // contra datos reales antes de fijarlo.
        const val KEY_RADIO_GENRE_MATCH_THRESHOLD_PERCENT = "radio_genre_match_threshold_percent"
        const val DEFAULT_RADIO_GENRE_MATCH_THRESHOLD_PERCENT = 40

        // S027 -- ventana de años del ancla (ver
        // PlayerManager.resolveYoutubeCandidate()). Por defecto 10 --
        // orden textual de Miguel Ángel tras el caso PISTONES (España,
        // new wave, 1984): con ±5 casi todo lo que YouTube devuelve de
        // cada artista queda fuera, porque no siempre es justo de esa
        // época. Configurable a 5 en Ajustes para cuando el ancla es
        // extranjera y hay mucho más donde elegir (más precisión sin
        // quedarse corto de candidatos).
        const val KEY_RADIO_YEAR_WINDOW = "radio_year_window"
        const val DEFAULT_RADIO_YEAR_WINDOW = 10

        // S027 -- rediseño completo del cupo de Radio, orden textual de
        // Miguel Ángel tras el desastre de AC/DC (Thin Lizzy/Them/
        // Spencer Davis Group repetidos 42 de ~45 veces): ya no es un
        // % que se reparte al agotarse una porción -- es un recuento
        // FIJO por cada bloque de 10 canciones. Solo se guardan
        // conocidos y disco; desconocidos se deriva siempre como
        // 10 - conocidos - disco, igual que el diccionario se derivaba
        // de 100 - exploración - disco en el sistema viejo.
        const val KEY_RADIO_KNOWN_QUOTA_PER_TEN = "radio_known_quota_per_ten"
        const val KEY_RADIO_DISCO_QUOTA_PER_TEN = "radio_disco_quota_per_ten"
        const val DEFAULT_RADIO_KNOWN_QUOTA_PER_TEN = 6
        const val DEFAULT_RADIO_DISCO_QUOTA_PER_TEN = 2

        // 2026-08-24 -- refuerzo de volumen (LoudnessEnhancer, ver
        // AudioNormalizer.kt), petición explícita de Miguel Ángel:
        // "podemos ponerlo como control en settings?" -- antes era una
        // constante fija en código (+6dB). En milibelios (100mB = 1dB,
        // ver LoudnessEnhancer.setTargetGain()); 0 = sin refuerzo.
        // Recorte a 0..1200 (0-12dB) -- pasado ese punto la propia
        // documentación de la API avisa de compresión/distorsión
        // constante en casi cualquier tema, verificado antes de fijar
        // el tope.
        const val KEY_VOLUME_BOOST_MILLIBELS = "volume_boost_millibels"
        const val DEFAULT_VOLUME_BOOST_MILLIBELS = 600
        const val MAX_VOLUME_BOOST_MILLIBELS = 1200
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _glassBorderEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_GLASS_BORDER_ENABLED, false))
    val glassBorderEnabled: StateFlow<Boolean> = _glassBorderEnabled.asStateFlow()

    fun setGlassBorderEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_GLASS_BORDER_ENABLED, enabled) }
        _glassBorderEnabled.value = enabled
    }

    private val _radioGenreMatchThresholdPercent = MutableStateFlow(
        prefs.getInt(KEY_RADIO_GENRE_MATCH_THRESHOLD_PERCENT, DEFAULT_RADIO_GENRE_MATCH_THRESHOLD_PERCENT)
    )

    /**
     * S026 -- % mínimo de intersección/unión de géneros específicos
     * (ver `GenreMatchQuality`) para que un candidato entre en la
     * Radio. Ajustable en escalones de 10 (0, 10, 20... 100).
     */
    val radioGenreMatchThresholdPercent: StateFlow<Int> = _radioGenreMatchThresholdPercent.asStateFlow()

    /** S026 -- redondea al escalón de 10 más cercano y recorta a 0..100. */
    fun setRadioGenreMatchThresholdPercent(percent: Int) {
        val stepped = ((percent + 5) / 10) * 10
        val clamped = stepped.coerceIn(0, 100)
        prefs.edit { putInt(KEY_RADIO_GENRE_MATCH_THRESHOLD_PERCENT, clamped) }
        _radioGenreMatchThresholdPercent.value = clamped
    }

    /** S027 -- ver el kdoc de KEY_RADIO_YEAR_WINDOW. Solo 5 o 10 -- se recorta a lo más cercano de los dos. */
    private val _radioYearWindow = MutableStateFlow(
        prefs.getInt(KEY_RADIO_YEAR_WINDOW, DEFAULT_RADIO_YEAR_WINDOW)
    )
    val radioYearWindow: StateFlow<Int> = _radioYearWindow.asStateFlow()

    fun setRadioYearWindow(years: Int) {
        val clamped = if (years <= 7) 5 else 10
        prefs.edit { putInt(KEY_RADIO_YEAR_WINDOW, clamped) }
        _radioYearWindow.value = clamped
    }

    private val _radioKnownQuotaPerTen = MutableStateFlow(
        prefs.getInt(KEY_RADIO_KNOWN_QUOTA_PER_TEN, DEFAULT_RADIO_KNOWN_QUOTA_PER_TEN)
    )
    /** S027 -- de cada 10 canciones de Radio, cuántas deben ser de artistas conocidos en España. */
    val radioKnownQuotaPerTen: StateFlow<Int> = _radioKnownQuotaPerTen.asStateFlow()

    private val _radioDiscoQuotaPerTen = MutableStateFlow(
        prefs.getInt(KEY_RADIO_DISCO_QUOTA_PER_TEN, DEFAULT_RADIO_DISCO_QUOTA_PER_TEN)
    )
    /** S027 -- de cada 10 canciones de Radio, cuántas deben ser de la biblioteca local del usuario. */
    val radioDiscoQuotaPerTen: StateFlow<Int> = _radioDiscoQuotaPerTen.asStateFlow()

    /**
     * S027 -- de cada 10 canciones de Radio, cuántas pueden ser de
     * artistas sin éxito catalogado en España. Nunca se guarda ni se
     * expone como `StateFlow` propio: siempre es el resto tras
     * conocidos y disco, igual que el diccionario se derivaba de
     * 100 - exploración - disco en el sistema de porcentajes.
     */
    val radioUnknownQuotaPerTen: Int
        get() = (10 - _radioKnownQuotaPerTen.value - _radioDiscoQuotaPerTen.value).coerceAtLeast(0)

    /** S027 -- fija la cuota de conocidos, recortando para dejar sitio a disco (suma máxima 10). */
    fun setRadioKnownQuotaPerTen(quota: Int) {
        val clamped = quota.coerceIn(0, 10 - _radioDiscoQuotaPerTen.value)
        prefs.edit { putInt(KEY_RADIO_KNOWN_QUOTA_PER_TEN, clamped) }
        _radioKnownQuotaPerTen.value = clamped
    }

    /** S027 -- fija la cuota de disco, recortando para dejar sitio a conocidos (suma máxima 10). */
    fun setRadioDiscoQuotaPerTen(quota: Int) {
        val clamped = quota.coerceIn(0, 10 - _radioKnownQuotaPerTen.value)
        prefs.edit { putInt(KEY_RADIO_DISCO_QUOTA_PER_TEN, clamped) }
        _radioDiscoQuotaPerTen.value = clamped
    }

    private val _volumeBoostMillibels = MutableStateFlow(
        prefs.getInt(KEY_VOLUME_BOOST_MILLIBELS, DEFAULT_VOLUME_BOOST_MILLIBELS)
    )

    /**
     * 2026-08-24 -- refuerzo de volumen (`LoudnessEnhancer`, ver
     * `AudioNormalizer.kt`). En milibelios (100mB = 1dB); 0 = sin
     * refuerzo. `PlayerManager` lo lee en vivo -- ajustarlo en Ajustes
     * se nota en la reproducción en curso sin reiniciar nada.
     */
    val volumeBoostMillibels: StateFlow<Int> = _volumeBoostMillibels.asStateFlow()

    /** Recorta a 0..MAX_VOLUME_BOOST_MILLIBELS (0-12dB). */
    fun setVolumeBoostMillibels(millibels: Int) {
        val clamped = millibels.coerceIn(0, MAX_VOLUME_BOOST_MILLIBELS)
        prefs.edit { putInt(KEY_VOLUME_BOOST_MILLIBELS, clamped) }
        _volumeBoostMillibels.value = clamped
    }
}

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

        // S016 -- cupo 80/10/10 de Radio (H08). Solo se guardan
        // exploración y disco; el de diccionario se deriva siempre
        // como 100 - exploración - disco (ver getters más abajo), así
        // que nunca puede desincronizarse de los otros dos.
        const val KEY_RADIO_EXPLORE_PERCENT = "radio_explore_percent"
        const val KEY_RADIO_DISCO_PERCENT = "radio_disco_percent"
        const val DEFAULT_RADIO_EXPLORE_PERCENT = 10
        const val DEFAULT_RADIO_DISCO_PERCENT = 10

        // S026 -- umbral de GenreMatchQuality (intersección/unión de
        // géneros específicos, ver esa clase). Petición explícita de
        // Miguel Ángel: "un 30% o un 40%... configurable en ajustes,
        // con escalones de diez". 40% por defecto -- ver
        // GenreMatchQuality.kt para el porqué de ese número, verificado
        // contra datos reales antes de fijarlo.
        const val KEY_RADIO_GENRE_MATCH_THRESHOLD_PERCENT = "radio_genre_match_threshold_percent"
        const val DEFAULT_RADIO_GENRE_MATCH_THRESHOLD_PERCENT = 40
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

    private val _radioExplorePercent = MutableStateFlow(
        prefs.getInt(KEY_RADIO_EXPLORE_PERCENT, DEFAULT_RADIO_EXPLORE_PERCENT)
    )
    /** S016 -- % del cupo de Radio que va a "exploración" (MusicBrainz, artistas no necesariamente conocidos). */
    val radioExplorePercent: StateFlow<Int> = _radioExplorePercent.asStateFlow()

    private val _radioDiscoPercent = MutableStateFlow(
        prefs.getInt(KEY_RADIO_DISCO_PERCENT, DEFAULT_RADIO_DISCO_PERCENT)
    )
    /** S016 -- % del cupo de Radio que va a "disco" (biblioteca local ya descargada). */
    val radioDiscoPercent: StateFlow<Int> = _radioDiscoPercent.asStateFlow()

    /**
     * S016 -- % del cupo de Radio que va a "diccionario" (éxitos
     * conocidos por década/origen, el 80% original). Nunca se guarda
     * en `SharedPreferences` ni se expone como `StateFlow` propio:
     * siempre es el resto tras exploración y disco, así que basta con
     * derivarlo bajo demanda para que la suma de los tres dé 100
     * siempre, sin excepción.
     */
    val radioDictPercent: Int
        get() = 100 - _radioExplorePercent.value - _radioDiscoPercent.value

    /**
     * S016 -- fija el % de exploración. Se recorta a 0..100 y, si
     * junto al disco actual superara 100, se recorta más para dejar
     * sitio: el diccionario nunca puede quedar en negativo.
     */
    fun setRadioExplorePercent(percent: Int) {
        val clamped = percent.coerceIn(0, 100 - _radioDiscoPercent.value)
        prefs.edit { putInt(KEY_RADIO_EXPLORE_PERCENT, clamped) }
        _radioExplorePercent.value = clamped
    }

    /**
     * S016 -- fija el % de disco. Mismo recorte que
     * `setRadioExplorePercent()`, en el sentido contrario.
     */
    fun setRadioDiscoPercent(percent: Int) {
        val clamped = percent.coerceIn(0, 100 - _radioExplorePercent.value)
        prefs.edit { putInt(KEY_RADIO_DISCO_PERCENT, clamped) }
        _radioDiscoPercent.value = clamped
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
}

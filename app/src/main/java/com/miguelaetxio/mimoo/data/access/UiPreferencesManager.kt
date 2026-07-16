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
 * Preferencias de interfaz de MiMoo (S011) -- de momento, solo el
 * interruptor de borde del efecto de cristal ("añade un toggle en
 * ajustes para cambiar de borde a sin borde", petición explícita de
 * Miguel Ángel). Mismo patrón de SharedPreferences que
 * `AccessPinManager`, con un `StateFlow` encima para que el cambio se
 * refleje en vivo en toda la app sin tener que reiniciarla --
 * `MainActivity` lo provee como `CompositionLocal`
 * (`LocalGlassBorderEnabled`, ver `ui/theme/Glass.kt`) para que
 * cualquier pantalla lo lea sin tener que pasarlo a mano por cada
 * ViewModel.
 * ---
 * MiMoo UI preferences (S011) -- for now, only the glass-effect
 * border toggle. Same SharedPreferences pattern as `AccessPinManager`,
 * with a `StateFlow` on top so the change reflects live across the
 * whole app without restarting it -- `MainActivity` provides it as a
 * `CompositionLocal` (`LocalGlassBorderEnabled`, see
 * `ui/theme/Glass.kt`) so any screen can read it without threading it
 * through every ViewModel by hand.
 */
@Singleton
class UiPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val PREFS_NAME = "mimoo_ui_prefs"
        const val KEY_GLASS_BORDER_ENABLED = "glass_border_enabled"
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
}

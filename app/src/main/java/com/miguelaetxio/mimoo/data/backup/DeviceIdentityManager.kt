package com.miguelaetxio.mimoo.data.backup

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * H07 PARTE 1 (redefinición S008, regla de negocio de Miguel Ángel):
 * cada dispositivo necesita un identificador propio y estable para
 * poder distinguir "esta es mi propia copia, si no coincide es que mi
 * disco se desincronizó" de "esta copia la hizo otro dispositivo,
 * toca preguntar". Un UUID generado una sola vez y persistido en
 * SharedPreferences -- se pierde y se regenera en una reinstalación
 * (igual que el resto del estado local), lo cual es correcto: un
 * dispositivo reinstalado es, a todos los efectos de esta regla, un
 * dispositivo "distinto" la primera vez que vuelve a sincronizar.
 * ---
 * H07 PART 1 (S008 redefinition, Miguel Ángel's business rule): each
 * device needs its own stable identifier to be able to tell "this is
 * my own copy, if it doesn't match my disk got out of sync" apart
 * from "this copy was made by another device, time to ask". A UUID
 * generated once and persisted in SharedPreferences -- lost and
 * regenerated on a reinstall (same as the rest of local state), which
 * is correct: a reinstalled device is, for the purposes of this rule,
 * a "different" device the first time it syncs again.
 */
@Singleton
class DeviceIdentityManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS_NAME = "mimoo_device_identity_prefs"
        private const val KEY_DEVICE_ID = "device_id"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** UUID estable de este dispositivo -- se genera y persiste la primera vez que se pide. */
    val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also { newId ->
            prefs.edit { putString(KEY_DEVICE_ID, newId) }
        }
    }

    /**
     * Nombre legible para mostrar en avisos ("sincronizado desde
     * SM-A566B") -- no es el identificador real usado para comparar,
     * solo para que los mensajes de la UI digan algo con sentido en
     * vez de un UUID.
     * ---
     * Human-readable name for display in notices ("synced from
     * SM-A566B") -- not the real identifier used for comparison, just
     * so UI messages say something meaningful instead of a UUID.
     */
    val deviceLabel: String
        get() = Build.MODEL ?: "dispositivo desconocido"
}

package com.miguelaetxio.mimoo.data.access

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestiona el bloqueo de acceso de la app por PIN (H07 PARTE 2,
 * PASO 2.7). El PIN correcto es fijo, embebido en el código -- no
 * configurable desde la UI, decisión explícita de Miguel Ángel.
 * Persiste solo un flag booleano "desbloqueado" en SharedPreferences,
 * igual patrón que StorageManager -- se borra igual que Room al
 * desinstalar, así que una reinstalación vuelve a pedir el PIN.
 * ---
 * Manages the app's PIN access lock (H07 PART 2, STEP 2.7). The
 * correct PIN is fixed, embedded in the code -- not configurable
 * from the UI, Miguel Ángel's explicit decision. Persists only a
 * boolean "unlocked" flag in SharedPreferences, same pattern as
 * StorageManager -- it's wiped the same as Room on uninstall, so a
 * reinstall asks for the PIN again.
 */
@Singleton
class AccessPinManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS_NAME = "mimoo_access_prefs"
        private const val KEY_UNLOCKED = "pin_unlocked"

        /**
         * PIN fijo de acceso, decisión explícita de Miguel Ángel
         * (S008) -- nunca configurable desde la UI ni almacenado en
         * ningún archivo de documentación en claro.
         * ---
         * Fixed access PIN, Miguel Ángel's explicit decision (S008)
         * -- never configurable from the UI nor stored in any
         * documentation file in the clear.
         */
        private const val CORRECT_PIN = "0485"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** true si ya se introdujo el PIN correcto en este dispositivo (desde la última instalación). */
    fun isUnlocked(): Boolean = prefs.getBoolean(KEY_UNLOCKED, false)

    /**
     * Compara `input` contra el PIN correcto en tiempo constante
     * (comparando los hashes SHA-256, no las cadenas directamente) --
     * el riesgo real de un ataque de canal lateral aquí es bajo, pero
     * es el mismo criterio ya usado para tokens en el proyecto
     * (BackupRepository/mimoo_updates). Si coincide, persiste el
     * desbloqueo para no volver a pedirlo hasta la próxima
     * desinstalación.
     * ---
     * Compares `input` against the correct PIN in constant time
     * (comparing SHA-256 hashes, not the strings directly) -- the
     * real side-channel risk here is low, but it's the same criterion
     * already used for tokens elsewhere in the project
     * (BackupRepository/mimoo_updates). If it matches, persists the
     * unlock so it isn't asked again until the next uninstall.
     */
    fun submitPin(input: String): Boolean {
        val inputHash = sha256(input)
        val correctHash = sha256(CORRECT_PIN)
        val matches = MessageDigest.isEqual(inputHash, correctHash)
        if (matches) {
            prefs.edit { putBoolean(KEY_UNLOCKED, true) }
        }
        return matches
    }

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
}

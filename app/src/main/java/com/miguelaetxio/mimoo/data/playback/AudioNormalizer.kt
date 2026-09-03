package com.miguelaetxio.mimoo.data.playback

import android.media.audiofx.DynamicsProcessing
import android.os.Build

/**
 * Nivelación de audio en tiempo real (Opción A cerrada con Miguel
 * Ángel, 2026-08-23): "que no haya altos y bajos de unas canciones a
 * otras, que suenen todas con el mismo volumen". NO es normalización
 * real por pista (eso exigiría analizar cada tema y guardar su
 * ganancia -- ver la Opción B descartada en la sesión de diseño); es
 * un compresor/limitador de banda ancha (`DynamicsProcessing`)
 * enganchado a la sesión de audio de ExoPlayer, que suaviza
 * automáticamente picos y valles de volumen sobre la marcha, sin
 * tocar el catálogo ni analizar nada por adelantado.
 *
 * Se construye con el constructor de TRES argumentos
 * (`DynamicsProcessing(priority, audioSession, Config)`) pasando
 * `Config = null` explícitamente -- el overload de dos argumentos
 * documentado en el fuente AOSP (`this(priority, audioSession, null)`)
 * no resolvió al compilar contra el stub SDK público de `compileSdk
 * 36` (build real, 2026-08-23: `Unresolved reference` en GitHub
 * Actions), así que se usa la forma explícita de tres. Un `Config`
 * nulo hace que el sistema elija una configuración de bandas/
 * compresor/limitador por defecto, sensata para el caso general. Se
 * evita a propósito construir un `Config` a mano (bandas del MBC,
 * umbrales, ratios, ataque/release) -- esa API es notoriamente
 * delicada (`IllegalArgumentException` en tiempo de ejecución si los
 * parámetros no encajan exactamente con la forma del `Config.Builder`)
 * y no hay manera de verificarla desde este entorno de trabajo sin
 * dispositivo real; la configuración por defecto ya resuelve el
 * problema real reportado (altos y bajos entre canciones) sin ese
 * riesgo.
 *
 * `DynamicsProcessing` exige API 28 -- el proyecto tiene `minSdk 26`
 * (ver `app/build.gradle.kts`), así que por debajo de API 28 esta
 * clase es un no-op seguro (nunca lanza, simplemente no nivela).
 *
 * S048 -- el refuerzo de volumen (`LoudnessEnhancer`, +0-12dB
 * configurable en Ajustes) se ha eliminado por completo, decisión
 * explícita de Miguel Ángel: el bug real reportado (el refuerzo no
 * aplicaba a tope hasta tocar el control) persistía en dispositivo
 * real incluso tras el intento de arreglo de esta misma sesión
 * (invertir `setEnabled`/`setTargetGain`). Sin ganas de seguir
 * diagnosticando un efecto de audio notoriamente inconsistente entre
 * fabricantes, se retira entero -- `DynamicsProcessing` (nivelación de
 * altos y bajos entre canciones) no se toca, es una función distinta
 * y sin ningún problema reportado.
 */
class AudioNormalizer {

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var attachedSessionId: Int = 0

    /**
     * Engancha (o reengancha) el efecto a la sesión de audio actual del
     * player -- idempotente: si ya está enganchado a esta misma sesión,
     * no hace nada. Se llama desde
     * `AnalyticsListener.onAudioSessionIdChanged()` de ExoPlayer (ver
     * PlayerManager), que Media3 1.10.1 dispara con el id real en
     * cuanto está disponible (ya no lo está de forma inmediata al crear
     * el player, ver release notes de Media3).
     */
    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (attachedSessionId == audioSessionId && dynamicsProcessing != null) {
            return
        }
        release()
        attachedSessionId = audioSessionId
        attachDynamicsProcessing(audioSessionId)
    }

    private fun attachDynamicsProcessing(audioSessionId: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            val effect = DynamicsProcessing(0, audioSessionId, null)
            effect.setEnabled(true)
            dynamicsProcessing = effect
        } catch (e: Exception) {
            // Dispositivo que declara API 28+ pero sin soporte real del
            // efecto, o sesión no disponible todavía -- se descarta en
            // silencio, mismo criterio tolerante que el resto de la app
            // con efectos de audio no garantizados por todos los
            // fabricantes.
            dynamicsProcessing = null
        }
    }

    /** Libera el efecto actual, si hay alguno -- se llama al reenganchar a una sesión distinta y en PlayerManager.release(). */
    fun release() {
        dynamicsProcessing?.let {
            try {
                it.setEnabled(false)
                it.release()
            } catch (e: Exception) {
                // Ya liberado o sesión inválida -- no hay nada más que hacer.
            }
        }
        dynamicsProcessing = null
    }
}

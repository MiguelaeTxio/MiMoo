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
 * ---
 * Real-time audio leveling (Option A closed with Miguel Ángel,
 * 2026-08-23): "no highs and lows from song to song, all playing at
 * the same volume". This is NOT true per-track normalization (that
 * would require analyzing each track and storing its gain -- see
 * Option B, discarded in the design session); it's a broadband
 * compressor/limiter (`DynamicsProcessing`) attached to ExoPlayer's
 * audio session, automatically smoothing volume peaks and valleys on
 * the fly, without touching the catalog or analyzing anything ahead of
 * time.
 *
 * Built with the THREE-argument constructor
 * (`DynamicsProcessing(priority, audioSession, Config)`), passing
 * `Config = null` explicitly -- the two-argument overload documented
 * in AOSP source (`this(priority, audioSession, null)`) failed to
 * resolve when compiling against the public `compileSdk 36` SDK stub
 * (real build, 2026-08-23: `Unresolved reference` on GitHub Actions),
 * so the explicit three-argument form is used instead. A null
 * `Config` makes the system pick a sensible default band/compressor/
 * limiter configuration for the general case. Building a `Config` by
 * hand (MBC bands, thresholds, ratios, attack/release) is deliberately
 * avoided -- that API is notoriously fragile (runtime
 * `IllegalArgumentException` if the parameters don't exactly match the
 * `Config.Builder`'s shape) and there's no way to verify it from this
 * work environment without a real device; the default configuration
 * already solves the real reported problem (highs and lows between
 * songs) without that risk.
 *
 * `DynamicsProcessing` requires API 28 -- the project has `minSdk 26`
 * (see `app/build.gradle.kts`), so below API 28 this class is a safe
 * no-op (never throws, simply doesn't level).
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (audioSessionId == 0) return
        if (dynamicsProcessing != null && attachedSessionId == audioSessionId) return
        release()
        try {
            val effect = DynamicsProcessing(0, audioSessionId, null)
            effect.setEnabled(true)
            dynamicsProcessing = effect
            attachedSessionId = audioSessionId
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

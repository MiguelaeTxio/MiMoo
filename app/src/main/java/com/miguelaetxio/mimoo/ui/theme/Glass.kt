package com.miguelaetxio.mimoo.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Chapita de cristal traslúcido" (S011) -- petición explícita de
 * Miguel Ángel, con una captura de un teclado como referencia: cada
 * tecla como una placa de plástico/cristal semitransparente sobre el
 * fondo. Extendido a toda la app tras el visto bueno de Miguel Ángel
 * ("me encanta... extiende la piel a toda la aplicación").
 *
 * Aproximación deliberada sin desenfoque real de fondo
 * (`RenderEffect`/blur real exigiría API 31+ y sería mucho más caro
 * de calcular en listas largas de Biblioteca) -- un degradado sutil de
 * arriba a abajo (más claro arriba, simulando un reflejo de luz) ya
 * lee como "cristal"/"placa metálica" de forma convincente sobre el
 * fondo sólido de cada piel (`MiMooTheme.kt`), sin coste de
 * rendimiento ni restricción de versión de Android.
 *
 * S052 -- `GlassTokens` (objeto único y fijo) pasa a `GlassTokenSet`
 * (data class), con una instancia por piel (`MsxGlassTokens`,
 * `AluminioGlassTokens`) seleccionada vía `LocalGlassTokens`, mismo
 * mecanismo de CompositionLocal que ya usa `LocalGlassBorderEnabled`.
 * Diseñado para escalar: una piel nueva es una instancia más de
 * `GlassTokenSet` y un caso más en `glassTokensFor()`, sin tocar
 * `glassChip()` ni ningún composable que ya la use.
 * ---
 * S052 -- `GlassTokens` (single fixed object) becomes `GlassTokenSet`
 * (data class), with one instance per skin (`MsxGlassTokens`,
 * `AluminioGlassTokens`) selected via `LocalGlassTokens`, the same
 * CompositionLocal mechanism `LocalGlassBorderEnabled` already uses.
 * Designed to scale: a new skin is one more `GlassTokenSet` instance
 * and one more case in `glassTokensFor()`, without touching
 * `glassChip()` or any composable that already uses it.
 */
data class GlassTokenSet(
    val fillTop: Color,
    val fillBottom: Color,

    /**
     * S011 -- petición explícita de Miguel Ángel: "en las chapitas
     * tenemos que distinguir entre chapitas clicables y decorativas,
     * para que el usuario sepa siempre cuáles son clicables a simple
     * vista". Las decorativas (títulos de sección informativos,
     * etiquetas que no llevan ninguna acción) usan un cristal mucho
     * más tenue -- casi imperceptible -- y NUNCA borde, sea cual sea
     * el interruptor de Ajustes. Es una señal por contraste de
     * relleno, no por el borde, para que funcione igual de bien con
     * el interruptor activado o desactivado.
     */
    val decorativeFillTop: Color,
    val decorativeFillBottom: Color,

    /**
     * H13 -- chapita ENCENDIDA, para controles con estado ON/OFF
     * (aleatorio y cíclico del reproductor). Nace de un fallo real:
     * hasta ahora el estado activo se señalaba con
     * `colorScheme.primary` como tint del icono, pero en la piel MSX
     * `primary` ES BLANCO -- exactamente el mismo blanco que
     * `LocalContentColor` del estado inactivo. El "cambio de color" no
     * cambiaba absolutamente nada en pantalla.
     *
     * La placa encendida es casi opaca, con el icono en
     * `colorScheme.onPrimary` -- una tecla iluminada, legible de un
     * vistazo sin depender del color del trazo. Mismo principio que un
     * `FilterChip` seleccionado de Material 3. Cada piel elige su
     * propio contraste: MSX usa una placa casi blanca (icono azul
     * MSX), Aluminio usa una placa casi negra (icono claro) -- lo que
     * más contraste dé sobre el fondo de esa piel en concreto.
     */
    val activeFillTop: Color,
    val activeFillBottom: Color,

    val border: Color,
    val borderWidth: Dp,
    val cornerRadius: Dp,
)

val MsxGlassTokens = GlassTokenSet(
    fillTop = Color.White.copy(alpha = 0.18f),
    fillBottom = Color.White.copy(alpha = 0.06f),
    decorativeFillTop = Color.White.copy(alpha = 0.08f),
    decorativeFillBottom = Color.White.copy(alpha = 0.02f),
    activeFillTop = Color.White.copy(alpha = 0.88f),
    activeFillBottom = Color.White.copy(alpha = 0.72f),
    border = Color.White.copy(alpha = 0.32f),
    borderWidth = 1.dp,
    cornerRadius = 16.dp,
)

/**
 * S052 -- "aluminio cepillado" (petición explícita de Miguel Ángel).
 * El fondo de esta piel es CLARO (`AluminioColorScheme.background`),
 * al revés que el azul oscuro de MSX -- por eso el degradado de la
 * chapita no puede ser el mismo blanco translúcido de MSX (sobre un
 * fondo ya claro, apenas se notaría). Aquí la chapita es una placa
 * blanca casi opaca con un degradado sutil (más clara arriba, como un
 * reflejo de luz sobre metal cepillado), y la placa ENCENDIDA se
 * invierte a oscura -- mismo criterio de "máximo contraste sobre el
 * fondo de la piel" que MSX, aplicado a la inversa por tener un fondo
 * claro en vez de oscuro.
 */
val AluminioGlassTokens = GlassTokenSet(
    fillTop = Color.White.copy(alpha = 0.85f),
    fillBottom = Color.White.copy(alpha = 0.45f),
    decorativeFillTop = Color.White.copy(alpha = 0.35f),
    decorativeFillBottom = Color.White.copy(alpha = 0.12f),
    activeFillTop = Color(0xFF2A2A2D).copy(alpha = 0.92f),
    activeFillBottom = Color(0xFF2A2A2D).copy(alpha = 0.75f),
    border = Color(0xFF2A2A2D).copy(alpha = 0.30f),
    borderWidth = 1.dp,
    cornerRadius = 16.dp,
)

fun glassTokensFor(skin: AppSkin): GlassTokenSet = when (skin) {
    AppSkin.MSX -> MsxGlassTokens
    AppSkin.ALUMINIO -> AluminioGlassTokens
}

/**
 * Qué piel está activa ahora mismo -- `MainActivity` lo provee a
 * partir de `UiPreferencesManager.appSkin` (persistido,
 * SharedPreferences) envolviendo todo el árbol de composición, así
 * que el cambio de piel se refleja en vivo en todas las pantallas sin
 * reiniciar la app. Valor por defecto MSX solo como red de seguridad
 * si algún Composable de test/preview no pasa por ese proveedor -- en
 * la app real siempre está provisto desde `MainActivity`.
 */
val LocalGlassTokens = compositionLocalOf { MsxGlassTokens }

/**
 * S011 -- interruptor de borde ("añade un toggle en ajustes para
 * cambiar de borde a sin borde"), leído por defecto por todas las
 * chapitas de cristal de la app. `MainActivity` lo provee a partir de
 * `UiPreferencesManager.glassBorderEnabled` (persistido,
 * SharedPreferences) envolviendo todo el árbol de composición, así
 * que el cambio se refleja en vivo en todas las pantallas sin
 * reiniciar la app y sin tener que pasar el valor a mano por cada
 * ViewModel. Valor por defecto `false` solo como red de seguridad si
 * algún Composable de test/preview no pasa por ese proveedor -- en la
 * app real siempre está provisto desde `MainActivity`.
 */
val LocalGlassBorderEnabled = compositionLocalOf { false }

/**
 * Aplica el efecto de cristal a cualquier composable -- shape por
 * defecto la chapita estándar de la piel activa
 * (`LocalGlassTokens.current.cornerRadius`), pero cada superficie
 * puede pedir la suya (p.ej. una chapita circular para el icono de
 * hamburguesa).
 *
 * S011 -- sin sombra/elevación a propósito: petición explícita de
 * Miguel Ángel tras ver el primer pase ("sin darle volumen") -- el
 * cristal queda plano en vez de leer como una tarjeta en relieve.
 *
 * `showBorder` por defecto lee `LocalGlassBorderEnabled` -- el
 * interruptor de Ajustes decide para toda la app; una superficie
 * concreta puede seguir forzando `true`/`false` explícitamente si
 * hiciera falta alguna vez, aunque hoy ninguna lo hace.
 *
 * `interactive = false` para chapitas puramente decorativas (títulos
 * de sección, etiquetas sin ninguna acción asociada) -- cristal mucho
 * más tenue y sin borde nunca, para que el usuario distinga a simple
 * vista qué chapitas se pueden tocar y cuáles no.
 *
 * `active = true` (H13) para controles con estado ON/OFF que están
 * encendidos -- placa casi opaca, ver `GlassTokenSet.activeFillTop`.
 * Manda sobre `interactive`: una chapita encendida es siempre
 * clicable por definición.
 */
@Composable
fun Modifier.glassChip(
    shape: Shape = RoundedCornerShape(LocalGlassTokens.current.cornerRadius),
    showBorder: Boolean = LocalGlassBorderEnabled.current,
    interactive: Boolean = true,
    active: Boolean = false,
): Modifier {
    val tokens = LocalGlassTokens.current
    return this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                when {
                    active -> listOf(tokens.activeFillTop, tokens.activeFillBottom)
                    interactive -> listOf(tokens.fillTop, tokens.fillBottom)
                    else -> listOf(tokens.decorativeFillTop, tokens.decorativeFillBottom)
                }
            )
        )
        .let {
            if (showBorder && interactive) {
                it.border(width = tokens.borderWidth, color = tokens.border, shape = shape)
            } else {
                it
            }
        }
}

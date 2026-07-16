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
 * de calcular en listas largas de Biblioteca) -- un degradado blanco
 * sutil de arriba a abajo (más claro arriba, simulando un reflejo de
 * luz) ya lee como "cristal" de forma convincente sobre el azul MSX
 * sólido de fondo (`MiMooTheme.kt`), sin coste de rendimiento ni
 * restricción de versión de Android.
 *
 * Un único punto de ajuste (`GlassTokens`) para que todas las
 * chapitas de la app compartan exactamente el mismo aspecto -- si
 * Miguel Ángel pide afinar la intensidad del cristal, se cambia aquí
 * una vez, no en cada pantalla.
 */
object GlassTokens {
    val fillTop: Color = Color.White.copy(alpha = 0.18f)
    val fillBottom: Color = Color.White.copy(alpha = 0.06f)
    val border: Color = Color.White.copy(alpha = 0.32f)
    val borderWidth: Dp = 1.dp
    val cornerRadius: Dp = 16.dp
}

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
 * defecto la chapita estándar de la app (`GlassTokens.cornerRadius`),
 * pero cada superficie puede pedir la suya (p.ej. una chapita circular
 * para el icono de hamburguesa).
 *
 * S011 -- sin sombra/elevación a propósito: petición explícita de
 * Miguel Ángel tras ver el primer pase ("sin darle volumen") -- el
 * cristal queda plano en vez de leer como una tarjeta en relieve.
 *
 * `showBorder` por defecto lee `LocalGlassBorderEnabled` -- el
 * interruptor de Ajustes decide para toda la app; una superficie
 * concreta puede seguir forzando `true`/`false` explícitamente si
 * hiciera falta alguna vez, aunque hoy ninguna lo hace.
 */
@Composable
fun Modifier.glassChip(
    shape: Shape = RoundedCornerShape(GlassTokens.cornerRadius),
    showBorder: Boolean = LocalGlassBorderEnabled.current,
): Modifier =
    this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(listOf(GlassTokens.fillTop, GlassTokens.fillBottom))
        )
        .let {
            if (showBorder) {
                it.border(width = GlassTokens.borderWidth, color = GlassTokens.border, shape = shape)
            } else {
                it
            }
        }

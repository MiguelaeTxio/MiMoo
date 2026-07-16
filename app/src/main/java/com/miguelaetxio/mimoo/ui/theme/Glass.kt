package com.miguelaetxio.mimoo.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * fondo. Aplicado a títulos, menú de hamburguesa, entradas de la
 * sidebar, artistas y álbumes.
 *
 * Aproximación deliberada sin desenfoque real de fondo
 * (`RenderEffect`/blur real exigiría API 31+ y sería mucho más caro
 * de calcular en listas largas de Biblioteca) -- un degradado blanco
 * sutil de arriba a abajo (más claro arriba, simulando un reflejo de
 * luz) más un borde fino y una sombra ligera ya leen como "cristal"
 * de forma convincente sobre el azul MSX sólido de fondo
 * (`MiMooTheme.kt`), sin coste de rendimiento ni restricción de
 * versión de Android.
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
 * Aplica el efecto de cristal a cualquier composable -- shape por
 * defecto la chapita estándar de la app (`GlassTokens.cornerRadius`),
 * pero cada superficie puede pedir la suya (p.ej. una chapita circular
 * para el icono de hamburguesa).
 *
 * S011 -- sin sombra/elevación a propósito: petición explícita de
 * Miguel Ángel tras ver el primer pase ("sin darle volumen") -- el
 * cristal queda plano en vez de leer como una tarjeta en relieve.
 */
fun Modifier.glassChip(shape: Shape = RoundedCornerShape(GlassTokens.cornerRadius)): Modifier =
    this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(listOf(GlassTokens.fillTop, GlassTokens.fillBottom))
        )
        .border(width = GlassTokens.borderWidth, color = GlassTokens.border, shape = shape)

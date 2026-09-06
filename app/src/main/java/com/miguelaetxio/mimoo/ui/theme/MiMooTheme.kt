package com.miguelaetxio.mimoo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * S052 -- petición explícita de Miguel Ángel: poder cambiar la "piel"
 * de la app desde Ajustes > Apariencia. Diseñado para escalar a más
 * pieles en el futuro sin tocar arquitectura -- añadir una nueva es:
 * (1) un valor más aquí, (2) un `ColorScheme` más en
 * `colorSchemeFor()`, (3) un `GlassTokenSet` más en `glassTokensFor()`
 * (ver Glass.kt), (4) una entrada más en el selector de
 * SettingsScreen. Nada más cambia -- MainActivity ya lee ambas
 * funciones a partir de este enum, sin ningún `when` adicional que
 * mantener en más de esos cuatro sitios.
 * ---
 * S052 -- explicit request from Miguel Ángel: being able to change the
 * app's "skin" from Settings > Appearance. Designed to scale to more
 * skins in the future without touching architecture -- adding a new
 * one is: (1) one more value here, (2) one more `ColorScheme` in
 * `colorSchemeFor()`, (3) one more `GlassTokenSet` in
 * `glassTokensFor()` (see Glass.kt), (4) one more entry in
 * SettingsScreen's picker. Nothing else changes -- MainActivity
 * already reads both functions from this enum, with no additional
 * `when` to maintain anywhere else.
 */
enum class AppSkin {
    /** Piel por defecto -- fondo azul tipo pantalla de arranque del MSX2, letra blanca. Ver MsxColorScheme. */
    MSX,

    /** Acabado en aluminio cepillado -- fondo gris claro, letra en gris oscuro. Ver AluminioColorScheme. */
    ALUMINIO,
}

fun colorSchemeFor(skin: AppSkin): ColorScheme = when (skin) {
    AppSkin.MSX -> MsxColorScheme
    AppSkin.ALUMINIO -> AluminioColorScheme
}

/**
 * Esquema de color de la piel MSX (por defecto) -- petición explícita
 * de Miguel Ángel (2026-07-05): fondo azul tipo pantalla de arranque
 * del MSX2 (mismo azul que el icono de la app), letra en blanco, y el
 * rojo de controles/errores sustituido por amarillo, porque los rojos
 * no resaltan bien sobre azul en pantalla.
 *
 * Al sustituir aquí `error` por amarillo, TODOS los iconos/textos que
 * ya usaban `MaterialTheme.colorScheme.error` (borrar pista, borrar
 * álbum, borrar artista, etc.) se vuelven amarillos automáticamente,
 * sin tocar cada composable uno a uno.
 * ---
 * MSX skin color scheme (default) -- explicit request from Miguel
 * Ángel (2026-07-05): MSX2 boot-screen-style blue background (same
 * blue as the app icon), white text, and the red used for
 * controls/errors replaced with yellow, because reds don't stand out
 * well against blue on screen.
 *
 * By overriding `error` to yellow here, EVERY icon/text that already
 * used `MaterialTheme.colorScheme.error` (delete track, delete album,
 * delete artist, etc.) automatically turns yellow, without touching
 * each composable one by one.
 */
private val MsxBlue = Color(0xFF2020B4)
private val MsxBlueLight = Color(0xFF3F3FD0)
private val MsxBlueLighter = Color(0xFF5C5CE0)
private val MsxYellow = Color(0xFFFFD400)
private val MsxYellowDark = Color(0xFF6B5C00)

val MsxColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = MsxBlue,
    primaryContainer = MsxBlueLighter,
    onPrimaryContainer = Color.White,

    secondary = Color.White,
    onSecondary = MsxBlue,
    secondaryContainer = MsxBlueLight,
    onSecondaryContainer = Color.White,

    tertiary = MsxYellow,
    onTertiary = MsxYellowDark,

    background = MsxBlue,
    onBackground = Color.White,

    surface = MsxBlue,
    onSurface = Color.White,
    surfaceVariant = MsxBlueLight,
    onSurfaceVariant = Color(0xFFD8D8FF),

    // Rojo -> amarillo, petición explícita de Miguel Ángel: borrar
    // pista/álbum/artista, mensajes de error, etc. -- todo lo que use
    // colorScheme.error pasa a amarillo automáticamente.
    // ---
    // Red -> yellow, explicit request from Miguel Ángel: delete
    // track/album/artist, error messages, etc. -- everything using
    // colorScheme.error automatically becomes yellow.
    error = MsxYellow,
    onError = MsxYellowDark,
    errorContainer = MsxYellowDark,
    onErrorContainer = MsxYellow,

    outline = Color(0xFFAFAFEF),
)

/**
 * S052 -- esquema de color de la piel Aluminio, petición explícita de
 * Miguel Ángel ("acabado en aluminio"): fondo gris claro tipo aluminio
 * cepillado, letra en gris oscuro/casi negro (el blanco no se lee
 * sobre un fondo claro). Es un `lightColorScheme`, no un
 * `darkColorScheme` como el de MSX -- es la diferencia real entre las
 * dos pieles, no solo los colores.
 *
 * `error` vuelve a un rojo normal (a diferencia de MSX, donde el rojo
 * se sustituyó por amarillo porque no resaltaba sobre el azul) -- aquí
 * sí resalta bien sobre gris claro, así que no hace falta el mismo
 * apaño.
 * ---
 * S052 -- Aluminio skin color scheme, explicit request from Miguel
 * Ángel ("brushed aluminum finish"): light gray brushed-aluminum-style
 * background, dark gray/near-black text (white doesn't read on a
 * light background). It's a `lightColorScheme`, not a
 * `darkColorScheme` like MSX's -- that's the real difference between
 * the two skins, not just the colors.
 *
 * `error` goes back to a normal red (unlike MSX, where red was
 * replaced with yellow because it didn't stand out against blue) --
 * here it stands out fine against light gray, so the same workaround
 * isn't needed.
 */
private val AluminioLight = Color(0xFFEDEDEF)
private val AluminioBase = Color(0xFFC9CACE)
private val AluminioDark = Color(0xFFA6A7AC)
private val AluminioText = Color(0xFF2A2A2D)
private val AluminioRed = Color(0xFFB3261E)

val AluminioColorScheme = lightColorScheme(
    primary = AluminioText,
    onPrimary = AluminioLight,
    primaryContainer = AluminioDark,
    onPrimaryContainer = AluminioText,

    secondary = AluminioText,
    onSecondary = AluminioLight,
    secondaryContainer = AluminioBase,
    onSecondaryContainer = AluminioText,

    tertiary = AluminioRed,
    onTertiary = Color.White,

    background = AluminioBase,
    onBackground = AluminioText,

    surface = AluminioBase,
    onSurface = AluminioText,
    surfaceVariant = AluminioLight,
    onSurfaceVariant = AluminioText.copy(alpha = 0.72f),

    error = AluminioRed,
    onError = Color.White,
    errorContainer = AluminioRed,
    onErrorContainer = Color.White,

    outline = AluminioDark,
)

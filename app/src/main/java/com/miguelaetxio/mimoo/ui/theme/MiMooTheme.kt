package com.miguelaetxio.mimoo.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Esquema de color de la app -- petición explícita de Miguel Ángel
 * (2026-07-05): fondo azul tipo pantalla de arranque del MSX2 (mismo
 * azul que el icono de la app), letra en blanco, y el rojo de
 * controles/errores sustituido por amarillo, porque los rojos no
 * resaltan bien sobre azul en pantalla.
 *
 * Al sustituir aquí `error` por amarillo, TODOS los iconos/textos que
 * ya usaban `MaterialTheme.colorScheme.error` (borrar pista, borrar
 * álbum, borrar artista, etc.) se vuelven amarillos automáticamente,
 * sin tocar cada composable uno a uno.
 * ---
 * App color scheme -- explicit request from Miguel Ángel (2026-07-05):
 * MSX2 boot-screen-style blue background (same blue as the app icon),
 * white text, and the red used for controls/errors replaced with
 * yellow, because reds don't stand out well against blue on screen.
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

val MiMooColorScheme = darkColorScheme(
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

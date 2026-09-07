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

    /** Acabado en aluminio cepillado -- textura real (foto), letra en gris oscuro. Ver AluminioColorScheme. */
    ALUMINIO,

    /**
     * S060 -- petición explícita de Miguel Ángel, con textura real
     * (foto de granito moteado) tras señalar que las pieles anteriores
     * usaban colores calculados en vez de texturas de verdad ("¿cómo
     * podemos enfocarlo para hacerlo así?"). Fondo gris claro moteado,
     * letra en gris muy oscuro/negro. Ver GranitoColorScheme.
     */
    GRANITO,

    /**
     * S060 -- textura real (foto de veta de madera), mismo motivo que
     * GRANITO. Fondo marrón cálido, letra en crema/blanco roto (el
     * negro no se lee bien sobre un marrón medio-oscuro). Ver
     * MaderaColorScheme.
     */
    MADERA,
}

fun colorSchemeFor(skin: AppSkin): ColorScheme = when (skin) {
    AppSkin.MSX -> MsxColorScheme
    AppSkin.ALUMINIO -> AluminioColorScheme
    AppSkin.GRANITO -> GranitoColorScheme
    AppSkin.MADERA -> MaderaColorScheme
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
 * S054 -- `background` pasa a `Color.Transparent` tras recibir una
 * textura real de aluminio cepillado (foto, no degradado calculado).
 * Cada pantalla de la app pinta su propio fondo por separado (18
 * `Scaffold()` distintos, cada uno con su `containerColor` por
 * defecto en `colorScheme.background`) -- tocar los 18 uno a uno para
 * añadir la imagen habría sido un cambio enorme y frágil. En vez de
 * eso, `MainActivity` pinta la textura UNA sola vez, en la raíz visual
 * de toda la app, detrás de `ModalNavigationDrawer`; con
 * `background = Transparent`, los 18 `Scaffold()` dejan de tapar esa
 * imagen y se ve a través de todos ellos sin tocar ni un archivo de
 * pantalla. `colorScheme.surface` (tarjetas, diálogos, menús) se
 * queda opaco, sin tocar -- solo el fondo raíz de cada pantalla es
 * transparente.
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
 * S054 -- `background` becomes `Color.Transparent` after receiving a
 * real brushed-aluminum texture (a photo, not a computed gradient).
 * Every screen in the app paints its own background separately (18
 * different `Scaffold()` calls, each defaulting `containerColor` to
 * `colorScheme.background`) -- touching all 18 one by one to add the
 * image would have been a huge, fragile change. Instead, MainActivity
 * paints the texture ONCE, at the app's true visual root, behind
 * `ModalNavigationDrawer`; with `background = Transparent`, the 18
 * `Scaffold()` calls stop covering that image and it shows through
 * all of them without touching a single screen file.
 * `colorScheme.surface` (cards, dialogs, menus) stays opaque,
 * untouched -- only each screen's root background is transparent.
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

    // S054 -- transparente a propósito, ver el kdoc de arriba: deja
    // ver la textura real de aluminio que pinta MainActivity detrás de
    // cada Scaffold().
    background = Color.Transparent,
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

/**
 * S060 -- esquema de color de la piel Granito, con textura real (foto
 * de granito moteado, ver `bg_granito.jpg` y el kdoc de `background`
 * de más abajo). Igual que Aluminio en estructura (`lightColorScheme`,
 * fondo transparente para dejar ver la textura, letra oscura) pero con
 * su propia paleta muestreada de la foto real: gris algo más frío y
 * moteado que el aluminio.
 * ---
 * S060 -- Granito skin color scheme, with a real texture (a photo of
 * speckled granite, see `bg_granito.jpg` and the `background` kdoc
 * below). Same structure as Aluminio (`lightColorScheme`, transparent
 * background to let the texture show through, dark text) but with its
 * own palette sampled from the real photo: a slightly cooler, more
 * speckled gray than aluminum.
 */
private val GranitoLight = Color(0xFFE4E4E6)
private val GranitoBase = Color(0xFFBFC0C3)
private val GranitoDark = Color(0xFF8F9093)
private val GranitoText = Color(0xFF1C1C1E)
private val GranitoRed = Color(0xFFB3261E)

val GranitoColorScheme = lightColorScheme(
    primary = GranitoText,
    onPrimary = GranitoLight,
    primaryContainer = GranitoDark,
    onPrimaryContainer = GranitoText,

    secondary = GranitoText,
    onSecondary = GranitoLight,
    secondaryContainer = GranitoBase,
    onSecondaryContainer = GranitoText,

    tertiary = GranitoRed,
    onTertiary = Color.White,

    // S060 -- transparente a propósito, ver el kdoc de
    // AluminioColorScheme.background: deja ver la textura real que
    // pinta MainActivity detrás de cada Scaffold().
    background = Color.Transparent,
    onBackground = GranitoText,

    surface = GranitoBase,
    onSurface = GranitoText,
    surfaceVariant = GranitoLight,
    onSurfaceVariant = GranitoText.copy(alpha = 0.72f),

    error = GranitoRed,
    onError = Color.White,
    errorContainer = GranitoRed,
    onErrorContainer = Color.White,

    outline = GranitoDark,
)

/**
 * S060 -- esquema de color de la piel Madera, con textura real (foto
 * de veta de madera, ver `bg_madera.jpg`). A diferencia de Aluminio y
 * Granito, la madera es un fondo MEDIO-OSCURO -- por eso es un
 * `darkColorScheme` con letra clara (crema, no blanco puro -- un blanco
 * frío desentona sobre un marrón cálido), igual que MSX en estructura
 * pero con paleta cálida en vez de azul.
 * ---
 * S060 -- Madera (Wood) skin color scheme, with a real texture (a
 * photo of wood grain, see `bg_madera.jpg`). Unlike Aluminio and
 * Granito, wood is a MEDIUM-DARK background -- so it's a
 * `darkColorScheme` with light text (cream, not pure white -- a cold
 * white clashes against a warm brown), same structure as MSX but with
 * a warm palette instead of blue.
 */
private val MaderaCream = Color(0xFFF3E9D8)
private val MaderaMid = Color(0xFF8A5A34)
private val MaderaDark = Color(0xFF6B4423)
private val MaderaAccent = Color(0xFFE0B84A)

val MaderaColorScheme = darkColorScheme(
    primary = MaderaCream,
    onPrimary = MaderaDark,
    primaryContainer = MaderaMid,
    onPrimaryContainer = MaderaCream,

    secondary = MaderaCream,
    onSecondary = MaderaDark,
    secondaryContainer = MaderaMid,
    onSecondaryContainer = MaderaCream,

    tertiary = MaderaAccent,
    onTertiary = MaderaDark,

    // S060 -- transparente a propósito, ver el kdoc de
    // AluminioColorScheme.background: deja ver la textura real que
    // pinta MainActivity detrás de cada Scaffold().
    background = Color.Transparent,
    onBackground = MaderaCream,

    surface = MaderaMid,
    onSurface = MaderaCream,
    surfaceVariant = MaderaDark,
    onSurfaceVariant = MaderaCream.copy(alpha = 0.75f),

    // Rojo normal -- sobre un marrón cálido un rojo se lee bien, no
    // hace falta el apaño del amarillo de MSX.
    error = Color(0xFFE05A4E),
    onError = MaderaDark,
    errorContainer = Color(0xFFE05A4E),
    onErrorContainer = MaderaDark,

    outline = MaderaMid,
)

package com.miguelaetxio.mimoo.data.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestor del archivo `cookies.txt` (formato Netscape) usado por
 * yt-dlp para descargar vídeos que YouTube marca como restringidos
 * por edad. Reportado por Miguel Ángel (2026-07-24, `debug_error.txt`
 * real): "Sign in to confirm your age" en yt-dlp -- verificado que en
 * 2026 la única vía fiable sigue siendo pasar cookies de una cuenta
 * ya verificada; ningún `player_client` alternativo lo evita sin
 * cuenta.
 *
 * El archivo se guarda EXCLUSIVAMENTE en el almacenamiento interno de
 * la app (`filesDir`, privado por el propio sandbox de Android) --
 * nunca se sube a Drive ni se sincroniza entre dispositivos (misma
 * directriz de "secrets nunca en claro" del `MASTER_DOCUMENT.md`,
 * §4.6, aplicada aquí a una credencial de cuenta de usuario en vez de
 * a un secret de proyecto). Cada dispositivo necesita su propia
 * importación manual.
 * ---
 * Manager for the `cookies.txt` file (Netscape format) used by
 * yt-dlp to download videos YouTube marks as age-restricted.
 * Reported by Miguel Ángel (2026-07-24, real `debug_error.txt`):
 * "Sign in to confirm your age" from yt-dlp -- verified that as of
 * 2026 the only reliable path is still passing cookies from an
 * already age-verified account; no alternative `player_client` avoids
 * it without an account.
 *
 * The file is stored EXCLUSIVELY in the app's internal storage
 * (`filesDir`, private by Android's own sandbox) -- never uploaded to
 * Drive nor synced between devices (same "secrets never in the
 * clear" guideline from `MASTER_DOCUMENT.md` §4.6, applied here to a
 * user account credential instead of a project secret). Each device
 * needs its own manual import.
 */
@Singleton
class CookiesManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val COOKIES_FILE_NAME = "youtube_cookies.txt"
    }

    private val cookiesFile: File
        get() = File(context.filesDir, COOKIES_FILE_NAME)

    private val _hasCookies = MutableStateFlow(cookiesFile.exists())
    val hasCookies: StateFlow<Boolean> = _hasCookies.asStateFlow()

    /**
     * Valida (comprobación ligera, no un parser completo de formato
     * Netscape) y guarda el contenido elegido por el usuario con el
     * selector de archivos. Devuelve `false` sin escribir nada si el
     * contenido no parece un cookies.txt de YouTube real -- para que
     * la UI pueda avisar antes de dar una falsa sensación de éxito.
     * ---
     * Validates (a light check, not a full Netscape-format parser)
     * and saves the content chosen by the user with the file picker.
     * Returns `false` without writing anything if the content doesn't
     * look like a real YouTube cookies.txt -- so the UI can warn
     * instead of giving a false sense of success.
     */
    fun importCookies(content: String): Boolean {
        val looksValid = content.lineSequence().any { line ->
            !line.startsWith("#") && line.contains("youtube.com") && line.count { it == '\t' } >= 5
        }
        if (!looksValid) return false

        cookiesFile.writeText(content)
        _hasCookies.value = true
        return true
    }

    fun clearCookies() {
        cookiesFile.delete()
        _hasCookies.value = false
    }

    /**
     * Ruta absoluta del cookies.txt para pasarla a downloader.py, o
     * `null` si no hay ninguno importado -- downloader.py trata
     * `null` como "sin cookies", comportamiento idéntico al de antes
     * de esta funcionalidad.
     * ---
     * Absolute path of cookies.txt to pass to downloader.py, or
     * `null` if none has been imported -- downloader.py treats `null`
     * as "no cookies", identical behavior to before this feature.
     */
    fun cookiesFilePathOrNull(): String? =
        cookiesFile.takeIf { it.exists() }?.absolutePath
}

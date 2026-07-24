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
 * El archivo se guarda en el almacenamiento interno de la app
 * (`filesDir`, privado por el propio sandbox de Android). Petición
 * explícita de Miguel Ángel (2026-07-24): que Silvia no tenga que
 * importarlo a mano en su dispositivo -- rechazada la vía de
 * embeberlo en la instalación (el APK se sirve desde PythonAnywhere,
 * no es un canal privado; cualquiera con el APK podría extraer una
 * sesión completa de la cuenta de Google de Miguel Ángel). En su
 * lugar, el contenido viaja EXCLUSIVAMENTE por el canal privado de
 * sincronización automática ya existente (H07, `SyncEnvelope` vía
 * Drive) -- ver `AutoSyncPusher`/`AutoSyncViewModel`. Deliberadamente
 * FUERA de `BackupBundle`: ese bundle también lo usan la exportación/
 * importación manual (H06) y los códigos de compartición (H10), que
 * pueden acabar en manos de terceros -- las cookies nunca deben viajar
 * por esas vías.
 * ---
 * Manager for the `cookies.txt` file (Netscape format) used by
 * yt-dlp to download videos YouTube marks as age-restricted.
 * Reported by Miguel Ángel (2026-07-24, real `debug_error.txt`):
 * "Sign in to confirm your age" from yt-dlp -- verified that as of
 * 2026 the only reliable path is still passing cookies from an
 * already age-verified account; no alternative `player_client` avoids
 * it without an account.
 *
 * The file is stored in the app's internal storage (`filesDir`,
 * private by Android's own sandbox). Explicit request from Miguel
 * Ángel (2026-07-24): that Silvia doesn't have to manually import it
 * on her device -- the "embed it in the install" route was rejected
 * (the APK is served from PythonAnywhere, not a private channel;
 * anyone with the APK could extract a full session for Miguel Ángel's
 * Google account). Instead, the content travels EXCLUSIVELY through
 * the already-existing private auto-sync channel (H07, `SyncEnvelope`
 * over Drive) -- see `AutoSyncPusher`/`AutoSyncViewModel`. Deliberately
 * kept OUT of `BackupBundle`: that bundle is also used by manual
 * export/import (H06) and share codes (H10), which can end up in
 * third parties' hands -- cookies must never travel through those.
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

    /**
     * Contenido actual del cookies.txt, o `null` si no hay ninguno --
     * usado por `AutoSyncPusher`/`AutoSyncViewModel` (H07) para
     * incluirlo en el `SyncEnvelope` subido a Drive. Nunca se incluye
     * en `BackupBundle` -- ver comentario de la clase.
     * ---
     * Current cookies.txt content, or `null` if there is none -- used
     * by `AutoSyncPusher`/`AutoSyncViewModel` (H07) to include it in
     * the `SyncEnvelope` uploaded to Drive. Never included in
     * `BackupBundle` -- see the class comment.
     */
    fun currentContentOrNull(): String? =
        cookiesFile.takeIf { it.exists() }?.readText()

    /**
     * Aplica un cookies.txt recibido desde el `SyncEnvelope` de Drive
     * (otro dispositivo lo importó, o este mismo dispositivo lo subió
     * antes) -- a diferencia de `importCookies()`, no repite la
     * validación ligera: el contenido ya viajó validado la primera vez
     * que se importó a mano en algún dispositivo. Sobrescribe siempre
     * sin comparar -- quien llama (`AutoSyncViewModel`) ya comprueba
     * antes que el contenido remoto es distinto del local.
     * ---
     * Applies a cookies.txt received from Drive's `SyncEnvelope`
     * (another device imported it, or this same device uploaded it
     * before) -- unlike `importCookies()`, it doesn't repeat the light
     * validation: the content already traveled validated the first
     * time it was manually imported on some device. Always overwrites
     * without comparing -- the caller (`AutoSyncViewModel`) already
     * checks beforehand that the remote content differs from local.
     */
    fun applySyncedCookies(content: String) {
        cookiesFile.writeText(content)
        _hasCookies.value = true
    }
}

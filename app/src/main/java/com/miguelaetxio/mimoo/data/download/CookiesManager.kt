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
     *
     * Fix real (2026-07-24): la comprobación original rechazaba
     * cualquier línea que empezara por "#", incluyendo el prefijo
     * "#HttpOnly_" -- una extensión real y muy común del formato
     * Netscape para marcar cookies HttpOnly, que es exactamente donde
     * viven las cookies de autenticación más importantes de Google
     * (SID, __Secure-3PSID, etc.). Si el cookies.txt real de Miguel
     * Ángel tenía esas líneas como mayoría o totalidad de las de
     * youtube.com, `looksValid` daba `false` y la importación se
     * rechazaba en silencio -- sin que el archivo se llegara a
     * guardar nunca, por más que el usuario repitiera el proceso.
     * ---
     * Validates (a light check, not a full Netscape-format parser)
     * and saves the content chosen by the user with the file picker.
     * Returns `false` without writing anything if the content doesn't
     * look like a real YouTube cookies.txt -- so the UI can warn
     * instead of giving a false sense of success.
     *
     * Real fix (2026-07-24): the original check rejected any line
     * starting with "#", including the "#HttpOnly_" prefix -- a real,
     * very common Netscape format extension marking HttpOnly cookies,
     * which is exactly where Google's most important auth cookies
     * live (SID, __Secure-3PSID, etc.). If Miguel Ángel's real
     * cookies.txt had those lines as most or all of its youtube.com
     * entries, `looksValid` returned `false` and the import was
     * silently rejected -- the file never actually got saved, no
     * matter how many times the process was repeated.
     */
    fun importCookies(content: String): Boolean {
        val looksValid = content.lineSequence().any { line ->
            val cookieLine = if (line.startsWith("#HttpOnly_")) {
                line.removePrefix("#HttpOnly_")
            } else {
                line
            }
            !cookieLine.startsWith("#") &&
                cookieLine.contains("youtube.com") &&
                cookieLine.count { it == '\t' } >= 5
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
     * Diagnóstico real (2026-07-24, segundo debug_error.txt de Miguel
     * Ángel: cookiesExist=true y sigue fallando igual) -- que el
     * archivo exista y "parezca" un cookies.txt (looksValid) no
     * significa que tenga las cookies de sesión de Google que YouTube
     * necesita para la verificación de edad. Esas cookies clave (SID,
     * __Secure-3PSID, etc.) viven en el dominio `.google.com`, NO en
     * `.youtube.com` -- si la extensión exportó solo "Current Tab"
     * estando en youtube.com, es muy probable que el archivo tenga
     * cookies de youtube.com pero NINGUNA de google.com, y por eso
     * yt-dlp recibe un cookiefile real que aun así no autentica nada.
     * Se vuelca en debug_error.txt para confirmarlo o descartarlo sin
     * depender de que Miguel Ángel abra el archivo a mano.
     * ---
     * Real diagnostic (2026-07-24, Miguel Ángel's second debug_error.txt:
     * cookiesExist=true and still failing the same way) -- the file
     * existing and "looking like" a cookies.txt (looksValid) doesn't
     * mean it has the Google session cookies YouTube needs for age
     * verification. Those key cookies (SID, __Secure-3PSID, etc.) live
     * under the `.google.com` domain, NOT `.youtube.com` -- if the
     * extension exported only "Current Tab" while on youtube.com, the
     * file very likely has youtube.com cookies but NONE from
     * google.com, so yt-dlp gets a real cookiefile that still doesn't
     * authenticate anything. Dumped into debug_error.txt to confirm or
     * rule this out without relying on Miguel Ángel opening the file
     * by hand.
     */
    fun diagnosticsSummary(): String {
        if (!cookiesFile.exists()) return "sin archivo"
        val lines = cookiesFile.readLines()
        val hasGoogleDomain = lines.any { it.contains("google.com") }
        val hasYoutubeDomain = lines.any { it.contains("youtube.com") }
        val hasSidCookie = lines.any { it.contains("\tSID\t") || it.contains("__Secure-3PSID") }
        return "tamaño=${cookiesFile.length()}B líneas=${lines.size} " +
            "dominio_google.com=$hasGoogleDomain dominio_youtube.com=$hasYoutubeDomain " +
            "cookie_SID_o_3PSID=$hasSidCookie"
    }

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

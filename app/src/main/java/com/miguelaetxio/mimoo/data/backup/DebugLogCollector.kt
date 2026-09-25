package com.miguelaetxio.mimoo.data.backup

import android.content.Context
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import com.miguelaetxio.mimoo.BuildConfig
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.playback.MimooutcastBrokenLinksLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** One diagnostic file read from the device, ready to upload. */
data class DebugLogFile(val name: String, val content: String)

/** Result of a collection pass: files found (manifest last) and names not present on the device. */
data class DebugLogBundle(val files: List<DebugLogFile>, val missing: List<String>)

/**
 * S037 -- gathers every diagnostic log the app writes so it can be
 * uploaded in one tap to the "MiMoo - Intercambio Claude" Drive folder
 * (platform relay convention, com-actions-relay). Miguel Ángel taps
 * the button and says "listo"; Claude reads the files with its own
 * Drive connector instead of Miguel Ángel sharing each .txt by hand.
 *
 * Explicit allowlist on purpose, never "every .txt at the SAF root":
 * the root also holds `youtube_cookies.txt`, a credential that must
 * never leave the device.
 *
 * Every logger writes to the SAF root and falls back to `filesDir`
 * when no root is chosen -- same order is used here to read them.
 * A manifest with the upload timestamp is appended LAST, so its
 * presence and time mark a completed upload: that is how Claude tells
 * a fresh upload from a stale one.
 */
@Singleton
class DebugLogCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: StorageManager,
    private val brokenLinksLogger: MimooutcastBrokenLinksLogger,
) {

    suspend fun collect(): DebugLogBundle = withContext(Dispatchers.IO) {
        val found = mutableListOf<DebugLogFile>()
        val missing = mutableListOf<String>()

        LOG_FILE_NAMES.forEach { name ->
            val content = readFromSafRoot(name) ?: readFromFile(File(context.filesDir, name))
            if (content != null) found.add(DebugLogFile(name, content)) else missing.add(name)
        }

        val brokenLinksFile = File(brokenLinksLogger.outputFilePath())
        val brokenLinks = readFromFile(brokenLinksFile)
        if (brokenLinks != null) {
            found.add(DebugLogFile(brokenLinksFile.name, brokenLinks))
        } else {
            missing.add(brokenLinksFile.name)
        }

        found.add(DebugLogFile(MANIFEST_FILE_NAME, buildManifest(found, missing)))
        DebugLogBundle(files = found, missing = missing)
    }

    private fun readFromSafRoot(name: String): String? {
        val rootUri = storageManager.getRootUri() ?: return null
        return try {
            val doc = DocumentFile.fromTreeUri(context, rootUri)?.findFile(name) ?: return null
            context.contentResolver.openInputStream(doc.uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    private fun readFromFile(file: File): String? =
        try {
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            null
        }

    private fun buildManifest(found: List<DebugLogFile>, missing: List<String>): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault()).format(Date())
        return buildString {
            appendLine("Subida: $stamp")
            appendLine("Versión: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
            appendLine()
            appendLine("Archivos subidos (${found.size}):")
            found.forEach { appendLine("  ${it.name}  ${it.content.toByteArray().size} bytes") }
            appendLine()
            appendLine("No encontrados en el dispositivo (${missing.size}):")
            missing.forEach { appendLine("  $it") }
        }
    }

    companion object {
        const val MANIFEST_FILE_NAME = "mimoo_logs_manifest.txt"

        private val LOG_FILE_NAMES = listOf(
            "crash_log.txt",
            "debug_error.txt",
            "backup_debug.txt",
            "notification_debug.txt",
            "radio_debug.txt",
            "radio_relacionados_debug.txt",
            "mimooutcast_debug.txt",
            "popurri_favoritos_debug.txt",
            "letras_debug.txt",
            "traslado_biblioteca_informe.txt",
        )
    }
}

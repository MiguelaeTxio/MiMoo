package com.miguelaetxio.mimoo.ui.common

import android.content.Context
import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AlertDialog de MiMoo: colores explícitos y opacos sacados de la piel
 * activa (no los de Material por defecto, que dependen de roles
 * surfaceContainer* que las pieles no definen), con comprobación de
 * contraste: si letra y fondo quedan demasiado parecidos se fuerza
 * negro/blanco. Cada vez que se muestra, apunta en dialog_debug.txt
 * qué diálogo es (archivo:línea de quien lo llama), los colores
 * resueltos y el contraste, para poder diagnosticar con datos.
 * ---
 * MiMoo AlertDialog: explicit opaque colors taken from the active skin
 * (not Material's defaults, which depend on surfaceContainer* roles the
 * skins don't define), with a contrast check: if text and background
 * end up too similar, black/white is forced. Every time it is shown it
 * writes to dialog_debug.txt which dialog it is (caller file:line), the
 * resolved colors and the contrast, so it can be diagnosed with data.
 */
@Composable
fun MiMooAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val container = scheme.surface.copy(alpha = 1f)
    val wantedText = scheme.onSurface
    val wantedVariant = scheme.onSurface
    val ratio = contrastRatio(wantedText, container)
    val safeText = if (ratio >= MIN_CONTRAST) wantedText
    else if (container.luminance() > 0.5f) Color.Black else Color.White

    val context = LocalContext.current
    val caller = remember {
        Throwable().stackTrace
            .firstOrNull { !it.className.contains("MiMooAlertDialog") && it.fileName != null }
            ?.let { "${it.fileName}:${it.lineNumber}" } ?: "?"
    }
    LaunchedEffect(Unit) {
        logDialog(
            context,
            "DIALOGO $caller container=${hex(container)} (surface=${hex(scheme.surface)}) " +
                "texto=${hex(wantedText)} contraste=${"%.2f".format(ratio)} " +
                "aplicado=${hex(safeText)} fondoSkin=${hex(scheme.background)}",
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        containerColor = container,
        titleContentColor = safeText,
        textContentColor = safeText,
    )
}

private const val MIN_CONTRAST = 4.5

private fun contrastRatio(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05) / (lo + 0.05)
}

private fun hex(c: Color): String {
    val argb = (c.alpha * 255).toInt().shl(24) or
        (c.red * 255).toInt().shl(16) or
        (c.green * 255).toInt().shl(8) or
        (c.blue * 255).toInt()
    return "#%08X".format(argb)
}

private const val DIALOG_LOG = "dialog_debug.txt"
private val dialogLock = Any()
private val dialogTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

private fun logDialog(context: Context, line: String) {
    Log.d("MiMoo-Dialog", line)
    try {
        synchronized(dialogLock) {
            val file = File(context.filesDir, DIALOG_LOG)
            if (file.exists() && file.length() > 100_000L) {
                file.writeText(file.readLines().takeLast(200).joinToString("\n", postfix = "\n"))
            }
            file.appendText("${dialogTime.format(Date())}  $line\n")
        }
    } catch (_: Exception) {
        // Un fallo de log nunca afecta a la UI.
        // A log failure never affects the UI.
    }
}

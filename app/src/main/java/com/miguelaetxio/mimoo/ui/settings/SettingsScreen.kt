package com.miguelaetxio.mimoo.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.backup.DriveBackupFile

/**
 * Pantalla "Ajustes" (H06 PASO 3 exportar + PASO 4 importar). Punto
 * de entrada elegido con Miguel Ángel (S006): aquí viven
 * Exportar/Importar repositorio, en vez de un ítem suelto en el menú
 * principal.
 * ---
 * "Settings" screen (H06 PASO 3 export + PASO 4 import). Entry point
 * agreed with Miguel Ángel (S006): Export/Import repository live
 * here, instead of a loose item in the main menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingConsent by viewModel.pendingConsent.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    val snackbarHostState = remember { SnackbarHostState() }

    // Backup elegido de la lista, pendiente de confirmación
    // destructiva -- separado de BackupsListed para poder mostrar el
    // diálogo de confirmación por encima de la lista sin perder cuál
    // se eligió.
    var backupPendingConfirmation by remember { mutableStateOf<DriveBackupFile?>(null) }

    // Lanza el diálogo de consentimiento de Google cuando
    // DriveAuthorizationHelper devuelve NeedsUserConsent -- solo la
    // primera vez que se pide el scope drive.file, o si el usuario
    // revocó el acceso.
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        // Bug real corregido en S006: NO mirar result.resultCode antes
        // de intentar extraer -- ver el comentario de
        // DriveAuthorizationHelper.extractAccessTokenFromResolution.
        // Se intenta siempre; si de verdad falló, la propia función
        // lanza y onConsentResolved lo convierte en un Error visible.
        viewModel.onConsentResolved(activity, result.data)
    }

    LaunchedEffect(pendingConsent) {
        pendingConsent?.let { consentLauncher.launch(it) }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is BackupUiState.ExportSuccess ->
                snackbarHostState.showSnackbar(
                    "Repositorio exportado a Drive: ${state.fileName}"
                )
            is BackupUiState.ImportSuccess ->
                snackbarHostState.showSnackbar(
                    "Repositorio importado: ${state.trackCount} pistas puestas a descargar."
                )
            is BackupUiState.Error ->
                snackbarHostState.showSnackbar("Error: ${state.message}")
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menú")
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
    ) { padding ->
        val isWorking = uiState is BackupUiState.Working

        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            Text(
                "Repositorio de música",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Exporta toda tu biblioteca (pistas, favoritos de " +
                    "álbum y listas de reproducción -- nunca el audio " +
                    "en sí) a un archivo en tu Google Drive, o " +
                    "impórtala en otro dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = { viewModel.onExportClicked(activity) },
                    enabled = !isWorking,
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Exportar a Drive")
                }
                TextButton(
                    onClick = { viewModel.onImportRequested(activity) },
                    enabled = !isWorking,
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Importar desde Drive")
                }
            }
            if (isWorking) {
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Lista de backups disponibles -- se muestra en cuanto
    // BackupUiState.BackupsListed llega, tras pulsar "Importar desde
    // Drive".
    val listedState = uiState as? BackupUiState.BackupsListed
    if (listedState != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text("Elige un backup") },
            text = {
                if (listedState.backups.isEmpty()) {
                    Text("No hay ningún backup en tu carpeta \"MiMoo Backups\" de Drive todavía.")
                } else {
                    LazyColumn {
                        items(listedState.backups, key = { it.id }) { backup ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            ) {
                                TextButton(
                                    onClick = {
                                        backupPendingConfirmation = backup
                                        viewModel.dismissMessage()
                                    },
                                ) {
                                    Column {
                                        Text(backup.name)
                                        backup.createdTime?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMessage) { Text("Cerrar") }
            },
        )
    }

    // Confirmación destructiva explícita -- decisión de Miguel Ángel
    // (ANNEX_H06.md): la importación borra por completo el
    // repositorio local del dispositivo y lo sustituye. Nunca se
    // ejecuta directamente al tocar un ítem de la lista.
    backupPendingConfirmation?.let { backup ->
        AlertDialog(
            onDismissRequest = { backupPendingConfirmation = null },
            title = { Text("¿Sustituir tu repositorio?") },
            text = {
                Text(
                    "Esto borrará por completo tu biblioteca actual en " +
                        "este dispositivo (pistas, favoritos de álbum y " +
                        "listas de reproducción) y la sustituirá por " +
                        "\"${backup.name}\". Todas las pistas se pondrán " +
                        "a descargar de nuevo. Esta acción no se puede " +
                        "deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = backup
                    backupPendingConfirmation = null
                    viewModel.onImportConfirmed(activity, chosen)
                }) {
                    Text("Sustituir")
                }
            },
            dismissButton = {
                TextButton(onClick = { backupPendingConfirmation = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

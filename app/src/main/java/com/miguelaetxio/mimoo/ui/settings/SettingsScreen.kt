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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Pantalla "Ajustes" (H06 PASO 3). Punto de entrada elegido con
 * Miguel Ángel (S006): aquí viven Exportar/Importar repositorio, en
 * vez de un ítem suelto en el menú principal. PASO 4 añadirá la
 * sección Importar debajo de Exportar, en esta misma pantalla.
 * ---
 * "Settings" screen (H06 PASO 3). Entry point agreed with Miguel
 * Ángel (S006): Export/Import repository live here, instead of a
 * loose item in the main menu. PASO 4 will add the Import section
 * below Export, on this same screen.
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

    // Lanza el diálogo de consentimiento de Google cuando
    // DriveAuthorizationHelper devuelve NeedsUserConsent -- solo la
    // primera vez que se pide el scope drive.file, o si el usuario
    // revocó el acceso.
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onConsentResolved(activity, result.data)
        } else {
            viewModel.onConsentCancelled()
        }
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
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            Text(
                "Repositorio de música",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Exporta toda tu biblioteca (pistas, favoritos de " +
                    "álbum y listas de reproducción -- nunca el audio " +
                    "en sí) a un archivo en tu Google Drive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            val isWorking = uiState is BackupUiState.Working
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
                    Text("Exportar repositorio a Drive")
                }
                if (isWorking) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

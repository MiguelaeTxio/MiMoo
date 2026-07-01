package com.miguelaetxio.mimoo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.ui.navigation.MiMooNavGraph
import com.miguelaetxio.mimoo.ui.player.PlayerBar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var storageManager: StorageManager

    /**
     * SAF folder picker. Launched only after the user confirms the
     * explanation dialog below, so it is clear what the picker is
     * for before the OS shows it. The chosen Uri is persisted by
     * StorageManager so the picker is not shown again.
     * ---
     * Selector de carpeta SAF. Se lanza solo tras confirmar el
     * dialogo explicativo de abajo, para que quede claro para que
     * sirve el selector antes de que el sistema lo muestre. El Uri
     * elegido es persistido por StorageManager para que el selector
     * no vuelva a aparecer.
     */
    private val openDocumentTree =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                storageManager.saveRootUri(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            var showStorageExplanation by remember {
                mutableStateOf(!storageManager.hasRootUri())
            }
            val drawerState = rememberDrawerState(
                initialValue = DrawerValue.Closed,
            )
            val scope = rememberCoroutineScope()
            val navController = rememberNavController()

            MaterialTheme {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Text(
                                text = "MiMoo",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(16.dp),
                            )
                            NavigationDrawerItem(
                                label = { Text("Búsqueda") },
                                icon = {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = null,
                                    )
                                },
                                selected = true,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    },
                ) {
                    Surface {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.weight(1f)) {
                                MiMooNavGraph(
                                    navController = navController,
                                    onOpenDrawer = {
                                        scope.launch { drawerState.open() }
                                    },
                                )
                            }
                            PlayerBar()
                        }

                        if (showStorageExplanation) {
                            AlertDialog(
                                onDismissRequest = { },
                                title = { Text("Carpeta de descargas") },
                                text = {
                                    Text(
                                        "MiMoo necesita una carpeta donde " +
                                            "guardar la música que " +
                                            "descargues para escucharla " +
                                            "sin conexión. En la " +
                                            "siguiente pantalla, elige o " +
                                            "crea una carpeta en tu " +
                                            "dispositivo."
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showStorageExplanation = false
                                        openDocumentTree.launch(null)
                                    }) {
                                        Text("Elegir carpeta")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.miguelaetxio.mimoo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.library.LibraryReconciler
import com.miguelaetxio.mimoo.data.playback.PlayerManager
import com.miguelaetxio.mimoo.ui.navigation.MiMooNavGraph
import com.miguelaetxio.mimoo.ui.navigation.Screen
import com.miguelaetxio.mimoo.ui.player.PlayerBar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var storageManager: StorageManager

    @Inject
    lateinit var libraryReconciler: LibraryReconciler

    @Inject
    lateinit var playerManager: PlayerManager

    /**
     * SAF folder picker. Launched only after the user confirms the
     * explanation dialog below, so it is clear what the picker is
     * for before the OS shows it. The chosen Uri is persisted by
     * StorageManager so the picker is not shown again, and the
     * library is reconciled once against whatever the folder already
     * contains (PASO 10, H03) — relevant when the user picks a folder
     * that already has audio files from a previous install.
     * ---
     * Selector de carpeta SAF. Se lanza solo tras confirmar el
     * dialogo explicativo de abajo, para que quede claro para que
     * sirve el selector antes de que el sistema lo muestre. El Uri
     * elegido es persistido por StorageManager para que el selector
     * no vuelva a aparecer, y la biblioteca se reconcilia una vez
     * contra lo que ya haya en la carpeta (PASO 10, H03) — relevante
     * cuando el usuario elige una carpeta que ya tiene audios de una
     * instalación anterior.
     */
    private val openDocumentTree =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                storageManager.saveRootUri(uri)
                lifecycleScope.launch {
                    libraryReconciler.rescan(uri)
                }
            }
        }

    /**
     * Routes an incoming ACTION_VIEW intent (PASO 9, H03) — the user
     * opened an audio file from the system file explorer and picked
     * MiMoo as the app to play it with. Independent of
     * SearchViewModel/Biblioteca: the file did not come from a search
     * result or a SearchResultTrack row, just a raw content:// or
     * file:// Uri handed to us by the OS, so it goes straight to
     * PlayerManager instead of through the Room-backed flows used
     * everywhere else in the app.
     * ---
     * Enruta un intent ACTION_VIEW entrante (PASO 9, H03) — el
     * usuario abrió un archivo de audio desde el explorador de
     * archivos del sistema y eligió MiMoo para reproducirlo.
     * Independiente de SearchViewModel/Biblioteca: el archivo no vino
     * de un resultado de búsqueda ni de una fila SearchResultTrack,
     * solo un Uri content:// o file:// en crudo que nos entrega el
     * SO, así que va directo a PlayerManager en vez de pasar por los
     * flujos respaldados por Room que usa el resto de la app.
     */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val title = uri.lastPathSegment ?: "Pista externa"
        playerManager.play(uri.toString(), title, isLocal = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleViewIntent(intent)

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
            val currentBackStackEntry by
                navController.currentBackStackEntryAsState()
            val currentRoute = currentBackStackEntry?.destination?.route

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
                                selected = currentRoute == Screen.Search.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Search.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            NavigationDrawerItem(
                                label = { Text("Biblioteca") },
                                icon = {
                                    Icon(
                                        Icons.Filled.LibraryMusic,
                                        contentDescription = null,
                                    )
                                },
                                selected = currentRoute == Screen.Library.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Library.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            NavigationDrawerItem(
                                label = { Text("Listas") },
                                icon = {
                                    Icon(
                                        Icons.Filled.QueueMusic,
                                        contentDescription = null,
                                    )
                                },
                                selected = currentRoute == Screen.Playlists.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Playlists.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            NavigationDrawerItem(
                                label = { Text("Buscar álbum") },
                                icon = {
                                    Icon(
                                        Icons.Filled.Album,
                                        contentDescription = null,
                                    )
                                },
                                selected = currentRoute == Screen.AlbumSearch.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.AlbumSearch.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            NavigationDrawerItem(
                                label = { Text("Importar enlace") },
                                icon = {
                                    Icon(
                                        Icons.Filled.Link,
                                        contentDescription = null,
                                    )
                                },
                                selected = currentRoute == Screen.ImportLink.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.ImportLink.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            NavigationDrawerItem(
                                label = { Text("Descargas") },
                                icon = {
                                    Icon(
                                        Icons.Filled.Downloading,
                                        contentDescription = null,
                                    )
                                },
                                selected = currentRoute == Screen.Downloads.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Downloads.route,
                                    ) { launchSingleTop = true }
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

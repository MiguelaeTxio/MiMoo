package com.miguelaetxio.mimoo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.library.LibraryReconciler
import com.miguelaetxio.mimoo.data.library.StartupNotices
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
    lateinit var startupNotices: StartupNotices

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
                // Escaneo SOLO en la primera instalación/primer permiso
                // de carpeta -- petición explícita de Miguel Ángel
                // (2026-07-05, reiterando y corrigiendo la decisión del
                // 2026-07-04): con una biblioteca grande (200 discos ×
                // 10 pistas = 2.000 canciones), reconciliar en CADA
                // arranque de la app sería una barbaridad de trabajo
                // repetido sin necesidad. El rescan de cada arranque
                // se quitó de onCreate() -- este es ahora el ÚNICO
                // punto donde se dispara solo, y nunca se vuelve a
                // repetir automáticamente (el botón de refresco manual
                // de Biblioteca sigue disponible para cuando el propio
                // usuario decida forzarlo). isInitialScanning muestra
                // un spinner de pantalla completa mientras dura, para
                // que no parezca que la app se ha quedado bloqueada.
                // ---
                // Scan ONLY on first install/first folder permission --
                // explicit request from Miguel Ángel (2026-07-05,
                // reiterating and correcting the 2026-07-04 decision):
                // with a large library (200 albums × 10 tracks = 2,000
                // songs), reconciling on EVERY app startup would be a
                // huge amount of needlessly repeated work. The
                // every-startup rescan was removed from onCreate() --
                // this is now the ONLY place it fires on its own, and
                // it's never repeated automatically again (Biblioteca's
                // manual refresh button is still there for whenever the
                // user themselves decides to force it).
                // isInitialScanning shows a full-screen spinner while it
                // runs, so it doesn't look like the app froze.
                isInitialScanning = true
                lifecycleScope.launch {
                    val result = libraryReconciler.rescan(uri)
                    postStartupNotice(result)
                    isInitialScanning = false
                }
            }
        }

    /**
     * true mientras dura el escaneo inicial (solo tras elegir la
     * carpeta por primera vez) -- MainActivity, no una pantalla
     * Composable concreta, porque el callback de openDocumentTree vive
     * fuera de setContent{}. Compose observa igualmente los cambios de
     * un `mutableStateOf` a nivel de Activity.
     * ---
     * true while the initial scan runs (only right after picking the
     * folder for the first time) -- lives on MainActivity, not a
     * specific Composable screen, because openDocumentTree's callback
     * lives outside setContent{}. Compose still observes changes to an
     * Activity-level `mutableStateOf` just the same.
     */
    private var isInitialScanning by mutableStateOf(false)

    /**
     * Aviso explícito pedido por Miguel Ángel (2026-07-04) -- solo si
     * hubo algo real que contar, para no mostrar un Snackbar vacío.
     * Extraído a función propia porque ahora solo se llama desde el
     * escaneo inicial (openDocumentTree), no en cada arranque.
     * ---
     * Explicit notice requested by Miguel Ángel (2026-07-04) -- only if
     * there was something real to report, so it doesn't show an empty
     * Snackbar. Extracted into its own function because it's now only
     * called from the initial scan (openDocumentTree), not on every
     * startup.
     */
    private fun postStartupNotice(result: com.miguelaetxio.mimoo.data.library.RescanResult) {
        if (result.emptyFoldersRemoved > 0 ||
            result.junkFilesRemoved > 0 ||
            result.tracksDiscovered > 0
        ) {
            startupNotices.post(
                buildString {
                    append("Limpieza de arranque: ")
                    val parts = mutableListOf<String>()
                    if (result.junkFilesRemoved > 0) {
                        parts.add(
                            "${result.junkFilesRemoved} archivo(s) " +
                                "no musical(es) borrado(s)"
                        )
                    }
                    if (result.emptyFoldersRemoved > 0) {
                        parts.add(
                            "${result.emptyFoldersRemoved} " +
                                "carpeta(s) vacía(s) borrada(s)"
                        )
                    }
                    if (result.tracksDiscovered > 0) {
                        parts.add(
                            "${result.tracksDiscovered} pista(s) " +
                                "nueva(s) encontrada(s) en disco"
                        )
                    }
                    append(parts.joinToString(", "))
                    append(".")
                }
            )
        }
    }

    /**
     * Solicita POST_NOTIFICATIONS (obligatorio desde Android 13) --
     * bug real reportado por Miguel Ángel (2026-07-05): la
     * notificación de MiMooPlaybackService no aparecía en absoluto,
     * ni siquiera la provisional, porque la app nunca pedía este
     * permiso. Sin él, el servicio en primer plano sigue funcionando
     * (el proceso no muere), pero el sistema suprime en silencio
     * cualquier notificación. No se hace nada especial si el usuario
     * lo deniega -- simplemente no verá la notificación con controles,
     * pero la reproducción en segundo plano sigue protegida igual.
     * ---
     * Requests POST_NOTIFICATIONS (mandatory since Android 13) --
     * real bug reported by Miguel Ángel (2026-07-05):
     * MiMooPlaybackService's notification wasn't showing up at all,
     * not even the placeholder, because the app never requested this
     * permission. Without it, the foreground service keeps working
     * fine (the process doesn't die), but the system silently
     * suppresses any notification. Nothing special is done if the
     * user denies it -- they simply won't see the notification with
     * controls, but background playback stays protected either way.
     */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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

    /**
     * H10 (S011) -- estado leído por el Composable de setContent()
     * vía LaunchedEffect. No puede vivir dentro de la composición
     * porque onNewIntent()/onCreate() se ejecutan fuera de ella;
     * mismo motivo por el que StorageManager/PlayerManager se
     * inyectan como campos de la Activity en vez de leerse solo
     * dentro de setContent().
     */
    private val incomingShareText = mutableStateOf<String?>(null)

    /**
     * H10 (S011) -- ruta de entrada de un código "miMoo+hash"
     * recibido vía el intent-filter ACTION_SEND (ver
     * AndroidManifest.xml). Comprobación de prefijo aquí mismo, antes
     * de tocar ningún ViewModel -- cualquier otro texto compartido a
     * MiMoo por error (una URL cualquiera, texto suelto) se ignora en
     * silencio, no genera ningún diálogo de error.
     */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        if (text.trim().startsWith(com.miguelaetxio.mimoo.data.share.SHARE_CODE_PREFIX)) {
            incomingShareText.value = text
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
        handleShareIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleViewIntent(intent)
        handleShareIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

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

            MaterialTheme(colorScheme = com.miguelaetxio.mimoo.ui.theme.MiMooColorScheme) {
                // H07 PARTE 2, PASO 2.7 -- bloquea TODO lo demás
                // (incluida la explicación de almacenamiento y el
                // escaneo inicial) hasta que se introduce el PIN
                // correcto. Se comprueba antes que isInitialScanning
                // a propósito: sin esto, alguien con el APK pero sin
                // el PIN podría llegar a ver el selector de carpeta
                // SAF antes de que se le pida nada.
                // ---
                // H07 PART 2, STEP 2.7 -- blocks EVERYTHING else
                // (including the storage explanation and the initial
                // scan) until the correct PIN is entered. Checked
                // before isInitialScanning on purpose: without this,
                // someone with the APK but not the PIN could reach
                // the SAF folder picker before being asked for
                // anything.
                val pinViewModel: com.miguelaetxio.mimoo.ui.pin.PinViewModel =
                    androidx.hilt.navigation.compose.hiltViewModel()
                val isUnlocked by pinViewModel.isUnlocked.collectAsState()
                if (!isUnlocked) {
                    com.miguelaetxio.mimoo.ui.pin.PinScreen(viewModel = pinViewModel)
                    return@MaterialTheme
                }

                if (isInitialScanning) {
                    // Spinner de pantalla completa durante el escaneo
                    // inicial (solo tras elegir la carpeta por primera
                    // vez) -- petición explícita de Miguel Ángel
                    // (2026-07-05): sin esto, con una biblioteca grande
                    // el usuario podría pensar que la app se ha
                    // quedado bloqueada al arrancar.
                    // ---
                    // Full-screen spinner during the initial scan (only
                    // right after picking the folder for the first
                    // time) -- explicit request from Miguel Ángel
                    // (2026-07-05): without this, with a large library
                    // the user might think the app froze on startup.
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(24.dp))
                            Text(
                                "Analizando tu biblioteca por primera vez...",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Puede tardar unos segundos si ya tienes " +
                                    "muchas canciones descargadas. Esto solo " +
                                    "ocurre una vez.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    return@MaterialTheme
                }

                // H07 PARTE 1 -- comprobación de sincronización
                // automática, una vez por arranque de app, solo si ya
                // hay una carpeta SAF elegida (si no la hay, el flujo
                // de selección de carpeta de más abajo tiene
                // prioridad -- no tiene sentido sincronizar antes de
                // saber dónde guardar nada). Se ejecuta en segundo
                // plano, sin bloquear la pantalla normal -- solo
                // interrumpe con un diálogo si hace falta confirmar
                // un borrado.
                // ---
                // H07 PART 1 -- automatic sync check, once per app
                // startup, only if a SAF folder is already chosen (if
                // not, the folder-picking flow below takes priority
                // -- no point syncing before knowing where to save
                // anything). Runs in the background, without blocking
                // the normal screen -- only interrupts with a dialog
                // if a deletion needs confirming.
                val autoSyncViewModel: com.miguelaetxio.mimoo.ui.sync.AutoSyncViewModel =
                    androidx.hilt.navigation.compose.hiltViewModel()
                val autoSyncState by autoSyncViewModel.uiState.collectAsState()
                val autoSyncPendingConsent by autoSyncViewModel.pendingConsent.collectAsState()

                val autoSyncConsentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult(),
                ) { result ->
                    autoSyncViewModel.onConsentResolved(this@MainActivity, result.data)
                }

                LaunchedEffect(Unit) {
                    if (storageManager.hasRootUri()) {
                        autoSyncViewModel.startAutoSync(this@MainActivity)
                    }
                }

                LaunchedEffect(autoSyncPendingConsent) {
                    autoSyncPendingConsent?.let { autoSyncConsentLauncher.launch(it) }
                }

                // H10 (S011) -- código "miMoo+hash" recibido vía
                // ACTION_SEND (handleShareIntent). LaunchedEffect
                // reacciona en cuanto incomingShareText cambia de
                // valor (recepción en frío en onCreate() o con la app
                // ya abierta vía onNewIntent(), launchMode
                // singleTask), decodifica, y limpia el valor para no
                // reprocesar el mismo texto en una recomposición
                // posterior.
                val shareImportViewModel: com.miguelaetxio.mimoo.ui.share.ShareImportViewModel =
                    androidx.hilt.navigation.compose.hiltViewModel()
                val shareImportState by shareImportViewModel.uiState.collectAsState()
                LaunchedEffect(incomingShareText.value) {
                    incomingShareText.value?.let { text ->
                        shareImportViewModel.handleIncomingShareCode(text)
                        incomingShareText.value = null
                    }
                }

                (shareImportState as? com.miguelaetxio.mimoo.ui.share.ShareImportUiState.Confirm)
                    ?.let { confirmState ->
                        val b = confirmState.shareBundle.bundle
                        AlertDialog(
                            onDismissRequest = shareImportViewModel::dismiss,
                            title = { Text("Contenido compartido") },
                            text = {
                                Text(
                                    "Alguien te ha compartido: ${confirmState.shareBundle.scopeLabel}. " +
                                        "Se añadirá a tu biblioteca (${b.tracks.size} pista(s), " +
                                        "${b.playlists.size} playlist(s), ${b.favoriteAlbums.size} " +
                                        "álbum(es) favorito(s)) sin borrar nada de lo que ya tienes. " +
                                        "¿Importar?"
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = shareImportViewModel::confirmImport) {
                                    Text("Importar")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = shareImportViewModel::dismiss) { Text("Cancelar") }
                            },
                        )
                    }

                (shareImportState as? com.miguelaetxio.mimoo.ui.share.ShareImportUiState.Done)
                    ?.let { doneState ->
                        AlertDialog(
                            onDismissRequest = shareImportViewModel::dismiss,
                            title = { Text("Contenido importado") },
                            text = {
                                Text(
                                    "${doneState.trackCount} pista(s) del código recibidas, " +
                                        "${doneState.newDownloadsCount} descarga(s) nueva(s) encolada(s)."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = shareImportViewModel::dismiss) { Text("Vale") }
                            },
                        )
                    }

                (shareImportState as? com.miguelaetxio.mimoo.ui.share.ShareImportUiState.Error)
                    ?.let { errorState ->
                        AlertDialog(
                            onDismissRequest = shareImportViewModel::dismiss,
                            title = { Text("No se pudo importar") },
                            text = { Text(errorState.message) },
                            confirmButton = {
                                TextButton(onClick = shareImportViewModel::dismiss) { Text("Vale") }
                            },
                        )
                    }

                // Caso 3 (regla de negocio S008): copia de OTRO
                // dispositivo -- se pregunta explícitamente antes de
                // tocar nada, con la pregunta tal cual la formuló
                // Miguel Ángel.
                // ---
                // Case 3 (S008 business rule): ANOTHER device's copy
                // -- explicitly asks before touching anything, with
                // the question phrased exactly as Miguel Ángel
                // stated it.
                (autoSyncState as? com.miguelaetxio.mimoo.ui.sync.AutoSyncUiState.ConflictOtherDevice)
                    ?.let { conflictState ->
                        val c = conflictState.comparison
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("Copia de otro dispositivo") },
                            text = {
                                Text(
                                    "La última copia de respaldo en Drive la hizo " +
                                        "${conflictState.envelope.deviceLabel}, y no coincide " +
                                        "con lo que tienes aquí (tú: ${c.localTrackCount} " +
                                        "pistas / Drive: ${c.remoteTrackCount} pistas). " +
                                        "¿Se han añadido o eliminado pistas desde ese otro " +
                                        "dispositivo?"
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = autoSyncViewModel::confirmCloudWins) {
                                    Text("Sí -- usar la copia de Drive")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = autoSyncViewModel::confirmLocalWins) {
                                    Text("No -- usar lo que tengo aquí")
                                }
                            },
                        )
                    }

                // Caso 2 (regla de negocio S008): este MISMO
                // dispositivo estaba desincronizado -- la nube ya se
                // restauró sola, aquí solo se informa, sin preguntar
                // nada (nunca se pregunta cuando el desfase es contra
                // la propia copia del dispositivo).
                // ---
                // Case 2 (S008 business rule): this SAME device was
                // out of sync -- the cloud copy was already restored
                // on its own, this only informs, without asking
                // anything (never asks when the gap is against the
                // device's own copy).
                (autoSyncState as? com.miguelaetxio.mimoo.ui.sync.AutoSyncUiState.RestoredFromCloud)
                    ?.let { restoredState ->
                        val c = restoredState.comparison
                        AlertDialog(
                            onDismissRequest = autoSyncViewModel::dismiss,
                            title = { Text("Restaurado desde Drive") },
                            text = {
                                Text(
                                    "Este dispositivo no coincidía con su propia copia de " +
                                        "respaldo en Drive (tenías ${c.localTrackCount} pistas, " +
                                        "la copia tenía ${c.remoteTrackCount}) -- probablemente " +
                                        "algo se tocó fuera de la app. Se ha restaurado desde " +
                                        "Drive."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = autoSyncViewModel::dismiss) { Text("Vale") }
                            },
                        )
                    }

                (autoSyncState as? com.miguelaetxio.mimoo.ui.sync.AutoSyncUiState.Done)
                    ?.message?.let { message ->
                        AlertDialog(
                            onDismissRequest = autoSyncViewModel::dismiss,
                            title = { Text("Sincronizado con Drive") },
                            text = { Text(message) },
                            confirmButton = {
                                TextButton(onClick = autoSyncViewModel::dismiss) { Text("Vale") }
                            },
                        )
                    }

                (autoSyncState as? com.miguelaetxio.mimoo.ui.sync.AutoSyncUiState.Error)?.let { errorState ->
                    AlertDialog(
                        onDismissRequest = autoSyncViewModel::dismiss,
                        title = { Text("No se pudo sincronizar con Drive") },
                        text = { Text(errorState.message) },
                        confirmButton = {
                            TextButton(onClick = autoSyncViewModel::dismiss) { Text("Vale") }
                        },
                    )
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Text(
                                text = "miMoo",
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
                                        Screen.ImportLink.routeFor(),
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            NavigationDrawerItem(
                                label = { Text("Cola de reproducción") },
                                icon = {
                                    Icon(
                                        Icons.Filled.PlaylistPlay,
                                        contentDescription = null,
                                    )
                                },
                                selected = currentRoute == Screen.Queue.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Queue.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            NavigationDrawerItem(
                                label = { Text("Radio Online") },
                                icon = {
                                    Icon(
                                        Icons.Filled.Radio,
                                        contentDescription = null,
                                    )
                                },
                                selected = currentRoute == Screen.RadioBrowser.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.RadioBrowser.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            NavigationDrawerItem(
                                label = { Text("Canales") },
                                icon = {
                                    Icon(
                                        Icons.Filled.Podcasts,
                                        contentDescription = null,
                                    )
                                },
                                selected = currentRoute == Screen.Channels.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Channels.route,
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
                            NavigationDrawerItem(
                                label = { Text("Ajustes") },
                                icon = {
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = null,
                                    )
                                },
                                selected = currentRoute == Screen.Settings.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Settings.route,
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
                            PlayerBar(
                                onOpenQueue = {
                                    navController.navigate(
                                        Screen.Queue.route,
                                    ) { launchSingleTop = true }
                                },
                            )
                        }

                        if (showStorageExplanation) {
                            AlertDialog(
                                onDismissRequest = { },
                                title = { Text("Carpeta de descargas") },
                                text = {
                                    Text(
                                        "miMoo necesita una carpeta donde " +
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

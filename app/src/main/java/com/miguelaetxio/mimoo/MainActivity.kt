package com.miguelaetxio.mimoo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
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
import com.miguelaetxio.mimoo.ui.theme.glassChip
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
    lateinit var autoSyncPusher: com.miguelaetxio.mimoo.data.backup.AutoSyncPusher

    @Inject
    lateinit var libraryReconciler: LibraryReconciler

    @Inject
    lateinit var startupNotices: StartupNotices

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var uiPreferencesManager: com.miguelaetxio.mimoo.data.access.UiPreferencesManager

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
     * S043 -- petición explícita de Miguel Ángel tras tres intentos
     * fallidos sin permiso ("cuando estoy en una llamada la música se
     * activa sola, en mitad de la llamada, y la tengo que parar a
     * mano"): AudioManager.mode/los eventos de foco de audio no son
     * lo bastante fiables para esto -- se pasa al mecanismo estándar y
     * robusto, `TelephonyManager`, que exige `READ_PHONE_STATE`. Sin
     * este permiso, PlayerManager sigue usando el mecanismo anterior
     * (AudioFocusRequest manual) como única red de seguridad -- pedirlo
     * mejora la fiabilidad, no es obligatorio para que la app funcione.
     */
    private val requestPhoneStatePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) playerManager.onPhoneStatePermissionGranted()
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
        if (isMimooShareFile(intent)) return
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
    private val incomingShareFileUri = mutableStateOf<android.net.Uri?>(null)

    /**
     * H10 (S011, tercer rediseño -- ver `DOCS/ANNEX_H10.md` y
     * `ShareCodeRepository` para el porqué completo) -- ruta de
     * entrada de un archivo `.txt` recibido vía el intent-filter
     * ACTION_VIEW. Emparejado por tipo `text/plain`/`application/txt`
     * (ambos -- algunas apps etiquetan los adjuntos `.txt` con el
     * segundo, no estándar, en vez del primero). MiMoo va a aparecer
     * en "Abrir con" para CUALQUIER `.txt`, no solo los suyos -- la
     * comprobación real de si es un archivo de MiMoo de verdad (marca
     * `SHARE_FILE_MARKER`) vive en `ShareCodeRepository.decodeFile()`,
     * async, con un mensaje amable si no lo es.
     */
    private fun handleShareFileIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (isMimooShareFile(intent)) {
            incomingShareFileUri.value = uri
        }
    }

    private fun isMimooShareFile(intent: Intent): Boolean =
        intent.type == "text/plain" || intent.type == "application/txt"

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
        handleShareFileIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleViewIntent(intent)
        handleShareFileIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // S043 -- ver el kdoc real junto a requestPhoneStatePermission.
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPhoneStatePermission.launch(Manifest.permission.READ_PHONE_STATE)
        } else {
            playerManager.onPhoneStatePermissionGranted()
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

            val glassBorderEnabled by uiPreferencesManager.glassBorderEnabled.collectAsState()
            // S052 -- petición explícita de Miguel Ángel: piel
            // seleccionable desde Ajustes > Apariencia. Se lee aquí,
            // en la raíz de la composición, y se provee tanto al
            // MaterialTheme (colores) como a LocalGlassTokens (efecto
            // cristal) -- el cambio se aplica a toda la app al
            // instante, sin reiniciar nada, igual que glassBorderEnabled.
            val appSkin by uiPreferencesManager.appSkin.collectAsState()

            androidx.compose.runtime.CompositionLocalProvider(
                com.miguelaetxio.mimoo.ui.theme.LocalGlassBorderEnabled provides glassBorderEnabled,
                com.miguelaetxio.mimoo.ui.theme.LocalGlassTokens provides
                    com.miguelaetxio.mimoo.ui.theme.glassTokensFor(appSkin),
            ) {
            MaterialTheme(colorScheme = com.miguelaetxio.mimoo.ui.theme.colorSchemeFor(appSkin)) {
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
                // S068 -- rediseño explícito de Miguel Ángel tras S066
                // (retirada completa) y la investigación de S067 (causa
                // real: pruneEmptyFolders() sin salvaguarda, ya
                // arreglado). El disparo automático vuelve, pero SIN el
                // caso que borraba en silencio: ver el kdoc de
                // AutoSyncUiState en AutoSyncViewModel.kt -- ya NO
                // existe ningún caso de "la nube manda sin preguntar".
                // Cualquier discrepancia, sea el mismo dispositivo o
                // no, pregunta SIEMPRE con el mismo diálogo
                // (CountMismatch) de más abajo.
                val autoSyncViewModel: com.miguelaetxio.mimoo.ui.sync.AutoSyncViewModel =
                    androidx.hilt.navigation.compose.hiltViewModel()
                val autoSyncState by autoSyncViewModel.uiState.collectAsState()
                val autoSyncPendingConsent by autoSyncViewModel.pendingConsent.collectAsState()

                val autoSyncConsentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
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

                // S068 -- único diálogo de discrepancia -- pregunta tal
                // cual la formuló Miguel Ángel, con las cifras reales de
                // cada lado para que la decisión sea informada. Nunca
                // se resuelve solo.
                (autoSyncState as? com.miguelaetxio.mimoo.ui.sync.AutoSyncUiState.CountMismatch)
                    ?.let { mismatchState ->
                        val c = mismatchState.comparison
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("Diferencia con Drive") },
                            text = {
                                Text(
                                    "En Drive tienes ${c.remoteTrackCount} pistas, en este " +
                                        "dispositivo ${c.localTrackCount} -- favoritos, Drive: " +
                                        "${c.remoteAllFavoritesCount}, aquí: " +
                                        "${c.localAllFavoritesCount}. ¿Con cuál te quedas?"
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = autoSyncViewModel::confirmCloudWins) {
                                    Text("Con la de Drive")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = autoSyncViewModel::confirmLocalWins) {
                                    Text("Con la de este dispositivo")
                                }
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

                // S070 -- petición explícita de Miguel Ángel: "subir
                // nunca pregunta, borrar siempre pregunta [...] si me
                // pilla dormido, no se machaca nada". Este diálogo NO
                // viene de AutoSyncViewModel (ese es solo la
                // comprobación de arranque) -- viene de AutoSyncPusher,
                // que dispara esto en CUALQUIER momento que una
                // mutación local (borrar pista/álbum/artista/lista,
                // etc.) deje el recuento por debajo de lo que ya hay en
                // Drive. Mientras no se responda, Drive se queda tal
                // cual estaba.
                val pushPendingConfirmation by autoSyncPusher.pendingConfirmation.collectAsState()
                (pushPendingConfirmation as? com.miguelaetxio.mimoo.data.backup.PushConfirmationState.PendingDeletionConfirm)
                    ?.let { pending ->
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("¿Borrar también en Drive?") },
                            text = {
                                Text(
                                    "Tu copia de Drive tiene ${pending.currentRemoteTrackCount} " +
                                        "pistas; en este dispositivo ahora hay " +
                                        "${pending.newTrackCount}. ¿Confirmas que has borrado tú " +
                                        "y quieres que se refleje también en Drive?"
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = { autoSyncPusher.confirmPushDeletion(this@MainActivity) },
                                ) {
                                    Text("Sí, borrar en Drive")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = autoSyncPusher::dismissPushDeletion) {
                                    Text("No, dejar Drive como está")
                                }
                            },
                        )
                    }

                // H10 (S011) -- archivo .txt recibido vía ACTION_VIEW
                // (handleShareFileIntent). LaunchedEffect reacciona en
                // cuanto incomingShareFileUri cambia de valor (recepción
                // en frío en onCreate() o con la app ya abierta vía
                // onNewIntent(), launchMode singleTask), decodifica, y
                // limpia el valor para no reprocesar el mismo archivo en
                // una recomposición posterior.
                val shareImportViewModel: com.miguelaetxio.mimoo.ui.share.ShareImportViewModel =
                    androidx.hilt.navigation.compose.hiltViewModel()
                val shareImportState by shareImportViewModel.uiState.collectAsState()
                LaunchedEffect(incomingShareFileUri.value) {
                    incomingShareFileUri.value?.let { uri ->
                        shareImportViewModel.handleIncomingShareFile(uri)
                        incomingShareFileUri.value = null
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

                // S066 -- diálogos de auto-sync (Casos 2/3, "Sincronizado
                // con Drive", errores) eliminados junto con su disparo --
                // ver el comentario de arriba.

                // S054/S060 -- textura real (foto) de cada piel con
                // fondo transparente, pintada UNA sola vez aquí, en la
                // raíz visual de toda la app, detrás de
                // ModalNavigationDrawer. Ver el kdoc de
                // AluminioColorScheme.background en MiMooTheme.kt para
                // el porqué completo (evita tocar los 18 Scaffold()
                // distintos de cada pantalla). MSX no tiene textura --
                // su fondo (background = MsxBlue) sigue siendo opaco,
                // así que el Box de aquí no pintaría nada visible de
                // todas formas.
                Box(modifier = Modifier.fillMaxSize()) {
                    val backgroundDrawableRes = when (appSkin) {
                        com.miguelaetxio.mimoo.ui.theme.AppSkin.ALUMINIO ->
                            com.miguelaetxio.mimoo.R.drawable.bg_aluminio_brushed
                        com.miguelaetxio.mimoo.ui.theme.AppSkin.GRANITO ->
                            com.miguelaetxio.mimoo.R.drawable.bg_granito
                        com.miguelaetxio.mimoo.ui.theme.AppSkin.MADERA ->
                            com.miguelaetxio.mimoo.R.drawable.bg_madera
                        com.miguelaetxio.mimoo.ui.theme.AppSkin.MSX -> null
                    }
                    if (backgroundDrawableRes != null) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(backgroundDrawableRes),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    }
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Box(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .glassChip(interactive = false),
                            ) {
                                Text(
                                    text = "miMoo",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                )
                            }
                            CompactDrawerItem(
                                label = "Búsqueda",
                                icon = Icons.Filled.Search,
                                selected = currentRoute == Screen.Search.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Search.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                            )
                            CompactDrawerItem(
                                label = "Biblioteca",
                                icon = Icons.Filled.LibraryMusic,
                                selected = currentRoute == Screen.Library.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Library.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                            )
                            CompactDrawerItem(
                                label = "Explorador",
                                icon = Icons.Filled.Explore,
                                selected = currentRoute == Screen.Explorer.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Explorer.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                            )
                            CompactDrawerItem(
                                label = "Favoritos",
                                icon = Icons.Filled.Star,
                                selected = currentRoute == Screen.Favorites.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Favorites.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                            )
                            CompactDrawerItem(
                                label = "Lista negra",
                                icon = Icons.Filled.ThumbDown,
                                selected = currentRoute == Screen.Disliked.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Disliked.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                            )
                            CompactDrawerItem(
                                label = "Letras",
                                icon = Icons.Filled.Subtitles,
                                selected = currentRoute == Screen.LyricsSearch.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.LyricsSearch.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                            )
                            CompactDrawerItem(
                                label = "Listas",
                                icon = Icons.Filled.QueueMusic,
                                selected = currentRoute == Screen.Playlists.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Playlists.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                            )
                            CompactDrawerItem(
                                label = "Importar enlace",
                                icon = Icons.Filled.Link,
                                selected = currentRoute == Screen.ImportLink.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.ImportLink.routeFor(),
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                            )
                            CompactDrawerItem(
                                label = "Cola de reproducción",
                                icon = Icons.Filled.PlaylistPlay,
                                selected = currentRoute == Screen.Queue.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Queue.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                            )
                            CompactDrawerItem(
                                label = "Radio Online",
                                icon = Icons.Filled.Radio,
                                selected = currentRoute == Screen.RadioBrowser.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.RadioBrowser.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                            )
                            CompactDrawerItem(
                                label = "miMooutCast",
                                icon = Icons.Filled.Tune,
                                selected = currentRoute == Screen.Mimooutcast.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Mimooutcast.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                            )
                            CompactDrawerItem(
                                label = "Canales",
                                icon = Icons.Filled.Podcasts,
                                selected = currentRoute == Screen.Channels.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Channels.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                            )
                            CompactDrawerItem(
                                label = "Descargas",
                                icon = Icons.Filled.Downloading,
                                selected = currentRoute == Screen.Downloads.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Downloads.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                            )
                            CompactDrawerItem(
                                label = "Ajustes",
                                icon = Icons.Filled.Settings,
                                selected = currentRoute == Screen.Settings.route,
                                onClick = {
                                    navController.navigate(
                                        Screen.Settings.route,
                                    ) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                },
                            )
                            }
                        }
                    },
                ) {
                    // S060 -- bug real reportado por Miguel Ángel: en
                    // las pieles con textura (Aluminio/Granito/Madera)
                    // no se veía ninguna foto, solo colores planos.
                    // Causa real: este Surface no fijaba `color`
                    // explícitamente, así que usaba el valor por
                    // defecto -- `colorScheme.surface`, que es OPACO A
                    // PROPÓSITO en esas pieles (para que tarjetas y
                    // menús se sigan viendo bien) y DISTINTO de
                    // `colorScheme.background` (transparente a
                    // propósito, para dejar ver la textura). Ese
                    // relleno opaco tapaba la imagen entera de
                    // MainActivity sin que se notara en MSX, donde
                    // `surface` y `background` son el mismo azul.
                    // Arreglo: fijar aquí `colorScheme.background`
                    // explícitamente, que es el color semánticamente
                    // correcto para el fondo raíz de toda la app (no
                    // `surface`, pensado para superficies elevadas
                    // como tarjetas).
                    Surface(color = MaterialTheme.colorScheme.background) {
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
                                onOpenAlbum = { artistName, albumName ->
                                    navController.navigate(
                                        Screen.Album.routeFor(artistName, albumName),
                                    )
                                },
                                onOpenArtist = { artistName ->
                                    navController.navigate(
                                        Screen.Artist.routeFor(artistName),
                                    )
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
    }
}

/**
 * Fila de drawer -- petición explícita de Miguel Ángel (2026-08-06):
 * "ya hay demasiadas opciones en la sidebar, hay que poner el tamaño
 * de la fuente 3/4 de altura", para que cupieran las 15 opciones sin
 * scroll. Revertido (S034, MiMoo-S34H12) -- nueva petición explícita
 * de Miguel Ángel: "volver a poner un tamaño de letra normal en las
 * opciones de la sidebar pero hacerla scrollable". La sidebar entera
 * (`ModalDrawerSheet`) ahora vive dentro de una `Column` con
 * `verticalScroll()`, así que ya no hace falta comprimir icono/texto
 * para que quepa todo. `NavigationDrawerItem` de Material3 sigue sin
 * exponer ninguna API pública para su altura mínima fija (~56dp), así
 * que se mantiene el `Row` propio (mismo lenguaje visual, glassChip,
 * `active = selected` para el resaltado), solo que ahora con
 * icono/texto/padding a tamaño normal en vez de 3/4.
 */
@Composable
private fun CompactDrawerItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .glassChip(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    com.miguelaetxio.mimoo.ui.theme.LocalGlassTokens.current.cornerRadius,
                ),
                active = selected,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

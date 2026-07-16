package com.miguelaetxio.mimoo.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miguelaetxio.mimoo.data.backup.DriveBackupFile
import com.miguelaetxio.mimoo.ui.theme.glassChip

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
    val generatedShareFileUri by viewModel.generatedShareFileUri.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    val snackbarHostState = remember { SnackbarHostState() }

    // H10 (S011) -- en cuanto el archivo .txt está generado, abre
    // el selector de "Compartir" del sistema con ese ARCHIVO
    // (EXTRA_STREAM), no texto -- necesario para que el receptor
    // tenga algo que tocar-para-abrir al recibirlo. Permiso de
    // lectura otorgado explícitamente al chooser -- FileProvider
    // exige esto para cualquier app que reciba el Uri content://.
    LaunchedEffect(generatedShareFileUri) {
        generatedShareFileUri?.let { uri ->
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, null))
            viewModel.consumeGeneratedShareFileUri()
        }
    }

    // Backup elegido de la lista, pendiente de confirmación
    // destructiva -- separado de BackupsListed para poder mostrar el
    // diálogo de confirmación por encima de la lista sin perder cuál
    // se eligió.
    var backupPendingConfirmation by remember { mutableStateOf<DriveBackupFile?>(null) }

    // S010 -- "Importar desde archivo": nombre + contenido JSON ya
    // leídos del archivo elegido con el selector de Android, pendiente
    // de la misma confirmación destructiva que el resto de
    // importaciones. Independiente de Drive del todo -- ver
    // SettingsViewModel.importFromFile().
    // ---
    // S010 -- "Import from file": name + JSON content already read
    // from the file picked with Android's file picker, pending the
    // same destructive confirmation as any other import. Completely
    // independent from Drive.
    var filePendingConfirmation by remember { mutableStateOf<Pair<String, String>?>(null) }
    var fileImportError by remember { mutableStateOf<String?>(null) }

    val fileImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val displayName = context.contentResolver.query(uri, null, null, null, null)
                ?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                } ?: "el archivo elegido"
            val json = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
            if (json.isNullOrBlank()) {
                fileImportError = "No se pudo leer el archivo elegido."
            } else {
                filePendingConfirmation = displayName to json
            }
        } catch (e: Exception) {
            fileImportError = e.message ?: "No se pudo leer el archivo elegido."
        }
    }

    // H10 (S011) -- selector manual del archivo .txt recibido, vía
    // de emergencia independiente de la apertura automática. Usa el
    // MISMO ShareImportViewModel de ámbito Activity que MainActivity
    // ya usa para el diálogo de confirmación -- se le pide
    // explícitamente por viewModelStoreOwner=activity para no
    // obtener una instancia nueva de ámbito NavBackStackEntry, que
    // sería una instancia distinta a la de MainActivity y no
    // compartiría estado con su diálogo.
    val shareImportViewModel: com.miguelaetxio.mimoo.ui.share.ShareImportViewModel =
        androidx.hilt.navigation.compose.hiltViewModel(activity as androidx.activity.ComponentActivity)
    val shareFileImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { shareImportViewModel.handleIncomingShareFile(it) }
    }

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
                title = {
                    Box(modifier = Modifier.glassChip()) {
                        Text("Ajustes", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                },
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

        // S011 -- petición explícita de Miguel Ángel: "hay que
        // ponerlo como acordeón, ya que con el reproductor no se
        // pueden instalar actualizaciones" -- con el PlayerBar
        // expandido ocupando la parte de abajo de la pantalla, el
        // contenido de Ajustes (cuatro secciones largas seguidas) no
        // dejaba sitio para llegar a "Buscar actualizaciones" sin
        // contraer antes el reproductor a mano. Todas las secciones
        // empiezan contraídas -- solo el título (con cristal) más una
        // flecha, así la pantalla entera es corta por defecto y
        // cualquier sección (incluida Actualizaciones) queda a un
        // toque, quepa o no el resto debajo del reproductor.
        // Comportamiento de acordeón real (Miguel Ángel: "como
        // acordeón"): solo una sección abierta a la vez, abrir otra
        // cierra la anterior.
        var expandedSection by remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SettingsAccordionSection(
                title = "Repositorio de música",
                expanded = expandedSection == "repositorio",
                onToggle = {
                    expandedSection = if (expandedSection == "repositorio") null else "repositorio"
                },
            ) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = { viewModel.onExportClicked(activity) },
                        enabled = !isWorking,
                        modifier = Modifier.glassChip(),
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Exportar a Drive")
                    }
                    TextButton(
                        onClick = { viewModel.onImportRequested(activity) },
                        enabled = !isWorking,
                        modifier = Modifier.glassChip(),
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Importar desde Drive")
                    }
                    TextButton(
                        onClick = { fileImportLauncher.launch(arrayOf("*/*")) },
                        enabled = !isWorking,
                        modifier = Modifier.glassChip(),
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Importar desde archivo")
                    }
                }
                if (isWorking) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // H10 (S011) -- primer nivel de compartición implementado
            // (Biblioteca completa); el resto (Artista, Álbum, Tema
            // suelto, Sencillos, Listas de reproducción, Canales) vive
            // en DOCS/ANNEX_H10.md para sesiones siguientes, mismo
            // mecanismo.
            SettingsAccordionSection(
                title = "Compartir",
                expanded = expandedSection == "compartir",
                onToggle = {
                    expandedSection = if (expandedSection == "compartir") null else "compartir"
                },
            ) {
                Text(
                    "Genera un archivo que se abre directamente con MiMoo al " +
                        "enviarlo por WhatsApp o cualquier otro medio. Se añade " +
                        "a la biblioteca de quien lo abre sin borrar nada de lo " +
                        "que ya tenía, y descarga el contenido directamente " +
                        "desde YouTube.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = viewModel::onShareLibraryClicked, modifier = Modifier.glassChip()) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Compartir biblioteca completa")
                }

                // H10 (S011) -- vía manual de emergencia. La apertura
                // automática al tocar el archivo en WhatsApp depende de
                // que WhatsApp conserve/informe bien el tipo de
                // contenido al abrirlo, algo que en la práctica es
                // conocido por ser poco fiable incluso para tipos de
                // archivo muy comunes (PDF, DOCX). Este selector usa el
                // selector de archivos de Android directamente
                // (ACTION_OPEN_DOCUMENT) y no depende de nada de eso --
                // funciona siempre, sea cual sea el motivo por el que la
                // apertura automática no dispare.
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { shareFileImportLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.glassChip(),
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Importar código recibido (elegir archivo)")
                }
            }

            Spacer(Modifier.height(12.dp))

            // S011 -- interruptor de borde del cristal ("añade un
            // toggle en ajustes para cambiar de borde a sin borde").
            // glassBorderEnabled es un StateFlow reactivo -- el
            // cambio se ve en toda la app al instante, sin reiniciar
            // (ver LocalGlassBorderEnabled, ui/theme/Glass.kt).
            val glassBorderEnabled by viewModel.glassBorderEnabled.collectAsState()
            SettingsAccordionSection(
                title = "Apariencia",
                expanded = expandedSection == "apariencia",
                onToggle = {
                    expandedSection = if (expandedSection == "apariencia") null else "apariencia"
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassChip()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Borde en las chapitas de cristal")
                        Text(
                            "Contorno fino alrededor de títulos, menús y filas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = glassBorderEnabled,
                        onCheckedChange = viewModel::setGlassBorderEnabled,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            SettingsAccordionSection(
                title = "Actualizaciones",
                expanded = expandedSection == "actualizaciones",
                onToggle = {
                    expandedSection = if (expandedSection == "actualizaciones") null else "actualizaciones"
                },
            ) {
                UpdateCheckSection()
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

    // S010 -- misma confirmación destructiva que el resto de
    // importaciones (ANNEX_H06.md), aplicada también al archivo
    // local -- borra el repositorio igual que un import de Drive, así
    // que necesita el mismo aviso explícito.
    // ---
    // S010 -- same destructive confirmation as any other import,
    // applied to the local file too -- it erases the repository the
    // same way a Drive import does, so it needs the same explicit
    // warning.
    filePendingConfirmation?.let { (displayName, json) ->
        AlertDialog(
            onDismissRequest = { filePendingConfirmation = null },
            title = { Text("¿Sustituir tu repositorio?") },
            text = {
                Text(
                    "Esto borrará por completo tu biblioteca actual en " +
                        "este dispositivo (pistas, favoritos de álbum y " +
                        "listas de reproducción) y la sustituirá por " +
                        "\"$displayName\". Todas las pistas se pondrán " +
                        "a descargar de nuevo. Esta acción no se puede " +
                        "deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    filePendingConfirmation = null
                    viewModel.importFromFile(activity, json)
                }) {
                    Text("Sustituir")
                }
            },
            dismissButton = {
                TextButton(onClick = { filePendingConfirmation = null }) {
                    Text("Cancelar")
                }
            },
        )
    }

    fileImportError?.let { message ->
        AlertDialog(
            onDismissRequest = { fileImportError = null },
            title = { Text("No se pudo leer el archivo") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { fileImportError = null }) { Text("Cerrar") }
            },
        )
    }
}

/**
 * S011 -- sección plegable de Ajustes ("hay que ponerlo como
 * acordeón, ya que con el reproductor no se pueden instalar
 * actualizaciones"). Cabecera con cristal (petición explícita:
 * "poner las chapas de cristal esmerilado... para los títulos de las
 * secciones de los ajustes"), tocable entera para expandir/contraer
 * -- una flecha a la derecha indica el estado. El acordeón real (solo
 * una sección abierta a la vez) lo gestiona quien llama, pasando
 * `expanded`/`onToggle` ya resueltos contra un único estado
 * compartido -- este composable no sabe nada de las demás secciones.
 */
@Composable
private fun SettingsAccordionSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassChip()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Contraer" else "Expandir",
            )
        }
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                content()
            }
        }
    }
}

/**
 * H07 PARTE 2, PASO 2.5. Autocontenida: lee su propio
 * BuildConfig.VERSION_CODE, gestiona su propio ViewModel, y lanza
 * ella misma el Intent(ACTION_VIEW) de instalación -- no depende de
 * nada del resto de SettingsScreen.
 * ---
 * H07 PART 2, STEP 2.5. Self-contained: reads its own
 * BuildConfig.VERSION_CODE, manages its own ViewModel, and fires the
 * install Intent(ACTION_VIEW) itself -- doesn't depend on anything
 * else in SettingsScreen.
 */
@Composable
private fun UpdateCheckSection(
    viewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var installErrorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Versión instalada: ${com.miguelaetxio.mimoo.BuildConfig.VERSION_NAME} " +
                "(${com.miguelaetxio.mimoo.BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        when (val state = uiState) {
            is UpdateUiState.Idle -> {
                TextButton(
                    onClick = {
                        viewModel.checkForUpdate(
                            com.miguelaetxio.mimoo.BuildConfig.VERSION_CODE,
                        )
                    },
                    modifier = Modifier.glassChip(),
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Buscar actualizaciones")
                }
            }
            is UpdateUiState.Checking -> {
                Row {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Comprobando...")
                }
            }
            is UpdateUiState.UpToDate -> {
                Text("Ya tienes la última versión.")
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = viewModel::dismiss) { Text("Cerrar") }
            }
            is UpdateUiState.UpdateAvailable -> {
                Text(
                    "Hay una versión nueva disponible: " +
                        "${state.manifest.versionName} " +
                        "(${state.manifest.versionCode})."
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { viewModel.downloadUpdate(state.manifest) }) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Descargar")
                    }
                    TextButton(onClick = viewModel::dismiss) { Text("Ahora no") }
                }
            }
            is UpdateUiState.Downloading -> {
                val hasTotal = state.totalBytes > 0
                val downloadedMb = state.bytesDownloaded / (1024f * 1024f)
                val totalMb = state.totalBytes / (1024f * 1024f)
                val percent = if (hasTotal) {
                    (state.bytesDownloaded * 100 / state.totalBytes).toInt()
                } else {
                    null
                }
                Column {
                    if (hasTotal) {
                        LinearProgressIndicator(
                            progress = { state.bytesDownloaded.toFloat() / state.totalBytes.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        // Servidor sin Content-Length (poco probable
                        // para un asset de Release) -- progreso
                        // indeterminado en vez de una barra falsa.
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (hasTotal) {
                            "Descargando... %.1f / %.1f MB (%d%%)".format(
                                downloadedMb, totalMb, percent,
                            )
                        } else {
                            "Descargando... %.1f MB".format(downloadedMb)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            is UpdateUiState.ReadyToInstall -> {
                Text("Descarga completa.")
                installErrorMessage?.let { msg ->
                    Spacer(Modifier.height(4.dp))
                    Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        installErrorMessage = null
                        // H07 PARTE 2, fix real: sin
                        // REQUEST_INSTALL_PACKAGES concedido para
                        // MiMoo, el Intent de instalación no hacía
                        // absolutamente nada -- ni error ni aviso.
                        // Se comprueba antes y, si falta, se manda a
                        // Miguel Ángel directamente a la pantalla de
                        // Ajustes del sistema donde concederlo, en
                        // vez de dejar que el botón "Instalar" no
                        // haga nada en silencio.
                        // ---
                        // H07 PART 2, real fix: without
                        // REQUEST_INSTALL_PACKAGES granted for MiMoo,
                        // the install Intent did absolutely nothing --
                        // no error, no notice. Checked beforehand and,
                        // if missing, sends Miguel Ángel straight to
                        // the system Settings screen to grant it,
                        // instead of letting the "Instalar" button
                        // silently do nothing.
                        if (!context.packageManager.canRequestPackageInstalls()) {
                            try {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        android.net.Uri.parse("package:${context.packageName}"),
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                                installErrorMessage = "Concede el permiso y vuelve a pulsar Instalar."
                            } catch (e: Exception) {
                                installErrorMessage = "Activa \"Instalar apps desconocidas\" " +
                                    "para MiMoo en Ajustes del sistema, y vuelve a intentarlo."
                            }
                            return@TextButton
                        }

                        val installIntent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                        ).apply {
                            setDataAndType(
                                state.apkUri,
                                "application/vnd.android.package-archive",
                            )
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(installIntent)
                            viewModel.dismiss()
                        } catch (e: Exception) {
                            installErrorMessage = "No se pudo abrir el instalador: ${e.message}"
                        }
                    },
                ) {
                    Text("Instalar")
                }
            }
            is UpdateUiState.Error -> {
                Text(
                    "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = viewModel::dismiss) { Text("Cerrar") }
            }
        }
    }
}

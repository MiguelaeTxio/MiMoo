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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
    // S025 -- estado del constructor del diccionario del ancla (H08).
    val dictionaryState by viewModel.dictionaryState.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshDictionaryCounts() }
    val mimooutcastBuildProgress by viewModel.mimooutcastBuildProgress.collectAsState()
    val mimooutcastDecadeBuildProgress by viewModel.mimooutcastDecadeBuildProgress.collectAsState()
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

    // Fix real (2026-07-24) -- selector del cookies.txt (formato
    // Netscape) exportado por Miguel Ángel desde su navegador logueado
    // en YouTube. Mismo patrón que fileImportLauncher (lee el
    // contenido con el ContentResolver, sin copiar el Uri en sí) --
    // ver CookiesManager.importCookies() para la validación real.
    val cookiesImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = try {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
        if (content.isNullOrBlank()) {
            viewModel.importCookies(activity, "")
        } else {
            viewModel.importCookies(activity, content)
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

    val cookiesImportMessage by viewModel.cookiesImportMessage.collectAsState()
    LaunchedEffect(cookiesImportMessage) {
        cookiesImportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearCookiesImportMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.glassChip(interactive = false)) {
                        Text("Ajustes", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(4.dp).glassChip(shape = androidx.compose.foundation.shape.CircleShape)) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menú")
                        }
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

            // S025 -- BOTÓN "CREAR BASE DE DATOS" (H08).
            //
            // Orden de Miguel Ángel: *"el botón de ajustes, que es
            // fundamental, el de creación de base de datos. Un lanzador
            // que diga 'hacer diccionario' y empezar grupo por grupo."*
            //
            // Sin esto el diccionario solo crece cuando la Radio
            // tropieza con un artista, de tres en tres por vuelta. Aquí
            // se recorre de golpe: el cajón de sin red primero y la
            // biblioteca local después, que son los artistas que de
            // verdad se escuchan y por tanto los que más veces van a
            // anclar una sesión.
            SettingsAccordionSection(
                title = "Base de datos de la Radio",
                expanded = expandedSection == "diccionario",
                onToggle = {
                    expandedSection = if (expandedSection == "diccionario") null else "diccionario"
                },
            ) {
                Text(
                    "La Radio necesita saber de dónde es cada artista y qué " +
                        "género toca. Este recorrido pregunta a MusicBrainz género " +
                        "a género y guarda en la tarjeta todo lo que encuentra, sin " +
                        "depender de lo que tengas descargado. Tarda alrededor de " +
                        "media hora y se puede parar: al volver a pulsar sigue donde " +
                        "lo dejó.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                when (val st = dictionaryState) {
                    is SettingsViewModel.DictionaryState.Idle -> {
                        Text(
                            "Ya guardados: ${st.learned} artista(s). " +
                                "Géneros por recorrer: ${st.queued}." +
                                if (st.pending > 0) " Pendientes por falta de red: ${st.pending}." else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = viewModel::startBuildingDictionary,
                            enabled = st.queued > 0,
                            modifier = Modifier.glassChip(),
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Crear base de datos")
                        }
                        if (st.queued == 0) {
                            Text(
                                "Todos los géneros están recorridos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is SettingsViewModel.DictionaryState.Running -> {
                        LinearProgressIndicator(
                            progress = {
                                if (st.total > 0) st.done.toFloat() / st.total else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Género ${st.done} de ${st.total} — ${st.resolved} artistas " +
                                "guardados, ${st.notFound} descartados",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (st.currentArtist.isNotBlank()) {
                            Text(
                                st.currentArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        // Se puede parar cuando sea: lo resuelto está
                        // escrito en la tarjeta desde el momento en que
                        // se resolvió, no al final.
                        TextButton(
                            onClick = viewModel::stopBuildingDictionary,
                            modifier = Modifier.glassChip(),
                        ) {
                            Text("Parar")
                        }
                    }

                    is SettingsViewModel.DictionaryState.Done -> {
                        Text(st.message, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = viewModel::dismissDictionaryState,
                            modifier = Modifier.glassChip(),
                        ) {
                            Text("Aceptar")
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // H15 (miMooutCast), S032 -- BOTÓN "GENERAR BASE DE DATOS
            // DE MIMOOUTCAST", mismo patrón que el de arriba (H08).
            //
            // Orden de Miguel Ángel tras el fallo del script de
            // GitHub Actions (bloqueado por IP de centro de datos,
            // sin las rutinas ni las cookies reales del teléfono):
            // *"Vas a montar el script en la propia aplicación...
            // desde ahí lo lanzo yo, desde la propia aplicación,
            // utilizando las mismas rutinas de la propia aplicación.
            // Y esto lo vamos a hacer con todos y cada uno de los
            // géneros."*
            SettingsAccordionSection(
                title = "Base de datos de miMooutCast",
                expanded = expandedSection == "mimooutcast_bd",
                onToggle = {
                    expandedSection = if (expandedSection == "mimooutcast_bd") null else "mimooutcast_bd"
                },
            ) {
                Text(
                    "Recorre los 24 géneros de miMooutCast y busca, para cada " +
                        "uno, temas reales ya comprobados en YouTube -- con esto, " +
                        "elegir un género en miMooutCast encola de golpe en vez de " +
                        "tener que buscar en el momento. Tarda un buen rato (horas, " +
                        "no minutos) y se puede parar: al volver a pulsar sigue " +
                        "donde lo dejó. Cuando termines, pulsa \"Compartir\" y envíame " +
                        "el archivo para meterlo en la aplicación de forma " +
                        "permanente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                val mbp = mimooutcastBuildProgress
                when {
                    mbp.isRunning -> {
                        // H15 -- fix real, S033: la barra y el titular usaban
                        // `currentGenreIndex` (posición de recorrido de ESTA
                        // tanda, que arranca de cero en cada apertura de la
                        // app) -- Miguel Ángel, con datos reales: "3 géneros,
                        // 3520 en total... ¿de dónde salen 3520 temas de 2
                        // géneros?". No salían de 2 géneros -- salían de
                        // TODOS los géneros ya agotados en tandas anteriores,
                        // pero la pantalla no lo mostraba, así que parecía
                        // un dato roto. `genresCompleted` (`doneGenres.size`,
                        // persistente entre tandas) es el dato que de verdad
                        // responde "¿cuántos géneros llevo hechos" -- ahora
                        // manda tanto en la barra como en el titular.
                        LinearProgressIndicator(
                            progress = {
                                if (mbp.totalGenres > 0) mbp.genresCompleted.toFloat() / mbp.totalGenres else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${mbp.genresCompleted} de ${mbp.totalGenres} géneros completados -- " +
                                "${mbp.totalTracksFound} temas en total",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Mirando ahora: ${mbp.currentGenreLabel} (posición ${mbp.currentGenreIndex + 1} " +
                                "del recorrido) -- ${mbp.tracksFoundThisGenre} temas de este género",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (mbp.lastError != null) {
                            Text(
                                "Último fallo (sigue intentando): ${mbp.lastError}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        // Se puede parar cuando sea: cada género se
                        // guarda al terminar, no solo al final.
                        TextButton(
                            onClick = viewModel::stopBuildingMimooutcastDatabase,
                            modifier = Modifier.glassChip(),
                        ) {
                            Text("Parar")
                        }
                    }

                    mbp.finished || mbp.totalTracksFound > 0 -> {
                        Text(
                            if (mbp.finished) {
                                "Terminado. ${mbp.totalTracksFound} temas guardados en total."
                            } else {
                                "Parado. ${mbp.totalTracksFound} temas guardados antes de parar; " +
                                    "al volver a pulsar sigue donde lo dejó."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            TextButton(
                                onClick = viewModel::startBuildingMimooutcastDatabase,
                                modifier = Modifier.glassChip(),
                            ) {
                                Text(if (mbp.finished) "Volver a generar" else "Continuar")
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = viewModel::onShareMimooutcastDatabaseClicked,
                                modifier = Modifier.glassChip(),
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Compartir")
                            }
                        }
                    }

                    else -> {
                        TextButton(
                            onClick = viewModel::startBuildingMimooutcastDatabase,
                            modifier = Modifier.glassChip(),
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generar base de datos de miMooutCast")
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "Generar semilla de década de miMooutCast",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Recorre el diccionario de éxitos entero (8 décadas, España + " +
                        "internacional) y valida cada tema contra YouTube -- con esto, " +
                        "elegir una década sola en miMooutCast arranca al instante, " +
                        "igual que ya lo hace un género. Tarda un buen rato (horas) y " +
                        "se puede parar: al volver a pulsar sigue donde lo dejó. Cuando " +
                        "termines, pulsa \"Compartir\" y envíame el archivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                val mdbp = mimooutcastDecadeBuildProgress
                when {
                    mdbp.isRunning -> {
                        LinearProgressIndicator(
                            progress = {
                                if (mdbp.totalDecades > 0) mdbp.decadesCompleted.toFloat() / mdbp.totalDecades else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${mdbp.decadesCompleted} de ${mdbp.totalDecades} décadas completadas -- " +
                                "${mdbp.totalEntriesFound} temas validados en total",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Mirando ahora: años ${mdbp.currentDecadeLabel} (posición " +
                                "${mdbp.currentDecadeIndex + 1} del recorrido) -- " +
                                "${mdbp.entriesFoundThisDecade} temas de esta década",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (mdbp.lastError != null) {
                            Text(
                                "Último fallo (sigue intentando): ${mdbp.lastError}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = viewModel::stopBuildingMimooutcastDecadeDatabase,
                            modifier = Modifier.glassChip(),
                        ) {
                            Text("Parar")
                        }
                    }

                    mdbp.finished || mdbp.totalEntriesFound > 0 -> {
                        Text(
                            if (mdbp.finished) {
                                "Terminado. ${mdbp.totalEntriesFound} temas guardados en total."
                            } else {
                                "Parado. ${mdbp.totalEntriesFound} temas guardados antes de parar; " +
                                    "al volver a pulsar sigue donde lo dejó."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            TextButton(
                                onClick = viewModel::startBuildingMimooutcastDecadeDatabase,
                                modifier = Modifier.glassChip(),
                            ) {
                                Text(if (mdbp.finished) "Volver a generar" else "Continuar")
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = viewModel::onShareMimooutcastDecadeDatabaseClicked,
                                modifier = Modifier.glassChip(),
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Compartir")
                            }
                        }
                    }

                    else -> {
                        TextButton(
                            onClick = viewModel::startBuildingMimooutcastDecadeDatabase,
                            modifier = Modifier.glassChip(),
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generar semilla de década de miMooutCast")
                        }
                    }
                }

                // S034 -- enlaces de la semilla bundleada que dejan de
                // funcionar en uso real (vídeo caído/retirado), con su
                // sustituto cuando ya se encontró uno. Orden de Miguel
                // Ángel: *"se pone un contador de aviso de que se
                // necesita restaurar la instalación cuando el contador
                // de links rotos llegue a 10 en un género."*
                val genresNeedingReinstall = viewModel.mimooutcastGenresNeedingReinstall()
                if (genresNeedingReinstall.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Estos géneros tienen 10 o más enlaces rotos en la lista de fábrica -- " +
                            "conviene actualizar la instalación pronto: " +
                            genresNeedingReinstall.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = viewModel::onShareMimooutcastBrokenLinksClicked,
                    modifier = Modifier.glassChip(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Compartir enlaces rotos")
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

            // ─────────────────────────────────────────────────────
            // S021 -- Carpeta de la biblioteca configurable.
            // Petición de Miguel Ángel registrada en S020
            // (DOCS/RESUMPTION_POINT.md) y concretada en S021: poder
            // llevarse la biblioteca a una tarjeta externa, con todo
            // el audio, sin perder favoritos, listas ni canales.
            //
            // Dos ramas deliberadamente distintas, tal como las pidió:
            // mover todo el audio, o cambiar solo el ajuste dejando lo
            // ya descargado donde está. La segunda NO rompe nada
            // porque filePath es un Uri absoluto y el permiso de la
            // raíz anterior no se libera (ver StorageManager).
            // ─────────────────────────────────────────────────────
            val libraryFolderLabel by viewModel.libraryFolderLabel.collectAsState()
            val libraryFolderState by viewModel.libraryFolderState.collectAsState()
            var pendingNewLibraryRoot by remember {
                mutableStateOf<android.net.Uri?>(null)
            }
            val libraryFolderPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree(),
            ) { uri ->
                if (uri != null) pendingNewLibraryRoot = uri
            }

            SettingsAccordionSection(
                title = "Almacenamiento",
                expanded = expandedSection == "almacenamiento",
                onToggle = {
                    expandedSection =
                        if (expandedSection == "almacenamiento") null else "almacenamiento"
                },
            ) {
                Text(
                    "Carpeta actual: ${libraryFolderLabel ?: "sin elegir todavía"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Aquí se guardan las canciones descargadas, organizadas en " +
                        "{Artista}/{Álbum}. Puedes moverla a una tarjeta externa " +
                        "sin perder favoritos, listas ni canales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { libraryFolderPicker.launch(null) },
                    modifier = Modifier.glassChip(),
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cambiar carpeta de la biblioteca")
                }
            }

            // Elegida ya la carpeta nueva: falta decidir qué hacer con
            // lo que ya está descargado. Las dos opciones van en el
            // cuerpo del diálogo, no como botones de acción, porque un
            // AlertDialog de Material 3 pone los botones en fila y con
            // tres no caben legibles en pantalla de móvil.
            pendingNewLibraryRoot?.let { newRoot ->
                AlertDialog(
                    onDismissRequest = { pendingNewLibraryRoot = null },
                    title = { Text("Cambiar carpeta de la biblioteca") },
                    text = {
                        Column {
                            Text(
                                "¿Qué hacemos con la música que ya tienes descargada?",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(
                                onClick = {
                                    viewModel.changeLibraryFolder(newRoot, moveFiles = true)
                                    pendingNewLibraryRoot = null
                                },
                                modifier = Modifier.fillMaxWidth().glassChip(),
                            ) {
                                Text("Mover toda la biblioteca a la carpeta nueva")
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    viewModel.changeLibraryFolder(newRoot, moveFiles = false)
                                    pendingNewLibraryRoot = null
                                },
                                modifier = Modifier.fillMaxWidth().glassChip(),
                            ) {
                                Text("Solo cambiar la carpeta (dejar lo descargado donde está)")
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "En los dos casos se conservan favoritos, listas de " +
                                    "reproducción, canales, carátulas y el resto de datos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { pendingNewLibraryRoot = null }) {
                            Text("Cancelar")
                        }
                    },
                )
            }

            when (val folderState = libraryFolderState) {
                is SettingsViewModel.LibraryFolderState.Idle -> Unit

                is SettingsViewModel.LibraryFolderState.Migrating -> AlertDialog(
                    // Sin onDismissRequest efectivo: mover archivos a
                    // medias y volver a tocar la app sería la vía más
                    // fácil de dejar la biblioteca en un estado raro.
                    onDismissRequest = {},
                    title = { Text("Moviendo la biblioteca") },
                    text = {
                        Column {
                            if (folderState.total > 0) {
                                LinearProgressIndicator(
                                    progress = {
                                        folderState.done.toFloat() / folderState.total
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("${folderState.done} de ${folderState.total} canciones")
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                Text("Preparando…")
                            }
                            if (folderState.failed > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${folderState.failed} no se han podido mover",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No cierres la app hasta que termine.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    confirmButton = {},
                )

                is SettingsViewModel.LibraryFolderState.Done -> AlertDialog(
                    onDismissRequest = viewModel::dismissLibraryFolderState,
                    title = { Text("Carpeta cambiada") },
                    text = {
                        // Lista explícita de lo que no se pudo mover.
                        // Hasta S022 aquí solo salía el número, y con
                        // 8 fallos sobre 700 y pico eso no permitía ni
                        // saber qué canciones eran ni por qué fallaban.
                        Column(
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                buildString {
                                    append("La biblioteca apunta ahora a ")
                                    append(folderState.folderLabel ?: "la carpeta elegida")
                                    append(".")
                                    if (folderState.movedFiles) {
                                        append("\n\nCanciones movidas: ${folderState.migrated}.")
                                    } else {
                                        append(
                                            "\n\nLo que ya estaba descargado sigue en la " +
                                                "carpeta anterior y se reproduce con " +
                                                "normalidad. Las descargas nuevas irán a la " +
                                                "carpeta nueva.",
                                        )
                                    }
                                },
                            )

                            if (folderState.movedFiles && folderState.failures.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "No se pudieron mover ${folderState.failures.size}, " +
                                        "que siguen sonando desde la carpeta anterior:",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(8.dp))
                                folderState.failures.forEach { failure ->
                                    Text(
                                        "• ${failure.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        "   ${failure.reasonText}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "El detalle completo, con la ruta de cada una, queda " +
                                        "en «traslado_biblioteca_informe.txt», en la raíz " +
                                        "de la carpeta nueva.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = viewModel::dismissLibraryFolderState) {
                            Text("Entendido")
                        }
                    },
                )

                is SettingsViewModel.LibraryFolderState.Error -> AlertDialog(
                    onDismissRequest = viewModel::dismissLibraryFolderState,
                    title = { Text("No se pudo cambiar la carpeta") },
                    text = { Text(folderState.message) },
                    confirmButton = {
                        TextButton(onClick = viewModel::dismissLibraryFolderState) {
                            Text("Cerrar")
                        }
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            // S027 -- REDISEÑO COMPLETO, orden textual de Miguel Ángel
            // tras el desastre de AC/DC: ya no es un % que se reparte
            // cuando una porción se agota -- es un recuento FIJO por
            // cada bloque de 10 canciones (ver el kdoc de
            // `radioRoundKnownCount` en PlayerManager.kt). Dos
            // steppers (conocidos, disco) más un texto derivado para
            // desconocidos -- mismo principio que antes: nunca un
            // tercer control para desconocidos, así la suma nunca
            // puede superar 10 desde la propia UI.
            val radioKnownQuota by viewModel.radioKnownQuotaPerTen.collectAsState()
            val radioDiscoQuota by viewModel.radioDiscoQuotaPerTen.collectAsState()
            val radioUnknownQuota = 10 - radioKnownQuota - radioDiscoQuota
            SettingsAccordionSection(
                title = "Radio",
                expanded = expandedSection == "radio",
                onToggle = {
                    expandedSection = if (expandedSection == "radio") null else "radio"
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassChip()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        "De cada 10 canciones de Radio (música relacionada): " +
                            "$radioKnownQuota conocidas en España · $radioDiscoQuota de tu biblioteca · " +
                            "$radioUnknownQuota desconocidas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))

                    Text("Conocidas en España (de cada 10): $radioKnownQuota")
                    Slider(
                        value = radioKnownQuota.toFloat(),
                        onValueChange = { viewModel.setRadioKnownQuotaPerTen(it.toInt()) },
                        valueRange = 0f..10f,
                        steps = 9,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text("De tu biblioteca ya descargada (de cada 10): $radioDiscoQuota")
                    Slider(
                        value = radioDiscoQuota.toFloat(),
                        onValueChange = { viewModel.setRadioDiscoQuotaPerTen(it.toInt()) },
                        valueRange = 0f..10f,
                        steps = 9,
                    )

                    Spacer(Modifier.height(12.dp))

                    // S027 -- ventana de años del ancla, orden textual
                    // de Miguel Ángel tras el caso PISTONES (España,
                    // new wave, 1984): con ±5 casi todo lo que YouTube
                    // devuelve de cada candidato queda fuera, porque no
                    // siempre es justo de esa época. Por defecto 10;
                    // configurable a 5 para anclas extranjeras, donde
                    // suele haber mucho más donde elegir.
                    val radioYearWindow by viewModel.radioYearWindow.collectAsState()
                    Text(
                        "Cuántos años, hacia delante y hacia atrás, puede alejarse un tema del año " +
                            "del que arrancó la Radio: ±$radioYearWindow",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(5, 10).forEach { years ->
                            FilterChip(
                                selected = radioYearWindow == years,
                                onClick = { viewModel.setRadioYearWindow(years) },
                                label = { Text("±$years años") },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // S026 -- umbral de GenreMatchQuality, petición
                    // explícita de Miguel Ángel: "configurable en
                    // ajustes, con escalones de diez". Por defecto
                    // 40% -- ver GenreMatchQuality.kt para el porqué,
                    // verificado contra datos reales antes de fijarlo.
                    val genreMatchThresholdPercent by viewModel.radioGenreMatchThresholdPercent.collectAsState()
                    Text(
                        "Cuánto tiene que parecerse un artista, por géneros, para entrar en la " +
                            "Radio: $genreMatchThresholdPercent%",
                    )
                    Slider(
                        value = genreMatchThresholdPercent.toFloat(),
                        onValueChange = { viewModel.setRadioGenreMatchThresholdPercent(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 9,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 2026-08-24 -- refuerzo de volumen (LoudnessEnhancer, ver
            // AudioNormalizer.kt). Petición explícita de Miguel Ángel:
            // "podemos ponerlo como control en settings?" -- antes era
            // una constante fija en código (+6dB). En decibelios para
            // el usuario (más intuitivo que milibelios); internamente
            // se guarda/aplica en milibelios (100mB = 1dB). Tope de 12dB
            // -- pasado ese punto la propia documentación de la API
            // avisa de compresión/distorsión constante en casi
            // cualquier tema, verificado antes de fijar el tope. Se
            // aplica en caliente, con la música sonando, sin
            // reiniciar nada.
            val volumeBoostMillibels by viewModel.volumeBoostMillibels.collectAsState()
            SettingsAccordionSection(
                title = "Audio",
                expanded = expandedSection == "audio",
                onToggle = {
                    expandedSection = if (expandedSection == "audio") null else "audio"
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassChip()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        "Refuerzo de volumen: ${if (volumeBoostMillibels == 0) "sin refuerzo" else "+${volumeBoostMillibels / 100}dB"}",
                    )
                    Slider(
                        value = volumeBoostMillibels.toFloat(),
                        onValueChange = { viewModel.setVolumeBoostMillibels(it.toInt()) },
                        valueRange = 0f..1200f,
                        steps = 11,
                    )
                    Text(
                        "Pasado unos 10-12dB puede notarse comprimido o forzado en algunos temas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Fix real (2026-07-24, debug_error.txt de Miguel Ángel):
            // cookies de YouTube para que yt-dlp pueda descargar
            // vídeos restringidos por edad ("Sign in to confirm your
            // age") -- ver CookiesManager.kt. hasCookies refleja en
            // vivo si ya hay un cookies.txt importado EN ESTE
            // dispositivo -- nunca se sincroniza vía Drive (§4.6).
            val hasCookies by viewModel.hasCookies.collectAsState()
            SettingsAccordionSection(
                title = "YouTube",
                expanded = expandedSection == "youtube",
                onToggle = {
                    expandedSection = if (expandedSection == "youtube") null else "youtube"
                },
            ) {
                Text(
                    "Algunos vídeos de YouTube están restringidos por edad y " +
                        "piden una cuenta verificada para descargarse. Exporta " +
                        "el cookies.txt (formato Netscape) de un navegador " +
                        "logueado en tu cuenta de YouTube e impórtalo aquí en " +
                        "un dispositivo -- se sincroniza automáticamente al " +
                        "resto por el mismo canal privado de Drive que ya usa " +
                        "la biblioteca (nunca por un enlace o código de " +
                        "compartición).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (hasCookies) "Cookies importadas en este dispositivo." else "Sin cookies importadas.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = { cookiesImportLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.glassChip(),
                ) {
                    Icon(Icons.Filled.VpnKey, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (hasCookies) "Reemplazar cookies.txt" else "Importar cookies.txt")
                }
                if (hasCookies) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { viewModel.clearCookies(activity) },
                        modifier = Modifier.glassChip(),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Eliminar cookies")
                    }
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

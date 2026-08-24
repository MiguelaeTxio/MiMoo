package com.miguelaetxio.mimoo.ui.settings

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.AutoSyncPusher
import com.miguelaetxio.mimoo.data.backup.BackupDebugLogger
import com.miguelaetxio.mimoo.data.backup.BackupDriveRepository
import com.miguelaetxio.mimoo.data.backup.BackupImportRepository
import com.miguelaetxio.mimoo.data.backup.BackupRepository
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationHelper
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationOutcome
import com.miguelaetxio.mimoo.data.backup.DriveBackupFile
import com.miguelaetxio.mimoo.data.download.CookiesManager
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.share.ShareCodeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MiMoo-Backup-VM"

/**
 * Estado de la pantalla Ajustes en lo relativo a H06. Un único
 * UiState (no dos, uno para exportar y otro para importar) porque
 * ambas operaciones comparten el mismo flujo de autorización y nunca
 * se hacen a la vez -- ver PendingAction.
 * ---
 * Settings screen state for H06. A single UiState (not two, one for
 * export and one for import) because both operations share the same
 * authorization flow and are never in flight at the same time -- see
 * PendingAction.
 */
sealed class BackupUiState {
    object Idle : BackupUiState()
    object Working : BackupUiState()
    data class ExportSuccess(val fileName: String) : BackupUiState()
    /** Backups disponibles en Drive, listados tras pulsar "Importar" -- la UI muestra esta lista para elegir uno. */
    data class BackupsListed(val backups: List<DriveBackupFile>) : BackupUiState()
    data class ImportSuccess(val trackCount: Int) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

/**
 * ViewModel de la pantalla Ajustes (H06 PASO 3: exportación; PASO 4
 * añadirá la importación reutilizando el mismo mecanismo de
 * autorización). Coordina DriveAuthorizationHelper (auth),
 * BackupRepository (construir/serializar el bundle) y
 * BackupDriveRepository (hablar con Drive).
 *
 * El ViewModel nunca guarda una Activity más allá de la llamada que
 * la recibe -- cada método que la necesita la toma como parámetro
 * desde el Composable (`LocalContext.current as Activity`), nunca
 * como campo de la clase, para no arriesgar una fuga de memoria.
 * ---
 * Settings screen's ViewModel (H06 PASO 3: export; PASO 4 will add
 * import reusing the same authorization mechanism). Coordinates
 * DriveAuthorizationHelper (auth), BackupRepository (build/serialize
 * the bundle) and BackupDriveRepository (talk to Drive).
 *
 * The ViewModel never holds an Activity beyond the call that receives
 * it -- every method that needs one takes it as a parameter from the
 * Composable (`LocalContext.current as Activity`), never as a class
 * field, to avoid risking a memory leak.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val driveRepository: BackupDriveRepository,
    private val authorizationHelper: DriveAuthorizationHelper,
    private val importRepository: BackupImportRepository,
    private val downloadQueueManager: DownloadQueueManager,
    private val storageManager: StorageManager,
    private val shareCodeRepository: ShareCodeRepository,
    private val uiPreferencesManager: com.miguelaetxio.mimoo.data.access.UiPreferencesManager,
    private val cookiesManager: CookiesManager,
    private val autoSyncPusher: AutoSyncPusher,
    private val libraryMigrator: com.miguelaetxio.mimoo.data.library.LibraryMigrator,
    // S025 -- constructor del diccionario del ancla (H08), lanzado
    // desde el botón "Crear base de datos".
    private val anchorDictionaryBuilder: com.miguelaetxio.mimoo.data.remote.AnchorDictionaryBuilder,
    private val anchorDictionary: com.miguelaetxio.mimoo.data.remote.AnchorDictionary,
    // H15 (miMooutCast), S032 -- constructor del recopilatorio de
    // enlaces validados para TODOS los géneros, lanzado desde el
    // botón "Generar base de datos de miMooutCast". Orden explícita
    // de Miguel Ángel tras el fallo del script de GitHub Actions
    // (sin las rutinas ni las cookies reales del teléfono): montarlo
    // en la propia app, mismo patrón que `anchorDictionaryBuilder`.
    private val mimooutcastDatabaseBuilder: com.miguelaetxio.mimoo.data.playback.MimooutcastDatabaseBuilder,
    // S034 -- registro de enlaces rotos de la semilla bundleada, ver su kdoc completo.
    private val mimooutcastBrokenLinksLogger: com.miguelaetxio.mimoo.data.playback.MimooutcastBrokenLinksLogger,
) : ViewModel() {

    // -----------------------------------------------------------------
    // S025 -- Crear base de datos del ancla
    // -----------------------------------------------------------------

    /**
     * Estado del recorrido que construye el diccionario. `Idle` lleva
     * el recuento de lo que ya hay y de lo que queda, para que el botón
     * no sea un salto al vacío.
     */
    sealed interface DictionaryState {
        data class Idle(val learned: Int, val pending: Int, val queued: Int) : DictionaryState
        data class Running(
            val done: Int,
            val total: Int,
            val resolved: Int,
            val notFound: Int,
            val currentArtist: String,
        ) : DictionaryState
        data class Done(val message: String) : DictionaryState
    }

    private val _dictionaryState =
        MutableStateFlow<DictionaryState>(DictionaryState.Idle(0, 0, 0))
    val dictionaryState: StateFlow<DictionaryState> = _dictionaryState.asStateFlow()

    private var dictionaryJob: kotlinx.coroutines.Job? = null

    fun refreshDictionaryCounts() {
        // S025 -- Dispatchers.IO, no el hilo principal.
        //
        // Fallo reportado por Miguel Ángel: *"cuando entro a ajustes
        // tarda muchísimo y termina crasheando, está lento el menú de
        // la sidebar."* La causa era esto: `viewModelScope.launch` sin
        // dispatcher corre en Main, y aquí dentro se lee la tarjeta por
        // SAF y se parsean las 1.161 entradas de la semilla. Con eso el
        // hilo de interfaz se queda bloqueado el tiempo que tarde la
        // tarjeta en responder, y Android acaba matando la app por ANR
        // -- que además no deja log de excepción, justo lo que él
        // describía.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (_dictionaryState.value is DictionaryState.Running) return@launch
            val queued = runCatching { anchorDictionaryBuilder.pendingWork() }.getOrDefault(0)
            _dictionaryState.value = DictionaryState.Idle(
                learned = anchorDictionary.learnedArtistCount(),
                pending = anchorDictionary.pendingArtistCount(),
                queued = queued,
            )
        }
    }

    fun startBuildingDictionary() {
        if (dictionaryJob?.isActive == true) return
        // S025 -- el recorrido es media hora de red y escritura en
        // tarjeta: jamás en el hilo principal.
        dictionaryJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = try {
                anchorDictionaryBuilder.build { p ->
                _dictionaryState.value = DictionaryState.Running(
                    done = p.done,
                    total = p.total,
                    resolved = p.resolved,
                    notFound = p.notFound,
                    currentArtist = p.currentArtist,
                )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelado desde el botón Parar: el estado ya lo ha
                // puesto `stopBuildingDictionary()`. Lo guardado hasta
                // aquí está escrito en la tarjeta.
                throw e
            }
            _dictionaryState.value = DictionaryState.Done(
                when (result) {
                    is com.miguelaetxio.mimoo.data.remote.AnchorDictionaryBuilder.Result.Finished ->
                        "Terminado. ${result.resolved} artista(s) en el diccionario, " +
                            "${result.notFound} descartados por parecer canales." +
                            if (result.renamedFolders > 0) {
                                " ${result.renamedFolders} carpeta(s) renombradas al nombre del artista."
                            } else {
                                ""
                            }
                    is com.miguelaetxio.mimoo.data.remote.AnchorDictionaryBuilder.Result.Stopped ->
                        "Parado. ${result.resolved} artista(s) añadidos antes de parar; " +
                            "lo hecho ya está guardado."
                    is com.miguelaetxio.mimoo.data.remote.AnchorDictionaryBuilder.Result.NetworkDown ->
                        "Sin conexión con MusicBrainz. ${result.resolved} artista(s) añadidos " +
                            "antes de cortarse; el resto queda para el próximo intento."
                },
            )
        }
    }

    /**
     * S025 -- parar de verdad.
     *
     * Fallo reportado por Miguel Ángel: *"el botón parar tampoco sirve
     * para nada"*. Y no servía: `cancel()` mataba la corrutina, pero la
     * línea que pone el estado final estaba DENTRO de esa misma
     * corrutina, así que nunca llegaba a ejecutarse. La pantalla se
     * quedaba congelada mostrando el progreso y el botón Parar para
     * siempre, aunque por debajo ya no hubiera nada corriendo.
     *
     * Ahora el estado se pone aquí, fuera de la corrutina cancelada, y
     * el aviso es inmediato.
     */
    fun stopBuildingDictionary() {
        val job = dictionaryJob
        dictionaryJob = null
        job?.cancel()
        val current = _dictionaryState.value
        val saved = (current as? DictionaryState.Running)?.resolved ?: 0
        _dictionaryState.value = DictionaryState.Done(
            "Parado. $saved artista(s) guardados antes de parar; " +
                "al volver a pulsar sigue donde lo dejó.",
        )
    }

    fun dismissDictionaryState() {
        refreshDictionaryCounts()
    }

    // -----------------------------------------------------------------
    // H15 (miMooutCast), S032 -- Generar base de datos de miMooutCast
    // (todos los géneros, enlaces ya validados)
    // -----------------------------------------------------------------

    val mimooutcastBuildProgress: StateFlow<com.miguelaetxio.mimoo.data.playback.MimooutcastDatabaseBuilder.BuildProgress> =
        mimooutcastDatabaseBuilder.progress

    private var mimooutcastBuildJob: kotlinx.coroutines.Job? = null

    fun startBuildingMimooutcastDatabase() {
        if (mimooutcastBuildJob?.isActive == true) return
        // H15, S032 -- horas de red y escritura, mismo motivo que
        // startBuildingDictionary(): jamás en el hilo principal.
        mimooutcastBuildJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            mimooutcastDatabaseBuilder.build()
        }
    }

    /**
     * H15, S032 -- CORREGIDO antes de que pasara: mismo bug ya
     * documentado en `stopBuildingDictionary()` -- cancelar el `Job`
     * directamente (`job.cancel()`) lanza `CancellationException` en
     * el siguiente punto de suspensión DENTRO de `build()`, y la
     * línea final que pone `isRunning = false` nunca llega a
     * ejecutarse porque está en esa misma corrutina cancelada. Aquí se
     * usa en su lugar `mimooutcastDatabaseBuilder.cancel()` -- una
     * bandera interna que el propio bucle de `build()` comprueba y
     * respeta, dejando que termine su iteración en curso y llegue de
     * verdad a su propia línea final de estado.
     */
    fun stopBuildingMimooutcastDatabase() {
        mimooutcastDatabaseBuilder.cancel()
    }

    fun mimooutcastDatabaseFilePath(): String = mimooutcastDatabaseBuilder.outputFilePath()

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    /** S011 -- interruptor de borde del cristal ("añade un toggle en ajustes para cambiar de borde a sin borde"). */
    val glassBorderEnabled: StateFlow<Boolean> = uiPreferencesManager.glassBorderEnabled

    // ─────────────────────────────────────────────────────────────
    // S021 -- Carpeta de la biblioteca configurable
    // ─────────────────────────────────────────────────────────────

    /**
     * Estado de un cambio de carpeta de biblioteca. Petición de Miguel
     * Ángel (S020, registrada en `DOCS/RESUMPTION_POINT.md`; caso de
     * uso concreto dictado en S021: mover la biblioteca a una tarjeta
     * externa): elegir una carpeta nueva y decidir si se lleva allí
     * todo el audio ya descargado o si solo cambia el ajuste para las
     * descargas futuras. En los dos casos, sin perder favoritos,
     * listas, canales ni metadatos -- ver `LibraryMigrator` para por
     * qué migrar `filePath` es condición suficiente.
     */
    sealed interface LibraryFolderState {
        data object Idle : LibraryFolderState

        /** Migración en curso. `total` 0 mientras se cuenta. */
        data class Migrating(val done: Int, val total: Int, val failed: Int) : LibraryFolderState

        /**
         * Terminado. `movedFiles = false` cuando se eligió cambiar
         * solo el ajuste, en cuyo caso `migrated`/`failed` son 0 y el
         * audio anterior sigue donde estaba, perfectamente
         * reproducible.
         */
        data class Done(
            val migrated: Int,
            val failed: Int,
            val movedFiles: Boolean,
            val folderLabel: String?,
            /**
             * Qué pistas fallaron y por qué. Añadido en S022: la
             * primera prueba real dejó 8 de 700 y pico sin mover, y el
             * resumen solo daba el número -- *"no sé qué canciones
             * son"*. Ahora el diálogo las lista con nombre y causa.
             */
            val failures: List<com.miguelaetxio.mimoo.data.library.LibraryMigrator.Failure> =
                emptyList(),
        ) : LibraryFolderState

        data class Error(val message: String) : LibraryFolderState
    }

    private val _libraryFolderLabel = MutableStateFlow(storageManager.getRootLabel())

    /** Nombre legible de la carpeta actual, o null si no hay ninguna elegida. */
    val libraryFolderLabel: StateFlow<String?> = _libraryFolderLabel.asStateFlow()

    private val _libraryFolderState =
        MutableStateFlow<LibraryFolderState>(LibraryFolderState.Idle)
    val libraryFolderState: StateFlow<LibraryFolderState> = _libraryFolderState.asStateFlow()

    /**
     * Aplica la carpeta nueva elegida en el selector del sistema.
     *
     * **La raíz se guarda SIEMPRE primero**, antes de mover un solo
     * byte, y en las dos ramas. Así el ajuste queda aplicado aunque la
     * migración se interrumpa a mitad: las descargas nuevas van ya a
     * la carpeta nueva y las pistas que no llegaron a moverse siguen
     * sonando desde la vieja, porque su `filePath` es un Uri absoluto
     * y el permiso de la raíz anterior no se libera nunca (ver
     * `StorageManager.persistedRootCount()`).
     *
     * @param moveFiles true -> llevar allí todo el audio ya descargado;
     *                  false -> solo cambiar el ajuste.
     */
    fun changeLibraryFolder(newRootUri: android.net.Uri, moveFiles: Boolean) {
        viewModelScope.launch {
            // S022 -- se recuerda la raíz anterior para poder deshacer
            // el ajuste si un traslado atómico aborta. Sin esto, un
            // aborto por falta de espacio dejaba el ajuste cambiado
            // mientras el mensaje afirmaba que no se había tocado nada:
            // las descargas nuevas habrían ido al destino y todo lo
            // anterior se habría quedado en el origen, que es justo el
            // estado a medias que la atomicidad debe evitar.
            val previousRootUri = storageManager.getRootUri()

            try {
                storageManager.saveRootUri(newRootUri)
            } catch (e: SecurityException) {
                _libraryFolderState.value = LibraryFolderState.Error(
                    "Android no ha concedido permiso permanente sobre esa " +
                        "carpeta. Elige otra o vuelve a intentarlo.",
                )
                return@launch
            }
            _libraryFolderLabel.value = storageManager.getRootLabel()

            if (!moveFiles) {
                _libraryFolderState.value = LibraryFolderState.Done(
                    migrated = 0,
                    failed = 0,
                    movedFiles = false,
                    folderLabel = _libraryFolderLabel.value,
                )
                return@launch
            }

            _libraryFolderState.value = LibraryFolderState.Migrating(0, 0, 0)
            val result = libraryMigrator.migrateTo(newRootUri) { progress ->
                _libraryFolderState.value = LibraryFolderState.Migrating(
                    done = progress.done,
                    total = progress.total,
                    failed = progress.failed,
                )
            }
            _libraryFolderState.value = when (result) {
                is com.miguelaetxio.mimoo.data.library.LibraryMigrator.Result.Completed ->
                    LibraryFolderState.Done(
                        migrated = result.migrated,
                        failed = result.failed,
                        movedFiles = true,
                        folderLabel = _libraryFolderLabel.value,
                        failures = result.failures,
                    )

                is com.miguelaetxio.mimoo.data.library.LibraryMigrator.Result.Aborted -> {
                    // El traslado no ha ocurrido, así que el ajuste
                    // tampoco debe haber ocurrido.
                    previousRootUri?.let {
                        runCatching { storageManager.saveRootUri(it) }
                    }
                    _libraryFolderLabel.value = storageManager.getRootLabel()
                    LibraryFolderState.Error(result.reason)
                }
            }
        }
    }

    fun dismissLibraryFolderState() {
        _libraryFolderState.value = LibraryFolderState.Idle
    }

    fun setGlassBorderEnabled(enabled: Boolean) {
        uiPreferencesManager.setGlassBorderEnabled(enabled)
    }

    /**
     * S027 -- REDISEÑO COMPLETO del cupo de Radio (H08), sustituye por
     * completo al reparto por porcentajes de S016. Ver el kdoc de
     * `PlayerManager.radioRoundKnownCount` para el porqué.
     * `radioKnownQuotaPerTen`/`radioDiscoQuotaPerTen` son los dos que
     * se guardan de verdad (`UiPreferencesManager`); desconocidos se
     * deriva siempre en la propia UI (10 - los otros dos), nunca puede
     * desincronizarse.
     */
    val radioKnownQuotaPerTen: StateFlow<Int> = uiPreferencesManager.radioKnownQuotaPerTen
    val radioDiscoQuotaPerTen: StateFlow<Int> = uiPreferencesManager.radioDiscoQuotaPerTen

    fun setRadioKnownQuotaPerTen(quota: Int) {
        uiPreferencesManager.setRadioKnownQuotaPerTen(quota)
    }

    fun setRadioDiscoQuotaPerTen(quota: Int) {
        uiPreferencesManager.setRadioDiscoQuotaPerTen(quota)
    }

    /** S027 -- ver el kdoc de UiPreferencesManager.KEY_RADIO_YEAR_WINDOW. */
    val radioYearWindow: StateFlow<Int> = uiPreferencesManager.radioYearWindow

    fun setRadioYearWindow(years: Int) {
        uiPreferencesManager.setRadioYearWindow(years)
    }

    /**
     * S026 -- umbral de coincidencia de género de la Radio (%
     * mínimo de intersección/unión de géneros específicos, ver
     * `GenreMatchQuality`). Petición explícita de Miguel Ángel:
     * configurable en Ajustes, en escalones de 10.
     */
    val radioGenreMatchThresholdPercent: StateFlow<Int> = uiPreferencesManager.radioGenreMatchThresholdPercent

    fun setRadioGenreMatchThresholdPercent(percent: Int) {
        uiPreferencesManager.setRadioGenreMatchThresholdPercent(percent)
    }

    /**
     * 2026-08-24 -- refuerzo de volumen (`LoudnessEnhancer`, ver
     * `AudioNormalizer.kt`). Petición explícita de Miguel Ángel:
     * "podemos ponerlo como control en settings?". En milibelios
     * (100mB = 1dB); se cambia en caliente, sin reiniciar la
     * reproducción.
     */
    val volumeBoostMillibels: StateFlow<Int> = uiPreferencesManager.volumeBoostMillibels

    fun setVolumeBoostMillibels(millibels: Int) {
        uiPreferencesManager.setVolumeBoostMillibels(millibels)
    }

    /**
     * Fix real (2026-07-24, `debug_error.txt` de Miguel Ángel):
     * cookies de YouTube para que yt-dlp pueda descargar vídeos
     * restringidos por edad ("Sign in to confirm your age") -- ver
     * `CookiesManager.kt`. `hasCookies` refleja en vivo si ya hay un
     * cookies.txt importado en este dispositivo; `cookiesImportMessage`
     * es el mensaje puntual (éxito O error) tras cada intento de
     * importar/eliminar -- fix real (2026-07-24, queja explícita de
     * Miguel Ángel: "no dice absolutamente nada" tras importar): antes
     * solo había mensaje en el caso de error, así que un éxito real no
     * daba ninguna confirmación visible.
     */
    val hasCookies: StateFlow<Boolean> = cookiesManager.hasCookies

    private val _cookiesImportMessage = MutableStateFlow<String?>(null)
    val cookiesImportMessage: StateFlow<String?> = _cookiesImportMessage.asStateFlow()

    /**
     * Fix real (2026-07-24, petición explícita de Miguel Ángel: "que
     * mi mujer no tenga que importar nada") -- tras guardar el
     * cookies.txt localmente, empuja el estado a Drive de inmediato
     * (mismo mecanismo que cualquier otra mutación, `AutoSyncPusher`)
     * en vez de esperar a la siguiente descarga/favorito para que el
     * envelope se actualice. Mutación vacía a propósito: lo único que
     * cambió ya lo recoge `CookiesManager.currentContentOrNull()`
     * dentro del propio push -- ver `AutoSyncPusher.pushCurrentState()`.
     * ---
     * Real fix (2026-07-24, explicit request from Miguel Ángel: "so my
     * wife doesn't have to import anything") -- after saving
     * cookies.txt locally, pushes the state to Drive immediately (same
     * mechanism as any other mutation, `AutoSyncPusher`) instead of
     * waiting for the next download/favorite for the envelope to
     * update. Empty mutation on purpose: the only thing that changed
     * is already picked up by `CookiesManager.currentContentOrNull()`
     * inside the push itself -- see `AutoSyncPusher.pushCurrentState()`.
     */
    fun importCookies(activity: Activity, content: String) {
        val success = cookiesManager.importCookies(content)
        _cookiesImportMessage.value = if (success) {
            "Cookies importadas: ${content.length} caracteres guardados. " +
                cookiesManager.diagnosticsSummary()
        } else {
            "El archivo elegido no parece un cookies.txt de YouTube válido -- nada guardado."
        }
        if (success) {
            viewModelScope.launch { autoSyncPusher.executeIfConnected(activity) { } }
        }
    }

    fun clearCookiesImportMessage() {
        _cookiesImportMessage.value = null
    }

    fun clearCookies(activity: Activity) {
        cookiesManager.clearCookies()
        _cookiesImportMessage.value = "Cookies eliminadas de este dispositivo."
        viewModelScope.launch { autoSyncPusher.executeIfConnected(activity) { } }
    }

    /**
     * H10 (S011) -- Uri `content://` del archivo `.mimoo` ya
     * generado, listo para que la UI abra el selector de "Compartir"
     * del sistema (`Intent.ACTION_SEND` con `EXTRA_STREAM`, no
     * `EXTRA_TEXT` -- rediseñado tras la prueba real de Miguel Ángel:
     * un texto plano no se puede "tocar para abrir", un archivo sí).
     * `null` = nada pendiente. Separado por completo de `_uiState`
     * (H06/Drive) -- generar el archivo es puramente local, sin
     * autorización ni red de por medio.
     */
    private val _generatedShareFileUri = MutableStateFlow<android.net.Uri?>(null)
    val generatedShareFileUri: StateFlow<android.net.Uri?> = _generatedShareFileUri.asStateFlow()

    /** Nivel 1 de compartición (S011): Biblioteca completa. Ver ShareCodeRepository. */
    fun onShareLibraryClicked() {
        viewModelScope.launch {
            _generatedShareFileUri.value = shareCodeRepository.buildLibraryShareFile()
        }
    }

    /**
     * H15 (miMooutCast), S032 -- comparte `mimooutcast_database.json`
     * (el resultado de "Generar base de datos de miMooutCast") por el
     * selector del sistema, mismo mecanismo que `onShareLibraryClicked()`
     * -- Miguel Ángel tiene que sacarlo del teléfono para dárselo a
     * Claude, que lo añadirá a `app/src/main/assets/` del repositorio.
     */
    fun onShareMimooutcastDatabaseClicked() {
        _generatedShareFileUri.value = mimooutcastDatabaseBuilder.shareableUri()
    }

    /**
     * S034 -- comparte `mimooutcast_broken_links.json` (enlaces de la
     * semilla bundleada que dejaron de funcionar en uso real, con su
     * sustituto cuando ya se encontró uno) por el mismo selector del
     * sistema que `onShareMimooutcastDatabaseClicked()` -- Miguel
     * Ángel lo saca del teléfono para dármelo, y yo sustituyo en
     * `mimooutcast_seed.json` cada roto por su sustituto antes de la
     * siguiente build.
     */
    fun onShareMimooutcastBrokenLinksClicked() {
        _generatedShareFileUri.value = mimooutcastBrokenLinksLogger.shareableUri()
    }

    /**
     * S034 -- géneros cuyo contador de enlaces rotos de la semilla ya
     * llegó a 10 -- ver `MimooutcastBrokenLinksLogger.needsReinstall()`.
     * Leído una vez al entrar en Ajustes (no observado en vivo -- el
     * contador solo cambia durante una sesión de reproducción real,
     * no mientras Ajustes está abierto).
     */
    fun mimooutcastGenresNeedingReinstall(): List<String> = mimooutcastBrokenLinksLogger.genresNeedingReinstall()

    /** Llamado por la UI justo después de lanzar el Intent.ACTION_SEND, para no relanzarlo en la siguiente recomposición. */
    fun consumeGeneratedShareFileUri() {
        _generatedShareFileUri.value = null
    }

    /**
     * Cuando la autorización necesita confirmación del usuario
     * (primera vez, o acceso revocado), la UI observa esto y lanza el
     * Intent con un `ActivityResultLauncher<IntentSenderRequest>`.
     * `null` = nada pendiente.
     * ---
     * When authorization needs user confirmation (first time, or
     * revoked access), the UI observes this and launches the Intent
     * with an `ActivityResultLauncher<IntentSenderRequest>`. `null` =
     * nothing pending.
     */
    private val _pendingConsent = MutableStateFlow<IntentSenderRequest?>(null)
    val pendingConsent: StateFlow<IntentSenderRequest?> = _pendingConsent.asStateFlow()

    /**
     * Qué operación real se ejecuta en cuanto haya un accessToken
     * válido -- puede ser inmediatamente (ya autorizado) o tras
     * resolver el consentimiento del usuario.
     * ---
     * Which real operation runs as soon as there's a valid
     * accessToken -- either immediately (already authorized) or after
     * resolving the user's consent.
     */
    private sealed class PendingAction {
        object Export : PendingAction()
        object ListBackups : PendingAction()
        data class ImportBackup(val backup: DriveBackupFile) : PendingAction()
    }

    private var pendingAction: PendingAction? = null

    fun onExportClicked(activity: Activity) {
        pendingAction = PendingAction.Export
        beginAuthorization(activity)
    }

    /** Pide la lista de backups disponibles en Drive -- la UI la muestra para que Miguel Ángel elija uno. */
    fun onImportRequested(activity: Activity) {
        pendingAction = PendingAction.ListBackups
        beginAuthorization(activity)
    }

    /**
     * Llamado tras la confirmación explícita del diálogo destructivo
     * en la UI ("esto borrará tu repositorio actual..."), nunca
     * directamente al tocar un ítem de la lista.
     * ---
     * Called after the explicit confirmation of the destructive
     * dialog in the UI ("this will erase your current
     * repository..."), never directly on tapping a list item.
     */
    fun onImportConfirmed(activity: Activity, backup: DriveBackupFile) {
        pendingAction = PendingAction.ImportBackup(backup)
        beginAuthorization(activity)
    }

    private fun beginAuthorization(activity: Activity) {
        Log.d(TAG, "beginAuthorization() -- pendingAction=$pendingAction")
        BackupDebugLogger.log(activity, storageManager, "beginAuthorization() -- pendingAction=$pendingAction")
        _uiState.value = BackupUiState.Working
        viewModelScope.launch {
            try {
                when (val outcome = authorizationHelper.requestAuthorization(activity)) {
                    is DriveAuthorizationOutcome.Authorized -> {
                        Log.d(TAG, "Ya autorizado, sin diálogo -- ejecutando pendingAction directamente")
                        BackupDebugLogger.log(activity, storageManager, "Ya autorizado, sin diálogo -- ejecutando pendingAction directamente")
                        runPendingAction(activity, outcome.accessToken)
                    }
                    is DriveAuthorizationOutcome.NeedsUserConsent -> {
                        Log.d(TAG, "Hace falta consentimiento del usuario -- lanzando IntentSenderRequest")
                        BackupDebugLogger.log(activity, storageManager, "Hace falta consentimiento del usuario -- lanzando IntentSenderRequest")
                        _pendingConsent.value = outcome.intentSenderRequest
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "beginAuthorization() FALLÓ", e)
                BackupDebugLogger.logError(activity, storageManager, "beginAuthorization() FALLÓ", e)
                pendingAction = null
                _uiState.value = BackupUiState.Error(
                    e.message ?: "No se pudo pedir autorización a Google."
                )
            }
        }
    }

    /**
     * Llamado por la UI tras resolver el `IntentSenderRequest`
     * (`ActivityResultLauncher`) que salió de `pendingConsent`, con
     * éxito.
     * ---
     * Called by the UI after resolving the `IntentSenderRequest`
     * (`ActivityResultLauncher`) that came out of `pendingConsent`,
     * on success.
     */
    fun onConsentResolved(activity: Activity, resultData: Intent?) {
        Log.d(TAG, "onConsentResolved() -- resolviendo consentimiento devuelto")
        BackupDebugLogger.log(activity, storageManager, "onConsentResolved() -- resolviendo consentimiento devuelto")
        _pendingConsent.value = null
        try {
            val token = authorizationHelper.extractAccessTokenFromResolution(activity, resultData)
            Log.d(TAG, "onConsentResolved() OK -- token obtenido, ejecutando pendingAction")
            BackupDebugLogger.log(activity, storageManager, "onConsentResolved() OK -- token obtenido, ejecutando pendingAction")
            viewModelScope.launch { runPendingAction(activity, token) }
        } catch (e: Exception) {
            Log.e(TAG, "onConsentResolved() FALLÓ", e)
            BackupDebugLogger.logError(activity, storageManager, "onConsentResolved() FALLÓ", e)
            pendingAction = null
            _uiState.value = BackupUiState.Error(
                e.message ?: "Google no concedió el acceso a Drive."
            )
        }
    }

    fun dismissMessage() {
        _uiState.value = BackupUiState.Idle
    }

    private suspend fun runPendingAction(activity: Activity, accessToken: String) {
        val action = pendingAction
        pendingAction = null
        if (action == null) {
            Log.w(TAG, "runPendingAction() llamado sin ninguna acción pendiente -- no hace nada")
            return
        }

        Log.d(TAG, "runPendingAction() -- ejecutando $action")
        BackupDebugLogger.log(activity, storageManager, "runPendingAction() -- ejecutando $action")
        _uiState.value = BackupUiState.Working
        try {
            when (action) {
                is PendingAction.Export -> exportNow(activity, accessToken)
                is PendingAction.ListBackups -> listBackupsNow(activity, accessToken)
                is PendingAction.ImportBackup -> importNow(activity, accessToken, action.backup)
            }
            Log.d(TAG, "runPendingAction() -- $action terminado con éxito")
            BackupDebugLogger.log(activity, storageManager, "runPendingAction() -- $action terminado con éxito")
        } catch (e: Exception) {
            Log.e(TAG, "runPendingAction() -- $action FALLÓ", e)
            BackupDebugLogger.logError(activity, storageManager, "runPendingAction() -- $action FALLÓ", e)
            _uiState.value = BackupUiState.Error(
                e.message ?: "Error inesperado hablando con Drive."
            )
        }
    }

    private suspend fun exportNow(activity: Activity, accessToken: String) {
        val bundle = backupRepository.buildCurrentBundle()
        val step1 = "exportNow() -- bundle construido: ${bundle.tracks.size} pistas, ${bundle.favoriteAlbums.size} favoritos, ${bundle.playlists.size} playlists"
        Log.d(TAG, step1)
        BackupDebugLogger.log(activity, storageManager, step1)
        val json = backupRepository.toJson(bundle)
        val step2 = "exportNow() -- JSON serializado, ${json.length} caracteres. Subiendo a Drive..."
        Log.d(TAG, step2)
        BackupDebugLogger.log(activity, storageManager, step2)
        val uploaded: DriveBackupFile = driveRepository.uploadBackup(accessToken, json)
        val step3 = "exportNow() -- subida OK, archivo '${uploaded.name}' (id=${uploaded.id})"
        Log.d(TAG, step3)
        BackupDebugLogger.log(activity, storageManager, step3)
        _uiState.value = BackupUiState.ExportSuccess(uploaded.name)
    }

    private suspend fun listBackupsNow(activity: Activity, accessToken: String) {
        val backups = driveRepository.listBackups(accessToken)
        val step = "listBackupsNow() -- ${backups.size} backups encontrados en Drive"
        Log.d(TAG, step)
        BackupDebugLogger.log(activity, storageManager, step)
        _uiState.value = BackupUiState.BackupsListed(backups)
    }

    /**
     * Descarga el backup elegido, lo deserializa (fromJson() ya
     * rechaza versiones no reconocidas), ejecuta la sustitución
     * destructiva (PASO 4) y encola automáticamente la descarga de
     * TODAS las pistas importadas con los metadatos ya fijados, sin
     * ningún diálogo de edición (PASO 5) -- reutiliza
     * DownloadQueueManager.enqueue(), el mismo mecanismo que H05
     * PASO 6b.
     * ---
     * Downloads the chosen backup, deserializes it (fromJson() already
     * rejects unrecognized versions), runs the destructive substitution
     * (PASO 4), and automatically enqueues the download of ALL
     * imported tracks with the metadata already set, with no edit
     * dialog (PASO 5) -- reuses DownloadQueueManager.enqueue(), the
     * same mechanism as H05 PASO 6b.
     */
    private suspend fun importNow(activity: Activity, accessToken: String, backup: DriveBackupFile) {
        val step1 = "importNow() -- descargando '${backup.name}' (id=${backup.id})"
        Log.d(TAG, step1)
        BackupDebugLogger.log(activity, storageManager, step1)
        val json = driveRepository.downloadBackupJson(accessToken, backup.id)
        val step2 = "importNow() -- descarga OK, ${json.length} caracteres. Deserializando..."
        Log.d(TAG, step2)
        BackupDebugLogger.log(activity, storageManager, step2)
        val bundle = backupRepository.fromJson(json)
        val step3 = "importNow() -- bundle válido: ${bundle.tracks.size} pistas, ${bundle.favoriteAlbums.size} favoritos, ${bundle.playlists.size} playlists. Ejecutando sustitución destructiva..."
        Log.d(TAG, step3)
        BackupDebugLogger.log(activity, storageManager, step3)
        val result = importRepository.importDestructively(bundle)
        val step4 = "importNow() -- sustitución OK, ${result.importedTracks.size} pistas insertadas. Encolando descargas..."
        Log.d(TAG, step4)
        BackupDebugLogger.log(activity, storageManager, step4)

        result.importedTracks.forEach { track ->
            downloadQueueManager.enqueue(
                youtubeId = track.youtubeId,
                title = track.title,
                artist = track.artist ?: track.channelTitle,
                album = track.album,
                trackPosition = track.trackPosition,
            )
        }
        val step5 = "importNow() -- ${result.importedTracks.size} descargas encoladas"
        Log.d(TAG, step5)
        BackupDebugLogger.log(activity, storageManager, step5)

        _uiState.value = BackupUiState.ImportSuccess(result.importedTracks.size)
    }

    /**
     * "Importar desde archivo" (S010, petición explícita de Miguel
     * Ángel): la app pide el scope `drive.file` de Drive a propósito
     * (ver DriveAuthorizationHelper), el más restringido posible --
     * con eso, la app SOLO puede ver los archivos que ella misma ha
     * creado en Drive, nunca uno que otra persona haya subido a mano
     * a una carpeta, aunque sea la carpeta correcta. Caso real: Miguel
     * Ángel exportó desde su móvil, compartió el archivo por mensaje a
     * su mujer, ella lo subió a Drive a mano -- "Importar desde Drive"
     * nunca iba a poder verlo, sea cual sea la cuenta.
     *
     * Esta vía no toca Drive para nada: lee el JSON que ya se ha leído
     * del archivo elegido con el selector de Android (fuera del scope
     * restringido de Drive, así que sin esa limitación), y a partir de
     * ahí reutiliza exactamente el mismo camino que importNow() desde
     * el paso de deserializar -- misma sustitución destructiva, mismo
     * encolado de descargas.
     * ---
     * "Import from file" (S010, explicit request from Miguel Ángel):
     * the app deliberately requests Drive's `drive.file` scope, the
     * most restricted one -- with it, the app can ONLY see files it
     * created itself in Drive, never one another person manually
     * uploaded to a folder, even the right one. Real case: Miguel
     * Ángel exported from his phone, shared the file via message to
     * his wife, she manually uploaded it to Drive -- "Import from
     * Drive" was never going to see it, regardless of account.
     *
     * This path never touches Drive at all: reads the JSON already
     * read from the file picked with Android's file picker (outside
     * Drive's restricted scope, so unaffected by that limitation), and
     * from there reuses the exact same path as importNow() from the
     * deserialize step onward.
     */
    fun importFromFile(activity: Activity, json: String) {
        _uiState.value = BackupUiState.Working
        viewModelScope.launch {
            try {
                val step1 = "importFromFile() -- ${json.length} caracteres leídos. Deserializando..."
                Log.d(TAG, step1)
                BackupDebugLogger.log(activity, storageManager, step1)
                val bundle = backupRepository.fromJson(json)
                val step2 = "importFromFile() -- bundle válido: ${bundle.tracks.size} pistas, " +
                    "${bundle.favoriteAlbums.size} favoritos, ${bundle.playlists.size} playlists. " +
                    "Ejecutando sustitución destructiva..."
                Log.d(TAG, step2)
                BackupDebugLogger.log(activity, storageManager, step2)
                val result = importRepository.importDestructively(bundle)

                result.importedTracks.forEach { track ->
                    downloadQueueManager.enqueue(
                        youtubeId = track.youtubeId,
                        title = track.title,
                        artist = track.artist ?: track.channelTitle,
                        album = track.album,
                        trackPosition = track.trackPosition,
                    )
                }
                val step3 = "importFromFile() -- ${result.importedTracks.size} pistas insertadas, descargas encoladas"
                Log.d(TAG, step3)
                BackupDebugLogger.log(activity, storageManager, step3)

                _uiState.value = BackupUiState.ImportSuccess(result.importedTracks.size)
            } catch (e: Exception) {
                Log.e(TAG, "importFromFile() FALLÓ", e)
                BackupDebugLogger.logError(activity, storageManager, "importFromFile() FALLÓ", e)
                _uiState.value = BackupUiState.Error(
                    e.message ?: "El archivo no es un backup de MiMoo válido."
                )
            }
        }
    }

}

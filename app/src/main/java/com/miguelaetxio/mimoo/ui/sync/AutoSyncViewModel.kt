package com.miguelaetxio.mimoo.ui.sync

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelaetxio.mimoo.data.backup.BackupBundle
import com.miguelaetxio.mimoo.data.backup.BackupDebugLogger
import com.miguelaetxio.mimoo.data.backup.BackupDriveRepository
import com.miguelaetxio.mimoo.data.backup.BackupImportRepository
import com.miguelaetxio.mimoo.data.backup.BackupMirrorRepository
import com.miguelaetxio.mimoo.data.backup.BackupRepository
import com.miguelaetxio.mimoo.data.backup.BundleComparison
import com.miguelaetxio.mimoo.data.backup.DeviceIdentityManager
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationHelper
import com.miguelaetxio.mimoo.data.backup.DriveAuthorizationOutcome
import com.miguelaetxio.mimoo.data.backup.SyncEnvelope
import com.miguelaetxio.mimoo.data.download.CookiesManager
import com.miguelaetxio.mimoo.data.download.DownloadQueueManager
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.library.LibraryReconciler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MiMoo-AutoSync-Pull"

/**
 * S081 -- ver el comentario completo junto a su uso en runSync().
 * Mismo valor y mismo porqué que en AutoSyncPusher.kt (no hay un sitio
 * común a ambas clases, así que se repite la constante).
 */
private const val ACTIVE_DOWNLOADS_SYNC_THRESHOLD = 5

/**
 * S068 -- rediseño explícito de Miguel Ángel tras la pérdida real de
 * archivos investigada en S067 ("¿por qué no teníamos esos 1.300
 * temas en Drive?" -- respuesta: Drive nunca ha sido copia de los
 * audios, solo de la lista de qué tenías descargado). Reemplaza por
 * completo el diseño de tres casos de S008/H07 PARTE 1 -- ya NO
 * existe ningún caso que se resuelva solo, sin preguntar:
 *
 * 1. **No hay copia en Drive todavía** -- este dispositivo crea la
 *    copia, sin preguntar nada (no hay nada que decidir).
 * 2. **Hay copia y coincide con lo local** -- nada que hacer.
 * 3. **Hay copia y NO coincide** -- sea el mismo dispositivo o no
 *    ([CountMismatch]), se pregunta SIEMPRE, con la misma pregunta
 *    tal cual la formuló Miguel Ángel: "En Drive tienes menos/más
 *    pistas que en local, ¿con cuál te quedas?". Ya no existe
 *    ninguna versión de esto que "la nube manda sin preguntar" --
 *    ese caso (antes exclusivo de "mismo dispositivo") fue
 *    exactamente el que borró 69 temas en silencio real, sin avisar,
 *    el 2026-09-07.
 *
 * Todo-o-nada: una de las dos copias completas sustituye siempre a la
 * otra entera (`BackupImportRepository.importDestructively()` para
 * H06 manual; `applyCloudWinsTargeted()`/`pushAsNewEnvelope()` aquí) --
 * nunca una fusión parcial.
 * ---
 * S068 -- explicit redesign from Miguel Ángel after the real file loss
 * investigated in S067 ("why didn't we have those 1,300 tracks in
 * Drive?" -- answer: Drive was never a copy of the audio files, only
 * of the list of what you had downloaded). Fully replaces S008/H07
 * PART 1's three-case design -- there is NO LONGER any case that
 * resolves itself without asking:
 *
 * 1. **There's no copy on Drive yet** -- this device creates the copy,
 *    without asking anything (nothing to decide).
 * 2. **There's a copy and it matches local** -- nothing to do.
 * 3. **There's a copy and it does NOT match** -- whether it's the same
 *    device or not ([CountMismatch]), it ALWAYS asks, with the same
 *    question exactly as Miguel Ángel phrased it: "Drive has
 *    fewer/more tracks than local, which do you want to keep?". There
 *    is no longer any version of this where "the cloud wins without
 *    asking" -- that case (previously exclusive to "same device") was
 *    exactly what silently deleted 69 tracks with no warning on
 *    2026-09-07.
 *
 * All-or-nothing: one of the two full copies always replaces the other
 * entirely (`BackupImportRepository.importDestructively()` for manual
 * H06; `applyCloudWinsTargeted()`/`pushAsNewEnvelope()` here) -- never
 * a partial merge.
 */
sealed class AutoSyncUiState {
    object Idle : AutoSyncUiState()
    object Checking : AutoSyncUiState()

    /**
     * S068 -- único caso de discrepancia que existe ahora; sustituye a
     * los antiguos RestoredFromCloud (Caso 2, silencioso) y
     * ConflictOtherDevice (Caso 3). Se llega aquí SIEMPRE que
     * `!comparison.identical`, sea el mismo dispositivo o no --
     * pendiente de que Miguel Ángel responda con cuál se queda.
     */
    data class CountMismatch(
        val envelope: SyncEnvelope,
        val comparison: BundleComparison,
    ) : AutoSyncUiState()

    /** Nada que hacer (copias idénticas), o resolución ya aplicada tras CountMismatch. */
    data class Done(val message: String? = null) : AutoSyncUiState()

    data class Error(val message: String) : AutoSyncUiState()
}

@HiltViewModel
class AutoSyncViewModel @Inject constructor(
    private val authorizationHelper: DriveAuthorizationHelper,
    private val driveRepository: BackupDriveRepository,
    private val backupRepository: BackupRepository,
    private val mirrorRepository: BackupMirrorRepository,
    private val importRepository: BackupImportRepository,
    private val downloadQueueManager: DownloadQueueManager,
    private val libraryReconciler: LibraryReconciler,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val storageManager: StorageManager,
    private val cookiesManager: CookiesManager,
    // S075 -- mismo motivo que AutoSyncPusher: durante una descarga
    // masiva en curso, "local < Drive" es esperado y transitorio, no
    // un borrado -- ver runSync() más abajo.
    private val searchResultTrackRepository: com.miguelaetxio.mimoo.data.local.repository.SearchResultTrackRepository,
    @ApplicationContext private val applicationContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AutoSyncUiState>(AutoSyncUiState.Idle)
    val uiState: StateFlow<AutoSyncUiState> = _uiState.asStateFlow()

    private val _pendingConsent = MutableStateFlow<IntentSenderRequest?>(null)
    val pendingConsent: StateFlow<IntentSenderRequest?> = _pendingConsent.asStateFlow()

    /** Access token ya conseguido, guardado solo mientras se espera la respuesta del caso 3. */
    private var pendingAccessToken: String? = null

    /** Llamado una vez al arrancar MainActivity, si ya hay carpeta SAF elegida. */
    fun startAutoSync(activity: Activity) {
        if (_uiState.value != AutoSyncUiState.Idle) return
        _uiState.value = AutoSyncUiState.Checking
        viewModelScope.launch {
            try {
                when (val outcome = authorizationHelper.requestAuthorization(activity)) {
                    is DriveAuthorizationOutcome.Authorized -> runSync(outcome.accessToken)
                    is DriveAuthorizationOutcome.NeedsUserConsent ->
                        _pendingConsent.value = outcome.intentSenderRequest
                }
            } catch (e: Exception) {
                Log.w(TAG, "startAutoSync() -- fallo pidiendo autorización", e)
                _uiState.value = AutoSyncUiState.Error(
                    e.message ?: "No se pudo comprobar la sincronización con Drive."
                )
            }
        }
    }

    /** Llamado por la UI tras resolver el IntentSenderRequest de pendingConsent. */
    fun onConsentResolved(activity: Activity, resultData: Intent?) {
        _pendingConsent.value = null
        viewModelScope.launch {
            try {
                val token = authorizationHelper.extractAccessTokenFromResolution(activity, resultData)
                runSync(token)
            } catch (e: Exception) {
                Log.w(TAG, "onConsentResolved() -- fallo extrayendo el token", e)
                _uiState.value = AutoSyncUiState.Error(
                    e.message ?: "Google no concedió el acceso a Drive."
                )
            }
        }
    }

    private suspend fun runSync(accessToken: String) {
        val remoteJson = driveRepository.pullSyncState(accessToken)
        if (remoteJson == null) {
            // Caso 1: no hay copia todavía -- se crea, sin preguntar nada.
            pushAsNewEnvelope(accessToken)
            verifyDiskAndReconcile()
            _uiState.value = AutoSyncUiState.Done()
            return
        }

        val envelope = try {
            backupRepository.fromSyncJson(remoteJson)
        } catch (e: BackupRepository.BackupParseException) {
            // No hay forma útil de recuperar nada de un archivo que
            // no se puede leer (formato antiguo, corrupto, etc.) --
            // en vez de dejar a Miguel Ángel atascado con el mismo
            // error cada vez que abre la app (bug real reportado,
            // reproducido con un archivo del formato anterior a esta
            // sesión ya en Drive), se trata igual que el caso 1: se
            // sobreescribe con el estado actual de este dispositivo y
            // se sigue adelante, sin preguntar nada raro.
            // ---
            // There's no useful way to recover anything from a file
            // that can't be read (old format, corrupt, etc.) --
            // instead of leaving Miguel Ángel stuck with the same
            // error every time the app opens (real bug reported,
            // reproduced with a file from before this session's
            // format already in Drive), it's treated the same as
            // case 1: overwritten with this device's current state
            // and moves on, without asking anything odd.
            Log.w(TAG, "runSync() -- copia remota ilegible (${e.message}), se sobreescribe", e)
            pushAsNewEnvelope(accessToken)
            verifyDiskAndReconcile()
            _uiState.value = AutoSyncUiState.Done()
            return
        }

        val localBundle = backupRepository.buildCurrentBundle()
        val comparison = mirrorRepository.compare(localBundle, envelope.bundle)
        val compareMsg = "runSync() -- comparación: local=${comparison.localTrackCount} " +
            "remoto=${comparison.remoteTrackCount} idéntico=${comparison.identical} " +
            "mismoDispositivo=${envelope.deviceId == deviceIdentityManager.deviceId}"
        Log.d(TAG, compareMsg)
        BackupDebugLogger.log(applicationContext, storageManager, compareMsg)

        // Fix real (2026-07-24, petición explícita de Miguel Ángel:
        // "que mi mujer no tenga que importar nada") -- las cookies de
        // YouTube se sincronizan SIEMPRE que el sobre remoto traiga
        // algo distinto de lo local, sin importar si las pistas
        // coinciden o de qué dispositivo venga el sobre. Deliberadamente
        // fuera del resto del flujo comparison.identical/caso2/caso3,
        // que es exclusivamente sobre pistas.
        applyRemoteCookiesIfNewer(envelope)

        if (comparison.identical) {
            // H07 PARTE 1 -- fallo real señalado por Miguel Ángel:
            // que la base de datos coincida con Drive NO significa
            // que el disco coincida con la base de datos. Se
            // verifica siempre, incluso cuando no hay nada que
            // resolver contra la nube (p.ej. alguien borró un
            // archivo a mano con el explorador de archivos sin
            // tocar Room para nada).
            // ---
            // H07 PART 1 -- real gap flagged by Miguel Ángel: the
            // database matching Drive does NOT mean the disk matches
            // the database. Always verified, even when there's
            // nothing to resolve against the cloud (e.g. someone
            // deleted a file by hand with a file explorer without
            // touching Room at all).
            verifyDiskAndReconcile()
            _uiState.value = AutoSyncUiState.Done()
            return
        }

        // S075 -- bug real reportado por Miguel Ángel: durante una
        // descarga masiva en curso, este diálogo saltaba una y otra
        // vez cada vez que se recomprobaba (rotación de pantalla,
        // app llevada a segundo plano y recuperada, etc.) -- porque
        // local, TODAVÍA a mitad de recuperar, siempre tenía menos
        // pistas que Drive. No es un desajuste real, es una
        // recuperación en curso; preguntar en cada ciclo interrumpía
        // la descarga sin motivo. Se salta el aviso entero (ni se
        // pregunta ni se toca nada) mientras haya una descarga masiva
        // de verdad en curso -- en cuanto la cola baje de ese umbral,
        // la siguiente comprobación sí compara y pregunta con toda
        // normalidad, con el estado ya asentado de verdad. Mismo
        // criterio que AutoSyncPusher.pushCurrentState().
        //
        // S081 -- bug real reportado por Miguel Ángel: "ahora no
        // detecta ningún tipo de cambio [...] está completamente
        // rota." Este guardián se saltaba con que hubiera UNA sola
        // pista QUEUED/DOWNLOADING -- fácil que quedara alguna fila
        // suelta atascada así para siempre tras todo el lío de
        // reconciliaciones de estos días, bloqueando la comparación
        // SIEMPRE, en ambos sentidos. Umbral subido a "más de un
        // puñado" -- ver ACTIVE_DOWNLOADS_SYNC_THRESHOLD.
        if (searchResultTrackRepository.getActiveDownloadsOnce().size > ACTIVE_DOWNLOADS_SYNC_THRESHOLD) {
            Log.d(TAG, "runSync() -- descarga masiva en curso, se salta el aviso de discrepancia")
            _uiState.value = AutoSyncUiState.Done()
            return
        }

        // S068 -- petición explícita de Miguel Ángel: se pregunta
        // SIEMPRE que no coincida, sea el mismo dispositivo o no --
        // ya no existe ningún caso de "la nube manda sin preguntar".
        // La verificación de disco se hace después de resolver, en
        // confirmCloudWins()/confirmLocalWins().
        pendingAccessToken = accessToken
        _uiState.value = AutoSyncUiState.CountMismatch(envelope, comparison)
    }

    /**
     * H07 PARTE 1 -- cierra el hueco señalado por Miguel Ángel: hasta
     * ahora todo el pipeline comparaba base de datos (local) contra
     * base de datos (Drive), sin comprobar nunca que el disco físico
     * coincidiera con lo que Room decía. Dos direcciones, ambas ya
     * construidas por separado (`LibraryReconciler`), aquí solo se
     * orquestan juntas en cada sincronización:
     *
     * 1. `verifyDiskState()`: filas `DONE` cuyo archivo ya no existe
     *    -- se marcan `PENDING` y se reencolan aquí mismo.
     * 2. `rescan()`: archivos huérfanos en disco sin fila en Room --
     *    se recuperan (mismo mecanismo que al elegir la carpeta SAF
     *    por primera vez o pulsar "Actualizar" en Biblioteca, ahora
     *    también en cada sincronización automática, no solo en esos
     *    dos momentos).
     * ---
     * H07 PART 1 -- closes the gap Miguel Ángel flagged: until now the
     * whole pipeline compared database (local) against database
     * (Drive), never checking whether the physical disk matched what
     * Room claimed. Two directions, both already built separately
     * (`LibraryReconciler`), just orchestrated together here on every
     * sync:
     *
     * 1. `verifyDiskState()`: `DONE` rows whose file no longer exists
     *    -- marked `PENDING` and re-queued right here.
     * 2. `rescan()`: orphaned files on disk with no Room row -- get
     *    recovered (same mechanism as first picking the SAF folder or
     *    tapping "Refresh" in Library, now also on every automatic
     *    sync, not just those two moments).
     */
    /**
     * Fix real (2026-07-24) -- ver comentario de
     * `SyncEnvelope.cookiesTxtContent` en `BackupDto.kt`. Sobrescribe
     * el cookies.txt local solo si el remoto trae contenido distinto
     * -- si el remoto no tiene cookies (`null`) o es idéntico al
     * local, no se toca nada.
     * ---
     * Real fix (2026-07-24) -- see `SyncEnvelope.cookiesTxtContent`'s
     * comment in `BackupDto.kt`. Overwrites the local cookies.txt only
     * if the remote one carries different content -- if the remote has
     * no cookies (`null`) or it's identical to local, nothing is
     * touched.
     */
    private fun applyRemoteCookiesIfNewer(envelope: SyncEnvelope) {
        val remoteCookies = envelope.cookiesTxtContent ?: return
        if (remoteCookies != cookiesManager.currentContentOrNull()) {
            cookiesManager.applySyncedCookies(remoteCookies)
            val msg = "applyRemoteCookiesIfNewer() -- cookies.txt actualizado desde " +
                "la copia de ${envelope.deviceLabel}"
            Log.d(TAG, msg)
            BackupDebugLogger.log(applicationContext, storageManager, msg)
        }
    }

    private suspend fun verifyDiskAndReconcile() {
        val missing = libraryReconciler.verifyDiskState()
        missing.forEach { track ->
            downloadQueueManager.enqueue(
                youtubeId = track.youtubeId,
                title = track.title,
                artist = track.artist ?: track.channelTitle,
                album = track.album,
                trackPosition = track.trackPosition,
            )
        }
        if (missing.isNotEmpty()) {
            val msg = "verifyDiskAndReconcile() -- ${missing.size} pista(s) DONE sin " +
                "archivo real, reencoladas"
            Log.d(TAG, msg)
            BackupDebugLogger.log(applicationContext, storageManager, msg)
        }

        storageManager.getRootUri()?.let { rootUri ->
            val result = libraryReconciler.rescan(rootUri)
            if (result.tracksDiscovered > 0) {
                val msg = "verifyDiskAndReconcile() -- ${result.tracksDiscovered} " +
                    "archivo(s) huérfano(s) recuperado(s) en disco"
                Log.d(TAG, msg)
                BackupDebugLogger.log(applicationContext, storageManager, msg)
            }
        }
    }

    /**
     * S068 -- respuesta "con la de Drive" -- la nube sustituye a
     * local.
     *
     * Fallo real detectado en logs de Miguel Ángel (2026-07-14): esta
     * función lanzaba la llamada de red sin `try/catch`, a diferencia
     * de `startAutoSync()`/`onConsentResolved()`. Un timeout real de
     * Drive (confirmado en `crash_log`) dejaba la excepción sin
     * capturar -- la app se caía, y al reabrirse volvía a preguntar
     * lo mismo desde cero, en bucle. Además, como el estado no
     * cambiaba hasta que la llamada de red terminaba, nada impedía
     * pulsar otra vez mientras la anterior seguía en vuelo (varias
     * subidas concurrentes). Fix: capturar el fallo como ya se hacía
     * en `startAutoSync()`, y pasar a `Checking` de forma síncrona en
     * cuanto se pulsa, para que el diálogo desaparezca al instante y
     * un segundo toque no dispare otra llamada.
     * ---
     * Case 3, "yes, tracks were added/removed on another device" --
     * cloud replaces local.
     *
     * Real bug found in Miguel Ángel's logs (2026-07-14): this
     * function fired the network call with no `try/catch`, unlike
     * `startAutoSync()`/`onConsentResolved()`. A real Drive timeout
     * (confirmed in `crash_log`) left the exception uncaught -- the
     * app crashed, and on reopening asked the same question again
     * from scratch, in a loop. Also, since the state didn't change
     * until the network call finished, nothing stopped a second tap
     * while the first was still in flight (multiple concurrent
     * uploads). Fix: catch the failure the same way
     * `startAutoSync()` already does, and move to `Checking`
     * synchronously the moment it's tapped, so the dialog disappears
     * instantly and a second tap can't fire another call.
     */
    fun confirmCloudWins() {
        val state = _uiState.value as? AutoSyncUiState.CountMismatch ?: return
        _uiState.value = AutoSyncUiState.Checking
        viewModelScope.launch {
            try {
                restoreFromCloud(state.envelope.bundle)
                verifyDiskAndReconcile()
                pendingAccessToken = null
                _uiState.value = AutoSyncUiState.Done(
                    "Restaurado desde la copia de ${state.envelope.deviceLabel}."
                )
            } catch (e: Exception) {
                Log.w(TAG, "confirmCloudWins() -- fallo restaurando desde Drive", e)
                _uiState.value = AutoSyncUiState.Error(
                    e.message ?: "No se pudo restaurar la copia de Drive."
                )
            }
        }
    }

    /**
     * Reconcilia el repositorio local contra `bundle` de forma
     * SELECTIVA (`BackupImportRepository.applyCloudWinsTargeted()`,
     * S008 sexta vuelta -- nunca `importDestructively()`, esa es solo
     * para H06 manual) y encola la descarga únicamente de las pistas
     * nuevas resultantes -- las que ya se tenían `DONE` no se tocan ni
     * se redescargan.
     * ---
     * Reconciles the local repository against `bundle` SELECTIVELY
     * (`BackupImportRepository.applyCloudWinsTargeted()`, S008 sixth
     * round -- never `importDestructively()`, that one's only for
     * manual H06) and queues the download of only the resulting new
     * tracks -- the ones already `DONE` aren't touched or
     * re-downloaded.
     */
    private suspend fun restoreFromCloud(bundle: BackupBundle) {
        // S082 -- petición explícita de Miguel Ángel: limpiar las
        // descargas en curso antes de traer la copia de Drive -- lo
        // que interesa ahora es lo que trae Drive, no lo que ya
        // estuviera en cola de antes.
        downloadQueueManager.cancelAllDownloads()
        val result = importRepository.applyCloudWinsTargeted(bundle)
        val step = "restoreFromCloud() -- encolando ${result.importedTracks.size} descarga(s)..."
        Log.d(TAG, step)
        BackupDebugLogger.log(applicationContext, storageManager, step)
        result.importedTracks.forEach { track ->
            downloadQueueManager.enqueue(
                youtubeId = track.youtubeId,
                title = track.title,
                artist = track.artist ?: track.channelTitle,
                album = track.album,
                trackPosition = track.trackPosition,
            )
        }
        val done = "restoreFromCloud() -- ${result.importedTracks.size} descarga(s) encoladas"
        Log.d(TAG, done)
        BackupDebugLogger.log(applicationContext, storageManager, done)
    }

    /**
     * S068 -- respuesta "con la de este dispositivo" -- local sustituye
     * a la nube (y se sube). Mismo fix que `confirmCloudWins()` -- ver
     * comentario ahí para el diagnóstico completo (2026-07-14).
     */
    fun confirmLocalWins() {
        val state = _uiState.value as? AutoSyncUiState.CountMismatch ?: return
        val accessToken = pendingAccessToken ?: return
        _uiState.value = AutoSyncUiState.Checking
        viewModelScope.launch {
            try {
                verifyDiskAndReconcile()
                pushAsNewEnvelope(accessToken)
                pendingAccessToken = null
                _uiState.value = AutoSyncUiState.Done("Tu copia local ha sustituido a la de Drive.")
            } catch (e: Exception) {
                Log.w(TAG, "confirmLocalWins() -- fallo subiendo la copia local", e)
                _uiState.value = AutoSyncUiState.Error(
                    e.message ?: "No se pudo subir tu copia a Drive."
                )
            }
        }
    }

    fun dismiss() {
        pendingAccessToken = null
        _uiState.value = AutoSyncUiState.Idle
    }

    private suspend fun pushAsNewEnvelope(accessToken: String) {
        val bundle = backupRepository.buildCurrentBundle()
        val envelope = SyncEnvelope(
            deviceId = deviceIdentityManager.deviceId,
            deviceLabel = deviceIdentityManager.deviceLabel,
            timestamp = System.currentTimeMillis(),
            bundle = bundle,
            cookiesTxtContent = cookiesManager.currentContentOrNull(),
        )
        driveRepository.pushSyncState(accessToken, backupRepository.toSyncJson(envelope))
    }
}

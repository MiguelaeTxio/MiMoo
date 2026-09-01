package com.miguelaetxio.mimoo.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.miguelaetxio.mimoo.data.remote.LyricsResult
import com.miguelaetxio.mimoo.ui.playlist.AddToPlaylistDialog
import com.miguelaetxio.mimoo.ui.theme.glassChip
import com.miguelaetxio.mimoo.util.LrcLine
import com.miguelaetxio.mimoo.util.LrcParser

/**
 * S010 -- "reproductor expandido", rediseño completo pedido por
 * Miguel Ángel tras un primer intento fallido (carátula pequeña
 * mezclada con los controles, no era lo pedido). Orden EXACTO,
 * confirmado explícitamente:
 *   1. Controles arriba del todo (aleatorio/favorito/anterior/
 *      play-pausa/siguiente/repetir).
 *   2. Barra de progreso + tiempos, debajo de los controles.
 *   3. Carátula cuadrada GRANDE a la izquierda, metadatos (título,
 *      artista, streaming/local) a su derecha -- la fila más abajo
 *      del todo.
 * Tamaño de la carátula: lado = ancho de pantalla ÷ 2 (fórmula
 * explícita de Miguel Ángel), no un valor fijo en dp -- así escala
 * igual de bien en un móvil pequeño que en una tablet.
 *
 * onOpenQueue: tocar el bloque de metadatos abre la cola de sesión
 * (QueueScreen) -- mismo patrón ya existente, ahora sobre el bloque de
 * metadatos en vez de sobre el título suelto.
 * ---
 * S010 -- "expanded player", full redesign requested by Miguel Ángel
 * after a first failed attempt. EXACT order, explicitly confirmed:
 *   1. Controls at the very top.
 *   2. Progress bar + times, below the controls.
 *   3. Big square cover art on the left, metadata on its right -- the
 *      bottom-most row.
 * Cover art size: side = screen width ÷ 2 (Miguel Ángel's explicit
 * formula), not a fixed dp value -- scales the same on a small phone
 * and a tablet.
 *
 * S011 -- fallo real reportado por Miguel Ángel: al ser fijo y grande
 * (petición explícita suya en S010, ver arriba), en pantallas con
 * poco contenido propio -- Ajustes es el caso real que lo destapó --
 * el reproductor expandido tapaba opciones enteras de la pantalla sin
 * dejar ninguna forma de acceder a ellas mientras algo estuviera
 * sonando. *"habría que hacer el exoplayer que sea ocultable"* --
 * ahora es colapsable: un botón (flecha) alterna entre el diseño
 * expandido de arriba (sin tocar su orden ni tamaños, tal cual se
 * pidió) y una mini-barra de una sola fila (~56dp) con carátula
 * pequeña, título/artista y play/pausa. Estado en memoria del propio
 * Composable (`remember`) -- no sobrevive a matar la app, pero sí a
 * navegar entre pantallas, porque este Composable nunca sale de
 * composición al cambiar de pantalla (vive en el nivel de
 * MainActivity, fuera del NavHost).
 */
/**
 * S048 -- "compartir el tema que se está tocando" (petición explícita
 * de Miguel Ángel al cerrar S035). Mismo patrón Intent.ACTION_SEND que
 * `shareLink()` de LibraryScreen.kt, sin depender de esa pantalla ni
 * duplicar su función privada. Construye el enlace de YouTube estándar
 * a partir del youtubeId de la pista actual -- decisión propia (no
 * usa el sistema de hash de H10, pensado para compartir bloques
 * completos de biblioteca dentro de la propia app).
 */
private fun shareCurrentTrack(context: android.content.Context, youtubeId: String) {
    val url = "https://www.youtube.com/watch?v=$youtubeId"
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, url)
    }
    context.startActivity(android.content.Intent.createChooser(intent, null))
}

@Composable
fun PlayerBar(
    viewModel: PlayerBarViewModel = hiltViewModel(),
    onOpenQueue: () -> Unit,
    onOpenAlbum: (artistName: String, albumName: String) -> Unit,
    onOpenArtist: (artistName: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val isCurrentFavorite by viewModel.isCurrentFavorite.collectAsState()
    val coverArtUrl by viewModel.coverArtUrl.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val menuArtist by viewModel.menuArtist.collectAsState()
    val menuAlbum by viewModel.menuAlbum.collectAsState()
    val localFilePath by viewModel.localFilePath.collectAsState()
    val ringtoneMessage by viewModel.ringtoneMessage.collectAsState()
    val dislikeChoiceVisible by viewModel.dislikeChoiceVisible.collectAsState()
    val lyricsPanelVisible by viewModel.lyricsPanelVisible.collectAsState()
    val lyricsLoading by viewModel.lyricsLoading.collectAsState()
    val lyricsResult by viewModel.lyricsResult.collectAsState()

    // "Elegir como tono para un contacto" (2026-08-02) -- aviso final vía Toast,
    // PlayerBar no tiene Scaffold/SnackbarHost propio.
    val toastContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(ringtoneMessage) {
        ringtoneMessage?.let { message ->
            android.widget.Toast.makeText(toastContext, message, android.widget.Toast.LENGTH_LONG).show()
            viewModel.dismissRingtoneMessage()
        }
    }

    // "Elegir como tono para un contacto" (2026-08-02): selector de
    // contacto del sistema, se lanza tras conceder WRITE_CONTACTS. El
    // resultado (Uri del contacto elegido, null si canceló) se pasa
    // directamente al ViewModel.
    val pickContactLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickContact(),
    ) { contactUri ->
        if (contactUri != null) viewModel.setAsRingtoneForContact(contactUri)
    }
    // Permiso runtime peligroso, se solicita justo antes de abrir el
    // selector de contacto -- solo si se concede se lanza el picker.
    val requestContactsPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pickContactLauncher.launch(null)
    }

    // S027 -- estos tres diálogos van ANTES del `return` de más abajo
    // por falta de pista actual: el modal de streaming en concreto
    // puede dispararse precisamente cuando TODAVÍA no hay nada sonando
    // (primera reproducción de la sesión) -- si se colocan después del
    // `return`, no se llegan a mostrar nunca en ese caso. Bug real de
    // un primer intento de esta misma sesión.

    // S026 -- orden explícita de Miguel Ángel: mejor parar la Radio del
    // todo que meter un vídeo sin verificar. Ver
    // PlayerManager.resolveYoutubeCandidate()/dismissRadioNetworkLost().
    if (state.radioNetworkLost) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Radio detenida") },
            text = {
                Text(
                    "Se ha perdido la conexión y no se ha podido comprobar que el " +
                        "siguiente tema sea de verdad del artista sugerido. La Radio se " +
                        "ha detenido para no añadir un vídeo equivocado.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissRadioNetworkLost) {
                    Text("Reintentar")
                }
            },
        )
    }

    // H15 (miMooutCast), S032 -- bug real reportado por Miguel Ángel:
    // una sesión anclada en "Minimal Techno" sirvió un único tema y se
    // quedó muda para siempre, sin ningún aviso. El ancla en
    // miMooutCast nunca se relaja, así que cuando de verdad se agota
    // en las tres fuentes no hay nada más que ofrecer -- se avisa en
    // vez de dejar sonar el silencio.
    val exhaustedAnchor = state.miMooutCastAnchorExhausted
    if (exhaustedAnchor != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Sin más música") },
            text = {
                Text(
                    "No queda más música que encaje con \"$exhaustedAnchor\". Prueba con otra " +
                        "combinación en miMooutCast.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMiMooutCastAnchorExhausted) {
                    Text("Entendido")
                }
            },
        )
    }

    // S027 -- red de seguridad: si la Radio llega al final de la cola
    // sin ancla (caso residual, p.ej. pista local sin artista), se
    // pregunta igual. El disparo normal es el de más abajo, al
    // arrancar el streaming.
    val radioArtistPromptTitle = state.radioArtistPromptTrackTitle
    if (radioArtistPromptTitle != null) {
        var artistInput by remember(radioArtistPromptTitle) { mutableStateOf("") }
        var songInput by remember(radioArtistPromptTitle) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::dismissRadioArtistPrompt,
            title = { Text("¿Quién es el artista?") },
            text = {
                Column {
                    Text(
                        "No se ha podido identificar el artista de \"$radioArtistPromptTitle\". " +
                            "Sin esa información la Radio no puede arrancar.",
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = artistInput,
                        onValueChange = { artistInput = it },
                        label = { Text("Artista") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = songInput,
                        onValueChange = { songInput = it },
                        label = { Text("Título de la canción") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.submitRadioArtist(artistInput, songInput) },
                    enabled = artistInput.isNotBlank() && songInput.isNotBlank(),
                ) {
                    Text("Empezar Radio")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRadioArtistPrompt) {
                    Text("Cancelar")
                }
            },
        )
    }

    // S027 -- TERCERA corrección: orden explícita de Miguel Ángel,
    // "tanto si hay título y artista, como si no, debe,
    // obligatoriamente saltar el modal, bien para preguntar o bien
    // para informar de título y artista". Ya no es condicional a que
    // falte el dato -- play() lo dispara SIEMPRE en streaming,
    // precargado con lo que se haya resuelto. El nombre del canal de
    // YouTube no aparece en ningún sitio de este modal.
    val streamArtistPromptTitle = state.streamArtistPromptVideoTitle
    if (streamArtistPromptTitle != null) {
        val prefilledArtist = state.streamArtistPromptPrefilledArtist
        val prefilledTitle = state.streamArtistPromptPrefilledTitle
        var streamArtistInput by remember(streamArtistPromptTitle) {
            mutableStateOf(prefilledArtist.orEmpty())
        }
        var streamSongInput by remember(streamArtistPromptTitle) {
            mutableStateOf(prefilledTitle ?: streamArtistPromptTitle)
        }
        val isInforming = !prefilledArtist.isNullOrBlank()
        AlertDialog(
            onDismissRequest = viewModel::dismissStreamArtistPrompt,
            title = { Text("¿Quién es el artista?") },
            text = {
                Column {
                    Text(
                        if (isInforming) {
                            "Antes de reproducir en streaming, confirma que esto es correcto."
                        } else {
                            "\"$streamArtistPromptTitle\" no trae artista. Sin esa información " +
                                "no se puede reproducir en streaming."
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = streamArtistInput,
                        onValueChange = { streamArtistInput = it },
                        label = { Text("Artista") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = streamSongInput,
                        onValueChange = { streamSongInput = it },
                        label = { Text("Título de la canción") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.submitStreamArtist(streamArtistInput, streamSongInput) },
                    enabled = streamArtistInput.isNotBlank() && streamSongInput.isNotBlank(),
                ) {
                    Text("Reproducir")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissStreamArtistPrompt) {
                    Text("Cancelar")
                }
            },
        )
    }

    // H16 -- botón "no me gusta" del reproductor: pregunta si el
    // rechazo es del artista o del tema que suena en ese momento --
    // ver ANNEX_H16.md, "Decisiones ya cerradas con Miguel Ángel en
    // S029", punto 4. Mismo criterio que los otros diálogos de esta
    // pantalla: va ANTES del `return` de más abajo.
    if (dislikeChoiceVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDislikeChoice,
            title = { Text("No me gusta") },
            text = { Text("¿El rechazo es del artista o solo de este tema?") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDislikeArtist) {
                    Text("Artista")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::confirmDislikeTrack) {
                    Text("Solo este tema")
                }
            },
        )
    }

    val title = state.currentTitle ?: return
    val artSize = LocalConfiguration.current.screenWidthDp.dp / 2
    var isExpanded by remember { mutableStateOf(true) }
    // Petición explícita de Miguel Ángel (2026-08-24): botón "+" para
    // añadir el tema actual a una lista (nueva o existente) sin tener
    // que ir a Biblioteca/Búsqueda -- reutiliza tal cual el mismo
    // AddToPlaylistDialog que ya usan LibraryScreen y SearchScreen,
    // con una sola pista (youtubeIds = listOf(...)).
    var showAddToPlaylist by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // H17 (S031, bloque 2) -- panel de karaoke, "justo encima del
        // ExoPlayer" (punto 4 de "Puntos de diseño -- CERRADOS EN
        // S031", DOCS/ANNEX_H17.md). Solo con el reproductor
        // expandido -- la mini-barra colapsada no tiene sitio
        // razonable para él.
        if (isExpanded && lyricsPanelVisible) {
            KaraokeLyricsPanel(
                loading = lyricsLoading,
                lyrics = lyricsResult,
                positionMs = positionMs,
            )
        }
    Surface(tonalElevation = 4.dp) {
        if (!isExpanded) {
            PlayerBarCollapsed(
                title = title,
                artist = state.currentArtist,
                coverArtUrl = coverArtUrl,
                isPlaying = state.isPlaying,
                showDislikeButton = state.currentYoutubeId != null,
                onTogglePlayPause = viewModel::togglePlayPause,
                onDislike = viewModel::requestDislikeChoice,
                onExpand = { isExpanded = true },
            )
            return@Surface
        }
        Column(modifier = Modifier.fillMaxWidth()) {

            // 1 -- Controles, arriba del todo.
            //
            // Fix real (2026-07-24, reportado por Miguel Ángel): con
            // varios controles opcionales visibles a la vez (favorito +
            // descarga + menú de tres puntos + aleatorio + cíclico), la
            // fila de controles superaba el ancho de pantalla -- un Row
            // normal no encoge ni envuelve sus hijos, así que el botón
            // de contraer (el último) se salía por el borde derecho,
            // invisible e inalcanzable, dejando el reproductor expandido
            // fijo tapando el resto de la pantalla sin ninguna forma de
            // colapsarlo. Fix: el botón de contraer sale de la fila
            // scrollable y queda FIJO al final, siempre visible pase lo
            // que pase con el resto de controles; los controles variables
            // (aleatorio..menú de tres puntos) quedan en una sub-fila con
            // weight(1f) + scroll horizontal propio -- con pocos
            // controles se ve igual que antes (SpaceBetween reparte el
            // espacio sobrante), con muchos se puede desplazar en vez de
            // desbordar fuera de pantalla.
            // ---
            // Real fix (2026-07-24, reported by Miguel Ángel): with
            // several optional controls visible at once (favorite +
            // download + three-dot menu + shuffle + repeat), the
            // controls row exceeded screen width -- a plain Row doesn't
            // shrink or wrap its children, so the collapse button (the
            // last one) fell off the right edge, invisible and
            // unreachable, leaving the expanded player fixed and
            // covering the rest of the screen with no way to collapse
            // it. Fix: the collapse button moves out of the scrollable
            // row and stays FIXED at the end, always visible no matter
            // what happens with the rest of the controls; the variable
            // controls (shuffle..three-dot menu) live in a sub-row with
            // weight(1f) + its own horizontal scroll -- with few controls
            // it looks the same as before (SpaceBetween distributes the
            // leftover space), with many it can be scrolled instead of
            // overflowing off-screen.
            // S048 -- rediseño explícito de Miguel Ángel al cerrar S035:
            // "tenemos muchos controles... habría que poner una fila con
            // lo que son los controles de reproducción y otra fila con
            // los like/dislike/add2list/download/share." Fila 1 (esta):
            // SOLO controles de transporte -- aleatorio, anterior,
            // play/pausa, siguiente, cíclico -- exactamente lo que había
            // antes, sin tocar orden ni tamaños, menos los botones que
            // bajan a la Fila 2. El botón de contraer se queda FIJO al
            // final, fuera de la sub-fila scrollable, mismo fix de
            // S011/2026-07-24 (con menos botones ya no debería
            // desbordar, pero se conserva el mecanismo por seguridad).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                if (state.queueSize > 1) {
                    // H13 -- chapita de cristal SIEMPRE presente (todos
                    // los botones del reproductor la llevan, petición
                    // explícita de Miguel Ángel), ENCENDIDA cuando el
                    // modo está activo. El tint deja de ser el
                    // diferenciador: `colorScheme.primary` es blanco en
                    // esta paleta, igual que el inactivo -- ver
                    // GlassTokens.activeFillTop.
                    GlassIconButton(
                        onClick = viewModel::toggleShuffle,
                        active = state.shuffleModeEnabled,
                    ) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = if (state.shuffleModeEnabled) {
                                "Desactivar orden aleatorio"
                            } else {
                                "Activar orden aleatorio"
                            },
                            tint = if (state.shuffleModeEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                }

                // H08 -- "anterior" no depende de que haya más de una
                // pista en cola, ver PlayerManager.playPrevious().
                GlassIconButton(onClick = viewModel::playPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior")
                }

                GlassIconButton(onClick = viewModel::togglePlayPause) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                    )
                }

                if (state.queueSize > 1) {
                    GlassIconButton(
                        onClick = viewModel::playNext,
                        enabled = state.repeatModeEnabled ||
                            state.queueIndex < state.queueSize - 1,
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente")
                    }
                }

                if (state.queueSize > 1) {
                    // H13 -- misma chapita encendida que aleatorio.
                    GlassIconButton(
                        onClick = viewModel::toggleRepeat,
                        active = state.repeatModeEnabled,
                    ) {
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = if (state.repeatModeEnabled) {
                                "Desactivar reproducción cíclica"
                            } else {
                                "Activar reproducción cíclica"
                            },
                            tint = if (state.repeatModeEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                }
                } // fin de la sub-fila scrollable de controles de transporte

                // S011 -- botón para colapsar el reproductor a la
                // mini-barra, ver comentario de cabecera. Fix real
                // (2026-07-24): FUERA de la sub-fila scrollable de
                // arriba, así queda fijo y siempre visible aunque los
                // demás controles necesiten desplazarse.
                GlassIconButton(onClick = { isExpanded = false }) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = "Contraer reproductor")
                }
            }

            // S048 -- Fila 2 del rediseño: like/dislike/add2list/
            // download/share, orden EXACTO dado por Miguel Ángel.
            // "Ver álbum/artista" (menú de tres puntos, H12) no estaba
            // en esa lista textual -- decisión propia, sin volver a
            // preguntar (S048, incidente de proceso): se queda al final
            // de esta misma fila, porque es una acción secundaria del
            // mismo tipo (no es transporte), no porque encaje en el
            // orden dado.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (state.currentYoutubeId != null) {
                    // Favorito ("like") conserva su patrón propio (glifo
                    // relleno vs contorno + amarillo), que ya se lee de
                    // un vistazo sin necesidad de placa encendida.
                    GlassIconButton(onClick = viewModel::toggleCurrentFavorite) {
                        Icon(
                            if (isCurrentFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isCurrentFavorite) {
                                "Quitar de favoritos"
                            } else {
                                "Añadir a favoritos"
                            },
                            tint = if (isCurrentFavorite) {
                                MaterialTheme.colorScheme.error
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                }

                // H16 -- botón "no me gusta", mismo criterio de
                // visibilidad que Favorito (solo si la pista actual
                // tiene equivalente real en la biblioteca). Sin estado
                // ON/OFF propio -- es una acción que abre el diálogo
                // "¿artista o tema?", no un chequeo del estado actual
                // (la gestión de lo ya marcado vive en la pantalla
                // CRUD, "Lista negra").
                if (state.currentYoutubeId != null) {
                    GlassIconButton(onClick = viewModel::requestDislikeChoice) {
                        Icon(
                            Icons.Filled.ThumbDown,
                            contentDescription = "No me gusta",
                        )
                    }
                }

                if (state.currentYoutubeId != null) {
                    // Petición explícita de Miguel Ángel (2026-08-24):
                    // botón "+" para añadir el tema actual a una lista
                    // (nueva o existente) directamente desde el
                    // reproductor.
                    GlassIconButton(onClick = { showAddToPlaylist = true }) {
                        Icon(
                            Icons.Filled.PlaylistAdd,
                            contentDescription = "Añadir a lista",
                        )
                    }
                }

                // S011 -- botón de descarga (petición explícita de
                // Miguel Ángel, junto con el de la notificación -- ver
                // MiMooPlaybackService para el límite real de huecos
                // de la notificación del sistema, que este reproductor
                // propio no sufre). Solo visible si la pista actual
                // tiene equivalente real en la biblioteca y todavía no
                // está descargada -- mismo criterio que el favorito de
                // arriba (state.currentYoutubeId != null).
                if (state.currentYoutubeId != null &&
                    downloadStatus != com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.DONE &&
                    downloadStatus != com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.QUEUED &&
                    downloadStatus != com.miguelaetxio.mimoo.data.local.entity.DownloadStatus.DOWNLOADING
                ) {
                    GlassIconButton(onClick = viewModel::downloadCurrentTrack) {
                        Icon(Icons.Filled.Download, contentDescription = "Descargar")
                    }
                }

                // S048 -- "compartir el tema que se está tocando"
                // (petición explícita de Miguel Ángel al cerrar S035).
                // Comparte el enlace de YouTube del tema (decisión
                // propia, sin volver a preguntar): es el mecanismo más
                // simple, no depende del sistema de hash de H10 (pensado
                // para compartir bloques completos de biblioteca dentro
                // de la propia app, no un tema suelto sonando). Mismo
                // patrón Intent.ACTION_SEND que ya usa LibraryScreen
                // (shareLink()) -- ver PlayerShare.kt. Solo visible con
                // youtubeId real, igual criterio que el resto de esta
                // fila.
                if (state.currentYoutubeId != null) {
                    val shareContext = androidx.compose.ui.platform.LocalContext.current
                    GlassIconButton(
                        onClick = {
                            shareCurrentTrack(shareContext, state.currentYoutubeId!!)
                        },
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Compartir")
                    }
                }

                // H12 (S018, roadmap punto 6) -- menú de tres puntos:
                // "Ver álbum"/"Ver artista". Oculto por completo si no
                // hay artista resoluble (ver PlayerBarViewModel.
                // resolveMenuArtist()) -- un menú con cero opciones no
                // aporta nada. "Ver álbum" solo aparece si además se
                // conoce el álbum (fila local con `album` no nulo).
                if (menuArtist != null) {
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        GlassIconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (menuAlbum != null) {
                                DropdownMenuItem(
                                    text = { Text("Ver álbum") },
                                    onClick = {
                                        showMenu = false
                                        onOpenAlbum(menuArtist!!, menuAlbum!!)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Ver artista") },
                                onClick = {
                                    showMenu = false
                                    onOpenArtist(menuArtist!!)
                                },
                            )
                            // "Elegir como tono para un contacto"
                            // (2026-08-02) -- solo si la pista actual
                            // está descargada (localFilePath != null):
                            // hace falta poder leer sus bytes para
                            // instalarla como tono, ver
                            // ContactRingtoneRepository.
                            if (localFilePath != null) {
                                DropdownMenuItem(
                                    text = { Text("Elegir como tono para un contacto") },
                                    onClick = {
                                        showMenu = false
                                        requestContactsPermissionLauncher.launch(
                                            android.Manifest.permission.WRITE_CONTACTS,
                                        )
                                    },
                                )
                            }
                            // H17 (S031, bloque 2) -- entrada de Karaoke
                            // & Lyrics. Excluida explícitamente cuando
                            // el stream en curso es una emisora en
                            // directo (Radio-Browser.info u otro stream
                            // sin metadatos fiables, ver
                            // PlaybackState.currentIsRadioStation) --
                            // punto 6 de "Puntos de diseño -- CERRADOS
                            // EN S031" en DOCS/ANNEX_H17.md.
                            if (!state.currentIsRadioStation) {
                                DropdownMenuItem(
                                    text = {
                                        Text(if (lyricsPanelVisible) "Ocultar karaoke" else "Karaoke")
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.toggleLyricsPanel()
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // 2 -- Barra de progreso + tiempos, debajo de los controles.
            if (state.durationMs > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatPlaybackTime(positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = positionMs.coerceIn(0L, state.durationMs).toFloat(),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..state.durationMs.toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Text(
                        text = "-" + formatPlaybackTime(state.durationMs - positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // 3 -- Carátula grande (lado = ancho de pantalla / 2) a la
            // izquierda, metadatos a su derecha. Fila más abajo del todo.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable(onClick = onOpenQueue),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(artSize)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    if (coverArtUrl != null) {
                        SubcomposeAsyncImage(
                            model = coverArtUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            error = { PlayerBarArtPlaceholder() },
                        )
                    } else {
                        PlayerBarArtPlaceholder()
                    }
                }

                Spacer(Modifier.width(16.dp))

                // H13 -- petición explícita de Miguel Ángel: el bloque
                // de metadatos junto a la carátula también sobre
                // cristal esmerilado, como el resto de la app. Es
                // clicable (abre la cola de sesión), así que cristal
                // interactivo, no decorativo.
                // ---
                // H13 -- explicit request: the metadata block next to
                // the cover art also sits on frosted glass. It is
                // clickable (opens the session queue), so interactive
                // glass, not decorative.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .glassChip()
                        .padding(12.dp),
                ) {
                    Text(
                        text = title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!state.currentArtist.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.currentArtist!!,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // H09 -- icono + etiqueta corta en vez de una frase
                    // larga, ver historial de esta misma fila más abajo.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (state.isLocal) Icons.Filled.Download else Icons.Filled.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (state.isLocal) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (state.isLocal) "Local" else "Streaming",
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.isLocal) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                        )
                    }
                }
            }

            // S010 -- pequeña franja vacía del color de fondo debajo
            // de la carátula, para que no quede pegada al filo
            // inferior de la pantalla. Petición explícita: la mitad de
            // alta que la franja de controles de arriba.
            // ---
            // S010 -- small empty strip in the background color below
            // the cover art, so it doesn't sit flush against the
            // bottom edge of the screen. Explicit request: half as
            // tall as the controls strip above.
            Spacer(Modifier.height(24.dp))
        }
    }
    }

    if (showAddToPlaylist) {
        state.currentYoutubeId?.let { youtubeId ->
            AddToPlaylistDialog(
                youtubeIds = listOf(youtubeId),
                onDismiss = { showAddToPlaylist = false },
            )
        }
    }
}

/**
 * H17 (S031, bloque 2) -- panel de karaoke sobre el ExoPlayer, ver
 * "Puntos de diseño -- CERRADOS EN S031" de DOCS/ANNEX_H17.md, puntos
 * 2 y 4. Altura variable según el caso:
 * - `lyrics.syncedLyrics != null` -> 1/3 de pantalla (S049, antes 1/9
 *   -- corregido a petición explícita de Miguel Ángel: "debería de ser
 *   del mismo tamaño que cuando no lo están"), teleprompter con la
 *   línea actual resaltada CENTRADA en el hueco del panel (H17, bug
 *   real, 2026-08-07 -- antes quedaba pegada arriba) y las líneas
 *   siguientes visibles debajo -- se auto-desplaza con `positionMs`,
 *   sin scroll manual.
 * - `lyrics.syncedLyrics == null && lyrics.plainLyrics != null` -> 1/3
 *   de pantalla, letra completa scrolleable, SIN ningún aviso.
 * - Sin ninguna letra (o mientras `loading`) -> 1/9 de pantalla,
 *   mensaje informativo mínimo -- este caso SÍ se queda pequeño a
 *   propósito, no es contenido real que leer.
 * ---
 * H17 (S031, block 2) -- karaoke panel over the ExoPlayer. Variable
 * height: 1/3 screen for either synced karaoke or scrollable plain
 * lyrics (S049, same size for both), 1/9 for the "no lyrics"/loading
 * message only.
 */
@Composable
private fun KaraokeLyricsPanel(
    loading: Boolean,
    lyrics: LyricsResult?,
    positionMs: Long,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val syncedLyrics = lyrics?.syncedLyrics
    val plainLyrics = lyrics?.plainLyrics

    when {
        !loading && syncedLyrics == null && plainLyrics != null -> {
            // Letra plana scrolleable, sin resaltado, sin aviso alguno
            // -- punto 2 revisado tras cerrar el punto 4: el aviso de
            // "sin karaoke" queda reservado exclusivamente para cuando
            // no hay ninguna letra en absoluto.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight / 3)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .glassChip(interactive = false)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = plainLyrics,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        !loading && syncedLyrics != null -> {
            val lines = remember(syncedLyrics) { LrcParser.parse(syncedLyrics) }
            KaraokeTeleprompter(lines = lines, positionMs = positionMs, screenHeight = screenHeight)
        }
        else -> {
            // `loading == true`, o consultado y confirmado sin ninguna
            // letra (ni sincronizada ni plana) -- mismo panel mínimo
            // para ambos casos, mensaje distinto.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight / 9)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .glassChip(interactive = false),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (loading) "Buscando letra..." else "No hay letra disponible para este tema.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Teleprompter de karaoke -- panel de 1/9 de pantalla.
 *
 * Bug real reportado por Miguel Ángel (2026-08-07): la línea activa
 * quedaba pegada arriba del todo del panel en vez de centrada en el
 * hueco disponible -- `animateScrollToItem(index)` a secas posiciona
 * el inicio del item en el borde superior del viewport, que es lo que
 * se veía. Corregido con el patrón estándar de "centrar en dos
 * pasos": primero un scroll normal para que el item entre en
 * `layoutInfo`, luego una corrección con el offset exacto
 * (viewport - alto del item) / 2 leído de `visibleItemsInfo` -- no
 * hace falta precalcular alturas de fuente a mano (la línea activa
 * usa `bodyLarge` en negrita, más alta que el resto en `bodySmall`),
 * el propio layout ya sabe cuánto mide.
 * ---
 * Karaoke teleprompter -- 1/9-screen panel.
 *
 * Real bug reported by Miguel Ángel (2026-08-07): the active line sat
 * glued to the very top of the panel instead of centered in the
 * available space -- a bare `animateScrollToItem(index)` places the
 * item's start at the viewport's top edge, which is exactly what was
 * showing. Fixed with the standard "two-step centering" pattern:
 * first a normal scroll so the item enters `layoutInfo`, then a
 * correction scroll with the exact offset (viewport - item height) /
 * 2 read from `visibleItemsInfo` -- no need to hand-precompute font
 * heights (the active line uses bold `bodyLarge`, taller than the
 * rest in `bodySmall`), the layout itself already knows its size.
 */
@Composable
private fun KaraokeTeleprompter(lines: List<LrcLine>, positionMs: Long, screenHeight: androidx.compose.ui.unit.Dp) {
    val currentIndex = remember(lines, positionMs) {
        val idx = lines.indexOfLast { it.timeMs <= positionMs }
        idx.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (lines.isEmpty()) return@LaunchedEffect
        val targetIndex = currentIndex.coerceIn(0, lines.size - 1)
        // Petición explícita de Miguel Ángel (2026-08-27): "tiene
        // movimientos muy bruscos, la frase resaltada sube hasta
        // arriba y luego baja al medio. No hace un smooth scrolling y
        // cansa." Causa real: dos animaciones seguidas --
        // `animateScrollToItem(targetIndex)` sube el tema al principio
        // del hueco (comportamiento por defecto de LazyColumn), y solo
        // DESPUÉS se medía el hueco real y se corregía al centro con
        // una SEGUNDA animación -- de ahí el "sube y luego baja".
        //
        // Ahora, una sola animación directa al centro. La altura de
        // línea se estima ANTES de moverse, a partir de cualquier
        // línea que ya esté visible en este mismo instante (todas
        // comparten `maxLines = 1`, así que la altura real apenas
        // varía entre la resaltada y el resto) -- no hace falta
        // esperar a que la pista destino esté renderizada para saber
        // cuánto mide.
        val info = listState.layoutInfo
        val estimatedItemHeight = info.visibleItemsInfo.firstOrNull()?.size ?: 0
        val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
        val centerOffset = ((viewportHeight - estimatedItemHeight) / 2).coerceAtLeast(0)
        listState.animateScrollToItem(targetIndex, -centerOffset)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Petición explícita de Miguel Ángel (2026-08-27): "el
            // karaoke me equivoqué al poner pequeña la 'pantalla' de
            // salida de las letras cuando están sincronizadas, debería
            // de ser del mismo tamaño que cuando no lo están" -- antes
            // 1/9 de pantalla aquí frente a 1/3 en la letra plana (ver
            // KaraokeLyricsPanel más arriba), decisión original de
            // S031 ahora corregida a petición suya: mismo tamaño en
            // los dos casos.
            .height(screenHeight / 3)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .glassChip(interactive = false)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (lines.isEmpty()) {
            Text(
                text = "No hay letra disponible para este tema.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(state = listState, userScrollEnabled = false) {
                itemsIndexed(lines) { index, line ->
                    Text(
                        text = line.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = if (index == currentIndex) {
                            MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                        color = if (index == currentIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** S011 -- mini-barra cuando el reproductor está contraído, ver comentario de cabecera de PlayerBar(). Una sola fila, ~56dp. */
@Composable
private fun PlayerBarCollapsed(
    title: String,
    artist: String?,
    coverArtUrl: String?,
    isPlaying: Boolean,
    showDislikeButton: Boolean,
    onTogglePlayPause: () -> Unit,
    onDislike: () -> Unit,
    onExpand: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            if (coverArtUrl != null) {
                SubcomposeAsyncImage(
                    model = coverArtUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    error = { PlayerBarArtPlaceholder() },
                )
            } else {
                PlayerBarArtPlaceholder()
            }
        }
        Spacer(Modifier.width(12.dp))
        // H13 -- mismo tratamiento que el reproductor expandido
        // (metadatos y botones sobre cristal), para que contraer el
        // reproductor no cambie el lenguaje visual.
        Column(
            modifier = Modifier
                .weight(1f)
                .glassChip()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!artist.isNullOrBlank()) {
                Text(
                    text = artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // H16 -- mismo criterio de ubicación que H13 (aleatorio/
        // cíclico): el botón "no me gusta" vive en AMBOS sitios,
        // mini-barra y expandido -- ver ANNEX_H16.md, "Puntos de
        // diseño -- CERRADOS", punto 1.
        if (showDislikeButton) {
            GlassIconButton(onClick = onDislike) {
                Icon(Icons.Filled.ThumbDown, contentDescription = "No me gusta")
            }
        }
        GlassIconButton(onClick = onTogglePlayPause) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
            )
        }
        GlassIconButton(onClick = onExpand) {
            Icon(Icons.Filled.ExpandLess, contentDescription = "Expandir reproductor")
        }
    }
}

/**
 * H13 -- botón del reproductor sobre chapita de cristal circular.
 * Petición explícita de Miguel Ángel: *"el exoplayer carece del efecto
 * cristal esmerilado en los botones"*. Todos los botones del
 * reproductor (expandido y mini-barra) pasan por aquí, así que el
 * aspecto se ajusta en un único sitio.
 *
 * `active` enciende la placa (`GlassTokens.activeFillTop`) para los
 * controles con estado ON/OFF -- aleatorio y cíclico. El `padding`
 * va ANTES del cristal a propósito: deja aire real entre chapitas
 * contiguas sin que ese aire se pinte de cristal.
 * ---
 * H13 -- player button on a circular glass chip. Every player button
 * (expanded and mini bar) goes through here, so the look is tuned in a
 * single place. `active` lights the plate up for ON/OFF controls
 * (shuffle, repeat). The padding comes BEFORE the glass on purpose:
 * real breathing room between adjacent chips, without that room being
 * painted as glass.
 */
@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .glassChip(shape = CircleShape, active = active),
    ) {
        IconButton(onClick = onClick, enabled = enabled, content = content)
    }
}

/**
 * Petición explícita de Miguel Ángel (2026-08-26): "cuando no haya
 * carátula, por ejemplo emisoras de radio online, metemos el logo
 * como carátula" -- antes mostraba un icono genérico de nota musical
 * (Icons.Filled.MusicNote), sin identidad de marca. Ahora el propio
 * icono de la app (mismo recurso que el lanzador, `ic_launcher`).
 */
@Composable
private fun PlayerBarArtPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.miguelaetxio.mimoo.R.mipmap.ic_launcher),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** "m:ss", igual que el resto de la app (ver formatDuration en Búsqueda/Biblioteca). */
private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

package com.miguelaetxio.mimoo.data.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * H07 PARTE 1 (regla de negocio de Miguel Ángel, S008): "si no hay
 * conexión de red, los borrados y los añadidos no están permitidos
 * hasta que se recupere la conexión" -- para que nunca pueda existir
 * un estado "solo local, todavía sin subir a Drive", que era la causa
 * raíz del bug real reportado (pistas descargadas sin conexión-o-sin-
 * push que un dispositivo interpretaba como "borradas en otro
 * sitio"). La app se puede seguir USANDO offline con normalidad
 * (escuchar lo ya descargado, navegar la biblioteca) -- esta
 * comprobación solo bloquea MODIFICAR el contenido (añadir/borrar
 * pistas, favoritos, playlists).
 * ---
 * H07 PART 1 (Miguel Ángel's business rule, S008): "if there's no
 * network connection, deletions and additions aren't allowed until
 * the connection is back" -- so a "local-only, not yet pushed to
 * Drive" state can never exist, which was the root cause of the real
 * bug reported (tracks downloaded without connection-or-push that a
 * device interpreted as "deleted somewhere else"). The app can still
 * be USED offline normally (listen to what's already downloaded,
 * browse the library) -- this check only blocks MODIFYING content
 * (adding/removing tracks, favorites, playlists).
 */
@Singleton
class NetworkConnectivityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    fun isConnected(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * SEGUNDO bug real reportado por Miguel Ángel, con capturas de
     * pantalla y logs en vivo de la versión 505 -- confirmó
     * explícitamente que el cartel "Radio detenida" seguía saliendo
     * CON conexión real funcionando (la propia cola de reproducción
     * mostraba streaming en marcha en el mismo instante). Causa: la
     * primera corrección (RadioRepository, commit 79ac73d) exigía
     * `isConnected()`, que depende de `NET_CAPABILITY_VALIDATED` --
     * una bandera que Android pone a `true` solo cuando SU PROPIA
     * sonda de validación interna ha terminado con éxito, y que es
     * conocida por quedarse en `false` de forma transitoria (tras
     * salir de Doze, cambios de red, o simplemente retraso de esa
     * sonda interna) incluso con Internet funcionando de verdad. El
     * resultado fue el mismo bug con un disfraz nuevo: la Radio ahora
     * exigía DOS condiciones para declarar "sin red" en vez de una,
     * pero la segunda condición era igual de propensa a falsos
     * positivos que la primera.
     *
     * Aquí no se pregunta a Android qué CREE que tiene -- se prueba
     * de verdad: una petición HTTP real y rápida (`HEAD`, sin cuerpo)
     * contra un host elegido por ser extremadamente fiable y ajeno a
     * MusicBrainz/Discogs/Wikidata (para que un fallo de esos tres
     * servicios en concreto no contamine la sonda). Cualquier
     * respuesta HTTP -- incluso un error del servidor -- cuenta como
     * "hay red", porque para llegar a recibir una respuesta HTTP el
     * paquete tuvo que salir del teléfono y volver. Solo una
     * excepción de red real (timeout, DNS, conexión rechazada) cuenta
     * como "no hay red".
     * ---
     * SECOND real bug reported by Miguel Ángel, with screenshots and
     * live logs from version 505 -- he explicitly confirmed the
     * "Radio detenida" banner kept appearing WITH real connectivity
     * working (the playback queue itself showed active streaming at
     * that exact moment). Cause: the first fix (RadioRepository,
     * commit 79ac73d) required `isConnected()`, which depends on
     * `NET_CAPABILITY_VALIDATED` -- a flag Android only sets `true`
     * once ITS OWN internal validation probe has succeeded, and known
     * to sit at `false` transiently (after leaving Doze, network
     * switches, or simply that internal probe lagging) even with
     * real working internet. The result was the same bug wearing a
     * new disguise: Radio now required TWO conditions to declare
     * "no network" instead of one, but the second condition was just
     * as prone to false positives as the first.
     *
     * This doesn't ask Android what it BELIEVES it has -- it actually
     * tests it: a real, fast HTTP request (`HEAD`, no body) against a
     * host chosen for being extremely reliable and unrelated to
     * MusicBrainz/Discogs/Wikidata (so a failure specific to those
     * three services doesn't contaminate the probe). Any HTTP
     * response -- even a server error -- counts as "there's a
     * network", because receiving any HTTP response at all means the
     * packet made it out of the phone and back. Only a real network
     * exception (timeout, DNS failure, connection refused) counts as
     * "no network".
     */
    suspend fun hasRealInternetAccess(): Boolean = withContext(Dispatchers.IO) {
        val probeClient = okHttpClient.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://www.gstatic.com/generate_204")
            .head()
            .build()
        try {
            probeClient.newCall(request).execute().use { true }
        } catch (e: Exception) {
            false
        }
    }
}

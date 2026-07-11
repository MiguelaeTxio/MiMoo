package com.miguelaetxio.mimoo.data.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
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
}

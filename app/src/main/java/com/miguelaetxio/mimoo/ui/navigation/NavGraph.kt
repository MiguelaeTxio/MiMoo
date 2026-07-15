package com.miguelaetxio.mimoo.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.miguelaetxio.mimoo.ui.albumsearch.AlbumSearchScreen
import com.miguelaetxio.mimoo.ui.downloads.DownloadsScreen
import com.miguelaetxio.mimoo.ui.importlink.ImportLinkScreen
import com.miguelaetxio.mimoo.ui.library.LibraryScreen
import com.miguelaetxio.mimoo.ui.playlist.PlaylistDetailScreen
import com.miguelaetxio.mimoo.ui.playlist.PlaylistsScreen
import com.miguelaetxio.mimoo.ui.queue.QueueScreen
import com.miguelaetxio.mimoo.ui.radiobrowser.RadioBrowserScreen
import com.miguelaetxio.mimoo.ui.search.SearchScreen
import com.miguelaetxio.mimoo.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Search : Screen("search")
    object Library : Screen("library")
    object Playlists : Screen("playlists")
    object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun routeFor(playlistId: Long) = "playlist/$playlistId"
    }
    object AlbumSearch : Screen("album_search")
    object ImportLink : Screen("import_link?url={url}") {
        // H08 PARTE 1 (S009) -- url opcional: null para el flujo
        // original (pegar a mano), o la url de una playlist/canal ya
        // encontrado por búsqueda, para saltarse el paso de pegarlo.
        // ---
        // H08 PARTE 1 (S009) -- optional url: null for the original
        // flow (paste by hand), or the url of a playlist/channel
        // already found by search, to skip the paste-it-yourself
        // step.
        fun routeFor(url: String? = null) =
            if (url != null) "import_link?url=${Uri.encode(url)}" else "import_link"
    }
    object Downloads : Screen("downloads")
    object Queue : Screen("queue")
    object RadioBrowser : Screen("radio_browser")
    object Channels : Screen("channels")
    object Settings : Screen("settings")
}

@Composable
fun MiMooNavGraph(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Search.route,
    ) {
        composable(Screen.Search.route) {
            SearchScreen(
                onOpenDrawer = onOpenDrawer,
                onOpenExternalLink = { url ->
                    navController.navigate(Screen.ImportLink.routeFor(url))
                },
            )
        }
        composable(Screen.Library.route) {
            LibraryScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Screen.Playlists.route) {
            PlaylistsScreen(
                onOpenDrawer = onOpenDrawer,
                onOpenPlaylist = { playlistId ->
                    navController.navigate(
                        Screen.PlaylistDetail.routeFor(playlistId),
                    )
                },
            )
        }
        composable(
            Screen.PlaylistDetail.route,
            arguments = listOf(
                navArgument("playlistId") { type = NavType.LongType },
            ),
        ) {
            PlaylistDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.AlbumSearch.route) {
            AlbumSearchScreen(
                onOpenDrawer = onOpenDrawer,
                onNavigateToLibrary = {
                    navController.navigate(Screen.Library.route)
                },
            )
        }
        composable(
            Screen.ImportLink.route,
            arguments = listOf(
                navArgument("url") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ImportLinkScreen(
                onOpenDrawer = onOpenDrawer,
                onNavigateToLibrary = {
                    navController.navigate(Screen.Library.route)
                },
            )
        }
        composable(Screen.Downloads.route) {
            DownloadsScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Screen.Queue.route) {
            QueueScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.RadioBrowser.route) {
            RadioBrowserScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Screen.Channels.route) {
            com.miguelaetxio.mimoo.ui.channels.ChannelsScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onOpenDrawer = onOpenDrawer)
        }
    }
}

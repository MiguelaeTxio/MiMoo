package com.miguelaetxio.mimoo.ui.navigation

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
    object ImportLink : Screen("import_link")
    object Downloads : Screen("downloads")
    object Queue : Screen("queue")
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
            SearchScreen(onOpenDrawer = onOpenDrawer)
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
        composable(Screen.ImportLink.route) {
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
        composable(Screen.Settings.route) {
            SettingsScreen(onOpenDrawer = onOpenDrawer)
        }
    }
}

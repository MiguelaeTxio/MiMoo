package com.miguelaetxio.mimoo.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.miguelaetxio.mimoo.ui.album.AlbumScreen
import com.miguelaetxio.mimoo.ui.artist.ArtistScreen
import com.miguelaetxio.mimoo.ui.disliked.DislikedScreen
import com.miguelaetxio.mimoo.ui.downloads.DownloadsScreen
import com.miguelaetxio.mimoo.ui.explorer.ExplorerScreen
import com.miguelaetxio.mimoo.ui.favorites.FavoritesScreen
import com.miguelaetxio.mimoo.ui.importlink.ImportLinkScreen
import com.miguelaetxio.mimoo.ui.library.LibraryScreen
import com.miguelaetxio.mimoo.ui.lyricssearch.LyricsSearchScreen
import com.miguelaetxio.mimoo.ui.mimooutcast.MimooutcastScreen
import com.miguelaetxio.mimoo.ui.playlist.PlaylistDetailScreen
import com.miguelaetxio.mimoo.ui.playlist.PlaylistsScreen
import com.miguelaetxio.mimoo.ui.queue.QueueScreen
import com.miguelaetxio.mimoo.ui.radiobrowser.RadioBrowserScreen
import com.miguelaetxio.mimoo.ui.settings.SettingsScreen
import com.miguelaetxio.mimoo.ui.song.SongScreen
import com.miguelaetxio.mimoo.ui.unifiedsearch.UnifiedSearchScreen

sealed class Screen(val route: String) {
    object Search : Screen("search")
    object Library : Screen("library")
    object Explorer : Screen("explorer")
    object Favorites : Screen("favorites")
    object Disliked : Screen("disliked")
    object LyricsSearch : Screen("lyrics_search")
    object Mimooutcast : Screen("mimooutcast")
    object Playlists : Screen("playlists")
    object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun routeFor(playlistId: Long) = "playlist/$playlistId"
    }
    object Artist : Screen("artist/{artistName}") {
        fun routeFor(artistName: String) = "artist/${Uri.encode(artistName)}"
    }
    object Album : Screen("album/{artistName}/{albumName}") {
        fun routeFor(artistName: String, albumName: String) =
            "album/${Uri.encode(artistName)}/${Uri.encode(albumName)}"
    }
    object Song : Screen("song/{artistName}/{songTitle}") {
        fun routeFor(artistName: String, songTitle: String) =
            "song/${Uri.encode(artistName)}/${Uri.encode(songTitle)}"
    }
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
            UnifiedSearchScreen(
                onOpenDrawer = onOpenDrawer,
                onOpenSong = { artistName, songTitle ->
                    navController.navigate(Screen.Song.routeFor(artistName, songTitle))
                },
                onOpenAlbum = { artistName, albumName ->
                    navController.navigate(Screen.Album.routeFor(artistName, albumName))
                },
                onOpenArtist = { artistName ->
                    navController.navigate(Screen.Artist.routeFor(artistName))
                },
                onOpenExternalLink = { url ->
                    navController.navigate(Screen.ImportLink.routeFor(url))
                },
            )
        }
        composable(Screen.Library.route) {
            LibraryScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Screen.Explorer.route) {
            ExplorerScreen(
                onOpenDrawer = onOpenDrawer,
                onOpenArtist = { artistName ->
                    navController.navigate(Screen.Artist.routeFor(artistName))
                },
                onOpenSong = { artistName, songTitle ->
                    navController.navigate(Screen.Song.routeFor(artistName, songTitle))
                },
                onOpenAlbum = { artistName, albumName ->
                    navController.navigate(Screen.Album.routeFor(artistName, albumName))
                },
                onOpenExternalLink = { url ->
                    navController.navigate(Screen.ImportLink.routeFor(url))
                },
            )
        }
        composable(Screen.Favorites.route) {
            FavoritesScreen(
                onOpenDrawer = onOpenDrawer,
                onOpenPlaylist = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.routeFor(playlistId))
                },
            )
        }
        composable(Screen.Disliked.route) {
            DislikedScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Screen.LyricsSearch.route) {
            LyricsSearchScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Screen.Mimooutcast.route) {
            MimooutcastScreen(onOpenDrawer = onOpenDrawer)
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
        composable(
            Screen.Artist.route,
            arguments = listOf(navArgument("artistName") { type = NavType.StringType }),
        ) {
            ArtistScreen(
                onBack = { navController.popBackStack() },
                onOpenAlbum = { albumName ->
                    val artistName = it.arguments?.getString("artistName") ?: return@ArtistScreen
                    navController.navigate(Screen.Album.routeFor(artistName, albumName))
                },
                onOpenSong = { songTitle ->
                    val artistName = it.arguments?.getString("artistName") ?: return@ArtistScreen
                    navController.navigate(Screen.Song.routeFor(artistName, songTitle))
                },
            )
        }
        composable(
            Screen.Album.route,
            arguments = listOf(
                navArgument("artistName") { type = NavType.StringType },
                navArgument("albumName") { type = NavType.StringType },
            ),
        ) {
            AlbumScreen(
                onBack = { navController.popBackStack() },
                onOpenSong = { songTitle ->
                    val artistName = it.arguments?.getString("artistName") ?: return@AlbumScreen
                    navController.navigate(Screen.Song.routeFor(artistName, songTitle))
                },
            )
        }
        composable(
            Screen.Song.route,
            arguments = listOf(
                navArgument("artistName") { type = NavType.StringType },
                navArgument("songTitle") { type = NavType.StringType },
            ),
        ) {
            SongScreen(
                onBack = { navController.popBackStack() },
                onOpenAlbum = { albumName ->
                    val artistName = it.arguments?.getString("artistName") ?: return@SongScreen
                    navController.navigate(Screen.Album.routeFor(artistName, albumName))
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

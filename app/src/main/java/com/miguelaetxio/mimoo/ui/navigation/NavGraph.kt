package com.miguelaetxio.mimoo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.miguelaetxio.mimoo.ui.artist.ArtistDetailScreen
import com.miguelaetxio.mimoo.ui.artist.ArtistFormScreen
import com.miguelaetxio.mimoo.ui.artist.ArtistListScreen
import com.miguelaetxio.mimoo.ui.album.AlbumListScreen
import com.miguelaetxio.mimoo.ui.album.AlbumFormScreen
import com.miguelaetxio.mimoo.ui.track.TrackListScreen
import com.miguelaetxio.mimoo.ui.track.TrackFormScreen
import com.miguelaetxio.mimoo.ui.playlist.PlaylistListScreen
import com.miguelaetxio.mimoo.ui.playlist.PlaylistDetailScreen
import com.miguelaetxio.mimoo.ui.playlist.PlaylistFormScreen
import com.miguelaetxio.mimoo.ui.playlist.PlaylistImportScreen

sealed class Screen(val route: String) {
    object ArtistList : Screen("artists")
    object ArtistDetail : Screen("artists/{artistId}") {
        fun createRoute(artistId: Long) = "artists/$artistId"
    }
    object ArtistForm : Screen("artists/form?artistId={artistId}") {
        fun createRoute(artistId: Long? = null) =
            if (artistId != null) "artists/form?artistId=$artistId"
            else "artists/form"
    }
    object AlbumList : Screen("artists/{artistId}/albums") {
        fun createRoute(artistId: Long) = "artists/$artistId/albums"
    }
    object AlbumForm : Screen(
        "artists/{artistId}/albums/form?albumId={albumId}"
    ) {
        fun createRoute(artistId: Long, albumId: Long? = null) =
            if (albumId != null)
                "artists/$artistId/albums/form?albumId=$albumId"
            else "artists/$artistId/albums/form"
    }
    object TrackList : Screen("artists/{artistId}/tracks") {
        fun createRoute(artistId: Long) = "artists/$artistId/tracks"
    }
    object TrackForm : Screen(
        "artists/{artistId}/tracks/form?trackId={trackId}"
    ) {
        fun createRoute(artistId: Long, trackId: Long? = null) =
            if (trackId != null)
                "artists/$artistId/tracks/form?trackId=$trackId"
            else "artists/$artistId/tracks/form"
    }
    object PlaylistList : Screen("playlists")
    object PlaylistDetail : Screen("playlists/{playlistId}") {
        fun createRoute(playlistId: Long) = "playlists/$playlistId"
    }
    object PlaylistForm : Screen(
        "playlists/form?playlistId={playlistId}"
    ) {
        fun createRoute(playlistId: Long? = null) =
            if (playlistId != null) "playlists/form?playlistId=$playlistId"
            else "playlists/form"
    }
    object PlaylistImport : Screen(
        "playlists/import?artistId={artistId}"
    ) {
        fun createRoute(artistId: Long) =
            "playlists/import?artistId=$artistId"
    }
}

@Composable
fun MiMooNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.ArtistList.route,
    ) {

        composable(Screen.ArtistList.route) {
            ArtistListScreen(
                onArtistClick = { id ->
                    navController.navigate(
                        Screen.ArtistDetail.createRoute(id)
                    )
                },
                onAddArtist = {
                    navController.navigate(Screen.ArtistForm.createRoute())
                },
            )
        }

        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(
                navArgument("artistId") { type = NavType.LongType }
            ),
        ) {
            ArtistDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id ->
                    navController.navigate(Screen.ArtistForm.createRoute(id))
                },
                onViewAlbums = { id ->
                    navController.navigate(Screen.AlbumList.createRoute(id))
                },
            )
        }

        composable(
            route = Screen.ArtistForm.route,
            arguments = listOf(
                navArgument("artistId") {
                    type = NavType.LongType; defaultValue = -1L
                }
            ),
        ) {
            ArtistFormScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.AlbumList.route,
            arguments = listOf(
                navArgument("artistId") { type = NavType.LongType }
            ),
        ) {
            AlbumListScreen(
                onBack = { navController.popBackStack() },
                onAddAlbum = { artistId ->
                    navController.navigate(
                        Screen.AlbumForm.createRoute(artistId)
                    )
                },
                onAlbumEdit = { artistId, albumId ->
                    navController.navigate(
                        Screen.AlbumForm.createRoute(artistId, albumId)
                    )
                },
            )
        }

        composable(
            route = Screen.AlbumForm.route,
            arguments = listOf(
                navArgument("artistId") { type = NavType.LongType },
                navArgument("albumId") {
                    type = NavType.LongType; defaultValue = -1L
                },
            ),
        ) {
            AlbumFormScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.TrackList.route,
            arguments = listOf(
                navArgument("artistId") { type = NavType.LongType }
            ),
        ) {
            TrackListScreen(
                onBack = { navController.popBackStack() },
                onAddTrack = { artistId ->
                    navController.navigate(
                        Screen.TrackForm.createRoute(artistId)
                    )
                },
                onTrackEdit = { artistId, trackId ->
                    navController.navigate(
                        Screen.TrackForm.createRoute(artistId, trackId)
                    )
                },
            )
        }

        composable(
            route = Screen.TrackForm.route,
            arguments = listOf(
                navArgument("artistId") { type = NavType.LongType },
                navArgument("trackId") {
                    type = NavType.LongType; defaultValue = -1L
                },
            ),
        ) {
            TrackFormScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.PlaylistList.route) {
            PlaylistListScreen(
                onPlaylistClick = { id ->
                    navController.navigate(
                        Screen.PlaylistDetail.createRoute(id)
                    )
                },
                onAddPlaylist = {
                    navController.navigate(Screen.PlaylistForm.createRoute())
                },
                onImportPlaylist = { artistId ->
                    navController.navigate(
                        Screen.PlaylistImport.createRoute(artistId)
                    )
                },
            )
        }

        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(
                navArgument("playlistId") { type = NavType.LongType }
            ),
        ) {
            PlaylistDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id ->
                    navController.navigate(
                        Screen.PlaylistForm.createRoute(id)
                    )
                },
            )
        }

        composable(
            route = Screen.PlaylistForm.route,
            arguments = listOf(
                navArgument("playlistId") {
                    type = NavType.LongType; defaultValue = -1L
                },
            ),
        ) {
            PlaylistFormScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.PlaylistImport.route,
            arguments = listOf(
                navArgument("artistId") {
                    type = NavType.LongType; defaultValue = -1L
                },
            ),
        ) {
            PlaylistImportScreen(onBack = { navController.popBackStack() })
        }
    }
}

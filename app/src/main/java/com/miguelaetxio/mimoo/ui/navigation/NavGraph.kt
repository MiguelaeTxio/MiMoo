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

sealed class Screen(val route: String) {
    object ArtistList   : Screen("artists")
    object ArtistDetail : Screen("artists/{artistId}") {
        fun createRoute(artistId: Long) = "artists/$artistId"
    }
    object ArtistForm   : Screen("artists/form?artistId={artistId}") {
        fun createRoute(artistId: Long? = null) =
            if (artistId != null) "artists/form?artistId=$artistId"
            else "artists/form"
    }
    object AlbumList    : Screen("artists/{artistId}/albums") {
        fun createRoute(artistId: Long) = "artists/$artistId/albums"
    }
    object AlbumForm    : Screen(
        "artists/{artistId}/albums/form?albumId={albumId}"
    ) {
        fun createRoute(artistId: Long, albumId: Long? = null) =
            if (albumId != null)
                "artists/$artistId/albums/form?albumId=$albumId"
            else "artists/$artistId/albums/form"
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
                    navController.navigate(
                        Screen.ArtistForm.createRoute(id)
                    )
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
    }
}

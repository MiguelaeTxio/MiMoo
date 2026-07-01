package com.miguelaetxio.mimoo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.miguelaetxio.mimoo.ui.search.SearchScreen

sealed class Screen(val route: String) {
    object Search : Screen("search")
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
    }
}

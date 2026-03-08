package com.mamton.zoomalbum.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mamton.zoomalbum.feature.projects_home.ui.AlbumListScreen

object Routes {
    const val PROJECTS_HOME = "projects_home"
    const val CANVAS = "canvas/{albumId}"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.PROJECTS_HOME) {
        composable(Routes.PROJECTS_HOME) {
            AlbumListScreen(
                onAlbumClick = { albumId ->
                    navController.navigate("canvas/$albumId")
                },
            )
        }
    }
}

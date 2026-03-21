package com.mamton.zoomalbum.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mamton.zoomalbum.app.navigation.AppNavigation
import com.mamton.zoomalbum.core.designsystem.ZoomAlbumTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ZoomAlbumTheme {
                AppNavigation()
            }
        }
    }
}

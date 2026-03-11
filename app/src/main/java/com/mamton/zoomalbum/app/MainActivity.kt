package com.mamton.zoomalbum.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.mamton.zoomalbum.core.designsystem.ZoomAlbumTheme
import com.mamton.zoomalbum.feature.canvas.view.CanvasScreen
import com.mamton.zoomalbum.feature.ide_ui.ui.IdeOverlayScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ZoomAlbumTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    CanvasScreen()
                    IdeOverlayScreen()
                }
            }
        }
    }
}

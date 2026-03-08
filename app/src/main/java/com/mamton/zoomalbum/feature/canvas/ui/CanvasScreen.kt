package com.mamton.zoomalbum.feature.canvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamton.zoomalbum.core.designsystem.CanvasDark
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasViewModel

@Composable
fun CanvasScreen(
    viewModel: CanvasViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(CanvasDark)
    }
}

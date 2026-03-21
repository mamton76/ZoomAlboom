package com.mamton.zoomalbum.feature.canvas.view

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamton.zoomalbum.core.designsystem.CanvasDark
import com.mamton.zoomalbum.feature.canvas.gestures.infiniteCanvasGestures
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasViewModel

@Composable
fun CanvasScreen(
    viewModel: CanvasViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cam = state.camera

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasDark)
            .onSizeChanged { size ->
                viewModel.onScreenSizeChanged(size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { viewModel.reset() })
            }
            .infiniteCanvasGestures { centroid, pan, zoom, rotation ->
                viewModel.onGesture(centroid, pan, zoom, rotation)
            },
    ) {
        // The single graphicsLayer on this inner Box handles ALL pan/zoom.
        // Individual node composables never recalculate their position during gestures;
        // the GPU performs the transform on the entire layer.
        Box(
            modifier = Modifier.graphicsLayer {
                translationX = cam.cx
                translationY = cam.cy
                scaleX = cam.scale
                scaleY = cam.scale
                rotationZ = cam.rotation
                transformOrigin = TransformOrigin(0f, 0f)
            },
        ) {
            for (node in state.visibleNodes) {
                CanvasNodeRenderer(node)
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

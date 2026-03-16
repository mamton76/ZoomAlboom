package com.mamton.zoomalbum.feature.canvas.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamton.zoomalbum.core.math.TransformUtils
import com.mamton.zoomalbum.core.math.ViewportCuller
import com.mamton.zoomalbum.domain.model.AlbumData
import com.mamton.zoomalbum.domain.model.AlbumMeta
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.Transform
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

// ── Camera ────────────────────────────────────────────────────────────
data class Camera(
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
) {
    companion object {
        const val MIN_SCALE = 0.00005f
        const val MAX_SCALE = 10000f
    }
}

// ── State ─────────────────────────────────────────────────────────────
data class CanvasState(
    val camera: Camera = Camera(),
    val visibleNodes: List<CanvasNode> = emptyList(),
    val totalNodeCount: Int = 0,
)

// ── ViewModel ─────────────────────────────────────────────────────────
@HiltViewModel
class CanvasViewModel @Inject constructor() : ViewModel() {

    private val albumData: AlbumData

    private val _state = MutableStateFlow(CanvasState())
    val state: StateFlow<CanvasState> = _state.asStateFlow()

    private var screenWidth = 1080f
    private var screenHeight = 1920f
    private var cullingJob: Job? = null

    init {
        albumData = generateRandomFrames(count = 500, spread = 5000f)
        _state.update { it.copy(totalNodeCount = albumData.nodes.size) }
        recalculateVisibleNodes()
    }

    /** Called once from Compose when the canvas size is known. */
    fun onScreenSizeChanged(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        screenWidth = width
        screenHeight = height
        recalculateVisibleNodes()
    }

    fun reset() {
        _state.update { s -> s.copy(camera = Camera()) }
        recalculateVisibleNodes()
    }

    /**
     * Main gesture callback.
     *
     * Centroid-based zoom + rotation:
     *   graphicsLayer applies translate → rotate → scale around (0,0).
     *   When the user pinches at screen-space centroid C, the world point
     *   under C must remain fixed after zoom & rotation change.
     *
     *   camera' = C - R(θ') · S' · inv(S) · inv(R(θ)) · (C - camera)
     *
     *   For pure zoom (no rotation delta) this simplifies to:
     *       camera' = C - (C - camera) · (scale' / scale)
     *
     *   Pan is in screen space and added directly.
     */
    fun onGesture(centroid: Offset, pan: Offset, zoom: Float, rotationDelta: Float) {
        _state.update { s ->
            val oldCam = s.camera
            val newScale = (oldCam.scale * zoom).coerceIn(Camera.MIN_SCALE, Camera.MAX_SCALE)
            val newRotation = oldCam.rotation + rotationDelta

            // Vector from centroid to current camera origin
            val dx = oldCam.x - centroid.x
            val dy = oldCam.y - centroid.y

            // Apply scale ratio
            val scaleRatio = newScale / oldCam.scale
            val sdx = dx * scaleRatio
            val sdy = dy * scaleRatio

            // Rotate by the rotation delta (in radians)
            val (rdx, rdy) = TransformUtils.rotateVector(sdx, sdy, rotationDelta)

            val newX = centroid.x + rdx + pan.x
            val newY = centroid.y + rdy + pan.y

            s.copy(
                camera = Camera(
                    x = newX,
                    y = newY,
                    scale = newScale,
                    rotation = newRotation,
                ),
            )
        }
        recalculateVisibleNodes()
    }

    // ── internals ─────────────────────────────────────────────────────
    private fun recalculateVisibleNodes() {
        cullingJob?.cancel()
        cullingJob = viewModelScope.launch(Dispatchers.Default) {
            val cam = _state.value.camera
            val viewport = TransformUtils.cameraViewport(
                cameraX = cam.x,
                cameraY = cam.y,
                cameraScale = cam.scale,
                cameraRotation = cam.rotation,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            )
            val visible = ViewportCuller.visibleNodes(albumData.nodes, viewport)
            _state.update { it.copy(visibleNodes = visible) }
        }
    }

    companion object {
        private val palette = arrayOf(
            "#E53935", // red
            "#1E88E5", // blue
            "#43A047", // green
            "#FB8C00", // orange
            "#8E24AA", // purple
            "#00897B", // teal
            "#F4511E", // deep-orange
            "#3949AB", // indigo
            "#C0CA33", // lime
            "#D81B60", // pink
        )

        private fun generateRandomFrames(count: Int, spread: Float): AlbumData {
            val rng = Random(seed = 42)
            val list = List(count) { i ->
                val w = rng.nextFloat() * 200f + 50f
                val h = rng.nextFloat() * 200f + 50f
                CanvasNode.Frame(
                    id = "frame_$i",
                    transform = Transform(
                        x = rng.nextFloat() * spread * 2 - spread,
                        y = rng.nextFloat() * spread * 2 - spread,
                        w = w,
                        h = h,
                        rotation = rng.nextFloat() * 360 - 180,
                        zIndex = i.toFloat(),
                    ),
                    color = palette[i % palette.size],
                )
            }
            return AlbumData(meta = AlbumMeta(name = "Random"), nodes = list)
        }
    }
}

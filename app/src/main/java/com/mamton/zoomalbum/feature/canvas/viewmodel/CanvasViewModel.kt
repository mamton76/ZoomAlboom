package com.mamton.zoomalbum.feature.canvas.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamton.zoomalbum.core.math.TransformUtils
import com.mamton.zoomalbum.core.math.ViewportCuller
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val isLoading: Boolean = true,
)

// ── ViewModel ─────────────────────────────────────────────────────────
@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val albumId: Long = savedStateHandle.get<Long>("albumId") ?: 0L

    private var allNodes: List<CanvasNode> = emptyList()

    private val _state = MutableStateFlow(CanvasState())
    val state: StateFlow<CanvasState> = _state.asStateFlow()

    private var screenWidth = 1080f
    private var screenHeight = 1920f
    private var cullingJob: Job? = null

    init {
        loadAlbum()
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
     */
    fun onGesture(centroid: Offset, pan: Offset, zoom: Float, rotationDelta: Float) {
        _state.update { s ->
            val oldCam = s.camera
            val newScale = (oldCam.scale * zoom).coerceIn(Camera.MIN_SCALE, Camera.MAX_SCALE)
            val newRotation = oldCam.rotation + rotationDelta

            val dx = oldCam.x - centroid.x
            val dy = oldCam.y - centroid.y

            val scaleRatio = newScale / oldCam.scale
            val sdx = dx * scaleRatio
            val sdy = dy * scaleRatio

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

    fun addNode(node: CanvasNode) {
        allNodes = allNodes + node
        _state.update { it.copy(totalNodeCount = allNodes.size) }
        recalculateVisibleNodes()
    }

    fun removeNode(nodeId: String) {
        allNodes = allNodes.filter { it.id != nodeId }
        _state.update { it.copy(totalNodeCount = allNodes.size) }
        recalculateVisibleNodes()
    }

    /** Current viewport in world coordinates. */
    fun currentViewport(): com.mamton.zoomalbum.core.math.BoundingBox {
        val cam = _state.value.camera
        return TransformUtils.cameraViewport(
            cameraX = cam.x, cameraY = cam.y,
            cameraScale = cam.scale, cameraRotation = cam.rotation,
            screenWidth = screenWidth, screenHeight = screenHeight,
        )
    }

    fun currentCamera(): Camera = _state.value.camera

    fun nextZIndex(): Float = (allNodes.maxOfOrNull { it.transform.zIndex } ?: 0f) + 1f

    override fun onCleared() {
        super.onCleared()
        if (albumId != 0L && allNodes.isNotEmpty()) {
            // Fire-and-forget save — ViewModel scope is cancelled but we use
            // a non-cancellable context for the final save.
            kotlinx.coroutines.MainScope().launch(Dispatchers.IO) {
                try {
                    mediaRepository.saveSceneGraph(albumId, allNodes)
                } catch (_: Exception) {
                    // Best-effort save on exit
                }
            }
        }
    }

    // ── internals ─────────────────────────────────────────────────────

    private fun loadAlbum() {
        viewModelScope.launch {
            val nodes = if (albumId != 0L) {
                mediaRepository.loadSceneGraph(albumId)
            } else {
                emptyList()
            }
            allNodes = nodes
            _state.update {
                it.copy(
                    totalNodeCount = nodes.size,
                    isLoading = false,
                )
            }
            recalculateVisibleNodes()
        }
    }

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
            val visible = ViewportCuller.visibleNodes(allNodes, viewport)
            _state.update { it.copy(visibleNodes = visible) }
        }
    }

}

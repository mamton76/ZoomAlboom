package com.mamton.zoomalbum.feature.canvas.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamton.zoomalbum.core.math.BoundingBox
import com.mamton.zoomalbum.core.math.Camera
import com.mamton.zoomalbum.core.math.LodResolver
import com.mamton.zoomalbum.core.math.ResizeHandle
import com.mamton.zoomalbum.core.math.TransformUtils
import com.mamton.zoomalbum.core.math.ViewportCuller
import com.mamton.zoomalbum.core.mvi.Intent
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.domain.model.withTransform
import com.mamton.zoomalbum.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── State ─────────────────────────────────────────────────────────────

/** A node paired with its resolved render detail for the current camera. */
data class VisibleNode(
    val node: CanvasNode,
    val detail: RenderDetail,
)

data class CanvasState(
    val camera: Camera = Camera(),
    val visibleNodes: List<VisibleNode> = emptyList(),
    val totalNodeCount: Int = 0,
    val isLoading: Boolean = true,
    val selectedNodeIds: Set<String> = emptySet(),
    val selectionRect: BoundingBox? = null,
    /** Group selection bounding rect — computed on selection change, rotation accumulated. */
    val groupSelectionTransform: com.mamton.zoomalbum.domain.model.Transform? = null,
)

// ── Actions ───────────────────────────────────────────────────────────

sealed interface CanvasAction : Intent {
    // Selection
    data class SelectNode(val nodeId: String) : CanvasAction
    data class ToggleNodeSelection(val nodeId: String) : CanvasAction
    data class SelectNodesInRect(val worldRect: BoundingBox) : CanvasAction
    data object DeselectAll : CanvasAction

    // Group operations — apply to ALL selected nodes
    data class MoveSelection(val worldDx: Float, val worldDy: Float) : CanvasAction
    data class ResizeSelection(val scaleFactor: Float) : CanvasAction
    data class RotateSelection(val angleDelta: Float) : CanvasAction

    // Lifecycle
    data object DeleteSelection : CanvasAction
    data object DuplicateSelection : CanvasAction
    data object FinishInteraction : CanvasAction

    // Rectangle selection preview
    data class UpdateSelectionRect(val worldRect: BoundingBox?) : CanvasAction
}

// ── ViewModel ─────────────────────────────────────────────────────────
@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val albumId: Long = savedStateHandle.get<Long>("albumId") ?: 0L

    private val _allNodes = MutableStateFlow<List<CanvasNode>>(emptyList())

    val frames: StateFlow<List<CanvasNode.Frame>> = _allNodes
        .map { nodes -> nodes.filterIsInstance<CanvasNode.Frame>() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

            val dx = oldCam.cx - centroid.x
            val dy = oldCam.cy - centroid.y

            val scaleRatio = newScale / oldCam.scale
            val sdx = dx * scaleRatio
            val sdy = dy * scaleRatio

            val (rdx, rdy) = TransformUtils.rotateVector(sdx, sdy, rotationDelta)

            val newCx = centroid.x + rdx + pan.x
            val newCy = centroid.y + rdy + pan.y

            s.copy(
                camera = Camera(
                    cx = newCx,
                    cy = newCy,
                    scale = newScale,
                    rotation = newRotation,
                ),
            )
        }
        recalculateVisibleNodes()
    }

    fun addNode(node: CanvasNode) {
        _allNodes.update { it + node }
        _state.update { it.copy(totalNodeCount = _allNodes.value.size) }
        recalculateVisibleNodes()
    }

    fun removeNode(nodeId: String) {
        _allNodes.update { nodes -> nodes.filter { it.id != nodeId } }
        _state.update { it.copy(totalNodeCount = _allNodes.value.size) }
        recalculateVisibleNodes()
    }

    // ── Action dispatch (MVI) ─────────────────────────────────────────

    fun onAction(action: CanvasAction) {
        when (action) {
            is CanvasAction.SelectNode -> {
                val ids = setOf(action.nodeId)
                _state.update { it.copy(selectedNodeIds = ids) }
                recomputeGroupTransform(ids)
            }

            is CanvasAction.ToggleNodeSelection -> {
                _state.update { s ->
                    val updated = if (action.nodeId in s.selectedNodeIds)
                        s.selectedNodeIds - action.nodeId
                    else
                        s.selectedNodeIds + action.nodeId
                    s.copy(selectedNodeIds = updated)
                }
                recomputeGroupTransform(_state.value.selectedNodeIds)
            }

            is CanvasAction.SelectNodesInRect -> {
                val ids = _allNodes.value
                    .filter { TransformUtils.toBoundingBox(it.transform).intersects(action.worldRect) }
                    .map { it.id }
                    .toSet()
                _state.update { it.copy(selectedNodeIds = ids, selectionRect = null) }
                recomputeGroupTransform(ids)
            }

            is CanvasAction.DeselectAll -> {
                _state.update { it.copy(selectedNodeIds = emptySet(), groupSelectionTransform = null) }
            }

            is CanvasAction.MoveSelection -> {
                val ids = _state.value.selectedNodeIds
                _allNodes.update { nodes ->
                    nodes.map { node ->
                        if (node.id in ids) {
                            node.withTransform(
                                node.transform.copy(
                                    cx = node.transform.cx + action.worldDx,
                                    cy = node.transform.cy + action.worldDy,
                                ),
                            )
                        } else node
                    }
                }
                inlinePatchVisibleNodes(ids) { t ->
                    t.copy(cx = t.cx + action.worldDx, cy = t.cy + action.worldDy)
                }
                // Shift group rect center
                _state.value.groupSelectionTransform?.let { gt ->
                    _state.update {
                        it.copy(
                            groupSelectionTransform = gt.copy(
                                cx = gt.cx + action.worldDx,
                                cy = gt.cy + action.worldDy,
                            ),
                        )
                    }
                }
            }

            is CanvasAction.ResizeSelection -> {
                val ids = _state.value.selectedNodeIds
                val selected = _allNodes.value.filter { it.id in ids }
                if (selected.isEmpty()) return
                val (gcx, gcy) = TransformUtils.groupCenter(selected)
                val factor = action.scaleFactor.coerceIn(0.01f, 100f)

                _allNodes.update { nodes ->
                    nodes.map { node ->
                        if (node.id in ids) {
                            val t = node.transform
                            val newCx = gcx + (t.cx - gcx) * factor
                            val newCy = gcy + (t.cy - gcy) * factor
                            val newScale = (t.scale * factor).coerceIn(0.01f, 1000f)
                            node.withTransform(t.copy(cx = newCx, cy = newCy, scale = newScale))
                        } else node
                    }
                }
                inlinePatchVisibleNodes(ids) { t ->
                    val newCx = gcx + (t.cx - gcx) * factor
                    val newCy = gcy + (t.cy - gcy) * factor
                    val newScale = (t.scale * factor).coerceIn(0.01f, 1000f)
                    t.copy(cx = newCx, cy = newCy, scale = newScale)
                }
            }

            is CanvasAction.RotateSelection -> {
                val ids = _state.value.selectedNodeIds
                val selected = _allNodes.value.filter { it.id in ids }
                if (selected.isEmpty()) return

                // Use group transform center if available (stable during rotation),
                // otherwise compute from current node positions.
                val gt = _state.value.groupSelectionTransform
                val (gcx, gcy) = if (gt != null) {
                    gt.cx to gt.cy
                } else {
                    TransformUtils.groupCenter(selected)
                }

                _allNodes.update { nodes ->
                    nodes.map { node ->
                        if (node.id in ids) {
                            val t = node.transform
                            val (newCx, newCy) = TransformUtils.rotatePointAround(
                                t.cx, t.cy, gcx, gcy, action.angleDelta,
                            )
                            node.withTransform(
                                t.copy(
                                    cx = newCx,
                                    cy = newCy,
                                    rotation = t.rotation + action.angleDelta,
                                ),
                            )
                        } else node
                    }
                }
                inlinePatchVisibleNodes(ids) { t ->
                    val (newCx, newCy) = TransformUtils.rotatePointAround(
                        t.cx, t.cy, gcx, gcy, action.angleDelta,
                    )
                    t.copy(cx = newCx, cy = newCy, rotation = t.rotation + action.angleDelta)
                }
                // Accumulate rotation on group rect (size stays fixed)
                if (gt != null) {
                    _state.update {
                        it.copy(
                            groupSelectionTransform = gt.copy(
                                rotation = gt.rotation + action.angleDelta,
                            ),
                        )
                    }
                }
            }

            is CanvasAction.DeleteSelection -> {
                val ids = _state.value.selectedNodeIds
                _allNodes.update { nodes -> nodes.filter { it.id !in ids } }
                _state.update {
                    it.copy(
                        selectedNodeIds = emptySet(),
                        totalNodeCount = _allNodes.value.size,
                    )
                }
                recalculateVisibleNodes()
            }

            is CanvasAction.DuplicateSelection -> {
                val ids = _state.value.selectedNodeIds
                val sources = _allNodes.value.filter { it.id in ids }
                if (sources.isEmpty()) return
                var z = nextZIndex()
                val copies = sources.map { node ->
                    val t = node.transform
                    val newId = "${node.id.substringBefore("_copy")}_copy_${System.currentTimeMillis()}"
                    val newTransform = t.copy(cx = t.cx + 50f, cy = t.cy + 50f, zIndex = z)
                    z += 1f
                    node.withTransform(newTransform).let { copy ->
                        when (copy) {
                            is CanvasNode.Frame -> copy.copy(id = newId)
                            is CanvasNode.Media -> copy.copy(id = newId)
                        }
                    }
                }
                _allNodes.update { it + copies }
                _state.update {
                    it.copy(
                        selectedNodeIds = copies.map { c -> c.id }.toSet(),
                        totalNodeCount = _allNodes.value.size,
                    )
                }
                recalculateVisibleNodes()
            }

            is CanvasAction.FinishInteraction -> {
                recalculateVisibleNodes()
                recomputeGroupTransform(_state.value.selectedNodeIds)
            }

            is CanvasAction.UpdateSelectionRect -> {
                _state.update { it.copy(selectionRect = action.worldRect) }
            }
        }
    }

    // ── Hit-testing ───────────────────────────────────────────────────

    /** Returns the topmost visible node at the given screen point, or null. */
    fun hitTest(screenX: Float, screenY: Float): CanvasNode? {
        val cam = _state.value.camera
        val (wx, wy) = TransformUtils.screenToWorld(screenX, screenY, cam)
        return _state.value.visibleNodes
            .sortedByDescending { it.node.transform.zIndex }
            .firstOrNull { TransformUtils.pointInNode(wx, wy, it.node.transform) }
            ?.node
    }

    /** Returns ALL visible nodes at the given screen point (for overlap picker). */
    fun hitTestAll(screenX: Float, screenY: Float): List<CanvasNode> {
        val cam = _state.value.camera
        val (wx, wy) = TransformUtils.screenToWorld(screenX, screenY, cam)
        return _state.value.visibleNodes
            .filter { TransformUtils.pointInNode(wx, wy, it.node.transform) }
            .sortedByDescending { it.node.transform.zIndex }
            .map { it.node }
    }

    /** True if the screen point is on ANY currently selected node. */
    fun isOnSelectedNode(screenX: Float, screenY: Float): Boolean {
        val ids = _state.value.selectedNodeIds
        if (ids.isEmpty()) return false
        val cam = _state.value.camera
        val (wx, wy) = TransformUtils.screenToWorld(screenX, screenY, cam)
        return _state.value.visibleNodes.any { vn ->
            vn.node.id in ids && TransformUtils.pointInNode(wx, wy, vn.node.transform)
        }
    }

    /** Returns the resize handle at the given screen point, or null. */
    fun hitTestHandle(screenX: Float, screenY: Float): ResizeHandle? {
        val ids = _state.value.selectedNodeIds
        if (ids.isEmpty()) return null
        val cam = _state.value.camera
        val (wx, wy) = TransformUtils.screenToWorld(screenX, screenY, cam)
        val handleWorldRadius = HANDLE_TOUCH_RADIUS_PX / cam.scale

        val selected = _state.value.visibleNodes.filter { it.node.id in ids }.map { it.node }
        if (selected.isEmpty()) return null

        val t = if (selected.size == 1) {
            selected.first().transform
        } else {
            _state.value.groupSelectionTransform ?: return null
        }
        return TransformUtils.hitTestHandle(wx, wy, t, handleWorldRadius)
    }

    /** True if the screen point is on the rotation handle. */
    fun hitTestRotationHandle(screenX: Float, screenY: Float): Boolean {
        val ids = _state.value.selectedNodeIds
        if (ids.isEmpty()) return false
        val cam = _state.value.camera
        val (wx, wy) = TransformUtils.screenToWorld(screenX, screenY, cam)
        val handleWorldRadius = HANDLE_TOUCH_RADIUS_PX / cam.scale
        val handleOffset = ROTATION_HANDLE_OFFSET_PX / cam.scale

        val selected = _state.value.visibleNodes.filter { it.node.id in ids }.map { it.node }
        if (selected.isEmpty()) return false

        val t = if (selected.size == 1) {
            selected.first().transform
        } else {
            _state.value.groupSelectionTransform ?: return false
        }
        return TransformUtils.hitTestRotationHandle(wx, wy, t, handleWorldRadius, handleOffset)
    }

    /** Current viewport in world coordinates. */
    fun currentViewport(): BoundingBox {
        val cam = _state.value.camera
        return TransformUtils.cameraViewport(
            cameraCx = cam.cx, cameraCy = cam.cy,
            cameraScale = cam.scale, cameraRotation = cam.rotation,
            screenWidth = screenWidth, screenHeight = screenHeight,
        )
    }

    fun currentCamera(): Camera = _state.value.camera

    fun screenSize(): Pair<Float, Float> = screenWidth to screenHeight

    fun nextZIndex(): Float = (_allNodes.value.maxOfOrNull { it.transform.zIndex } ?: 0f) + 1f

    override fun onCleared() {
        super.onCleared()
        val nodes = _allNodes.value
        if (albumId != 0L && nodes.isNotEmpty()) {
            // Fire-and-forget save — ViewModel scope is cancelled but we use
            // a non-cancellable context for the final save.
            kotlinx.coroutines.MainScope().launch(Dispatchers.IO) {
                try {
                    mediaRepository.saveSceneGraph(albumId, nodes)
                } catch (_: Exception) {
                    // Best-effort save on exit
                }
            }
        }
    }

    // ── internals ─────────────────────────────────────────────────────

    /**
     * Inline-patches [_state].visibleNodes without triggering full recalculation.
     * Used during drag/resize/rotate for performance.
     */
    private fun inlinePatchVisibleNodes(
        ids: Set<String>,
        patchTransform: (com.mamton.zoomalbum.domain.model.Transform) -> com.mamton.zoomalbum.domain.model.Transform,
    ) {
        _state.update { s ->
            s.copy(
                visibleNodes = s.visibleNodes.map { vn ->
                    if (vn.node.id in ids) {
                        val updated = vn.node.withTransform(patchTransform(vn.node.transform))
                        vn.copy(node = updated)
                    } else vn
                },
            )
        }
    }

    /**
     * Recomputes [groupSelectionTransform] from the current selected nodes.
     * Called when selection changes (add/remove/rect-select). The rotation
     * is reset to 0 — it accumulates during handle drag.
     */
    private fun recomputeGroupTransform(selectedIds: Set<String>) {
        if (selectedIds.size < 2) {
            _state.update { it.copy(groupSelectionTransform = null) }
            return
        }
        val selected = _allNodes.value.filter { it.id in selectedIds }
        if (selected.size < 2) {
            _state.update { it.copy(groupSelectionTransform = null) }
            return
        }
        val bbox = TransformUtils.selectionBoundingBox(selected)
        _state.update {
            it.copy(
                groupSelectionTransform = com.mamton.zoomalbum.domain.model.Transform(
                    cx = bbox.centerX,
                    cy = bbox.centerY,
                    w = bbox.width,
                    h = bbox.height,
                    rotation = 0f,
                ),
            )
        }
    }

    private fun loadAlbum() {
        viewModelScope.launch {
            val nodes = if (albumId != 0L) {
                mediaRepository.loadSceneGraph(albumId)
            } else {
                emptyList()
            }
            _allNodes.value = nodes
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
                cameraCx = cam.cx,
                cameraCy = cam.cy,
                cameraScale = cam.scale,
                cameraRotation = cam.rotation,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            )
            val geometryVisible = ViewportCuller.visibleNodes(_allNodes.value, viewport)
            val resolved = geometryVisible.mapNotNull { node ->
                val detail = LodResolver.resolveRenderDetail(node, cam)
                if (detail == RenderDetail.Hidden) null else VisibleNode(node, detail)
            }
            _state.update { it.copy(visibleNodes = resolved) }
        }
    }

    companion object {
        /** Handle visual size in screen pixels. */
        const val HANDLE_SCREEN_PX = 24f
        /** Touch target radius in screen pixels (larger than visual for easy tapping). */
        const val HANDLE_TOUCH_RADIUS_PX = 48f
        /** Rotation handle offset above node top in screen pixels. */
        const val ROTATION_HANDLE_OFFSET_PX = 40f
    }
}

package com.mamton.zoomalbum.feature.canvas.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
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
import com.mamton.zoomalbum.domain.model.CanvasNodeFactory
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.domain.model.withTransform
import com.mamton.zoomalbum.domain.repository.HistoryRepository
import com.mamton.zoomalbum.domain.repository.MediaRepository
import com.mamton.zoomalbum.domain.undo.CanvasCommand
import com.mamton.zoomalbum.domain.undo.CommandHistory
import com.mamton.zoomalbum.domain.undo.CommandKind
import com.mamton.zoomalbum.domain.undo.InteractionKind
import com.mamton.zoomalbum.domain.undo.toCommandKind
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.withContext
import java.io.File
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
    /**
     * Rectangle selection result.
     *
     * @param additive if true, union with the current selection (keeps previously
     *        selected nodes). If false (default), replaces the selection.
     */
    data class SelectNodesInRect(
        val worldRect: BoundingBox,
        val additive: Boolean = false,
    ) : CanvasAction
    data object DeselectAll : CanvasAction

    // Group operations — apply to ALL selected nodes
    data class MoveSelection(val worldDx: Float, val worldDy: Float) : CanvasAction
    data class ResizeSelection(val scaleFactor: Float) : CanvasAction
    data class RotateSelection(val angleDelta: Float) : CanvasAction

    // Lifecycle
    data object DeleteSelection : CanvasAction
    data object DuplicateSelection : CanvasAction
    data class BeginInteraction(val kind: InteractionKind) : CanvasAction
    data object FinishInteraction : CanvasAction

    // Undo/Redo
    data object Undo : CanvasAction
    data object Redo : CanvasAction

    // Rectangle selection preview
    data class UpdateSelectionRect(val worldRect: BoundingBox?) : CanvasAction
}

// ── ViewModel ─────────────────────────────────────────────────────────
@HiltViewModel
class CanvasViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val historyRepository: HistoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val albumId: Long = savedStateHandle.get<Long>("albumId") ?: 0L

    private val _allNodes = MutableStateFlow<List<CanvasNode>>(emptyList())

    // Undo/Redo
    private val history = CommandHistory()
    private var pendingSnapshot: List<CanvasNode>? = null
    private var pendingKind: InteractionKind? = null
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

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
        _state.update {
            it.copy(
                totalNodeCount = _allNodes.value.size,
                selectedNodeIds = setOf(node.id),
                groupSelectionTransform = null,
            )
        }
        commit(
            CanvasCommand(
                before = null,
                after = listOf(node),
                kind = CommandKind.ADD,
                timestampMs = System.currentTimeMillis(),
            ),
        )
        recalculateVisibleNodes()
    }

    /**
     * Copies [sourceUri] to app-private storage and adds a [CanvasNode.Media] node.
     * Must be called from any thread — does IO work internally.
     */
    fun addMedia(sourceUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val (imgW, imgH) = decodeImageDimensions(sourceUri)
            val localPath = copyToAppStorage(sourceUri) ?: return@launch
            val viewport = currentViewport()
            val camera = currentCamera()
            val zIndex = nextZIndex()
            val (sw, sh) = screenSize()
            val node = CanvasNodeFactory.createMedia(
                uri = localPath,
                imageWidth = imgW,
                imageHeight = imgH,
                screenWidth = sw,
                screenHeight = sh,
                viewport = viewport,
                nextZIndex = zIndex,
                camera = camera,
            )
            withContext(Dispatchers.Main) { addNode(node) }
        }
    }

    fun removeNode(nodeId: String) {
        val current = _allNodes.value
        val idx = current.indexOfFirst { it.id == nodeId }
        if (idx < 0) return
        val removed = current[idx]
        _allNodes.update { nodes -> nodes.filter { it.id != nodeId } }
        _state.update { it.copy(totalNodeCount = _allNodes.value.size) }
        commit(
            CanvasCommand(
                before = listOf(removed),
                after = null,
                beforeIndices = listOf(idx),
                kind = CommandKind.REMOVE,
                timestampMs = System.currentTimeMillis(),
            ),
        )
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
                val rectHits = _allNodes.value
                    .filter { TransformUtils.toBoundingBox(it.transform).intersects(action.worldRect) }
                    .map { it.id }
                    .toSet()
                val ids = if (action.additive) {
                    _state.value.selectedNodeIds + rectHits
                } else {
                    rectHits
                }
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

                // Pivot = group rect center (stable rigid-body pivot).
                // Fall back to node centroid only when no rect exists (single node).
                val gt = _state.value.groupSelectionTransform
                val (gcx, gcy) = if (gt != null) {
                    gt.cx to gt.cy
                } else {
                    TransformUtils.groupCenter(selected)
                }
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
                // Rigid-body scale: rect grows/shrinks with the nodes
                if (gt != null) {
                    _state.update {
                        it.copy(
                            groupSelectionTransform = gt.copy(
                                w = gt.w * factor,
                                h = gt.h * factor,
                            ),
                        )
                    }
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
                if (ids.isEmpty()) return
                val current = _allNodes.value
                val removedWithIdx = current.withIndex()
                    .filter { it.value.id in ids }
                    .map { it.index to it.value }
                if (removedWithIdx.isEmpty()) return
                _allNodes.update { nodes -> nodes.filter { it.id !in ids } }
                _state.update {
                    it.copy(
                        selectedNodeIds = emptySet(),
                        totalNodeCount = _allNodes.value.size,
                    )
                }
                commit(
                    CanvasCommand(
                        before = removedWithIdx.map { it.second },
                        after = null,
                        beforeIndices = removedWithIdx.map { it.first },
                        kind = CommandKind.DELETE,
                        timestampMs = System.currentTimeMillis(),
                    ),
                )
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
                commit(
                    CanvasCommand(
                        before = null,
                        after = copies,
                        kind = CommandKind.DUPLICATE,
                        timestampMs = System.currentTimeMillis(),
                    ),
                )
                recalculateVisibleNodes()
            }

            is CanvasAction.BeginInteraction -> {
                // Idempotent: both gesture detectors fire one Begin per gesture, but
                // be defensive — if a snapshot is already pending, keep it.
                if (pendingSnapshot != null) return
                val ids = _state.value.selectedNodeIds
                if (ids.isEmpty()) return
                pendingSnapshot = _allNodes.value.filter { it.id in ids }
                pendingKind = action.kind
            }

            is CanvasAction.FinishInteraction -> {
                commitPendingInteraction()
                // Do NOT recompute group rect here — it would snap back to
                // screen-aligned. The rect is kept in sync during Move/Resize/Rotate
                // and only recomputed on selection membership changes.
                recalculateVisibleNodes()
            }

            is CanvasAction.Undo -> {
                // Defensive: if a gesture is in flight, commit it first so undo
                // operates on a consistent state. Gesture detectors should consume
                // pointer events before reaching this branch.
                commitPendingInteraction()
                history.undo()?.let { applyCommand(it, reverse = true) }
                refreshHistoryFlags()
            }

            is CanvasAction.Redo -> {
                commitPendingInteraction()
                history.redo()?.let { applyCommand(it, reverse = false) }
                refreshHistoryFlags()
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

    /**
     * Effective transform for the current selection:
     * - Multi-select: group rect ([CanvasState.groupSelectionTransform]).
     * - Single-select: that node's transform (looked up in [_allNodes], not
     *   visibleNodes — selection must behave consistently even when nodes are
     *   culled out of the viewport).
     * - Empty selection: null.
     */
    fun selectionTransform(): com.mamton.zoomalbum.domain.model.Transform? {
        val ids = _state.value.selectedNodeIds
        return when {
            ids.isEmpty() -> null
            ids.size == 1 -> _allNodes.value.firstOrNull { it.id == ids.first() }?.transform
            else -> _state.value.groupSelectionTransform
        }
    }

    /** True if the screen point is on ANY currently selected node. */
    fun isOnSelectedNode(screenX: Float, screenY: Float): Boolean {
        val ids = _state.value.selectedNodeIds
        if (ids.isEmpty()) return false
        val cam = _state.value.camera
        val (wx, wy) = TransformUtils.screenToWorld(screenX, screenY, cam)
        return _allNodes.value.any { node ->
            node.id in ids && TransformUtils.pointInNode(wx, wy, node.transform)
        }
    }

    /** Returns the resize handle at the given screen point, or null. */
    fun hitTestHandle(screenX: Float, screenY: Float): ResizeHandle? {
        val t = selectionTransform() ?: return null
        val cam = _state.value.camera
        val (wx, wy) = TransformUtils.screenToWorld(screenX, screenY, cam)
        val handleWorldRadius = HANDLE_TOUCH_RADIUS_PX / cam.scale
        return TransformUtils.hitTestHandle(wx, wy, t, handleWorldRadius)
    }

    /** True if the screen point is on the rotation handle. */
    fun hitTestRotationHandle(screenX: Float, screenY: Float): Boolean {
        val t = selectionTransform() ?: return false
        val cam = _state.value.camera
        val (wx, wy) = TransformUtils.screenToWorld(screenX, screenY, cam)
        val handleWorldRadius = HANDLE_TOUCH_RADIUS_PX / cam.scale
        val handleOffset = ROTATION_HANDLE_OFFSET_PX / cam.scale
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
        val historySnapshot = history.snapshot()
        if (albumId != 0L) {
            // Fire-and-forget save — ViewModel scope is cancelled but we use
            // a non-cancellable context for the final save.
            kotlinx.coroutines.MainScope().launch(Dispatchers.IO) {
                try {
                    if (nodes.isNotEmpty()) {
                        mediaRepository.saveSceneGraph(albumId, nodes)
                    }
                    historyRepository.save(albumId, historySnapshot)
                } catch (_: Exception) {
                    // Best-effort save on exit
                }
            }
        }
    }

    // ── Media import helpers ──────────────────────────────────────────

    private fun decodeImageDimensions(uri: Uri): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (_: Exception) {}
        var w = opts.outWidth
        var h = opts.outHeight
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = androidx.exifinterface.media.ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
                )
                if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 ||
                    orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 ||
                    orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE ||
                    orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE
                ) {
                    val tmp = w; w = h; h = tmp
                }
            }
        } catch (_: Exception) {}
        return w to h
    }

    private fun copyToAppStorage(uri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "media/$albumId").also { it.mkdirs() }
            val dest = File(dir, "img_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        } catch (_: Exception) {
            null
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
     * Recomputes [CanvasState.groupSelectionTransform] from the current selected nodes.
     * Called when selection changes (add/remove/rect-select).
     *
     * The rect is screen-aligned at formation time (rotation = -camera.rotation),
     * pinned to world afterward. During handle-drag (rotate/resize) the rect
     * is mutated in place — NOT recomputed here.
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
        val cameraRotation = _state.value.camera.rotation
        val gt = TransformUtils.screenAlignedGroupTransform(selected, cameraRotation)
        _state.update { it.copy(groupSelectionTransform = gt) }
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
            if (albumId != 0L) {
                runCatching { historyRepository.load(albumId) }
                    .getOrNull()
                    ?.let { history.restore(it) }
            }
            refreshHistoryFlags()
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

    // ── Undo/Redo internals ───────────────────────────────────────────

    private fun commit(command: CanvasCommand) {
        history.push(command)
        refreshHistoryFlags()
    }

    private fun refreshHistoryFlags() {
        _canUndo.value = history.canUndo
        _canRedo.value = history.canRedo
    }

    /**
     * Commits the in-flight gesture snapshot, if any. Called on FinishInteraction
     * and defensively on Undo/Redo. Skips the push if the gesture was a no-op
     * (before == after — e.g. user pressed a handle and lifted without moving).
     */
    private fun commitPendingInteraction() {
        val before = pendingSnapshot ?: return
        val kind = pendingKind ?: InteractionKind.MOVE  // shouldn't happen; safe default
        // Order `after` to match `before`'s id ordering — positional pairing.
        val byId: Map<String, CanvasNode> = _allNodes.value.associateBy { it.id }
        val after = before.mapNotNull { byId[it.id] }
        pendingSnapshot = null
        pendingKind = null
        if (after.size != before.size) return  // some ids vanished — drop the snapshot
        val noop = before.zip(after).all { (b, a) -> b == a }
        if (noop) return
        commit(
            CanvasCommand(
                before = before,
                after = after,
                kind = kind.toCommandKind(),
                timestampMs = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Applies [cmd] to `_allNodes` and refreshes derived state. When [reverse]
     * is true, applies the inverse direction (used for Undo). List order is
     * preserved across all three command shapes; for restored deletes,
     * [CanvasCommand.beforeIndices] is used to re-insert at the original positions.
     */
    private fun applyCommand(cmd: CanvasCommand, reverse: Boolean) {
        val from = if (reverse) cmd.after else cmd.before
        val to = if (reverse) cmd.before else cmd.after

        _allNodes.update { current ->
            when {
                // Mutation (forward or reverse): replace by id in-place. Order preserved.
                from != null && to != null -> {
                    val byId = to.associateBy { it.id }
                    current.map { byId[it.id] ?: it }
                }
                // Pure delete (forward of DELETE/REMOVE, OR reverse of ADD/DUPLICATE).
                from != null && to == null -> {
                    val ids = from.map { it.id }.toSet()
                    current.filter { it.id !in ids }
                }
                // Pure insert (forward of ADD/DUPLICATE, OR reverse of DELETE/REMOVE).
                from == null && to != null -> {
                    val restoreWithIndices =
                        reverse && cmd.before != null && cmd.beforeIndices != null
                    if (restoreWithIndices) {
                        // Re-insert deleted nodes at their original positions.
                        val mutable = current.toMutableList()
                        cmd.before!!.zip(cmd.beforeIndices!!)
                            .sortedBy { (_, idx) -> idx }
                            .forEach { (node, idx) ->
                                mutable.add(idx.coerceAtMost(mutable.size), node)
                            }
                        mutable.toList()
                    } else {
                        // ADD/DUPLICATE forward — append (matches original behavior).
                        current + to
                    }
                }
                else -> current
            }
        }

        // Selection policy:
        //   pure insert    → select inserted nodes
        //   pure delete    → clear selection
        //   mutation       → select mutated nodes
        val newSel: Set<String> = when {
            to != null && from == null -> to.map { it.id }.toSet()
            to == null && from != null -> emptySet()
            else -> to!!.map { it.id }.toSet()
        }
        _state.update {
            it.copy(
                selectedNodeIds = newSel,
                totalNodeCount = _allNodes.value.size,
                groupSelectionTransform = null,
            )
        }
        recomputeGroupTransform(newSel)
        recalculateVisibleNodes()
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

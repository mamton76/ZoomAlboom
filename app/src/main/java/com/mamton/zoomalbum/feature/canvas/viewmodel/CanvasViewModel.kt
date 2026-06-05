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
import com.mamton.zoomalbum.core.math.CameraAnimation
import com.mamton.zoomalbum.core.math.CameraInterpolation
import com.mamton.zoomalbum.core.math.LodResolver
import com.mamton.zoomalbum.core.math.ResizeHandle
import com.mamton.zoomalbum.core.math.TransformUtils
import com.mamton.zoomalbum.core.math.ViewportCuller
import com.mamton.zoomalbum.core.math.toCamera
import com.mamton.zoomalbum.core.mvi.Intent
import com.mamton.zoomalbum.domain.model.AlbumBackground
import com.mamton.zoomalbum.domain.model.AlbumPresentationProfile
import com.mamton.zoomalbum.domain.model.CanvasInteractionMode
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.CanvasNodeFactory
import com.mamton.zoomalbum.domain.model.EasingType
import com.mamton.zoomalbum.domain.model.FrameAppearance
import com.mamton.zoomalbum.domain.model.FrameEditOptions
import com.mamton.zoomalbum.domain.model.MediaAppearance
import com.mamton.zoomalbum.feature.canvas.editor.EditorState
import com.mamton.zoomalbum.feature.canvas.editor.EditorTool
import com.mamton.zoomalbum.domain.model.FrameFitMode
import com.mamton.zoomalbum.domain.model.MembershipOrigin
import com.mamton.zoomalbum.domain.model.MembershipState
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.domain.model.SceneGraph
import com.mamton.zoomalbum.domain.model.Transform
import com.mamton.zoomalbum.domain.model.TransitionPreset
import com.mamton.zoomalbum.domain.model.withTransform
import com.mamton.zoomalbum.domain.repository.HistoryRepository
import com.mamton.zoomalbum.domain.repository.MediaRepository
import com.mamton.zoomalbum.domain.usecase.ApplyFrameEditUseCase
import com.mamton.zoomalbum.domain.usecase.FrameMembershipUseCase
import com.mamton.zoomalbum.domain.usecase.FrameOverrideUseCase
import com.mamton.zoomalbum.domain.undo.AlbumBackgroundChange
import com.mamton.zoomalbum.domain.undo.CanvasCommand
import com.mamton.zoomalbum.domain.undo.CommandHistory
import com.mamton.zoomalbum.domain.undo.CommandKind
import com.mamton.zoomalbum.domain.undo.InteractionKind
import com.mamton.zoomalbum.domain.undo.toCommandKind
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

/**
 * Top-level canvas state. Split into two coherent groups:
 *
 *  - **Canvas / rendering state** (camera, visible nodes, profile, album
 *    background, in-flight focus animation, loading flag). Read by the renderer
 *    and culling.
 *  - **Editor-session state** (`editor`). Owns gesture interpretation +
 *    overlay rendering — see [EditorState].
 *
 * UI-surface state (open popups / bottom sheets / dialogs) deliberately lives
 * outside this class, in `CanvasScaffold`'s `remember` cells, because the
 * presentation surface for a given editor operation may differ between phone
 * (bottom sheet / popup) and tablet (docked panel) without changing the
 * editor-session semantics.
 */
data class CanvasState(
    val camera: Camera = Camera(),
    val visibleNodes: List<VisibleNode> = emptyList(),
    val totalNodeCount: Int = 0,
    val isLoading: Boolean = true,
    val profile: AlbumPresentationProfile? = null,
    /** Transient in-flight focus animation. Cancelled by any pan/pinch gesture. */
    val cameraAnimation: CameraAnimation? = null,
    val albumBackground: AlbumBackground? = null,
    val editor: EditorState = EditorState(),
)

// ── Actions ───────────────────────────────────────────────────────────

sealed interface CanvasAction : Intent {
    // Selection
    data class SelectNode(val nodeId: String) : CanvasAction
    data class ToggleNodeSelection(val nodeId: String) : CanvasAction
    /**
     * Add a single node to the selection. Idempotent — no-op if the node is
     * already selected. Dispatched by the long-press gesture (replaces the
     * earlier `ToggleNodeSelection` dispatch); the toggle action is retained
     * for the future context-menu "Remove this from selection" item.
     * See `docs/architecture/context-menu.md`.
     */
    data class AddNodeToSelection(val nodeId: String) : CanvasAction
    /** Union the given ids into the current selection. Insertion order preserved. */
    data class AddNodesToSelection(val nodeIds: Set<String>) : CanvasAction
    /**
     * Rectangle selection result.
     *
     * @param additive if true, union with the current selection (keeps previously
     *        selected nodes). If false (default), replaces the selection.
     */
    /**
     * Replace or extend the selection with every node whose **screen-space**
     * AABB intersects [screenRect]. Screen-space — not world — so the
     * marquee stays axis-aligned to the screen regardless of camera
     * rotation. See `selectionMarqueeGestures` + `TransformUtils.toScreenBoundingBox`.
     */
    data class SelectNodesInRect(
        val screenRect: BoundingBox,
        val additive: Boolean = false,
    ) : CanvasAction
    data object DeselectAll : CanvasAction

    // Group operations — apply to ALL selected nodes
    data class MoveSelection(val worldDx: Float, val worldDy: Float) : CanvasAction
    /**
     * Uniform scale around [pivotX, pivotY] (world coords). The pivot is the
     * opposite corner of the dragged handle so that corner stays fixed during
     * the gesture — corner-anchored resize, not center-anchored.
     */
    data class ResizeSelection(
        val scaleFactor: Float,
        val pivotX: Float,
        val pivotY: Float,
    ) : CanvasAction
    data class RotateSelection(val angleDelta: Float) : CanvasAction

    // Lifecycle
    data object DeleteSelection : CanvasAction
    /**
     * Delete an explicit set of nodes by id. One `CanvasCommand` entry —
     * undo restores all of them at their original indices, preserving
     * z-order. Used by Object-mode Eraser (`editor-tools.md § 4.6`) for
     * both tap-on-node (single-id set) and each per-cross step of a scrub
     * (also single-id set). Each crossing in a scrub becomes its own undo
     * entry, symmetric with tap-delete. The ids are pruned from the active
     * selection if present.
     */
    data class DeleteNodes(val ids: Set<String>) : CanvasAction

    data object DuplicateSelection : CanvasAction
    data class BeginInteraction(val kind: InteractionKind) : CanvasAction
    data object FinishInteraction : CanvasAction

    // Undo/Redo
    data object Undo : CanvasAction
    data object Redo : CanvasAction

    // Rectangle selection preview
    /**
     * Sets the live marquee rect (screen coords, axis-aligned to screen).
     * `null` clears it. The rect is interpreted as screen-space everywhere
     * — see [SelectNodesInRect] and `SelectionRectOverlay`.
     */
    data class UpdateSelectionRect(val screenRect: BoundingBox?) : CanvasAction

    // Mode + navigation
    data class SetMode(val mode: CanvasInteractionMode) : CanvasAction
    /** Animated camera focus on the given node (frame or media). */
    data class FocusNode(val nodeId: String) : CanvasAction

    /**
     * Sets the active editor tool. Selection persists across tool switches by
     * construction — see `docs/architecture/editor-tools.md § 6`.
     */
    data class SetActiveTool(val tool: EditorTool) : CanvasAction

    /**
     * Sets the long-press anchor node id — the node decorated with an outer
     * halo while the context menu popup is open. `null` clears the halo.
     * Driven by `CanvasScaffold`'s `LaunchedEffect` that mirrors the
     * popup's `ContextMenuRequest.anchorNodeId` into MVI state.
     */
    data class SetContextAnchor(val nodeId: String?) : CanvasAction

    // Backgrounds (§19)
    data class SetAlbumBackground(val background: AlbumBackground?) : CanvasAction

    // Appearance (§20). Replaces the entire FrameAppearance on each node in
    // [appearancesById] — keys identify target frames, values are the new
    // appearance (`null` resets to default rendering). The map shape carries
    // per-node values so a multi-edit session can fan out different appearances
    // in one snapshot command; today's single-node callers pass a single-entry
    // map. Ids that don't resolve to a Frame are silently skipped; a session
    // where no target actually changes pushes no history.
    data class SetFrameAppearance(
        val appearancesById: Map<String, FrameAppearance?>,
    ) : CanvasAction

    /**
     * Replaces the entire `MediaAppearance` on each node in [appearancesById] —
     * keys identify target media nodes, values are the new appearance (`null`
     * resets to default rendering). Per-node payload supports multi-edit
     * sessions where touched fields go uniform but untouched fields keep
     * per-node variation (`docs/architecture/appearance.md § 14.2`). Ids that
     * don't resolve to a Media node are silently skipped; a session where no
     * target actually changes pushes no history.
     */
    data class SetMediaAppearance(
        val appearancesById: Map<String, MediaAppearance?>,
    ) : CanvasAction

    // Frame membership — explicit pin / detach / clear overrides. See frame-membership.md.
    data class PinToFrame(val frameId: String, val nodeIds: Set<String>) : CanvasAction
    data class DetachFromFrame(val frameId: String, val nodeIds: Set<String>) : CanvasAction
    /** Drop override entries for [nodeIds] on [frameId]; membership reverts to geometry. */
    data class ClearFrameOverrides(val frameId: String, val nodeIds: Set<String>) : CanvasAction

    /** Transient toggles for the next frame transform gesture. */
    data class SetFrameEditOptions(val options: FrameEditOptions) : CanvasAction

    // Z-order — single-node only for MVP. Multi-select disables the button.
    data class BringToFront(val nodeId: String) : CanvasAction
    data class SendToBack(val nodeId: String) : CanvasAction
    data class BringForward(val nodeId: String) : CanvasAction
    data class SendBackward(val nodeId: String) : CanvasAction
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

    private val frameOverrideUseCase = FrameOverrideUseCase()
    private val frameMembershipUseCase = FrameMembershipUseCase()
    private val applyFrameEditUseCase = ApplyFrameEditUseCase(frameMembershipUseCase)

    /**
     * For frame-edit gestures: ids of nodes the gesture should move/transform together.
     * Captured at BeginInteraction when the selection is exactly one Frame and
     * `transformContents=true`. Null otherwise (gesture uses `selectedNodeIds`).
     */
    private var pendingGestureNodeIds: Set<String>? = null
    /** Frames whose membership may need post-gesture rebind handling. Populated at
     *  BeginInteraction for every Frame in the selection; rebind-suppression runs
     *  per frame on FinishInteraction. */
    private var pendingEditedFrameIds: Set<String> = emptySet()

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
    private var cameraAnimationJob: Job? = null

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
        // Any direct gesture cancels an in-flight focus animation.
        cancelCameraAnimation()
        val oldCam = _state.value.camera
        _state.update { s ->
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
        _state.update { s ->
            s.copy(
                totalNodeCount = _allNodes.value.size,
                editor = s.editor.copy(
                    selectedNodeIds = setOf(node.id),
                    groupSelectionTransform = null,
                ),
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
                updateEditor { it.copy(selectedNodeIds = ids) }
                recomputeGroupTransform(ids)
            }

            is CanvasAction.ToggleNodeSelection -> {
                _state.update { s ->
                    val updated = if (action.nodeId in s.editor.selectedNodeIds)
                        s.editor.selectedNodeIds - action.nodeId
                    else
                        s.editor.selectedNodeIds + action.nodeId
                    s.copy(editor = s.editor.copy(selectedNodeIds = updated))
                }
                recomputeGroupTransform(_state.value.editor.selectedNodeIds)
            }

            is CanvasAction.AddNodeToSelection -> {
                if (action.nodeId in _state.value.editor.selectedNodeIds) return
                val updated = _state.value.editor.selectedNodeIds + action.nodeId
                updateEditor { it.copy(selectedNodeIds = updated) }
                recomputeGroupTransform(updated)
            }

            is CanvasAction.AddNodesToSelection -> {
                if (action.nodeIds.isEmpty()) return
                val merged = _state.value.editor.selectedNodeIds + action.nodeIds
                if (merged.size == _state.value.editor.selectedNodeIds.size) return
                updateEditor { it.copy(selectedNodeIds = merged) }
                recomputeGroupTransform(merged)
            }

            is CanvasAction.SelectNodesInRect -> {
                // Marquee rect is in SCREEN coordinates (axis-aligned to the
                // screen, decoupled from camera rotation). Hit-test each
                // node's projected screen AABB against it.
                val cam = _state.value.camera
                val rectHits = _allNodes.value
                    .filter {
                        TransformUtils
                            .toScreenBoundingBox(it.transform, cam)
                            .intersects(action.screenRect)
                    }
                    .map { it.id }
                    .toSet()
                val ids = if (action.additive) {
                    _state.value.editor.selectedNodeIds + rectHits
                } else {
                    rectHits
                }
                updateEditor { it.copy(selectedNodeIds = ids, selectionRect = null) }
                recomputeGroupTransform(ids)
            }

            is CanvasAction.DeselectAll -> {
                updateEditor { it.copy(selectedNodeIds = emptySet(), groupSelectionTransform = null) }
            }

            is CanvasAction.MoveSelection -> {
                // During a frame-edit move-with-content gesture, `pendingGestureNodeIds`
                // expands the moved set to include the frame's effective members captured
                // at BeginInteraction. Outside such a gesture, plain selection moves.
                val ids = pendingGestureNodeIds ?: _state.value.editor.selectedNodeIds
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
                _state.value.editor.groupSelectionTransform?.let { gt ->
                    updateEditor {
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
                // Frame-edit resize-with-content uses the augmented set captured at
                // BeginInteraction; the pivot is supplied by the gesture detector.
                val ids = pendingGestureNodeIds ?: _state.value.editor.selectedNodeIds
                val selected = _allNodes.value.filter { it.id in ids }
                if (selected.isEmpty()) return

                val factor = action.scaleFactor.coerceIn(0.01f, 100f)
                val px = action.pivotX
                val py = action.pivotY

                _allNodes.update { nodes ->
                    nodes.map { node ->
                        if (node.id in ids) {
                            val t = node.transform
                            val newCx = px + (t.cx - px) * factor
                            val newCy = py + (t.cy - py) * factor
                            val newScale = (t.scale * factor).coerceIn(0.01f, 1000f)
                            node.withTransform(t.copy(cx = newCx, cy = newCy, scale = newScale))
                        } else node
                    }
                }
                inlinePatchVisibleNodes(ids) { t ->
                    val newCx = px + (t.cx - px) * factor
                    val newCy = py + (t.cy - py) * factor
                    val newScale = (t.scale * factor).coerceIn(0.01f, 1000f)
                    t.copy(cx = newCx, cy = newCy, scale = newScale)
                }
                // Group rect: its center moves with the pivot too, w/h scale uniformly.
                _state.value.editor.groupSelectionTransform?.let { gt ->
                    val newGtCx = px + (gt.cx - px) * factor
                    val newGtCy = py + (gt.cy - py) * factor
                    updateEditor {
                        it.copy(
                            groupSelectionTransform = gt.copy(
                                cx = newGtCx,
                                cy = newGtCy,
                                w = gt.w * factor,
                                h = gt.h * factor,
                            ),
                        )
                    }
                }
            }

            is CanvasAction.RotateSelection -> {
                // Frame-edit rotate-with-content: rotate the augmented set, but the pivot
                // must come from the user's selection (the frame center) — using the
                // centroid of (frame + members) would orbit the wrong point.
                val ids = pendingGestureNodeIds ?: _state.value.editor.selectedNodeIds
                val pivotIds = _state.value.editor.selectedNodeIds
                val selected = _allNodes.value.filter { it.id in ids }
                if (selected.isEmpty()) return

                // Use group transform center if available (stable during rotation),
                // otherwise compute from the user-selected node positions.
                val gt = _state.value.editor.groupSelectionTransform
                val (gcx, gcy) = if (gt != null) {
                    gt.cx to gt.cy
                } else {
                    val pivotNodes = _allNodes.value.filter { it.id in pivotIds }
                    TransformUtils.groupCenter(pivotNodes.ifEmpty { selected })
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
                    updateEditor {
                        it.copy(
                            groupSelectionTransform = gt.copy(
                                rotation = gt.rotation + action.angleDelta,
                            ),
                        )
                    }
                }
            }

            is CanvasAction.DeleteSelection ->
                deleteNodesById(_state.value.editor.selectedNodeIds)

            is CanvasAction.DeleteNodes -> deleteNodesById(action.ids)

            is CanvasAction.DuplicateSelection -> {
                val ids = _state.value.editor.selectedNodeIds
                val sources = _allNodes.value.filter { it.id in ids }
                if (sources.isEmpty()) return
                // Diagonal offset constant in screen pixels — visible at any zoom,
                // rotated to match camera so the shift always reads as down-right on screen.
                val cam = _state.value.camera
                val shiftWorld = DUPLICATE_SHIFT_PX / cam.scale
                val (dx, dy) = TransformUtils.rotateVector(shiftWorld, shiftWorld, -cam.rotation)
                // Single timestamp + per-copy index — currentTimeMillis() in a tight loop
                // returns the same value for every copy, and substringBefore("_copy")
                // strips prior copy suffixes, so sources sharing a base name would
                // otherwise collapse to identical ids.
                val ts = System.currentTimeMillis()
                var z = nextZIndex()
                val copies = sources.mapIndexed { index, node ->
                    val t = node.transform
                    val newId = "${node.id.substringBefore("_copy")}_copy_${ts}_$index"
                    val newTransform = t.copy(cx = t.cx + dx, cy = t.cy + dy, zIndex = z)
                    z += 1f
                    node.withTransform(newTransform).let { copy ->
                        when (copy) {
                            is CanvasNode.Frame -> copy.copy(id = newId)
                            is CanvasNode.Media -> copy.copy(id = newId)
                        }
                    }
                }
                _allNodes.update { it + copies }
                val newIds = copies.map { c -> c.id }.toSet()
                _state.update { s ->
                    s.copy(
                        totalNodeCount = _allNodes.value.size,
                        editor = s.editor.copy(selectedNodeIds = newIds),
                    )
                }
                // Rebuild group rect for the new selection — without this the rect stays
                // pinned at the originals' old position and handles render in the wrong place.
                recomputeGroupTransform(newIds)
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
                val ids = _state.value.editor.selectedNodeIds
                if (ids.isEmpty()) return

                // Frame-edit augmentation: for EVERY Frame in the selection, optionally
                // expand the gesture's touched set to include its effective members.
                // Applies to MOVE / RESIZE / ROTATE — all three share the gesture-set
                // mechanism. Rebind suppression below runs per frame regardless of
                // `transformContents` (geometry can change for frame-only edits too).
                val allNodes = _allNodes.value
                val selectedFrames = allNodes.filter { it.id in ids }.filterIsInstance<CanvasNode.Frame>()
                val opts = _state.value.editor.frameEditOptions
                val gestureIds: Set<String> = if (selectedFrames.isNotEmpty() && opts.transformContents) {
                    val members = selectedFrames.flatMap {
                        frameMembershipUseCase.effectiveMembers(it, allNodes)
                    }.toSet()
                    ids + members
                } else {
                    ids
                }

                pendingSnapshot = allNodes.filter { it.id in gestureIds }
                pendingKind = action.kind
                pendingGestureNodeIds = if (gestureIds != ids) gestureIds else null
                pendingEditedFrameIds = selectedFrames.map { it.id }.toSet()
            }

            is CanvasAction.FinishInteraction -> {
                applyPendingRebindSuppression()
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
                updateEditor { it.copy(selectionRect = action.screenRect) }
            }

            is CanvasAction.SetMode -> {
                val target = action.mode
                if (_state.value.editor.mode == target) return
                updateEditor { e ->
                    if (target != CanvasInteractionMode.Edit) {
                        // Any non-Edit mode is read-only — clear selection so
                        // overlays/handles/action bar are auto-hidden.
                        e.copy(
                            mode = target,
                            selectedNodeIds = emptySet(),
                            groupSelectionTransform = null,
                            selectionRect = null,
                        )
                    } else {
                        e.copy(mode = target)
                    }
                }
            }

            is CanvasAction.FocusNode -> {
                val node = _allNodes.value.firstOrNull { it.id == action.nodeId } ?: return
                startCameraAnimation(node.transform)
            }

            is CanvasAction.SetActiveTool -> {
                if (_state.value.editor.activeTool == action.tool) return
                updateEditor { it.copy(activeTool = action.tool) }
            }

            is CanvasAction.SetContextAnchor -> {
                if (_state.value.editor.contextAnchorNodeId == action.nodeId) return
                updateEditor { it.copy(contextAnchorNodeId = action.nodeId) }
            }

            is CanvasAction.SetAlbumBackground -> {
                val before = _state.value.albumBackground
                val after = action.background
                if (before == after) return
                _state.update { it.copy(albumBackground = after) }
                commit(
                    CanvasCommand(
                        before = null,
                        after = null,
                        albumBackgroundChange = AlbumBackgroundChange(before, after),
                        kind = CommandKind.SET_ALBUM_BACKGROUND,
                        timestampMs = System.currentTimeMillis(),
                    ),
                )
            }

            is CanvasAction.SetFrameAppearance -> {
                // Collapse all-default appearances back to `null` per node so
                // the JSON stays tidy when the user clears every field — applied
                // independently per id so per-node Mixed state survives.
                val normalized = action.appearancesById.mapValues { (_, appr) ->
                    appr?.takeUnless { it == FrameAppearance() }
                }
                val result = computeAppearanceUpdate<CanvasNode.Frame>(
                    currentNodes = _allNodes.value,
                    ids = normalized.keys,
                    kind = CommandKind.SET_FRAME_APPEARANCE,
                    timestampMs = System.currentTimeMillis(),
                ) { frame -> frame.copy(appearance = normalized[frame.id]) }
                val command = result.command ?: return
                applyAppearanceResult(result, command)
            }

            is CanvasAction.SetMediaAppearance -> {
                val normalized = action.appearancesById.mapValues { (_, appr) ->
                    appr?.takeUnless { it == MediaAppearance() }
                }
                val result = computeAppearanceUpdate<CanvasNode.Media>(
                    currentNodes = _allNodes.value,
                    ids = normalized.keys,
                    kind = CommandKind.SET_MEDIA_APPEARANCE,
                    timestampMs = System.currentTimeMillis(),
                ) { media -> media.copy(appearance = normalized[media.id]) }
                val command = result.command ?: return
                applyAppearanceResult(result, command)
            }

            is CanvasAction.PinToFrame -> applyFrameOverride(
                action.frameId, action.nodeIds, MembershipState.Included,
            )

            is CanvasAction.DetachFromFrame -> applyFrameOverride(
                action.frameId, action.nodeIds, MembershipState.Excluded,
            )

            is CanvasAction.ClearFrameOverrides -> applyClearFrameOverrides(
                action.frameId, action.nodeIds,
            )

            is CanvasAction.SetFrameEditOptions -> {
                updateEditor { it.copy(frameEditOptions = action.options) }
            }

            is CanvasAction.BringToFront -> applyZIndexReorder(action.nodeId, ZReorder.ToFront)
            is CanvasAction.SendToBack -> applyZIndexReorder(action.nodeId, ZReorder.ToBack)
            is CanvasAction.BringForward -> applyZIndexReorder(action.nodeId, ZReorder.Forward)
            is CanvasAction.SendBackward -> applyZIndexReorder(action.nodeId, ZReorder.Backward)
        }
    }

    // ── Frame membership overrides ────────────────────────────────────

    /**
     * Pin / detach helper. Writes `(state, MembershipOrigin.User)` overrides for [nodeIds]
     * on the frame [frameId]. No-op if the frame doesn't exist, isn't a Frame, or the
     * override map is unchanged. Undo round-trips via [CommandKind.SET_FRAME_OVERRIDES].
     */
    private fun applyFrameOverride(
        frameId: String,
        nodeIds: Set<String>,
        state: MembershipState,
    ) {
        if (nodeIds.isEmpty()) return
        val current = _allNodes.value
        val idx = current.indexOfFirst { it.id == frameId }
        if (idx < 0) return
        val frame = current[idx] as? CanvasNode.Frame ?: return
        val updated = frameOverrideUseCase.applyOverride(
            frame = frame,
            nodeIds = nodeIds,
            state = state,
            origin = MembershipOrigin.User,
        )
        if (updated === frame) return
        _allNodes.update { nodes -> nodes.map { if (it.id == frameId) updated else it } }
        _state.update { s ->
            s.copy(
                visibleNodes = s.visibleNodes.map { vn ->
                    if (vn.node.id == frameId) vn.copy(node = updated) else vn
                },
            )
        }
        commit(
            CanvasCommand(
                before = listOf(frame),
                after = listOf(updated),
                kind = CommandKind.SET_FRAME_OVERRIDES,
                timestampMs = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Clear-overrides helper. Drops `Frame.overrides` entries for [nodeIds]; membership
     * reverts to pure geometry. No-op if the frame doesn't exist, isn't a Frame, or the
     * override map is unchanged. Undo round-trips via [CommandKind.SET_FRAME_OVERRIDES].
     */
    private fun applyClearFrameOverrides(frameId: String, nodeIds: Set<String>) {
        if (nodeIds.isEmpty()) return
        val current = _allNodes.value
        val idx = current.indexOfFirst { it.id == frameId }
        if (idx < 0) return
        val frame = current[idx] as? CanvasNode.Frame ?: return
        val updated = frameOverrideUseCase.clearOverrides(frame, nodeIds)
        if (updated === frame) return
        _allNodes.update { nodes -> nodes.map { if (it.id == frameId) updated else it } }
        _state.update { s ->
            s.copy(
                visibleNodes = s.visibleNodes.map { vn ->
                    if (vn.node.id == frameId) vn.copy(node = updated) else vn
                },
            )
        }
        commit(
            CanvasCommand(
                before = listOf(frame),
                after = listOf(updated),
                kind = CommandKind.SET_FRAME_OVERRIDES,
                timestampMs = System.currentTimeMillis(),
            ),
        )
    }

    // ── Z-order ───────────────────────────────────────────────────────

    private enum class ZReorder { ToFront, ToBack, Forward, Backward }

    /**
     * Mutates `Transform.zIndex` on [nodeId] (and possibly one neighbor for
     * Forward/Backward swaps). All four actions go through this single helper.
     *
     * Render order depends on `visibleNodes` being sorted by zIndex (done in
     * [recalculateVisibleNodes] and after each reorder via the in-place patch below).
     * Hit-testing already sorts on its own. Undo round-trips via the standard
     * `before/after` snapshot path with [CommandKind.REORDER].
     */
    private fun applyZIndexReorder(nodeId: String, mode: ZReorder) {
        val current = _allNodes.value
        val node = current.firstOrNull { it.id == nodeId } ?: return
        val currentZ = node.transform.zIndex

        val mutations: List<Pair<CanvasNode, CanvasNode>> = when (mode) {
            ZReorder.ToFront -> {
                val maxZ = current.maxOf { it.transform.zIndex }
                if (currentZ >= maxZ) return // already on top
                listOf(node to node.withTransform(node.transform.copy(zIndex = maxZ + 1f)))
            }
            ZReorder.ToBack -> {
                val minZ = current.minOf { it.transform.zIndex }
                if (currentZ <= minZ) return // already at bottom
                listOf(node to node.withTransform(node.transform.copy(zIndex = minZ - 1f)))
            }
            ZReorder.Forward -> {
                // Swap with the next-higher node (smallest zIndex > currentZ).
                val neighbor = current
                    .filter { it.transform.zIndex > currentZ }
                    .minByOrNull { it.transform.zIndex } ?: return
                listOf(
                    node to node.withTransform(node.transform.copy(zIndex = neighbor.transform.zIndex)),
                    neighbor to neighbor.withTransform(neighbor.transform.copy(zIndex = currentZ)),
                )
            }
            ZReorder.Backward -> {
                val neighbor = current
                    .filter { it.transform.zIndex < currentZ }
                    .maxByOrNull { it.transform.zIndex } ?: return
                listOf(
                    node to node.withTransform(node.transform.copy(zIndex = neighbor.transform.zIndex)),
                    neighbor to neighbor.withTransform(neighbor.transform.copy(zIndex = currentZ)),
                )
            }
        }

        val mutatedById = mutations.associate { (_, after) -> after.id to after }
        _allNodes.update { nodes -> nodes.map { mutatedById[it.id] ?: it } }

        // Patch visibleNodes by id, then re-sort so the renderer iteration order
        // reflects the new zIndex. Cheaper than a full viewport re-cull.
        _state.update { s ->
            s.copy(
                visibleNodes = s.visibleNodes
                    .map { vn -> mutatedById[vn.node.id]?.let { vn.copy(node = it) } ?: vn }
                    .sortedBy { it.node.transform.zIndex },
            )
        }

        commit(
            CanvasCommand(
                before = mutations.map { it.first },
                after = mutations.map { it.second },
                kind = CommandKind.REORDER,
                timestampMs = System.currentTimeMillis(),
            ),
        )
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
     * - Multi-select: group rect ([EditorState.groupSelectionTransform]).
     * - Single-select: that node's transform (looked up in [_allNodes], not
     *   visibleNodes — selection must behave consistently even when nodes are
     *   culled out of the viewport).
     * - Empty selection: null.
     */
    fun selectionTransform(): com.mamton.zoomalbum.domain.model.Transform? {
        val editor = _state.value.editor
        val ids = editor.selectedNodeIds
        return when {
            ids.isEmpty() -> null
            ids.size == 1 -> _allNodes.value.firstOrNull { it.id == ids.first() }?.transform
            else -> editor.groupSelectionTransform
        }
    }

    /** True if the screen point is on ANY currently selected node. */
    fun isOnSelectedNode(screenX: Float, screenY: Float): Boolean {
        val ids = _state.value.editor.selectedNodeIds
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
        val camera = _state.value.camera
        val profile = _state.value.profile
        val albumBackground = _state.value.albumBackground
        val historySnapshot = history.snapshot()
        if (albumId != 0L) {
            // Fire-and-forget save — ViewModel scope is cancelled but we use
            // a non-cancellable context for the final save.
            kotlinx.coroutines.MainScope().launch(Dispatchers.IO) {
                try {
                    mediaRepository.saveSceneGraph(
                        albumId,
                        SceneGraph(
                            albumId = albumId,
                            camera = camera,
                            nodes = nodes,
                            profile = profile,
                            background = albumBackground,
                        ),
                    )
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
     * Recomputes [EditorState.groupSelectionTransform] from the current selected nodes.
     * Called when selection changes (add/remove/rect-select).
     *
     * The rect is screen-aligned at formation time (rotation = -camera.rotation),
     * pinned to world afterward. During handle-drag (rotate/resize) the rect
     * is mutated in place — NOT recomputed here.
     */
    private fun recomputeGroupTransform(selectedIds: Set<String>) {
        if (selectedIds.size < 2) {
            updateEditor { it.copy(groupSelectionTransform = null) }
            return
        }
        val selected = _allNodes.value.filter { it.id in selectedIds }
        if (selected.size < 2) {
            updateEditor { it.copy(groupSelectionTransform = null) }
            return
        }
        val cameraRotation = _state.value.camera.rotation
        val gt = TransformUtils.screenAlignedGroupTransform(selected, cameraRotation)
        updateEditor { it.copy(groupSelectionTransform = gt) }
    }

    /** Mutates only [EditorState] under [CanvasState.editor]. */
    private inline fun updateEditor(crossinline block: (EditorState) -> EditorState) {
        _state.update { s -> s.copy(editor = block(s.editor)) }
    }

    private fun loadAlbum() {
        viewModelScope.launch {
            val sceneGraph = if (albumId != 0L) {
                mediaRepository.loadSceneGraph(albumId)
            } else {
                SceneGraph(albumId = albumId)
            }
            _allNodes.value = sceneGraph.nodes
            _state.update {
                it.copy(
                    camera = sceneGraph.camera,
                    profile = sceneGraph.profile,
                    albumBackground = sceneGraph.background,
                    totalNodeCount = sceneGraph.nodes.size,
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
            // Sort by zIndex (ascending — Compose draws in iteration order, so
            // lowest first means highest ends up on top). Render correctness
            // now depends only on Transform.zIndex, not _allNodes insertion
            // order — necessary for BringToFront / SendToBack / etc to work.
            val resolved = geometryVisible
                .mapNotNull { node ->
                    val detail = LodResolver.resolveRenderDetail(node, cam)
                    if (detail == RenderDetail.Hidden) null else VisibleNode(node, detail)
                }
                .sortedBy { it.node.transform.zIndex }
            _state.update { it.copy(visibleNodes = resolved) }
        }
    }

    // ── Camera focus animation ────────────────────────────────────────

    /**
     * Animates [_state].camera from its current value to the camera derived from
     * [targetTransform], using the active profile's transition preset + easing.
     * Cancels any prior animation; first user gesture cancels this one.
     */
    private fun startCameraAnimation(targetTransform: Transform) {
        cancelCameraAnimation()
        val from = _state.value.camera
        val profile = _state.value.profile
        val fitMode = profile?.defaultFitMode ?: FrameFitMode.CONTAIN
        val safeArea = profile?.safeAreaInset ?: 0.1f
        val to = targetTransform.toCamera(screenWidth, screenHeight, fitMode, safeArea)

        val (durationMs, easing) = CameraInterpolation.resolveTransition(
            preset = profile?.defaultTransitionPreset ?: TransitionPreset.SOFT,
            profileEasing = profile?.defaultEasing ?: EasingType.EASE_IN_OUT,
            from = from,
            to = to,
        )

        val animation = CameraAnimation(
            from = from,
            to = to,
            startTimeMs = System.currentTimeMillis(),
            durationMs = durationMs,
            easing = easing,
        )
        _state.update { it.copy(cameraAnimation = animation) }

        cameraAnimationJob = viewModelScope.launch {
            val startNs = System.nanoTime()
            while (isActive) {
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000f
                val t = (elapsedMs / durationMs.toFloat()).coerceIn(0f, 1f)
                val current = CameraInterpolation.interpolate(from, to, t, easing)
                _state.update { it.copy(camera = current) }
                recalculateVisibleNodes()
                if (t >= 1f) break
                delay(FRAME_DELAY_MS)
            }
            _state.update { it.copy(cameraAnimation = null) }
            cameraAnimationJob = null
        }
    }

    private fun cancelCameraAnimation() {
        cameraAnimationJob?.cancel()
        cameraAnimationJob = null
        if (_state.value.cameraAnimation != null) {
            _state.update { it.copy(cameraAnimation = null) }
        }
    }

    // ── Undo/Redo internals ───────────────────────────────────────────

    private fun commit(command: CanvasCommand) {
        history.push(command)
        refreshHistoryFlags()
    }

    /**
     * Apply a non-empty appearance fan-out result: swap `_allNodes`, patch the
     * affected entries in `visibleNodes` (avoids a full re-cull), commit the
     * snapshot. Callers must have already null-checked `result.command`.
     */
    private fun applyAppearanceResult(result: AppearanceUpdateResult, command: CanvasCommand) {
        val updatedById = (command.after ?: emptyList()).associateBy { it.id }
        _allNodes.value = result.updatedNodes
        _state.update { s ->
            s.copy(
                visibleNodes = s.visibleNodes.map { vn ->
                    updatedById[vn.node.id]?.let { vn.copy(node = it) } ?: vn
                },
            )
        }
        commit(command)
    }

    private fun refreshHistoryFlags() {
        _canUndo.value = history.canUndo
        _canRedo.value = history.canRedo
    }

    /**
     * Atomic bulk-delete of [ids] with one [CanvasCommand] entry. Used by
     * both [CanvasAction.DeleteSelection] (passes the current selection) and
     * [CanvasAction.DeleteNodes] (passes explicit ids — including each
     * per-cross step of an Object-mode Eraser scrub). Removes the nodes
     * from `_allNodes`, prunes them from `editor.selectedNodeIds` in place
     * (so partial deletes preserve any remaining selection), and rebuilds
     * `groupSelectionTransform` if the surviving selection is still a
     * multi-selection.
     */
    private fun deleteNodesById(ids: Set<String>) {
        if (ids.isEmpty()) return
        val current = _allNodes.value
        val removedWithIdx = current.withIndex()
            .filter { it.value.id in ids }
            .map { it.index to it.value }
        if (removedWithIdx.isEmpty()) return
        _allNodes.update { nodes -> nodes.filter { it.id !in ids } }
        _state.update { s ->
            s.copy(
                totalNodeCount = _allNodes.value.size,
                editor = s.editor.copy(selectedNodeIds = s.editor.selectedNodeIds - ids),
            )
        }
        val newSelection = _state.value.editor.selectedNodeIds
        when {
            newSelection.size >= 2 -> recomputeGroupTransform(newSelection)
            newSelection.isEmpty() ->
                updateEditor { it.copy(groupSelectionTransform = null) }
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

    /**
     * Commits the in-flight gesture snapshot, if any. Called on FinishInteraction
     * and defensively on Undo/Redo. Skips the push if the gesture was a no-op
     * (before == after — e.g. user pressed a handle and lifted without moving).
     */
    private fun commitPendingInteraction() {
        val before = pendingSnapshot ?: run {
            // No snapshot in flight — clear gesture-augment fields defensively and exit.
            pendingGestureNodeIds = null
            pendingEditedFrameIds = emptySet()
            return
        }
        val kind = pendingKind ?: InteractionKind.MOVE  // shouldn't happen; safe default
        // Order `after` to match `before`'s id ordering — positional pairing.
        val byId: Map<String, CanvasNode> = _allNodes.value.associateBy { it.id }
        val after = before.mapNotNull { byId[it.id] }
        pendingSnapshot = null
        pendingKind = null
        pendingGestureNodeIds = null
        pendingEditedFrameIds = emptySet()
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
     * Frame-edit rebind suppression. Runs at FinishInteraction, BEFORE
     * [commitPendingInteraction], so override changes are captured in the same
     * compound CanvasCommand as the transform changes.
     *
     * When `rebindAfterEdit=false`, iterates every frame in [pendingEditedFrameIds]
     * and writes `RebindSuppressed` overrides so each frame's pre-edit logical
     * membership survives. See `docs/architecture/frame-membership.md`.
     */
    private fun applyPendingRebindSuppression() {
        if (pendingEditedFrameIds.isEmpty()) return
        val snapshot = pendingSnapshot ?: return
        if (_state.value.editor.frameEditOptions.rebindAfterEdit) return

        val current = _allNodes.value
        val snapshotById = snapshot.associateBy { it.id }
        // Reconstruct the pre-gesture world by overlaying snapshot versions onto current nodes.
        val allNodesBefore = current.map { node -> snapshotById[node.id] ?: node }
        val options = _state.value.editor.frameEditOptions

        val updatedById = mutableMapOf<String, CanvasNode.Frame>()
        for (frameId in pendingEditedFrameIds) {
            val frameBefore = snapshot.firstOrNull { it.id == frameId } as? CanvasNode.Frame ?: continue
            val frameAfter = current.firstOrNull { it.id == frameId } as? CanvasNode.Frame ?: continue
            val updated = applyFrameEditUseCase.applyFrameEdit(
                frameBefore = frameBefore,
                frameAfter = frameAfter,
                allNodesBefore = allNodesBefore,
                allNodesAfter = current,
                options = options,
            )
            if (updated !== frameAfter) updatedById[frameId] = updated
        }
        if (updatedById.isEmpty()) return

        _allNodes.update { nodes ->
            nodes.map { updatedById[it.id] ?: it }
        }
        _state.update { s ->
            s.copy(
                visibleNodes = s.visibleNodes.map { vn ->
                    val u = updatedById[vn.node.id]
                    if (u != null) vn.copy(node = u) else vn
                },
            )
        }
    }

    /**
     * Applies [cmd] to `_allNodes` and refreshes derived state. When [reverse]
     * is true, applies the inverse direction (used for Undo). List order is
     * preserved across all three command shapes; for restored deletes,
     * [CanvasCommand.beforeIndices] is used to re-insert at the original positions.
     */
    private fun applyCommand(cmd: CanvasCommand, reverse: Boolean) {
        // Album-only commands carry no node sides — handle and return early.
        if (cmd.albumBackgroundChange != null && cmd.before == null && cmd.after == null) {
            val target = if (reverse) cmd.albumBackgroundChange.before else cmd.albumBackgroundChange.after
            _state.update { it.copy(albumBackground = target) }
            return
        }

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
        _state.update { s ->
            s.copy(
                totalNodeCount = _allNodes.value.size,
                editor = s.editor.copy(
                    selectedNodeIds = newSel,
                    groupSelectionTransform = null,
                ),
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
        /** Diagonal offset (screen px) applied to duplicated nodes. */
        const val DUPLICATE_SHIFT_PX = 40f
        /** ~60 fps tick interval for camera focus animations. */
        private const val FRAME_DELAY_MS = 16L
    }
}

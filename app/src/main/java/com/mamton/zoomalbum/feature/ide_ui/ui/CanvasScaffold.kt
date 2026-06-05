package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamton.zoomalbum.domain.model.BackgroundData
import com.mamton.zoomalbum.domain.model.BorderStyle
import com.mamton.zoomalbum.domain.model.CanvasInteractionMode
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.CanvasNodeFactory
import com.mamton.zoomalbum.domain.model.CaptionStyle
import com.mamton.zoomalbum.domain.model.CropSettings
import com.mamton.zoomalbum.domain.model.FrameAppearance
import com.mamton.zoomalbum.domain.model.MediaAppearance
import com.mamton.zoomalbum.domain.model.MediaColorAdjustments
import com.mamton.zoomalbum.domain.model.MediaFrameDecoration
import com.mamton.zoomalbum.domain.model.OverlayStyle
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.domain.model.ShadowStyle
import com.mamton.zoomalbum.feature.canvas.actions.EditorAction
import com.mamton.zoomalbum.feature.canvas.actions.EditorActionEffect
import com.mamton.zoomalbum.feature.canvas.actions.SelectionContext
import com.mamton.zoomalbum.feature.canvas.editor.AppearanceTarget
import com.mamton.zoomalbum.feature.canvas.view.CanvasScreen
import com.mamton.zoomalbum.feature.canvas.view.ContextMenuPopup
import com.mamton.zoomalbum.feature.canvas.view.ContextMenuRequest
import com.mamton.zoomalbum.feature.canvas.view.SelectionDebugPanel
import com.mamton.zoomalbum.feature.canvas.view.buildEditContextMenuItems
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasViewModel
import com.mamton.zoomalbum.feature.ide_ui.ui.content.BackgroundEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.BorderStyleEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.CaptionStyleEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.ColorAdjustmentsEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.CropEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.MediaFrameDecorationEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.MixedAwareCornerRadiusSlider
import com.mamton.zoomalbum.feature.ide_ui.ui.content.MixedAwareOpacitySlider
import com.mamton.zoomalbum.feature.ide_ui.ui.content.OverlayListEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.content.ShadowStyleEditor
import com.mamton.zoomalbum.feature.ide_ui.ui.sheets.AddContentBottomSheet
import com.mamton.zoomalbum.feature.ide_ui.ui.sheets.AlbumSettingsBottomSheet
import com.mamton.zoomalbum.feature.ide_ui.ui.sheets.ConceptEditorSheet
import com.mamton.zoomalbum.feature.ide_ui.ui.sheets.FrameListBottomSheet
import com.mamton.zoomalbum.feature.ide_ui.viewmodel.IdeViewModel

@Composable
fun CanvasScaffold(
    albumName: String = "Album",
    onNavigateBack: () -> Unit = {},
) {
    val canvasViewModel: CanvasViewModel = hiltViewModel()
    val canvasState by canvasViewModel.state.collectAsStateWithLifecycle()
    val ideViewModel: IdeViewModel = hiltViewModel()
    val ideState by ideViewModel.state.collectAsStateWithLifecycle()

    val frames by canvasViewModel.frames.collectAsStateWithLifecycle()
    val canUndo by canvasViewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by canvasViewModel.canRedo.collectAsStateWithLifecycle()

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        canvasViewModel.addMedia(uri)
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var showFrameList by remember { mutableStateOf(false) }
    var showPanelConfig by remember { mutableStateOf(false) }
    var showAlbumSettings by remember { mutableStateOf(false) }
    // Per-concept editor state (`appearance.md § 14.1`). Universal concepts
    // (opacity / cornerRadius / border / shadow / overlays) populate an
    // `AppearanceTarget` discriminator since they apply to either all-frames or
    // all-media selections. Type-specific concepts hold a typed node list
    // directly.
    var opacityEditing by remember { mutableStateOf<AppearanceTarget>(AppearanceTarget.None) }
    var cornerRadiusEditing by remember { mutableStateOf<AppearanceTarget>(AppearanceTarget.None) }
    var borderEditing by remember { mutableStateOf<AppearanceTarget>(AppearanceTarget.None) }
    var shadowEditing by remember { mutableStateOf<AppearanceTarget>(AppearanceTarget.None) }
    var overlaysEditing by remember { mutableStateOf<AppearanceTarget>(AppearanceTarget.None) }
    var backgroundEditing by remember { mutableStateOf<List<CanvasNode.Frame>>(emptyList()) }
    var cropEditing by remember { mutableStateOf<List<CanvasNode.Media>>(emptyList()) }
    var colorAdjustmentsEditing by remember { mutableStateOf<List<CanvasNode.Media>>(emptyList()) }
    var frameDecorationEditing by remember { mutableStateOf<List<CanvasNode.Media>>(emptyList()) }
    var captionEditing by remember { mutableStateOf<List<CanvasNode.Media>>(emptyList()) }
    var contextMenuRequest by remember { mutableStateOf<ContextMenuRequest?>(null) }

    // Top-level chrome controls (top bar, FAB) treat their tap as an outside-tap
    // of the context-menu popup: dismiss-then-act. The single exception is the
    // top bar's two frame-edit toggles (`frameEditOptions`), which are
    // selection-scoped gesture modifiers — the popup remains contextual to the
    // same selection. See `docs/architecture/context-menu.md § 3 — Dismissal rules`.
    val dismissPopupAnd: (() -> Unit) -> () -> Unit = { action ->
        {
            contextMenuRequest = null
            action()
        }
    }

    // Mirror the popup's anchor into MVI state so `SelectionOverlay` can draw
    // an outer halo around the anchor node. When the popup closes (or the user
    // picks a different anchor in the inline picker), the halo follows.
    LaunchedEffect(contextMenuRequest?.anchorNodeId) {
        canvasViewModel.onAction(CanvasAction.SetContextAnchor(contextMenuRequest?.anchorNodeId))
    }

    val editorState = canvasState.editor

    // Back-press dismissal for the context menu.
    //
    // The popup itself uses `focusable = false` so a long-press elsewhere can
    // replace it in a single gesture (a focusable popup would steal the touch
    // and require an outside-tap intermediate). The trade-off is that the
    // popup window doesn't receive key events, so `Popup.dismissOnBackPress`
    // cannot fire. We restore back-to-dismiss by intercepting back at the
    // scaffold level only while the menu is open; otherwise back falls
    // through to normal NavController behavior.
    BackHandler(enabled = contextMenuRequest != null) {
        contextMenuRequest = null
    }

    val selectedNodeIds = editorState.selectedNodeIds
    // Single-Frame selection enables the Background button in the action bar.
    val singleSelectedFrame: CanvasNode.Frame? = remember(selectedNodeIds, canvasState.visibleNodes) {
        if (selectedNodeIds.size != 1) null
        else canvasState.visibleNodes
            .map { it.node }
            .firstOrNull { it.id == selectedNodeIds.first() } as? CanvasNode.Frame
    }
    // Single-Media selection enables the Appearance button in the action bar.
    val singleSelectedMedia: CanvasNode.Media? = remember(selectedNodeIds, canvasState.visibleNodes) {
        if (selectedNodeIds.size != 1) null
        else canvasState.visibleNodes
            .map { it.node }
            .firstOrNull { it.id == selectedNodeIds.first() } as? CanvasNode.Media
    }
    // Frames in selection, in selection (insertion) order. Used by Pin/Detach and the
    // target-picker dialog. `selectedNodeIds.toList()` preserves order because
    // `Set<String>.plus` returns a LinkedHashSet (stdlib).
    val selectedFramesInOrder: List<CanvasNode.Frame> = remember(
        selectedNodeIds, canvasState.visibleNodes,
    ) {
        val byId = canvasState.visibleNodes.associate { it.node.id to it.node }
        selectedNodeIds.toList().mapNotNull { byId[it] as? CanvasNode.Frame }
    }
    // Media in selection, in selection (insertion) order. Feeds the appearance
    // editor's multi-node target list and `SelectionContext.isAllMedia` for
    // future § 14.3 gating.
    val selectedMediaInOrder: List<CanvasNode.Media> = remember(
        selectedNodeIds, canvasState.visibleNodes,
    ) {
        val byId = canvasState.visibleNodes.associate { it.node.id to it.node }
        selectedNodeIds.toList().mapNotNull { byId[it] as? CanvasNode.Media }
    }
    // Pin / Detach are available when the selection contains at least one frame and
    // at least one other node (member candidate). The target frame is either the
    // single selected frame (direct dispatch) or chosen via FrameTargetPickerDialog.
    val pinDetachEnabled: Boolean =
        selectedFramesInOrder.isNotEmpty() && selectedNodeIds.size > selectedFramesInOrder.size ||
            selectedFramesInOrder.size >= 2  // ≥2 frames + 0 others → pin one frame into another

    // "Auto" is offered only when at least one selected frame has an override entry
    // for at least one of the candidate nodes — otherwise there's nothing to clear.
    val anyOverrideExists: Boolean = remember(selectedNodeIds, selectedFramesInOrder) {
        selectedFramesInOrder.any { frame ->
            (selectedNodeIds - frame.id).any { it in frame.overrides }
        }
    }

    // Pending pin/detach intent — when non-null, the FrameTargetPickerDialog is shown.
    var pendingFrameMembershipIntent by remember {
        mutableStateOf<FrameMembershipIntent?>(null)
    }

    // Dispatch helper: directly fire Pin/Detach when only one frame is in the selection,
    // otherwise stash the intent and open the target-picker dialog.
    fun dispatchFrameMembership(intent: FrameMembershipIntent) {
        when (selectedFramesInOrder.size) {
            0 -> Unit
            1 -> {
                val target = selectedFramesInOrder.first()
                val candidates = selectedNodeIds - target.id
                if (candidates.isEmpty()) return
                canvasViewModel.onAction(intent.toAction(target.id, candidates))
            }
            else -> pendingFrameMembershipIntent = intent
        }
    }

    // Snapshot of the editor state consumed by `EditorActionCatalog` for
    // visibility / dispatch in the long-press popup. See
    // `docs/architecture/editor-actions.md` for the catalog model.
    val selectionContext = SelectionContext(
        selectedNodeIds = selectedNodeIds,
        anchorNodeId = editorState.contextAnchorNodeId,
        singleSelectedFrame = singleSelectedFrame,
        singleSelectedMedia = singleSelectedMedia,
        selectedFramesInOrder = selectedFramesInOrder,
        selectedMediaInOrder = selectedMediaInOrder,
        pinDetachEnabled = pinDetachEnabled,
        anyOverrideExists = anyOverrideExists,
    )

    // Helper: turn the current selection into an [AppearanceTarget] for a
    // universal-concept editor. Returns `None` if the selection is not
    // homogeneous (universal-concept visibility predicates would have hidden
    // the action; this is just defence-in-depth at dispatch time).
    fun universalTargetForSelection(): AppearanceTarget = when {
        selectionContext.isAllFrames -> AppearanceTarget.Frames(selectedFramesInOrder)
        selectionContext.isAllMedia -> AppearanceTarget.Media(selectedMediaInOrder)
        else -> AppearanceTarget.None
    }

    // Central effect dispatcher: every `EditorAction` tap (bar or popup) routes
    // here. New effect kinds become a single `when` branch addition.
    fun runEditorActionEffect(effect: EditorActionEffect) {
        when (effect) {
            is EditorActionEffect.Dispatch -> canvasViewModel.onAction(effect.action)
            is EditorActionEffect.FrameMembership -> dispatchFrameMembership(effect.intent)
            EditorActionEffect.OpenAddSheet -> showAddSheet = true

            // Per-concept editors (`appearance.md § 14.1`).
            EditorActionEffect.OpenOpacityEditor -> opacityEditing = universalTargetForSelection()
            EditorActionEffect.OpenCornerRadiusEditor -> cornerRadiusEditing = universalTargetForSelection()
            EditorActionEffect.OpenBorderEditor -> borderEditing = universalTargetForSelection()
            EditorActionEffect.OpenShadowEditor -> shadowEditing = universalTargetForSelection()
            EditorActionEffect.OpenOverlaysEditor -> overlaysEditing = universalTargetForSelection()
            EditorActionEffect.OpenBackgroundEditor -> {
                if (selectionContext.isAllFrames) backgroundEditing = selectedFramesInOrder
            }
            EditorActionEffect.OpenCropEditor -> {
                if (selectionContext.isAllMedia) cropEditing = selectedMediaInOrder
            }
            EditorActionEffect.OpenColorAdjustmentsEditor -> {
                if (selectionContext.isAllMedia) colorAdjustmentsEditing = selectedMediaInOrder
            }
            EditorActionEffect.OpenFrameDecorationEditor -> {
                if (selectionContext.isAllMedia) frameDecorationEditing = selectedMediaInOrder
            }
            EditorActionEffect.OpenCaptionEditor -> {
                if (selectionContext.isAllMedia) captionEditing = selectedMediaInOrder
            }
        }
    }

    // Per-concept dispatch helpers. Each merges a per-id concept value into
    // the existing per-node appearance and fires the typed multi-id action.
    // The VM's existing collapse-defaults-to-null rule applies post-dispatch.
    fun <T> dispatchFrameConcept(
        nodes: List<CanvasNode.Frame>,
        perId: Map<String, T>,
        merge: (existing: FrameAppearance?, value: T) -> FrameAppearance,
    ) {
        val byId = nodes.associateBy { it.id }
        val appearancesById: Map<String, FrameAppearance?> = perId.mapValues { (id, value) ->
            merge(byId[id]?.appearance, value)
        }
        canvasViewModel.onAction(CanvasAction.SetFrameAppearance(appearancesById = appearancesById))
    }

    fun <T> dispatchMediaConcept(
        nodes: List<CanvasNode.Media>,
        perId: Map<String, T>,
        merge: (existing: MediaAppearance?, value: T) -> MediaAppearance,
    ) {
        val byId = nodes.associateBy { it.id }
        val appearancesById: Map<String, MediaAppearance?> = perId.mapValues { (id, value) ->
            merge(byId[id]?.appearance, value)
        }
        canvasViewModel.onAction(CanvasAction.SetMediaAppearance(appearancesById = appearancesById))
    }

    Scaffold(
        topBar = {
            val lodCounts = canvasState.visibleNodes.groupingBy { it.detail }.eachCount()
            // `topBar` is a Column so the `ToolControlBar` slots immediately
            // below the global chrome — the baseline layout in
            // `editor-surfaces.md § 4` (horizontal bar below GlobalChromeSurface).
            // Visibility is Edit-only so View / Presentation reclaim the
            // pixels.
            Column {
                CanvasTopBar(
                albumName = albumName,
                visibleNodeCount = canvasState.visibleNodes.size,
                totalNodeCount = canvasState.totalNodeCount,
                camera = canvasState.camera,
                lodFullCount = lodCounts[RenderDetail.Full] ?: 0,
                lodStubCount = lodCounts[RenderDetail.Stub] ?: 0,
                lodSimplifiedCount = lodCounts[RenderDetail.Simplified] ?: 0,
                onUndo = if (canUndo) {
                    dismissPopupAnd {
                        canvasViewModel.onAction(
                            CanvasAction.Undo,
                        )
                    }
                } else null,
                onRedo = if (canRedo) {
                    dismissPopupAnd {
                        canvasViewModel.onAction(
                            CanvasAction.Redo,
                        )
                    }
                } else null,
                onNavigateBack = dismissPopupAnd { onNavigateBack() },
                onOpenFrameList = dismissPopupAnd { showFrameList = true },
                onOpenPanelConfig = dismissPopupAnd { showPanelConfig = true },
                onOpenAlbumSettings = dismissPopupAnd { showAlbumSettings = true },
                mode = editorState.mode,
                onToggleMode = dismissPopupAnd {
                    val next = if (editorState.mode == CanvasInteractionMode.Edit) {
                        CanvasInteractionMode.View
                    } else {
                        CanvasInteractionMode.Edit
                    }
                    canvasViewModel.onAction(
                        CanvasAction.SetMode(next),
                    )
                },
                // Frame-gesture modifiers — visible only while a frame is in
                // the selection; toggling them does **not** dismiss the popup
                // (per `context-menu.md § 3 — Dismissal rules`).
                frameEditOptions = editorState.frameEditOptions
                    .takeIf { selectedFramesInOrder.isNotEmpty() },
                onFrameEditOptionsChange = {
                    canvasViewModel.onAction(CanvasAction.SetFrameEditOptions(it))
                },
                )
                if (editorState.mode == CanvasInteractionMode.Edit) {
                    ToolControlBar(
                        activeTool = editorState.activeTool,
                        onToolSelected = { tool ->
                            canvasViewModel.onAction(CanvasAction.SetActiveTool(tool))
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = dismissPopupAnd { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CanvasScreen(
                onShowContextMenu = { request -> contextMenuRequest = request },
                // Tap / double-tap / drag-start dismisses an open context menu
                // *without* running its normal canvas action — outside-tap of
                // the popup is "close only", selection untouched. Long-press is
                // handled separately: it produces a new request which replaces
                // the popup in place (zero-flicker swap).
                onCanvasGesture = { contextMenuRequest = null },
                isContextMenuOpen = contextMenuRequest != null,
            )
            IdeOverlayScreen()

            // Debug panel — shows info about selected nodes
            if (selectedNodeIds.isNotEmpty()) {
                val selectedNodes = canvasState.visibleNodes
                    .filter { it.node.id in selectedNodeIds }
                    .map { it.node }
                SelectionDebugPanel(
                    selectedNodes = selectedNodes,
                    camera = canvasState.camera,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }

        }
    }

    // Bottom sheets
    if (showAddSheet) {
        AddContentBottomSheet(
            onDismiss = { showAddSheet = false },
            onContentTypeSelected = { type ->
                val viewport = canvasViewModel.currentViewport()
                val camera = canvasViewModel.currentCamera()
                val zIndex = canvasViewModel.nextZIndex()
                val node = when (type) {
                    "Frame" -> {
                        val (sw, sh) = canvasViewModel.screenSize()
                        CanvasNodeFactory.createFrame(
                            screenWidth = sw,
                            screenHeight = sh,
                            viewport = viewport,
                            nextZIndex = zIndex,
                            camera = camera,
                            profile = canvasState.profile,
                        )
                    }
                    "Photo" -> {
                        showAddSheet = false
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                        null
                    }
                    else -> null
                }
                node?.let { canvasViewModel.addNode(it) }
                showAddSheet = false
            },
        )
    }
    if (showFrameList) {
        val visibleFrameIds = canvasState.visibleNodes
            .map { it.node }
            .filterIsInstance<CanvasNode.Frame>()
            .map { it.id }
            .toHashSet()
        FrameListBottomSheet(
            frames = frames,
            onDeleteFrame = { canvasViewModel.removeNode(it) },
            onDismiss = { showFrameList = false },
            visibleFrameIds = visibleFrameIds,
            onFocusFrame = { frameId ->
                canvasViewModel.onAction(
                    CanvasAction.FocusNode(frameId),
                )
                showFrameList = false
            },
        )
    }

    if (showAlbumSettings) {
        AlbumSettingsBottomSheet(
            initial = canvasState.albumBackground,
            onApply = { bg ->
                canvasViewModel.onAction(
                    CanvasAction.SetAlbumBackground(bg),
                )
                showAlbumSettings = false
            },
            onDismiss = { showAlbumSettings = false },
        )
    }

    // ── Per-concept appearance editors (appearance.md § 14.1) ────────────────
    // Universal concepts (opacity, cornerRadius, border, shadow, overlays)
    // branch on AppearanceTarget; the popup body is identical between frame and
    // media variants — only the dispatch helper differs. Type-specific concepts
    // (background = frame-only; crop / color adjustments / frame decoration /
    // caption = media-only) use their typed editing list directly.

    when (val t = opacityEditing) {
        is AppearanceTarget.Frames -> ConceptEditorSheet(
            title = "Opacity",
            initialById = t.nodes.associate { it.id to (it.appearance?.opacity ?: 1f) },
            onApply = { perId ->
                dispatchFrameConcept(t.nodes, perId) { existing, v ->
                    (existing ?: FrameAppearance()).copy(opacity = v)
                }
                opacityEditing = AppearanceTarget.None
            },
            onDismiss = { opacityEditing = AppearanceTarget.None },
        ) { mixed, _, onChange -> MixedAwareOpacitySlider(mixed, onChange) }
        is AppearanceTarget.Media -> ConceptEditorSheet(
            title = "Opacity",
            initialById = t.nodes.associate { it.id to (it.appearance?.opacity ?: 1f) },
            onApply = { perId ->
                dispatchMediaConcept(t.nodes, perId) { existing, v ->
                    (existing ?: MediaAppearance()).copy(opacity = v)
                }
                opacityEditing = AppearanceTarget.None
            },
            onDismiss = { opacityEditing = AppearanceTarget.None },
        ) { mixed, _, onChange -> MixedAwareOpacitySlider(mixed, onChange) }
        AppearanceTarget.None -> Unit
    }

    when (val t = cornerRadiusEditing) {
        is AppearanceTarget.Frames -> ConceptEditorSheet(
            title = "Corner radius",
            initialById = t.nodes.associate { it.id to (it.appearance?.cornerRadius ?: 0f) },
            onApply = { perId ->
                dispatchFrameConcept(t.nodes, perId) { existing, v ->
                    (existing ?: FrameAppearance()).copy(cornerRadius = v)
                }
                cornerRadiusEditing = AppearanceTarget.None
            },
            onDismiss = { cornerRadiusEditing = AppearanceTarget.None },
        ) { mixed, _, onChange -> MixedAwareCornerRadiusSlider(mixed, onChange) }
        is AppearanceTarget.Media -> ConceptEditorSheet(
            title = "Corner radius",
            initialById = t.nodes.associate { it.id to (it.appearance?.cornerRadius ?: 0f) },
            onApply = { perId ->
                dispatchMediaConcept(t.nodes, perId) { existing, v ->
                    (existing ?: MediaAppearance()).copy(cornerRadius = v)
                }
                cornerRadiusEditing = AppearanceTarget.None
            },
            onDismiss = { cornerRadiusEditing = AppearanceTarget.None },
        ) { mixed, _, onChange -> MixedAwareCornerRadiusSlider(mixed, onChange) }
        AppearanceTarget.None -> Unit
    }

    when (val t = borderEditing) {
        is AppearanceTarget.Frames -> ConceptEditorSheet<BorderStyle?>(
            title = "Border",
            initialById = t.nodes.associate { it.id to it.appearance?.border },
            onApply = { perId ->
                dispatchFrameConcept(t.nodes, perId) { existing, v ->
                    (existing ?: FrameAppearance()).copy(border = v)
                }
                borderEditing = AppearanceTarget.None
            },
            onDismiss = { borderEditing = AppearanceTarget.None },
        ) { _, firstDraft, onChange ->
            BorderStyleEditor(initial = firstDraft, onChange = onChange)
        }
        is AppearanceTarget.Media -> ConceptEditorSheet<BorderStyle?>(
            title = "Border",
            initialById = t.nodes.associate { it.id to it.appearance?.border },
            onApply = { perId ->
                dispatchMediaConcept(t.nodes, perId) { existing, v ->
                    (existing ?: MediaAppearance()).copy(border = v)
                }
                borderEditing = AppearanceTarget.None
            },
            onDismiss = { borderEditing = AppearanceTarget.None },
        ) { _, firstDraft, onChange ->
            BorderStyleEditor(initial = firstDraft, onChange = onChange)
        }
        AppearanceTarget.None -> Unit
    }

    when (val t = shadowEditing) {
        is AppearanceTarget.Frames -> ConceptEditorSheet<ShadowStyle?>(
            title = "Shadow",
            initialById = t.nodes.associate { it.id to it.appearance?.shadow },
            onApply = { perId ->
                dispatchFrameConcept(t.nodes, perId) { existing, v ->
                    (existing ?: FrameAppearance()).copy(shadow = v)
                }
                shadowEditing = AppearanceTarget.None
            },
            onDismiss = { shadowEditing = AppearanceTarget.None },
        ) { _, firstDraft, onChange ->
            ShadowStyleEditor(initial = firstDraft, onChange = onChange)
        }
        is AppearanceTarget.Media -> ConceptEditorSheet<ShadowStyle?>(
            title = "Shadow",
            initialById = t.nodes.associate { it.id to it.appearance?.shadow },
            onApply = { perId ->
                dispatchMediaConcept(t.nodes, perId) { existing, v ->
                    (existing ?: MediaAppearance()).copy(shadow = v)
                }
                shadowEditing = AppearanceTarget.None
            },
            onDismiss = { shadowEditing = AppearanceTarget.None },
        ) { _, firstDraft, onChange ->
            ShadowStyleEditor(initial = firstDraft, onChange = onChange)
        }
        AppearanceTarget.None -> Unit
    }

    when (val t = overlaysEditing) {
        is AppearanceTarget.Frames -> ConceptEditorSheet<List<OverlayStyle>>(
            title = "Overlays",
            initialById = t.nodes.associate { it.id to (it.appearance?.overlays ?: emptyList()) },
            onApply = { perId ->
                dispatchFrameConcept(t.nodes, perId) { existing, v ->
                    (existing ?: FrameAppearance()).copy(overlays = v)
                }
                overlaysEditing = AppearanceTarget.None
            },
            onDismiss = { overlaysEditing = AppearanceTarget.None },
        ) { _, firstDraft, onChange ->
            OverlayListEditor(
                overlays = firstDraft,
                onChange = onChange,
                tileSizeUnitLabel = "world units",
            )
        }
        is AppearanceTarget.Media -> ConceptEditorSheet<List<OverlayStyle>>(
            title = "Overlays",
            initialById = t.nodes.associate { it.id to (it.appearance?.overlays ?: emptyList()) },
            onApply = { perId ->
                dispatchMediaConcept(t.nodes, perId) { existing, v ->
                    (existing ?: MediaAppearance()).copy(overlays = v)
                }
                overlaysEditing = AppearanceTarget.None
            },
            onDismiss = { overlaysEditing = AppearanceTarget.None },
        ) { _, firstDraft, onChange ->
            OverlayListEditor(
                overlays = firstDraft,
                onChange = onChange,
                tileSizeUnitLabel = "world units",
            )
        }
        AppearanceTarget.None -> Unit
    }

    if (backgroundEditing.isNotEmpty()) {
        val nodes = backgroundEditing
        ConceptEditorSheet<BackgroundData?>(
            title = "Background",
            initialById = nodes.associate { it.id to it.appearance?.background },
            onApply = { perId ->
                dispatchFrameConcept(nodes, perId) { existing, v ->
                    (existing ?: FrameAppearance()).copy(background = v)
                }
                backgroundEditing = emptyList()
            },
            onDismiss = { backgroundEditing = emptyList() },
        ) { _, firstDraft, onChange ->
            BackgroundEditor(
                initial = firstDraft,
                onValueChange = onChange,
                tileSizeUnitLabel = "world units",
            )
        }
    }

    if (cropEditing.isNotEmpty()) {
        val nodes = cropEditing
        ConceptEditorSheet<CropSettings>(
            title = "Crop",
            initialById = nodes.associate { it.id to (it.appearance?.crop ?: CropSettings()) },
            onApply = { perId ->
                dispatchMediaConcept(nodes, perId) { existing, v ->
                    (existing ?: MediaAppearance()).copy(crop = v)
                }
                cropEditing = emptyList()
            },
            onDismiss = { cropEditing = emptyList() },
        ) { _, firstDraft, onChange ->
            CropEditor(initial = firstDraft, onChange = onChange)
        }
    }

    if (colorAdjustmentsEditing.isNotEmpty()) {
        val nodes = colorAdjustmentsEditing
        ConceptEditorSheet<MediaColorAdjustments?>(
            title = "Color adjustments",
            initialById = nodes.associate { it.id to it.appearance?.colorAdjustments },
            onApply = { perId ->
                dispatchMediaConcept(nodes, perId) { existing, v ->
                    (existing ?: MediaAppearance()).copy(colorAdjustments = v)
                }
                colorAdjustmentsEditing = emptyList()
            },
            onDismiss = { colorAdjustmentsEditing = emptyList() },
        ) { _, firstDraft, onChange ->
            ColorAdjustmentsEditor(initial = firstDraft, onChange = onChange)
        }
    }

    if (frameDecorationEditing.isNotEmpty()) {
        val nodes = frameDecorationEditing
        ConceptEditorSheet<MediaFrameDecoration?>(
            title = "Frame decoration",
            initialById = nodes.associate { it.id to it.appearance?.frameDecoration },
            onApply = { perId ->
                dispatchMediaConcept(nodes, perId) { existing, v ->
                    (existing ?: MediaAppearance()).copy(frameDecoration = v)
                }
                frameDecorationEditing = emptyList()
            },
            onDismiss = { frameDecorationEditing = emptyList() },
        ) { _, firstDraft, onChange ->
            MediaFrameDecorationEditor(initial = firstDraft, onChange = onChange)
        }
    }

    if (captionEditing.isNotEmpty()) {
        val nodes = captionEditing
        ConceptEditorSheet<CaptionStyle?>(
            title = "Caption",
            initialById = nodes.associate { it.id to it.appearance?.caption },
            onApply = { perId ->
                dispatchMediaConcept(nodes, perId) { existing, v ->
                    (existing ?: MediaAppearance()).copy(caption = v)
                }
                captionEditing = emptyList()
            },
            onDismiss = { captionEditing = emptyList() },
        ) { _, firstDraft, onChange ->
            CaptionStyleEditor(initial = firstDraft, onChange = onChange)
        }
    }

    // Panel config dialog
    if (showPanelConfig) {
        PanelConfigDialog(
            state = ideState,
            onAction = ideViewModel::onAction,
            onDismiss = { showPanelConfig = false },
        )
    }

    // Context menu — opens on long-press lift (Edit mode only). See
    // `docs/architecture/context-menu.md`. Anchor-scoped items use the
    // post-resolution selection + the long-pressed node id. When the
    // long-press hit a stack of overlapping nodes, the popup also renders
    // a checkbox row per stacked node above the menu items so the user
    // can adjust the selection without leaving the popover.
    contextMenuRequest?.let { storedRequest ->
        // The request stored at long-press time carries a *snapshot* of the
        // selection. Refresh it from live MVI state on every recomposition so
        // picker checkboxes reflect the actual current selection (toggling a
        // checkbox dispatches `ToggleNodeSelection`, which mutates state but
        // not the stored snapshot) and so the menu structure (single vs group)
        // stays in sync if the user changes selection via the picker.
        val request = storedRequest.copy(selection = editorState.selectedNodeIds)
        val items = buildEditContextMenuItems(
            request = request,
            // `selectionContext` is rebuilt every recomposition from
            // `editorState.selectedNodeIds`, so picker toggles that mutate the
            // selection are reflected here without any extra plumbing.
            ctx = selectionContext,
            runEffect = ::runEditorActionEffect,
            // `Remove this from selection` keeps the popup open; per Option A
            // (see `docs/architecture/context-menu.md § 4.4`), removing the
            // anchor clears it — anchor-scoped items disappear until the user
            // picks a new anchor via the inline picker or a fresh long-press.
            onAnchorRemoved = {
                contextMenuRequest = contextMenuRequest?.copy(anchorNodeId = null)
            },
        )
        ContextMenuPopup(
            request = request,
            items = items,
            onDismiss = { contextMenuRequest = null },
            onTogglePickerNode = { node ->
                // Toggle add/remove the picker node. Anchor follows Option A
                // (see `context-menu.md § 4.4`): unchecking the current anchor
                // clears it; toggling a non-anchor row makes it the new anchor.
                // Selection mutation goes through MVI; the popup re-reads it
                // via the `request.copy(selection = …)` rebind on the next
                // recomposition.
                canvasViewModel.onAction(CanvasAction.ToggleNodeSelection(node.id))
                val current = contextMenuRequest ?: return@ContextMenuPopup
                contextMenuRequest = if (node.id == current.anchorNodeId) {
                    current.copy(anchorNodeId = null)
                } else {
                    current.copy(anchorNodeId = node.id)
                }
            },
        )
    }

    // Pin / Detach target picker — shown when ≥2 frames are in the selection.
    pendingFrameMembershipIntent?.let { intent ->
        val defaultId = remember(selectedFramesInOrder) {
            selectedFramesInOrder.maxByOrNull {
                it.transform.renderW.toDouble() * it.transform.renderH.toDouble()
            }?.id ?: selectedFramesInOrder.firstOrNull()?.id
        }
        if (defaultId == null) {
            // Race: selection changed between intent capture and recomposition.
            pendingFrameMembershipIntent = null
        } else {
            FrameTargetPickerDialog(
                title = intent.title,
                frames = selectedFramesInOrder,
                defaultFrameId = defaultId,
                onConfirm = { frameId ->
                    val candidates = selectedNodeIds - frameId
                    if (candidates.isNotEmpty()) {
                        canvasViewModel.onAction(intent.toAction(frameId, candidates))
                    }
                    pendingFrameMembershipIntent = null
                },
                onDismiss = { pendingFrameMembershipIntent = null },
            )
        }
    }
}

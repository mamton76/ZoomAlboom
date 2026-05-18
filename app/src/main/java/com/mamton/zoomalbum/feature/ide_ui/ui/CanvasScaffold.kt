package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.mamton.zoomalbum.domain.model.CanvasInteractionMode
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.CanvasNodeFactory
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.feature.canvas.view.CanvasScreen
import com.mamton.zoomalbum.feature.canvas.view.SelectionDebugPanel
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasViewModel
import com.mamton.zoomalbum.feature.ide_ui.ui.sheets.AddContentBottomSheet
import com.mamton.zoomalbum.feature.ide_ui.ui.sheets.AlbumSettingsBottomSheet
import com.mamton.zoomalbum.feature.ide_ui.ui.sheets.FrameAppearanceBottomSheet
import com.mamton.zoomalbum.feature.ide_ui.ui.sheets.MediaAppearanceBottomSheet
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
    var frameBgEditing by remember { mutableStateOf<CanvasNode.Frame?>(null) }
    var mediaApprEditing by remember { mutableStateOf<CanvasNode.Media?>(null) }
    var overlapPickerNodes by remember { mutableStateOf<List<CanvasNode>>(emptyList()) }

    val selectedNodeIds = canvasState.selectedNodeIds
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

    Scaffold(
        topBar = {
            val lodCounts = canvasState.visibleNodes.groupingBy { it.detail }.eachCount()
            CanvasTopBar(
                albumName = albumName,
                visibleNodeCount = canvasState.visibleNodes.size,
                totalNodeCount = canvasState.totalNodeCount,
                camera = canvasState.camera,
                lodFullCount = lodCounts[RenderDetail.Full] ?: 0,
                lodStubCount = lodCounts[RenderDetail.Stub] ?: 0,
                lodSimplifiedCount = lodCounts[RenderDetail.Simplified] ?: 0,
                onUndo = if (canUndo) {
                    {
                        canvasViewModel.onAction(
                            com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.Undo,
                        )
                    }
                } else null,
                onRedo = if (canRedo) {
                    {
                        canvasViewModel.onAction(
                            com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.Redo,
                        )
                    }
                } else null,
                onNavigateBack = onNavigateBack,
                onOpenFrameList = { showFrameList = true },
                onOpenPanelConfig = { showPanelConfig = true },
                onOpenAlbumSettings = { showAlbumSettings = true },
                mode = canvasState.mode,
                onToggleMode = {
                    val next = if (canvasState.mode == CanvasInteractionMode.Edit) {
                        CanvasInteractionMode.View
                    } else {
                        CanvasInteractionMode.Edit
                    }
                    canvasViewModel.onAction(
                        com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.SetMode(next),
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
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
                onShowOverlapPicker = { nodes -> overlapPickerNodes = nodes },
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

            FrameEditOptionsBar(
                // Visible whenever the selection contains at least one frame — the
                // toggles apply to every selected frame's gesture (move / resize / rotate).
                visible = selectedFramesInOrder.isNotEmpty(),
                options = canvasState.frameEditOptions,
                onOptionsChange = {
                    canvasViewModel.onAction(
                        CanvasAction.SetFrameEditOptions(it),
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp),
            )

            ContextualActionBar(
                hasSelection = selectedNodeIds.isNotEmpty(),
                showBackgroundAction = singleSelectedFrame != null,
                showMediaAppearanceAction = singleSelectedMedia != null,
                showZOrderActions = selectedNodeIds.size == 1,
                showFrameMembershipActions = pinDetachEnabled,
                showAutoAction = pinDetachEnabled && anyOverrideExists,
                modifier = Modifier.align(Alignment.BottomCenter),
                onAction = { label ->
                    when (label) {
                        "Delete" -> canvasViewModel.onAction(
                            com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.DeleteSelection,
                        )
                        "Duplicate" -> canvasViewModel.onAction(
                            com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.DuplicateSelection,
                        )
                        "Background" -> singleSelectedFrame?.let { frameBgEditing = it }
                        "Appearance" -> singleSelectedMedia?.let { mediaApprEditing = it }
                        "Pin" -> dispatchFrameMembership(FrameMembershipIntent.Pin)
                        "Detach" -> dispatchFrameMembership(FrameMembershipIntent.Detach)
                        "Auto" -> dispatchFrameMembership(FrameMembershipIntent.Reset)
                        "ToFront" -> selectedNodeIds.firstOrNull()?.let {
                            canvasViewModel.onAction(
                                com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.BringToFront(it),
                            )
                        }
                        "Forward" -> selectedNodeIds.firstOrNull()?.let {
                            canvasViewModel.onAction(
                                com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.BringForward(it),
                            )
                        }
                        "Backward" -> selectedNodeIds.firstOrNull()?.let {
                            canvasViewModel.onAction(
                                com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.SendBackward(it),
                            )
                        }
                        "ToBack" -> selectedNodeIds.firstOrNull()?.let {
                            canvasViewModel.onAction(
                                com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.SendToBack(it),
                            )
                        }
                    }
                },
            )
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
                    com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.FocusNode(frameId),
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
                    com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.SetAlbumBackground(bg),
                )
                showAlbumSettings = false
            },
            onDismiss = { showAlbumSettings = false },
        )
    }

    frameBgEditing?.let { frame ->
        FrameAppearanceBottomSheet(
            initial = frame.appearance,
            onApply = { nextAppearance ->
                canvasViewModel.onAction(
                    com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.SetFrameAppearance(
                        nodeId = frame.id,
                        appearance = nextAppearance,
                    ),
                )
                frameBgEditing = null
            },
            onDismiss = { frameBgEditing = null },
        )
    }

    mediaApprEditing?.let { media ->
        MediaAppearanceBottomSheet(
            initial = media.appearance,
            onApply = { nextAppearance ->
                canvasViewModel.onAction(
                    com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.SetMediaAppearance(
                        nodeId = media.id,
                        appearance = nextAppearance,
                    ),
                )
                mediaApprEditing = null
            },
            onDismiss = { mediaApprEditing = null },
        )
    }

    // Panel config dialog
    if (showPanelConfig) {
        PanelConfigDialog(
            state = ideState,
            onAction = ideViewModel::onAction,
            onDismiss = { showPanelConfig = false },
        )
    }

    // Overlap picker dialog — multi-select, additive (matches long-press semantics).
    if (overlapPickerNodes.isNotEmpty()) {
        OverlapPickerDialog(
            nodes = overlapPickerNodes,
            onConfirm = { ids ->
                canvasViewModel.onAction(
                    com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.AddNodesToSelection(ids),
                )
                overlapPickerNodes = emptyList()
            },
            onDismiss = { overlapPickerNodes = emptyList() },
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

package com.mamton.zoomalbum.feature.ide_ui.ui

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
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.CanvasNodeFactory
import com.mamton.zoomalbum.domain.model.RenderDetail
import com.mamton.zoomalbum.feature.canvas.view.CanvasScreen
import com.mamton.zoomalbum.feature.canvas.view.SelectionDebugPanel
import com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasViewModel
import com.mamton.zoomalbum.feature.ide_ui.ui.sheets.AddContentBottomSheet
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

    var showAddSheet by remember { mutableStateOf(false) }
    var showFrameList by remember { mutableStateOf(false) }
    var showPanelConfig by remember { mutableStateOf(false) }
    var overlapPickerNodes by remember { mutableStateOf<List<CanvasNode>>(emptyList()) }

    val selectedNodeIds = canvasState.selectedNodeIds

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
                onNavigateBack = onNavigateBack,
                onOpenFrameList = { showFrameList = true },
                onOpenPanelConfig = { showPanelConfig = true },
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
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
            }

            ContextualActionBar(
                hasSelection = selectedNodeIds.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter),
                onAction = { label ->
                    when (label) {
                        "Delete" -> canvasViewModel.onAction(
                            com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.DeleteSelection,
                        )
                        "Duplicate" -> canvasViewModel.onAction(
                            com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.DuplicateSelection,
                        )
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
                        )
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

    // Overlap picker dialog
    if (overlapPickerNodes.isNotEmpty()) {
        OverlapPickerDialog(
            nodes = overlapPickerNodes,
            onSelectNode = { nodeId ->
                canvasViewModel.onAction(
                    com.mamton.zoomalbum.feature.canvas.viewmodel.CanvasAction.SelectNode(nodeId),
                )
                overlapPickerNodes = emptyList()
            },
            onDismiss = { overlapPickerNodes = emptyList() },
        )
    }
}

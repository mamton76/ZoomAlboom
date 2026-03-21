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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.CanvasNodeFactory
import com.mamton.zoomalbum.feature.canvas.view.CanvasScreen
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

    // Stub: no node selection yet
    val selectedNodeId: String? = null

    Scaffold(
        topBar = {
            CanvasTopBar(
                albumName = albumName,
                visibleNodeCount = canvasState.visibleNodes.size,
                totalNodeCount = canvasState.totalNodeCount,
                camera = canvasState.camera,
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
            CanvasScreen()
            IdeOverlayScreen()

            ContextualActionBar(
                selectedNodeId = selectedNodeId,
                modifier = Modifier.align(Alignment.BottomCenter),
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
}

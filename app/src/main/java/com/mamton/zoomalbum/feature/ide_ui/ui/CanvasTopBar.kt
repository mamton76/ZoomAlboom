package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mamton.zoomalbum.core.designsystem.PanelBackground
import com.mamton.zoomalbum.core.designsystem.TextPrimary
import com.mamton.zoomalbum.core.designsystem.TextSecondary
import com.mamton.zoomalbum.core.math.Camera

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasTopBar(
    albumName: String,
    visibleNodeCount: Int = 0,
    totalNodeCount: Int = 0,
    camera: Camera = Camera(),
    lodFullCount: Int = 0,
    lodStubCount: Int = 0,
    lodSimplifiedCount: Int = 0,
    onNavigateBack: () -> Unit,
    onOpenFrameList: () -> Unit,
    onOpenPanelConfig: () -> Unit,
) {
    TopAppBar(
        title = {
            Row {
                Text(
                    text = albumName,
                    modifier = Modifier.padding(horizontal = 5.dp))
                Text(
                    text = "visible: $visibleNodeCount / $totalNodeCount",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 5.dp),
                )
                Text(
                    text = "zoom: ${"%.2f".format(camera.scale)}x" +
                        "  rot: ${"%.1f".format(camera.rotation)}\u00B0" +
                        "  xy: ${"%.0f".format(camera.cx)}, ${"%.0f".format(camera.cy)}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 5.dp),
                )
                Text(
                    text = "LOD: ${lodFullCount}F ${lodStubCount}S ${lodSimplifiedCount}X",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 5.dp),
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) { Text("\u2190") }
        },
        actions = {
            IconButton(onClick = onOpenFrameList) { Text("\u2630") } // ☰ hamburger
            IconButton(onClick = onOpenPanelConfig) { Text("\u2699") } // ⚙ gear
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PanelBackground.copy(alpha = 0.85f),
            titleContentColor = TextPrimary,
            navigationIconContentColor = TextPrimary,
            actionIconContentColor = TextPrimary,
        ),
    )
}

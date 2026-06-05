package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.mamton.zoomalbum.domain.model.CanvasInteractionMode
import com.mamton.zoomalbum.domain.model.FrameEditOptions

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
    onUndo: (() -> Unit)? = null,
    onRedo: (() -> Unit)? = null,
    onNavigateBack: () -> Unit,
    onOpenFrameList: () -> Unit,
    onOpenPanelConfig: () -> Unit,
    onOpenAlbumSettings: () -> Unit = {},
    mode: CanvasInteractionMode = CanvasInteractionMode.Edit,
    onToggleMode: () -> Unit = {},
    /**
     * Per-session toggles that shape how frame-transform gestures behave (see
     * [FrameEditOptions]). `null` hides the two toggles entirely — typically
     * because the current selection contains no frame.
     */
    frameEditOptions: FrameEditOptions? = null,
    onFrameEditOptionsChange: (FrameEditOptions) -> Unit = {},
) {
    TopAppBar(
        title = {
            Row {
                Text(
                    text = albumName,
                    modifier = Modifier.padding(horizontal = 5.dp))
                Text(
                    text = "visible: $visibleNodeCount / $totalNodeCount"
                            //+ "  LOD: ${lodFullCount}F ${lodStubCount}S ${lodSimplifiedCount}X"
                            ,
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
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) { Text("\u2190") }
        },
        actions = {
            if (frameEditOptions != null) {
                // Selection-scoped frame-gesture modifiers. Visible only while a
                // frame is selected. Same persistence semantics as before — the
                // toggles apply to subsequent frame transform gestures and are
                // **not** wrapped in `dismissPopupAnd`, so toggling them while
                // the long-press popup is open keeps the popup in place (the
                // popup is still contextual to the same selection). See
                // `docs/architecture/context-menu.md § 3 — Dismissal rules`.
                FrameEditFilterChip(
                    selected = frameEditOptions.transformContents,
                    icon = "🔗",
                    label = "Content",
                    onSelectedChange = {
                        onFrameEditOptionsChange(frameEditOptions.copy(transformContents = it))
                    },
                )
                FrameEditFilterChip(
                    selected = frameEditOptions.rebindAfterEdit,
                    icon = "🔄",
                    label = "Rebind",
                    onSelectedChange = {
                        onFrameEditOptionsChange(frameEditOptions.copy(rebindAfterEdit = it))
                    },
                )
            }
            IconButton(onClick = onUndo ?: {}, enabled = onUndo != null) { Text("↶") } // undo
            IconButton(onClick = onRedo ?: {}, enabled = onRedo != null) { Text("↷") } // redo
            IconButton(onClick = onToggleMode) {
                Text(
                    text = when (mode) {
                        CanvasInteractionMode.Edit -> "Edit"
                        CanvasInteractionMode.View -> "View"
                        CanvasInteractionMode.Presentation -> "Pres"
                    },
                    fontSize = 11.sp,
                )
            }
            IconButton(onClick = onOpenAlbumSettings) { Text("🎨") } // 🎨 palette
            IconButton(onClick = onOpenFrameList){ Text("\u2630") } // ☰ hamburger
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

/**
 * Compact filter chip used by the frame-edit toggles in the top bar. Renders
 * `[icon] label` with a clearly differentiated selected state — `FilterChip`
 * adds a tonal background + check leading-icon affordance when selected, so
 * the on/off state is visible at a glance.
 */
@Composable
private fun FrameEditFilterChip(
    selected: Boolean,
    icon: String,
    label: String,
    onSelectedChange: (Boolean) -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = { Text(icon) },
        modifier = Modifier.padding(horizontal = 2.dp),
        colors = FilterChipDefaults.filterChipColors(
            labelColor = TextSecondary,
            selectedLabelColor = TextPrimary,
        ),
    )
}

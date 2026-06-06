package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mamton.zoomalbum.feature.canvas.editor.EditorTool

/**
 * `ToolControlSurface` baseline rendering. See `editor-surfaces.md § 4`.
 *
 * Single dropdown listing every available tool — the current tool's label
 * sits on the button; tapping opens the menu, picking a row dispatches
 * [onToolSelected] and closes. The bar is the host for per-tool secondary
 * controls (brush size, mode chip, etc.) when they ship — they'll render
 * to the right of the tool selector.
 *
 * Lives in `feature.ide_ui.ui` alongside [CanvasTopBar] because it's
 * editor chrome — Scaffold-level surface that frames the canvas, not
 * an on-canvas overlay. Canvas-anchored UI (selection chrome, context-
 * menu popup) lives in `feature.canvas.view`.
 *
 * Design constraints honored:
 *
 *  - **Show only implemented tools.** `VectorEdit` / `MaskEdit` / `CropEdit`
 *    are context-gated (entered when a valid target is selected) and don't
 *    appear as permanent rows. `FreeDraw` / `Shape` / `Text` haven't shipped
 *    yet — no disabled placeholders.
 *  - **Active tool always visible.** The button always shows the active
 *    tool's label — destructive tools (Eraser today; FreeDraw / Text /
 *    Shape later) are never ambiguous.
 *  - **Context-gated tools surface their own controls in-place.** When
 *    `CropEdit` is active, the dropdown is replaced by the crop topbar
 *    (aspect-lock toggle, source-zoom slider, reset, leave) — there's no
 *    way to *enter* `CropEdit` from the dropdown (the context menu does
 *    that), but the user is in it now, so they get its controls.
 *  - **Edit-mode only.** Visibility gating happens at the call site
 *    (`CanvasScaffold`), not inside this composable, so the bar can
 *    short-circuit to nothing without taking layout space.
 *
 * The mode-axis (`Edit` / `View` / `Present`) is the caller's concern; the
 * tool axis is this composable's. Keeps the boundary clean for the eventual
 * tablet docked-panel placement (`editor-surfaces.md § 6`).
 */
@Composable
fun ToolControlBar(
    activeTool: EditorTool,
    onToolSelected: (EditorTool) -> Unit,
    cropEditState: CropEditTopbarState? = null,
    cropEditCallbacks: CropEditTopbarCallbacks? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (activeTool === EditorTool.CropEdit && cropEditState != null && cropEditCallbacks != null) {
                CropEditControls(state = cropEditState, callbacks = cropEditCallbacks)
            } else {
                ToolDropdown(activeTool = activeTool, onToolSelected = onToolSelected)
            }
        }
    }
}

/**
 * Snapshot the topbar needs to render `CropEdit`'s controls. `sourceZoom` is
 * the current `MediaAppearance.crop.zoom` on the edited media. `null` means
 * no media is currently selected — caller should not pass [CropEditTopbarState]
 * at all in that case.
 */
data class CropEditTopbarState(
    val aspectLocked: Boolean,
    val sourceZoom: Float,
    val zoomMin: Float = MIN_SOURCE_ZOOM,
    val zoomMax: Float = MAX_SOURCE_ZOOM,
)

/**
 * Topbar callback wiring for `CropEdit`. The host (`CanvasScaffold`) is
 * responsible for snapshot / undo wrap around `onSourceZoomChange` —
 * specifically `onBeginZoomDrag` on first slider value emission and
 * `onEndZoomDrag` on `onValueChangeFinished`, so the whole drag becomes
 * one Compound undo entry. Reset and aspect-lock toggles each get their
 * own one-shot undo entries.
 */
data class CropEditTopbarCallbacks(
    val onAspectLockedChange: (Boolean) -> Unit,
    val onBeginZoomDrag: () -> Unit,
    val onSourceZoomChange: (Float) -> Unit,
    val onEndZoomDrag: () -> Unit,
    val onReset: () -> Unit,
    val onLeave: () -> Unit,
    /**
     * Restores the media's transform + appearance to the snapshot captured
     * at `CropEdit` entry, then exits to `Selection`. Note: does not touch
     * undo history — the intermediate per-gesture entries from the session
     * remain available via Undo.
     */
    val onCancel: () -> Unit,
)

@Composable
private fun CropEditControls(
    state: CropEditTopbarState,
    callbacks: CropEditTopbarCallbacks,
) {
    Text("Crop", style = MaterialTheme.typography.titleSmall)
    var dragging by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Lock aspect")
        Switch(
            checked = state.aspectLocked,
            onCheckedChange = callbacks.onAspectLockedChange,
        )
    }
    Box(modifier = Modifier.width(160.dp)) {
        Slider(
            value = state.sourceZoom.coerceIn(state.zoomMin, state.zoomMax),
            onValueChange = { v ->
                if (!dragging) {
                    dragging = true
                    callbacks.onBeginZoomDrag()
                }
                callbacks.onSourceZoomChange(v)
            },
            onValueChangeFinished = {
                if (dragging) {
                    dragging = false
                    callbacks.onEndZoomDrag()
                }
            },
            valueRange = state.zoomMin..state.zoomMax,
        )
    }
    Text("Zoom ${"%.2f".format(state.sourceZoom)}", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = callbacks.onReset) { Text("Reset") }
    TextButton(onClick = callbacks.onCancel) { Text("Cancel") }
    TextButton(onClick = callbacks.onLeave) { Text("Apply") }
}

@Composable
private fun ToolDropdown(
    activeTool: EditorTool,
    onToolSelected: (EditorTool) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text("${activeTool.label} ▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (tool in availableTools) {
                val prefix = if (tool == activeTool) "● " else "  "
                DropdownMenuItem(
                    text = { Text("$prefix${tool.label}") },
                    onClick = {
                        onToolSelected(tool)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * Tools that appear as permanent rows in the selector. `VectorEdit` /
 * `MaskEdit` / `CropEdit` are context-gated and surface elsewhere; future
 * tools (`FreeDraw`, `Shape`, `Text`) get added here when they ship.
 */
private val availableTools: List<EditorTool> = listOf(
    EditorTool.Selection,
    EditorTool.Eraser,
)

private val EditorTool.label: String
    get() = when (this) {
        EditorTool.Selection -> "Select"
        EditorTool.Eraser -> "Erase"
        EditorTool.CropEdit -> "Crop"
    }

/**
 * Source-zoom slider bounds. `zoom = 1` is defined as the Fill scale; the
 * range below 1 lets the user pull the source back toward Fit / letterbox
 * framing, and the range above 1 allows up to 4× tighter crops. Matches the
 * historical sheet-slider bounds (see media-appearance.md / § 20.8.5).
 */
private const val MIN_SOURCE_ZOOM = 0.1f
private const val MAX_SOURCE_ZOOM = 4f

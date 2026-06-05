package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
 *  - **Show only implemented tools.** `VectorEdit` / `MaskEdit` are
 *    context-gated (entered when a valid target is selected) and don't
 *    appear as permanent rows. `FreeDraw` / `Shape` / `Text` haven't
 *    shipped yet — no disabled placeholders.
 *  - **Active tool always visible.** The button always shows the active
 *    tool's label — destructive tools (Eraser today; FreeDraw / Text /
 *    Shape later) are never ambiguous.
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
            ToolDropdown(activeTool = activeTool, onToolSelected = onToolSelected)
            // Future: per-tool secondary controls slot in here (e.g., Eraser
            // mode chip, brush size slider, Shape primitive picker).
        }
    }
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
 * `MaskEdit` are context-gated and surface elsewhere; future tools
 * (`FreeDraw`, `Shape`, `Text`) get added here when they ship.
 */
private val availableTools: List<EditorTool> = listOf(
    EditorTool.Selection,
    EditorTool.Eraser,
)

private val EditorTool.label: String
    get() = when (this) {
        EditorTool.Selection -> "Select"
        EditorTool.Eraser -> "Erase"
    }

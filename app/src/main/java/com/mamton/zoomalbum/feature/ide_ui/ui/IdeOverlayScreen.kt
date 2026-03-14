package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamton.zoomalbum.feature.ide_ui.viewmodel.IdeViewModel
import com.mamton.zoomalbum.feature.ide_ui.viewmodel.PanelPosition

/**
 * Root overlay rendered above CanvasScreen.
 *
 * Layout:
 * ┌────────────────────────────────────┐
 * │               Top                  │  ← PanelSlot(Top)
 * ├──────────┬──────────────┬──────────┤
 * │ LeftTop  │              │ RightTop │
 * │          │   (canvas)   │          │  ← Row: left column | canvas viewport | right column
 * │LeftBottom│              │RightBottom│
 * ├──────────┴──────────────┴──────────┤
 * │              Bottom                │  ← PanelSlot(Bottom)
 * └────────────────────────────────────┘
 *
 * Floating panels sit in a separate Box layer on top of the whole layout.
 */
@Composable
fun IdeOverlayScreen(
    viewModel: IdeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val slots = state.activePanelPerSlot

    Box(modifier = Modifier.fillMaxSize()) {

        // Layer 1: docked slots
        Column(modifier = Modifier.fillMaxSize()) {

            // Top bar
            PanelSlot(
                position = PanelPosition.Top,
                panels = state.panels,
                activePanelId = slots[PanelPosition.Top],
                onAction = viewModel::onAction,
                modifier = Modifier.fillMaxWidth(),
            )

            // Middle row: left column | canvas viewport | right column
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {

                // Left column: LeftTop stacked above LeftBottom
                Column(modifier = Modifier.fillMaxHeight()) {
                    PanelSlot(
                        position = PanelPosition.LeftTop,
                        panels = state.panels,
                        activePanelId = slots[PanelPosition.LeftTop],
                        onAction = viewModel::onAction,
                        modifier = Modifier.weight(1f),
                    )
                    PanelSlot(
                        position = PanelPosition.LeftBottom,
                        panels = state.panels,
                        activePanelId = slots[PanelPosition.LeftBottom],
                        onAction = viewModel::onAction,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Centre: canvas sits behind this overlay — this Box is transparent
                Box(modifier = Modifier.weight(1f).fillMaxHeight())

                // Right column: RightTop stacked above RightBottom
                Column(modifier = Modifier.fillMaxHeight()) {
                    PanelSlot(
                        position = PanelPosition.RightTop,
                        panels = state.panels,
                        activePanelId = slots[PanelPosition.RightTop],
                        onAction = viewModel::onAction,
                        modifier = Modifier.weight(1f),
                    )
                    PanelSlot(
                        position = PanelPosition.RightBottom,
                        panels = state.panels,
                        activePanelId = slots[PanelPosition.RightBottom],
                        onAction = viewModel::onAction,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Bottom bar
            PanelSlot(
                position = PanelPosition.Bottom,
                panels = state.panels,
                activePanelId = slots[PanelPosition.Bottom],
                onAction = viewModel::onAction,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Layer 2: floating panels — each positions itself via Modifier.offset + zIndex
        state.panels
            .filter { it.position == PanelPosition.Floating && it.isVisible }
            .forEach { panel ->
                FloatingPanel(
                    panel = panel,
                    onAction = viewModel::onAction,
                )
            }
    }
}

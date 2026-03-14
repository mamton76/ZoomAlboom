package com.mamton.zoomalbum.feature.ide_ui.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mamton.zoomalbum.core.designsystem.AccentCyan
import com.mamton.zoomalbum.core.designsystem.PanelBackground
import com.mamton.zoomalbum.core.designsystem.PanelBorder
import com.mamton.zoomalbum.core.designsystem.TextSecondary
import com.mamton.zoomalbum.feature.ide_ui.viewmodel.IdeAction
import com.mamton.zoomalbum.feature.ide_ui.viewmodel.PanelPosition
import com.mamton.zoomalbum.feature.ide_ui.viewmodel.PanelState

private val VERTICAL_POSITIONS = setOf(
    PanelPosition.LeftTop, PanelPosition.LeftBottom,
    PanelPosition.RightTop, PanelPosition.RightBottom,
)

/**
 * A positional container that:
 *  - Filters panels by [position] and visibility.
 *  - When the slot holds multiple panels, renders a tab bar so the user can
 *    switch between them. Only the active panel's content is shown.
 *  - Has no knowledge of what content a panel renders — delegates to [DockedPanel].
 */
@Composable
fun PanelSlot(
    position: PanelPosition,
    panels: List<PanelState>,
    activePanelId: String?,
    onAction: (IdeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slotPanels = panels.filter { it.position == position && it.isVisible }
    if (slotPanels.isEmpty()) return

    val activePanel = slotPanels.firstOrNull { it.id == activePanelId } ?: slotPanels.first()

    val isVertical = position in VERTICAL_POSITIONS
    val sizeModifier = if (isVertical) {
        Modifier.width(activePanel.width)
    } else {
        Modifier.fillMaxWidth().height(activePanel.height)
    }

    Surface(
        modifier = modifier
            .then(sizeModifier)
            .animateContentSize(),
        color = PanelBackground,
        tonalElevation = 2.dp,
    ) {
        Column {
            if (slotPanels.size > 1) {
                SlotTabBar(
                    panels = slotPanels,
                    activePanel = activePanel,
                    onSelectPanel = { onAction(IdeAction.SelectSlotActivePanel(position, it.id)) },
                )
            }
            DockedPanel(panel = activePanel, onAction = onAction)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotTabBar(
    panels: List<PanelState>,
    activePanel: PanelState,
    onSelectPanel: (PanelState) -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = panels.indexOf(activePanel).coerceAtLeast(0),
        containerColor = PanelBackground,
        contentColor = AccentCyan,
        divider = { HorizontalDivider(color = PanelBorder, thickness = 1.dp) },
    ) {
        panels.forEach { panel ->
            Tab(
                selected = panel.id == activePanel.id,
                onClick = { onSelectPanel(panel) },
                text = {
                    Text(
                        text = panel.title,
                        fontSize = 12.sp,
                        color = if (panel.id == activePanel.id) AccentCyan else TextSecondary,
                    )
                },
            )
        }
    }
}